package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.salvia95.cozy.ui.CozyFlash;
import io.salvia95.cozy.ui.CozyIcons;
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
 * 빈칸)를 보인다. 화면 목록에는 화면마다 위반 수가 붙는다. {@code /commands}는 {@code cozy recipes}의 레시피마다 폼을 보이고, 폼의 값으로 명령을
 * 만들어 계획을 보인 뒤 적용한다. 쓰는 것은 cozy 명령이다.
 *
 * <p>
 * {@code view=fixture}는 화면 스펙의 상태 값을 모델 타입에 맞춰 속성 표와 그리드로 보이고({@link FixtureForm}), {@code /fixture}가 고친 값을
 * 저장한다({@link FixtureEditor}). 워크벤치가 직접 쓰는 파일은
 * 스펙 YAML뿐이다.
 */
@Controller
@RequestMapping(WorkbenchController.PATH)
public class WorkbenchController {

	public static final String PATH = "/cozy/dev/workbench";

	/** 미리보기 폭. {@code full}은 칸 전체 */
	static final List<String> WIDTHS = List.of("full", "1024", "768", "390");

	static final List<String> VIEWS = List.of("preview", "fixture", "template", "spec", "behavior", "script");

	/** 상태 비교에서 한 번에 고를 수 있는 상태 수. 상태마다 미리보기를 하나씩 그린다 */
	static final int COMPARE_LIMIT = 4;

	private final FragmentManifest manifest;

	private final SourceScreenSpecs screenSpecs;

	private final WorkbenchEngine engine;

	/** cozy-ui의 프레임워크 컴포넌트 명세 (실행 중에는 바뀌지 않는다) */
	private final io.salvia95.cozy.cli.internal.behavior.FrameworkBehaviors framework = BehaviorViews.framework();

	/** 앱이 템플릿과 {@code static/}을 소스 폴더에서 읽는가 (적용한 것이 새로고침만으로 보이는가) */
	private final boolean live;

	public WorkbenchController(FragmentManifest manifest, SourceScreenSpecs specs, WorkbenchEngine engine) {
		this(manifest, specs, engine, false);
	}

	public WorkbenchController(FragmentManifest manifest, SourceScreenSpecs specs, WorkbenchEngine engine, boolean live) {
		this.manifest = manifest;
		this.screenSpecs = specs;
		this.engine = engine;
		this.live = live;
	}

	/** 화면마다: 바뀐 파일이 언제 보이는지 알리는 데 쓴다 */
	@org.springframework.web.bind.annotation.ModelAttribute("liveResources")
	boolean liveResources() {
		return this.live;
	}

	/** 번호가 붙은 소스 한 줄 */
	public record Line(int number, String text) {
	}

	/** 워크벤치 화면의 Alpine 컴포넌트. 레이아웃이 Alpine보다 먼저 싣는다 */
	@ModelAttribute("cozyScripts")
	List<String> scripts() {
		return List.of("/cozy/workbench.js");
	}

	/**
	 * 화면을 고르지 않았으면({@code spec}이 없으면) 개요, 골랐으면 그 화면의 보기.
	 *
	 * @param compare 참이면 상태 비교 창을 열어 둔다 (개요의 "상태 비교")
	 * @param node 매개변수를 고친 뒤 다시 고를 구조의 줄 (없으면 null)
	 */
	@GetMapping
	String workbench(@RequestParam(required = false) String spec, @RequestParam(required = false) String state,
			@RequestParam(defaultValue = "full") String w, @RequestParam(defaultValue = "preview") String view,
			@RequestParam(defaultValue = "false") boolean compare, @RequestParam(required = false) Integer node,
			@RequestParam(required = false) String module, @RequestParam(required = false) String template,
			@RequestParam(required = false) String at, @RequestParam(required = false) String component, Model model) {
		if (spec == null) {
			return overview(model);
		}
		model.addAttribute("attachAt", at);
		model.addAttribute("attachComponent", component);
		model.addAttribute("compareOpen", compare);
		model.addAttribute("node", node);
		model.addAttribute("sourceModule", module);
		model.addAttribute("sourceTemplate", template);
		return screen(spec, state, w, view, model);
	}

	/** 메뉴의 "화면": 첫 화면으로 간다. 화면이 없으면 빈 화면 보기 */
	@GetMapping("/screens")
	String screens(Model model) {
		List<ScreenSpec> specs = this.screenSpecs.all();
		if (specs.isEmpty()) {
			return screen(null, null, "full", "preview", model);
		}
		return "redirect:" + org.springframework.web.util.UriComponentsBuilder.fromPath(PATH)
			.queryParam("spec", specs.getFirst().screen())
			.build()
			.encode()
			.toUriString();
	}

	private String screen(String spec, String state, String w, String view, Model model) {
		if (!WIDTHS.contains(w)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] w는 " + WIDTHS + " 가운데 하나입니다: " + w);
		}
		if (!VIEWS.contains(view)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] view는 " + VIEWS + " 가운데 하나입니다: " + view);
		}
		List<ScreenSpec> specs = this.screenSpecs.all();
		model.addAttribute("specs", specs);
		Map<String, List<ScreenSpec>> groups = new LinkedHashMap<>();
		specs.forEach((s) -> groups.computeIfAbsent(module(s), (m) -> new ArrayList<>()).add(s));
		model.addAttribute("screenGroups", groups);
		model.addAttribute("specProblem", this.screenSpecs.problem());
		model.addAttribute("engineAvailable", this.engine.available());
		WorkbenchEngine.Check check = check();
		model.addAttribute("errors", check.errors());
		model.addAttribute("violationCounts", violationCounts(check));
		model.addAttribute("widths", WIDTHS);
		model.addAttribute("w", w);
		model.addAttribute("view", view);
		// 주소 뒤에 붙일 view: 기본값이면 붙이지 않는다
		model.addAttribute("fixtureSuffix", view.equals("fixture") ? "&view=fixture" : "");
		model.addAttribute("centerSuffix", (view.equals("fixture") || view.equals("preview")) ? "" : "&view=" + view);
		if (specs.isEmpty()) {
			return "cozy/dev/workbench";
		}
		ScreenSpec screen = (spec == null) ? specs.getFirst()
				: specs.stream()
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
		model.addAttribute("stateLabels", WorkbenchTerms.states(screen.states().keySet()));
		model.addAttribute("compareLimit", COMPARE_LIMIT);
		model.addAttribute("compared", compared(screen.states().keySet(), selectedState));

		String templateFile = "templates/" + screen.template() + ".html";
		List<ScreenTree.Item> tree = List.of();
		List<BehaviorViews.Element> elements = List.of();
		String specFile = null;
		// 소스 탭: 화면이 넣는 앱 템플릿, 화면이 쓰는 앱 컴포넌트의 모듈
		List<String> screenTemplates = new ArrayList<>(List.of(screen.template()));
		List<String> behaviorModules = new ArrayList<>();
		if (this.engine.available()) {
			WorkbenchEngine.Described all = this.engine.described();
			Optional<WorkbenchEngine.Screen> described = all.screens()
				.stream()
				.filter((s) -> s.template().equals(screen.template()))
				.findFirst();
			Map<String, BehaviorViews.Component> components = BehaviorViews.components(all.behaviors(), this.framework);
			if (model.getAttribute("attachAt") instanceof String at) {
				model.addAttribute("attachPlace", place(at, (String) model.getAttribute("attachComponent"), components));
			}
			if (described.isPresent()) {
				elements = BehaviorViews.elements(described.get().bindings(), components);
				tree = BehaviorViews.attach(ScreenTree.of(described.get().outline(), this.manifest, templateFile), elements);
				specFile = described.get().spec();
				described.get().outline().forEach((n) -> addTemplate(screenTemplates, n.target()));
				Map<String, String> owners = new LinkedHashMap<>();
				all.behaviors().forEach((b) -> owners.put(b.name(), b.module()));
				for (WorkbenchEngine.Binding b : described.get().bindings()) {
					addTemplate(screenTemplates, b.template());
					String owner = (b.component() == null) ? null : owners.get(b.component());
					if (owner != null && !behaviorModules.contains(owner)) {
						behaviorModules.add(owner);
					}
				}
			}
		}
		if (model.getAttribute("attachAt") != null && !this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 템플릿을 고칠 수 있습니다");
		}
		model.addAttribute("screenTemplates", screenTemplates);
		model.addAttribute("behaviorModules", behaviorModules);
		model.addAttribute("screenBehaviors", elements);
		model.addAttribute("tree", tree);
		model.addAttribute("nodes", ScreenTree.nest(tree));
		model.addAttribute("screenModule", module(screen));
		model.addAttribute("templateFile", "src/main/resources/" + templateFile);
		model.addAttribute("specFile", specFile);
		if (view.equals("fixture") && this.engine.available()) {
			String text = this.engine.source(specFile(screen.screen())).orElse(null);
			if (text != null) {
				model.addAttribute("fixtureFile", specFile(screen.screen()));
				model.addAttribute("fixtureHash", hash(text));
				@SuppressWarnings("unchecked")
				Map<String, String> draft = (model.getAttribute("fixtureDraft") instanceof Map<?, ?> d) ? (Map<String, String>) d : Map.of();
				model.addAttribute("fixtureSections", FixtureForm.of(text, selectedState, screen.model(), draft, this::docs));
			}
		}
		String shownTemplate = (model.getAttribute("sourceTemplate") instanceof String t) ? t : screen.template();
		if (view.equals("template") && !screenTemplates.contains(shownTemplate)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"[cozy] 화면 " + screen.screen() + "이 넣는 앱 템플릿이 아닙니다: " + shownTemplate + ". 있는 것: " + String.join(", ", screenTemplates));
		}
		model.addAttribute("shownTemplate", shownTemplate);
		String shownModule = null;
		if (view.equals("behavior") || view.equals("script")) {
			shownModule = (model.getAttribute("sourceModule") instanceof String m) ? m
					: behaviorModules.isEmpty() ? null : behaviorModules.getFirst();
			if (shownModule == null || !behaviorModules.contains(shownModule)) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 화면 " + screen.screen() + "이 쓰는 앱 컴포넌트의 모듈이 아닙니다: "
						+ shownModule + ". 있는 것: " + (behaviorModules.isEmpty() ? "없음" : String.join(", ", behaviorModules)));
			}
		}
		model.addAttribute("shownModule", shownModule);
		String sourceFile = switch (view) {
			case "template" -> "src/main/resources/templates/" + shownTemplate + ".html";
			case "spec" -> specFile;
			case "behavior" -> behaviorSpec(shownModule);
			case "script" -> behaviorScript(shownModule);
			default -> null;
		};
		model.addAttribute("sourceFile", sourceFile);
		model.addAttribute("sourceLines", (sourceFile != null) ? lines(sourceFile) : List.of());
		model.addAttribute("sourceNote", sourceNote(view));
		return "cozy/dev/workbench";
	}

	// ── 개요: 화면, 모듈, 검사, 미구현 항목을 한 화면에 ─────────────────────────────

	/** 개요에 보일 위반의 수. 나머지는 검사 화면에서 본다 */
	static final int OVERVIEW_VIOLATIONS = 5;

	/** 개요에 보일 미구현 항목의 수 */
	static final int OVERVIEW_SLOTS = 10;

	/** 화면이 이보다 많으면 개요의 화면 목록은 위반이 있는 모듈만 펼친다 */
	static final int OVERVIEW_OPEN_SCREENS = 12;

	/**
	 * 개요의 화면 한 줄.
	 *
	 * @param states 상태 이름 → 표시 이름
	 * @param violations 그 화면의 템플릿이나 화면 명세에 있는 위반 수
	 */
	public record ScreenRow(ScreenSpec spec, Map<String, String> states, long violations) {

		/** 화면 이름. 화면 명세에 {@code title}이 없으면 식별자 */
		public String name() {
			return this.spec.title().isEmpty() ? this.spec.screen() : this.spec.title();
		}
	}

	/**
	 * 개요의 모듈 하나와 그 화면들.
	 *
	 * @param open 처음에 펼쳐 둘까. 화면이 적으면 모두, 많으면 위반이 있는 모듈만
	 */
	public record ScreenGroup(String module, List<ScreenRow> rows, long violations, boolean open) {
	}

	/** 개요의 미구현 항목 하나 ({@code cozy describe}의 {@code slots[]}) */
	public record SlotRow(String slot, String file, int line, String what) {
	}

	/** 모듈별 화면을 묶음으로. 화면이 {@link #OVERVIEW_OPEN_SCREENS}개 이하면 모두 펼치고, 많으면 위반이 있는 모듈만 펼친다 */
	static List<ScreenGroup> screenGroups(Map<String, List<ScreenRow>> byModule) {
		boolean openAll = byModule.values().stream().mapToInt(List::size).sum() <= OVERVIEW_OPEN_SCREENS;
		List<ScreenGroup> groups = new ArrayList<>();
		byModule.forEach((module, rows) -> {
			long violations = rows.stream().mapToLong(ScreenRow::violations).sum();
			groups.add(new ScreenGroup(module, List.copyOf(rows), violations, openAll || violations > 0));
		});
		return groups;
	}

	@SuppressWarnings("unchecked")
	private String overview(Model model) {
		model.addAttribute("engineAvailable", this.engine.available());
		model.addAttribute("specProblem", this.screenSpecs.problem());
		WorkbenchEngine.Check check = check();
		model.addAttribute("errors", check.errors());
		model.addAttribute("check", check);
		Map<String, Long> counts = violationCounts(check);
		Map<String, List<ScreenRow>> screens = new LinkedHashMap<>();
		List<ScreenSpec> specs = this.screenSpecs.all();
		specs.forEach((s) -> screens.computeIfAbsent(module(s), (m) -> new ArrayList<>())
			.add(new ScreenRow(s, WorkbenchTerms.states(s.states().keySet()), counts.getOrDefault(s.screen(), 0L))));
		model.addAttribute("screenGroups", screenGroups(screens));
		model.addAttribute("screensFolded", specs.size() > OVERVIEW_OPEN_SCREENS);
		model.addAttribute("screenCount", specs.size());
		List<Finding> findings = new ArrayList<>();
		check.violations().forEach((v) -> findings.add(new Finding(v, link(v).orElse(null))));
		model.addAttribute("findings", findings.subList(0, Math.min(OVERVIEW_VIOLATIONS, findings.size())));
		model.addAttribute("findingCount", findings.size());

		Map<String, Object> described = this.engine.available() ? this.engine.describe() : null;
		model.addAttribute("app", (described != null && described.get("app") instanceof Map<?, ?> app) ? app : null);
		List<ModuleGraph.Node> modules = List.of();
		List<SlotRow> slots = new ArrayList<>();
		if (described != null) {
			List<Map<String, Object>> list = new ArrayList<>();
			if (described.get("modules") instanceof List<?> m) {
				m.forEach((item) -> list.add((Map<String, Object>) item));
			}
			modules = ModuleGraph.of(list, null).nodes().stream().sorted(java.util.Comparator.comparing(ModuleGraph.Node::name)).toList();
			if (described.get("slots") instanceof List<?> found) {
				for (Object item : found) {
					Map<?, ?> slot = (Map<?, ?>) item;
					slots.add(new SlotRow(String.valueOf(slot.get("slot")), String.valueOf(slot.get("file")),
							(slot.get("line") instanceof Number n) ? n.intValue() : 0,
							(slot.get("what") != null) ? String.valueOf(slot.get("what")) : ""));
				}
			}
		}
		model.addAttribute("modules", modules);
		model.addAttribute("slots", slots.subList(0, Math.min(OVERVIEW_SLOTS, slots.size())));
		model.addAttribute("slotCount", slots.size());
		return "cozy/dev/workbench-overview";
	}

	// ── 검색: 화면, 화면 안의 동작, 화면 이벤트, 컴포넌트, 생성 항목, 모듈 ─────────────────────────────────────

	/** 카탈로그의 묶음 이름 (패턴 카탈로그와 같다) */
	private static final Map<String, String> CATALOG_GROUPS = Map.of("pattern", "패턴", "component", "컴포넌트", "icon", "아이콘", "layout",
			"레이아웃", "spi", "앱 공개 fragment");

	@GetMapping("/search")
	@SuppressWarnings("unchecked")
	String search(@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("engineAvailable", this.engine.available());
		model.addAttribute("errors", check().errors());
		List<WorkbenchSearch.Entry> entries = new ArrayList<>();
		for (ScreenSpec spec : this.screenSpecs.all()) {
			entries.add(new WorkbenchSearch.Entry("화면", spec.title().isEmpty() ? spec.screen() : spec.title(), spec.screen(), "",
					module(spec) + " · " + spec.template(), PATH + "?spec=" + spec.screen(), List.of(spec.template())));
		}
		for (FragmentManifest.Entry fragment : this.manifest.fragments()) {
			entries.add(new WorkbenchSearch.Entry("컴포넌트", fragment.displayName(), fragment.id(),
					CATALOG_GROUPS.getOrDefault(fragment.group(), fragment.group()), fragment.purpose(),
					"/cozy/dev/catalog#fragment-" + fragment.id().replace('/', '-'), List.of(fragment.purpose(), fragment.signature())));
		}
		for (WorkbenchEngine.Recipe recipe : this.engine.recipes()) {
			entries.add(new WorkbenchSearch.Entry("생성 항목", recipe.title(), recipe.commandText(), recipe.group(), recipe.does(),
					PATH + "/commands?recipe=" + recipe.name(), List.of(recipe.does(), recipe.name())));
		}
		Map<String, Object> described = this.engine.available() ? this.engine.describe() : Map.of();
		if (this.engine.available()) {
			entries.addAll(WorkbenchSearch.behaviors(described, PATH + "/map?module="));
		}
		if (described.get("modules") instanceof List<?> modules) {
			List<Map<String, Object>> list = new ArrayList<>();
			modules.forEach((item) -> list.add((Map<String, Object>) item));
			for (ModuleGraph.Node node : ModuleGraph.of(list, null).nodes()) {
				Object pkg = list.stream().filter((m) -> node.name().equals(m.get("name"))).findFirst().map((m) -> m.get("package")).orElse("");
				entries.add(new WorkbenchSearch.Entry("모듈", node.name(), String.valueOf(pkg), "", node.summary(),
						PATH + "/map?module=" + node.name(), List.of()));
			}
		}
		String query = q.strip();
		WorkbenchSearch.Result result = WorkbenchSearch.search(query, entries);
		model.addAttribute("query", query);
		model.addAttribute("searched", !WorkbenchSearch.terms(query).isEmpty());
		model.addAttribute("results", result.groups());
		model.addAttribute("inDescriptions", result.descriptions());
		model.addAttribute("total", result.total());
		return "cozy/dev/workbench-search";
	}

	/** 상태 비교를 열 때 고른 상태: 지금 상태와 그 뒤의 상태들, 모두 {@link #COMPARE_LIMIT}개까지 */
	static List<String> compared(java.util.Set<String> states, String current) {
		List<String> picked = new ArrayList<>();
		picked.add(current);
		for (String name : states) {
			if (picked.size() < COMPARE_LIMIT && !name.equals(current)) {
				picked.add(name);
			}
		}
		return picked;
	}

	/** 화면의 모듈: 템플릿 경로의 첫 마디 */
	private static String module(ScreenSpec spec) {
		return spec.template().substring(0, spec.template().indexOf('/'));
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
		for (ScreenSpec spec : this.screenSpecs.all()) {
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
	 *
	 * @param fix 워크벤치가 계획하고 적용할 수 있는 고치는 명령({@link #CHECK_FIXES}의 키). 없으면 {@code null}
	 * @param places 고치는 화면 (C13: 붙일 화면, 붙인 곳, 동작 고치기, 화면 이벤트)
	 */
	public record Finding(WorkbenchEngine.Violation violation, ScreenLink link, String fix, List<Place> places) {

		Finding(WorkbenchEngine.Violation violation, ScreenLink link) {
			this(violation, link, fixOf(violation), placesOf(violation));
		}
	}

	/** 고치는 화면으로 가는 링크 하나 */
	public record Place(String label, String url) {
	}

	/** 검사 항목의 계획 버튼이 부르는 명령: 키 → {@code cozy} 뒤의 인자 */
	static final Map<String, List<String>> CHECK_FIXES = Map.of("sync-behaviors", List.of("sync", "behaviors"), "migrate-behaviors",
			List.of("migrate", "behaviors"));

	/** 위반의 고치는 명령이 워크벤치가 부를 수 있는 것이면 그 키 */
	static String fixOf(WorkbenchEngine.Violation violation) {
		if (violation.command() == null) {
			return null;
		}
		return CHECK_FIXES.entrySet()
			.stream()
			.filter((e) -> violation.command().equals("cozy " + String.join(" ", e.getValue())))
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}

	private static final java.util.regex.Pattern BEHAVIOR_SPEC = java.util.regex.Pattern.compile("src/main/resources/behaviors/([a-z][a-z0-9]*)\\.yml");

	/**
	 * C13 항목을 고치는 화면. 대상({@code memoPanel}, {@code memoSelection.drop}, {@code memoSelection → memo:picked},
	 * {@code memoSelection ← memo:archived})에 맞춰 고른다.
	 */
	static List<Place> placesOf(WorkbenchEngine.Violation violation) {
		if (!"C13".equals(violation.id()) || violation.target() == null || violation.file() == null) {
			return List.of();
		}
		java.util.regex.Matcher spec = BEHAVIOR_SPEC.matcher(violation.file());
		if (!spec.matches()) {
			return List.of();
		}
		String module = spec.group(1);
		String target = violation.target();
		String component = target.split("[ .]", 2)[0];
		String map = PATH + "/map?module=" + module;
		Place edit = new Place("동작 고치기", PATH + "/commands?recipe=behavior&edit=" + component);
		if (target.contains(" → ") || target.contains(" ← ")) {
			String event = target.substring(target.indexOf(' ', component.length() + 1) + 1);
			return List.of(edit, new Place("화면 이벤트", map + "#" + WorkbenchSearch.eventAnchor(event)));
		}
		if (target.contains(".")) {
			return List.of(new Place("붙인 곳", map + "#behavior-" + component), edit);
		}
		return List.of(new Place("붙일 화면", map + "#module-screens"), edit);
	}

	/**
	 * 검사 항목의 고치는 명령의 변경 파일이나 적용 결과.
	 *
	 * @param name 사람에게 보일 이름 ({@code 동작 명세에서 JS 다시 만들기})
	 */
	public record CheckFix(String fix, String name, String command, boolean dryRun, WorkbenchEngine.Outcome outcome) {
	}

	private static final Map<String, String> CHECK_FIX_NAMES = Map.of("sync-behaviors", "동작 명세에서 JS 다시 만들기", "migrate-behaviors",
			"손으로 쓴 모듈 JS를 동작 명세로 옮기기");

	/**
	 * 검사 항목의 고치는 명령({@code cozy sync behaviors}, {@code cozy migrate behaviors})을 계획하거나 적용한다. 결과는 검사 화면 위에 보인다.
	 */
	@PostMapping("/check")
	String fix(@RequestParam String fix, @RequestParam String step, HttpServletRequest request, RedirectAttributes flash) {
		if (!STEPS.contains(step)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] step은 " + STEPS + " 가운데 하나입니다: " + step);
		}
		List<String> args = CHECK_FIXES.get(fix);
		if (args == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] fix는 " + new java.util.TreeSet<>(CHECK_FIXES.keySet()) + " 가운데 하나입니다: " + fix);
		}
		requireSameOrigin(request);
		if (!this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 고칠 수 있습니다");
		}
		boolean dryRun = step.equals("plan");
		flash.addFlashAttribute("checkFix", new CheckFix(fix, CHECK_FIX_NAMES.get(fix), commandLine(args), dryRun, this.engine.execute(args, dryRun)));
		return "redirect:" + PATH + "/check";
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

	// ── 값: 화면 스펙의 상태 값을 고쳐 저장한다 ─────────────────────────────

	private static String specFile(String screen) {
		return SourceScreenSpecs.FOLDER + "/" + screen + ".yml";
	}

	/** 파일 내용의 지문. 폼을 보인 뒤 파일이 바뀌었는지 본다 */
	static String hash(String text) {
		try {
			return java.util.HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** 고칠 스펙의 지금 내용. 쓰는 요청의 조건(같은 출처, 프로젝트 폴더, 아는 스펙)을 함께 본다 */
	private String specText(String spec, HttpServletRequest request) {
		requireSameOrigin(request);
		if (!this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 스펙을 고칠 수 있습니다");
		}
		if (this.screenSpecs.all().stream().noneMatch((s) -> s.screen().equals(spec))) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 화면 스펙이 없습니다: " + spec);
		}
		return this.engine.source(specFile(spec))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 스펙 파일이 없습니다: " + specFile(spec)));
	}

	/** 바꾼 스펙이 형식과 모델 타입에 맞으면 쓴다. 맞지 않으면 이유를 돌려주고 쓰지 않는다 */
	private String writeIfValid(String spec, String changed) {
		try (java.io.InputStream in = new java.io.ByteArrayInputStream(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
			ScreenSpec.parse(in, spec + ".yml", "specs/" + spec + ".yml").verify();
		}
		catch (java.io.IOException | RuntimeException ex) {
			return readable(ex.getMessage());
		}
		this.engine.writeSpec(spec + ".yml", changed);
		return null;
	}

	/** 검증 메시지에서 사람에게 필요 없는 꼬리(변환기의 위치 정보와 참조 사슬)를 뗀다 */
	static String readable(String message) {
		String text = (message != null) ? message.replaceFirst("^\\[cozy\\] ", "") : "스펙을 읽지 못했습니다.";
		for (String tail : List.of(" at [", " (through reference chain", "\n")) {
			int at = text.indexOf(tail);
			if (at > 0) {
				text = text.substring(0, at);
			}
		}
		// 클래스 이름은 짧게: io.x.MemoViews$ListResult → ListResult
		return QUALIFIED.matcher(text).replaceAll((m) -> (m.group(2) != null) ? m.group(2) : m.group(1));
	}

	private static final java.util.regex.Pattern QUALIFIED = java.util.regex.Pattern
		.compile("\\b(?:[a-z_][A-Za-z0-9_]*\\.)+([A-Z][A-Za-z0-9_]*)(?:\\$([A-Z][A-Za-z0-9_]*))*");

	private static String fixtureView(String spec, String state, String w) {
		return fixtureView(spec, state, w, null);
	}

	/** @param anchor 화면 안의 위치 (행을 고친 그리드). 없으면 {@code null} */
	private static String fixtureView(String spec, String state, String w, String anchor) {
		return "redirect:" + org.springframework.web.util.UriComponentsBuilder.fromPath(PATH)
			.queryParam("spec", spec)
			.queryParam("state", state)
			.queryParam("w", WIDTHS.contains(w) ? w : "full")
			.queryParam("view", "fixture")
			.fragment(anchor)
			.build()
			.encode()
			.toUriString();
	}

	/** 행 작업: {@code add:result/rows}, {@code duplicate:result/rows/1}, {@code delete:result/rows/1} */
	private record RowOp(String action, String list, int index) {

		static RowOp parse(String value) {
			int colon = value.indexOf(':');
			String action = (colon < 0) ? "" : value.substring(0, colon);
			String path = (colon < 0) ? "" : value.substring(colon + 1);
			if (action.equals("add") && !path.isEmpty()) {
				return new RowOp(action, path, -1);
			}
			int slash = path.lastIndexOf('/');
			if ((action.equals("duplicate") || action.equals("delete")) && slash > 0 && path.substring(slash + 1).matches("\\d{1,6}")) {
				return new RowOp(action, path.substring(0, slash), Integer.parseInt(path.substring(slash + 1)));
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] row는 add:{목록}, duplicate:{목록}/{행}, delete:{목록}/{행}입니다: " + value);
		}

		String apply(String yaml, String state, Map<String, String> model) {
			return switch (this.action) {
				case "duplicate" -> FixtureEditor.duplicateRow(yaml, state, this.list, this.index);
				case "delete" -> FixtureEditor.deleteRow(yaml, state, this.list, this.index);
				default -> {
					FixtureForm.Block grid = FixtureForm.grid(FixtureForm.of(yaml, state, model, Map.of()), this.list)
						.orElseThrow(() -> new IllegalArgumentException(this.list + "는 목록이 아닙니다."));
					if (!grid.newItem().isEmpty()) {
						yield FixtureEditor.addRow(yaml, state, this.list, grid.newItem());
					}
					if (grid.rows().isEmpty()) {
						throw new IllegalArgumentException(this.list + "의 행 모양을 알 수 없어 행을 더할 수 없습니다. 화면 명세 파일에서 첫 행을 적으세요.");
					}
					yield FixtureEditor.duplicateRow(yaml, state, this.list, grid.rows().size() - 1);
				}
			};
		}

		String done() {
			return switch (this.action) {
				case "duplicate" -> (this.index + 1) + "행을 복제했습니다.";
				case "delete" -> (this.index + 1) + "행을 삭제했습니다.";
				default -> "행을 추가했습니다.";
			};
		}
	}

	/** DTO의 속성 설명: 프레임워크 타입은 아는 이름, 앱 타입은 소스의 Javadoc */
	private Map<String, String> docs(Class<?> type) {
		Map<String, String> framework = SourceDocs.FRAMEWORK.get(type.getName());
		if (framework != null) {
			return framework;
		}
		return this.engine.source(SourceDocs.file(type)).map((source) -> SourceDocs.of(source, type)).orElse(Map.of());
	}

	/** 화면 명세의 모델 이름 → 타입 */
	private Map<String, String> model(String spec) {
		return this.screenSpecs.all().stream().filter((s) -> s.screen().equals(spec)).findFirst().map(ScreenSpec::model).orElse(Map.of());
	}

	@PostMapping("/fixture")
	String saveFixture(@RequestParam String spec, @RequestParam String state, @RequestParam String hash,
			@RequestParam(defaultValue = "full") String w, @RequestParam(required = false) String row,
			@RequestParam MultiValueMap<String, String> form, HttpServletRequest request, RedirectAttributes flash) {
		String text = specText(spec, request);
		RowOp op = (row == null) ? null : RowOp.parse(row);
		String anchor = (op == null) ? null : FixtureForm.Block.anchor(op.list());
		Map<String, String> values = new LinkedHashMap<>();
		// 체크박스는 숨은 false 뒤에 true가 온다: 마지막 값을 쓴다
		form.forEach((name, list) -> {
			if (name.startsWith("v.")) {
				values.put(name.substring(2), list.getLast().replace("\r\n", "\n"));
			}
		});
		String problem;
		if (!hash(text).equals(hash)) {
			problem = "폼을 연 뒤에 파일이 바뀌었습니다. 지금 값을 다시 보고 고치세요.";
		}
		else {
			try {
				List<FixtureForm.Input> inputs = FixtureForm.inputs(FixtureForm.of(text, state, model(spec), Map.of()));
				problem = FixtureForm.problem(inputs, values);
				if (problem == null) {
					// 적던 값을 먼저 반영하고 행을 고친다. 행의 자리는 값을 바꿔도 그대로다
					String changed = FixtureEditor.set(text, state, FixtureForm.changes(inputs, values));
					if (op != null) {
						changed = op.apply(changed, state, model(spec));
					}
					if (changed.equals(text)) {
						flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.info("바뀐 값이 없습니다."));
						return fixtureView(spec, state, w);
					}
					problem = writeIfValid(spec, changed);
				}
			}
			catch (IllegalArgumentException ex) {
				problem = ex.getMessage();
			}
		}
		if (problem != null) {
			// 적은 값은 다음 화면에 그대로 둔다
			flash.addFlashAttribute("fixtureDraft", values);
		}
		String done = ((op == null) ? "저장했습니다. " : op.done() + " ") + specFile(spec);
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, (problem == null) ? CozyFlash.success(done)
				: CozyFlash.critical("저장하지 않았습니다. " + problem));
		return fixtureView(spec, state, w, anchor);
	}

	@PostMapping("/state")
	String duplicateState(@RequestParam String spec, @RequestParam String from, @RequestParam String name,
			@RequestParam String hash, @RequestParam(defaultValue = "full") String w, HttpServletRequest request,
			RedirectAttributes flash) {
		String text = specText(spec, request);
		String problem;
		if (!hash(text).equals(hash)) {
			problem = "폼을 연 뒤에 파일이 바뀌었습니다. 지금 값을 다시 보고 고치세요.";
		}
		else {
			try {
				problem = writeIfValid(spec, FixtureEditor.duplicate(text, from, name.strip()));
			}
			catch (IllegalArgumentException ex) {
				problem = ex.getMessage();
			}
		}
		if (problem != null) {
			flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.critical("상태를 만들지 않았습니다. " + problem));
			return fixtureView(spec, from, w);
		}
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("상태 " + name.strip() + "를 만들었습니다. 값을 고쳐 저장하세요."));
		return fixtureView(spec, name.strip(), w);
	}

	// ── 매개변수: 속성 패널에서 문자열 인자를 고친다 (cozy set arg) ───────────────

	/**
	 * 속성 패널에서 고친 매개변수 하나의 변경 파일이나 적용 결과. 화면 보기가 오른쪽 칸 위에 보인다.
	 *
	 * @param name 사람에게 보일 이름 ({@code 배지 · 색상})
	 * @param command 터미널에 붙여 넣을 수 있는 cozy 명령
	 * @param dryRun 변경 파일 확인이면 참, 적용이면 거짓
	 */
	public record ArgChange(String name, String command, boolean dryRun, WorkbenchEngine.Outcome outcome, int line, String fragment,
			int index, String from, String value) {
	}

	/**
	 * 템플릿의 fragment 호출 인자 가운데 문자열 하나를 고친다. 파일은 cozy 명령이 쓴다({@code cozy set arg}). 변경 파일을 확인하거나
	 * ({@code step=plan}) 적용하고({@code step=apply}), 같은 화면의 그 줄로 돌아간다({@code node}).
	 */
	@PostMapping("/arg")
	String setArg(@RequestParam String spec, @RequestParam String state, @RequestParam(defaultValue = "full") String w,
			@RequestParam int line, @RequestParam String fragment, @RequestParam int index, @RequestParam String from,
			@RequestParam String value, @RequestParam(defaultValue = "") String name, @RequestParam String step,
			HttpServletRequest request, RedirectAttributes flash) {
		if (!STEPS.contains(step)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] step은 " + STEPS + " 가운데 하나입니다: " + step);
		}
		requireSameOrigin(request);
		if (!this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 템플릿을 고칠 수 있습니다");
		}
		ScreenSpec screen = this.screenSpecs.all()
			.stream()
			.filter((s) -> s.screen().equals(spec))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 화면 스펙이 없습니다: " + spec));
		List<String> args = List.of("set", "arg", screen.template(), "--line", String.valueOf(line), "--fragment", fragment, "--index",
				String.valueOf(index), "--from", from, "--value", value);
		boolean dryRun = step.equals("plan");
		WorkbenchEngine.Outcome outcome = this.engine.execute(args, dryRun);
		flash.addFlashAttribute("argChange", new ArgChange(name, commandLine(args), dryRun, outcome, line, fragment, index, from, value));
		return "redirect:" + org.springframework.web.util.UriComponentsBuilder.fromPath(PATH)
			.queryParam("spec", spec)
			.queryParam("state", state)
			.queryParam("w", WIDTHS.contains(w) ? w : "full")
			.queryParam("node", line)
			.build()
			.encode()
			.toUriString();
	}

	// ── 화면 안의 동작: 속성 패널에서 이벤트와 묶인 값을 붙이고 뗀다 (cozy attach) ─────────

	/**
	 * 속성 패널에서 붙이거나 뗀 것 하나의 변경 파일이나 적용 결과. 화면 보기가 오른쪽 칸 위에 보인다. 적용 폼은 같은 값을 다시 보낸다.
	 *
	 * @param name 사람에게 보일 이름 ({@code memoBodyCount · 보임 over})
	 * @param command 터미널에 붙여 넣을 수 있는 cozy 명령
	 * @param node 돌아가서 다시 고를 구조의 줄. 없으면 {@code null}
	 */
	public record AttachChange(String name, String command, boolean dryRun, WorkbenchEngine.Outcome outcome, Integer node,
			String component, String template, int line, Map<String, String> data, String option, String key, String value,
			boolean detach, boolean fromPlace) {
	}

	/** 미리보기에서 고른 요소의 위치 ({@code memo/internal/fields:9}). 앱 템플릿만 */
	private static final java.util.regex.Pattern AT = java.util.regex.Pattern
		.compile("((?!cozy/)[a-z0-9_-]+(?:/[a-z0-9_-]+)*):([1-9][0-9]{0,5})");

	/** {@code data.이름}의 이름 */
	private static final java.util.regex.Pattern DATA_NAME = java.util.regex.Pattern.compile("[a-z][a-z0-9-]*");

	/** 고른 요소에 붙이기: 그 줄의 요소, 붙은 컴포넌트, 고를 수 있는 컴포넌트 */
	private BehaviorViews.Place place(String at, String component, Map<String, BehaviorViews.Component> components) {
		java.util.regex.Matcher m = AT.matcher(at);
		if (!m.matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] at은 앱 템플릿:줄 모양입니다 (예: memo/internal/fields:9): " + at);
		}
		String text = this.engine.source("src/main/resources/templates/" + m.group(1) + ".html")
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 없는 템플릿입니다: " + m.group(1)));
		return BehaviorViews.place(m.group(1), Integer.parseInt(m.group(2)), text, components, component);
	}

	/** 폼의 {@code data.이름}: 값을 적었고 지금 값({@code was.이름})과 다른 것만 */
	static Map<String, String> attachData(Map<String, String[]> parameters) {
		Map<String, String> data = new LinkedHashMap<>();
		parameters.forEach((name, values) -> {
			if (name.startsWith("data.") && values.length > 0) {
				String key = name.substring("data.".length());
				if (!DATA_NAME.matcher(key).matches()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] data-* 이름은 소문자와 -입니다: " + key);
				}
				String value = values[values.length - 1].strip();
				String[] was = parameters.get("was." + key);
				String before = (was != null && was.length > 0) ? was[was.length - 1].strip() : "";
				if (!value.isEmpty() && !value.equals(before)) {
					data.put(key, value);
				}
			}
		});
		return data;
	}

	private static final Map<String, String> ATTACH_LABELS = Map.of("on", "이벤트", "text", "글자", "show", "보임", "bind", "속성", "model",
			"입력값", "cloak", "준비 전 숨김", "data", "넘기는 값");

	/** 속성 패널의 붙이기·떼기를 {@code cozy attach}의 인자로 */
	static List<String> attachArgs(String component, String template, int line, String option, String key, String value, boolean detach) {
		return attachArgs(component, template, line, Map.of(), option, key, value, detach);
	}

	/**
	 * 붙이기 폼을 {@code cozy attach}의 인자로.
	 *
	 * @param data {@code data-*} 이름 → 값 ({@code --data 이름=값})
	 * @param option 붙일 것. 빈 문자열이면 {@code data-*}만 (새 자리면 {@code x-data}도)
	 */
	static List<String> attachArgs(String component, String template, int line, Map<String, String> data, String option, String key,
			String value, boolean detach) {
		List<String> args = new ArrayList<>(List.of("attach", component, "--at", template + ":" + line));
		data.forEach((name, text) -> args.addAll(List.of("--data", name + "=" + text)));
		switch (option) {
			case "" -> {
			}
			case "on" -> args.addAll(List.of("--on", key + ":" + value));
			case "bind" -> args.addAll(List.of("--bind", key + ":" + value));
			case "data" -> args.addAll(List.of("--data", key + "=" + value));
			case "cloak" -> args.add("--cloak");
			default -> args.addAll(List.of("--" + option, value));
		}
		if (detach) {
			args.add("--detach");
		}
		return args;
	}

	/**
	 * 템플릿 요소 하나에 화면 안의 동작을 붙이거나 뗀다. 파일은 cozy 명령이 쓴다({@code cozy attach}). 변경 파일을 확인하거나
	 * ({@code step=plan}) 적용하고({@code step=apply}), 같은 화면(구조의 줄 {@code node})으로 돌아간다.
	 */
	@PostMapping("/attach")
	String attach(@RequestParam String spec, @RequestParam String state, @RequestParam(defaultValue = "full") String w,
			@RequestParam(required = false) Integer node, @RequestParam String component, @RequestParam String template,
			@RequestParam int line, @RequestParam String option, @RequestParam(defaultValue = "") String key,
			@RequestParam(defaultValue = "") String value, @RequestParam(defaultValue = "false") boolean detach,
			@RequestParam(defaultValue = "false") boolean fromPlace, @RequestParam String step, HttpServletRequest request,
			RedirectAttributes flash) {
		if (!STEPS.contains(step)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] step은 " + STEPS + " 가운데 하나입니다: " + step);
		}
		if (!option.isEmpty() && !BehaviorViews.OPTIONS.contains(option)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] option은 " + BehaviorViews.OPTIONS + " 가운데 하나입니다: " + option);
		}
		requireSameOrigin(request);
		if (!this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 템플릿을 고칠 수 있습니다");
		}
		if (this.screenSpecs.all().stream().noneMatch((s) -> s.screen().equals(spec))) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 화면 스펙이 없습니다: " + spec);
		}
		Map<String, String> data = detach ? Map.of() : attachData(request.getParameterMap());
		List<String> args = attachArgs(component, template, line, data, option, key.strip(), value.strip(), detach);
		boolean dryRun = step.equals("plan");
		WorkbenchEngine.Outcome outcome = this.engine.execute(args, dryRun);
		List<String> what = new ArrayList<>();
		if (!data.isEmpty()) {
			what.add("넘기는 값 " + String.join(", ", data.keySet()));
		}
		if (!option.isEmpty()) {
			what.add(ATTACH_LABELS.get(option) + (key.isBlank() ? "" : " " + key.strip()) + (value.isBlank() ? "" : " " + value.strip()));
		}
		if (what.isEmpty()) {
			what.add("컴포넌트의 자리");
		}
		flash.addFlashAttribute("attachChange", new AttachChange(component + " · " + String.join(" · ", what) + (detach ? " 떼기" : ""),
				commandLine(args), dryRun, outcome, node, component, template, line, data, option, key.strip(), value.strip(), detach,
				fromPlace));
		org.springframework.web.util.UriComponentsBuilder back = org.springframework.web.util.UriComponentsBuilder.fromPath(PATH)
			.queryParam("spec", spec)
			.queryParam("state", state)
			.queryParam("w", WIDTHS.contains(w) ? w : "full");
		if (node != null) {
			back.queryParam("node", node);
		}
		if (fromPlace && outcome.error() != null) {
			// 거절되면 붙이기 카드를 다시 열어 고쳐 보낸다
			back.queryParam("at", template + ":" + line).queryParam("component", component);
		}
		return "redirect:" + back.build().encode().toUriString();
	}

	// ── 만들기: 폼 → cozy 명령 → 계획 → 적용 ───────────────────────────────

	/** 계획과 적용 */
	static final List<String> STEPS = List.of("plan", "apply");

	@GetMapping("/commands")
	String commands(@RequestParam(required = false) String recipe, @RequestParam(required = false) String edit, Model model) {
		WorkbenchEngine.Behavior editing = editing(recipe, edit);
		commandModel(recipe, (editing == null) ? Map.of() : BehaviorEdit.values(editing), null, editing, model);
		return "cozy/dev/workbench-commands";
	}

	/** 컴포넌트를 고치는 폼에서 하는 일: 항목 고치기, 이름 바꾸기, 지우기 */
	static final List<String> CHANGES = List.of("edit", "rename", "delete");

	/**
	 * 폼의 값으로 cozy 명령을 만들어 계획하거나({@code step=plan}) 적용한다({@code step=apply}). 인자는 폼의 값뿐이고, 쓰는 경로는 cozy 명령의
	 * 계획이 정한다. 그리드의 행 버튼({@code row=add:opt.field} 등)은 명령을 부르지 않고 행만 고친 폼을 다시 보인다.
	 *
	 * <p>
	 * 컴포넌트를 고치는 폼({@code edit}): 바꾸거나 더한 행은 {@code cozy add behavior}, 지운 행은 {@code cozy remove behavior}이고 둘은 한
	 * 계획이다({@link BehaviorEdit}). {@code change=rename}은 {@code cozy rename behavior}, {@code change=delete}는 {@code cozy remove
	 * behavior}(컴포넌트 전체)다. 폼을 연 뒤 동작 명세가 바뀌었으면({@code specHash}) 명령을 부르지 않고 다시 열라고 한다.
	 */
	@PostMapping("/commands")
	String execute(@RequestParam String recipe, @RequestParam(required = false) String step,
			@RequestParam(required = false) String row, @RequestParam(required = false) String edit,
			@RequestParam(defaultValue = "") String specHash, @RequestParam(defaultValue = "edit") String change,
			@RequestParam(defaultValue = "") String newName, @RequestParam MultiValueMap<String, String> form, HttpServletRequest request,
			Model model) {
		if (row == null && !STEPS.contains(step)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] step은 " + STEPS + " 가운데 하나입니다: " + step);
		}
		if (!CHANGES.contains(change)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] change는 " + CHANGES + " 가운데 하나입니다: " + change);
		}
		requireSameOrigin(request);
		if (row == null && !this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 cozy 명령을 쓸 수 있습니다");
		}
		WorkbenchEngine.Behavior editing = editing(recipe, edit);
		Map<String, List<String>> values = new LinkedHashMap<>();
		form.forEach((name, list) -> {
			if (name.startsWith("arg.") || name.startsWith("opt.")) {
				values.put(name, List.copyOf(list));
			}
		});
		if (editing != null) {
			// 고치는 컴포넌트는 주소가 정한다. 폼의 모듈과 동작 칸은 보이지 않는다
			BehaviorEdit.values(editing).entrySet()
				.stream()
				.filter((e) -> e.getKey().startsWith("arg."))
				.forEach((e) -> values.put(e.getKey(), e.getValue()));
		}
		CommandForm built = commandModel(recipe, values, row, editing, model);
		if (row != null) {
			model.addAttribute("specHash", specHash);
			return "cozy/dev/workbench-commands";
		}
		if (editing != null && !specHash.equals(model.getAttribute("specHash"))) {
			model.addAttribute("specHash", specHash);
			model.addAttribute("stale", true);
			return "cozy/dev/workbench-commands";
		}
		boolean dryRun = step.equals("plan");
		List<List<String>> commands;
		if (editing == null) {
			model.addAttribute("missing", built.missing());
			if (!built.missing().isEmpty()) {
				return "cozy/dev/workbench-commands";
			}
			commands = List.of(built.args());
		}
		else {
			String module = editing.module();
			String action = BehaviorEdit.action(module, editing.name());
			model.addAttribute("change", change);
			model.addAttribute("newName", newName.strip());
			commands = switch (change) {
				case "rename" -> newName.isBlank() ? List.of() : List.of(List.of("rename", "behavior", module, action, newName.strip()));
				case "delete" -> List.of(List.of("remove", "behavior", module, action));
				default -> built.missing().isEmpty() ? BehaviorEdit.commands(editing, values) : List.of();
			};
			if (change.equals("edit") && !built.missing().isEmpty()) {
				model.addAttribute("missing", built.missing());
				return "cozy/dev/workbench-commands";
			}
			if (change.equals("rename") && newName.isBlank()) {
				model.addAttribute("missing", List.of("새 이름"));
				return "cozy/dev/workbench-commands";
			}
			if (commands.isEmpty()) {
				model.addAttribute("unchanged", true);
				return "cozy/dev/workbench-commands";
			}
		}
		WorkbenchEngine.Outcome outcome = (editing == null) ? this.engine.execute(commands.getFirst(), dryRun)
				: this.engine.executeTogether(commands, dryRun);
		List<String> lines = commands.stream().map(WorkbenchController::commandLine).toList();
		model.addAttribute("commandLines", lines);
		model.addAttribute("commandText", String.join("\n", lines));
		model.addAttribute("outcome", outcome);
		model.addAttribute("dryRun", dryRun);
		Map<String, Object> described = this.engine.describe();
		model.addAttribute("changeTree", ChangeTree.of(outcome.changes(), basePackage(described), eventModules(described)));
		model.addAttribute("changed", outcome.changes().stream().filter((c) -> !c.action().equals("unchanged")).count());
		if (editing != null && outcome.applied()) {
			// 적용한 뒤에는 바뀐 명세로 폼을 다시 채운다. 지웠으면 새 동작을 만드는 폼이다
			// 새 동작 이름(body-length)은 컴포넌트의 뒷부분(BodyLength)이 된다
			String now = change.equals("rename") ? editing.module() + io.salvia95.cozy.cli.internal.recipe.Names.pascalOfKebab(newName.strip())
					: editing.name();
			WorkbenchEngine.Behavior after = change.equals("delete") ? null : behavior(now);
			commandModel(recipe, (after == null) ? Map.of() : BehaviorEdit.values(after), null, after, model);
			model.addAttribute("removedComponent", change.equals("delete") ? editing.name() : null);
		}
		return "cozy/dev/workbench-commands";
	}

	/** 주소의 {@code edit}: 고칠 컴포넌트. 없으면 {@code null} */
	private WorkbenchEngine.Behavior editing(String recipe, String edit) {
		if (edit == null) {
			return null;
		}
		if (!"behavior".equals(recipe)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] edit는 화면 동작(recipe=behavior)에만 씁니다: " + recipe);
		}
		if (!this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 동작을 고칠 수 있습니다");
		}
		WorkbenchEngine.Behavior found = behavior(edit);
		if (found == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 동작 명세에 없는 컴포넌트입니다: " + edit);
		}
		return found;
	}

	private WorkbenchEngine.Behavior behavior(String name) {
		return this.engine.described().behaviors().stream().filter((b) -> b.name().equals(name)).findFirst().orElse(null);
	}

	/**
	 * @param editing 고치는 컴포넌트. 새로 만들면 {@code null}
	 * @return 고른 레시피의 폼. 고르지 않았으면 {@code null}
	 */
	private CommandForm commandModel(String recipe, Map<String, List<String>> values, String row, WorkbenchEngine.Behavior editing,
			Model model) {
		List<WorkbenchEngine.Recipe> recipes = this.engine.recipes();
		WorkbenchEngine.Recipe selected = (recipe == null) ? null
				: recipes.stream()
					.filter((r) -> r.name().equals(recipe))
					.findFirst()
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 생성 항목이 없습니다: " + recipe));
		model.addAttribute("engineAvailable", this.engine.available());
		model.addAttribute("errors", check().errors());
		model.addAttribute("recipes", recipes);
		Map<String, List<WorkbenchEngine.Recipe>> groups = new LinkedHashMap<>();
		this.engine.recipeGroups().forEach((group) -> groups.put(group, new ArrayList<>()));
		recipes.forEach((r) -> groups.computeIfAbsent(r.group(), (group) -> new ArrayList<>()).add(r));
		groups.values().removeIf(List::isEmpty);
		model.addAttribute("recipeGroups", groups);
		model.addAttribute("selected", selected);
		model.addAttribute("missing", List.of());
		model.addAttribute("editing", editing);
		if (editing != null) {
			model.addAttribute("editAction", BehaviorEdit.action(editing.module(), editing.name()));
			model.addAttribute("specHash", hash(this.engine.source(behaviorSpec(editing.module())).orElse("")));
		}
		if (selected == null) {
			return null;
		}
		Map<String, List<WorkbenchEngine.Word>> words = new LinkedHashMap<>();
		selected.vocabulary().forEach((w) -> words.computeIfAbsent(w.kind(), (k) -> new ArrayList<>()).add(w));
		model.addAttribute("vocabulary", words);
		CommandForm built;
		try {
			built = CommandForm.of(selected, values, CommandForm.sources(this.engine.available() ? this.engine.describe() : Map.of(),
					CozyIcons.outline(), selected.vocabulary(), editing), row);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
		model.addAttribute("commandForm", built);
		model.addAttribute("iconSprite", CozyIcons.OUTLINE_SPRITE);
		return built;
	}

	private static String basePackage(Map<String, Object> described) {
		return (described.get("app") instanceof Map<?, ?> app && app.get("basePackage") != null)
				? String.valueOf(app.get("basePackage")) : "";
	}

	/** 이벤트 이름 → 모듈 */
	private static Map<String, String> eventModules(Map<String, Object> described) {
		Map<String, String> events = new LinkedHashMap<>();
		if (described.get("modules") instanceof List<?> modules) {
			for (Object item : modules) {
				if (item instanceof Map<?, ?> m && m.get("events") instanceof List<?> list) {
					for (Object event : list) {
						if (event instanceof Map<?, ?> e && e.get("type") != null) {
							events.put(String.valueOf(e.get("type")), String.valueOf(m.get("name")));
						}
					}
				}
			}
		}
		return events;
	}

	/** 터미널에 붙여 넣을 수 있는 명령. 공백이나 따옴표가 있는 인자는 큰따옴표로 묶는다 */
	static String commandLine(List<String> args) {
		StringBuilder line = new StringBuilder("cozy");
		for (String arg : args) {
			// 셸이 펼치는 글자($, `)가 있으면 작은따옴표로 (Thymeleaf 식 ${…}을 그대로 넘긴다)
			boolean expands = arg.indexOf('$') >= 0 || arg.indexOf('`') >= 0;
			boolean quote = arg.isEmpty() || expands || arg.chars().anyMatch((c) -> Character.isWhitespace(c) || c == '"');
			if (expands && arg.indexOf('\'') < 0) {
				line.append(" '").append(arg).append('\'');
			}
			else if (quote) {
				line.append(" \"")
					.append(arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`"))
					.append('"');
			}
			else {
				line.append(' ').append(arg);
			}
		}
		return line.toString();
	}

	/** 파일을 쓰는 요청은 이 앱의 화면에서 온 것만 받는다 (CSRF 토큰에 더해 Origin을 본다) */
	private static void requireSameOrigin(HttpServletRequest request) {
		String origin = request.getHeader("Origin");
		if (origin == null) {
			return;
		}
		int port = request.getServerPort();
		boolean defaultPort = (request.getScheme().equals("http") && port == 80) || (request.getScheme().equals("https") && port == 443);
		String own = request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
		if (!origin.equals(own)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "[cozy] 다른 출처의 요청입니다: " + origin);
		}
	}

	private static final List<String> MAP_VIEWS = List.of("diagram", "table");

	/** 모듈 화면에서 여는 소스: 동작 명세, 만든 JS */
	private static final List<String> SOURCES = List.of("behavior", "script");

	/**
	 * 모듈 화면: 의존성 다이어그램(또는 표)과 선택한 모듈의 속성.
	 *
	 * @param module 선택한 모듈. 없으면 앱의 정보를 보인다
	 * @param view {@code diagram} 또는 {@code table}
	 */
	@GetMapping("/map")
	@SuppressWarnings("unchecked")
	String map(@RequestParam(required = false) String module, @RequestParam(defaultValue = "diagram") String view,
			@RequestParam(required = false) String source, Model model) {
		if (!MAP_VIEWS.contains(view)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] view는 " + MAP_VIEWS + " 가운데 하나입니다: " + view);
		}
		model.addAttribute("engineAvailable", this.engine.available());
		model.addAttribute("errors", check().errors());
		model.addAttribute("view", view);
		model.addAttribute("viewSuffix", view.equals("table") ? "&view=table" : "");
		String base = PATH + "/map";
		model.addAttribute("diagramUrl", (module != null) ? base + "?module=" + module : base);
		model.addAttribute("tableUrl", base + "?view=table" + ((module != null) ? "&module=" + module : ""));
		Map<String, Object> map = this.engine.available() ? this.engine.describe() : null;
		model.addAttribute("map", map);
		Map<String, String> screens = new LinkedHashMap<>();
		this.screenSpecs.all().forEach((spec) -> screens.put(spec.template(), spec.screen()));
		model.addAttribute("screenNames", screens);
		if (map == null) {
			return "cozy/dev/workbench-map";
		}
		List<Map<String, Object>> modules = new ArrayList<>();
		if (map.get("modules") instanceof List<?> described) {
			described.forEach((m) -> modules.add((Map<String, Object>) m));
		}
		Map<String, Object> selected = (module == null) ? null
				: modules.stream()
					.filter((m) -> module.equals(m.get("name")))
					.findFirst()
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 모듈이 없습니다: " + module));
		if (ModuleGraph.EVENTS.equals(module)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"[cozy] events는 이벤트 계약 모듈이라 모듈 의존성에 그리지 않습니다. 이벤트는 주인 모듈을 선택하면 보입니다.");
		}
		if (source != null) {
			if (!SOURCES.contains(source)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] source는 " + SOURCES + " 가운데 하나입니다: " + source);
			}
			if (selected == null || !(selected.get("behaviors") instanceof List<?> declared) || declared.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "[cozy] 동작 명세가 있는 모듈을 고르세요: " + module);
			}
			String file = source.equals("behavior") ? behaviorSpec(module) : behaviorScript(module);
			model.addAttribute("source", source);
			model.addAttribute("sourceFile", file);
			model.addAttribute("sourceLines", lines(file));
			model.addAttribute("sourceNote", sourceNote(source));
		}
		ModuleGraph graph = ModuleGraph.of(modules, module);
		model.addAttribute("graph", graph);
		model.addAttribute("nodeWidth", ModuleGraph.NODE_WIDTH);
		model.addAttribute("nodeHeight", ModuleGraph.NODE_HEIGHT);
		model.addAttribute("selectedSlots", 0L);
		model.addAttribute("selected", selected);
		model.addAttribute("selectedNode", graph.selectedNode());

		// 이벤트를 받는 곳: "발행 모듈.이벤트" → "수신 모듈.리스너", 그리고 수신 모듈 → 발행 모듈 → 이벤트 이름
		Map<String, List<String>> receivers = new LinkedHashMap<>();
		Map<String, Map<String, List<String>>> listens = new LinkedHashMap<>();
		for (Map<String, Object> m : modules) {
			if (m.get("listeners") instanceof List<?> listeners) {
				for (Object item : listeners) {
					Map<?, ?> listener = (Map<?, ?>) item;
					String on = String.valueOf(listener.get("on"));
					receivers.computeIfAbsent(on, (k) -> new ArrayList<>()).add(m.get("name") + "." + listener.get("type"));
					int dot = on.indexOf('.');
					if (dot > 0) {
						listens.computeIfAbsent(String.valueOf(m.get("name")), (k) -> new LinkedHashMap<>())
							.computeIfAbsent(on.substring(0, dot), (k) -> new ArrayList<>())
							.add(on.substring(dot + 1));
					}
				}
			}
		}
		model.addAttribute("receivers", receivers);
		model.addAttribute("listens", listens);
		// 화면 안의 동작: 컴포넌트 → 붙인 화면, 그리고 이 모듈이 내거나 받는 화면 이벤트
		Map<String, java.util.Set<String>> behaviorScreens = new LinkedHashMap<>();
		for (Map<String, Object> m : modules) {
			if (m.get("screens") instanceof List<?> list) {
				for (Object s : list) {
					Map<?, ?> screen = (Map<?, ?>) s;
					if (screen.get("bindings") instanceof List<?> bindings) {
						for (Object b : bindings) {
							Object name = ((Map<?, ?>) b).get("component");
							if (name != null) {
								behaviorScreens.computeIfAbsent(String.valueOf(name), (k) -> new java.util.LinkedHashSet<>())
									.add(String.valueOf(screen.get("template")));
							}
						}
					}
				}
			}
		}
		model.addAttribute("behaviorScreens", behaviorScreens);
		model.addAttribute("behaviorLines", (module != null) ? componentLines(module) : Map.of());
		List<UiEventRow> uiEvents = new ArrayList<>();
		if (selected != null && map.get("uiEvents") instanceof List<?> events) {
			for (Object item : events) {
				Map<?, ?> event = (Map<?, ?>) item;
				List<?> emitted = (event.get("emittedBy") instanceof List<?> l) ? l : List.of();
				List<?> received = (event.get("receivedBy") instanceof List<?> l) ? l : List.of();
				boolean involved = module.equals(event.get("module")) || java.util.stream.Stream.concat(emitted.stream(), received.stream())
					.anyMatch((w) -> module.equals(((Map<?, ?>) w).get("module")) || String.valueOf(((Map<?, ?>) w).get("template")).startsWith(module + "/")
							|| String.valueOf(((Map<?, ?>) w).get("file")).contains("/" + module + "/"));
				if (involved) {
					uiEvents.add(new UiEventRow(String.valueOf(event.get("event")), emitted.stream().map((w) -> where((Map<?, ?>) w)).toList(),
							received.stream().map((w) -> where((Map<?, ?>) w)).toList()));
				}
			}
		}
		model.addAttribute("uiEvents", uiEvents);
		if (selected != null && map.get("slots") instanceof List<?> slots) {
			// 미구현 항목 가운데 이 모듈의 폴더에 있는 것
			String folder = "/" + module + "/";
			model.addAttribute("selectedSlots",
					slots.stream().filter((slot) -> String.valueOf(((Map<?, ?>) slot).get("file")).contains(folder)).count());
		}
		return "cozy/dev/workbench-map";
	}

	/**
	 * 모듈 화면의 화면 이벤트 한 줄.
	 *
	 * @param emittedBy 내는 곳 ({@code memoSelection.pick}, 서버 파일)
	 * @param receivedBy 받는 곳 ({@code memoList.clear}, {@code memo/api/list:12 (htmx)})
	 */
	public record UiEventRow(String event, List<String> emittedBy, List<String> receivedBy) {
	}

	private static String where(Map<?, ?> w) {
		return switch (String.valueOf(w.get("kind"))) {
			case "behavior" -> w.get("component") + "." + w.get("action");
			case "server" -> w.get("file") + " (서버)";
			default -> w.get("template") + ":" + w.get("line") + " (" + w.get("kind") + ")";
		};
	}

	/** 동작 명세의 컴포넌트 줄: 이름({@code memoBodyCount}) → 그 키({@code   bodyCount:})의 줄. 명세가 없으면 빈 맵 */
	Map<String, Integer> componentLines(String module) {
		Map<String, Integer> found = new LinkedHashMap<>();
		String[] lines = this.engine.source(behaviorSpec(module)).orElse("").split("\\R", -1);
		for (int i = 0; i < lines.length; i++) {
			java.util.regex.Matcher m = COMPONENT_KEY.matcher(lines[i]);
			if (m.matches()) {
				found.put(module + Character.toUpperCase(m.group(1).charAt(0)) + m.group(1).substring(1), i + 1);
			}
		}
		return found;
	}

	/** 동작 명세에서 컴포넌트를 여는 줄 ({@code components:} 바로 아래, 들여쓰기 두 칸) */
	private static final java.util.regex.Pattern COMPONENT_KEY = java.util.regex.Pattern.compile("  ([a-z][A-Za-z0-9]*):\\s*");

	/** 소스 위에 보일 안내: 동작 명세와 만든 JS는 고치는 길을 알린다 */
	static String sourceNote(String source) {
		return switch (source) {
			case "behavior" -> "동작 명세가 원본입니다. cozy add behavior가 고치고, 손으로 고쳤으면 cozy sync behaviors가 정리하고 JS를 다시 만듭니다.";
			case "script" -> "cozy가 동작 명세에서 만든 파일입니다. 손으로 고치지 않습니다(C12). 동작을 바꾸려면 동작 명세를 고칩니다.";
			default -> "";
		};
	}

	/** 모듈의 동작 명세 ({@code src/main/resources/behaviors/memo.yml}) */
	static String behaviorSpec(String module) {
		return "src/main/resources/behaviors/" + module + ".yml";
	}

	/** 동작 명세에서 만든 JS ({@code src/main/resources/static/memo/memo.js}) */
	static String behaviorScript(String module) {
		return "src/main/resources/static/" + module + "/" + module + ".js";
	}

	/** 앱 템플릿이면(프레임워크 템플릿과 이름 없는 대상은 빼고) 한 번만 더한다 */
	private static void addTemplate(List<String> templates, String template) {
		if (template != null && !template.isEmpty() && !template.startsWith("cozy/") && !templates.contains(template)) {
			templates.add(template);
		}
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
