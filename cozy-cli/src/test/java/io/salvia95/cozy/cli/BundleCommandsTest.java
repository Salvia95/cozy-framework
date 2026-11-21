package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * 오프라인 번들의 cozy 명령(bash의 cozy, Windows의 cozy.ps1)은 cozy-cli가 아는 동작을 모두 cozy-cli로 넘긴다. 하나라도 빠지면 문서에 있는
 * 명령을 번들에서 쓸 수 없다.
 */
class BundleCommandsTest {

	static final Path BUNDLE = Path.of("../distribution/offline-bundle");

	/** 사용법에 적힌 동작({@code cozy <동작>})과 옛 이름 */
	static Set<String> cliActions() {
		Set<String> actions = new TreeSet<>(CozyCli.ALIASES);
		Matcher m = Pattern.compile("(?m)^\\s+cozy ([a-z]+)").matcher(CozyCli.USAGE);
		while (m.find()) {
			actions.add(m.group(1));
		}
		return actions;
	}

	/** 스크립트 머리의 도움말(맨 위의 # 줄들). 큰따옴표 안의 예시 값만 한글을 쓸 수 있다 */
	static List<String> helpLines(Path script) throws IOException {
		List<String> help = new ArrayList<>();
		for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
			String text = line.replace("\uFEFF", "");
			if (text.startsWith("#!")) {
				continue;
			}
			if (!text.startsWith("#")) {
				break;
			}
			help.add(text);
		}
		return help;
	}

	@Test
	void theBundleHelpIsEnglish() throws IOException {
		for (String name : List.of("cozy", "cozy.ps1")) {
			List<String> help = helpLines(BUNDLE.resolve(name));
			assertThat(String.join("\n", help)).as(name)
				.contains("Install the bundle on this PC")
				.contains("cozy new")
				.contains("cozy version")
				.contains("cozy help");
			assertThat(help).as(name).noneMatch((line) -> line.replaceAll("\"[^\"]*\"", "").matches(".*\\p{IsHangul}.*"));
		}
		assertThat(Files.readString(BUNDLE.resolve("cozy.ps1"), StandardCharsets.UTF_8)).as("cozy.ps1의 도움말이 cozy-cli의 도움말을 잇는다")
			.contains("java -jar $jar help");
	}

	@Test
	void theBashCommandPassesEveryCliActionToCozyCli() throws IOException {
		String script = Files.readString(BUNDLE.resolve("cozy"), StandardCharsets.UTF_8);
		Matcher line = Pattern.compile("(?m)^\\s*([a-z|]+)\\) cli \"\\$ACTION\" \"\\$@\" ;;$").matcher(script);
		assertThat(line.find()).as("cozy의 cozy-cli 줄").isTrue();
		assertThat(Set.of(line.group(1).split("\\|"))).containsExactlyInAnyOrderElementsOf(cliActions());
	}

	@Test
	void theWindowsCommandPassesEveryCliActionToCozyCli() throws IOException {
		String script = Files.readString(BUNDLE.resolve("cozy.ps1"), StandardCharsets.UTF_8);
		Matcher line = Pattern.compile("\\{ \\$_ -in @\\(([^)]*)\\) \\} \\{ Invoke-CozyCli").matcher(script);
		assertThat(line.find()).as("cozy.ps1의 cozy-cli 줄").isTrue();
		Set<String> passed = new TreeSet<>();
		Matcher name = Pattern.compile("'([a-z]+)'").matcher(line.group(1));
		while (name.find()) {
			passed.add(name.group(1));
		}
		assertThat(passed).containsExactlyInAnyOrderElementsOf(cliActions());
	}

	/**
	 * Windows PowerShell 5.1은 {@code $ErrorActionPreference = 'Stop'}일 때 {@code 2>&1}로 받은 외부 명령의 stderr를 오류로
	 * 바꿔 스크립트를 멈춘다. java처럼 정상 출력을 stderr에 쓰는 명령을 그렇게 받으면 명령이 늘 실패한다. 그래서 {@code 2>&1}은 바로
	 * 앞에서 Continue로 바꾼 곳에서만 쓴다.
	 */
	@Test
	void powershellScriptsRedirectStderrOnlyWhileErrorsContinue() throws IOException {
		List<Path> scripts;
		try (var paths = Files.walk(BUNDLE.getParent())) {
			scripts = paths.filter((path) -> path.toString().endsWith(".ps1")).sorted().toList();
		}
		assertThat(scripts).isNotEmpty();
		List<String> unsafe = new ArrayList<>();
		for (Path script : scripts) {
			List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i).strip();
				if (line.startsWith("#") || !line.contains("2>&1")) {
					continue;
				}
				boolean continues = lines.subList(Math.max(0, i - 3), i)
					.stream()
					.anyMatch((before) -> before.contains("$ErrorActionPreference = 'Continue'"));
				if (!continues) {
					unsafe.add(BUNDLE.getParent().relativize(script) + ":" + (i + 1) + " " + line);
				}
			}
		}
		assertThat(unsafe).isEmpty();
	}

	/**
	 * PowerShell 변수 이름에는 한글도 들어간다. {@code "$path가 없습니다"}는 {@code $path가}라는 빈 변수가 되어 메시지에서 값이
	 * 사라진다. 한글이 바로 붙으면 {@code ${path}}로 쓴다.
	 */
	/** Windows 설치도 사용자 저장소에 쓰고 설치 폴더의 저장소를 꼬리로 읽는다. MSI가 이 설치 결과를 그대로 묶는다 */
	@Test
	void theWindowsInstallUsesTheUserRepositoryAndKeepsWhatOnlyTheOldRepositoryHad() throws IOException {
		String script = Files.readString(BUNDLE.resolve("cozy.ps1"), StandardCharsets.UTF_8);

		assertThat(script).contains("<localRepository>`${env.LOCALAPPDATA}/Cozy/repository</localRepository>")
			.contains("<maven.repo.local.tail>`${maven.home}/../repository</maven.repo.local.tail>")
			.contains("<activeProfile>cozy-bundle</activeProfile>")
			.contains("이전 판에만 있던 파일")
			.contains("$env:COZY_REPOSITORY = $UserRepository + ';' + (Join-Path $Root 'repository')")
			.contains("$env:COZY_HOME = $Root")
			.contains("'channel', 'update', 'upgrade')");
	}

	/**
	 * MSI로 설치한 폴더에는 표시 파일이 있어 cozy upgrade가 새 판도 MSI로 설치한다. zip 설치({@code cozy install})는 이 파일을 쓰지
	 * 않는다. Windows 러너가 MSI 두 판으로 업그레이드를 확인한다.
	 */
	@Test
	void theMsiMarksItsInstallFolderAndTheRunnerChecksAnUpgrade() throws IOException {
		String build = Files.readString(BUNDLE.resolveSibling("msi/build-msi.ps1"), StandardCharsets.UTF_8);
		String workflow = Files.readString(BUNDLE.resolve("../../.github/workflows/release.yml"), StandardCharsets.UTF_8);

		assertThat(build).contains("[System.IO.File]::WriteAllText((Join-Path $staging 'COZY_INSTALLER'), \"msi`n\"");
		assertThat(Files.readString(BUNDLE.resolve("cozy"), StandardCharsets.UTF_8)).doesNotContain("COZY_INSTALLER");
		assertThat(Files.readString(BUNDLE.resolve("cozy.ps1"), StandardCharsets.UTF_8)).doesNotContain("COZY_INSTALLER");
		assertThat(workflow).contains("distribution\\msi\\verify-msi-upgrade.ps1 -Bundle");
	}

	@Test
	void powershellVariablesAreNotGluedToHangul() throws IOException {
		Pattern glued = Pattern.compile("\\$[A-Za-z_][A-Za-z0-9_]*[\\uAC00-\\uD7A3]");
		List<String> found = new ArrayList<>();
		try (var paths = Files.walk(BUNDLE.getParent())) {
			for (Path script : paths.filter((path) -> path.toString().endsWith(".ps1")).sorted().toList()) {
				List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					if (!lines.get(i).strip().startsWith("#") && glued.matcher(lines.get(i)).find()) {
						found.add(BUNDLE.getParent().relativize(script) + ":" + (i + 1) + " " + lines.get(i).strip());
					}
				}
			}
		}
		assertThat(found).isEmpty();
	}

	/**
	 * bash의 cozy가 번들의 JDK({@code jdk/})를 쓰는 방식. 가짜 JDK와 가짜 Maven으로 번들 모양을 만들고, 시스템 JDK 자리에는 부르면
	 * 실패하는 java를 둔다.
	 */
	@Nested
	@DisabledOnOs(value = OS.WINDOWS, disabledReason = "bash의 cozy를 실행한다. Windows는 cozy.ps1이 돈다")
	class BundledJdk {

		@TempDir
		Path temp;

		Path bundle;

		Path systemJdk;

		@BeforeEach
		void bundle() throws Exception {
			this.bundle = Files.createDirectories(this.temp.resolve("bundle"));
			Files.copy(BUNDLE.resolve("cozy"), this.bundle.resolve("cozy"));
			Files.copy(BUNDLE.resolve("cozy.cmd"), this.bundle.resolve("cozy.cmd"));
			Files.copy(BUNDLE.resolve("cozy.ps1"), this.bundle.resolve("cozy.ps1"));
			executable(this.bundle.resolve("cozy"));
			Files.writeString(this.bundle.resolve("COZY_VERSION"), "9.9.9\n");
			Files.writeString(this.bundle.resolve("LICENSE"), "license");
			Files.writeString(this.bundle.resolve("NOTICE"), "notice");
			write(this.bundle.resolve("docs/README.md"), "docs");
			write(this.bundle.resolve("licenses/THIRD-PARTY.txt"), "list");
			write(this.bundle.resolve("repository/marker"), "repo");
			write(this.bundle.resolve("lib/cli/cozy-cli.jar"), "jar");
			// Maven 배포판: mvn은 자기가 받은 JAVA_HOME과 인수를 출력한다
			Path maven = this.temp.resolve("maven/apache-maven-3.10.1");
			write(maven.resolve("conf/settings.xml"), "<settings/>");
			executable(write(maven.resolve("bin/mvn"), "#!/bin/sh\necho \"maven JAVA_HOME=$JAVA_HOME args=$*\"\n"));
			Process zip = new ProcessBuilder("zip", "-qr", this.bundle.resolve("apache-maven-3.10.1-bin.zip").toString(),
					"apache-maven-3.10.1").directory(maven.getParent().toFile()).redirectErrorStream(true).start();
			assertThat(zip.waitFor(30, TimeUnit.SECONDS)).isTrue();
			assertThat(zip.exitValue()).as("zip").isZero();
			jdk(this.bundle.resolve("jdk"), "bundled-java");
			// 시스템 JDK: 부르면 실패한다. 번들 JDK가 있는데 이것을 쓰면 테스트가 실패한다
			this.systemJdk = this.temp.resolve("system-jdk");
			executable(write(this.systemJdk.resolve("bin/java"), "#!/bin/sh\necho system-java >&2\nexit 97\n"));
		}

		@Test
		void cozyCommandsRunOnTheBundledJdkInsteadOfTheSystemOne() throws Exception {
			Result result = run(this.bundle.resolve("cozy"), "describe", "--json");

			assertThat(result.code()).as(result.output()).isZero();
			assertThat(result.output())
				.contains("bundled-java -jar " + this.bundle.resolve("lib/cli/cozy-cli.jar") + " describe --json")
				.doesNotContain("system-java");
		}

		@Test
		void installCopiesTheJdkAndTheInstalledMavenRunsOnIt() throws Exception {
			Path dest = this.temp.resolve("installed");

			Result install = run(this.bundle.resolve("cozy"), "install", dest.toString());

			assertThat(install.code()).as(install.output()).isZero();
			assertThat(dest.resolve("jdk/bin/java")).isExecutable();
			assertThat(dest.resolve("bin/mvn")).isExecutable();
			// Maven은 사용자 저장소에 쓰고, 설치 폴더의 저장소는 읽기만 한다(꼬리). 설치 폴더의 절대 경로가 들어가지 않는다
			assertThat(Files.readString(dest.resolve("apache-maven-3.10.1/conf/settings.xml")))
				.contains("<localRepository>${env.HOME}/.local/share/cozy/repository</localRepository>")
				.contains("<maven.repo.local.tail>${maven.home}/../repository</maven.repo.local.tail>")
				.contains("<activeProfile>cozy-bundle</activeProfile>")
				.contains("<offline>true</offline>")
				.doesNotContain(dest.toString());
			// PATH에는 bin 하나만 더한다. bin의 mvn이 설치 폴더의 Maven을 부른다
			assertThat(install.output()).contains("export PATH=\"" + dest.resolve("bin") + ":$PATH\"")
				.doesNotContain("apache-maven-3.10.1/bin");
			assertThat(run(dest.resolve("bin/mvn"), "-v").output()).contains("maven JAVA_HOME=" + dest.resolve("jdk") + " args=-v");
			Result created = run(dest.resolve("bin/cozy"), "new", "-DartifactId=demo");
			assertThat(created.code()).as(created.output()).isZero();
			assertThat(created.output()).contains("maven JAVA_HOME=" + dest.resolve("jdk"))
				.contains("maven-archetype-plugin")
				.contains("-DartifactId=demo");
		}

		@Test
		void aBundleWithoutAJdkRemovesTheOldOneAndUsesTheSystemJdk() throws Exception {
			Path dest = this.temp.resolve("installed");
			assertThat(run(this.bundle.resolve("cozy"), "install", dest.toString()).code()).isZero();
			delete(this.bundle.resolve("jdk"));
			jdk(this.systemJdk, "system-ok");

			Result install = run(this.bundle.resolve("cozy"), "install", dest.toString());

			assertThat(install.code()).as(install.output()).isZero();
			// 이전 번들의 JDK가 남아 있으면 판이 다른 JDK를 쓰게 된다
			assertThat(dest.resolve("jdk")).doesNotExist();
			assertThat(run(dest.resolve("bin/mvn"), "-v").output()).contains("maven JAVA_HOME=" + this.systemJdk + " args=-v");
			assertThat(run(dest.resolve("bin/cozy"), "check").output()).contains("system-ok -jar");
		}

		/**
		 * 다시 설치하면 설치 폴더의 저장소가 새 번들의 것으로 바뀐다. 이전 판에만 있던 파일(이전 판의 cozy 모듈, 그 판만 쓰는
		 * 의존성, 예전 설치가 이 저장소에 쓴 앱)은 사용자 저장소로 옮겨서 올리기 전의 앱도 빌드된다.
		 */
		@Test
		void reinstallMovesWhatOnlyTheOldRepositoryHadToTheUserRepository() throws Exception {
			Path dest = this.temp.resolve("installed");
			write(this.bundle.resolve("repository/io/salvia95/cozy/cozy-ui/9.9.8/cozy-ui-9.9.8.jar"), "old ui");
			assertThat(run(this.bundle.resolve("cozy"), "install", dest.toString()).code()).isZero();
			write(dest.resolve("repository/com/example/app/1.0/app-1.0.jar"), "installed by an old build");
			write(dest.resolve("repository/.locks/artifact~com.example~app~1.0.lock"), "");
			Path user = this.temp.resolve("home/.local/share/cozy/repository");
			write(user.resolve("com/example/kept/1.0/kept-1.0.jar"), "already there");
			write(dest.resolve("repository/com/example/kept/1.0/kept-1.0.jar"), "older copy");
			delete(this.bundle.resolve("repository/io/salvia95/cozy/cozy-ui/9.9.8"));
			write(this.bundle.resolve("repository/io/salvia95/cozy/cozy-ui/9.9.9/cozy-ui-9.9.9.jar"), "new ui");

			Result install = run(this.bundle.resolve("cozy"), "install", dest.toString());

			assertThat(install.code()).as(install.output()).isZero();
			assertThat(install.output()).contains("이전 판에만 있던 파일 2개를 사용자 저장소로 옮겼습니다: " + user);
			assertThat(user.resolve("io/salvia95/cozy/cozy-ui/9.9.8/cozy-ui-9.9.8.jar")).hasContent("old ui");
			assertThat(user.resolve("com/example/app/1.0/app-1.0.jar")).hasContent("installed by an old build");
			// 사용자 저장소에 이미 있는 파일은 덮어쓰지 않고, 잠금 파일은 옮기지 않는다
			assertThat(user.resolve("com/example/kept/1.0/kept-1.0.jar")).hasContent("already there");
			assertThat(user.resolve(".locks")).doesNotExist();
			// 설치 폴더의 저장소는 새 번들의 것 그대로다
			assertThat(dest.resolve("repository/io/salvia95/cozy/cozy-ui/9.9.9/cozy-ui-9.9.9.jar")).hasContent("new ui");
			assertThat(dest.resolve("repository/io/salvia95/cozy/cozy-ui/9.9.8")).doesNotExist();
			assertThat(dest.resolve("repository/com/example")).doesNotExist();
		}

		@Test
		void firstInstallMovesNothing() throws Exception {
			Result install = run(this.bundle.resolve("cozy"), "install", this.temp.resolve("installed").toString());

			assertThat(install.code()).as(install.output()).isZero();
			assertThat(install.output()).doesNotContain("이전 판에만 있던 파일");
			assertThat(install.output()).contains("  저장소:      " + this.temp.resolve("home/.local/share/cozy/repository"));
		}

		/** cozy 명령은 앱이 쓰는 판의 cozy-ui를 사용자 저장소에서 먼저 찾고, 없으면 설치 폴더의 저장소에서 찾는다 */
		@Test
		void cozyCommandsSearchTheUserRepositoryBeforeTheInstalledOne() throws Exception {
			Path dest = this.temp.resolve("installed");
			assertThat(run(this.bundle.resolve("cozy"), "install", dest.toString()).code()).isZero();

			Result check = run(dest.resolve("bin/cozy"), "check");

			assertThat(check.output()).contains("COZY_REPOSITORY=" + this.temp.resolve("home/.local/share/cozy/repository")
					+ ":" + dest.resolve("repository"));
		}

		@Test
		void helpShowsTheBundleCommandsAndThenTheCliHelp() throws Exception {
			Result help = run(this.bundle.resolve("cozy"), "help");

			assertThat(help.code()).as(help.output()).isZero();
			assertThat(help.output()).contains("Install the bundle on this PC")
				.contains("bundled-java -jar " + this.bundle.resolve("lib/cli/cozy-cli.jar") + " help");
		}

		/** cozy upgrade --check는 cozy-cli가 한다. 설치 폴더를 알려 주므로 cozy-cli가 그 COZY_VERSION으로 지금 판을 안다 */
		@Test
		void cozyUpgradeCheckTellsTheCliWhereCozyIsInstalled() throws Exception {
			Path dest = this.temp.resolve("installed");
			assertThat(run(this.bundle.resolve("cozy"), "install", dest.toString()).code()).isZero();

			Result update = run(dest.resolve("bin/cozy"), "upgrade", "--check", "--json");

			assertThat(update.output()).contains(" upgrade --check --json ").contains("COZY_HOME=" + dest);
			assertThat(dest.resolve("COZY_VERSION")).isRegularFile();
		}

		/** JDK 25인 척하는 java. {@code -XshowSettings}에는 버전을, 그 밖에는 이름과 인수를 출력한다 */
		void jdk(Path home, String name) throws IOException {
			executable(write(home.resolve("bin/java"), """
					#!/bin/sh
					case "$*" in *-XshowSettings*) echo "    java.specification.version = 25" >&2; exit 0 ;; esac
					echo "%s $* COZY_REPOSITORY=$COZY_REPOSITORY COZY_HOME=$COZY_HOME"
					""".formatted(name)));
		}

		Result run(Path script, String... args) throws Exception {
			List<String> command = new ArrayList<>(List.of("bash", script.toString()));
			command.addAll(List.of(args));
			ProcessBuilder builder = new ProcessBuilder(command).directory(this.temp.toFile()).redirectErrorStream(true);
			Map<String, String> env = builder.environment();
			env.put("PATH", this.systemJdk.resolve("bin") + ":" + System.getenv("PATH"));
			env.put("JAVA_HOME", this.systemJdk.toString());
			env.put("HOME", this.temp.resolve("home").toString());
			env.remove("COZY_REPOSITORY");
			Process process = builder.start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
			return new Result(process.exitValue(), output);
		}

		record Result(int code, String output) {
		}

		static Path write(Path file, String text) throws IOException {
			Files.createDirectories(file.getParent());
			return Files.writeString(file, text, StandardCharsets.UTF_8);
		}

		static void executable(Path file) throws IOException {
			Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
		}

		static void delete(Path dir) throws IOException {
			try (var paths = Files.walk(dir)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
	}
}
