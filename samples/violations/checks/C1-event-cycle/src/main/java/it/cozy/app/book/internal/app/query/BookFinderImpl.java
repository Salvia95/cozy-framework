package it.cozy.app.book.internal.app.query;

import org.springframework.stereotype.Component;

import it.cozy.app.book.internal.infra.BookStore;
import it.cozy.app.book.spi.BookFinder;

@Component
class BookFinderImpl implements BookFinder {

	private final BookStore store;

	BookFinderImpl(BookStore store) {
		this.store = store;
	}

	@Override
	public boolean exists(long bookId) {
		return this.store.exists(bookId);
	}

	@Override
	public boolean rented(long bookId) {
		return this.store.rented(bookId);
	}
}
