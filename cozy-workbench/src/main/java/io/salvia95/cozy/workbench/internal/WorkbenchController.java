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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.salvia95.cozy.ui.CozyFlash;
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

	static final List<String> VIEWS = List.of("preview", "fixture", "template", "spec");

	private final FragmentManifest manifest;

	private final SourceScreenSpecs screenSpecs;

	private final WorkbenchEngine engine;

	public WorkbenchController(FragmentManifest manifest, SourceScreenSpecs specs, WorkbenchEngine engine) {
		this.manifest = manifest;
		this.screenSpecs = specs;
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
		String sourceFile = switch (view) {
			case "template" -> "src/main/resources/" + templateFile;
			case "spec" -> specFile;
			default -> null;
		};
		model.addAttribute("sourceFile", sourceFile);
		model.addAttribute("sourceLines", (sourceFile != null) ? lines(sourceFile) : List.of());
		return "cozy/dev/workbench";
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

	// ── 만들기: 폼 → cozy 명령 → 계획 → 적용 ───────────────────────────────

	/** 계획과 적용 */
	static final List<String> STEPS = List.of("plan", "apply");

	@GetMapping("/commands")
	String commands(@RequestParam(required = false) String recipe, Model model) {
		commandModel(recipe, Map.of(), model);
		return "cozy/dev/workbench-commands";
	}

	/**
	 * 폼의 값으로 cozy 명령을 만들어 계획하거나({@code step=plan}) 적용한다({@code step=apply}). 인자는 폼의 값뿐이고, 쓰는 경로는 cozy 명령의
	 * 계획이 정한다.
	 */
	@PostMapping("/commands")
	String execute(@RequestParam String recipe, @RequestParam String step, @RequestParam MultiValueMap<String, String> form,
			HttpServletRequest request, Model model) {
		if (!STEPS.contains(step)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[cozy] step은 " + STEPS + " 가운데 하나입니다: " + step);
		}
		requireSameOrigin(request);
		if (!this.engine.available()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "[cozy] 앱 프로젝트 폴더에서 실행할 때만 cozy 명령을 쓸 수 있습니다");
		}
		Map<String, String> values = new LinkedHashMap<>();
		form.forEach((name, list) -> {
			if (name.startsWith("arg.") || name.startsWith("opt.")) {
				values.put(name, list.getFirst());
			}
		});
		WorkbenchEngine.Recipe selected = commandModel(recipe, values, model);
		List<String> missing = new ArrayList<>();
		List<String> args = new ArrayList<>(selected.command());
		for (WorkbenchEngine.Param param : selected.params()) {
			List<String> given = values.getOrDefault(param.field(), "").lines().map(String::strip).filter((v) -> !v.isEmpty()).toList();
			if (given.isEmpty()) {
				if (param.required()) {
					missing.add(param.label());
				}
				continue;
			}
			for (String value : param.repeatable() ? given : List.of(String.join(" ", given))) {
				if (param.option()) {
					args.add("--" + param.name());
				}
				args.add(value);
			}
		}
		model.addAttribute("missing", missing);
		if (!missing.isEmpty()) {
			return "cozy/dev/workbench-commands";
		}
		boolean dryRun = step.equals("plan");
		WorkbenchEngine.Outcome outcome = this.engine.execute(args, dryRun);
		model.addAttribute("commandLine", commandLine(args));
		model.addAttribute("outcome", outcome);
		model.addAttribute("dryRun", dryRun);
		model.addAttribute("hasDetails", outcome.changes().stream().anyMatch((c) -> c.detail() != null && !c.detail().isBlank()));
		model.addAttribute("changed", outcome.changes().stream().filter((c) -> !c.action().equals("unchanged")).count());
		model.addAttribute("needsRestart", outcome.applied()
				&& outcome.changes().stream().anyMatch((c) -> !c.action().equals("unchanged")));
		return "cozy/dev/workbench-commands";
	}

	private WorkbenchEngine.Recipe commandModel(String recipe, Map<String, String> values, Model model) {
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
		model.addAttribute("values", values);
		model.addAttribute("missing", List.of());
		List<Object> modules = new ArrayList<>();
		if (this.engine.available() && this.engine.describe().get("modules") instanceof List<?> described) {
			for (Object module : described) {
				if (module instanceof Map<?, ?> m) {
					modules.add(m.get("name"));
				}
			}
		}
		model.addAttribute("modules", modules);
		return selected;
	}

	/** 터미널에 붙여 넣을 수 있는 명령. 공백이나 따옴표가 있는 인자는 큰따옴표로 묶는다 */
	static String commandLine(List<String> args) {
		StringBuilder line = new StringBuilder("cozy");
		for (String arg : args) {
			boolean quote = arg.isEmpty() || arg.chars().anyMatch((c) -> Character.isWhitespace(c) || c == '"');
			line.append(' ').append(quote ? '"' + arg.replace("\\", "\\\\").replace("\"", "\\\"") + '"' : arg);
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

	/**
	 * 모듈 화면: 의존성 다이어그램(또는 표)과 선택한 모듈의 속성.
	 *
	 * @param module 선택한 모듈. 없으면 앱의 정보를 보인다
	 * @param view {@code diagram} 또는 {@code table}
	 */
	@GetMapping("/map")
	@SuppressWarnings("unchecked")
	String map(@RequestParam(required = false) String module, @RequestParam(defaultValue = "diagram") String view, Model model) {
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
		if (selected != null && map.get("slots") instanceof List<?> slots) {
			// 미구현 항목 가운데 이 모듈의 폴더에 있는 것
			String folder = "/" + module + "/";
			model.addAttribute("selectedSlots",
					slots.stream().filter((slot) -> String.valueOf(((Map<?, ?>) slot).get("file")).contains(folder)).count());
		}
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
