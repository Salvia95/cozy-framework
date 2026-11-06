package io.salvia95.cozy.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;

import io.salvia95.cozy.runtime.CozyDiagnosticsContributor;
import io.salvia95.cozy.events.testapp.EventsTestApp;
import io.salvia95.cozy.events.testapp.sample.SampleListener;
import io.salvia95.cozy.events.testapp.sample.SampleService;

/**
 * 진단 파일의 미완료 이벤트 개수 (PRD §9.5).
 */
class EventDiagnosticsTest {

	ConfigurableApplicationContext context;

	@AfterEach
	void tearDown() {
		SampleListener.FAIL.set(false);
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void incompletePublicationsAreCountedWithoutEventContent() throws Exception {
		SampleListener.FAIL.set(true);
		this.context = new SpringApplicationBuilder(EventsTestApp.class).web(WebApplicationType.NONE)
			.properties(Map.of("cozy.data-dir", Path.of("target/test-data/events-diag-" + UUID.randomUUID()).toString()))
			.run();
		this.context.getBean(SampleService.class).order("비밀스러운 주문", false);
		EventPublicationRepository repository = this.context.getBean(EventPublicationRepository.class);
		EventContractTest.await(() -> repository.countByStatus(Status.FAILED) == 1);

		String text = this.context.getBeansOfType(CozyDiagnosticsContributor.class)
			.values()
			.stream()
			.filter((c) -> c.fileName().equals("events.txt"))
			.findFirst()
			.orElseThrow()
			.content();
		assertThat(text).contains("미완료 이벤트: 1")
			.contains("FAILED: 1")
			.contains("io.salvia95.cozy.events.testapp.sample.SampleEvent")
			.contains("SampleListener.on(")
			.doesNotContain("비밀스러운 주문");
	}
}
