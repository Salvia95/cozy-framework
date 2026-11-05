package io.salvia95.cozy.sqlite.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CozyMigrationsTest {

	@Test
	void appMigrationNameRules() {
		assertThat(CozyMigrations.problem("V202611061200__memo_create_memo.sql", "SQL")).isNull();
		assertThat(CozyMigrations.problem("V202611061200__memo_메모_테이블.sql", "SQL")).isNull();
		assertThat(CozyMigrations.problem("V1__init.sql", "SQL")).isNotNull();
		assertThat(CozyMigrations.problem("V202613061200__memo_x.sql", "SQL")).contains("날짜가 아닙니다");
		assertThat(CozyMigrations.problem("V202611061200__Memo_x.sql", "SQL")).isNotNull();
		assertThat(CozyMigrations.problem("V202611061200__memo.sql", "SQL")).isNotNull();
		assertThat(CozyMigrations.problem("R__memo_view.sql", "SQL")).isNotNull();
		assertThat(CozyMigrations.problem("V202611061200__memo_x", "JDBC")).contains("JDBC");
	}

	@Test
	void instantParsingAcceptsSqliteFormats() {
		assertThat(TextTypeHandlers.parseInstant("2026-11-06 01:02:03")).hasToString("2026-11-06T01:02:03Z");
		assertThat(TextTypeHandlers.parseInstant("2026-11-06T01:02:03.5Z")).hasToString("2026-11-06T01:02:03.500Z");
		assertThat(TextTypeHandlers.parseInstant("2026-11-06T10:02:03+09:00")).hasToString("2026-11-06T01:02:03Z");
	}
}
