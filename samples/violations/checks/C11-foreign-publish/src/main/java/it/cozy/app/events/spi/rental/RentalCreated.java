package it.cozy.app.events.spi.rental;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/** 도서를 빌려 갔다 */
public record RentalCreated(UUID eventId, int schemaVersion, long rentalId, long bookId) implements CozyEvent {

	public static RentalCreated of(long rentalId, long bookId) {
		return new RentalCreated(UUID.randomUUID(), 1, rentalId, bookId);
	}
}
