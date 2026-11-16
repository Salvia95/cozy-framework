package it.cozy.app.book.internal.app.command;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.cozy.app.book.internal.infra.BookStore;
import it.cozy.app.book.spi.BookFacade;
import it.cozy.app.book.spi.event.BookAdded;

@Component
class BookFacadeImpl implements BookFacade {

	private final BookStore store;

	private final ApplicationEventPublisher events;

	BookFacadeImpl(BookStore store, ApplicationEventPublisher events) {
		this.store = store;
		this.events = events;
	}

	@Override
	@Transactional
	public long add(String title) {
		long id = this.store.add(title);
		this.events.publishEvent(BookAdded.of(id, title));
		return id;
	}

	@Override
	@Transactional
	public void markRented(long bookId) {
		this.store.markRented(bookId);
	}
}
