package it.cozy.app.rental.internal.app.command;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import it.cozy.app.book.spi.event.BookAdded;
import it.cozy.app.rental.spi.RentalFacade;

/** 새로 들인 도서를 기억한다 */
@Component
class BookAddedListener {

	private final RentalFacade facade;

	BookAddedListener(RentalFacade facade) {
		this.facade = facade;
	}

	@ApplicationModuleListener
	void on(BookAdded event) {
		this.facade.noteNewBook(event.bookId());
	}
}
