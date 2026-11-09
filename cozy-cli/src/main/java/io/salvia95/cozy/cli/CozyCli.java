package io.salvia95.cozy.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.AppResources;
import io.salvia95.cozy.cli.internal.Checks;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Describe;
import io.salvia95.cozy.cli.internal.FrameworkJar;
import io.salvia95.cozy.cli.internal.Json;
import io.salvia95.cozy.cli.internal.Recipes;
import io.salvia95.cozy.cli.internal.recipe.RecipeRunner;
import io.salvia95.cozy.rules.Violation;

/**
 * cozy 명령. 형식: {@code cozy <동작> [옵션]}. 문서: docs/conventions/cli.md
 *
 * <ul>
 * <li>{@code describe [--json]}: 앱의 지도(모듈, 의존, spi 계약, 이벤트, 화면, 공개 fragment, 빈칸)</li>
 * <li>{@code check [--json]}: 컴파일 없이 하는 검사(R1~R15, C8, C9). 최종 판단은 {@code mvn verify}</li>
 * <li>{@code recipes [--json]}: 구조를 만드는 명령 목록</li>
 * <li>{@code add <레시피>}, {@code link}, {@code enable events}: 구조를 만든다 (원자적으로 적용, {@code --dry-run})</li>
 * </ul>
 * 종료 코드: 0 이상 없음, 1 위반 있음, 2 사용법 오류이거나 판단할 수 없음.
 */
public final class CozyCli {

	public static final int OK = 0;

	public static final int VIOLATIONS = 1;

	public static final int UNUSABLE = 2;

	static final String USAGE = """
			cozy 명령. 앱 폴더(또는 그 아래)에서 실행한다.

			  cozy describe [--json]   앱의 지도: 모듈, 의존, spi 계약, 이벤트, 화면, 공개 fragment, 남은 빈칸
			  cozy check [--json]      빠른 검사: 템플릿 규칙 R1~R15, 모듈 구조 C8, 빈칸 C9 (컴파일하지 않는다)
			  cozy recipes [--json]    구조를 만드는 명령 목록 (add, link, enable)
			  cozy add <레시피> ...     구조 만들기: module, crud, screen, fragment, event, listener, migration, dto, query, command, event-version
			  cozy link <모듈> --uses <모듈>   모듈 의존 선언 (allowedDependencies)
			  cozy enable events       pom에 cozy-starter-events 더하기

			옵션
			  --project <폴더>         앱 폴더 (기본: 현재 폴더에서 위로 찾는다)
			  --repository <폴더>      cozy jar가 있는 Maven 저장소 (기본: COZY_REPOSITORY, 없으면 ~/.m2/repository)
			  --json                   기계가 읽는 출력
			  --dry-run                (add, link, enable) 바꿀 파일만 보이고 쓰지 않는다

			종료 코드: 0 이상 없음, 1 위반 있음, 2 사용법 오류이거나 판단할 수 없음
			""";

	private CozyCli() {
	}

	public static void main(String[] args) {
		PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
		PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
		System.exit(run(args, Path.of(""), System.getenv("COZY_REPOSITORY"), out, err));
	}

	/**
	 * @param workDir 현재 폴더
	 * @param repositoryEnv {@code COZY_REPOSITORY} 값 (없으면 {@code null})
	 * @return 종료 코드
	 */
	public static int run(String[] args, Path workDir, String repositoryEnv, PrintStream out, PrintStream err) {
		List<String> rest = new ArrayList<>();
		Path project = null;
		Path repository = (repositoryEnv != null && !repositoryEnv.isBlank()) ? Path.of(repositoryEnv)
				: Path.of(System.getProperty("user.home"), ".m2", "repository");
		boolean json = false;
		boolean dryRun = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--json" -> json = true;
				case "--dry-run" -> dryRun = true;
				case "--project", "--repository" -> {
					if (i + 1 >= args.length) {
						err.println("[cozy] " + args[i] + " 뒤에 폴더를 적습니다.");
						return UNUSABLE;
					}
					Path value = workDir.resolve(args[++i]);
					if (args[i - 1].equals("--project")) {
						project = value;
					}
					else {
						repository = value;
					}
				}
				default -> rest.add(args[i]);
			}
		}
		String action = rest.isEmpty() ? "help" : rest.getFirst();
		try {
			return switch (action) {
				case "describe" -> describe(project(project, workDir), json, out);
				case "check" -> check(project(project, workDir), repository, json, out);
				case "recipes" -> {
					out.print(json ? Json.write(Recipes.model()) : Recipes.text());
					yield OK;
				}
				case "add", "link", "enable" -> {
					AppProject app = project(project, workDir);
					Path repo = repository;
					RecipeRunner.run(action, rest.subList(1, rest.size()), app, () -> framework(app, repo, "아이콘 이름"), now(),
							dryRun, json, out);
					yield OK;
				}
				case "help", "--help", "-h" -> {
					out.print(USAGE);
					yield OK;
				}
				default -> {
					err.println("[cozy] 알 수 없는 동작: " + action);
					err.print(USAGE);
					yield UNUSABLE;
				}
			};
		}
		catch (CliException ex) {
			err.println("[cozy] " + ex.getMessage());
			return UNUSABLE;
		}
	}

	/** 앱이 쓰는 버전의 cozy-ui jar. 없으면 판단할 수 없으므로 실패한다 */
	private static FrameworkJar framework(AppProject project, Path repository, String what) {
		if (project.cozyVersion() == null) {
			throw new CliException("앱이 cozy-parent를 상속하지 않아 cozy 버전을 알 수 없습니다. pom.xml의 parent를 "
					+ "io.salvia95.cozy:cozy-parent로 두세요.");
		}
		Path jar = FrameworkJar.locate(repository, project.cozyVersion());
		if (!Files.isRegularFile(jar)) {
			throw new CliException("cozy-ui " + project.cozyVersion() + " jar가 없습니다: " + jar + ". 앱 폴더에서 mvn -o "
					+ "dependency:resolve를 한 번 실행하거나 --repository로 cozy jar가 있는 저장소를 알려 주세요. 프레임워크 "
					+ "템플릿 없이는 " + what + "을 판단할 수 없습니다.");
		}
		return FrameworkJar.read(jar);
	}

	/** 레시피 시각: 테스트는 시스템 속성 cozy.cli.now(yyyyMMddHHmm)로 고정한다 */
	private static LocalDateTime now() {
		String fixed = System.getProperty("cozy.cli.now");
		return (fixed != null) ? LocalDateTime.parse(fixed, DateTimeFormatter.ofPattern("yyyyMMddHHmm")) : LocalDateTime.now();
	}

	private static AppProject project(Path project, Path workDir) {
		return (project != null) ? AppProject.find(project) : AppProject.find(workDir);
	}

	private static int describe(AppProject project, boolean json, PrintStream out) {
		List<AppResources.ManifestEntry> manifest = List.of();
		Path file = project.dir().resolve("src/main/resources/cozy/fragments.yml");
		if (Files.isRegularFile(file)) {
			try {
				manifest = AppResources.manifest(Files.readString(file, StandardCharsets.UTF_8),
						"src/main/resources/cozy/fragments.yml");
			}
			catch (Exception ex) {
				// 형식 오류는 check(R8)가 알린다. describe는 공개 fragment 없이 보인다
			}
		}
		Map<String, Object> model = Describe.model(project, manifest);
		out.print(json ? Json.write(model) : Describe.text(model));
		return OK;
	}

	private static int check(AppProject project, Path repository, boolean json, PrintStream out) {
		FrameworkJar framework = project.uses("cozy-ui") ? framework(project, repository, "템플릿 규칙") : FrameworkJar.none();
		List<Checks.Finding> findings = Checks.run(project, framework);
		long errors = findings.stream().filter((f) -> f.violation().isError()).count();
		long warnings = findings.size() - errors;
		if (json) {
			List<Object> list = new ArrayList<>();
			for (Checks.Finding f : findings) {
				Violation v = f.violation();
				list.add(Describe.ordered("id", v.ruleId(), "severity", v.isError() ? "error" : "warning", "location",
						v.location(), "file", f.file(), "line", v.line(), "target", v.reference(), "message", v.message(),
						"command", v.command(), "text", v.format()));
			}
			out.print(Json.write(Describe.ordered("ok", errors == 0, "errors", errors, "warnings", warnings, "violations",
					list)));
		}
		else {
			findings.forEach((f) -> out.println(f.violation().format()));
			out.println("[cozy check] " + ((errors == 0) ? "이상 없음" : "오류 " + errors + "건") + ((warnings > 0) ? ", 경고 "
					+ warnings + "건" : "") + " (R1~R15, C8, C9). 컴파일과 렌더링 검사(C1, C3~C7)는 mvn verify가 합니다.");
		}
		return (errors == 0) ? OK : VIOLATIONS;
	}
}
