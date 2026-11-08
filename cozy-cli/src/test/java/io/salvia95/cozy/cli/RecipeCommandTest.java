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

	/** 위반 → 명령 (ADR-0032 §6): check가 알려 준 명령을 그대로 실행하면 그 위반이 사라진다 */
	@Test
	void checkNamesTheCommandThatFixesAMissingDependency() throws IOException {
		app();
		ok("add", "module", "voc");
		ok("add", "module", "wiki");
		ok("add", "fragment", "wiki", "card", "--param", "title:text", "--purpose", "위키 카드");
		write("src/main/resources/templates/voc/api/list.html", "<div th:replace=\"~{wiki/spi/card :: card('x')}\"></div>\n");
		Result json = cozy("check", "--json");
		assertThat(json.out()).contains("\"id\": \"R3\"").contains("\"command\": \"cozy link voc --uses wiki\"");
		assertThat(cozy("check").out()).contains("에 추가하세요. 고치는 명령: cozy link voc --uses wiki");

		ok("link", "voc", "--uses", "wiki");
		assertThat(cozy("check").out()).doesNotContain("[R3]").contains("[C9]");
	}

	@Test
	void unknownAndStageTwoRecipes() throws IOException {
		app();
		fails("모르는 레시피입니다: table", "add", "table", "memo");
		fails("cozy add event-version는 2단계 레시피라", "add", "event-version", "memo", "MemoCreated");
		fails("cozy add field는 보류한 레시피입니다", "add", "field", "memo", "x:text");
		fails("cozy add 뒤에 레시피 이름을 적습니다", "add");
		fails("cozy enable 뒤에는 events를 적습니다", "enable");
		fails("cozy enable 뒤에는 events를 적습니다", "enable", "mail");
	}

	static final String ACTIVITY = "src/main/java/it/cozy/app/activity/";

	/** ADR-0033 §5.1: spi DTO record. 1단계처럼 파일 전체로 비교한다 */
	@Test
	void dtoCreatesASpiRecordAndRunningItAgainChangesNothing() throws IOException {
		app();
		fails("모듈 'activity'가 없습니다", "add", "dto", "activity", "ActivityItem", "--field", "message:text");
		ok("add", "module", "activity");
		fails("--field가 하나 이상 필요합니다", "add", "dto", "activity", "ActivityItem");
		fails("record에는 필수 여부가 없어 !를 쓰지 않습니다", "add", "dto", "activity", "ActivityItem", "--field", "message:text!");
		fails("it.cozy.app.activity.spi.dto.ActivityLine가 없습니다", "add", "dto", "activity", "ActivityPage", "--field",
				"lines:list:ActivityLine");
		fails("같은 이름의 필드가 둘 이상입니다", "add", "dto", "activity", "ActivityItem", "--field", "a:text", "--field", "a:int");
		fails("Java 예약어", "add", "dto", "activity", "ActivityItem", "--field", "class:text");

		Result first = ok("add", "dto", "activity", "ActivityItem", "--field", "message:text:무엇이 있었는지", "--field",
				"at:text:언제 (yyyy-MM-dd HH:mm)");
		assertThat(first.out()).contains("만듦  " + ACTIVITY + "spi/dto/ActivityItem.java")
			.contains("만듦  " + ACTIVITY + "spi/dto/package-info.java")
			.doesNotContain("빈칸");
		assertThat(read(ACTIVITY + "spi/dto/ActivityItem.java")).isEqualTo("""
				package it.cozy.app.activity.spi.dto;

				/**
				 * ActivityItem: activity가 다른 모듈에 내보내는 값 (모듈 사이 DTO, cozy add dto). 다른 모듈은 activity의 Finder와 공개 fragment로 받는다.
				 *
				 * @param message 무엇이 있었는지
				 * @param at 언제 (yyyy-MM-dd HH:mm)
				 */
				public record ActivityItem(String message, String at) {
				}
				""");
		assertThat(ok("add", "dto", "activity", "ActivityItem", "--field", "message:text:무엇이 있었는지", "--field",
				"at:text:언제 (yyyy-MM-dd HH:mm)").out()).contains("[cozy] 바뀐 것 없음");
		fails("이미 있고 내용이 다릅니다", "add", "dto", "activity", "ActivityItem", "--field", "message:text");

		ok("add", "dto", "activity", "ActivitySummary", "--field", "total:long", "--field", "latest:ActivityItem", "--field",
				"recent:list:ActivityItem", "--field", "since:date", "--field", "at:instant", "--field", "pinned:bool");
		assertThat(read(ACTIVITY + "spi/dto/ActivitySummary.java")).contains("""
				import java.time.Instant;
				import java.time.LocalDate;
				import java.util.List;

				/**
				 * ActivitySummary: activity가 다른 모듈에 내보내는 값 (모듈 사이 DTO, cozy add dto). 다른 모듈은 activity의 Finder와 공개 fragment로 받는다.
				 */
				public record ActivitySummary(long total, ActivityItem latest, List<ActivityItem> recent, LocalDate since, Instant at,
						boolean pinned) {
				""".replace("Instant at,\n\t\tboolean", "Instant at, boolean"));
		// 공개 fragment의 파라미터로 쓸 수 있다 (M10에서 손으로 쓴 ActivityItem의 자리)
		ok("add", "fragment", "activity", "recent-activity", "--param", "items:list:ActivityItem", "--purpose", "최근 활동 목록");
		Result check = cozy("check");
		assertThat(check.out()).doesNotContain("[C8]").doesNotContain("[C5]").contains("fragment-body");
	}

	/** 아키타입 persistence=true, events=false와 같은 의존의 빈 앱 */
	void sqliteApp() throws IOException {
		app();
		write("pom.xml", read("pom.xml").replace("""
				    <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-starter-events</artifactId></dependency>
				""", """
				    <!-- SQLite + MyBatis + Flyway -->
				    <dependency>
				      <groupId>io.salvia95.cozy</groupId>
				      <artifactId>cozy-starter-sqlite</artifactId>
				    </dependency>
				"""));
	}

	/** ADR-0033 §5.5: pom에 cozy-starter-events. 이미 있으면 바뀐 것 없음 */
	@Test
	void enableEventsAddsTheStarterOnce() throws IOException {
		sqliteApp();
		ok("add", "module", "memo");
		fails("이벤트에는 cozy-starter-events가 필요합니다", "add", "event", "memo", "MemoCreated", "--field", "memoId:long");
		Result dry = ok("enable", "events", "--dry-run");
		assertThat(dry.out()).contains("바꿈  pom.xml    의존 += io.salvia95.cozy:cozy-starter-events").contains("--dry-run");
		assertThat(read("pom.xml")).doesNotContain("cozy-starter-events");

		Result enabled = ok("enable", "events");
		assertThat(enabled.out()).contains("[cozy] 적용했습니다");
		assertThat(read("pom.xml")).contains("""
				      <artifactId>cozy-starter-sqlite</artifactId>
				    </dependency>
				    <!-- 모듈 사이 이벤트 (cozy enable events) -->
				    <dependency>
				      <groupId>io.salvia95.cozy</groupId>
				      <artifactId>cozy-starter-events</artifactId>
				    </dependency>
				""");
		assertThat(ok("enable", "events").out()).contains("[cozy] 바뀐 것 없음");
		ok("add", "event", "memo", "MemoCreated", "--field", "memoId:long");
		assertThat(ok("enable", "events", "--json").out()).contains("\"recipe\": \"enable events\"").contains("\"applied\": false");
	}

	@Test
	void enableEventsNeedsTheSqliteStarter() throws IOException {
		app();
		write("pom.xml", read("pom.xml").replace(
				"    <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-starter-events</artifactId></dependency>\n", ""));
		fails("이벤트에는 저장(persistence)이 필요합니다", "enable", "events");
	}

	/**
	 * ADR-0033 §5.6: 리스너는 늘 자기 모듈의 {M}Facade를 받는다. 그래서 Facade가 생기기 전과 후에 같은 명령이 같은 파일을 만든다. 리스너가 만든 빈
	 * Facade는 crud가 채운다.
	 */
	@Test
	void aListenerIsTheSameBeforeAndAfterTheFacadeExists() throws IOException {
		app();
		ok("add", "module", "memo");
		ok("add", "event", "memo", "MemoCreated", "--field", "memoId:long");
		ok("add", "module", "activity");
		Result first = ok("add", "listener", "activity", "--on", "memo.MemoCreated");
		assertThat(first.out()).contains("만듦  " + ACTIVITY + "spi/ActivityFacade.java")
			.contains("만듦  " + ACTIVITY + "internal/app/command/ActivityFacadeImpl.java")
			.contains("만듦  " + ACTIVITY + "internal/app/command/MemoCreatedListener.java");
		String listener = read(ACTIVITY + "internal/app/command/MemoCreatedListener.java");
		assertThat(listener).contains("import it.cozy.app.activity.spi.ActivityFacade;")
			.contains("\tprivate final ActivityFacade facade;\n\n\tMemoCreatedListener(ActivityFacade facade) {")
			.contains("자기 모듈의 ActivityFacade(this.facade)를 부르세요");
		assertThat(read(ACTIVITY + "spi/ActivityFacade.java")).contains("public interface ActivityFacade {\n}\n");
		assertThat(read(ACTIVITY + "internal/app/command/ActivityFacadeImpl.java"))
			.contains("@Service\n@Transactional\nclass ActivityFacadeImpl implements ActivityFacade {\n}\n");
		assertThat(ok("add", "listener", "activity", "--on", "memo.MemoCreated").out()).contains("[cozy] 바뀐 것 없음");
		assertThat(cozy("check").out()).contains("오류 1건").contains("listener-body");

		// 빈 Facade는 crud가 채운다. 그 뒤에도 리스너 명령은 바뀐 것이 없고, 리스너를 지우고 다시 만들어도 같은 파일이다
		Result crud = ok("add", "crud", "activity", "--entity", "Activity", "--label", "활동", "--icon", "activity", "--field",
				"message:text!:내용");
		assertThat(crud.out()).contains("바꿈  " + ACTIVITY + "spi/ActivityFacade.java    listener가 만든 빈 Facade를 crud로 채움");
		assertThat(read(ACTIVITY + "spi/ActivityFacade.java")).contains("long create(");
		assertThat(ok("add", "listener", "activity", "--on", "memo.MemoCreated").out()).contains("[cozy] 바뀐 것 없음");
		Files.delete(this.dir.resolve(ACTIVITY + "internal/app/command/MemoCreatedListener.java"));
		ok("add", "listener", "activity", "--on", "memo.MemoCreated");
		assertThat(read(ACTIVITY + "internal/app/command/MemoCreatedListener.java")).isEqualTo(listener);
	}

	/** 빈 Facade가 아니면(메서드를 더했으면) crud가 덮어쓰지 않는다 */
	@Test
	void crudDoesNotOverwriteAFacadeWithMethods() throws IOException {
		app();
		ok("add", "module", "memo");
		ok("add", "event", "memo", "MemoCreated", "--field", "memoId:long");
		ok("add", "module", "activity");
		ok("add", "listener", "activity", "--on", "memo.MemoCreated");
		write(ACTIVITY + "spi/ActivityFacade.java", read(ACTIVITY + "spi/ActivityFacade.java").replace("public interface ActivityFacade {\n",
				"public interface ActivityFacade {\n\n\tvoid record(String message);\n"));
		fails("ActivityFacade.java가 이미 있고 내용이 다릅니다", "add", "crud", "activity", "--entity", "Activity", "--label", "활동", "--icon",
				"activity", "--field", "message:text!:내용");
	}

	static final String[] QUERY = { "add", "query", "activity", "recent", "--param", "limit:int", "--returns", "list:ActivityItem" };

	static final String[] COMMAND = { "add", "command", "activity", "record", "--param", "eventId:text", "--param", "message:text",
			"--sql", "insert" };

	/** reference-app의 activity: 리스너, dto, query, command (ADR-0033 §6) */
	void activity() throws IOException {
		app();
		ok("add", "module", "memo");
		ok("add", "event", "memo", "MemoCreated", "--field", "memoId:long", "--field", "title:text");
		ok("add", "module", "activity");
		ok("add", "listener", "activity", "--on", "memo.MemoCreated");
		ok("add", "dto", "activity", "ActivityItem", "--field", "message:text", "--field", "at:text");
	}

	/** ADR-0033 §5.2: Finder가 없으면 만들고, 구현·Mapper·행·select를 함께 만든다. 빈칸은 SQL 하나다 */
	@Test
	void queryCreatesTheFinderMapperRowAndSelect() throws IOException {
		activity();
		Result query = ok(QUERY);
		assertThat(query.out()).contains("만듦  " + ACTIVITY + "spi/ActivityFinder.java")
			.contains("만듦  " + ACTIVITY + "internal/app/query/ActivityFinderImpl.java")
			.contains("만듦  " + ACTIVITY + "internal/infra/ActivityMapper.java")
			.contains("만듦  " + ACTIVITY + "internal/infra/ActivityItemRow.java")
			.contains("만듦  src/main/resources/mapper/activity/ActivityMapper.xml")
			.contains("빈칸 1곳")
			.contains("query-sql")
			.contains("마이그레이션에 activity 테이블이 아직 없습니다");
		assertThat(read(ACTIVITY + "spi/ActivityFinder.java")).contains("""
				import java.util.List;

				import it.cozy.app.activity.spi.dto.ActivityItem;
				""").contains("public interface ActivityFinder {\n\n\tList<ActivityItem> recent(int limit);\n}\n");
		assertThat(read(ACTIVITY + "internal/app/query/ActivityFinderImpl.java")).contains("""
				@Service
				@Transactional(readOnly = true)
				class ActivityFinderImpl implements ActivityFinder {

					private final ActivityMapper mapper;

					ActivityFinderImpl(ActivityMapper mapper) {
						this.mapper = mapper;
					}

					@Override
					public List<ActivityItem> recent(int limit) {
						return this.mapper.recent(limit).stream().map((row) -> new ActivityItem(row.message(), row.at())).toList();
					}
				}
				""").contains("import it.cozy.app.activity.internal.infra.ActivityMapper;");
		assertThat(read(ACTIVITY + "internal/infra/ActivityMapper.java"))
			.contains("public interface ActivityMapper {\n\n\tList<ActivityItemRow> recent(@Param(\"limit\") int limit);\n}\n")
			.contains("import org.apache.ibatis.annotations.Param;");
		assertThat(read(ACTIVITY + "internal/infra/ActivityItemRow.java")).contains("public record ActivityItemRow(String message, String at) {");
		assertThat(read("src/main/resources/mapper/activity/ActivityMapper.xml")).contains("""
				<mapper namespace="it.cozy.app.activity.internal.infra.ActivityMapper">

				  <select id="recent" resultType="it.cozy.app.activity.internal.infra.ActivityItemRow">
				    <!-- cozy:slot query-sql — SELECT 문을 쓰세요. 결과 열: message, at (행 ActivityItemRow의 구성 요소). 파라미터: #{limit} -->
				  </select>
				</mapper>
				""");
		assertThat(ok(QUERY).out()).contains("[cozy] 바뀐 것 없음");

		// 에이전트가 SQL과 옮기는 코드를 고친 뒤에도 다시 실행하면 바뀐 것이 없다(본문은 비교하지 않는다)
		write("src/main/resources/mapper/activity/ActivityMapper.xml", read("src/main/resources/mapper/activity/ActivityMapper.xml")
			.replaceAll("<!-- cozy:slot query-sql[^\n]*", "SELECT message, created_at AS at FROM activity LIMIT #{limit}"));
		write(ACTIVITY + "internal/app/query/ActivityFinderImpl.java", read(ACTIVITY + "internal/app/query/ActivityFinderImpl.java")
			.replace("new ActivityItem(row.message(), row.at())", "new ActivityItem(row.message(), \"방금\")"));
		assertThat(ok(QUERY).out()).contains("[cozy] 바뀐 것 없음");
	}

	/** ADR-0033 §5.3: 쓰기는 Facade로. 리스너가 만든 빈 구현에는 Mapper를 받는 생성자를 더한다 */
	@Test
	void commandAddsTheFacadeMethodAndItsSql() throws IOException {
		activity();
		ok(QUERY);
		Result command = ok(COMMAND);
		assertThat(command.out()).contains("바꿈  " + ACTIVITY + "internal/app/command/ActivityFacadeImpl.java    생성자 += ActivityMapper; ")
			.as("바뀐 파일에 남은 빈칸을 모두 보인다(query가 남긴 query-sql 포함)")
			.contains("빈칸 3곳")
			.contains("command-body")
			.contains("command-sql")
			.contains("ApplicationEventPublisher");
		assertThat(read(ACTIVITY + "spi/ActivityFacade.java")).contains("\tvoid record(String eventId, String message);\n}\n");
		assertThat(read(ACTIVITY + "internal/app/command/ActivityFacadeImpl.java")).contains("""
				class ActivityFacadeImpl implements ActivityFacade {

					private final ActivityMapper mapper;

					ActivityFacadeImpl(ActivityMapper mapper) {
						this.mapper = mapper;
					}

					@Override
					public void record(String eventId, String message) {
						// cozy:slot command-body — this.mapper.record(eventId, message)로 SQL을 실행하고""")
			.contains("throw new UnsupportedOperationException(\"cozy:slot command-body\");");
		assertThat(read(ACTIVITY + "internal/infra/ActivityMapper.java"))
			.contains("\tint record(@Param(\"eventId\") String eventId, @Param(\"message\") String message);\n}\n")
			.as("query가 만든 Mapper를 함께 쓴다")
			.contains("List<ActivityItemRow> recent(");
		assertThat(read("src/main/resources/mapper/activity/ActivityMapper.xml")).contains("""
				  <insert id="record">
				    <!-- cozy:slot command-sql — INSERT 문을 쓰세요. 파라미터: #{eventId}, #{message} -->
				  </insert>
				</mapper>
				""");
		assertThat(ok(COMMAND).out()).contains("[cozy] 바뀐 것 없음");
		// 리스너 명령도 여전히 바뀐 것이 없다
		assertThat(ok("add", "listener", "activity", "--on", "memo.MemoCreated").out()).contains("[cozy] 바뀐 것 없음");
		// 빈칸 없는 command: Facade 메서드와 본문 빈칸만
		Result plain = ok("add", "command", "activity", "clear", "--returns", "int");
		assertThat(plain.out()).contains("빈칸 1곳").doesNotContain("ActivityMapper.xml");
		assertThat(read(ACTIVITY + "spi/ActivityFacade.java")).contains("\tint clear();\n}\n");
		Result check = cozy("check");
		assertThat(check.out()).contains("command-body").contains("command-sql").contains("query-sql").doesNotContain("[C8]");
	}

	/** crud가 만든 Finder·Facade·Mapper에 더한다. 옵션 없이 정해진다 */
	@Test
	void queryAndCommandExtendACrudModule() throws IOException {
		app();
		ok("add", "crud", "memo", "--entity", "Memo", "--label", "메모", "--icon", "notes", "--field", "title:text!:제목", "--field",
				"pinned:bool:고정");
		Result query = ok("add", "query", "memo", "countPinned", "--returns", "long");
		assertThat(query.out()).contains("바꿈  " + MEMO + "spi/MemoFinder.java    MemoFinder += long countPinned()")
			.contains("테이블: memo(id, title, pinned, created_at, updated_at)")
			.doesNotContain("Row.java");
		assertThat(read(MEMO + "internal/app/query/MemoFinderImpl.java")).contains("\t\treturn this.mapper.countPinned();");
		assertThat(read("src/main/resources/mapper/memo/MemoMapper.xml")).contains("<select id=\"countPinned\" resultType=\"long\">");
		Result command = ok("add", "command", "memo", "pin", "--param", "id:long", "--param", "pinned:bool", "--returns", "bool",
				"--sql", "update");
		assertThat(command.out()).doesNotContain("생성자 +=");
		assertThat(read(MEMO + "internal/infra/MemoMapper.java")).contains("int pin(@Param(\"id\") long id, @Param(\"pinned\") boolean pinned);");
		assertThat(read("src/main/resources/mapper/memo/MemoMapper.xml")).contains("<update id=\"pin\">");
		assertThat(ok("add", "query", "memo", "countPinned", "--returns", "long").out()).contains("[cozy] 바뀐 것 없음");
	}

	@Test
	void queryAndCommandRefuseWhatTheyCannotDecide() throws IOException {
		activity();
		fails("ActivityLine가 없습니다. 먼저 cozy add dto activity ActivityLine", "add", "query", "activity", "lines", "--returns",
				"list:ActivityLine");
		fails("--returns는 long, int, bool, text", "add", "query", "activity", "x", "--returns", "list:int");
		fails("--returns가 필요합니다", "add", "query", "activity", "x");
		fails("--sql은 insert, update, delete 중 하나입니다", "add", "command", "activity", "x", "--sql", "select");
		fails("메서드 이름 'Recent'는", "add", "query", "activity", "Recent", "--returns", "long");
		fails("같은 이름의 파라미터가 둘 이상입니다", "add", "query", "activity", "x", "--param", "a:int", "--param", "a:text", "--returns",
				"long");
		ok(QUERY);
		fails("같은 이름이 다른 시그니처로 있습니다: ActivityFinder에 List<ActivityItem> recent(int)", "add", "query", "activity", "recent",
				"--param", "limit:long", "--returns", "list:ActivityItem");

		// 일부만 있다: Mapper에서 메서드를 지우면 다시 실행할 수 없다
		String mapper = ACTIVITY + "internal/infra/ActivityMapper.java";
		write(mapper, read(mapper).replace("\n\tList<ActivityItemRow> recent(@Param(\"limit\") int limit);\n", ""));
		Result partial = fails("query recent의 일부만 있습니다", QUERY);
		assertThat(partial.err()).contains("없는 곳: ActivityMapper.recent");

		// Finder가 둘이면 --in이 필요하다
		write(ACTIVITY + "spi/ActivityStatsFinder.java", "package it.cozy.app.activity.spi;\n\npublic interface ActivityStatsFinder {\n}\n");
		fails("Finder가 둘 이상입니다(ActivityFinder, ActivityStatsFinder). --in ActivityFinder|ActivityStatsFinder", "add", "query",
				"activity", "total", "--returns", "long");
		fails("ActivityStatsFinder를 구현한 클래스를 하나로 찾지 못했습니다", "add", "query", "activity", "total", "--returns", "long", "--in",
				"ActivityStatsFinder");

		// 생성자가 있는데 Mapper가 없는 구현은 고치지 않는다
		write(ACTIVITY + "internal/app/query/ActivityStatsFinderImpl.java", """
				package it.cozy.app.activity.internal.app.query;

				import it.cozy.app.activity.spi.ActivityStatsFinder;

				class ActivityStatsFinderImpl implements ActivityStatsFinder {

					private final java.time.Clock clock;

					ActivityStatsFinderImpl(java.time.Clock clock) {
						this.clock = clock;
					}
				}
				""");
		fails("Mapper가 없습니다. 이미 있는 생성자는 고치지 않습니다", "add", "query", "activity", "total", "--returns", "long", "--in",
				"ActivityStatsFinder");
	}

	static String[] concat(String[] base, String... more) {
		return Stream.concat(Stream.of(base), Stream.of(more)).toArray(String[]::new);
	}
}
