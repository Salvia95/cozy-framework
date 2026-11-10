package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.ScreenSpec;

/**
 * 워크벤치 {@code /cozy/dev/workbench}. {@code cozy.dev.workbench.enabled=true}일 때만 등록된다(dev 프로필의 기본값).
 *
 * <p>
 * 화면 하나를 고르면 왼쪽에 구조(패턴 → 자리 → fragment, 줄 번호), 오른쪽에 상태별 미리보기나 소스(템플릿, 스펙)를 보인다. 미리보기는 패턴
 * 카탈로그의 {@code /cozy/dev/catalog/screen}을 그대로 쓴다. 고른 것은 모두 주소의 파라미터({@code spec}, {@code state}, {@code w},
 * {@code view})에 있다. 미리보기는 {@code inspect} 파라미터로 요소마다 소스 위치를 받아, 요소를 누르면 그 위치와 감싸는 구조를 보인다
 * ({@code /cozy/workbench.js}).
 *
 * <p>
 * {@code /check}는 {@code cozy check}의 위반과 고치는 명령을, {@code /map}은 {@code cozy describe}의 앱 지도(모듈, 의존, 계약, 이벤트, 화면,
 * 빈칸)를 보인다. 화면 목록에는 화면마다 위반 수가 붙는다.
 */
@Controller
@RequestMapping(WorkbenchController.PATH)
public class WorkbenchController {

	public static final String PATH = "/cozy/dev/workbench";

	/** 미리보기 폭. {@code full}은 칸 전체 */
	static final List<String> WIDTHS = List.of("full", "1024", "768", "390");

	static final List<String> VIEWS = List.of("preview", "template", "spec");

	private final FragmentManifest manifest;

	private final List<ScreenSpec> specs;

	private final WorkbenchEngine engine;

	public WorkbenchController(FragmentManifest manifest, List<ScreenSpec> specs, WorkbenchEngine engine) {
		this.manifest = manifest;
		this.specs = List.copyOf(specs);
		this.engine = engine;
	}

	/** 번호가 붙은 소스 한 줄 */
	public record Line(int number, String text) {
	}

	@GetMapping
	String workbench(@RequestParam(required = false) String spec, @RequestParam(required = false) String state,
			@RequestParam(defaultValue = "full") String w, @RequestParam(defaultValue = "preview") String view, Model model) {
		if (!WIDTHS.contains(w)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] w는 " + WIDTHS + " 가운데 하나입니다: " + w);
		}
		if (!VIEWS.contains(view)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] view는 " + VIEWS + " 가운데 하나입니다: " + view);
		}
		model.addAttribute("specs", this.specs);
		model.addAttribute("engineAvailable", this.engine.available());
		WorkbenchEngine.Check check = check();
		model.addAttribute("errors", check.errors());
		model.addAttribute("violationCounts", violationCounts(check));
		model.addAttribute("widths", WIDTHS);
		model.addAttribute("w", w);
		model.addAttribute("view", view);
		if (this.specs.isEmpty()) {
			return "cozy/dev/workbench";
		}
		ScreenSpec screen = (spec == null) ? this.specs.getFirst()
				: this.specs.stream()
					.filter((s) -> s.screen().equals(spec))
					.findFirst()
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 화면 스펙이 없습니다: " + spec));
		String selectedState = (state != null) ? state : screen.states().keySet().iterator().next();
		if (!screen.states().containsKey(selectedState)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"[cozy] 스펙 " + screen.screen() + "에 상태 '" + selectedState + "'가 없습니다");
		}
		model.addAttribute("screen", screen);
		model.addAttribute("state", selectedState);

		String templateFile = "templates/" + screen.template() + ".html";
		List<ScreenTree.Item> tree = List.of();
		String specFile = null;
		if (this.engine.available()) {
			Optional<WorkbenchEngine.Screen> described = this.engine.screens()
				.stream()
				.filter((s) -> s.template().equals(screen.template()))
				.findFirst();
			if (described.isPresent()) {
				tree = ScreenTree.of(described.get().outline(), this.manifest, templateFile);
				specFile = described.get().spec();
			}
		}
		model.addAttribute("tree", tree);
		model.addAttribute("specFile", specFile);
		String sourceFile = switch (view) {
			case "template" -> "src/main/resources/" + templateFile;
			case "spec" -> specFile;
			default -> null;
		};
		model.addAttribute("sourceFile", sourceFile);
		model.addAttribute("sourceLines", (sourceFile != null) ? lines(sourceFile) : List.of());
		return "cozy/dev/workbench";
	}

	/** 검사할 수 없으면(프로젝트 폴더가 아님) 빈 결과 */
	private WorkbenchEngine.Check check() {
		return this.engine.available() ? this.engine.check() : new WorkbenchEngine.Check(0, 0, List.of(), null);
	}

	/** 화면 이름 → 그 화면의 템플릿이나 스펙에 있는 위반 수 */
	private Map<String, Long> violationCounts(WorkbenchEngine.Check check) {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (WorkbenchEngine.Violation violation : check.violations()) {
			link(violation).ifPresent((link) -> counts.merge(link.screen(), 1L, Long::sum));
		}
		return counts;
	}

	/**
	 * 위반이 있는 화면과 소스 보기.
	 *
	 * @param screen 화면 스펙 이름
	 * @param view {@code template} 또는 {@code spec}
	 */
	public record ScreenLink(String screen, String view) {
	}

	private Optional<ScreenLink> link(WorkbenchEngine.Violation violation) {
		if (violation.file() == null) {
			return Optional.empty();
		}
		for (ScreenSpec spec : this.specs) {
			if (violation.file().equals("src/main/resources/templates/" + spec.template() + ".html")) {
				return Optional.of(new ScreenLink(spec.screen(), "template"));
			}
			if (violation.file().equals("src/main/resources/specs/" + spec.screen() + ".yml")) {
				return Optional.of(new ScreenLink(spec.screen(), "spec"));
			}
		}
		return Optional.empty();
	}

	/**
	 * 검사 화면의 줄 하나: 위반과, 화면 파일이면 그 소스 보기.
	 */
	public record Finding(WorkbenchEngine.Violation violation, ScreenLink link) {
	}

	/** 검사 화면의 파일 하나 */
	public record FindingGroup(String file, List<Finding> findings) {
	}

	@GetMapping("/check")
	String check(Model model) {
		model.addAttribute("engineAvailable", this.engine.available());
		WorkbenchEngine.Check check = check();
		model.addAttribute("errors", check.errors());
		model.addAttribute("check", check);
		Map<String, List<Finding>> byFile = new LinkedHashMap<>();
		for (WorkbenchEngine.Violation violation : check.violations()) {
			byFile.computeIfAbsent((violation.file() != null) ? violation.file() : "파일을 알 수 없음", (f) -> new ArrayList<>())
				.add(new Finding(violation, link(violation).orElse(null)));
		}
		List<FindingGroup> groups = new ArrayList<>();
		byFile.forEach((file, findings) -> groups.add(new FindingGroup(file, findings)));
		model.addAttribute("groups", groups);
		return "cozy/dev/workbench-check";
	}

	@GetMapping("/map")
	String map(Model model) {
		model.addAttribute("engineAvailable", this.engine.available());
		model.addAttribute("errors", check().errors());
		model.addAttribute("map", this.engine.available() ? this.engine.describe() : null);
		Map<String, String> screens = new LinkedHashMap<>();
		this.specs.forEach((spec) -> screens.put(spec.template(), spec.screen()));
		model.addAttribute("screenNames", screens);
		return "cozy/dev/workbench-map";
	}

	private List<Line> lines(String file) {
		List<Line> lines = new ArrayList<>();
		String[] texts = this.engine.source(file).orElse("").split("\\R", -1);
		int count = (texts.length > 0 && texts[texts.length - 1].isEmpty()) ? texts.length - 1 : texts.length;
		for (int i = 0; i < count; i++) {
			lines.add(new Line(i + 1, texts[i]));
		}
		return lines;
	}
}
