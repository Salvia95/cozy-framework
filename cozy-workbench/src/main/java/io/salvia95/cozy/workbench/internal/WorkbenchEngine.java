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

	/** 명령 한 번의 결과 */
	public record Result(int code, String out, String err) {
	}

	/** {@code cozy <인자> --project <프로젝트>} */
	public Result run(String... args) {
		if (this.projectDir == null) {
			throw new IllegalStateException("[cozy] 앱 프로젝트 폴더에서 실행할 때만 cozy 명령을 쓸 수 있습니다");
		}
		List<String> all = new ArrayList<>(List.of(args));
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
