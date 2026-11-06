package io.salvia95.cozy.reference.activity;

import java.time.Clock;
import java.time.Instant;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.salvia95.cozy.reference.activity.internal.ActivityMapper;
import io.salvia95.cozy.reference.memo.api.MemoCreated;

/**
 * 이벤트를 받아 활동을 남긴다. {@code @ApplicationModuleListener}는 발행한 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 비동기로
 * 실행된다. 같은 이벤트를 두 번 받아도 인박스가 본문을 한 번만 실행한다 (PRD §8.3).
 */
@Component
class ActivityRecorder {

	private final ActivityMapper mapper;

	private final Clock clock = Clock.systemUTC();

	ActivityRecorder(ActivityMapper mapper) {
		this.mapper = mapper;
	}

	@ApplicationModuleListener
	void on(MemoCreated event) {
		this.mapper.insert(event.eventId().toString(), "메모를 만들었습니다: " + event.title(), Instant.now(this.clock));
	}
}
