package io.salvia95.cozy.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

/**
 * 배포한 프레임워크 마이그레이션은 바꾸지 않는다. 설치된 앱의 {@code cozy_schema_history}에 체크섬이 남아 있어서, 주석 한 글자만 바뀌어도 업데이트한 앱이
 * Flyway 검증에서 시작하지 못한다.
 *
 * <p>
 * 모든 {@code cozy-starter-*}의 {@code src/main/resources/db/cozy/**} 마이그레이션을 Flyway로 읽어 체크섬을 구하고, 고정한 목록
 * {@code framework-migration-checksums.txt}와 비교한다. 바꿀 것이 있으면 새 버전 파일을 더하고, 이 테스트가 알려 주는 한 줄을 목록에 더한다.
 */
class ShippedMigrationsTest {

	static final Path ROOT = Path.of("..");

	static final Path PINNED = Path.of("src/test/resources/framework-migration-checksums.txt");

	/** 레포 기준 경로 → Flyway 체크섬 */
	static Map<String, Integer> current() throws IOException {
		Map<String, Integer> checksums = new TreeMap<>();
		List<Path> locations = new ArrayList<>();
		try (Stream<Path> modules = Files.list(ROOT)) {
			for (Path module : modules.filter((m) -> m.getFileName().toString().startsWith("cozy-starter-")).toList()) {
				Path cozy = module.resolve("src/main/resources/db/cozy");
				if (Files.isDirectory(cozy)) {
					try (Stream<Path> dirs = Files.walk(cozy)) {
						dirs.filter(Files::isDirectory).forEach(locations::add);
					}
				}
			}
		}
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite::memory:");
		for (Path location : locations) {
			Flyway flyway = Flyway.configure()
				.dataSource(dataSource)
				.locations("filesystem:" + location.toAbsolutePath().normalize())
				.table("cozy_schema_history")
				.load();
			for (MigrationInfo info : flyway.info().all()) {
				String file = ROOT.relativize(location).resolve(info.getScript()).toString().replace('\\', '/');
				checksums.put(file, info.getChecksum());
			}
		}
		return checksums;
	}

	static Map<String, Integer> pinned() throws IOException {
		Map<String, Integer> pinned = new TreeMap<>();
		for (String line : Files.readAllLines(PINNED, StandardCharsets.UTF_8)) {
			String text = line.strip();
			if (text.isEmpty() || text.startsWith("#")) {
				continue;
			}
			String[] parts = text.split("\\s+");
			pinned.put(parts[0], Integer.valueOf(parts[1]));
		}
		return pinned;
	}

	@Test
	void shippedMigrationsKeepTheirChecksums() throws IOException {
		Map<String, Integer> current = current();
		Map<String, Integer> pinned = pinned();
		assertThat(current).as("프레임워크 마이그레이션을 찾지 못했습니다(경로를 확인하세요)").isNotEmpty();
		List<String> problems = new ArrayList<>();
		pinned.forEach((file, checksum) -> {
			Integer now = current.get(file);
			if (now == null) {
				problems.add(file + " — 배포한 마이그레이션이 없습니다. 지우거나 옮기지 않습니다.");
			}
			else if (!now.equals(checksum)) {
				problems.add(file + " — 배포한 마이그레이션이 바뀌었습니다(체크섬 " + checksum + " → " + now
						+ "). 되돌리고, 바꿀 것은 새 버전 파일로 더하세요.");
			}
		});
		current.forEach((file, checksum) -> {
			if (!pinned.containsKey(file)) {
				problems.add(file + " — 새 마이그레이션입니다. 릴리스에 넣을 내용이면 " + PINNED + "에 다음 줄을 더하세요: " + file + " "
						+ checksum);
			}
		});
		assertThat(problems).as(String.join("\n", problems)).isEmpty();
	}
}
