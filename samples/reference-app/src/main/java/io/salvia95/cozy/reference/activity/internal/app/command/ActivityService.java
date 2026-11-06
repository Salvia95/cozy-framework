package io.salvia95.cozy.reference.activity.internal.app.command;

/** 활동 기록 쓰기 (모듈 안에서만 쓴다). */
public interface ActivityService {

	/**
	 * @param eventId 활동을 만든 이벤트 (같은 이벤트로 두 번 기록하지 않는다)
	 * @param message 무엇이 있었는지
	 */
	void record(String eventId, String message);
}
