package ${package}.home.internal.app.command;

import java.time.Clock;
import java.time.Instant;

#if( $events == "true" )
import org.springframework.context.ApplicationEventPublisher;
#end
import org.springframework.stereotype.Service;
#if( $persistence == "true" )
import org.springframework.transaction.annotation.Transactional;
#end

import ${package}.home.internal.infra.NoteStore;
import ${package}.home.spi.HomeFacade;
#if( $events == "true" )
import ${package}.home.spi.event.NoteAdded;
#end

/**
 * 홈 쓰기 구현. 저장은 infra의 NoteStore가 한다.
#if( $persistence == "true" )
 * 트랜잭션 경계가 여기다(쓰기 트랜잭션은 BEGIN IMMEDIATE).
#end
 */
@Service
#if( $persistence == "true" )
@Transactional
#end
class HomeFacadeImpl implements HomeFacade {

	private final NoteStore store;

#if( $events == "true" )
	private final ApplicationEventPublisher events;

#end
	private final Clock clock = Clock.systemUTC();

#if( $events == "true" )
	HomeFacadeImpl(NoteStore store, ApplicationEventPublisher events) {
		this.store = store;
		this.events = events;
	}
#else
	HomeFacadeImpl(NoteStore store) {
		this.store = store;
	}
#end

	@Override
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
}
