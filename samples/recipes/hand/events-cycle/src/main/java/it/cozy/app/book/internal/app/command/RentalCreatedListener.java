package it.cozy.app.book.internal.app.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import it.cozy.app.events.spi.rental.RentalCreated;
import it.cozy.app.book.spi.BookFacade;

/**
 * rental의 {@link RentalCreated}를 받는다 (cozy add listener). 발행한 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 비동기로 실행되고,
 * 같은 이벤트는 인박스 덕분에 한 번만 처리된다(docs/conventions/events.md). 메서드 이름(on)과 위치가 리스너 id이므로 바꾸지 않는다.
 */
@Component
class RentalCreatedListener {

	private static final Logger LOG = LoggerFactory.getLogger(RentalCreatedListener.class);

	private final BookFacade facade;

	RentalCreatedListener(BookFacade facade) {
		this.facade = facade;
	}

	@ApplicationModuleListener
	void on(RentalCreated event) {
		// 흐름만 보이는 예시라 기록만 남긴다. 업무 처리는 this.facade의 메서드를 부른다
		LOG.info("대여 {}가 생겼습니다: {}", event.rentalId(), event.borrower());
	}
}
