package io.salvia95.cozy.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.salvia95.cozy.runtime.CozyDiagnosticsContributor;
import io.salvia95.cozy.sqlite.testapp.SampleItem;
import io.salvia95.cozy.sqlite.testapp.SampleMapper;

/**
 * 연결 설정과 시간·UUID·boolean 왕복 변환.
 */
class SqliteSettingsAndTypesTest {

	static Path dataDir;

	static ConfigurableApplicationContext context;

	@BeforeAll
	static void start() {
		dataDir = SqliteTestSupport.newDataDir("settings");
		context = SqliteTestSupport.start(dataDir);
	}

	@AfterAll
	static void stop() {
		context.close();
	}

	@Test
	void diagnosticsReportSettingsAndBothMigrationHistories() throws Exception {
		CozyDiagnosticsContributor diagnostics = context.getBeansOfType(CozyDiagnosticsContributor.class)
			.values()
			.stream()
			.filter((c) -> c.fileName().equals("sqlite.txt"))
			.findFirst()
			.orElseThrow();
		String text = diagnostics.content();
		assertThat(text).contains("DB 파일: " + dataDir.resolve("data/app.db"))
			.contains("journal_mode=wal")
			.contains("== Flyway 이력: cozy_schema_history ==")
			.contains("== Flyway 이력: flyway_schema_history ==")
			.containsPattern("\\| 202611061000 \\| sample create tables \\| SQL \\| .+ \\| true");
	}

	@Test
	void databaseFileLivesInDataDirectory() {
		assertThat(dataDir.resolve("data/app.db")).isRegularFile();
		HikariDataSource hikari = context.getBean(HikariDataSource.class);
		assertThat(hikari.getJdbcUrl()).isEqualTo("jdbc:sqlite:" + dataDir.resolve("data/app.db"));
		assertThat(hikari.getMaximumPoolSize()).isEqualTo(4);
	}

	@Test
	void everyPooledConnectionHasPragmas() throws Exception {
		DataSource dataSource = context.getBean(DataSource.class);
		List<Connection> connections = new ArrayList<>();
		try {
			for (int i = 0; i < 4; i++) {
				connections.add(dataSource.getConnection());
			}
			for (Connection connection : connections) {
				assertThat(pragma(connection, "journal_mode")).isEqualTo("wal");
				assertThat(pragma(connection, "busy_timeout")).isEqualTo("5000");
				assertThat(pragma(connection, "foreign_keys")).isEqualTo("1");
				assertThat(pragma(connection, "synchronous")).isEqualTo("1"); // NORMAL
			}
		}
		finally {
			for (Connection connection : connections) {
				connection.close();
			}
		}
		Map<String, String> reported = io.salvia95.cozy.sqlite.autoconfigure.CozySqliteAutoConfiguration
			.readSettings(dataSource);
		assertThat(reported).containsEntry("journal_mode", "wal").containsKey("sqlite_version");
	}

	@Test
	void timeUuidAndBooleanRoundTrip() {
		SampleMapper mapper = context.getBean(SampleMapper.class);
		Instant instant = Instant.parse("2026-11-06T01:02:03.123456789Z");
		LocalDateTime localDateTime = LocalDateTime.parse("2026-11-06T10:02:03.000000001");
		LocalDate localDate = LocalDate.parse("2026-02-28");
		UUID uid = UUID.fromString("0F8FAD5B-D9CB-469F-A165-70867728950E");

		mapper.insert(new SampleItem(null, "round-trip", instant, localDateTime, localDate, uid, true, null));
		mapper.insert(new SampleItem(null, "round-trip-false", Instant.parse("2026-11-06T01:02:03Z"), null, null, null,
				false, false));

		SampleItem read = mapper.findByName("round-trip");
		assertThat(read.createdAt()).isEqualTo(instant);
		assertThat(read.localDt()).isEqualTo(localDateTime);
		assertThat(read.localD()).isEqualTo(localDate);
		assertThat(read.uid()).isEqualTo(uid);
		assertThat(read.flag()).isTrue();
		assertThat(read.maybe()).isNull();
		SampleItem second = mapper.findByName("round-trip-false");
		assertThat(second.flag()).isFalse();
		assertThat(second.maybe()).isFalse();
		assertThat(second.localDt()).isNull();

		// 저장 형식: 자릿수를 고정한 ISO-8601 TEXT, 소문자 UUID, INTEGER 0/1
		var raw = context.getBean(JdbcClient.class)
			.sql("SELECT created_at, typeof(created_at) t1, local_dt, local_d, uid, flag, typeof(flag) t2 FROM sample_item WHERE name = 'round-trip'")
			.query()
			.singleRow();
		assertThat(raw).containsEntry("created_at", "2026-11-06T01:02:03.123456789Z")
			.containsEntry("t1", "text")
			.containsEntry("local_dt", "2026-11-06T10:02:03.000000001")
			.containsEntry("local_d", "2026-02-28")
			.containsEntry("uid", "0f8fad5b-d9cb-469f-a165-70867728950e")
			.containsEntry("t2", "integer");
		assertThat(((Number) raw.get("flag")).intValue()).isEqualTo(1);
		var secondRaw = context.getBean(JdbcClient.class)
			.sql("SELECT created_at FROM sample_item WHERE name = 'round-trip-false'")
			.query(String.class)
			.single();
		assertThat(secondRaw).isEqualTo("2026-11-06T01:02:03.000000000Z");
		// 고정 자릿수라 문자열 정렬이 시간 순서와 같다
		assertThat(secondRaw).isLessThan("2026-11-06T01:02:03.123456789Z");
	}

	@Test
	void sqliteCurrentTimestampIsReadAsUtc() {
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		jdbc.sql("INSERT INTO sample_item (name, created_at, local_dt, flag) VALUES ('sqlite-now', '2026-11-06 01:02:03', '2026-11-06 10:02:03', 0)")
			.update();
		SampleItem read = context.getBean(SampleMapper.class).findByName("sqlite-now");
		assertThat(read.createdAt()).isEqualTo(Instant.parse("2026-11-06T01:02:03Z"));
		assertThat(read.localDt()).isEqualTo(LocalDateTime.parse("2026-11-06T10:02:03"));
	}

	private static String pragma(Connection connection, String name) throws Exception {
		try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA " + name)) {
			rs.next();
			return rs.getString(1);
		}
	}
}
