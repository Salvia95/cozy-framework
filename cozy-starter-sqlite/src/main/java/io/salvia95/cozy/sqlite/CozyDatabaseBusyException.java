package io.salvia95.cozy.sqlite;

import org.springframework.dao.CannotAcquireLockException;

/**
 * SQLite 쓰기 경합 (SQLITE_BUSY, SQLITE_LOCKED와 그 확장 코드).
 *
 * <p>{@code busy_timeout}(기본 5초) 동안 기다려도 다른 연결이 쓰기 잠금을 놓지 않았다는 뜻이다.
 * 일시적인 실패라서 다시 시도하면 대개 성공한다. {@link CannotAcquireLockException}을 상속하므로
 * Spring의 재시도 도구에서 일시적 오류로 분류된다.
 */
public class CozyDatabaseBusyException extends CannotAcquireLockException {

	private static final long serialVersionUID = 1L;

	public CozyDatabaseBusyException(String message, Throwable cause) {
		super(message, cause);
	}
}
