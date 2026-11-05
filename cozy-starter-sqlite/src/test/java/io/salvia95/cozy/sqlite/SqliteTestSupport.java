package io.salvia95.cozy.sqlite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import io.salvia95.cozy.sqlite.testapp.SqliteTestApp;

final class SqliteTestSupport {

	private SqliteTestSupport() {
	}

	static Path newDataDir(String name) {
		return Path.of("target/test-data/" + name + "-" + UUID.randomUUID()).toAbsolutePath();
	}

	static ConfigurableApplicationContext start(Path dataDir, String... properties) {
		List<String> all = new ArrayList<>();
		all.add("cozy.data-dir=" + dataDir);
		all.addAll(List.of(properties));
		return new SpringApplicationBuilder(SqliteTestApp.class).web(WebApplicationType.NONE)
			.properties(all.toArray(String[]::new))
			.run();
	}
}
