package io.salvia95.cozy.ui.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Entry;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Param;
import io.salvia95.cozy.ui.catalog.ScreenSpec;

/**
 * 패턴 카탈로그 {@code /cozy/dev/catalog} (PRD §6.4). {@code cozy.dev.catalog.enabled=true}일 때만 등록된다.
 *
 * <ul>
 * <li>{@code /render?id&state}: 매니페스트 fragment를 상태별 샘플로 그린다.</li>
 * <li>{@code /screen?spec&state[&fragment]}: 화면 스펙의 템플릿을 상태별 fixture로 그린다 (PRD §10).</li>
 * </ul>
 * 미리보기는 iframe으로 분리해 레이아웃(완전한 문서)도 그대로 보인다. cozy-test의 렌더링 테스트가 같은 경로를 쓴다.
 */
@Controller
@RequestMapping(CatalogController.PATH)
public class CatalogController {

	public static final String PATH = "/cozy/dev/catalog";

	/** 샘플에서 폼 값({@code th:object})으로 쓰는 키 */
	static final String FORM_KEY = "_form";

	/** 샘플에서 필드 오류로 쓰는 키 */
	static final String ERRORS_KEY = "_errors";

	private static final Set<String> PLAIN_TYPES = Set.of("String", "boolean", "int", "long", "List", "Map", "Object");

	private final FragmentManifest manifest;

	private final List<ScreenSpec> specs;

	public CatalogController(FragmentManifest manifest, List<ScreenSpec> specs) {
		this.manifest = manifest;
		this.specs = List.copyOf(specs);
	}

	@GetMapping
	String catalog(Model model) {
		Map<String, List<Entry>> groups = new LinkedHashMap<>();
		for (String group : List.of("layout", "pattern", "component", "api")) {
			groups.put(group, this.manifest.fragments().stream().filter((e) -> e.group().equals(group)).toList());
		}
		model.addAttribute("groups", groups);
		model.addAttribute("total", this.manifest.fragments().size());
		model.addAttribute("screens", this.specs);
		return "cozy/dev/catalog";
	}

	@GetMapping("/render")
	String render(@RequestParam String id, @RequestParam String state, Model model) {
		Entry entry = this.manifest.require(id);
		Map<String, Object> sample = entry.samples().get(state);
		if (sample == null) {
			throw new IllegalArgumentException("[cozy] " + id + "에 상태 '" + state + "'가 없습니다");
		}
		model.addAttribute("cozySample", convert(entry, state, sample));
		if (sample.get(FORM_KEY) instanceof Map<?, ?> form) {
			addSampleForm(model, form, sample.get(ERRORS_KEY));
		}
		return CatalogTemplateResolver.viewName(id, state);
	}

	@GetMapping("/screen")
	String screen(@RequestParam String spec, @RequestParam String state,
			@RequestParam(required = false) String fragment, Model model) {
		ScreenSpec screen = this.specs.stream()
			.filter((s) -> s.screen().equals(spec))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("[cozy] 화면 스펙이 없습니다: " + spec));
		Map<String, Object> values = screen.states().get(state);
		if (values == null) {
			throw new IllegalArgumentException("[cozy] 스펙 " + spec + "에 상태 '" + state + "'가 없습니다");
		}
		if (fragment != null && !screen.fragments().contains(fragment)) {
			throw new IllegalArgumentException("[cozy] 스펙 " + spec + "의 fragments에 '" + fragment + "'가 없습니다");
		}
		Map<String, Object> errors = (values.get(ScreenSpec.ERRORS_KEY) instanceof Map<?, ?> e) ? cast(e) : Map.of();
		screen.model().forEach((name, type) -> {
			Object value = FixtureMapper.convert(values.get(name), type,
					"스펙 " + spec + "의 상태 '" + state + "' 모델 '" + name + "'");
			model.addAttribute(name, value);
			if (errors.get(name) instanceof Map<?, ?> fieldErrors) {
				BindingResult result = new BeanPropertyBindingResult(value, name);
				fieldErrors.forEach((field, message) -> result.rejectValue(String.valueOf(field), "spec",
						String.valueOf(message)));
				model.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, result);
			}
		});
		return (fragment != null) ? screen.template() + " :: " + fragment : screen.template();
	}

	private Map<String, Object> convert(Entry entry, String state, Map<String, Object> sample) {
		Map<String, Object> converted = new LinkedHashMap<>();
		for (Param param : entry.params()) {
			Object value = sample.get(param.name());
			if (value != null && !param.isFragment() && !PLAIN_TYPES.contains(param.type())) {
				value = FixtureMapper.convert(value, param.type(),
						"매니페스트 " + entry.id() + "의 상태 '" + state + "' 파라미터 '" + param.name() + "'");
			}
			converted.put(param.name(), value);
		}
		return converted;
	}

	private static void addSampleForm(Model model, Map<?, ?> form, Object errors) {
		Map<String, Object> target = new LinkedHashMap<>(cast(form));
		MapBindingResult bindingResult = new MapBindingResult(target, "cozySampleForm");
		if (errors instanceof Map<?, ?> fieldErrors) {
			fieldErrors.forEach((field, message) -> bindingResult.rejectValue(String.valueOf(field), "sample",
					String.valueOf(message)));
		}
		model.addAttribute("cozySampleForm", target);
		model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "cozySampleForm", bindingResult);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Map<?, ?> map) {
		return (Map<String, Object>) map;
	}
}
