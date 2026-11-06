package ${package}.home.internal.infra;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import ${package}.home.internal.domain.Note;
import ${package}.home.internal.domain.NoteActivity;

/** SQLite 저장소. 트랜잭션은 호출하는 서비스가 연다. */
@Repository
class SqliteNoteStore implements NoteStore {

	private final NoteMapper mapper;

	SqliteNoteStore(NoteMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public long add(String title, Instant createdAt) {
		NoteRow row = new NoteRow();
		row.setTitle(title);
		row.setCreatedAt(createdAt);
		this.mapper.insert(row);
		return row.getId();
	}

	@Override
	public List<Note> search(String q, int limit) {
		String like = (q == null) ? null : "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		return this.mapper.search(like, limit);
	}

	@Override
	public void addActivity(String message, Instant at) {
		this.mapper.insertActivity(message, at);
	}

	@Override
	public List<NoteActivity> recentActivity(int limit) {
		return this.mapper.recentActivity(limit);
	}
}
