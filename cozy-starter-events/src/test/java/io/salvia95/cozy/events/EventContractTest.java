package io.salvia95.cozy.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;

import io.salvia95.cozy.events.internal.EventRetentionCleaner;
import io.salvia95.cozy.events.internal.ListenerIds;
import io.salvia95.cozy.events.internal.SqliteEventPublicationRepository;
import io.salvia95.cozy.events.testapp.EventsTestApp;
import io.salvia95.cozy.events.testapp.sample.SampleEvent;
import io.salvia95.cozy.events.testapp.sample.SampleListener;
import io.salvia95.cozy.events.testapp.sample.SampleService;

/**
 * 이벤트 계약 테스트. 컨텍스트를 직접 열고 닫아 재시작을 흉내 낸다. 같은 데이터 경로를 쓰면 같은 DB 파일이다.
 */
class EventContractTest {

	Path dataDir;

	final List<ConfigurableApplicationContext> contexts = new ArrayList<>();

	@BeforeEach
	void setUp() {
		this.dataDir = Path.of("target/test-data/events-" + UUID.randomUUID());
		SampleListener.FAIL.set(false);
		SampleListener.BODY_RUNS.set(0);
	}

	@AfterEach
	void tearDown() {
		this.contexts.forEach(ConfigurableApplicationContext::close);
	}

	ConfigurableApplicationContext start() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(EventsTestApp.class)
			.web(WebApplicationType.NONE)
			.properties(Map.of("cozy.data-dir", this.dataDir.toString()))
			.run();
		this.contexts.add(context);
		return context;
	}

	@Test
	void rollbackOfBusinessTransactionLeavesNoPublication() {
		ConfigurableApplicationContext context = start();
		assertThatIllegalStateException()
			.isThrownBy(() -> context.getBean(SampleService.class).order("롤백", true));
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		assertThat(count(jdbc, "cozy_event_publication")).isZero();
		assertThat(count(jdbc, "sample_order")).isZero();
		pause(Duration.ofMillis(500));
		assertThat(SampleListener.BODY_RUNS).hasValue(0);
	}

	@Test
	void committedEventIsHandledAndCompleted() {
		ConfigurableApplicationContext context = start();
		SampleEvent event = context.getBean(SampleService.class).order("정상", false);
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		EventPublicationRepository repository = context.getBean(EventPublicationRepository.class);
		await(() -> repository.countByStatus(Status.COMPLETED) == 1);
		assertThat(count(jdbc, "sample_handled")).isEqualTo(1);
		assertThat(jdbc.sql("SELECT listener_id FROM cozy_inbox WHERE event_id = ?")
			.param(event.eventId().toString())
			.query(String.class)
			.single()).isEqualTo(listenerId());
	}

	@Test
	void failedListenerStaysIncompleteAndIsRepublishedOnRestart() {
		SampleListener.FAIL.set(true);
		ConfigurableApplicationContext first = start();
		first.getBean(SampleService.class).order("재시작", false);
		EventPublicationRepository repository = first.getBean(EventPublicationRepository.class);
		await(() -> repository.countByStatus(Status.FAILED) == 1);
		JdbcClient jdbc = first.getBean(JdbcClient.class);
		// 리스너가 실패하면 그 트랜잭션이 롤백되어 본문의 쓰기와 인박스 행이 함께 사라진다
		assertThat(count(jdbc, "sample_handled")).isZero();
		assertThat(count(jdbc, "cozy_inbox")).isZero();
		assertThat(repository.findIncompletePublications()).hasSize(1);
		first.close();

		SampleListener.FAIL.set(false);
		ConfigurableApplicationContext second = start();
		EventPublicationRepository again = second.getBean(EventPublicationRepository.class);
		await(() -> again.countByStatus(Status.COMPLETED) == 1);
		assertThat(count(second.getBean(JdbcClient.class), "sample_handled")).isEqualTo(1);
		assertThat(again.findIncompletePublications()).isEmpty();
	}

	@Test
	void failedPublicationCanBeResubmitted() {
		SampleListener.FAIL.set(true);
		ConfigurableApplicationContext context = start();
		context.getBean(SampleService.class).order("재제출", false);
		EventPublicationRepository repository = context.getBean(EventPublicationRepository.class);
		await(() -> repository.countByStatus(Status.FAILED) == 1);
		assertThat(repository.findFailedPublications(EventPublicationRepository.FailedCriteria.ALL)).hasSize(1);

		SampleListener.FAIL.set(false);
		context.getBean(IncompleteEventPublications.class).resubmitIncompletePublications((p) -> true);
		await(() -> repository.countByStatus(Status.COMPLETED) == 1);
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		assertThat(jdbc.sql("SELECT completion_attempts FROM cozy_event_publication").query(Integer.class).single())
			.isEqualTo(2);
		assertThat(count(jdbc, "sample_handled")).isEqualTo(1);
	}

	@Test
	void sameEventDeliveredTwiceRunsBodyOnce() throws Exception {
		ConfigurableApplicationContext context = start();
		SampleListener listener = context.getBean(SampleListener.class);
		SampleEvent event = SampleEvent.of("중복");
		listener.on(event);
		listener.on(event);
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		await(() -> count(jdbc, "cozy_inbox") == 1);
		pause(Duration.ofMillis(500));
		assertThat(SampleListener.BODY_RUNS).hasValue(1);
		assertThat(count(jdbc, "sample_handled")).isEqualTo(1);
	}

	@Test
	void killedProcessIsRecoveredOnRestart() throws Exception {
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		Process process = new ProcessBuilder(java, "-Dcozy.test.slow=true", "-cp", System.getProperty("java.class.path"),
				EventsTestApp.class.getName(), "publish", "--cozy.data-dir=" + this.dataDir)
			.redirectErrorStream(true)
			.redirectOutput(this.dataDir.resolveSibling(this.dataDir.getFileName() + "-child.log").toFile())
			.start();
		try {
			// 리스너가 본문 안에서 멈춰 있는 동안 강제 종료한다
			Path log = this.dataDir.resolveSibling(this.dataDir.getFileName() + "-child.log");
			await(() -> read(log).contains("PUBLISHED"), Duration.ofSeconds(60));
			pause(Duration.ofSeconds(1));
		}
		finally {
			process.destroyForcibly().waitFor();
		}
		ConfigurableApplicationContext context = start();
		EventPublicationRepository repository = context.getBean(EventPublicationRepository.class);
		await(() -> repository.countByStatus(Status.COMPLETED) == 1);
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		assertThat(count(jdbc, "sample_handled")).isEqualTo(1);
		assertThat(jdbc.sql("SELECT name FROM sample_handled").query(String.class).single()).isEqualTo("강제 종료");
	}

	@Test
	void retentionRemovesOldCompletedPublicationsAndInboxRows() {
		ConfigurableApplicationContext context = start();
		JdbcClient jdbc = context.getBean(JdbcClient.class);
		String old = "2020-01-01T00:00:00.000000000Z";
		String recent = Instant.now().toString();
		insertPublication(jdbc, "old-done", old, old);
		insertPublication(jdbc, "recent-done", recent, recent);
		insertPublication(jdbc, "old-open", old, null);
		insertInbox(jdbc, "old-done", old);
		insertInbox(jdbc, "old-open", old);
		insertInbox(jdbc, "recent-done", recent);

		context.getBean(EventRetentionCleaner.class).cleanUp();

		assertThat(jdbc.sql("SELECT json_extract(serialized_event, '$.name') FROM cozy_event_publication ORDER BY 1")
			.query(String.class)
			.list()).containsExactly("old-open", "recent-done");
		assertThat(jdbc.sql("SELECT event_id FROM cozy_inbox ORDER BY 1").query(String.class).list())
			.containsExactlyInAnyOrder(eventId("old-open"), eventId("recent-done"));
	}

	@Test
	void repositoryImplementsEveryInterfaceMethod() {
		// Modulith를 올렸을 때 새 메서드나 바뀐 시그니처를 기본 구현에 맡기지 않게 한다
		List<String> notImplemented = new ArrayList<>();
		for (Method method : EventPublicationRepository.class.getMethods()) {
			if (Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			try {
				Method impl = SqliteEventPublicationRepository.class.getMethod(method.getName(), method.getParameterTypes());
				if (!impl.getDeclaringClass().equals(SqliteEventPublicationRepository.class)) {
					notImplemented.add(method.toGenericString());
				}
			}
			catch (NoSuchMethodException ex) {
				notImplemented.add(method.toGenericString());
			}
		}
		assertThat(notImplemented).as("SqliteEventPublicationRepository가 직접 구현하지 않은 메서드").isEmpty();
	}

	@Test
	void eventMapperToleratesAddedPrimitiveFieldsAndIsDeterministic() {
		record V2(UUID eventId, int schemaVersion, String name, int priority, boolean urgent) {
		}
		String v1 = CozyEventMapper.get().writeValueAsString(new SampleEvent(UUID.randomUUID(), 1, "a"));
		V2 read = CozyEventMapper.get().readValue(v1, V2.class);
		assertThat(read.priority()).isZero();
		assertThat(read.urgent()).isFalse();
		SampleEvent event = SampleEvent.of("같은 문자열");
		assertThat(CozyEventMapper.get().writeValueAsString(event)).isEqualTo(CozyEventMapper.get().writeValueAsString(event));
	}

	static String listenerId() {
		try {
			return ListenerIds.of(SampleListener.class.getMethod("on", SampleEvent.class));
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String eventId(String name) {
		return UUID.nameUUIDFromBytes(name.getBytes()).toString();
	}

	static void insertPublication(JdbcClient jdbc, String name, String published, String completed) {
		String json = CozyEventMapper.get().writeValueAsString(new SampleEvent(UUID.fromString(eventId(name)), 1, name));
		jdbc.sql("INSERT INTO cozy_event_publication (id, listener_id, event_type, serialized_event, publication_date, "
				+ "completion_date, status) VALUES (?, ?, ?, ?, ?, ?, ?)")
			.params(UUID.randomUUID().toString(), listenerId(), SampleEvent.class.getName(), json, published, completed,
					(completed != null) ? "COMPLETED" : "FAILED")
			.update();
	}

	static void insertInbox(JdbcClient jdbc, String name, String processedAt) {
		jdbc.sql("INSERT INTO cozy_inbox (event_id, listener_id, processed_at) VALUES (?, ?, ?)")
			.params(eventId(name), listenerId(), processedAt)
			.update();
	}

	static int count(JdbcClient jdbc, String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
	}

	static void await(BooleanSupplier condition) {
		await(condition, Duration.ofSeconds(15));
	}

	static void await(BooleanSupplier condition, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			pause(Duration.ofMillis(100));
		}
		assertThat(condition.getAsBoolean()).as("제한 시간 안에 조건이 맞지 않았습니다").isTrue();
	}

	static void pause(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	static String read(Path file) {
		try {
			return java.nio.file.Files.exists(file) ? java.nio.file.Files.readString(file) : "";
		}
		catch (java.io.IOException ex) {
			return "";
		}
	}
}
