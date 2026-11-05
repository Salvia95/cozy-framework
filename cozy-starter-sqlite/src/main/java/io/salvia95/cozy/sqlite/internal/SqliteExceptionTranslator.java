package io.salvia95.cozy.sqlite.internal;

import java.sql.SQLException;

import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.AbstractFallbackSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;

import io.salvia95.cozy.sqlite.CozyDatabaseBusyException;

/**
 * SQLite 결과 코드로 예외를 분류한다. Spring의 sql-error-codes.xml에는 SQLite가 없다.
 *
 * <ul>
 * <li>SQLITE_BUSY, SQLITE_LOCKED(확장 코드 포함) → {@link CozyDatabaseBusyException}</li>
 * <li>PRIMARY KEY, UNIQUE, ROWID 위반 → {@link DuplicateKeyException}</li>
 * <li>그 밖의 제약 위반(NOT NULL, FOREIGN KEY, CHECK) → {@link DataIntegrityViolationException}</li>
 * </ul>
 * JdbcTemplate·JdbcClient와 MyBatis가 같은 번역기를 쓴다.
 */
public class SqliteExceptionTranslator extends AbstractFallbackSQLExceptionTranslator {

	private static final int SQLITE_BUSY = 5;

	private static final int SQLITE_LOCKED = 6;

	private static final int SQLITE_CONSTRAINT = 19;

	public SqliteExceptionTranslator() {
		setFallbackTranslator(new SQLExceptionSubclassTranslator());
	}

	@Override
	protected @Nullable DataAccessException doTranslate(String task, @Nullable String sql, SQLException ex) {
		SQLiteException sqlite = findSqliteException(ex);
		if (sqlite == null) {
			return null;
		}
		SQLiteErrorCode code = sqlite.getResultCode();
		int primary = code.code & 0xff;
		String detail = buildMessage(task, sql, ex);
		if (primary == SQLITE_BUSY || primary == SQLITE_LOCKED) {
			return new CozyDatabaseBusyException("[cozy] SQLite 쓰기 경합(" + code.name()
					+ "): 다른 연결이 잠금을 놓지 않았습니다. 잠시 뒤 다시 시도하세요. " + detail, ex);
		}
		if (primary == SQLITE_CONSTRAINT) {
			if (code == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY || code == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE
					|| code == SQLiteErrorCode.SQLITE_CONSTRAINT_ROWID) {
				return new DuplicateKeyException(detail, ex);
			}
			return new DataIntegrityViolationException(detail, ex);
		}
		return null;
	}

	private static @Nullable SQLiteException findSqliteException(Throwable ex) {
		for (Throwable current = ex; current != null; current = current.getCause()) {
			if (current instanceof SQLiteException sqlite) {
				return sqlite;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return null;
	}
}
