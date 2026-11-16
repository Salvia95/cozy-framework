package it.cozy.app.rental.internal.app.command;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.cozy.app.book.spi.BookFinder;
import it.cozy.app.events.spi.book.BookAdded;
import it.cozy.app.events.spi.rental.RentalCreated;
import it.cozy.app.rental.internal.infra.RentalStore;
import it.cozy.app.rental.spi.RentalFacade;

@Component
class RentalFacadeImpl implements RentalFacade {

	private final RentalStore store;

	private final BookFinder books;

	private final ApplicationEventPublisher events;

	RentalFacadeImpl(RentalStore store, BookFinder books, ApplicationEventPublisher events) {
		this.store = store;
		this.books = books;
		this.events = events;
	}

	@Override
	@Transactional
	public long rent(long bookId) {
		if (!this.books.exists(bookId)) {
			throw new IllegalArgumentException("도서가 없습니다: " + bookId);
		}
		long id = this.store.nextId();
		this.events.publishEvent(RentalCreated.of(id, bookId));
		// 남의 이벤트를 만든다: BookAdded의 주인은 book이다
		this.events.publishEvent(BookAdded.of(bookId, "대여"));
		return id;
	}

	@Override
	@Transactional
	public void noteNewBook(long bookId) {
		this.store.noteNewBook(bookId);
	}
}
