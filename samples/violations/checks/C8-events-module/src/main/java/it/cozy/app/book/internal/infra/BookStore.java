package it.cozy.app.book.internal.infra;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** 도서 저장소 (메모리) */
@Component
public class BookStore {

	private final AtomicLong ids = new AtomicLong();

	private final Map<Long, String> titles = new ConcurrentHashMap<>();

	private final Set<Long> rented = ConcurrentHashMap.newKeySet();

	public long add(String title) {
		long id = this.ids.incrementAndGet();
		this.titles.put(id, title);
		return id;
	}

	public boolean exists(long id) {
		return this.titles.containsKey(id);
	}

	public void markRented(long id) {
		this.rented.add(id);
	}

	public boolean rented(long id) {
		return this.rented.contains(id);
	}
}
