package io.salvia95.cozy.cli.internal;

/** 명령을 계속할 수 없는 상태. 메시지를 그대로 보이고 종료 코드 2로 끝낸다 */
public final class CliException extends RuntimeException {

	public CliException(String message) {
		super(message);
	}
}
