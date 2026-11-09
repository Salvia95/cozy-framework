package io.salvia95.cozy.sqlite.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 마이그레이션 전 DB 백업.
 *
 * <p>{@code VACUUM INTO}로 일관된 사본을 만든다. 열린 연결과 WAL 파일이 있어도 안전하다.
 * 파일 이름은 {@code {db이름}-{yyyyMMdd-HHmmss-SSS}.db}이고, 이름순으로 최근 {@code keep}개만 남긴다.
 */
public class DatabaseBackup {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS", Locale.ROOT);

	private static final Log logger = LogFactory.getLog(DatabaseBackup.class);

	private final Path databaseFile;

	private final Path backupDir;

	private final int keep;

	private final Clock clock;

	public DatabaseBackup(Path databaseFile, Path backupDir, int keep, Clock clock) {
		if (keep < 1) {
			throw new IllegalArgumentException("[cozy] cozy.sqlite.backup.keep은 1 이상이어야 합니다: " + keep);
		}
		this.databaseFile = databaseFile;
		this.backupDir = backupDir;
		this.keep = keep;
		this.clock = clock;
	}

	/** 사용자 테이블이 하나라도 있으면 백업한다. 첫 설치(빈 DB)는 건너뛴다. @return 만든 백업 파일 또는 null */
	public Path backupIfNotEmpty(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			try (ResultSet tables = statement
				.executeQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
				tables.next();
				if (tables.getInt(1) == 0) {
					return null;
				}
			}
			Files.createDirectories(this.backupDir);
			Path target = this.backupDir.resolve(prefix() + LocalDateTime.now(this.clock).format(STAMP) + ".db");
			statement.execute("VACUUM INTO '" + target.toString().replace("'", "''") + "'");
			logger.info("[cozy] 마이그레이션 전 DB를 백업했습니다: " + target);
			prune();
			return target;
		}
		catch (SQLException ex) {
			throw new IllegalStateException("[cozy] 마이그레이션 전 DB 백업에 실패해 마이그레이션을 멈춥니다: " + this.databaseFile, ex);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 백업 폴더를 쓸 수 없습니다: " + this.backupDir, ex);
		}
	}

	List<Path> backups() throws IOException {
		String prefix = prefix();
		try (Stream<Path> files = Files.list(this.backupDir)) {
			return files.filter((p) -> {
				String name = p.getFileName().toString();
				return name.startsWith(prefix) && name.endsWith(".db");
			}).sorted().toList();
		}
	}

	private void prune() throws IOException {
		List<Path> all = backups();
		for (int i = 0; i < all.size() - this.keep; i++) {
			Files.deleteIfExists(all.get(i));
		}
	}

	private String prefix() {
		String name = this.databaseFile.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return ((dot > 0) ? name.substring(0, dot) : name) + "-";
	}
}
