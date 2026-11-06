package ${package}.home.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 메모리 저장소 (persistence=false). 앱을 다시 시작하면 사라진다. DB가 필요해지면 cozy-starter-sqlite를 추가하고
 * MyBatis Mapper 구현으로 바꾼다(docs/conventions/sqlite.md).
 */
@Component
class MemoryNoteStore implements NoteStore {

	private final AtomicLong ids = new AtomicLong();

	private final List<Note> notes = new ArrayList<>();

	private final List<NoteActivity> activity = new ArrayList<>();

	@Override
	public synchronized long add(String title, Instant createdAt) {
		long id = this.ids.incrementAndGet();
		this.notes.add(0, new Note(id, title, createdAt));
		return id;
	}

	@Override
	public synchronized List<Note> search(String q, int limit) {
		return this.notes.stream().filter((note) -> q == null || note.title().contains(q)).limit(limit).toList();
	}

	@Override
	public synchronized void addActivity(String message, Instant at) {
		this.activity.add(0, new NoteActivity(message, at));
	}

	@Override
	public synchronized List<NoteActivity> recentActivity(int limit) {
		return this.activity.stream().limit(limit).toList();
	}
}
