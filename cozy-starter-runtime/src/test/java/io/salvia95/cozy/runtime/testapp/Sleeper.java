package io.salvia95.cozy.runtime.testapp;

/** 업데이트 테스트에서 "실행 중인 이전 앱"을 흉내 내는 프로세스. 테스트가 끝낸다. */
public final class Sleeper {

	private Sleeper() {
	}

	public static void main(String[] args) throws InterruptedException {
		Thread.sleep(600_000);
	}
}
