package ${package}.home.internal;

import java.time.Clock;
import java.time.Instant;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import ${package}.home.api.NoteAdded;

/**
 * NoteAdded를 받아 최근 활동에 남긴다. 발행한 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 비동기로 돈다.
 * 같은 이벤트가 두 번 와도 인박스가 한 번만 처리한다 (docs/conventions/events.md).
 */
@Component
class NoteAddedListener {

	private final NoteStore store;

	private final Clock clock = Clock.systemUTC();

	NoteAddedListener(NoteStore store) {
		this.store = store;
	}

	@ApplicationModuleListener
	void on(NoteAdded event) {
		this.store.addActivity("메모를 추가했습니다: " + event.title(), Instant.now(this.clock));
	}
}
