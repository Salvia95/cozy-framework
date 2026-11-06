package io.salvia95.cozy.reference.activity.internal.app.command;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.activity.internal.infra.ActivityMapper;

@Service
@Transactional
class ActivityServiceImpl implements ActivityService {

	private final ActivityMapper mapper;

	private final Clock clock = Clock.systemUTC();

	ActivityServiceImpl(ActivityMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public void record(String eventId, String message) {
		this.mapper.insert(eventId, message, Instant.now(this.clock));
	}
}
