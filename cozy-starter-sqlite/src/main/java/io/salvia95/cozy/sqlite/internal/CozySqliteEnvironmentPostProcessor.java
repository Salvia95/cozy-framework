package io.salvia95.cozy.sqlite.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import io.salvia95.cozy.runtime.internal.CozyRuntimeEnvironmentPostProcessor;

/**
 * SQLite, Hikari, Flyway, MyBatis의 기본값을 정한다.
 *
 * <p>런타임 EnvironmentPostProcessor가 데이터 루트를 정한 다음에 실행된다. 기본값은 가장 낮은 우선순위라 앱 설정으로 바꿀 수 있다.
 */
public class CozySqliteEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	public static final int ORDER = CozyRuntimeEnvironmentPostProcessor.ORDER + 1;

	/** sqlite-jdbc 연결 설정 (SQLiteConfig.Pragma 키). 연결마다 적용된다. */
	public static final Map<String, String> CONNECTION_PRAGMAS = Map.of("journal_mode", "WAL", "busy_timeout", "5000",
			"foreign_keys", "true", "synchronous", "NORMAL");

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Path dataDir = Path.of(environment.getRequiredProperty("cozy.data-dir"));
		Path file = resolveFile(dataDir, environment.getProperty("cozy.sqlite.file"));
		try {
			Files.createDirectories(file.getParent());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] DB 폴더를 만들 수 없습니다: " + file.getParent(), ex);
		}

		Map<String, Object> defaults = new LinkedHashMap<>();
		defaults.put("cozy.sqlite.resolved-file", file.toString());
		defaults.put("spring.datasource.url", "jdbc:sqlite:" + file);
		defaults.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
		defaults.put("spring.datasource.hikari.pool-name", "cozy-sqlite");
		defaults.put("spring.datasource.hikari.maximum-pool-size", "4");
		CONNECTION_PRAGMAS
			.forEach((key, value) -> defaults.put("spring.datasource.hikari.data-source-properties[" + key + "]", value));
		// 앱 Flyway. 프레임워크 Flyway가 먼저 테이블을 만들기 때문에, 앱 이력이 없는 비어 있지 않은 스키마를 0번 기준선에서 시작한다.
		defaults.put("spring.flyway.baseline-on-migrate", "true");
		defaults.put("spring.flyway.baseline-version", "0");
		defaults.put("mybatis.mapper-locations", "classpath*:mapper/*/*.xml");
		defaults.put("mybatis.configuration.map-underscore-to-camel-case", "true");
		defaults.put("mybatis.configuration.arg-name-based-constructor-auto-mapping", "true");
		DefaultPropertiesPropertySource.addOrMerge(defaults, environment.getPropertySources());
	}

	static Path resolveFile(Path dataDir, String configured) {
		if (configured == null || configured.isBlank()) {
			return dataDir.resolve("data").resolve("app.db").toAbsolutePath().normalize();
		}
		Path path = Path.of(configured);
		return (path.isAbsolute() ? path : dataDir.resolve(path)).toAbsolutePath().normalize();
	}
}
