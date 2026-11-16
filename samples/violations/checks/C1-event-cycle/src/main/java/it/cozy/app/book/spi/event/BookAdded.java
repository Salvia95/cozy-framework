package it.cozy.app.book.spi.event;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/** 도서를 들였다 */
public record BookAdded(UUID eventId, int schemaVersion, long bookId, String title) implements CozyEvent {

	public static BookAdded of(long bookId, String title) {
		return new BookAdded(UUID.randomUUID(), 1, bookId, title);
	}
}
