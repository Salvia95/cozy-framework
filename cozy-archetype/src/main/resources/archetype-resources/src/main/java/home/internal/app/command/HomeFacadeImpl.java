package ${package}.home;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

#if( $events == "true" )
import org.springframework.context.ApplicationEventPublisher;
#end
import org.springframework.stereotype.Service;
#if( $persistence == "true" )
import org.springframework.transaction.annotation.Transactional;
#end

#if( $events == "true" )
import ${package}.home.api.NoteAdded;
#end
import ${package}.home.internal.Note;
import ${package}.home.internal.NoteActivity;
import ${package}.home.internal.NoteStore;

/**
 * 홈의 업무 로직. 저장은 internal의 NoteStore가 맡는다.
#if( $persistence == "true" )
 * 쓰기는 @Transactional, 읽기는 @Transactional(readOnly = true)로 표시한다(쓰기 트랜잭션은 BEGIN IMMEDIATE).
#end
 */
@Service
public class HomeService {

	static final int LIST_LIMIT = 50;

	static final int ACTIVITY_LIMIT = 5;

	private final NoteStore store;

#if( $events == "true" )
	private final ApplicationEventPublisher events;

#end
	private final Clock clock = Clock.systemUTC();

#if( $events == "true" )
	HomeService(NoteStore store, ApplicationEventPublisher events) {
		this.store = store;
		this.events = events;
	}
#else
	HomeService(NoteStore store) {
		this.store = store;
	}
#end

#if( $persistence == "true" )
	@Transactional
#end
	public long add(String title) {
		Instant now = Instant.now(this.clock);
		long id = this.store.add(title.strip(), now);
#if( $events == "true" )
		// 같은 트랜잭션에 발행 기록이 남는다. 커밋되면 리스너(NoteAddedListener)가 비동기로 받는다
		this.events.publishEvent(NoteAdded.of(id, title.strip()));
#else
		this.store.addActivity("메모를 추가했습니다: " + title.strip(), now);
#end
		return id;
	}

#if( $persistence == "true" )
	@Transactional(readOnly = true)
#end
	public List<Note> search(String q) {
		return this.store.search((q == null || q.isBlank()) ? null : q.strip(), LIST_LIMIT);
	}

#if( $persistence == "true" )
	@Transactional(readOnly = true)
#end
	public List<NoteActivity> recentActivity() {
		return this.store.recentActivity(ACTIVITY_LIMIT);
	}
}
