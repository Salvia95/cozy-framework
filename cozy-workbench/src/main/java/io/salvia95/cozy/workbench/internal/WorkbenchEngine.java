package io.salvia95.cozy.workbench.internal;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.salvia95.cozy.cli.CozyCli;
import io.salvia95.cozy.cli.internal.LineDiff;

/**
 * 워크벤치의 엔진. 에이전트가 쓰는 것과 같은 cozy 명령을 같은 프로세스에서 부르고 {@code --json} 출력을 읽는다. 워크벤치는 따로 앱을 읽거나 고치지
 * 않는다.
 *
 * <p>
 * 앱 프로젝트 폴더({@code pom.xml}과 {@code src/main/java}가 있는 폴더)에서 실행할 때만 쓸 수 있다. jar나 app-image로 실행하면 소스가 없으므로
 * {@link #available()}가 거짓이다.
 */
public class WorkbenchEngine {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Path projectDir;

	private final Path uiLocation;

	/**
	 * @param start 찾기 시작할 폴더. 여기서 위로 올라가며 앱 프로젝트를 찾는다
	 * @param uiLocation 실행 중인 cozy-ui의 jar나 클래스 폴더. 모르면 {@code null}(cozy 명령이 Maven 저장소에서 찾는다)
	 */
	public WorkbenchEngine(Path start, Path uiLocation) {
		this(find(start.toAbsolutePath().normalize()), uiLocation, true);
	}

	private WorkbenchEngine(Path projectDir, Path uiLocation, boolean resolved) {
		this.projectDir = projectDir;
		this.uiLocation = uiLocation;
	}

	/**
	 * 위로 찾지 않고 이 폴더만 본다. 앱 프로젝트가 아니면 쓸 수 없는 엔진이 된다.
	 */
	public static WorkbenchEngine at(Path projectDir, Path uiLocation) {
		Path dir = projectDir.toAbsolutePath().normalize();
		boolean project = Files.isRegularFile(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve("src/main/java"));
		return new WorkbenchEngine(project ? dir : null, uiLocation, true);
	}

	/**
	 * 앱 프로젝트 폴더: {@code projectDir}이 있으면 그 폴더만, 없으면 {@code start}에서 위로 올라가며 찾는다.
	 *
	 * @param projectDir 정한 폴더. 비었으면 찾는다
	 */
	public static Optional<Path> locate(String projectDir, Path start) {
		if (projectDir != null && !projectDir.isBlank()) {
			return at(Path.of(projectDir), null).projectDir();
		}
		return Optional.ofNullable(find(start.toAbsolutePath().normalize()));
	}

	private static Path find(Path start) {
		for (Path dir = start; dir != null; dir = dir.getParent()) {
			if (Files.isRegularFile(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve("src/main/java"))) {
				return dir;
			}
		}
		return null;
	}

	public boolean available() {
		return this.projectDir != null;
	}

	/** 앱 프로젝트 폴더. 없으면 빈 값 */
	public Optional<Path> projectDir() {
		return Optional.ofNullable(this.projectDir);
	}

	/** 프로젝트 기준 파일의 내용. 프로젝트가 없거나 파일이 없으면 빈 값 */
	public Optional<String> source(String file) {
		if (this.projectDir == null) {
			return Optional.empty();
		}
		Path path;
		try {
			path = this.projectDir.resolve(file).normalize();
		}
		catch (java.nio.file.InvalidPathException ex) {
			// 경로가 아닌 이름 (Windows의 : | 등)
			return Optional.empty();
		}
		if (!path.startsWith(this.projectDir) || !Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	/**
	 * 화면 스펙 파일을 바꿔 쓴다. 스펙 폴더({@code src/main/resources/specs}) 바로 아래의 이미 있는 {@code .yml} 파일만 받는다.
	 *
	 * @param name 파일 이름 ({@code memo-list.yml})
	 */
	public void writeSpec(String name, String content) {
		if (this.projectDir == null) {
			throw new IllegalStateException("[cozy] 앱 프로젝트 폴더에서 실행할 때만 스펙을 고칠 수 있습니다");
		}
		Path folder = this.projectDir.resolve(SourceScreenSpecs.FOLDER);
		Path file = folder.resolve(name).normalize();
		if (!file.getParent().equals(folder) || !name.endsWith(".yml") || !Files.isRegularFile(file)) {
			throw new IllegalArgumentException("[cozy] 스펙 파일이 아닙니다: " + name);
		}
		Path temp = file.resolveSibling(file.getFileName() + ".cozy-tmp");
		try {
			Files.writeString(temp, content, StandardCharsets.UTF_8);
			Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	/** 명령 한 번의 결과 */
	public record Result(int code, String out, String err) {
	}

	/** {@code cozy <인자> --project <프로젝트>} */
	public Result run(String... args) {
		return run(List.of(args));
	}

	/** 프로젝트가 필요 없는 명령({@code recipes}) */
	private static Result runAnywhere(String... args) {
		return capture((out, err) -> CozyCli.run(args, Path.of(""), null, out, err));
	}

	private Result run(List<String> args) {
		List<String> all = new ArrayList<>(args);
		all.addAll(projectOptions());
		return capture((out, err) -> CozyCli.run(all.toArray(String[]::new), this.projectDir, System.getenv("COZY_REPOSITORY"), out, err));
	}

	/** 모든 명령에 붙이는 {@code --project}와 {@code --ui} (실행 중인 cozy-ui) */
	private List<String> projectOptions() {
		if (this.projectDir == null) {
			throw new IllegalStateException("[cozy] 앱 프로젝트 폴더에서 실행할 때만 cozy 명령을 쓸 수 있습니다");
		}
		List<String> options = new ArrayList<>(List.of("--project", this.projectDir.toString()));
		if (this.uiLocation != null) {
			options.addAll(List.of("--ui", this.uiLocation.toString()));
		}
		return options;
	}

	/** 명령 하나를 부르고 출력과 오류를 글로 받는다 */
	private static Result capture(java.util.function.ToIntBiFunction<PrintStream, PrintStream> command) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = command.applyAsInt(new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	/**
	 * 화면 구조의 마디 하나 ({@code cozy describe --json}의 {@code screens[].outline[]}).
	 *
	 * @param kind {@code fragment}, {@code replace}, {@code insert}, {@code slot}
	 */
	public record OutlineNode(int line, int endLine, int depth, String kind, String target, String fragment, String id,
			List<String> args) {
	}

	/**
	 * 요소의 속성 하나 ({@code bindings[].alpine[]}, {@code htmx[]}).
	 *
	 * @param value 값. 값이 없는 속성({@code x-cloak})이면 {@code null}
	 */
	public record Attribute(String name, String value) {
	}

	/**
	 * Alpine이나 htmx 속성이 붙은 요소 하나 ({@code screens[].bindings[]}, {@code behaviors[].attached[]}).
	 *
	 * @param screenLine 화면 템플릿에서 이 요소가 보이는 줄 (넣은 템플릿의 요소면 넣은 줄). 화면 밖이면 0
	 * @param element 사람이 알아볼 여는 태그 ({@code <p class="form-hint">})
	 * @param component 감싸는 컴포넌트. 없으면 {@code null}
	 * @param root 컴포넌트를 붙인 요소({@code x-data})인가
	 */
	public record Binding(String template, int line, int screenLine, String element, String component, boolean root,
			List<Attribute> alpine, List<Attribute> htmx) {
	}

	/**
	 * 화면 하나 ({@code cozy describe --json}의 {@code screens[]}).
	 *
	 * @param template 뷰 이름 ({@code memo/api/list})
	 * @param spec 스펙 파일 (프로젝트 기준). 없으면 {@code null}
	 * @param bindings 화면에 보이는 요소 가운데 Alpine이나 htmx 속성이 붙은 것 (문서 순서)
	 */
	public record Screen(String template, String spec, List<OutlineNode> outline, List<Binding> bindings) {
	}

	/** 이름과 그 설명 한 줄 ({@code length}, {@code number@data-length}) */
	public record Entry(String name, String text) {
	}

	/**
	 * 모듈 동작 명세의 컴포넌트 하나 ({@code modules[].behaviors[]}).
	 *
	 * @param state 이름 → {@code 종류[=처음 값][@data-이름]}
	 * @param actions 이름 → 단계를 {@code ; }로 이은 것
	 * @param emits 화면 이벤트 → 내는 동작
	 * @param listens 화면 이벤트 → 받아서 부르는 동작
	 */
	public record Behavior(String module, String name, String purpose, List<Entry> state, List<Entry> computed, List<Entry> actions,
			List<Entry> emits, List<Entry> listens, List<Binding> attached) {
	}

	/** {@code cozy describe --json} 한 번에서 읽은 화면과 동작 */
	public record Described(List<Screen> screens, List<Behavior> behaviors) {
	}

	/** {@code cozy describe --json}이 알려 주는 앱의 화면 */
	public List<Screen> screens() {
		return described().screens();
	}

	/** {@code cozy describe --json}의 화면과 모듈의 동작 명세 */
	public Described described() {
		Result result = run("describe", "--json");
		if (result.code() != CozyCli.OK) {
			throw new IllegalStateException("[cozy] cozy describe가 실패했습니다: " + result.err().strip());
		}
		List<Screen> screens = new ArrayList<>();
		List<Behavior> behaviors = new ArrayList<>();
		for (JsonNode module : JSON.readTree(result.out()).path("modules")) {
			for (JsonNode screen : module.path("screens")) {
				List<OutlineNode> outline = new ArrayList<>();
				for (JsonNode n : screen.path("outline")) {
					List<String> args = new ArrayList<>();
					n.path("args").forEach((a) -> args.add(a.asString()));
					outline.add(new OutlineNode(n.path("line").asInt(), n.path("endLine").asInt(), n.path("depth").asInt(), n.path("kind").asString(),
							n.path("target").asString(""), text(n, "fragment"), text(n, "id"), List.copyOf(args)));
				}
				screens.add(new Screen(screen.path("template").asString(), text(screen, "spec"), List.copyOf(outline),
						bindings(screen.path("bindings"))));
			}
			for (JsonNode b : module.path("behaviors")) {
				List<Entry> state = new ArrayList<>();
				for (JsonNode s : b.path("state")) {
					state.add(new Entry(s.path("name").asString(), s.path("type").asString()
							+ ((text(s, "initial") != null) ? "=" + text(s, "initial") : "") + ((text(s, "from") != null) ? "@" + text(s, "from") : "")));
				}
				List<Entry> computed = new ArrayList<>();
				b.path("computed").forEach((c) -> computed.add(new Entry(c.path("name").asString(), c.path("expression").asString())));
				List<Entry> actions = new ArrayList<>();
				b.path("actions").forEach((a) -> actions.add(new Entry(a.path("name").asString(), String.join("; ", strings(a.path("steps"))))));
				List<Entry> emits = new ArrayList<>();
				b.path("emits").forEach((e) -> emits.add(new Entry(e.path("event").asString(), e.path("action").asString())));
				List<Entry> listens = new ArrayList<>();
				b.path("listens").forEach((e) -> listens.add(new Entry(e.path("event").asString(), e.path("action").asString())));
				behaviors.add(new Behavior(module.path("name").asString(), b.path("name").asString(), b.path("purpose").asString(),
						List.copyOf(state), List.copyOf(computed), List.copyOf(actions), List.copyOf(emits), List.copyOf(listens),
						bindings(b.path("attached"))));
			}
		}
		return new Described(List.copyOf(screens), List.copyOf(behaviors));
	}

	private static List<Binding> bindings(JsonNode array) {
		List<Binding> bindings = new ArrayList<>();
		for (JsonNode b : array) {
			bindings.add(new Binding(b.path("template").asString(), b.path("line").asInt(), b.path("screenLine").asInt(0),
					b.path("element").asString(""), text(b, "component"), b.path("root").asBoolean(), attributes(b.path("alpine")),
					attributes(b.path("htmx"))));
		}
		return List.copyOf(bindings);
	}

	private static List<Attribute> attributes(JsonNode array) {
		List<Attribute> attributes = new ArrayList<>();
		array.forEach((a) -> attributes.add(new Attribute(a.path("attribute").asString(), text(a, "value"))));
		return List.copyOf(attributes);
	}

	/**
	 * 위반 하나 ({@code cozy check --json}의 {@code violations[]}).
	 *
	 * @param id 규칙이나 검사 ({@code R3}, {@code C9})
	 * @param error 오류인가 (아니면 경고)
	 * @param file 프로젝트 기준 파일. 모르면 {@code null}
	 * @param line 줄. 모르면 0
	 * @param target 문제가 된 것
	 * @param message 고치는 법
	 * @param command 고치는 명령. 없으면 {@code null}
	 */
	public record Violation(String id, boolean error, String file, int line, String target, String message, String command) {

		/** 규칙의 이름. 모르는 규칙이면 빈 문자열 */
		public String name() {
			return io.salvia95.cozy.rules.RuleNames.of(this.id);
		}
	}

	/**
	 * {@code cozy check}의 결과.
	 *
	 * @param failure 검사하지 못한 이유. 검사했으면 {@code null}
	 */
	public record Check(long errors, long warnings, List<Violation> violations, String failure) {

		public boolean clean() {
			return this.failure == null && this.errors == 0 && this.warnings == 0;
		}
	}

	/** {@code cozy check --json}. 판단할 수 없으면(종료 코드 2) 이유를 담아 돌려준다 */
	public Check check() {
		Result result = run("check", "--json");
		if (result.code() == CozyCli.UNUSABLE) {
			return new Check(0, 0, List.of(), result.err().strip().replaceFirst("^\\[cozy\\] ", ""));
		}
		JsonNode root = JSON.readTree(result.out());
		List<Violation> violations = new ArrayList<>();
		for (JsonNode v : root.path("violations")) {
			violations.add(new Violation(v.path("id").asString(), "error".equals(v.path("severity").asString()), text(v, "file"),
					v.path("line").asInt(0), text(v, "target"), v.path("message").asString(""), text(v, "command")));
		}
		return new Check(root.path("errors").asLong(), root.path("warnings").asLong(), List.copyOf(violations), null);
	}

	/**
	 * 레시피의 인자 하나 ({@code cozy recipes --json}의 {@code params[]}).
	 *
	 * @param option 옵션({@code --이름 값})인가. 아니면 위치 인자다
	 * @param choiceLabels 고를 값 → 사람이 읽는 이름
	 * @param from 앱에서 가져오는 선택지. 없으면 {@code null}
	 * @param separator 여럿을 고를 때 사이에 넣는 글자. 하나만 고르면 {@code null}
	 * @param parts 값의 구성. 통째로 적는 값이면 빈 목록
	 * @param strict {@code from}의 값만 받는가. 거짓이면 앱의 값은 제안이고 새 값도 받는다
	 */
	public record Param(String name, String label, boolean option, boolean required, boolean repeatable, List<String> choices,
			String example, java.util.Map<String, String> choiceLabels, String from, String separator, List<Part> parts,
			boolean strict) {

		/** 폼의 필드 이름 */
		public String field() {
			return (this.option ? "opt." : "arg.") + this.name;
		}
	}

	/**
	 * 값의 구성 요소 하나 ({@code params[].parts[]}).
	 *
	 * @param flag 켜면 앞 요소 뒤에 붙는 글자({@code !}). 플래그가 아니면 {@code null}
	 * @param strict {@code from}의 값만 받는가. 거짓이면 그 값은 제안이고 다른 글도 받는다(동작의 식)
	 */
	public record Part(String name, String label, boolean required, List<String> choices, java.util.Map<String, String> choiceLabels,
			String from, String flag, String example, boolean strict) {
	}

	/**
	 * 동작 명세의 낱말 하나 ({@code recipes[].vocabulary[]}).
	 *
	 * @param kind {@code type}(상태의 종류), {@code event}(이벤트 값), {@code function}(계산 함수), {@code step}(동작 단계)
	 * @param shape 적는 모양 ({@code gt(a, b)})
	 */
	public record Word(String kind, String name, String shape, String text) {
	}

	/**
	 * 레시피 하나 ({@code cozy recipes --json}의 {@code recipes[]}).
	 *
	 * @param title 목록에 보일 제목 ({@code CRUD 화면})
	 * @param group 목록에서 묶는 분류 ({@code 화면})
	 * @param command {@code cozy} 뒤에 오는 동작 ({@code [add, crud]})
	 * @param vocabulary 동작 명세의 낱말. 동작 레시피가 아니면 빈 목록
	 */
	public record Recipe(String name, int stage, String title, String group, String usage, String does, String slots,
			List<String> command, List<Param> params, List<Word> vocabulary) {

		/** 화면에 보일 명령 ({@code cozy add crud}) */
		public String commandText() {
			return "cozy " + String.join(" ", this.command);
		}

		/** 화면에 보일 미구현 항목. 레시피는 빈칸이 없으면 {@code none …}이라고 적는다 */
		public String slotsText() {
			return this.slots.startsWith("none") ? "없음" : this.slots;
		}
	}

	/** {@code cozy recipes --json}. 쓸 수 있는 레시피만 */
	public List<Recipe> recipes() {
		Result result = runAnywhere("recipes", "--json");
		if (result.code() != CozyCli.OK) {
			throw new IllegalStateException("[cozy] cozy recipes가 실패했습니다: " + result.err().strip());
		}
		List<Recipe> recipes = new ArrayList<>();
		for (JsonNode r : JSON.readTree(result.out()).path("recipes")) {
			if (!r.path("available").asBoolean()) {
				continue;
			}
			List<Param> params = new ArrayList<>();
			for (JsonNode p : r.path("params")) {
				List<Part> parts = new ArrayList<>();
				for (JsonNode part : p.path("parts")) {
					parts.add(new Part(part.path("name").asString(), part.path("label").asString(), part.path("required").asBoolean(),
							strings(part.path("choices")), labels(part.path("choiceLabels")), text(part, "from"), text(part, "flag"),
							part.path("example").asString(""), part.path("strict").asBoolean(true)));
				}
				params.add(new Param(p.path("name").asString(), p.path("label").asString(), p.path("option").asBoolean(),
						p.path("required").asBoolean(), p.path("repeatable").asBoolean(), strings(p.path("choices")),
						p.path("example").asString(""), labels(p.path("choiceLabels")), text(p, "from"), text(p, "separator"),
						List.copyOf(parts), p.path("strict").asBoolean(true)));
			}
			List<Word> words = new ArrayList<>();
			for (JsonNode w : r.path("vocabulary")) {
				words.add(new Word(w.path("kind").asString(), w.path("name").asString(), w.path("shape").asString(), w.path("text").asString("")));
			}
			recipes.add(new Recipe(r.path("name").asString(), r.path("stage").asInt(), r.path("title").asString(),
					r.path("group").asString(), r.path("usage").asString(),
					r.path("does").asString(), r.path("slots").asString(), strings(r.path("command")), List.copyOf(params),
					List.copyOf(words)));
		}
		return recipes;
	}

	/** 레시피의 분류. 목록을 이 순서로 묶는다 ({@code cozy recipes --json}의 {@code groups}) */
	public List<String> recipeGroups() {
		Result result = runAnywhere("recipes", "--json");
		if (result.code() != CozyCli.OK) {
			throw new IllegalStateException("[cozy] cozy recipes가 실패했습니다: " + result.err().strip());
		}
		return strings(JSON.readTree(result.out()).path("groups"));
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach((v) -> values.add(v.asString()));
		return List.copyOf(values);
	}

	private static java.util.Map<String, String> labels(JsonNode object) {
		java.util.Map<String, String> labels = new java.util.LinkedHashMap<>();
		object.properties().forEach((e) -> labels.put(e.getKey(), e.getValue().asString()));
		return java.util.Collections.unmodifiableMap(labels);
	}

	/**
	 * 계획의 파일 하나.
	 *
	 * @param action {@code create}, {@code edit}, {@code delete}, {@code unchanged}
	 * @param diff 지금 파일과 바뀐 뒤의 글을 견준 덩어리 (계획일 때만. 적용한 뒤나 지우는 파일이면 빈 목록)
	 * @param diffOmitted 파일이 커서 계획이 바뀐 뒤의 글을 싣지 않았는가
	 */
	public record Change(String action, String file, String detail, List<LineDiff.Hunk> diff, boolean diffOmitted) {

		public Change(String action, String file, String detail) {
			this(action, file, detail, List.of(), false);
		}

		/** 더한 줄 수 */
		public long added() {
			return this.diff.stream().flatMap((h) -> h.lines().stream()).filter(LineDiff.Line::added).count();
		}

		/** 지운 줄 수 */
		public long removed() {
			return this.diff.stream().flatMap((h) -> h.lines().stream()).filter(LineDiff.Line::removed).count();
		}

		/**
		 * 적용한 뒤 새로고침만으로 보이는가. 화면 명세와 동작 명세는 워크벤치가 소스에서 읽는다. 템플릿과 {@code static/}은 dev에서 소스를 먼저 읽을 때만
		 * 그렇고({@code cozy.dev.workbench.live-resources}), 지운 것은 빌드 결과에 남으므로 아니다. 그 밖(Java, SQL, 설정, 테스트)은 다시 빌드하고
		 * 실행해야 한다.
		 *
		 * @param live 앱이 템플릿과 {@code static/}을 소스 폴더에서 읽는가
		 */
		public boolean immediate(boolean live) {
			if (this.file.startsWith("src/main/resources/specs/") || this.file.startsWith("src/main/resources/behaviors/")) {
				return true;
			}
			// 지운 템플릿과 정적 파일은 빌드 결과의 것이 남아 계속 보인다
			return live && !this.action.equals("delete")
					&& (this.file.startsWith("src/main/resources/templates/") || this.file.startsWith("src/main/resources/static/"));
		}

		/** 화면에 보일 동작 이름 */
		public String label() {
			return switch (this.action) {
				case "create" -> "생성";
				case "edit" -> "수정";
				case "delete" -> "삭제";
				default -> "변경 없음";
			};
		}
	}

	/** 채울 빈칸 하나 */
	public record Slot(String slot, String file, int line, String what) {
	}

	/**
	 * 명령이 고치지 못해 손으로 할 일 하나.
	 *
	 * @param snippet 넣을 내용
	 */
	public record Manual(String file, String what, String snippet) {
	}

	/**
	 * 레시피를 실행한 결과.
	 *
	 * @param applied 파일을 바꿨는가
	 * @param manual 명령이 고치지 못해 손으로 할 일
	 * @param error 명령이 거절한 이유. 거절하지 않았으면 {@code null}
	 */
	public record Outcome(boolean applied, List<Change> changes, List<Slot> slots, List<Manual> manual, String next, String error) {

		public boolean changesNothing() {
			return this.error == null && this.changes.stream().allMatch((c) -> c.action().equals("unchanged"));
		}

		/**
		 * 바뀐 파일이 언제 보이는지 한 줄. 바뀐 파일이 없으면 {@code null}.
		 *
		 * @param live 앱이 템플릿과 {@code static/}을 소스 폴더에서 읽는가
		 */
		public String visibility(boolean live) {
			List<Change> changed = this.changes.stream().filter((c) -> !c.action().equals("unchanged")).toList();
			if (this.error != null || changed.isEmpty()) {
				return null;
			}
			long now = changed.stream().filter((c) -> c.immediate(live)).count();
			long later = changed.size() - now;
			if (later == 0) {
				return "바뀐 파일은 모두 새로고침하면 미리보기와 화면에 보입니다.";
			}
			if (now == 0) {
				return "바뀐 파일은 앱을 다시 빌드하고 실행한 뒤 보입니다.";
			}
			return "바뀐 파일 " + now + "개는 새로고침하면 보이고, " + later + "개는 앱을 다시 빌드하고 실행한 뒤 보입니다.";
		}
	}

	/**
	 * 레시피를 실행한다. 인자는 배열 그대로 넘긴다(셸을 거치지 않는다).
	 *
	 * @param args {@code cozy} 뒤의 인자 ({@code add crud memo --entity Memo …})
	 * @param dryRun 계획만 세우고 쓰지 않는다
	 */
	public Outcome execute(List<String> args, boolean dryRun) {
		List<String> all = new ArrayList<>(args);
		all.add("--json");
		if (dryRun) {
			all.add("--dry-run");
		}
		return outcome(run(all), this::current);
	}

	/**
	 * 화면 안의 동작 명령 여럿을 한 계획으로 실행한다({@link CozyCli#runTogether}). 하나라도 거절하면 아무 파일도 쓰지 않는다. 같은 명령을 터미널에서
	 * 차례로 실행한 것과 같은 파일이 된다.
	 *
	 * @param commands 명령마다 {@code cozy} 뒤의 인자
	 */
	public Outcome executeTogether(List<List<String>> commands, boolean dryRun) {
		List<String> options = new ArrayList<>(List.of("--json"));
		options.addAll(projectOptions());
		if (dryRun) {
			options.add("--dry-run");
		}
		return outcome(capture((out, err) -> CozyCli.runTogether(commands, options, this.projectDir, System.getenv("COZY_REPOSITORY"), out, err)),
				this::current);
	}

	/** 프로젝트 기준 파일의 지금 글. 없으면 빈 문자열 */
	private String current(String file) {
		return source(file).orElse("");
	}

	/** @param current 파일의 지금 글 (계획이 바뀐 뒤의 글과 견준다) */
	static Outcome outcome(Result result, java.util.function.Function<String, String> current) {
		if (result.code() != CozyCli.OK) {
			return new Outcome(false, List.of(), List.of(), List.of(), "", result.err().strip().replaceFirst("^\\[cozy\\] ", ""));
		}
		JsonNode root = JSON.readTree(result.out());
		List<Change> changes = new ArrayList<>();
		for (JsonNode c : root.path("plan")) {
			String after = text(c, "after");
			List<LineDiff.Hunk> diff = (after != null) ? LineDiff.of(current.apply(c.path("file").asString()), after) : List.of();
			changes.add(new Change(c.path("action").asString(), c.path("file").asString(), text(c, "detail"), diff,
					c.path("afterOmitted").asBoolean(false)));
		}
		List<Slot> slots = new ArrayList<>();
		for (JsonNode s : root.path("slots")) {
			slots.add(new Slot(s.path("slot").asString(), s.path("file").asString(), s.path("line").asInt(),
					s.path("what").asString("")));
		}
		List<Manual> manual = new ArrayList<>();
		for (JsonNode m : root.path("manual")) {
			manual.add(new Manual(m.path("file").asString(), m.path("what").asString(""), m.path("snippet").asString("")));
		}
		return new Outcome(root.path("applied").asBoolean(), List.copyOf(changes), List.copyOf(slots), List.copyOf(manual),
				root.path("next").asString(""), null);
	}

	/**
	 * {@code cozy describe --json} 전체: app, modules, events, slots, problems. 화면이 모듈마다 이벤트를 보이도록 앱의 {@code events[]}를
	 * 주인 모듈의 {@code events}에도 싣는다.
	 */
	@SuppressWarnings("unchecked")
	public java.util.Map<String, Object> describe() {
		Result result = run("describe", "--json");
		if (result.code() != CozyCli.OK) {
			throw new IllegalStateException("[cozy] cozy describe가 실패했습니다: " + result.err().strip());
		}
		return withModuleEvents(JSON.readValue(result.out(), java.util.Map.class));
	}

	/** 앱의 {@code events[]}를 {@code owner}가 같은 모듈의 {@code events}에 싣는다. 주인이 없는 이벤트는 싣지 않는다 */
	@SuppressWarnings("unchecked")
	static java.util.Map<String, Object> withModuleEvents(java.util.Map<String, Object> described) {
		if (!(described.get("modules") instanceof List<?> modules)) {
			return described;
		}
		List<?> events = (described.get("events") instanceof List<?> list) ? list : List.of();
		for (Object item : modules) {
			if (item instanceof java.util.Map<?, ?> raw) {
				java.util.Map<String, Object> module = (java.util.Map<String, Object>) raw;
				module.put("events", events.stream()
					.filter((e) -> e instanceof java.util.Map<?, ?> m && String.valueOf(module.get("name")).equals(String.valueOf(m.get("owner"))))
					.toList());
			}
		}
		return described;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return (value.isMissingNode() || value.isNull()) ? null : value.asString();
	}
}
