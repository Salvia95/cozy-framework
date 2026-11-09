package io.salvia95.cozy.ui.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 화면 스펙 {@code specs/{module}-{screen}.yml}. 패턴 카탈로그 미리보기와 빌드 렌더링 테스트(cozy-test)가 같이
 * 쓴다.
 *
 * <pre>
 * screen: memo-list
 * template: memo/api/list        # 뷰 이름 (화면은 {모듈}/api/ 아래)
 * fragments: [result]            # htmx로 따로 렌더링하는 fragment
 * model:
 *   result: io.salvia95.cozy.reference.memo.api.dto.MemoViews.ListResult
 * states:                        # default, empty는 필수
 *   default:
 *     result: { rows: [ ... ], page: { number: 1, size: 10, totalElements: 1 } }
 *   empty:
 *     result: { rows: [], page: { number: 1, size: 10, totalElements: 0 } }
 * </pre>
 *
 * <p>각 상태는 model의 모든 이름에 값을 준다. {@code _errors}({@code 모델 이름 → 필드 → 메시지})로 검증 오류 상태를 만들
 * 수 있다. 문서: docs/conventions/testing.md
 *
 * @param screen 스펙 이름 ({@code memo-list}). 템플릿 경로에서 {@code api/}를 빼고 {@code /}를 {@code -}로 바꾼 것과 같다
 * @param template 뷰 이름
 * @param fragments htmx 부분 갱신으로 따로 렌더링하는 fragment 이름
 * @param model 모델 이름 → 화면 DTO 타입(FQN, 중첩 클래스는 {@code Outer.Inner})
 * @param states 상태 이름 → 모델 이름 → fixture
 * @param source 읽은 파일
 */
public record ScreenSpec(String screen, String template, List<String> fragments, Map<String, String> model,
		Map<String, Map<String, Object>> states, String source) {

	public static final String LOCATION = "classpath*:specs/*.yml";

	/** 상태에서 검증 오류를 적는 키 */
	public static final String ERRORS_KEY = "_errors";

	/** 모든 스펙에 있어야 하는 상태 */
	public static final List<String> REQUIRED_STATES = List.of("default", "empty");

	public static List<ScreenSpec> load(ClassLoader classLoader) {
		List<ScreenSpec> specs = new ArrayList<>();
		try {
			for (Resource resource : new PathMatchingResourcePatternResolver(classLoader).getResources(LOCATION)) {
				try (InputStream in = resource.getInputStream()) {
					specs.add(parse(in, resource.getFilename(), FragmentManifest.describe(resource, "specs/" + resource.getFilename())));
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 화면 스펙을 읽을 수 없습니다", ex);
		}
		Set<String> names = new LinkedHashSet<>();
		for (ScreenSpec spec : specs) {
			if (!names.add(spec.screen())) {
				throw new IllegalStateException("[cozy] 화면 스펙 '" + spec.screen() + "'가 두 개 있습니다 (" + spec.source() + ")");
			}
		}
		return specs.stream().sorted(java.util.Comparator.comparing(ScreenSpec::screen)).toList();
	}

	/**
	 * @param in YAML
	 * @param fileName 파일 이름 ({@code memo-list.yml}). {@code screen}과 맞는지 검사한다
	 * @param source 메시지에 보일 위치
	 */
	@SuppressWarnings("unchecked")
	/** 화면 템플릿의 스펙 이름: {@code memo/api/list} → {@code memo-list}, {@code memo/api/admin/list} → {@code memo-admin-list} */
	public static String screenName(String template) {
		String[] parts = template.split("/");
		if (parts.length >= 3 && parts[1].equals("api")) {
			return parts[0] + "-" + String.join("-", java.util.Arrays.asList(parts).subList(2, parts.length));
		}
		return template.replace('/', '-');
	}

	public static ScreenSpec parse(InputStream in, String fileName, String source) {
		Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
		if (!(root instanceof Map<?, ?> raw)) {
			throw invalid(source, "최상위가 screen, template, model, states를 가진 맵이어야 합니다");
		}
		Map<String, Object> map = (Map<String, Object>) raw;
		String screen = text(map, "screen", source);
		String template = text(map, "template", source);
		String[] parts = template.split("/");
		if (parts.length < 3 || !parts[1].equals("api")) {
			throw invalid(source, "화면 템플릿은 {모듈}/api/ 아래에 있어야 합니다 (지금: " + template + ")");
		}
		String expected = screenName(template);
		if (!screen.equals(expected)) {
			throw invalid(source, "screen은 템플릿 경로에서 api/를 빼고 '/'를 '-'로 바꾼 '" + expected + "'여야 합니다 (지금: "
					+ screen + ")");
		}
		if (fileName != null && !fileName.equals(screen + ".yml")) {
			throw invalid(source, "파일 이름은 " + screen + ".yml이어야 합니다");
		}
		List<String> fragments = new ArrayList<>();
		if (map.get("fragments") instanceof List<?> list) {
			list.forEach((f) -> fragments.add(String.valueOf(f)));
		}
		else if (map.containsKey("fragments") && map.get("fragments") != null) {
			throw invalid(source, "fragments는 목록이어야 합니다 (예: [result])");
		}
		Map<String, String> model = new LinkedHashMap<>();
		if (map.get("model") instanceof Map<?, ?> rawModel) {
			rawModel.forEach((name, type) -> model.put(String.valueOf(name), String.valueOf(type)));
		}
		else if (map.get("model") != null) {
			throw invalid(source, "model은 '모델 이름: 타입 FQN' 맵이어야 합니다");
		}
		if (!(map.get("states") instanceof Map<?, ?> rawStates)) {
			throw invalid(source, "states가 필요합니다. 최소 default와 empty 두 상태를 적으세요");
		}
		Map<String, Map<String, Object>> states = new LinkedHashMap<>();
		rawStates.forEach((state, values) -> states.put(String.valueOf(state),
				(values == null) ? Map.of() : new LinkedHashMap<>((Map<String, Object>) values)));
		for (String required : REQUIRED_STATES) {
			if (!states.containsKey(required)) {
				throw invalid(source, "states에 '" + required + "'가 없습니다. default와 empty는 모든 스펙에 필요합니다");
			}
		}
		states.forEach((state, values) -> {
			for (String name : model.keySet()) {
				if (!values.containsKey(name)) {
					throw invalid(source, "상태 '" + state + "'에 모델 '" + name + "'의 값이 없습니다. 빈 값이면 {}를 적으세요");
				}
			}
			for (String key : values.keySet()) {
				if (!key.equals(ERRORS_KEY) && !model.containsKey(key)) {
					throw invalid(source, "상태 '" + state + "'의 '" + key + "'가 model에 선언되어 있지 않습니다");
				}
			}
		});
		return new ScreenSpec(screen, template, List.copyOf(fragments), model, states, source);
	}

	private static String text(Map<String, Object> map, String key, String source) {
		if (!(map.get(key) instanceof String s) || s.isBlank()) {
			throw invalid(source, "'" + key + "'가 필요합니다");
		}
		return s;
	}

	private static IllegalStateException invalid(String source, String message) {
		return new IllegalStateException("[cozy] 화면 스펙 오류 (" + source + "): " + message);
	}
}
