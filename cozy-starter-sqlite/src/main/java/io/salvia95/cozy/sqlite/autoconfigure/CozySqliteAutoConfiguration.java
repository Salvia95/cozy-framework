package io.salvia95.cozy.sqlite.autoconfigure;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.transaction.TransactionManager;

import io.salvia95.cozy.runtime.CozyDataDirectory;
import io.salvia95.cozy.sqlite.CozyMigrationLocation;
import io.salvia95.cozy.sqlite.CozySqliteProperties;
import io.salvia95.cozy.sqlite.internal.CozyMigrations;
import io.salvia95.cozy.sqlite.internal.CozySqliteTransactionManager;
import io.salvia95.cozy.sqlite.internal.DatabaseBackup;
import io.salvia95.cozy.sqlite.internal.SqliteDiagnostics;
import io.salvia95.cozy.sqlite.internal.SqliteExceptionTranslator;
import io.salvia95.cozy.sqlite.internal.TextTypeHandlers;

/**
 * SQLite + MyBatis + Flyway 자동 설정 (PRD §6.3).
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class,
		before = { JdbcTemplateAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
				MybatisAutoConfiguration.class, FlywayAutoConfiguration.class })
@EnableConfigurationProperties(CozySqliteProperties.class)
public class CozySqliteAutoConfiguration {

	private static final Log logger = LogFactory.getLog(CozySqliteAutoConfiguration.class);

	/** JdbcTemplate·JdbcClient는 이 빈을 쓴다(Boot는 유일한 SQLExceptionTranslator 빈을 주입한다). */
	@Bean
	@ConditionalOnMissingBean
	SQLExceptionTranslator cozySqliteExceptionTranslator() {
		return new SqliteExceptionTranslator();
	}

	/** 쓰기 트랜잭션은 BEGIN IMMEDIATE (ADR-0020). Boot의 DataSourceTransactionManager는 이 빈이 있으면 물러난다. */
	@Bean
	@ConditionalOnMissingBean(TransactionManager.class)
	CozySqliteTransactionManager transactionManager(DataSource dataSource, SQLExceptionTranslator translator) {
		return new CozySqliteTransactionManager(dataSource, translator);
	}

	/** MyBatis도 같은 번역기를 쓰게 한다. MybatisAutoConfiguration의 SqlSessionTemplate은 이 빈이 있으면 물러난다. */
	@Bean
	@ConditionalOnMissingBean
	SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory, MybatisProperties properties,
			SQLExceptionTranslator translator) {
		ExecutorType executorType = (properties.getExecutorType() != null) ? properties.getExecutorType()
				: ExecutorType.SIMPLE;
		return new SqlSessionTemplate(sqlSessionFactory, executorType,
				new MyBatisExceptionTranslator(() -> translator, true));
	}

	@Bean
	ConfigurationCustomizer cozyTypeHandlers() {
		return (configuration) -> TextTypeHandlers.registerAll(configuration.getTypeHandlerRegistry());
	}

	@Bean
	DatabaseBackup cozyDatabaseBackup(Environment environment, CozyDataDirectory dataDirectory,
			CozySqliteProperties properties) {
		return new DatabaseBackup(databaseFile(environment), dataDirectory.backup(), properties.backup().keep(),
				Clock.systemDefaultZone());
	}

	@Bean
	@ConditionalOnMissingBean
	CozyMigrations cozyMigrations(ObjectProvider<CozyMigrationLocation> locations, DatabaseBackup backup) {
		return new CozyMigrations(locations.orderedStream().toList(), backup);
	}

	/** 진단 파일의 sqlite.txt: DB 파일, PRAGMA, Flyway 이력 (PRD §9.5) */
	@Bean
	SqliteDiagnostics cozySqliteDiagnostics(DataSource dataSource, Environment environment) {
		return new SqliteDiagnostics(dataSource, databaseFile(environment),
				List.of(CozyMigrations.FRAMEWORK_HISTORY_TABLE,
						environment.getProperty("spring.flyway.table", "flyway_schema_history")),
				CozySqliteAutoConfiguration::readSettings);
	}

	/** 기동할 때 실제 연결 설정값을 로그로 남긴다. */
	@Bean
	InitializingBean cozySqliteSettingsReport(DataSource dataSource, Environment environment) {
		return () -> logger.info("[cozy] SQLite " + databaseFile(environment) + " " + readSettings(dataSource));
	}

	/** 연결 하나에서 실제로 적용된 PRAGMA 값. */
	public static Map<String, String> readSettings(DataSource dataSource) throws SQLException {
		Map<String, String> settings = new LinkedHashMap<>();
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			for (String pragma : new String[] { "journal_mode", "busy_timeout", "foreign_keys", "synchronous" }) {
				try (ResultSet rs = statement.executeQuery("PRAGMA " + pragma)) {
					rs.next();
					settings.put(pragma, rs.getString(1));
				}
			}
			try (ResultSet rs = statement.executeQuery("SELECT sqlite_version()")) {
				rs.next();
				settings.put("sqlite_version", rs.getString(1));
			}
		}
		return settings;
	}

	private static Path databaseFile(Environment environment) {
		return Path.of(environment.getRequiredProperty("cozy.sqlite.resolved-file"));
	}
}
