package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 검사 C9: 채우지 않은 빈칸.
 */
class SlotRulesTest {

	static final String LISTENER = """
			@Component
			class MemoCreatedListener {

				@ApplicationModuleListener
				void on(MemoCreated event) {
					// cozy:slot listener-body — MemoCreated를 받았을 때 할 일을 쓰세요
					throw new UnsupportedOperationException("cozy:slot listener-body");
				}
			}
			""";

	@Test
	void javaSlotIsReportedOnceAtItsComment() {
		List<Violation> slots = SlotRules.scan("src/main/java/Listener.java", LISTENER);
		assertThat(slots).singleElement().satisfies((v) -> {
			assertThat(v.ruleId()).isEqualTo("C9");
			assertThat(v.line()).isEqualTo(6);
			assertThat(v.isError()).isTrue();
			assertThat(v.format()).isEqualTo("[C9] src/main/java/Listener.java:6 → cozy:slot listener-body — "
					+ "MemoCreated를 받았을 때 할 일을 쓰세요. 다 채우면 이 cozy:slot 표시를 지웁니다.");
		});
	}

	@Test
	void throwWithoutCommentIsStillASlot() {
		String body = "void on(MemoCreated e) {\n  throw new UnsupportedOperationException(\"cozy:slot listener-body\");\n}\n";
		assertThat(SlotRules.scan("A.java", body)).singleElement()
			.extracting(Violation::format)
			.isEqualTo("[C9] A.java:2 → cozy:slot listener-body — 빈칸이 남아 있습니다. 다 채우면 이 cozy:slot 표시를 지웁니다.");
	}

	@Test
	void htmlSqlXmlAndYamlMarkers() {
		assertThat(SlotRules.scan("a.html", "<div>\n<!--/* cozy:slot fragment-body — 파라미터로 받은 값만으로 그리세요 */-->\n</div>"))
			.singleElement()
			.extracting(Violation::format)
			.isEqualTo("[C9] a.html:2 → cozy:slot fragment-body — 파라미터로 받은 값만으로 그리세요. 다 채우면 이 cozy:slot 표시를 지웁니다.");
		assertThat(SlotRules.scan("V1__memo_x.sql", "-- cozy:slot migration-sql — DDL을 쓰세요."))
			.singleElement()
			.extracting(Violation::message)
			.isEqualTo("DDL을 쓰세요. 다 채우면 이 cozy:slot 표시를 지웁니다.");
		assertThat(SlotRules.scan("M.xml", "<select id=\"x\">\n  <!-- cozy:slot query-sql — SELECT를 쓰세요 -->\n</select>"))
			.singleElement()
			.extracting(Violation::reference)
			.isEqualTo("cozy:slot query-sql");
		assertThat(SlotRules.scan("a.yml", "states:\n  # cozy:slot fixture\n")).singleElement()
			.extracting(Violation::line)
			.isEqualTo(2);
	}

	@Test
	void distinctSlotsInOneFileAreAllReported() {
		String body = "// cozy:slot a — 첫째\n// cozy:slot b — 둘째\n// cozy:slot a — 첫째 다시\n";
		assertThat(SlotRules.scan("A.java", body)).extracting(Violation::line).containsExactly(1, 2);
	}

	@Test
	void markerWithoutIdIsReportedEveryTime() {
		assertThat(SlotRules.scan("A.java", "// cozy:slot\n// cozy:slot\n")).extracting(Violation::reference)
			.containsExactly("cozy:slot", "cozy:slot");
	}

	@Test
	void fileWithoutMarkerPasses() {
		assertThat(SlotRules.scan("A.java", "class A { /* slot 이야기 */ }")).isEmpty();
	}

	@Test
	void walksTheSourceTreeInPathOrder(@TempDir Path dir) throws IOException {
		write(dir.resolve("java/b/B.java"), "// cozy:slot b — B");
		write(dir.resolve("java/a/A.java"), "class A {}");
		write(dir.resolve("resources/templates/memo/api/list.html"), "<!--/* cozy:slot list — 목록 */-->");
		write(dir.resolve("resources/db/migration/V1__memo_init.sql"), "-- cozy:slot ddl — 테이블");
		List<Violation> slots = SlotRules.check(dir, "src/main/");
		assertThat(slots).extracting(Violation::location)
			.containsExactly("src/main/java/b/B.java", "src/main/resources/db/migration/V1__memo_init.sql",
					"src/main/resources/templates/memo/api/list.html");
	}

	@Test
	void missingRootHasNoSlots(@TempDir Path dir) {
		assertThat(SlotRules.check(dir.resolve("nothing"), "src/main/")).isEmpty();
	}

	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}
}
