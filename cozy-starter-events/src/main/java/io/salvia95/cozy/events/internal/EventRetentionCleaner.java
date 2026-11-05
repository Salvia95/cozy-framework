package io.salvia95.cozy.events.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.transaction.support.TransactionTemplate;

import io.salvia95.cozy.events.CozyEventsProperties;

/**
 * 보관 기간({@code cozy.events.retention})이 지난 완료 발행 기록과 인박스 행을 지운다 (PRD §8.1, §8.3). 기동 1분 뒤 처음,
 * 그 뒤 {@code cozy.events.cleanup-interval}마다 실행한다.
 *
 * <p>인박스 행은 같은 이벤트가 다시 오지 않을 만큼 오래된 것만 지운다. 미완료 발행 기록이 있는 이벤트는 재시도될 수 있으므로
 * 보관 기간이 지나도 남긴다.
 */
public class EventRetentionCleaner implements SmartLifecycle {

	private static final Log logger = LogFactory.getLog(EventRetentionCleaner.class);

	private final EventPublicationRepository repository;

	private final JdbcClient jdbc;

	private final TransactionTemplate transactions;

	private final CozyEventsProperties properties;

	private final Clock clock;

	private ScheduledExecutorService scheduler;

	public EventRetentionCleaner(EventPublicationRepository repository, JdbcClient jdbc, TransactionTemplate transactions,
			CozyEventsProperties properties, Clock clock) {
		this.repository = repository;
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.properties = properties;
		this.clock = clock;
	}

	/** @return 지운 인박스 행 수 */
	public int cleanUp() {
		Instant before = Instant.now(this.clock).minus(this.properties.retention());
		Integer inbox = this.transactions.execute((status) -> {
			this.repository.deleteCompletedPublicationsBefore(before);
			return this.jdbc.sql("DELETE FROM cozy_inbox WHERE processed_at < ? AND event_id NOT IN "
					+ "(SELECT json_extract(serialized_event, '$.eventId') FROM " + SqliteEventPublicationRepository.TABLE
					+ " WHERE completion_date IS NULL)")
				.param(EventTimes.format(before))
				.update();
		});
		return (inbox != null) ? inbox : 0;
	}

	@Override
	public void start() {
		this.scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
			Thread thread = new Thread(r, "cozy-events-cleanup");
			thread.setDaemon(true);
			return thread;
		});
		long interval = this.properties.cleanupInterval().toSeconds();
		this.scheduler.scheduleWithFixedDelay(this::safeCleanUp, 60, interval, TimeUnit.SECONDS);
	}

	private void safeCleanUp() {
		try {
			int inbox = cleanUp();
			logger.debug("[cozy] 이벤트 보관 정리: 인박스 " + inbox + "행");
		}
		catch (RuntimeException ex) {
			logger.warn("[cozy] 이벤트 보관 정리 실패. 다음 주기에 다시 합니다", ex);
		}
	}

	@Override
	public void stop() {
		if (this.scheduler != null) {
			this.scheduler.shutdownNow();
			this.scheduler = null;
		}
	}

	@Override
	public boolean isRunning() {
		return this.scheduler != null;
	}
}
