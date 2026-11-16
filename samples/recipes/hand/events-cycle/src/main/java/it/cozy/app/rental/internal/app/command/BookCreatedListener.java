package it.cozy.app.rental.internal.app.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import it.cozy.app.events.spi.book.BookCreated;
import it.cozy.app.rental.spi.RentalFacade;

/**
 * book의 {@link BookCreated}를 받는다 (cozy add listener). 발행한 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 비동기로 실행되고,
 * 같은 이벤트는 인박스 덕분에 한 번만 처리된다(docs/conventions/events.md). 메서드 이름(on)과 위치가 리스너 id이므로 바꾸지 않는다.
 */
@Component
class BookCreatedListener {

	private static final Logger LOG = LoggerFactory.getLogger(BookCreatedListener.class);

	private final RentalFacade facade;

	BookCreatedListener(RentalFacade facade) {
		this.facade = facade;
	}

	@ApplicationModuleListener
	void on(BookCreated event) {
		// 흐름만 보이는 예시라 기록만 남긴다. 업무 처리는 this.facade의 메서드를 부른다
		LOG.info("도서 {}가 생겼습니다: {}", event.bookId(), event.title());
	}
}
