package ${package}.home.internal;

import java.time.Instant;
import java.util.List;

/** 메모 저장소. 구현은 하나만 있다(메모리 또는 SQLite). */
public interface NoteStore {

	long add(String title, Instant createdAt);

	/** 제목에 q가 들어간 메모, 최근 것부터. q가 null이면 전부. */
	List<Note> search(String q, int limit);

	void addActivity(String message, Instant at);

	List<NoteActivity> recentActivity(int limit);
}
