package ${package}.home.internal.app.query;

import java.util.List;

import org.springframework.stereotype.Service;
#if( $persistence == "true" )
import org.springframework.transaction.annotation.Transactional;
#end

import ${package}.home.internal.infra.NoteStore;
import ${package}.home.spi.HomeFinder;
import ${package}.home.spi.dto.NoteActivityItem;
import ${package}.home.spi.dto.NoteItem;

/**
 * 홈 읽기 구현. 도메인 모델을 spi DTO로 바꿔서 내보낸다.
#if( $persistence == "true" )
 * 읽기 트랜잭션(BEGIN DEFERRED)이다.
#end
 */
@Service
#if( $persistence == "true" )
@Transactional(readOnly = true)
#end
class HomeFinderImpl implements HomeFinder {

	static final int LIST_LIMIT = 50;

	static final int ACTIVITY_LIMIT = 5;

	private final NoteStore store;

	HomeFinderImpl(NoteStore store) {
		this.store = store;
	}

	@Override
	public List<NoteItem> search(String q) {
		return this.store.search((q == null || q.isBlank()) ? null : q.strip(), LIST_LIMIT)
			.stream()
			.map((note) -> new NoteItem(note.id(), note.title(), note.createdAt()))
			.toList();
	}

	@Override
	public List<NoteActivityItem> recentActivity() {
		return this.store.recentActivity(ACTIVITY_LIMIT)
			.stream()
			.map((activity) -> new NoteActivityItem(activity.message(), activity.at()))
			.toList();
	}
}
