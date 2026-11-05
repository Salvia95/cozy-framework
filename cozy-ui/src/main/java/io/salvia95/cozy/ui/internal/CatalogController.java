package io.salvia95.cozy.ui.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.salvia95.cozy.ui.internal.FragmentManifest.Entry;
import io.salvia95.cozy.ui.internal.FragmentManifest.Param;

/**
 * 패턴 카탈로그 {@code /cozy/dev/catalog} (PRD §6.4). {@code cozy.dev.catalog.enabled=true}일 때만 등록된다.
 *
 * <p>프레임워크와 앱 매니페스트의 모든 fragment를 상태별 샘플로 보여 준다. 미리보기는 fragment마다 iframe으로 분리해
 * 레이아웃 fragment(완전한 문서)도 그대로 보인다.
 */
@Controller
@RequestMapping("/cozy/dev/catalog")
public class CatalogController {

	/** 샘플에서 폼 값({@code th:object})으로 쓰는 키 */
	static final String FORM_KEY = "_form";

	/** 샘플에서 필드 오류로 쓰는 키 */
	static final String ERRORS_KEY = "_errors";

	private static final Set<String> PLAIN_TYPES = Set.of("String", "boolean", "int", "long", "List", "Map", "Object");

	private final FragmentManifest manifest;

	private final JsonMapper jsonMapper;

	public CatalogController(FragmentManifest manifest, JsonMapper jsonMapper) {
		this.manifest = manifest;
		this.jsonMapper = jsonMapper;
	}

	@GetMapping
	String catalog(Model model) {
		Map<String, List<Entry>> groups = new LinkedHashMap<>();
		for (String group : List.of("layout", "pattern", "component")) {
			groups.put(group, this.manifest.fragments().stream().filter((e) -> e.group().equals(group)).toList());
		}
		model.addAttribute("groups", groups);
		model.addAttribute("total", this.manifest.fragments().size());
		return "cozy/dev/catalog";
	}

	@GetMapping("/render")
	String render(@RequestParam String id, @RequestParam String state, Model model) {
		Entry entry = this.manifest.require(id);
		Map<String, Object> sample = entry.samples().get(state);
		if (sample == null) {
			throw new IllegalArgumentException("[cozy] " + id + "에 상태 '" + state + "'가 없습니다");
		}
		model.addAttribute("cozySample", convert(entry, sample));
		if (sample.get(FORM_KEY) instanceof Map<?, ?> form) {
			addSampleForm(model, form, sample.get(ERRORS_KEY));
		}
		return CatalogTemplateResolver.viewName(id, state);
	}

	private Map<String, Object> convert(Entry entry, Map<String, Object> sample) {
		Map<String, Object> converted = new LinkedHashMap<>();
		for (Param param : entry.params()) {
			Object value = sample.get(param.name());
			if (value != null && !param.isFragment() && !PLAIN_TYPES.contains(param.type())) {
				value = this.jsonMapper.convertValue(value, loadType(param.type()));
			}
			converted.put(param.name(), value);
		}
		return converted;
	}

	@SuppressWarnings("unchecked")
	private static void addSampleForm(Model model, Map<?, ?> form, Object errors) {
		Map<String, Object> target = new LinkedHashMap<>((Map<String, Object>) form);
		MapBindingResult bindingResult = new MapBindingResult(target, "cozySampleForm");
		if (errors instanceof Map<?, ?> fieldErrors) {
			fieldErrors.forEach((field, message) -> bindingResult.rejectValue(String.valueOf(field), "sample",
					String.valueOf(message)));
		}
		model.addAttribute("cozySampleForm", target);
		model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "cozySampleForm", bindingResult);
	}

	private static Class<?> loadType(String type) {
		try {
			return Class.forName(type, false, CatalogController.class.getClassLoader());
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException("[cozy] 매니페스트의 파라미터 타입을 찾을 수 없습니다: " + type, ex);
		}
	}
}
