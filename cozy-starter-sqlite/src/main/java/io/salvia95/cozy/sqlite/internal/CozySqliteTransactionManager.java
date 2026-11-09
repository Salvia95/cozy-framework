package io.salvia95.cozy.sqlite.internal;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteConnection;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.salvia95.cozy.sqlite.CozyDatabaseBusyException;

/**
 * 쓰기 트랜잭션은 {@code BEGIN IMMEDIATE}, 읽기 전용 트랜잭션은 {@code BEGIN DEFERRED}로 시작한다.
 *
 * <p>SQLite 기본(DEFERRED)에서는 읽기로 시작한 트랜잭션이 쓰기로 올라갈 때 다른 연결이 먼저 썼으면
 * {@code busy_timeout}을 기다리지 않고 바로 SQLITE_BUSY로 실패한다. 조회 후 수정하는 흔한 패턴이 여기에 해당한다.
 * IMMEDIATE는 시작할 때 쓰기 잠금을 잡으므로 쓰기 트랜잭션끼리는 {@code busy_timeout} 안에서 줄을 선다.
 *
 * <p>sqlite-jdbc는 {@code setAutoCommit(false)} 때 연결 설정의 모드로 BEGIN을 실행한다. 그래서 쓰기 트랜잭션은
 * 시작 직후의 빈 DEFERRED 트랜잭션(아직 잠금 없음)을 닫고 IMMEDIATE로 다시 연다. 끝나면 연결의 모드를 DEFERRED로 되돌린다.
 * 읽기만 하는 트랜잭션은 {@code @Transactional(readOnly = true)}로 표시해야 쓰기와 줄을 서지 않는다.
 */
public class CozySqliteTransactionManager extends JdbcTransactionManager {

	private static final long serialVersionUID = 1L;

	public CozySqliteTransactionManager(DataSource dataSource, SQLExceptionTranslator exceptionTranslator) {
		super(dataSource);
		setExceptionTranslator(exceptionTranslator);
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		try {
			super.doBegin(transaction, definition);
		}
		catch (CannotCreateTransactionException ex) {
			// BEGIN IMMEDIATE가 busy_timeout 안에 잠금을 못 잡았으면 분류된 예외로 돌려준다
			if (ex.getCause() instanceof CozyDatabaseBusyException busy) {
				throw busy;
			}
			throw ex;
		}
	}

	@Override
	protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
			throws SQLException {
		super.prepareTransactionalConnection(con, definition);
		if (definition.isReadOnly()) {
			return;
		}
		SQLiteConnection sqlite = sqlite(con);
		sqlite.getConnectionConfig().setTransactionMode(TransactionMode.IMMEDIATE);
		try {
			con.setAutoCommit(true);
			con.setAutoCommit(false);
		}
		catch (SQLException ex) {
			// BEGIN IMMEDIATE가 실패하면 sqlite-jdbc에는 "자동 커밋 꺼짐" 표시만 남고 열린 트랜잭션은 없다.
			// 이대로 풀에 돌아가면 다음 트랜잭션이 깨지므로 연결 상태를 자동 커밋으로 되돌린다.
			sqlite.getConnectionConfig().setAutoCommit(true);
			sqlite.getConnectionConfig().setTransactionMode(TransactionMode.DEFERRED);
			DataAccessException translated = getExceptionTranslator().translate("BEGIN IMMEDIATE", null, ex);
			if (translated != null) {
				throw translated;
			}
			throw ex;
		}
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		Object resource = TransactionSynchronizationManager.getResource(obtainDataSource());
		if (resource instanceof ConnectionHolder holder && holder.getConnectionHandle() != null) {
			try {
				sqlite(holder.getConnection()).getConnectionConfig().setTransactionMode(TransactionMode.DEFERRED);
			}
			catch (SQLException ex) {
				logger.debug("SQLite 트랜잭션 모드를 되돌리지 못했습니다", ex);
			}
		}
		super.doCleanupAfterCompletion(transaction);
	}

	private static SQLiteConnection sqlite(Connection con) throws SQLException {
		return con.unwrap(SQLiteConnection.class);
	}
}
