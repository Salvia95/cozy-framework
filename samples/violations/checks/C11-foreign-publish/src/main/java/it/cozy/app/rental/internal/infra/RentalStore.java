package it.cozy.app.rental.internal.infra;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** 대여 저장소 (메모리) */
@Component
public class RentalStore {

	private final AtomicLong ids = new AtomicLong();

	private final Set<Long> newBooks = ConcurrentHashMap.newKeySet();

	public long nextId() {
		return this.ids.incrementAndGet();
	}

	public void noteNewBook(long bookId) {
		this.newBooks.add(bookId);
	}

	public boolean knowsBook(long bookId) {
		return this.newBooks.contains(bookId);
	}
}
