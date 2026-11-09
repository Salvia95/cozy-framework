package io.salvia95.cozy.reference.activity.internal.app.command;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.salvia95.cozy.reference.memo.spi.event.MemoCreated;

/**
 * memo의 MemoCreated를 받아 활동을 남긴다. {@code @ApplicationModuleListener}는 발행한 트랜잭션이 커밋된 뒤 별도
 * 트랜잭션에서 비동기로 실행된다. 같은 이벤트를 두 번 받아도 인박스가 본문을 한 번만 실행한다.
 */
@Component
class MemoCreatedListener {

	private final ActivityService activities;

	MemoCreatedListener(ActivityService activities) {
		this.activities = activities;
	}

	@ApplicationModuleListener
	void on(MemoCreated event) {
		this.activities.record(event.eventId().toString(), "메모를 만들었습니다: " + event.title());
	}
}
