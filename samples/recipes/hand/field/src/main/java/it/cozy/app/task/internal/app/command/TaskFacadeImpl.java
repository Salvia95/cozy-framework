package it.cozy.app.task.internal.app.command;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.cozy.app.task.internal.infra.TaskMapper;
import it.cozy.app.task.internal.infra.TaskRow;
import it.cozy.app.task.spi.TaskFacade;
import it.cozy.app.task.spi.event.TaskCreated;

/**
 * 할 일 쓰기 (cozy add crud, 본문은 손으로 고쳤다). 트랜잭션 경계가 여기다(쓰기 트랜잭션은 BEGIN IMMEDIATE, docs/conventions/sqlite.md). 만들기가 성공하면 같은 트랜잭션 안에서 이벤트를 발행한다(TaskCreated).
 */
@Service
@Transactional
class TaskFacadeImpl implements TaskFacade {

	private final TaskMapper mapper;

	private final ApplicationEventPublisher events;

	private final Clock clock = Clock.systemUTC();

	TaskFacadeImpl(TaskMapper mapper, ApplicationEventPublisher events) {
		this.mapper = mapper;
		this.events = events;
	}

	@Override
	public long create(String title, boolean done, String note) {
		Instant now = Instant.now(this.clock);
		TaskRow row = new TaskRow();
		// 제목 앞뒤의 공백은 저장하지 않는다
		row.setTitle(title.strip());
		row.setDone(done);
		row.setNote(note);
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		this.mapper.insert(row);
		// 같은 트랜잭션에 발행 기록이 남는다. 커밋되면 받는 모듈의 리스너가 비동기로 받는다 (docs/conventions/events.md)
		this.events.publishEvent(TaskCreated.of(row.getId(), title));
		return row.getId();
	}

	@Override
	public boolean update(long id, String title, boolean done, String note) {
		boolean updated = this.mapper.update(id, title, done, note, Instant.now(this.clock)) == 1;
		return updated;
	}

	@Override
	public boolean delete(long id) {
		boolean deleted = this.mapper.delete(id) == 1;
		return deleted;
	}
}
