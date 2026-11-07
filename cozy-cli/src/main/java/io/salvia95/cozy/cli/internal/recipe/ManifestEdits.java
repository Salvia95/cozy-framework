package io.salvia95.cozy.cli.internal.recipe;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 앱 매니페스트({@code cozy/fragments.yml})의 {@code fragments} 목록 끝에 항목을 덧붙인다. 알아보는 형식은 {@code fragments: []}와,
 * 마지막 최상위 키인 블록 목록이다. 다른 형식이면 손으로 고치도록 알리고 실패한다.
 */
final class ManifestEdits {

	static final String FILE = "src/main/resources/cozy/fragments.yml";

	private static final Pattern EMPTY = Pattern.compile("(?m)^fragments:\\s*\\[\\s*]\\s*$");

	private ManifestEdits() {
	}

	/**
	 * @param text 지금 내용 (없으면 {@code null})
	 * @param entry 들여쓰기 없는 항목 YAML ({@code - id: ...}로 시작)
	 * @return 바뀐 내용. 같은 id·template·signature가 이미 있으면 그대로
	 */
	@SuppressWarnings("unchecked")
	static String append(String text, String id, String template, String signature, String entry) {
		if (text == null) {
			return "# 앱 fragment 매니페스트 (docs/conventions/ui.md)\nfragments:\n" + indent(entry, 2);
		}
		Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
		if (!(root instanceof Map<?, ?> map) || !(map.get("fragments") instanceof List<?> list)) {
			throw cannot("최상위에 'fragments:' 목록이 없습니다");
		}
		for (Object item : list) {
			if (item instanceof Map<?, ?> fragment && id.equals(fragment.get("id"))) {
				if (template.equals(fragment.get("template")) && signature.equals(fragment.get("signature"))) {
					return text;
				}
				throw new CliException(FILE + "에 id가 " + id + "인 항목이 이미 있고 내용이 다릅니다. 덮어쓰지 않습니다.");
			}
		}
		Matcher empty = EMPTY.matcher(text);
		if (list.isEmpty() && empty.find()) {
			return text.substring(0, empty.start()) + "fragments:\n" + indent(entry, 2).stripTrailing() + "\n"
					+ text.substring(empty.end()).replaceFirst("^\\n", "");
		}
		Node node = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(text));
		if (!(node instanceof MappingNode mapping) || mapping.getValue().isEmpty()) {
			throw cannot("최상위가 맵이 아닙니다");
		}
		NodeTuple last = mapping.getValue().getLast();
		if (!(last.getKeyNode() instanceof ScalarNode key) || !key.getValue().equals("fragments")
				|| !(last.getValueNode() instanceof SequenceNode sequence)
				|| sequence.getFlowStyle() != DumperOptions.FlowStyle.BLOCK || sequence.getValue().isEmpty()) {
			throw cannot("fragments가 파일의 마지막 최상위 키인 블록 목록(- id: ...)이어야 덧붙일 수 있습니다");
		}
		int column = sequence.getValue().getFirst().getStartMark().getColumn() - 2;
		String body = text.endsWith("\n") ? text : text + "\n";
		return body + indent(entry, Math.max(column, 0));
	}

	private static String indent(String entry, int spaces) {
		String pad = " ".repeat(spaces);
		StringBuilder out = new StringBuilder();
		for (String line : entry.split("\n", -1)) {
			if (!line.isEmpty()) {
				out.append(pad).append(line);
			}
			out.append('\n');
		}
		return out.toString().replaceAll("\\n+$", "\n");
	}

	private static CliException cannot(String why) {
		return new CliException(FILE + "에 덧붙일 수 없습니다: " + why + ". 항목을 손으로 더하세요 (형식: docs/conventions/ui.md).");
	}
}
