package it.cozy.app.rental.internal.app.query;

import org.springframework.stereotype.Component;

import it.cozy.app.rental.internal.infra.RentalStore;
import it.cozy.app.rental.spi.RentalFinder;

@Component
class RentalFinderImpl implements RentalFinder {

	private final RentalStore store;

	RentalFinderImpl(RentalStore store) {
		this.store = store;
	}

	@Override
	public boolean knowsBook(long bookId) {
		return this.store.knowsBook(bookId);
	}
}
