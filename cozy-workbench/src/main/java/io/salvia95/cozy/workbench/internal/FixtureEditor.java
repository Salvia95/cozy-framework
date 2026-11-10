package io.salvia95.cozy.workbench.internal;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * 화면 스펙 YAML의 상태 값을 고친다. YAML을 다시 쓰지 않고, 파서가 알려 주는 값의 자리만 바꿔 쓴다. 그래서 주석, 키 순서, 들여쓰기, 다른 값의 모양이
 * 글자 그대로 남는다.
 *
 * <ul>
 * <li>고치는 것은 이미 있는 값(문자열, 숫자, 참·거짓)뿐이다. 키나 목록의 항목을 더하거나 빼지 않는다.</li>
 * <li>여러 줄 블록({@code |}, {@code >})과 비어 있는 값은 고치지 않는다.</li>
 * <li>상태는 통째로 복제할 수 있다: 그 상태의 글을 새 이름으로 바로 아래에 넣는다.</li>
 * </ul>
 */
final class FixtureEditor {

	private FixtureEditor() {
	}

	/**
	 * 상태의 값 하나.
	 *
	 * @param path 상태 안의 자리 ({@code result/rows/0/title})
	 * @param value 값 (따옴표와 이스케이프를 푼 것)
	 * @param kind {@code text}, {@code int}, {@code number}, {@code bool}, {@code null}
	 * @param editable 고칠 수 있는가
	 */
	record Field(String path, String value, String kind, boolean editable) {
	}

	/** 값과 소스에서의 자리 */
	private record Located(Field field, int start, int end, boolean quoted) {
	}

	/** 따옴표 없이 써도 되는 글: 앞뒤 공백이 없고 YAML이 달리 읽을 글자가 없다 */
	private static final Pattern PLAIN_SAFE = Pattern.compile("[^\\s:#,\\[\\]{}&*!|>'\"%@`?-][^:#,\\[\\]{}\\n\\r\"'`]*(?<!\\s)");

	private static final Pattern STATE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

	private static Yaml yaml() {
		return new Yaml(new SafeConstructor(new LoaderOptions()));
	}

	private static MappingNode statesNode(String yaml) {
		Node root = yaml().compose(new StringReader(yaml));
		if (root instanceof MappingNode map) {
			for (NodeTuple tuple : map.getValue()) {
				if (key(tuple).equals("states") && tuple.getValueNode() instanceof MappingNode states) {
					return states;
				}
			}
		}
		throw new IllegalArgumentException("스펙에 states가 없습니다.");
	}

	private static String key(NodeTuple tuple) {
		return (tuple.getKeyNode() instanceof ScalarNode scalar) ? scalar.getValue() : "";
	}

	private static NodeTuple state(MappingNode states, String state) {
		return states.getValue()
			.stream()
			.filter((t) -> key(t).equals(state))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("스펙에 상태 '" + state + "'가 없습니다."));
	}

	/** 상태 이름 (파일에 적힌 순서) */
	static List<String> states(String yaml) {
		return statesNode(yaml).getValue().stream().map(FixtureEditor::key).toList();
	}

	/** 상태의 값들 (파일에 적힌 순서) */
	static List<Field> fields(String yaml, String state) {
		return locate(yaml, state).stream().map(Located::field).toList();
	}

	private static List<Located> locate(String yaml, String state) {
		List<Located> found = new ArrayList<>();
		walk(yaml, state(statesNode(yaml), state).getValueNode(), "", found);
		return found;
	}

	private static void walk(String yaml, Node node, String path, List<Located> found) {
		if (node instanceof MappingNode map) {
			for (NodeTuple tuple : map.getValue()) {
				walk(yaml, tuple.getValueNode(), path.isEmpty() ? key(tuple) : path + "/" + key(tuple), found);
			}
		}
		else if (node instanceof SequenceNode sequence) {
			for (int i = 0; i < sequence.getValue().size(); i++) {
				walk(yaml, sequence.getValue().get(i), path + "/" + i, found);
			}
		}
		else if (node instanceof ScalarNode scalar && !path.isEmpty()) {
			// 파서의 자리는 코드 포인트 수다
			int start = yaml.offsetByCodePoints(0, scalar.getStartMark().getIndex());
			int end = yaml.offsetByCodePoints(0, scalar.getEndMark().getIndex());
			DumperOptions.ScalarStyle style = scalar.getScalarStyle();
			boolean quoted = style == DumperOptions.ScalarStyle.DOUBLE_QUOTED || style == DumperOptions.ScalarStyle.SINGLE_QUOTED;
			boolean editable = (quoted || style == DumperOptions.ScalarStyle.PLAIN) && end > start;
			found.add(new Located(new Field(path, scalar.getValue(), kind(scalar.getTag()), editable), start, end, quoted));
		}
	}

	private static String kind(Tag tag) {
		if (Tag.BOOL.equals(tag)) {
			return "bool";
		}
		if (Tag.INT.equals(tag)) {
			return "int";
		}
		if (Tag.FLOAT.equals(tag)) {
			return "number";
		}
		return Tag.NULL.equals(tag) ? "null" : "text";
	}

	/**
	 * 값을 바꾼 YAML. 지금 값과 같은 것은 건드리지 않는다.
	 *
	 * @param values 자리 → 새 값
	 * @throws IllegalArgumentException 없는 자리이거나 고칠 수 없는 값일 때
	 */
	static String set(String yaml, String state, Map<String, String> values) {
		List<Located> located = locate(yaml, state);
		List<Located> targets = new ArrayList<>();
		for (String path : values.keySet()) {
			Located target = located.stream()
				.filter((l) -> l.field().path().equals(path))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("상태 '" + state + "'에 " + path + "가 없습니다."));
			if (target.field().value().equals(values.get(path))) {
				continue;
			}
			if (!target.field().editable()) {
				throw new IllegalArgumentException(path + "는 여기서 고칠 수 없습니다(여러 줄 값이거나 비어 있는 값). 스펙 파일에서 고치세요.");
			}
			targets.add(target);
		}
		// 뒤의 것부터 바꿔야 앞의 자리가 그대로다
		targets.sort(Comparator.comparingInt(Located::start).reversed());
		StringBuilder result = new StringBuilder(yaml);
		for (Located target : targets) {
			result.replace(target.start(), target.end(), encode(values.get(target.field().path()), target));
		}
		return result.toString();
	}

	/** 새 값을 YAML 스칼라로 쓴다. 글은 글로, 숫자·참거짓은 그렇게 읽히는 동안만 따옴표 없이 */
	private static String encode(String text, Located target) {
		boolean plainSafe = PLAIN_SAFE.matcher(text).matches();
		Object loaded = plainSafe ? yaml().load(text) : null;
		if (target.field().kind().equals("text")) {
			return (!target.quoted() && plainSafe && text.equals(loaded)) ? text : quote(text);
		}
		return (plainSafe && (loaded instanceof Number || loaded instanceof Boolean)) ? text : quote(text);
	}

	private static String quote(String text) {
		return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
				+ '"';
	}

	/**
	 * 상태 하나를 새 이름으로 복제한 YAML. 복제한 상태는 원래 상태 바로 아래에 놓인다.
	 *
	 * @throws IllegalArgumentException 없는 상태, 이미 있는 이름, 쓸 수 없는 이름일 때
	 */
	static String duplicate(String yaml, String from, String name) {
		if (!STATE_NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("상태 이름은 영문으로 시작하고 영문, 숫자, -, _만 씁니다: " + name);
		}
		MappingNode states = statesNode(yaml);
		List<NodeTuple> tuples = states.getValue();
		NodeTuple source = state(states, from);
		if (tuples.stream().anyMatch((t) -> key(t).equals(name))) {
			throw new IllegalArgumentException("상태 '" + name + "'가 이미 있습니다.");
		}
		int keyStart = yaml.offsetByCodePoints(0, source.getKeyNode().getStartMark().getIndex());
		int keyEnd = yaml.offsetByCodePoints(0, source.getKeyNode().getEndMark().getIndex());
		int blockStart = lineStart(yaml, keyStart);
		int index = tuples.indexOf(source);
		int blockEnd;
		if (index + 1 < tuples.size()) {
			blockEnd = lineStart(yaml, yaml.offsetByCodePoints(0, tuples.get(index + 1).getKeyNode().getStartMark().getIndex()));
		}
		else {
			int statesEnd = yaml.offsetByCodePoints(0, states.getEndMark().getIndex());
			blockEnd = (statesEnd >= yaml.length()) ? yaml.length() : lineStart(yaml, statesEnd);
			// 마지막 상태 뒤의 빈 줄은 복제하지 않는다
			while (blockEnd > keyEnd && yaml.substring(lineStart(yaml, blockEnd - 1), blockEnd).isBlank()) {
				blockEnd = lineStart(yaml, blockEnd - 1);
			}
		}
		String block = yaml.substring(blockStart, blockEnd);
		if (!block.endsWith("\n")) {
			block += "\n";
		}
		String copy = block.substring(0, keyStart - blockStart) + name + block.substring(keyEnd - blockStart);
		String head = yaml.substring(0, blockEnd);
		return (head.endsWith("\n") ? head : head + "\n") + copy + yaml.substring(blockEnd);
	}

	private static int lineStart(String text, int offset) {
		return text.lastIndexOf('\n', Math.max(offset - 1, 0)) + 1;
	}
}
