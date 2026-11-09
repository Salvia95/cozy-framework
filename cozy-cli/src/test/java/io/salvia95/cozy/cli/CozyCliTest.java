package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.internal.AppResources;
import io.salvia95.cozy.cli.internal.Recipes;

/**
 * reference-app의 describe가 기대 파일과 같고, check가 템플릿 규칙(R1~R15), 모듈 구조(C8), 빈칸(C9)의
 * 위반 픽스처를 모두 잡는다. 픽스처는 samples/violations의 것을 그대로 쓴다.
 */
class CozyCliTest {

	static final Path REFERENCE_APP = Path.of("../samples/reference-app");

	static final Path VIOLATIONS = Path.of("../samples/violations");

	static final String REPOSITORY = System.getProperty("cozy.cli.test-repository");

	static final String VERSION = System.getProperty("cozy.cli.version");

	/** 명령 한 번의 결과 */
	record Result(int code, String out, String err) {
	}

	static Result run(String... args) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(args, Path.of(""), REPOSITORY, new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	static String expected(String name) throws IOException {
		return Files.readString(Path.of("src/test/resources/expected", name), StandardCharsets.UTF_8);
	}

	@Nested
	class DescribeCommand {

		@Test
		void referenceAppMatchesTheExpectedText() throws IOException {
			Result result = run("describe", "--project", REFERENCE_APP.toString());
			assertThat(result.code()).isZero();
			assertThat(result.out()).isEqualTo(expected("reference-app.describe.txt"));
		}

		@Test
		void referenceAppMatchesTheExpectedJson() throws IOException {
			Result result = run("describe", "--json", "--project", REFERENCE_APP.toString());
			assertThat(result.code()).isZero();
			assertThat(result.out()).isEqualTo(expected("reference-app.describe.json"));
		}

		@Test
		void findsTheProjectFromASubfolder() {
			Result result = run("describe", "--project", REFERENCE_APP.resolve("src/main/resources/templates/memo").toString());
			assertThat(result.code()).isZero();
			assertThat(result.out()).startsWith("앱 io.salvia95.cozy.reference");
		}

		@Test
		void listsSlotsAndUnpublishedEvents(@TempDir Path dir) throws IOException {
			Path app = App.of(dir).module("memo", "{}")
				.java("memo/spi/package-info.java", "@NamedInterface(\"spi\")\npackage it.cozy.app.memo.spi;\n\n"
						+ "import org.springframework.modulith.NamedInterface;\n")
				.java("memo/spi/event/package-info.java", "@NamedInterface(\"spi\")\npackage it.cozy.app.memo.spi.event;\n\n"
						+ "import org.springframework.modulith.NamedInterface;\n")
				.java("memo/spi/event/MemoCreated.java", """
						package it.cozy.app.memo.spi.event;

						import java.util.UUID;

						import io.salvia95.cozy.events.CozyEvent;

						public record MemoCreated(UUID eventId, int schemaVersion, long memoId) implements CozyEvent {
						}
						""")
				.file("src/main/resources/db/migration/V1__memo_init.sql", "-- cozy:slot migration-sql — memo 테이블을 만드세요\n")
				.build();
			Result result = run("describe", "--project", app.toString());
			assertThat(result.out()).contains("이벤트 MemoCreated(long memoId) 발행: 없음(아직 발행하지 않음)")
				.contains("빈칸: 1곳")
				.contains("src/main/resources/db/migration/V1__memo_init.sql:1 migration-sql — memo 테이블을 만드세요.");
		}
	}

	@Nested
	class CheckCommand {

		@Test
		void referenceAppIsClean() {
			Result result = run("check", "--project", REFERENCE_APP.toString());
			assertThat(result.err()).isEmpty();
			assertThat(result.code()).isZero();
			assertThat(result.out()).startsWith("[cozy check] 이상 없음");
		}

		/** C8은 바이트코드로 보는 cozy-test와 같은 7건을 같은 메시지로 낸다 */
		@Test
		void layerFixtureReportsTheSameSevenViolationsAsCozyTest(@TempDir Path dir) throws IOException {
			Result result = run("check", "--project", fixture("checks/C8-layers", dir).toString());
			assertThat(result.code()).isEqualTo(CozyCli.VIOLATIONS);
			for (String expected : groovyExpectations("checks/C8-layers")) {
				assertThat(result.out()).contains(expected);
			}
			assertThat(lines(result.out(), "[C8]")).hasSize(7);
			assertThat(result.out()).contains("[cozy check] 오류 7건");
		}

		@Test
		void slotFixtureReportsThreeSlots(@TempDir Path dir) throws IOException {
			Result result = run("check", "--project", fixture("checks/C9-slots", dir).toString());
			assertThat(result.code()).isEqualTo(CozyCli.VIOLATIONS);
			assertThat(lines(result.out(), "[C9]")).hasSize(3);
			for (String expected : groovyExpectations("checks/C9-slots")) {
				if (expected.startsWith("[C9] src/")) {
					assertThat(result.out()).contains(expected);
				}
			}
		}

		@Test
		void templateFixtureReportsR3(@TempDir Path dir) throws IOException {
			Result result = run("check", "--project", fixture("checks/C2-rules", dir).toString());
			assertThat(result.code()).isEqualTo(CozyCli.VIOLATIONS);
			assertThat(lines(result.out(), "[R")).singleElement().asString().startsWith("[R3] templates/voc/api/list.html:2");
		}

		/** samples/violations/rules의 R1~R15 픽스처를 앱 프로젝트로 감싸 check한다. 위반은 모두 그 규칙 ID의 오류다 */
		@TestFactory
		Stream<DynamicTest> everyTemplateRuleFixtureFailsWithItsRule(@TempDir Path dir) throws IOException {
			List<Path> fixtures;
			try (Stream<Path> dirs = Files.list(VIOLATIONS.resolve("rules"))) {
				fixtures = dirs.filter(Files::isDirectory).sorted().toList();
			}
			assertThat(fixtures).hasSize(15);
			return fixtures.stream().map((fixture) -> DynamicTest.dynamicTest(fixture.getFileName().toString(), () -> {
				String name = fixture.getFileName().toString();
				String rule = name.substring(0, name.indexOf('-'));
				Path app = ruleApp(fixture, dir.resolve(name));
				Result result = run("check", "--json", "--project", app.toString());
				assertThat(result.err()).isEmpty();
				assertThat(result.code()).as(result.out()).isEqualTo(CozyCli.VIOLATIONS);
				List<String> ids = ids(result.out());
				assertThat(ids).as(result.out()).isNotEmpty().allMatch(rule::equals);
				assertThat(result.out()).doesNotContain("\"severity\": \"warning\"");
			}));
		}

		@Test
		void jsonNamesTheFileToFix(@TempDir Path dir) throws IOException {
			Result result = run("check", "--json", "--project", fixture("checks/C8-layers", dir).toString());
			assertThat(result.out()).startsWith("{\n  \"ok\": false,\n  \"errors\": 7,\n  \"warnings\": 0,")
				.contains("\"location\": \"it.cozy.app.memo.MemoService\",\n      \"file\": \"src/main/java/it/cozy/app/memo/MemoService.java\"")
				.contains("\"location\": \"it.cozy.app.memo.spi.dto\",\n      \"file\": \"src/main/java/it/cozy/app/memo/spi/dto\"")
				.contains("\"target\": \"it.cozy.app.memo.internal.infra.MemoStore\"")
				.contains("\"command\": null");
		}

		@Test
		void missingFrameworkJarCannotBeJudged() {
			ByteArrayOutputStream err = new ByteArrayOutputStream();
			int code = CozyCli.run(new String[] { "check", "--project", REFERENCE_APP.toString() }, Path.of(""),
					"target/no-such-repository", new PrintStream(new ByteArrayOutputStream()),
					new PrintStream(err, true, StandardCharsets.UTF_8));
			assertThat(code).isEqualTo(CozyCli.UNUSABLE);
			assertThat(err.toString(StandardCharsets.UTF_8)).contains("cozy-ui " + VERSION + " jar가 없습니다")
				.contains("--repository");
		}

		@Test
		void unreadableDeclarationsAreErrorsNotGuesses(@TempDir Path dir) throws IOException {
			Path app = App.of(dir)
				.java("memo/package-info.java", "@ApplicationModule(allowedDependencies = Deps.ALL)\npackage it.cozy.app.memo;\n\n"
						+ "import org.springframework.modulith.ApplicationModule;\n")
				.java("memo/api/Broken.java", "package it.cozy.app.memo.api;\n\nclass Broken {\n  void x( {\n}\n")
				.build();
			Result result = run("check", "--project", app.toString());
			assertThat(result.code()).isEqualTo(CozyCli.VIOLATIONS);
			assertThat(result.out()).contains("[CLI] src/main/java/it/cozy/app/memo/package-info.java:1 — allowedDependencies를 읽을 수 없습니다")
				.contains("[CLI] src/main/java/it/cozy/app/memo/api/Broken.java:4 — Java 문법 오류:");
		}

		@Test
		void appSettingsAreReadLikeSpringBoot(@TempDir Path dir) throws IOException {
			Path app = App.of(dir).file("src/main/resources/application.yml", "cozy:\n  rules:\n    allow-same-layer: true\n")
				.build();
			assertThat(AppResources.appProperty(app, "cozy.rules.allow-same-layer")).isEqualTo("true");
			App.of(dir).file("src/main/resources/application.yml", "cozy.rules:\n  allow-same-layer: false\n");
			assertThat(AppResources.appProperty(app, "cozy.rules.allow-same-layer")).isEqualTo("false");
			App.of(dir).file("src/main/resources/application.properties", "cozy.rules.allow-same-layer=true\n");
			assertThat(AppResources.appProperty(app, "cozy.rules.allow-same-layer")).isEqualTo("true");
			assertThat(AppResources.appProperty(app, "cozy.nothing")).isNull();
		}
	}

	@Nested
	class OtherCommands {

		@Test
		void recipesListEveryRecipe() {
			Result result = run("recipes");
			assertThat(result.code()).isZero();
			for (Recipes.Recipe recipe : Recipes.ALL) {
				assertThat(result.out()).contains(recipe.usage());
			}
			assertThat(Recipes.ALL).extracting(Recipes.Recipe::name)
				.containsExactly("module", "link", "crud", "screen", "fragment", "event", "listener", "migration", "dto",
						"enable events", "query", "command", "event-version");
		}

		@Test
		void recipesJsonSaysWhatIsAvailable() {
			Result result = run("recipes", "--json");
			assertThat(result.out()).startsWith("{\n  \"recipes\": [\n    {\n      \"name\": \"module\",\n      \"stage\": 1,")
				.contains("\"available\": true")
				.as("모든 레시피를 쓸 수 있다(field는 보류해 목록에 없다)")
				.doesNotContain("\"available\": false");
		}

		@Test
		void unknownActionAndMissingProject(@TempDir Path dir) {
			assertThat(run("deploy").code()).isEqualTo(CozyCli.UNUSABLE);
			Result missing = run("describe", "--project", dir.toString());
			assertThat(missing.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(missing.err()).contains("앱 프로젝트(pom.xml과 src/main/java가 있는 폴더)를 찾지 못했습니다");
			assertThat(run("--project").code()).isEqualTo(CozyCli.UNUSABLE);
		}

		@Test
		void helpIsTheDefault() {
			Result result = run();
			assertThat(result.code()).isZero();
			assertThat(result.out()).contains("cozy describe [--json]").contains("종료 코드");
		}
	}

	// ── 픽스처 ─────────────────────────────────────────────────

	/** samples/violations의 검사 픽스처를 복사하고 invoker처럼 @project.version@을 채운다 */
	static Path fixture(String name, Path dir) throws IOException {
		Path source = VIOLATIONS.resolve(name);
		Path target = dir.resolve(source.getFileName().toString());
		try (Stream<Path> files = Files.walk(source)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				Path copy = target.resolve(source.relativize(file).toString());
				Files.createDirectories(copy.getParent());
				Files.writeString(copy, Files.readString(file, StandardCharsets.UTF_8).replace("@project.version@", VERSION),
						StandardCharsets.UTF_8);
			}
		}
		return target;
	}

	/** verify.groovy의 expected 목록에서 "[C" 로 시작하는 기대 메시지 */
	static List<String> groovyExpectations(String name) throws IOException {
		String script = Files.readString(VIOLATIONS.resolve(name).resolve("verify.groovy"), StandardCharsets.UTF_8);
		List<String> result = new ArrayList<>();
		Matcher m = Pattern.compile("^\\s*\"(\\[C[0-9]\\] [^\\n]*)\",\\s*$", Pattern.MULTILINE).matcher(script);
		while (m.find()) {
			result.add(m.group(1).replace("\\\"", "\""));
		}
		assertThat(result).isNotEmpty();
		return result;
	}

	/** 템플릿 규칙 픽스처(templates, modules.properties, fragments.yml)를 앱 프로젝트로 감싼다 */
	static Path ruleApp(Path fixture, Path dir) throws IOException {
		App app = App.of(dir);
		Properties modules = new Properties();
		try (Reader reader = Files.newBufferedReader(fixture.resolve("modules.properties"), StandardCharsets.UTF_8)) {
			modules.load(reader);
		}
		for (String name : modules.stringPropertyNames()) {
			String value = modules.getProperty(name).strip();
			if (value.equals("open")) {
				app.java(name + "/package-info.java", "package it.cozy.app." + name + ";\n");
			}
			else {
				String deps = value.isEmpty() ? "{}"
						: "{ " + String.join(", ", Stream.of(value.split("\\s*,\\s*")).map((d) -> "\"" + d + "\"").toList()) + " }";
				app.module(name, deps);
			}
		}
		try (Stream<Path> files = Files.walk(fixture.resolve("templates"))) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				app.file("src/main/resources/templates/" + fixture.resolve("templates").relativize(file).toString().replace('\\', '/'),
						Files.readString(file, StandardCharsets.UTF_8));
			}
		}
		if (Files.isRegularFile(fixture.resolve("fragments.yml"))) {
			app.file("src/main/resources/cozy/fragments.yml", Files.readString(fixture.resolve("fragments.yml"), StandardCharsets.UTF_8));
		}
		return app.build();
	}

	static List<String> lines(String out, String prefix) {
		return out.lines().filter((l) -> l.startsWith(prefix)).toList();
	}

	static List<String> ids(String json) {
		List<String> ids = new ArrayList<>();
		Matcher m = Pattern.compile("\"id\": \"([^\"]+)\"").matcher(json);
		while (m.find()) {
			ids.add(m.group(1));
		}
		return ids;
	}

	/** 테스트용 작은 앱 프로젝트 (패키지 it.cozy.app, cozy-ui 사용) */
	static final class App {

		private final Path dir;

		private App(Path dir) throws IOException {
			this.dir = dir;
			file("pom.xml", """
					<project>
					  <modelVersion>4.0.0</modelVersion>
					  <parent>
					    <groupId>io.salvia95.cozy</groupId>
					    <artifactId>cozy-parent</artifactId>
					    <version>%s</version>
					  </parent>
					  <artifactId>test-app</artifactId>
					  <dependencies>
					    <dependency>
					      <groupId>io.salvia95.cozy</groupId>
					      <artifactId>cozy-ui</artifactId>
					    </dependency>
					  </dependencies>
					</project>
					""".formatted(VERSION));
			java("App.java", "package it.cozy.app;\n\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
					+ "@SpringBootApplication\npublic class App {\n}\n");
		}

		static App of(Path dir) throws IOException {
			return new App(dir);
		}

		App module(String name, String allowedDependencies) throws IOException {
			return java(name + "/package-info.java", "@ApplicationModule(allowedDependencies = " + allowedDependencies + ")\n"
					+ "package it.cozy.app." + name + ";\n\nimport org.springframework.modulith.ApplicationModule;\n");
		}

		App java(String path, String content) throws IOException {
			return file("src/main/java/it/cozy/app/" + path, content);
		}

		App file(String path, String content) throws IOException {
			Path file = this.dir.resolve(path);
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8);
			return this;
		}

		Path build() {
			return this.dir;
		}
	}
}
