package io.salvia95.cozy.sqlite.internal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

import io.salvia95.cozy.sqlite.CozyMigrationLocation;

/**
 * 마이그레이션 순서.
 *
 * <ol>
 * <li>앱 마이그레이션 파일 이름을 검사한다. 규칙과 다르면 아무것도 적용하지 않고 실패한다.</li>
 * <li>적용할 마이그레이션이 있고 DB에 이미 테이블이 있으면, DB를 {@code {dataDir}/backup/}에 복사한다.</li>
 * <li>프레임워크 Flyway({@code cozy_schema_history}, 스타터가 등록한 {@code classpath:db/cozy/*} 위치)를 실행한다.</li>
 * <li>앱 Flyway({@code classpath:db/migration}, 기본 이력 테이블)를 실행한다.</li>
 * </ol>
 * 적용된 파일의 체크섬이 바뀌면 Flyway의 validate가 기동을 멈춘다. 이 기본값은 끄지 않는다.
 */
public class CozyMigrations implements FlywayMigrationStrategy {

	public static final String FRAMEWORK_HISTORY_TABLE = "cozy_schema_history";

	/** {@code V{yyyyMMddHHmm}__{module}_{설명}.sql} */
	static final Pattern APP_MIGRATION_NAME = Pattern.compile("V(\\d{12})__([a-z][a-z0-9]*)_(.+)\\.sql");

	private static final DateTimeFormatter VERSION_TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMddHHmm", Locale.ROOT)
		.withResolverStyle(ResolverStyle.STRICT);

	private final List<CozyMigrationLocation> frameworkLocations;

	private final DatabaseBackup backup;

	public CozyMigrations(List<CozyMigrationLocation> frameworkLocations, DatabaseBackup backup) {
		this.frameworkLocations = List.copyOf(frameworkLocations);
		this.backup = backup;
	}

	@Override
	public void migrate(Flyway app) {
		checkAppMigrationNames(app);
		Flyway framework = frameworkFlyway(app.getConfiguration().getDataSource(),
				app.getConfiguration().getClassLoader());

		int pending = pendingCount(framework) + pendingCount(app);
		if (pending > 0) {
			this.backup.backupIfNotEmpty(app.getConfiguration().getDataSource());
		}
		if (framework != null) {
			framework.migrate();
		}
		app.migrate();
	}

	private Flyway frameworkFlyway(DataSource dataSource, ClassLoader classLoader) {
		if (this.frameworkLocations.isEmpty()) {
			return null;
		}
		String[] locations = this.frameworkLocations.stream()
			.map(CozyMigrationLocation::location)
			.distinct()
			.sorted()
			.toArray(String[]::new);
		return Flyway.configure(classLoader)
			.dataSource(dataSource)
			.table(FRAMEWORK_HISTORY_TABLE)
			.locations(locations)
			// 스타터마다 버전 접두 번호가 달라서(1_x, 2_x …) 한 스타터가 나중에 낮은 번호를 추가할 수 있다
			.outOfOrder(true)
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.failOnMissingLocations(false)
			.load();
	}

	private static int pendingCount(Flyway flyway) {
		return (flyway != null) ? flyway.info().pending().length : 0;
	}

	static void checkAppMigrationNames(Flyway app) {
		List<String> violations = new ArrayList<>();
		for (MigrationInfo info : app.info().all()) {
			if (info.getType().isSynthetic() || info.getType().isBaseline() || info.getType().isUndo()
					|| info.getScript() == null) {
				continue;
			}
			String script = info.getScript();
			String fileName = script.substring(script.lastIndexOf('/') + 1);
			String problem = problem(fileName, info.getType().name());
			if (problem != null) {
				violations.add(script + " → " + problem);
			}
		}
		if (!violations.isEmpty()) {
			throw new IllegalStateException("[cozy] 앱 마이그레이션 파일 이름이 규칙과 다릅니다. "
					+ "V{yyyyMMddHHmm}__{module}_{설명}.sql 형식으로 바꾸세요 (예: V202611061200__memo_create_memo.sql).\n  "
					+ String.join("\n  ", violations));
		}
	}

	static String problem(String fileName, String type) {
		if (!"SQL".equals(type)) {
			return "SQL 버전 마이그레이션(V...)만 씁니다 (" + type + ")";
		}
		Matcher matcher = APP_MIGRATION_NAME.matcher(fileName);
		if (!matcher.matches()) {
			return "이름 형식이 다릅니다";
		}
		try {
			LocalDateTime.parse(matcher.group(1), VERSION_TIMESTAMP);
		}
		catch (DateTimeParseException ex) {
			return "버전 " + matcher.group(1) + "이 yyyyMMddHHmm 날짜가 아닙니다";
		}
		return null;
	}
}
