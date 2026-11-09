package io.salvia95.cozy.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.salvia95.cozy.sqlite.internal.CozyMigrations;

/**
 * 프레임워크와 앱 마이그레이션의 분리, 백업, 이름 규칙, 체크섬 검증 (PRD §6.3).
 */
class MigrationTest {

	private static final String APP_HISTORY = "SELECT installed_rank, version, script, checksum, installed_on, success "
			+ "FROM flyway_schema_history ORDER BY installed_rank";

	@Test
	void frameworkMigrationsDoNotAffectAppMigrations() throws Exception {
		Path dataDir = SqliteTestSupport.newDataDir("migration");

		List<Map<String, Object>> appHistoryBefore;
		try (ConfigurableApplicationContext first = SqliteTestSupport.start(dataDir)) {
			JdbcClient jdbc = first.getBean(JdbcClient.class);
			assertThat(tables(jdbc)).contains("cozy_schema_history", "flyway_schema_history", "cozy_test_a",
					"sample_item", "unique_item");
			assertThat(versions(jdbc, CozyMigrations.FRAMEWORK_HISTORY_TABLE)).containsExactly("1.1");
			assertThat(versions(jdbc, "flyway_schema_history")).containsExactly("0", "202611061000");
			appHistoryBefore = jdbc.sql(APP_HISTORY).query().listOfRows();
		}
		assertThat(backups(dataDir)).as("첫 설치(빈 DB)는 백업하지 않는다").isEmpty();

		// 프레임워크 마이그레이션 추가 (새 스타터나 새 버전)
		try (ConfigurableApplicationContext second = SqliteTestSupport.start(dataDir, "test.framework-b=true")) {
			JdbcClient jdbc = second.getBean(JdbcClient.class);
			assertThat(tables(jdbc)).contains("cozy_test_b");
			assertThat(versions(jdbc, CozyMigrations.FRAMEWORK_HISTORY_TABLE)).containsExactly("1.1", "9.1");
			assertThat(jdbc.sql(APP_HISTORY).query().listOfRows()).as("앱 이력은 그대로").isEqualTo(appHistoryBefore);
		}
		assertThat(backups(dataDir)).as("적용할 마이그레이션이 있으면 백업한다").hasSize(1);

		// 바뀐 것이 없으면 백업하지 않는다
		try (ConfigurableApplicationContext third = SqliteTestSupport.start(dataDir, "test.framework-b=true")) {
			assertThat(third.isActive()).isTrue();
		}
		assertThat(backups(dataDir)).hasSize(1);

		// 앱 마이그레이션 추가
		try (ConfigurableApplicationContext fourth = SqliteTestSupport.start(dataDir, "test.framework-b=true",
				"spring.flyway.locations=classpath:db/migration-next")) {
			JdbcClient jdbc = fourth.getBean(JdbcClient.class);
			assertThat(versions(jdbc, "flyway_schema_history")).containsExactly("0", "202611061000", "202611061100");
			assertThat(versions(jdbc, CozyMigrations.FRAMEWORK_HISTORY_TABLE)).containsExactly("1.1", "9.1");
		}
		assertThat(backups(dataDir)).hasSize(2);
	}

	@Test
	void changedAppliedMigrationStopsStartup() {
		Path dataDir = SqliteTestSupport.newDataDir("checksum");
		SqliteTestSupport.start(dataDir).close();
		assertThatThrownBy(
				() -> SqliteTestSupport.start(dataDir, "spring.flyway.locations=classpath:db/migration-changed"))
			.hasStackTraceContaining("checksum mismatch");
	}

	@Test
	void appMigrationNameRuleIsEnforcedBeforeAnythingIsApplied() {
		Path dataDir = SqliteTestSupport.newDataDir("badname");
		assertThatThrownBy(
				() -> SqliteTestSupport.start(dataDir, "spring.flyway.locations=classpath:db/migration-badname"))
			.hasStackTraceContaining("[cozy] 앱 마이그레이션 파일 이름이 규칙과 다릅니다")
			.hasStackTraceContaining("V1__init.sql");
	}

	@Test
	void keepsOnlyRecentBackups() throws Exception {
		Path dataDir = SqliteTestSupport.newDataDir("prune");
		SqliteTestSupport.start(dataDir).close();
		Files.createDirectories(dataDir.resolve("backup"));
		for (int i = 0; i < 7; i++) {
			Files.writeString(dataDir.resolve("backup/app-20200101-00000" + i + "-000.db"), "old");
		}
		SqliteTestSupport.start(dataDir, "spring.flyway.locations=classpath:db/migration-next").close();
		List<String> names = backups(dataDir).stream().map((p) -> p.getFileName().toString()).toList();
		assertThat(names).hasSize(5).noneMatch((n) -> n.startsWith("app-20200101-000000"))
			.noneMatch((n) -> n.startsWith("app-20200101-000001"))
			.noneMatch((n) -> n.startsWith("app-20200101-000002"));
		assertThat(names.get(4)).doesNotStartWith("app-2020");
	}


	private static List<String> tables(JdbcClient jdbc) {
		return jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table'").query(String.class).list();
	}

	private static List<String> versions(JdbcClient jdbc, String table) {
		return jdbc.sql("SELECT version FROM " + table + " WHERE version IS NOT NULL ORDER BY installed_rank")
			.query(String.class)
			.list();
	}

	private static List<Path> backups(Path dataDir) throws IOException {
		Path dir = dataDir.resolve("backup");
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.sorted().toList();
		}
	}
}
