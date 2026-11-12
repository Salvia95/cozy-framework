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

	/** 사용법에 적힌 동작: {@code cozy <동작>} */
	static Set<String> cliActions() {
		Set<String> actions = new TreeSet<>();
		Matcher m = Pattern.compile("(?m)^\\s+cozy ([a-z]+)").matcher(CozyCli.USAGE);
		while (m.find()) {
			actions.add(m.group(1));
		}
		return actions;
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
			Path maven = this.temp.resolve("maven/apache-maven-3.10.0");
			write(maven.resolve("conf/settings.xml"), "<settings/>");
			executable(write(maven.resolve("bin/mvn"), "#!/bin/sh\necho \"maven JAVA_HOME=$JAVA_HOME args=$*\"\n"));
			Process zip = new ProcessBuilder("zip", "-qr", this.bundle.resolve("apache-maven-3.10.0-bin.zip").toString(),
					"apache-maven-3.10.0").directory(maven.getParent().toFile()).redirectErrorStream(true).start();
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
			// 저장소는 Maven 폴더 옆을 가리킨다. 설치 폴더의 절대 경로가 들어가지 않는다
			assertThat(Files.readString(dest.resolve("apache-maven-3.10.0/conf/settings.xml")))
				.contains("<localRepository>${maven.home}/../repository</localRepository>")
				.contains("<offline>true</offline>")
				.doesNotContain(dest.toString());
			// PATH에는 bin 하나만 더한다. bin의 mvn이 설치 폴더의 Maven을 부른다
			assertThat(install.output()).contains("export PATH=\"" + dest.resolve("bin") + ":$PATH\"")
				.doesNotContain("apache-maven-3.10.0/bin");
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

		/** JDK 25인 척하는 java. {@code -XshowSettings}에는 버전을, 그 밖에는 이름과 인수를 출력한다 */
		void jdk(Path home, String name) throws IOException {
			executable(write(home.resolve("bin/java"), """
					#!/bin/sh
					case "$*" in *-XshowSettings*) echo "    java.specification.version = 25" >&2; exit 0 ;; esac
					echo "%s $*"
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
