package io.salvia95.cozy.reference.memo.internal.app.command;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.memo.internal.infra.MemoMapper;
import io.salvia95.cozy.reference.memo.internal.infra.MemoRow;
import io.salvia95.cozy.reference.memo.spi.MemoFacade;
import io.salvia95.cozy.reference.memo.spi.event.MemoCreated;

/**
 * 메모 쓰기. 트랜잭션 경계가 여기다(쓰기 트랜잭션은 BEGIN IMMEDIATE, docs/conventions/sqlite.md). 메모를 만들면 같은
 * 트랜잭션 안에서 {@link MemoCreated}를 발행한다.
 */
@Service
@Transactional
class MemoFacadeImpl implements MemoFacade {

	private final MemoMapper mapper;

	private final ApplicationEventPublisher events;

	private final Clock clock = Clock.systemUTC();

	MemoFacadeImpl(MemoMapper mapper, ApplicationEventPublisher events) {
		this.mapper = mapper;
		this.events = events;
	}

	@Override
	public long create(String title, String body, boolean pinned) {
		Instant now = Instant.now(this.clock);
		MemoRow row = new MemoRow();
		row.setTitle(title);
		row.setBody(nullToEmpty(body));
		row.setPinned(pinned);
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		this.mapper.insert(row);
		// 같은 트랜잭션에 발행 기록이 남는다. 커밋되면 다른 모듈의 리스너가 비동기로 받는다
		this.events.publishEvent(MemoCreated.of(row.getId(), title));
		return row.getId();
	}

	@Override
	public boolean update(long id, String title, String body, boolean pinned) {
		return this.mapper.update(id, title, nullToEmpty(body), pinned, Instant.now(this.clock)) == 1;
	}

	@Override
	public boolean delete(long id) {
		return this.mapper.delete(id) == 1;
	}

	private static String nullToEmpty(String value) {
		return (value != null) ? value : "";
	}
}
