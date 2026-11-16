package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * {@code cozy migrate events}: 옛 이벤트 자리({@code {모듈}/spi/event/})의 이벤트를 이벤트 계약 모듈({@code events/spi/{모듈}/})로
 * 옮기고, 이름을 가리키는 곳과 모듈 의존을 고치고, 저장된 클래스 이름을 바꾸는 마이그레이션을 만든다. 다른 명령처럼 계획을 보이고
 * 원자적으로 적용하며, 두 번째 실행은 바뀐 것이 없다.
 */
class MigrateEventsTest {

	static final String MAIN = "src/main/java/it/cozy/app/";

	static final String TEST = "src/test/java/it/cozy/app/";

	static final String MIGRATIONS = "src/main/resources/db/migration";

	static final String OLD = "it.cozy.app.book.spi.event.BookAdded";

	static final String NEW = "it.cozy.app.events.spi.book.BookAdded";

	@TempDir
	Path dir;

	App app;

	@BeforeEach
	void app() throws IOException {
		this.app = App.of(this.dir);
		this.app.file("pom.xml", """
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
				      <artifactId>cozy-starter-events</artifactId>
				    </dependency>
				  </dependencies>
				</project>
				""".formatted(CozyCliTest.VERSION));
		this.app.module("book", "{}")
			.module("rental", "{ \"book :: spi\" }")
			.module("audit", "{ \"book :: spi\" }")
			.module("notice", "\"book\"")
			.java("book/spi/package-info.java", namedInterface("it.cozy.app.book.spi"))
			.java("book/spi/BookFinder.java", "package it.cozy.app.book.spi;\n\npublic interface BookFinder {\n}\n")
			.java("book/spi/event/package-info.java", namedInterface("it.cozy.app.book.spi.event"))
			.java("book/spi/event/BookAdded.java", """
					package it.cozy.app.book.spi.event;

					import java.util.UUID;

					import io.salvia95.cozy.events.CozyEvent;

					public record BookAdded(UUID eventId, int schemaVersion, long bookId) implements CozyEvent {

						public static BookAdded of(long bookId) {
							return new BookAdded(UUID.randomUUID(), 1, bookId);
						}
					}
					""")
			.java("book/internal/app/command/BookFacadeImpl.java", """
					package it.cozy.app.book.internal.app.command;

					import it.cozy.app.book.spi.event.BookAdded;

					class BookFacadeImpl {

						Object add() {
							return BookAdded.of(1);
						}
					}
					""")
			.java("rental/internal/app/command/BookAddedListener.java", listener("rental", "import it.cozy.app.book.spi.event.BookAdded;\n"
					+ "import it.cozy.app.book.spi.BookFinder;\n"))
			.java("audit/internal/app/command/BookAddedListener.java", listener("audit", "import it.cozy.app.book.spi.event.*;\n"))
			.java("notice/internal/app/command/BookAddedListener.java", listener("notice", "import it.cozy.app.book.spi.event.BookAdded;\n"))
			.file(TEST + "book/spi/event/BookAddedTest.java", """
					package it.cozy.app.book.spi.event;

					class BookAddedTest {
						BookAdded event = BookAdded.of(1);
					}
					""")
			.file(TEST + "rental/FlowTest.java", """
					package it.cozy.app.rental;

					import static it.cozy.app.book.spi.event.BookAdded.of;

					class FlowTest {
						Object event = of(1);
						Object full = it.cozy.app.book.spi.event.BookAdded.of(2);
					}
					""")
			.file("src/test/resources/fixtures/book.json", "{\"type\":\"" + OLD + "\"}\n")
			.file(MIGRATIONS + "/V202611010900__book_create_book.sql", "CREATE TABLE book (id INTEGER PRIMARY KEY);\n");
	}

	static String namedInterface(String pkg) {
		return "@NamedInterface(\"spi\")\npackage " + pkg + ";\n\nimport org.springframework.modulith.NamedInterface;\n";
	}

	static String listener(String module, String imports) {
		return "package it.cozy.app." + module + ".internal.app.command;\n\n" + imports + "\nclass BookAddedListener {\n\n"
				+ "\tvoid on(BookAdded event) {\n\t}\n}\n";
	}

	String read(String file) throws IOException {
		return Files.readString(this.dir.resolve(file), StandardCharsets.UTF_8);
	}

	Result migrate(String... options) {
		String[] args = new String[options.length + 4];
		args[0] = "migrate";
		args[1] = "events";
		System.arraycopy(options, 0, args, 2, options.length);
		args[options.length + 2] = "--project";
		args[options.length + 3] = this.dir.toString();
		return CozyCliTest.run(args);
	}

	List<String> moveMigrations() throws IOException {
		try (Stream<Path> files = Files.list(this.dir.resolve(MIGRATIONS))) {
			return files.map((p) -> p.getFileName().toString())
				.filter((name) -> name.endsWith("__cozy_move_event_contracts.sql"))
				.toList();
		}
	}

	@Test
	void eventsMoveToTheEventsModuleAndEveryReferenceFollows() throws IOException {
		Result result = migrate();
		assertThat(result.code()).as(result.out() + result.err()).isZero();

		assertThat(this.dir.resolve(MAIN + "book/spi/event")).doesNotExist();
		assertThat(read(MAIN + "events/spi/book/BookAdded.java")).startsWith("package it.cozy.app.events.spi.book;\n")
			.contains("public record BookAdded(");
		assertThat(read(MAIN + "events/package-info.java")).contains("@ApplicationModule(allowedDependencies = {})")
			.contains("package it.cozy.app.events;");
		assertThat(read(MAIN + "events/spi/book/package-info.java")).contains("@NamedInterface(\"spi\")")
			.contains("package it.cozy.app.events.spi.book;");
		assertThat(read(MAIN + "book/internal/app/command/BookFacadeImpl.java")).contains("import " + NEW + ";")
			.doesNotContain("spi.event");

		// 테스트 폴더: 같은 패키지의 테스트는 함께 옮기고, static import와 전체 이름도 고친다
		assertThat(this.dir.resolve(TEST + "book/spi/event")).doesNotExist();
		assertThat(read(TEST + "events/spi/book/BookAddedTest.java")).startsWith("package it.cozy.app.events.spi.book;\n");
		assertThat(read(TEST + "rental/FlowTest.java")).contains("import static " + NEW + ".of;")
			.contains("Object full = " + NEW + ".of(2);");
		assertThat(read(MAIN + "audit/internal/app/command/BookAddedListener.java")).contains("import it.cozy.app.events.spi.book.*;");
	}

	@Test
	void modulesDependOnTheEventsModuleAndDropTheOwnerWhenOnlyItsEventsWereUsed() throws IOException {
		Result result = migrate();
		assertThat(result.code()).as(result.out() + result.err()).isZero();

		assertThat(read(MAIN + "book/package-info.java")).contains("allowedDependencies = { \"events :: spi\" }");
		// rental은 book의 Finder를 읽으므로 book :: spi를 남긴다
		assertThat(read(MAIN + "rental/package-info.java")).contains("allowedDependencies = { \"book :: spi\", \"events :: spi\" }");
		// audit은 book의 이벤트만 받았다
		assertThat(read(MAIN + "audit/package-info.java")).contains("allowedDependencies = { \"events :: spi\" }");
		// notice는 형식이 달라 판단하지 않고 손으로 할 일로 알린다
		assertThat(read(MAIN + "notice/package-info.java")).contains("allowedDependencies = { \"book\", \"events :: spi\" }");
		assertThat(result.out()).contains("남은 일 2곳")
			.contains("notice는 book의 이벤트만 받는 것으로 보입니다")
			.contains("src/test/resources/fixtures/book.json")
			.contains("옛 이벤트 이름(it.cozy.app.book.spi.event.…)이 남아 있습니다");
		assertThat(result.out()).contains("[cozy] 다음: mvn clean verify로 확인합니다");
	}

	@Test
	void aMigrationRenamesTheStoredClassNames() throws IOException {
		Result result = migrate();
		assertThat(result.code()).as(result.out() + result.err()).isZero();

		assertThat(moveMigrations()).singleElement().satisfies((name) -> {
			assertThat(name).matches("V\\d{12}__cozy_move_event_contracts\\.sql").isGreaterThan("V202611010900");
			assertThat(read(MIGRATIONS + "/" + name)).contains("-- " + OLD + " → " + NEW + "\n"
					+ "UPDATE cozy_event_publication SET event_type = '" + NEW + "' WHERE event_type = '" + OLD + "';\n"
					+ "UPDATE cozy_event_publication SET listener_id = replace(listener_id, '(" + OLD + ")', '(" + NEW + ")') "
					+ "WHERE instr(listener_id, '(" + OLD + ")') > 0;\n"
					+ "UPDATE cozy_inbox SET listener_id = replace(listener_id, '(" + OLD + ")', '(" + NEW + ")') "
					+ "WHERE instr(listener_id, '(" + OLD + ")') > 0;\n");
		});
	}

	/** cozy-starter-events의 통합 테스트가 적용하는 마이그레이션과 같은 SQL이다 (이름만 다르다) */
	@Test
	void theMigrationIsTheOneTheEventsStarterTestApplies() throws IOException {
		assertThat(migrate().code()).isZero();
		String generated = read(MIGRATIONS + "/" + moveMigrations().getFirst()).replace(OLD, "{old}").replace(NEW, "{new}");
		Path starter = Path.of("..", "cozy-starter-events", "src", "test", "resources", "db", "moved-events",
				"V202611170001__cozy_move_event_contracts.sql");
		String applied = Files.readString(starter, StandardCharsets.UTF_8)
			.replace("io.salvia95.cozy.events.testapp.sample.spi.event.SampleEvent", "{old}")
			.replace("io.salvia95.cozy.events.testapp.sample.SampleEvent", "{new}");
		assertThat(generated).isEqualTo(applied);
	}

	@Test
	void theSecondRunChangesNothing() throws IOException {
		assertThat(migrate().code()).isZero();
		Result again = migrate();
		assertThat(again.code()).isZero();
		assertThat(again.out()).contains("바뀐 것 없음").contains("옮길 것이 없습니다");
		assertThat(moveMigrations()).hasSize(1);
	}

	@Test
	void dryRunWritesNothing() throws IOException {
		Result result = migrate("--dry-run", "--json");
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("\"recipe\": \"migrate events\"")
			.contains("\"file\": \"src/main/java/it/cozy/app/events/spi/book/BookAdded.java\"")
			.contains("\"applied\": false");
		assertThat(this.dir.resolve(MAIN + "book/spi/event/BookAdded.java")).exists();
		assertThat(this.dir.resolve(MAIN + "events")).doesNotExist();
		assertThat(moveMigrations()).isEmpty();
	}

	@Test
	void aTypeThatIsNotAnEventStopsTheMoveBeforeAnythingChanges() throws IOException {
		this.app.java("book/spi/event/BookFormat.java", "package it.cozy.app.book.spi.event;\n\npublic record BookFormat(String name) {\n}\n");
		Result result = migrate();
		assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains("BookFormat는 이벤트(CozyEvent record)가 아니라 옮기지 않습니다")
			.contains("it.cozy.app.book.spi.dto로 옮긴 뒤 다시 실행하세요");
		assertThat(this.dir.resolve(MAIN + "book/spi/event/BookAdded.java")).exists();
		assertThat(this.dir.resolve(MAIN + "events")).doesNotExist();
	}

	@Test
	void anAppWithoutOldEventsHasNothingToMove() throws IOException {
		Path empty = this.dir.resolve("empty");
		App.of(empty).module("memo", "{}");
		Result result = CozyCliTest.run("migrate", "events", "--project", empty.toString());
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("바뀐 것 없음").contains("옮길 것이 없습니다");
	}

	@Test
	void commandsAskToMigrateBeforeUsingAnEventInTheOldPlace() throws IOException {
		Result result = CozyCliTest.run("add", "listener", "audit", "--on", "book.BookAdded", "--project", this.dir.toString());
		assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains("book의 이벤트 BookAdded가 옛 자리(it.cozy.app.book.spi.event)에 있습니다. 먼저 cozy migrate "
				+ "events로");
	}
}
