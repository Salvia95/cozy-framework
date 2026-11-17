package io.salvia95.cozy.cli;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.AppResources;
import io.salvia95.cozy.cli.internal.Checks;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Describe;
import io.salvia95.cozy.cli.internal.FrameworkJar;
import io.salvia95.cozy.cli.internal.Json;
import io.salvia95.cozy.cli.internal.Recipes;
import io.salvia95.cozy.cli.internal.channel.ChannelClient;
import io.salvia95.cozy.cli.internal.channel.ChannelCommands;
import io.salvia95.cozy.cli.internal.channel.UpdateCommands;
import io.salvia95.cozy.cli.internal.channel.UpgradeCommands;
import io.salvia95.cozy.cli.internal.channel.UserConfig;
import io.salvia95.cozy.cli.internal.recipe.RecipeRunner;
import io.salvia95.cozy.rules.Violation;

/**
 * cozy 명령. 형식: {@code cozy <동작> [옵션]}. 문서: docs/conventions/cli.md
 *
 * <ul>
 * <li>{@code describe [--json]}: 앱의 지도(모듈, 의존, spi 계약, 이벤트, 화면, 공개 fragment, 빈칸)</li>
 * <li>{@code check [--json]}: 컴파일 없이 하는 검사(R1~R16, C8, C9). 최종 판단은 {@code mvn verify}</li>
 * <li>{@code recipes [--json]}: 구조를 만드는 명령 목록</li>
 * <li>{@code add <레시피>}, {@code link}, {@code enable events|workbench}: 구조를 만든다 (원자적으로 적용, {@code --dry-run})</li>
 * <li>{@code sync agents [--force]}: 앱의 AGENTS.md와 cozy-helper 스킬을 이 cozy 명령에 든 것과 같게 맞춘다</li>
 * <li>{@code set arg}: 템플릿의 fragment 호출 인자 가운데 문자열 하나를 바꾼다 ({@code --dry-run})</li>
 * <li>{@code migrate alpine}: 앱 템플릿의 없앤 동작 속성을 cozy 컴포넌트로 바꾼다 ({@code --dry-run})</li>
 * <li>{@code migrate events}: 옛 이벤트 자리({@code {모듈}/spi/event/})의 이벤트를 이벤트 계약 모듈로 옮기고, 저장된 클래스 이름을 바꾸는
 * 마이그레이션을 만든다 ({@code --dry-run})</li>
 * <li>{@code channel set|show|add|release}: 개발 도구의 업데이트 채널</li>
 * <li>{@code update [--json]}: 채널의 새 판을 보인다. 아무것도 바꾸지 않는다</li>
 * <li>{@code upgrade [--to 판]}: 채널에서 판을 받아 확인하고, cozy 명령이 끝난 뒤에 설치한다</li>
 * </ul>
 * 채널을 정했으면 앱에 쓰는 명령 뒤에 하루에 한 번 새 판을 확인하고, 있으면 오류 출력에 한 줄을 보인다. {@code cozy upgrade}로 맡긴
 * 설치의 결과는 그다음 명령이 오류 출력에 보인다.
 * 종료 코드: 0 이상 없음, 1 위반 있음, 2 사용법 오류이거나 판단할 수 없음.
 */
public final class CozyCli {

	public static final int OK = 0;

	public static final int VIOLATIONS = 1;

	public static final int UNUSABLE = 2;

	static final String USAGE = """
			cozy 명령. 앱 폴더(또는 그 아래)에서 실행한다.

			  cozy describe [--json]   앱의 지도: 모듈, 의존, spi 계약, 이벤트, 화면, 공개 fragment, 남은 빈칸
			  cozy check [--json]      빠른 검사: 템플릿 규칙 R1~R16, 모듈 구조 C8, 빈칸 C9 (컴파일하지 않는다)
			  cozy recipes [--json]    구조를 만드는 명령 목록 (add, link, enable)
			  cozy add <레시피> ...     구조 만들기: module, crud, screen, fragment, event, listener, migration, dto, query, command, event-version
			  cozy link <모듈> --uses <모듈>   모듈 의존 선언 (allowedDependencies)
			  cozy enable events       pom에 cozy-starter-events 더하기
			  cozy enable workbench    pom에 dev 전용 워크벤치(/cozy/dev/workbench) 더하기
			  cozy sync agents [--force]   AGENTS.md와 cozy-helper 스킬을 이 cozy 명령의 것으로 맞추기
			  cozy set arg <템플릿> --line <줄> --fragment <이름> --index <번째> --from <지금 값> --value <새 값>
			                           fragment 호출의 문자열 인자 하나 바꾸기 (워크벤치의 속성 패널이 쓴다)
			  cozy migrate alpine      앱 템플릿의 없앤 동작 속성(data-cozy-confirm 등)을 cozy 컴포넌트로 바꾸기 (2.0.0으로 올릴 때)
			  cozy migrate events      옛 자리(<모듈>/spi/event/)의 이벤트를 이벤트 계약 모듈 events로 옮기기 (2.0.0으로 올릴 때)
			  cozy channel set <주소> [--header <이름>]   업데이트 채널(cozy-channel.json의 주소) 정하기: 파일 서버, 웹 서버, GitLab, GitHub
			  cozy channel show [--json]   정한 채널 보기 (환경 변수 COZY_CHANNEL이 설정 파일보다 앞선다)
			  cozy channel add <채널 폴더> <릴리스 폴더>   (관리자) 받은 릴리스를 채널 폴더에 더하기
			  cozy channel release <릴리스 폴더> <channel.yml>   (릴리스 워크플로) 릴리스 하나의 cozy-channel.json 만들기
			  cozy update [--json]     채널의 새 판, 요약, 호환 깨짐, 앱에서 돌릴 명령 보기 (아무것도 바꾸지 않는다)
			  cozy upgrade [--to <판>]   채널의 판을 받아 SHA-256을 확인하고, 이 명령이 끝나면 설치하기 (MSI나 zip, 설치한 모양대로)
			                           주 버전이 같은 가장 새 판으로 올린다. 호환이 깨지는 판은 --to로 적는다. 낮은 판으로는 가지 않는다

			옵션
			  --project <폴더>         앱 폴더 (기본: 현재 폴더에서 위로 찾는다)
			  --repository <폴더>      cozy jar가 있는 Maven 저장소 (기본: COZY_REPOSITORY, 없으면 ~/.m2/repository)
			                           COZY_REPOSITORY에는 경로 구분자(Windows ;, 그 밖 :)로 여러 저장소를 적고, 앞에서부터 찾는다
			  --ui <jar나 폴더>        저장소에서 찾지 않고 이 cozy-ui 자원을 읽는다 (워크벤치가 쓴다)
			  --json                   기계가 읽는 출력
			  --dry-run                (add, link, enable, sync, set, migrate, channel add) 바꿀 파일만 보이고 쓰지 않는다
			  --force                  (sync agents) 앱에서 고친 AGENTS.md도 바꾼다

			채널을 정했으면 하루에 한 번 새 판을 확인하고, 있으면 명령 끝에 한 줄을 보인다 (--json 출력에는 보이지 않는다)
			cozy upgrade로 맡긴 설치의 결과는 그다음 cozy 명령이 처음에 보인다

			종료 코드: 0 이상 없음, 1 위반 있음, 2 사용법 오류이거나 판단할 수 없음
			""";

	/** 이 명령 뒤에 새 판 알림을 보인다. 채널과 업데이트 명령, 도움말 뒤에는 보이지 않는다 */
	private static final Set<String> NOTICE_ACTIONS = Set.of("describe", "check", "recipes", "add", "link", "enable", "sync",
			"set", "migrate");

	private static final Duration UPDATE_CONNECT = Duration.ofSeconds(10);

	private static final Duration UPDATE_REQUEST = Duration.ofSeconds(60);

	private static final Duration NOTICE_TIMEOUT = Duration.ofSeconds(3);

	private CozyCli() {
	}

	public static void main(String[] args) {
		// 채널 서버에 닿을 때 시스템의 프록시 설정을 따른다. 기본 프록시 선택기를 처음 만들기 전에 정해야 한다
		System.setProperty("java.net.useSystemProxies", "true");
		PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
		PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
		UpgradeCommands.report(System.getenv(), windows(), Instant.now(), err);
		int code = runWith(args, Path.of(""), System.getenv(), out, err);
		notice(args, System.getenv(), windows(), LocalDate.now(), () -> ChannelClient.forThisPc(windows(), NOTICE_TIMEOUT,
				NOTICE_TIMEOUT), err);
		System.exit(code);
	}

	/**
	 * 명령 뒤의 새 판 알림. 앱에 쓰는 명령의 사람이 읽는 출력 뒤에만 보이고, 명령의 결과(출력, 종료 코드)를 바꾸지 않는다.
	 *
	 * @param args 명령의 인자
	 */
	static void notice(String[] args, Map<String, String> env, boolean windows, LocalDate today, Supplier<ChannelClient> client,
			PrintStream err) {
		String action = null;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--json" -> {
					return;
				}
				case "--project", "--repository", "--ui" -> i++;
				default -> {
					if (action == null) {
						action = args[i];
					}
				}
			}
		}
		if (action != null && NOTICE_ACTIONS.contains(action)) {
			UpdateCommands.notice(env, windows, today, client, err);
		}
	}

	/**
	 * @param workDir 현재 폴더
	 * @param repositoryEnv {@code COZY_REPOSITORY} 값 (없으면 {@code null}). 경로 구분자로 나눈 저장소 목록이고 앞에서부터 찾는다
	 * @return 종료 코드
	 */
	public static int run(String[] args, Path workDir, String repositoryEnv, PrintStream out, PrintStream err) {
		Map<String, String> env = new HashMap<>(System.getenv());
		if (repositoryEnv != null) {
			env.put("COZY_REPOSITORY", repositoryEnv);
		}
		else {
			env.remove("COZY_REPOSITORY");
		}
		return runWith(args, workDir, env, out, err);
	}

	/**
	 * @param workDir 현재 폴더
	 * @param env 환경 변수({@code COZY_REPOSITORY}, {@code COZY_CHANNEL}, {@code COZY_CHANNEL_TOKEN}, 설정 파일의 자리)
	 * @return 종료 코드
	 */
	public static int runWith(String[] args, Path workDir, Map<String, String> env, PrintStream out, PrintStream err) {
		List<String> rest = new ArrayList<>();
		Path project = null;
		List<Path> repository = repositories(env.get("COZY_REPOSITORY"));
		boolean json = false;
		boolean dryRun = false;
		Path ui = null;
		boolean force = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--json" -> json = true;
				case "--dry-run" -> dryRun = true;
				case "--force" -> force = true;
				case "--project", "--repository", "--ui" -> {
					if (i + 1 >= args.length) {
						err.println("[cozy] " + args[i] + " 뒤에 폴더를 적습니다.");
						return UNUSABLE;
					}
					Path value = workDir.resolve(args[++i]);
					switch (args[i - 1]) {
						case "--project" -> project = value;
						case "--ui" -> ui = value;
						default -> repository = List.of(value);
					}
				}
				default -> rest.add(args[i]);
			}
		}
		String action = rest.isEmpty() ? "help" : rest.getFirst();
		if (force && !action.equals("sync")) {
			err.println("[cozy] --force는 cozy sync agents에만 씁니다.");
			return UNUSABLE;
		}
		try {
			return switch (action) {
				case "describe" -> describe(project(project, workDir), json, out);
				case "check" -> check(project(project, workDir), repository, ui, json, out);
				case "recipes" -> {
					out.print(json ? Json.write(Recipes.model()) : Recipes.text());
					yield OK;
				}
				case "add", "link", "enable" -> {
					AppProject app = project(project, workDir);
					List<Path> repo = repository;
					Path uiLocation = ui;
					RecipeRunner.run(action, rest.subList(1, rest.size()), app, () -> framework(app, repo, uiLocation, "아이콘 이름"), now(),
							dryRun, json, out);
					yield OK;
				}
				case "set" -> {
					AppProject app = project(project, workDir);
					List<Path> repo = repository;
					Path uiLocation = ui;
					RecipeRunner.set(rest.subList(1, rest.size()), app, () -> framework(app, repo, uiLocation, "프레임워크 fragment의 값"),
							dryRun, json, out);
					yield OK;
				}
				case "sync" -> {
					RecipeRunner.sync(rest.subList(1, rest.size()), project(project, workDir), force, dryRun, json, out);
					yield OK;
				}
				case "migrate" -> {
					RecipeRunner.migrate(rest.subList(1, rest.size()), project(project, workDir), now(), dryRun, json, out);
					yield OK;
				}
				case "channel" -> {
					ChannelCommands.run(rest.subList(1, rest.size()), env, windows(), now().toLocalDate(), dryRun, json, out);
					yield OK;
				}
				case "update" -> {
					UpdateCommands.update(rest.subList(1, rest.size()), env, windows(), now().toLocalDate(), json,
							ChannelClient.forThisPc(windows(), UPDATE_CONNECT, UPDATE_REQUEST), out);
					yield OK;
				}
				case "upgrade" -> {
					if (json || dryRun) {
						throw new CliException("cozy upgrade에는 " + (json ? "--json" : "--dry-run") + "이 없습니다. 올릴 판과 바뀌는 것은 "
								+ "cozy update로 봅니다.");
					}
					UpgradeCommands.upgrade(rest.subList(1, rest.size()), env, windows(), Instant.now(),
							ChannelClient.forThisPc(windows(), UPDATE_CONNECT, UPDATE_REQUEST), UpgradeCommands.detached(windows()),
							UpgradeCommands.waitFor(windows()), out);
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
	private static FrameworkJar framework(AppProject project, List<Path> repositories, Path ui, String what) {
		if (ui != null) {
			if (!Files.exists(ui)) {
				throw new CliException("--ui로 준 cozy-ui 자원이 없습니다: " + ui + ". 프레임워크 템플릿 없이는 " + what
						+ "을 판단할 수 없습니다.");
			}
			return FrameworkJar.read(ui);
		}
		if (project.cozyVersion() == null) {
			throw new CliException("앱이 cozy-parent를 상속하지 않아 cozy 버전을 알 수 없습니다. pom.xml의 parent를 "
					+ "io.salvia95.cozy:cozy-parent로 두세요.");
		}
		List<Path> candidates = repositories.stream().map(repository -> FrameworkJar.locate(repository, project.cozyVersion())).toList();
		Path jar = candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
		if (jar == null) {
			String searched = String.join(", ", candidates.stream().map(Path::toString).toList());
			throw new CliException("cozy-ui " + project.cozyVersion() + " jar가 없습니다: " + searched + ". 앱 폴더에서 mvn -o "
					+ "dependency:resolve를 한 번 실행하거나 --repository로 cozy jar가 있는 저장소를 알려 주세요. 프레임워크 "
					+ "템플릿 없이는 " + what + "을 판단할 수 없습니다.");
		}
		return FrameworkJar.read(jar);
	}

	/** 사용자 설정 파일의 자리 */
	static Path configFile(Map<String, String> env, boolean windows) {
		return UserConfig.file(env, windows);
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").startsWith("Windows");
	}

	/** {@code COZY_REPOSITORY}의 저장소 목록. 없으면 {@code ~/.m2/repository} 하나다 */
	static List<Path> repositories(String repositoryEnv) {
		if (repositoryEnv == null || repositoryEnv.isBlank()) {
			return List.of(Path.of(System.getProperty("user.home"), ".m2", "repository"));
		}
		return Arrays.stream(repositoryEnv.split(Pattern.quote(File.pathSeparator)))
			.filter(part -> !part.isBlank())
			.map(Path::of)
			.toList();
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

	private static int check(AppProject project, List<Path> repository, Path ui, boolean json, PrintStream out) {
		FrameworkJar framework = project.uses("cozy-ui") ? framework(project, repository, ui, "템플릿 규칙")
				: FrameworkJar.none();
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
					+ warnings + "건" : "") + " (R1~R16, C8, C9). 컴파일, 렌더링, 스크립트 검사(C1, C3~C7, C10)는 mvn verify가 합니다.");
		}
		return (errors == 0) ? OK : VIOLATIONS;
	}
}
