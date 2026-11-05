package io.salvia95.cozy.sqlite.testapp;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.salvia95.cozy.sqlite.CozyMigrationLocation;

/** SQLite 스타터 테스트용 앱. 프레임워크 스타터처럼 마이그레이션 위치를 등록한다. */
@SpringBootApplication
public class SqliteTestApp {

	@Bean
	CozyMigrationLocation testFrameworkA() {
		return new CozyMigrationLocation("classpath:db/cozy/test-a");
	}

	@Bean
	@ConditionalOnProperty("test.framework-b")
	CozyMigrationLocation testFrameworkB() {
		return new CozyMigrationLocation("classpath:db/cozy/test-b");
	}
}
