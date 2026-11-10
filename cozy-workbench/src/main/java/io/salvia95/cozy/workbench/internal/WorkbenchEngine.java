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
		Path path = this.projectDir.resolve(file).normalize();
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
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(args, Path.of(""), null, new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	private Result run(List<String> args) {
		if (this.projectDir == null) {
			throw new IllegalStateException("[cozy] 앱 프로젝트 폴더에서 실행할 때만 cozy 명령을 쓸 수 있습니다");
		}
		List<String> all = new ArrayList<>(args);
		all.add("--project");
		all.add(this.projectDir.toString());
		if (this.uiLocation != null) {
			all.add("--ui");
			all.add(this.uiLocation.toString());
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(all.toArray(String[]::new), this.projectDir, System.getenv("COZY_REPOSITORY"),
				new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
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
	 * 화면 하나 ({@code cozy describe --json}의 {@code screens[]}).
	 *
	 * @param template 뷰 이름 ({@code memo/api/list})
	 * @param spec 스펙 파일 (프로젝트 기준). 없으면 {@code null}
	 */
	public record Screen(String template, String spec, List<OutlineNode> outline) {
	}

	/** {@code cozy describe --json}이 알려 주는 앱의 화면 */
	public List<Screen> screens() {
		Result result = run("describe", "--json");
		if (result.code() != CozyCli.OK) {
			throw new IllegalStateException("[cozy] cozy describe가 실패했습니다: " + result.err().strip());
		}
		List<Screen> screens = new ArrayList<>();
		for (JsonNode module : JSON.readTree(result.out()).path("modules")) {
			for (JsonNode screen : module.path("screens")) {
				List<OutlineNode> outline = new ArrayList<>();
				for (JsonNode n : screen.path("outline")) {
					List<String> args = new ArrayList<>();
					n.path("args").forEach((a) -> args.add(a.asString()));
					outline.add(new OutlineNode(n.path("line").asInt(), n.path("endLine").asInt(), n.path("depth").asInt(), n.path("kind").asString(),
							n.path("target").asString(""), text(n, "fragment"), text(n, "id"), List.copyOf(args)));
				}
				screens.add(new Screen(screen.path("template").asString(), text(screen, "spec"), List.copyOf(outline)));
			}
		}
		return screens;
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
	 */
	public record Param(String name, String label, boolean option, boolean required, boolean repeatable, List<String> choices,
			String example) {

		/** 폼의 필드 이름 */
		public String field() {
			return (this.option ? "opt." : "arg.") + this.name;
		}
	}

	/**
	 * 레시피 하나 ({@code cozy recipes --json}의 {@code recipes[]}).
	 *
	 * @param title 목록에 보일 제목 ({@code CRUD 화면})
	 * @param group 목록에서 묶는 분류 ({@code 화면})
	 * @param command {@code cozy} 뒤에 오는 동작 ({@code [add, crud]})
	 */
	public record Recipe(String name, int stage, String title, String group, String usage, String does, String slots,
			List<String> command, List<Param> params) {

		/** 화면에 보일 명령 ({@code cozy add crud}) */
		public String commandText() {
			return "cozy " + String.join(" ", this.command);
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
				params.add(new Param(p.path("name").asString(), p.path("label").asString(), p.path("option").asBoolean(),
						p.path("required").asBoolean(), p.path("repeatable").asBoolean(), strings(p.path("choices")),
						p.path("example").asString("")));
			}
			recipes.add(new Recipe(r.path("name").asString(), r.path("stage").asInt(), r.path("title").asString(),
					r.path("group").asString(), r.path("usage").asString(),
					r.path("does").asString(), r.path("slots").asString(), strings(r.path("command")), List.copyOf(params)));
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

	/**
	 * 계획의 파일 하나.
	 *
	 * @param action {@code create}, {@code edit}, {@code delete}, {@code unchanged}
	 */
	public record Change(String action, String file, String detail) {

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
		Result result = run(all);
		if (result.code() != CozyCli.OK) {
			return new Outcome(false, List.of(), List.of(), List.of(), "", result.err().strip().replaceFirst("^\\[cozy\\] ", ""));
		}
		JsonNode root = JSON.readTree(result.out());
		List<Change> changes = new ArrayList<>();
		for (JsonNode c : root.path("plan")) {
			changes.add(new Change(c.path("action").asString(), c.path("file").asString(), text(c, "detail")));
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

	/** {@code cozy describe --json} 전체: app, modules, slots, problems */
	@SuppressWarnings("unchecked")
	public java.util.Map<String, Object> describe() {
		Result result = run("describe", "--json");
		if (result.code() != CozyCli.OK) {
			throw new IllegalStateException("[cozy] cozy describe가 실패했습니다: " + result.err().strip());
		}
		return JSON.readValue(result.out(), java.util.Map.class);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return (value.isMissingNode() || value.isNull()) ? null : value.asString();
	}
}
