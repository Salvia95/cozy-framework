package io.salvia95.cozy.sqlite.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.salvia95.cozy.runtime.CozyDiagnosticsContributor;

/**
 * 진단 파일의 {@code sqlite.txt} (PRD §9.5): DB 파일, 실제 PRAGMA 값, Flyway 이력(프레임워크와 앱).
 */
public class SqliteDiagnostics implements CozyDiagnosticsContributor {

	private final DataSource dataSource;

	private final Path databaseFile;

	private final List<String> historyTables;

	private final SettingsReader settings;

	public SqliteDiagnostics(DataSource dataSource, Path databaseFile, List<String> historyTables,
			SettingsReader settings) {
		this.dataSource = dataSource;
		this.databaseFile = databaseFile;
		this.historyTables = List.copyOf(historyTables);
		this.settings = settings;
	}

	@Override
	public String fileName() {
		return "sqlite.txt";
	}

	@Override
	public String content() throws Exception {
		StringBuilder text = new StringBuilder();
		text.append("DB 파일: ").append(this.databaseFile);
		if (Files.isRegularFile(this.databaseFile)) {
			text.append(" (").append(Files.size(this.databaseFile) / 1024).append("KB)");
		}
		text.append('\n');
		text.append("설정: ").append(this.settings.read(this.dataSource)).append("\n\n");
		try (Connection connection = this.dataSource.getConnection(); Statement statement = connection.createStatement()) {
			for (String table : this.historyTables) {
				text.append("== Flyway 이력: ").append(table).append(" ==\n");
				if (!exists(statement, table)) {
					text.append("(테이블 없음)\n\n");
					continue;
				}
				text.append("순서 | 버전 | 설명 | 종류 | 적용 시각 | 성공\n");
				try (ResultSet rs = statement.executeQuery("SELECT installed_rank, version, description, type, "
						+ "installed_on, success FROM " + table + " ORDER BY installed_rank")) {
					while (rs.next()) {
						text.append(rs.getInt(1)).append(" | ").append(rs.getString(2)).append(" | ")
							.append(rs.getString(3)).append(" | ").append(rs.getString(4)).append(" | ")
							.append(rs.getString(5)).append(" | ").append(rs.getBoolean(6)).append('\n');
					}
				}
				text.append('\n');
			}
		}
		return text.toString();
	}

	private static boolean exists(Statement statement, String table) throws Exception {
		try (ResultSet rs = statement
			.executeQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table.replace("'", "''") + "'")) {
			return rs.next() && rs.getInt(1) > 0;
		}
	}

	/** 실제 연결의 PRAGMA 값 */
	@FunctionalInterface
	public interface SettingsReader {

		Map<String, String> read(DataSource dataSource) throws Exception;

	}
}
