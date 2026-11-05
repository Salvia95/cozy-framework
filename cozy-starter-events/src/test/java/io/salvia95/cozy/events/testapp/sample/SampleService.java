package io.salvia95.cozy.events.testapp.sample;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 업무 데이터를 쓰고 같은 트랜잭션에서 이벤트를 발행한다. */
@Service
public class SampleService {

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	public SampleService(JdbcClient jdbc, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.events = events;
	}

	@Transactional
	public SampleEvent order(String name, boolean failAfterPublish) {
		this.jdbc.sql("INSERT INTO sample_order (name) VALUES (?)").param(name).update();
		SampleEvent event = SampleEvent.of(name);
		this.events.publishEvent(event);
		if (failAfterPublish) {
			throw new IllegalStateException("업무 실패");
		}
		return event;
	}
}
