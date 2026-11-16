package it.cozy.app.book.internal.app.command;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import it.cozy.app.book.spi.BookFacade;
import it.cozy.app.events.spi.rental.RentalCreated;

/** 빌려 간 도서를 표시한다 */
@Component
class RentalCreatedListener {

	private final BookFacade facade;

	RentalCreatedListener(BookFacade facade) {
		this.facade = facade;
	}

	@ApplicationModuleListener
	void on(RentalCreated event) {
		this.facade.markRented(event.bookId());
	}
}
