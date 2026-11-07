package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * {@code cozy add}와 {@code cozy link}: 계획·적용·멱등·원자성과 실패 이유 (ADR-0032 §2). 생성한 앱이 {@code mvn verify}를 통과하는지는
 * samples/recipes가 확인한다.
 */
class RecipeCommandTest {

	@TempDir
	Path dir;

	@BeforeAll
	static void fixTheClock() {
		System.setProperty("cozy.cli.now", "202611081000");
	}

	/** 아키타입(persistence=true, events=true)과 같은 의존의 빈 앱 */
	Path app() throws IOException {
		write("pom.xml", """
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <parent>
				    <groupId>io.salvia95.cozy</groupId>
				    <artifactId>cozy-parent</artifactId>
				    <version>%s</version>
				  </parent>
				  <artifactId>test-app</artifactId>
				  <dependencies>
				    <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-ui</artifactId></dependency>
				    <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-starter-events</artifactId></dependency>
				  </dependencies>
				</project>
				""".formatted(CozyCliTest.VERSION));
		write("src/main/java/it/cozy/app/App.java", "package it.cozy.app;\n\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
				+ "@SpringBootApplication\npublic class App {\n}\n");
		write("src/main/resources/cozy/fragments.yml", "# 매니페스트\nfragments: []\n");
		return this.dir;
	}

	Result cozy(String... args) {
		List<String> all = new ArrayList<>(List.of(args));
		all.addAll(List.of("--project", this.dir.toString()));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(all.toArray(String[]::new), Path.of(""), CozyCliTest.REPOSITORY,
				new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	Result ok(String... args) {
		Result result = cozy(args);
		assertThat(result.code()).as(result.err()).isZero();
		return result;
	}

	Result fails(String message, String... args) {
		Map<String, String> before = files();
		Result result = cozy(args);
		assertThat(result.code()).as(result.out()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains(message);
		assertThat(files()).as("실패한 명령은 아무 파일도 바꾸지 않는다").isEqualTo(before);
		return result;
	}

	String read(String path) {
		try {
			return Files.readString(this.dir.resolve(path), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	void write(String path, String content) throws IOException {
		Path file = this.dir.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	Map<String, String> files() {
		try (Stream<Path> paths = Files.walk(this.dir)) {
			Map<String, String> result = new TreeMap<>();
			paths.filter(Files::isRegularFile).forEach((p) -> result.put(this.dir.relativize(p).toString(), read(this.dir.relativize(p).toString())));
			return result;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static final String MEMO = "src/main/java/it/cozy/app/memo/";

	@Test
	void moduleShowsThePlanAndRunningItAgainChangesNothing() throws IOException {
		app();
		Result first = ok("add", "module", "memo", "--label", "메모");
		assertThat(first.out()).isEqualTo("""
				[cozy] 계획
				  만듦  src/main/java/it/cozy/app/memo/package-info.java
				[cozy] 적용했습니다
				[cozy] 다음: 화면은 cozy add crud memo 또는 cozy add screen memo, 다른 모듈의 spi를 쓰려면 cozy link memo --uses <모듈>
				""");
		assertThat(read(MEMO + "package-info.java")).startsWith("/**\n * 메모.\n")
			.contains("@ApplicationModule(allowedDependencies = {})\npackage it.cozy.app.memo;");
		assertThat(ok("add", "module", "memo").out()).contains("그대로  src/main/java/it/cozy/app/memo/package-info.java")
			.contains("[cozy] 바뀐 것 없음");
	}

	@Test
	void dryRunWritesNothingAndJsonListsThePlanAndSlots() throws IOException {
		app();
		ok("add", "module", "memo");
		Map<String, String> before = files();
		Result dry = ok("add", "migration", "memo", "create_memo", "--dry-run");
		assertThat(dry.out()).contains("만듦  src/main/resources/db/migration/V202611081000__memo_create_memo.sql")
			.contains("[cozy] 계획만 보였습니다(--dry-run). 빈칸 1곳");
		assertThat(files()).isEqualTo(before);
		Result json = ok("add", "migration", "memo", "create_memo", "--json");
		assertThat(json.out()).startsWith("{\n  \"recipe\": \"migration\",\n  \"applied\": true,\n  \"plan\": [\n")
			.contains("\"slot\": \"migration-sql\"")
			.contains("\"line\": 3");
	}

	@Test
	void migrationsComeAfterTheLastOneAndKeepTheirName() throws IOException {
		app();
		ok("add", "module", "memo");
		write("src/main/resources/db/migration/V202611081030__memo_create_memo.sql", "CREATE TABLE memo (id INTEGER);\n");
		assertThat(ok("add", "migration", "memo", "add_index").out()).contains("V202611081031__memo_add_index.sql");
		assertThat(ok("add", "migration", "memo", "add_index").out()).contains("그대로  src/main/resources/db/migration/V202611081031__memo_add_index.sql");
		fails("이미 있고 내용이 다릅니다", "add", "migration", "memo", "create_memo");
		fails("설명", "add", "migration", "memo", "Add-Index");
	}

	@Test
	void eventThenListenerInAnotherModuleDeclaresTheDependency() throws IOException {
		app();
		ok("add", "module", "memo");
		ok("add", "module", "activity");
		fails("이벤트 MemoCreated가 없습니다", "add", "listener", "activity", "--on", "memo.MemoCreated");
		ok("add", "event", "memo", "MemoCreated", "--field", "memoId:long", "--field", "title:text");
		assertThat(read(MEMO + "spi/event/MemoCreated.java"))
			.contains("public record MemoCreated(UUID eventId, int schemaVersion, long memoId, String title) implements CozyEvent {")
			.contains("return new MemoCreated(UUID.randomUUID(), 1, memoId, title);");
		assertThat(read("src/test/resources/events/MemoCreated/v1.json"))
			.isEqualTo("{\"eventId\":\"7d4b3c2a-1e0f-4a5b-9c8d-6e7f8a9b0c1d\",\"memoId\":1,\"schemaVersion\":1,\"title\":\"예시 title\"}\n");
		assertThat(read(MEMO + "spi/event/package-info.java")).contains("@NamedInterface(\"spi\")");

		Result listener = ok("add", "listener", "activity", "--on", "memo.MemoCreated");
		assertThat(listener.out()).contains("바꿈  src/main/java/it/cozy/app/activity/package-info.java    allowedDependencies += \"memo :: spi\"")
			.contains("빈칸 1곳")
			.contains("listener-body");
		assertThat(read("src/main/java/it/cozy/app/activity/package-info.java"))
			.contains("@ApplicationModule(allowedDependencies = { \"memo :: spi\" })");
		assertThat(read("src/main/java/it/cozy/app/activity/internal/app/command/MemoCreatedListener.java"))
			.contains("@ApplicationModuleListener\n\tvoid on(MemoCreated event) {")
			.contains("throw new UnsupportedOperationException(\"cozy:slot listener-body\");");
		assertThat(ok("add", "listener", "activity", "--on", "memo.MemoCreated").out()).contains("[cozy] 바뀐 것 없음");
		Result check = cozy("check");
		assertThat(check.code()).isEqualTo(CozyCli.VIOLATIONS);
		assertThat(check.out()).contains("cozy:slot listener-body").contains("오류 1건");
	}

	@Test
	void linkRefusesCyclesModulesWithoutSpiAndOpenModules() throws IOException {
		app();
		ok("add", "module", "memo");
		ok("add", "module", "board");
		fails("memo에 공개된 것(spi)이 없습니다", "link", "board", "--uses", "memo");
		ok("add", "event", "memo", "MemoCreated");
		ok("add", "event", "board", "PostCreated");
		ok("link", "board", "--uses", "memo");
		fails("의존이 순환합니다: memo → board → memo", "link", "memo", "--uses", "board");
		fails("자기 자신", "link", "memo", "--uses", "memo");
		fails("모듈 'nothing'가 없습니다", "link", "memo", "--uses", "nothing");
		write("src/main/java/it/cozy/app/open/package-info.java", "package it.cozy.app.open;\n");
		fails("@ApplicationModule이 없습니다", "link", "open", "--uses", "memo");
	}

	@Test
	void aConflictInTheMiddleOfAPlanWritesNothing() throws IOException {
		app();
		write(MEMO + "spi/MemoFinder.java", "package it.cozy.app.memo.spi;\n\npublic interface MemoFinder {\n}\n");
		fails("MemoFinder.java가 이미 있고 내용이 다릅니다", "add", "crud", "memo", "--entity", "Memo", "--label", "메모", "--icon", "notes",
				"--field", "title:text!:제목");
	}

	@Test
	void crudChecksItsArgumentsBeforeWriting() throws IOException {
		app();
		String[] base = { "add", "crud", "memo", "--entity", "Memo", "--label", "메모", "--icon", "notes" };
		fails("--field가 하나 이상 필요합니다", base);
		fails("필수 text 필드", concat(base, "--field", "body:longtext"));
		fails("instant를 쓰지 않습니다", concat(base, "--field", "title:text!", "--field", "at:instant"));
		fails("created, updated, deleted 중에서", concat(base, "--field", "title:text!", "--publish", "renamed"));
		fails("아이콘 'no-such-icon'가 Tabler Icons에 없습니다",
				"add", "crud", "memo", "--entity", "Memo", "--label", "메모", "--icon", "no-such-icon", "--field", "title:text!");
		fails("crud는 --title를 받지 않습니다", concat(base, "--field", "title:text!", "--title", "메모"));
		fails("모르는 옵션입니다: --colour", concat(base, "--colour", "blue"));
		fails("--label 뒤에 값을 적습니다", "add", "module", "memo", "--label");
	}

	@Test
	void crudCreatesTheWholeSetWithoutSlots() throws IOException {
		app();
		Result result = ok("add", "crud", "memo", "--entity", "Memo", "--label", "메모", "--icon", "notes", "--field", "title:text!:제목",
				"--field", "body:longtext:본문", "--field", "pinned:bool:고정", "--publish", "created");
		assertThat(result.out()).doesNotContain("빈칸").contains("[cozy] 적용했습니다\n");
		assertThat(result.out().lines().filter((l) -> l.startsWith("  만듦")).count()).isEqualTo(33);
		assertThat(read(MEMO + "internal/config/MemoConfiguration.java"))
			.contains("return new CozyNavItem(\"메모\", \"/memo\", 10, \"notes\");")
			.contains("// cozy:append nav");
		assertThat(read(MEMO + "internal/app/command/MemoFacadeImpl.java"))
			.contains("this.events.publishEvent(MemoCreated.of(row.getId(), title));");
		assertThat(read("src/main/resources/db/migration/V202611081000__memo_create_memo.sql"))
			.contains("    title      TEXT    NOT NULL CHECK (length(title) > 0),\n")
			.contains("    pinned     INTEGER NOT NULL DEFAULT 0 CHECK (pinned IN (0, 1)),\n");
		assertThat(ok("check").out()).contains("이상 없음");
		assertThat(ok("add", "crud", "memo", "--entity", "Memo", "--label", "메모", "--icon", "notes", "--field", "title:text!:제목",
				"--field", "body:longtext:본문", "--field", "pinned:bool:고정", "--publish", "created")
			.out()).contains("[cozy] 바뀐 것 없음");
	}

	@Test
	void screenAddsItsNavigationAtTheAnchor() throws IOException {
		app();
		ok("add", "screen", "report", "index", "--pattern", "overview", "--title", "보고", "--field", "total:long:전체", "--nav",
				"보고:chart-bar");
		ok("add", "screen", "report", "monthly", "--pattern", "details", "--title", "월간 보고", "--field", "title:text!:제목", "--nav",
				"월간 보고:calendar");
		String config = read("src/main/java/it/cozy/app/report/internal/config/ReportConfiguration.java");
		assertThat(config).contains("CozyNavItem reportNavItem() {\n\t\treturn new CozyNavItem(\"보고\", \"/report\", 10, \"chart-bar\");")
			.contains("CozyNavItem reportMonthlyNavItem() {\n\t\treturn new CozyNavItem(\"월간 보고\", \"/report/monthly\", 20, \"calendar\");");
		assertThat(config.indexOf("reportMonthlyNavItem")).isLessThan(config.indexOf("// cozy:append nav"));
		assertThat(read("src/main/java/it/cozy/app/report/api/ReportMonthlyController.java"))
			.contains("@RequestMapping(\"/report/monthly\")")
			.contains("cozy:slot view-model");
		assertThat(read("src/main/resources/templates/report/api/index.html"))
			.contains("stat('chart-bar', ${view.total} ?: 0, '전체')");
		fails("--pattern은 overview(개요) 또는 details(상세)입니다", "add", "screen", "report", "list", "--pattern", "list", "--title",
				"목록");
		write("src/main/java/it/cozy/app/report/internal/config/ReportConfiguration.java", config.replace("\t// cozy:append nav\n", ""));
		fails("덧붙일 자리 표시(// cozy:append nav)가 없습니다", "add", "screen", "report", "weekly", "--pattern", "details", "--title",
				"주간", "--nav", "주간:calendar");
	}

	@Test
	void fragmentDescribesItsParametersInTheManifest() throws IOException {
		app();
		ok("add", "module", "memo");
		fails("it.cozy.app.memo.spi.dto.MemoCard가 없습니다", "add", "fragment", "memo", "memo-card", "--param", "item:MemoCard",
				"--purpose", "메모 카드");
		write(MEMO + "spi/dto/MemoCard.java",
				"package it.cozy.app.memo.spi.dto;\n\npublic record MemoCard(String title, int count, Object extra) {\n}\n");
		Result result = ok("add", "fragment", "memo", "memo-card", "--param", "item:MemoCard", "--param", "size:int", "--purpose",
				"메모 카드");
		assertThat(result.out()).contains("빈칸 2곳").contains("fragment-body").contains("fragment-samples");
		assertThat(read("src/main/resources/cozy/fragments.yml")).isEqualTo("""
				# 매니페스트
				fragments:
				  - id: memo/memo-card
				    template: memo/spi/memo-card
				    signature: memoCard(item, size)
				    purpose: 메모 카드.
				    params:
				      - { name: item, type: it.cozy.app.memo.spi.dto.MemoCard, required: true, description: "MemoCard" }
				      - { name: size, type: int, required: true, description: "size" }
				    samples:
				      # cozy:slot fragment-samples — null로 둔 값을 화면 확인용 예시 값으로 바꾸세요
				      default: { item: { title: "예시", count: 1, extra: null }, size: 3 }
				      empty: { item: { title: "예시", count: 1, extra: null }, size: null }
				""");
		assertThat(read("src/main/resources/templates/memo/spi/memo-card.html")).contains("th:fragment=\"memoCard(item, size)\"");
		assertThat(ok("add", "fragment", "memo", "memo-card", "--param", "item:MemoCard", "--param", "size:int", "--purpose",
				"메모 카드").out()).contains("[cozy] 바뀐 것 없음");
	}

	@Test
	void unknownAndStageTwoRecipes() throws IOException {
		app();
		fails("모르는 레시피입니다: table", "add", "table", "memo");
		fails("cozy add query는 2단계 레시피라", "add", "query", "memo", "pinned");
		fails("cozy add 뒤에 레시피 이름을 적습니다", "add");
	}

	static String[] concat(String[] base, String... more) {
		return Stream.concat(Stream.of(base), Stream.of(more)).toArray(String[]::new);
	}
}
