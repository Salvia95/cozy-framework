package io.salvia95.cozy.cli.internal;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import io.salvia95.cozy.rules.FragmentDeclaration;

/**
 * 앱과 프레임워크의 YAML 자원: fragment 매니페스트(cozy-ui의 {@code FragmentManifest}와 같은 형식과 검사), 화면 스펙, 앱 설정.
 */
public final class AppResources {

	private static final Pattern SIGNATURE = Pattern.compile("([A-Za-z][A-Za-z0-9]*)\\(([^)]*)\\)");

	private AppResources() {
	}

	/**
	 * 매니페스트 항목. 규칙 검사, describe, {@code cozy set arg}가 쓴다.
	 *
	 * @param params 파라미터 이름 (signature 순서)
	 * @param choices 파라미터 이름 → 넘길 수 있는 값 (적은 순서). 값을 정한 파라미터만
	 */
	public record ManifestEntry(String id, String template, String signature, String purpose, String source, int line,
			List<String> params, Map<String, List<String>> choices) {

		public FragmentDeclaration declaration() {
			return new FragmentDeclaration(this.id, this.template, this.signature, this.source, this.line);
		}

		/** fragment 이름 ({@code badge(text, tone)}의 {@code badge}) */
		public String fragmentName() {
			int open = this.signature.indexOf('(');
			return (open < 0) ? this.signature : this.signature.substring(0, open);
		}
	}

	/** 화면 스펙 한 파일의 이름, 템플릿, 상태 */
	public record Spec(String file, String screen, String template, List<String> states) {
	}

	/**
	 * 매니페스트 하나. 형식이 틀리면 cozy-ui와 같은 기준으로 실패한다.
	 *
	 * @throws IllegalStateException 형식이 틀렸을 때 ({@code [cozy] fragment 매니페스트 오류 (...)})
	 */
	@SuppressWarnings("unchecked")
	public static List<ManifestEntry> manifest(String text, String source) {
		Object root = yaml().load(text);
		if (root == null) {
			return List.of();
		}
		if (!(root instanceof Map<?, ?> map) || !(map.get("fragments") instanceof List<?> list)) {
			throw invalid(source, "최상위에 'fragments:' 목록이 있어야 합니다");
		}
		List<Integer> lines = entryLines(text);
		List<ManifestEntry> entries = new ArrayList<>();
		for (int index = 0; index < list.size(); index++) {
			if (!(list.get(index) instanceof Map<?, ?> raw)) {
				throw invalid(source, "fragments의 각 항목은 맵이어야 합니다");
			}
			Map<String, Object> fragment = (Map<String, Object>) raw;
			String id = text(fragment, "id", source, null);
			String template = text(fragment, "template", source, id);
			String signature = text(fragment, "signature", source, id);
			String purpose = text(fragment, "purpose", source, id);
			var m = SIGNATURE.matcher(signature);
			if (!m.matches()) {
				throw invalid(source, id + ": signature는 'name(a, b)' 형식이어야 합니다: " + signature);
			}
			List<String> signatureParams = m.group(2).isBlank() ? List.of()
					: Stream.of(m.group(2).split("\\s*,\\s*")).map(String::trim).toList();
			List<String> declared = new ArrayList<>();
			Map<String, List<String>> choices = new LinkedHashMap<>();
			if (fragment.get("params") instanceof List<?> params) {
				for (Object p : params) {
					String name = text((Map<String, Object>) p, "name", source, id);
					String type = text((Map<String, Object>) p, "type", source, id);
					declared.add(name);
					List<String> values = choices(((Map<String, Object>) p).get("choices"), type, source, id + "의 " + name);
					if (!values.isEmpty()) {
						choices.put(name, values);
					}
				}
			}
			if (!declared.equals(signatureParams)) {
				throw invalid(source, id + ": params " + declared + "가 signature의 파라미터 " + signatureParams
						+ "와 같은 순서로 같아야 합니다");
			}
			if (!(fragment.get("samples") instanceof Map<?, ?> samples) || samples.isEmpty()) {
				throw invalid(source, id + ": samples에 상태가 하나 이상 있어야 합니다 (예: default)");
			}
			entries.add(new ManifestEntry(id, template, signature, purpose, source,
					(index < lines.size()) ? lines.get(index) : 0, List.copyOf(declared), Map.copyOf(choices)));
		}
		return entries;
	}

	/** 파라미터의 {@code choices}: cozy-ui {@code FragmentManifest.choices}와 같은 검사 */
	private static List<String> choices(Object raw, String type, String source, String where) {
		if (raw == null) {
			return List.of();
		}
		if (!"String".equals(type)) {
			throw invalid(source, where + ": choices는 String 파라미터에만 씁니다 (type: " + type + ")");
		}
		List<Object> values;
		if (raw instanceof List<?> list) {
			values = new ArrayList<>(list);
		}
		else if (raw instanceof Map<?, ?> map) {
			values = new ArrayList<>(map.keySet());
		}
		else {
			throw invalid(source, where + ": choices는 값의 목록이나 값 → 이름의 맵입니다 (예: { info: 정보, success: 성공 })");
		}
		if (values.isEmpty()) {
			throw invalid(source, where + ": choices가 비어 있습니다. 값을 정하지 않으려면 choices를 지웁니다");
		}
		List<String> texts = new ArrayList<>();
		for (Object value : values) {
			if (!(value instanceof String text) || text.isBlank() || text.contains("'") || text.contains("\\")) {
				throw invalid(source, where + ": choices의 값은 따옴표와 역슬래시가 없는 글자입니다: " + value);
			}
			texts.add(text);
		}
		return List.copyOf(texts);
	}

	/** {@code src/main/resources/specs/*.yml}. 읽지 못하는 스펙은 상태 없이 둔다(검사는 C3가 한다) */
	@SuppressWarnings("unchecked")
	public static List<Spec> specs(Path project) {
		Path dir = project.resolve("src/main/resources/specs");
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		List<Spec> specs = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter((f) -> f.toString().endsWith(".yml") || f.toString().endsWith(".yaml"))
				.sorted()
				.toList()) {
				String relative = project.relativize(file).toString().replace('\\', '/');
				Object root;
				try {
					root = yaml().load(Files.readString(file, StandardCharsets.UTF_8));
				}
				catch (RuntimeException ex) {
					specs.add(new Spec(relative, null, null, List.of()));
					continue;
				}
				if (root instanceof Map<?, ?> map) {
					List<String> states = (map.get("states") instanceof Map<?, ?> s)
							? s.keySet().stream().map(String::valueOf).toList() : List.of();
					specs.add(new Spec(relative, string(map.get("screen")), string(map.get("template")), states));
				}
				else {
					specs.add(new Spec(relative, null, null, List.of()));
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return specs;
	}

	/** 앱 설정 속성 하나 ({@code application.properties}, {@code application.yml}, {@code application.yaml}) */
	public static String appProperty(Path project, String name) {
		Path resources = project.resolve("src/main/resources");
		Path properties = resources.resolve("application.properties");
		if (Files.isRegularFile(properties)) {
			Properties p = new Properties();
			try (Reader reader = Files.newBufferedReader(properties, StandardCharsets.UTF_8)) {
				p.load(reader);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			if (p.getProperty(name) != null) {
				return p.getProperty(name);
			}
		}
		for (String file : List.of("application.yml", "application.yaml")) {
			Path yml = resources.resolve(file);
			if (!Files.isRegularFile(yml)) {
				continue;
			}
			try {
				for (Object document : yaml().loadAll(Files.readString(yml, StandardCharsets.UTF_8))) {
					Object value = lookup(document, name);
					if (value != null) {
						return String.valueOf(value);
					}
				}
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return null;
	}

	/** {@code a.b.c}를 중첩 맵이나 점 이름 키에서 찾는다 */
	private static Object lookup(Object node, String name) {
		if (!(node instanceof Map<?, ?> map)) {
			return null;
		}
		if (map.containsKey(name)) {
			return map.get(name);
		}
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key = String.valueOf(entry.getKey());
			if (name.startsWith(key + ".")) {
				Object found = lookup(entry.getValue(), name.substring(key.length() + 1));
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static Yaml yaml() {
		return new Yaml(new SafeConstructor(new LoaderOptions()));
	}

	private static List<Integer> entryLines(String text) {
		List<Integer> lines = new ArrayList<>();
		Node root = yaml().compose(new StringReader(text));
		if (root instanceof MappingNode mapping) {
			for (NodeTuple tuple : mapping.getValue()) {
				if (tuple.getKeyNode() instanceof ScalarNode key && key.getValue().equals("fragments")
						&& tuple.getValueNode() instanceof SequenceNode sequence) {
					for (Node item : sequence.getValue()) {
						lines.add(item.getStartMark().getLine() + 1);
					}
				}
			}
		}
		return lines;
	}

	private static String text(Map<String, Object> map, String key, String source, String id) {
		Object value = map.get(key);
		if (!(value instanceof String s) || s.isBlank()) {
			throw invalid(source, ((id != null) ? id + ": " : "") + "'" + key + "'가 필요합니다");
		}
		return s;
	}

	private static String string(Object value) {
		return (value != null) ? String.valueOf(value) : null;
	}

	private static IllegalStateException invalid(String source, String message) {
		return new IllegalStateException("[cozy] fragment 매니페스트 오류 (" + source + "): " + message);
	}
}
