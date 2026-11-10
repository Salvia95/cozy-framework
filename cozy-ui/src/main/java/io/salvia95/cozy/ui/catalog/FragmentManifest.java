package io.salvia95.cozy.ui.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * fragment 매니페스트 {@code cozy/fragments.yml}. 프레임워크(cozy-ui jar)와 앱이 같은 경로에 각자 둔다.
 *
 * <p>형식 오류는 고치는 방법이 담긴 메시지로 즉시 실패한다. 공개 형식이며 문서는 docs/conventions/ui.md에 있다. cozy-test가
 * 규칙 R8 검사에 같은 파서를 쓴다.
 */
public record FragmentManifest(List<Entry> fragments) {

	public static final String LOCATION = "classpath*:cozy/fragments.yml";

	private static final Pattern SIGNATURE = Pattern.compile("([A-Za-z][A-Za-z0-9]*)\\(([^)]*)\\)");

	/** 값 대신 fragment를 받는 파라미터 타입 */
	public static final String FRAGMENT_TYPE = "fragment";

	/**
	 * @param id 매니페스트 안에서 고유한 이름 ({@code component/badge})
	 * @param template 템플릿 경로 ({@code cozy/component/badge})
	 * @param signature {@code badge(text, color)}
	 * @param purpose 용도 한 줄
	 * @param label 사람에게 보일 이름 ({@code 배지}). 적지 않았으면 빈 문자열
	 * @param params 파라미터 (signature와 같은 순서)
	 * @param samples 상태 이름 → 파라미터 값
	 * @param document 완전한 HTML 문서를 그리는 fragment인가 (레이아웃)
	 * @param source 이 항목을 읽은 파일
	 * @param line 이 항목이 시작하는 줄 (1부터)
	 */
	public record Entry(String id, String template, String signature, String purpose, String label, List<Param> params,
			Map<String, Map<String, Object>> samples, boolean document, String source, int line) {

		public String fragmentName() {
			Matcher m = SIGNATURE.matcher(this.signature);
			return m.matches() ? m.group(1) : this.signature;
		}

		/** 화면에 보일 이름. {@code label}이 없으면 id의 마지막 마디({@code badge}) */
		public String displayName() {
			return this.label.isBlank() ? this.id.substring(this.id.lastIndexOf('/') + 1) : this.label;
		}

		public boolean isFramework() {
			return this.template.startsWith("cozy/");
		}

		/** 카탈로그 묶음: layout, pattern, component, 모듈 공개 fragment는 spi */
		public String group() {
			String[] parts = this.template.split("/");
			return (parts.length >= 2) ? parts[1] : "";
		}
	}

	/**
	 * @param name 파라미터 이름
	 * @param type {@code String}, {@code boolean}, {@code int}, {@code fragment}, 또는 클래스 FQN
	 * @param required 필수 여부
	 * @param description 설명
	 * @param label 사람에게 보일 이름 ({@code 필터}). 적지 않았으면 빈 문자열
	 */
	public record Param(String name, String type, boolean required, String description, String label) {

		/** 화면에 보일 이름. {@code label}이 없으면 파라미터 이름 */
		public String displayName() {
			return this.label.isBlank() ? this.name : this.label;
		}

		public boolean isFragment() {
			return FRAGMENT_TYPE.equals(this.type);
		}
	}

	public static FragmentManifest load(ClassLoader classLoader) {
		List<Entry> all = new ArrayList<>();
		try {
			Resource[] resources = new PathMatchingResourcePatternResolver(classLoader).getResources(LOCATION);
			for (Resource resource : resources) {
				try (InputStream in = resource.getInputStream()) {
					all.addAll(parse(in, describe(resource, "cozy/fragments.yml")));
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] fragment 매니페스트를 읽을 수 없습니다", ex);
		}
		Set<String> ids = new LinkedHashSet<>();
		for (Entry entry : all) {
			if (!ids.add(entry.id())) {
				throw new IllegalStateException("[cozy] fragment 매니페스트에 id '" + entry.id() + "'가 두 번 있습니다 (" + entry.source() + ")");
			}
		}
		return new FragmentManifest(List.copyOf(all));
	}

	public Entry require(String id) {
		return this.fragments.stream()
			.filter((e) -> e.id().equals(id))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("[cozy] 매니페스트에 없는 fragment: " + id));
	}

	/**
	 * 매니페스트 파일 하나를 읽는다. 형식 검사를 바꾸면 cozy-cli의 {@code AppResources.manifest}도 같이 고친다.
	 * @param in YAML (UTF-8)
	 * @param source 메시지에 보일 위치
	 */
	@SuppressWarnings("unchecked")
	public static List<Entry> parse(InputStream in, String source) {
		String text;
		try {
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
		List<Integer> lines = entryLines(text);
		if (root == null) {
			return List.of();
		}
		if (!(root instanceof Map<?, ?> map) || !(map.get("fragments") instanceof List<?> list)) {
			throw invalid(source, "최상위에 'fragments:' 목록이 있어야 합니다");
		}
		List<Entry> entries = new ArrayList<>();
		for (int index = 0; index < list.size(); index++) {
			Object item = list.get(index);
			int line = (index < lines.size()) ? lines.get(index) : 0;
			if (!(item instanceof Map<?, ?> raw)) {
				throw invalid(source, "fragments의 각 항목은 맵이어야 합니다");
			}
			Map<String, Object> fragment = (Map<String, Object>) raw;
			String id = text(fragment, "id", source, null);
			String template = text(fragment, "template", source, id);
			String signature = text(fragment, "signature", source, id);
			String purpose = text(fragment, "purpose", source, id);
			Matcher m = SIGNATURE.matcher(signature);
			if (!m.matches()) {
				throw invalid(source, id + ": signature는 'name(a, b)' 형식이어야 합니다: " + signature);
			}
			List<String> signatureParams = m.group(2).isBlank() ? List.of()
					: List.of(m.group(2).split("\\s*,\\s*")).stream().map(String::trim).toList();
			List<Param> params = new ArrayList<>();
			if (fragment.get("params") instanceof List<?> rawParams) {
				for (Object p : rawParams) {
					Map<String, Object> param = (Map<String, Object>) p;
					params.add(new Param(text(param, "name", source, id), text(param, "type", source, id),
							Boolean.TRUE.equals(param.get("required")), String.valueOf(param.getOrDefault("description", "")),
							String.valueOf(param.getOrDefault("label", ""))));
				}
			}
			List<String> declared = params.stream().map(Param::name).toList();
			if (!declared.equals(signatureParams)) {
				throw invalid(source, id + ": params " + declared + "가 signature의 파라미터 " + signatureParams + "와 같은 순서로 같아야 합니다");
			}
			Map<String, Map<String, Object>> samples = new LinkedHashMap<>();
			if (fragment.get("samples") instanceof Map<?, ?> rawSamples) {
				rawSamples.forEach((state, values) -> samples.put(String.valueOf(state),
						(values == null) ? Map.of() : new LinkedHashMap<>((Map<String, Object>) values)));
			}
			if (samples.isEmpty()) {
				throw invalid(source, id + ": samples에 상태가 하나 이상 있어야 합니다 (예: default)");
			}
			entries.add(new Entry(id, template, signature, purpose, String.valueOf(fragment.getOrDefault("label", "")), List.copyOf(params), samples,
					Boolean.TRUE.equals(fragment.get("document")), source, line));
		}
		return entries;
	}

	/**
	 * 메시지용 위치: jar 안이면 {@code [cozy-ui] cozy/fragments.yml}, 디렉터리면 {@code cozy/fragments.yml}.
	 */
	static String describe(Resource resource, String path) {
		try {
			String url = resource.getURL().toString();
			int bang = url.indexOf("!/");
			if (bang < 0) {
				return path;
			}
			String jar = url.substring(0, bang);
			jar = jar.substring(jar.lastIndexOf('/') + 1).replaceAll("-\\d.*\\.jar$", "").replace(".jar", "");
			return "[" + jar + "] " + path;
		}
		catch (IOException ex) {
			return path;
		}
	}

	/** fragments 목록의 각 항목이 시작하는 줄 */
	private static List<Integer> entryLines(String text) {
		List<Integer> lines = new ArrayList<>();
		Node root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new java.io.StringReader(text));
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

	private static IllegalStateException invalid(String source, String message) {
		return new IllegalStateException("[cozy] fragment 매니페스트 오류 (" + source + "): " + message);
	}
}
