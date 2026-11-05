package io.salvia95.cozy.events.testapp.sample;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 리스너. 테스트가 실패 여부를 바꾼다. 본문 실행 횟수를 센다. */
@Component
public class SampleListener {

	public static final AtomicBoolean FAIL = new AtomicBoolean();

	public static final AtomicInteger BODY_RUNS = new AtomicInteger();

	private final JdbcClient jdbc;

	public SampleListener(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@ApplicationModuleListener
	public void on(SampleEvent event) throws InterruptedException {
		BODY_RUNS.incrementAndGet();
		this.jdbc.sql("INSERT INTO sample_handled (event_id, name) VALUES (?, ?)")
			.params(event.eventId().toString(), event.name())
			.update();
		if (Boolean.getBoolean("cozy.test.slow")) {
			Thread.sleep(120_000);
		}
		if (FAIL.get()) {
			throw new IllegalStateException("리스너 실패");
		}
	}
}
