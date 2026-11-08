package it.cozy.app.activity.internal.app.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.cozy.app.activity.spi.ActivityFacade;
import it.cozy.app.activity.internal.infra.ActivityMapper;

/**
 * activity 쓰기 구현. 트랜잭션 경계가 여기다(쓰기 트랜잭션은 BEGIN IMMEDIATE, docs/conventions/sqlite.md). 메서드는 cozy add command
 * activity <이름>으로 더한다.
 */
@Service
@Transactional
class ActivityFacadeImpl implements ActivityFacade {

	private final ActivityMapper mapper;

	ActivityFacadeImpl(ActivityMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public void record(String eventId, String message) {
		this.mapper.record(eventId, message);
	}
}
