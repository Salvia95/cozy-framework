package io.salvia95.cozy.sqlite;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.salvia95.cozy.sqlite.testapp.SampleMapper;

/**
 * SQLite 오류가 분류된 예외로 바뀐다. MyBatis와 JdbcClient가 같은 결과를 낸다.
 */
class ExceptionTranslationTest {

	static Path dataDir;

	static ConfigurableApplicationContext context;

	@BeforeAll
	static void start() {
		dataDir = SqliteTestSupport.newDataDir("translation");
		// 경합 테스트가 오래 기다리지 않도록 busy_timeout을 줄인다
		context = SqliteTestSupport.start(dataDir, "spring.datasource.hikari.data-source-properties[busy_timeout]=200");
	}

	@AfterAll
	static void stop() {
		context.close();
	}

	@Test
	void duplicateKey() {
		SampleMapper mapper = context.getBean(SampleMapper.class);
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		mapper.insertUnique("dup");
		assertThatThrownBy(() -> mapper.insertUnique("dup")).isInstanceOf(DuplicateKeyException.class);
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO unique_item (code) VALUES ('dup')").update())
			.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void notNullViolation() {
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO sample_item (name) VALUES (NULL)").update())
			.isInstanceOf(DataIntegrityViolationException.class)
			.isNotInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void writeContentionBecomesBusyException() throws Exception {
		SampleMapper mapper = context.getBean(SampleMapper.class);
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		try (Connection holder = DriverManager.getConnection("jdbc:sqlite:" + dataDir.resolve("data/app.db"));
				Statement statement = holder.createStatement()) {
			statement.execute("BEGIN EXCLUSIVE");
			assertThatThrownBy(() -> mapper.insertName("blocked")).isInstanceOf(CozyDatabaseBusyException.class)
				.hasMessageContaining("SQLITE_BUSY");
			assertThatThrownBy(() -> jdbc.sql("INSERT INTO sample_item (name) VALUES ('blocked')").update())
				.isInstanceOf(CozyDatabaseBusyException.class);
			statement.execute("ROLLBACK");
		}
		mapper.insertName("unblocked");
	}
}
