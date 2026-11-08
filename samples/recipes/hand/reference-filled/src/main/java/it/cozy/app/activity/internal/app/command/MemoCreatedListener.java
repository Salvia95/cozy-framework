package it.cozy.app.activity.internal.app.command;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import it.cozy.app.memo.spi.event.MemoCreated;
import it.cozy.app.activity.spi.ActivityFacade;

/**
 * memo의 {@link MemoCreated}를 받는다 (cozy add listener). 발행한 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 비동기로 실행되고,
 * 같은 이벤트는 인박스 덕분에 한 번만 처리된다(docs/conventions/events.md). 메서드 이름(on)과 위치가 리스너 id이므로 바꾸지 않는다.
 */
@Component
class MemoCreatedListener {

	private final ActivityFacade facade;

	MemoCreatedListener(ActivityFacade facade) {
		this.facade = facade;
	}

	@ApplicationModuleListener
	void on(MemoCreated event) {
		this.facade.record(event.eventId().toString(), "메모를 만들었습니다: " + event.title());
	}
}
