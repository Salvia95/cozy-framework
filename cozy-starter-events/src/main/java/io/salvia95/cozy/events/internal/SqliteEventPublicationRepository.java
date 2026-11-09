package io.salvia95.cozy.events.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

/**
 * SQLite 발행 기록 저장소. Spring Modulith 2.1.2의 {@code JdbcEventPublicationRepositoryV2}
 * (Apache-2.0)를 옮겼다. 완료 처리는 UPDATE 방식만 지원한다(완료 기록은 보관 기간 뒤 정리 작업이 지운다).
 *
 * <p>인터페이스의 기본 메서드도 모두 구현한다. 기본 구현은 아무 일도 하지 않아서 빠뜨리면 상태 전이와 재제출이 조용히 틀린다.
 * {@code EventPublicationRepositoryContractTest}가 모든 메서드를 이 클래스가 직접 구현하는지 검사한다.
 */
@Transactional
public class SqliteEventPublicationRepository implements EventPublicationRepository {

	private static final Log logger = LogFactory.getLog(SqliteEventPublicationRepository.class);

	static final String TABLE = "cozy_event_publication";

	private static final String COLUMNS = "id, listener_id, event_type, serialized_event, publication_date, "
			+ "completion_date, status, completion_attempts, last_resubmission_date";

	private final JdbcClient jdbc;

	private final EventSerializer serializer;

	private final ClassLoader classLoader;

	public SqliteEventPublicationRepository(JdbcClient jdbc, EventSerializer serializer, ClassLoader classLoader) {
		this.jdbc = jdbc;
		this.serializer = serializer;
		this.classLoader = classLoader;
	}

	@Override
	public TargetEventPublication create(TargetEventPublication publication) {
		this.jdbc.sql("INSERT INTO " + TABLE + " (id, listener_id, event_type, serialized_event, publication_date, "
				+ "status, completion_attempts, last_resubmission_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
			.params(publication.getIdentifier().toString(), publication.getTargetIdentifier().getValue(),
					publication.getEvent().getClass().getName(), serialize(publication.getEvent()),
					EventTimes.format(publication.getPublicationDate()), publication.getStatus().name(), 1,
					EventTimes.format(publication.getPublicationDate()))
			.update();
		return publication;
	}

	@Override
	public void markProcessing(UUID identifier) {
		updateStatus(identifier, Status.PROCESSING);
	}

	@Override
	public void markCompleted(TargetEventPublication publication, Instant completionDate) {
		publication.markCompleted(completionDate);
		markCompleted(publication.getIdentifier(), completionDate);
	}

	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {
		this.jdbc.sql("UPDATE " + TABLE + " SET status = 'COMPLETED', completion_date = ? "
				+ "WHERE listener_id = ? AND completion_date IS NULL AND serialized_event = ?")
			.params(EventTimes.format(completionDate), identifier.getValue(), serialize(event))
			.update();
	}

	@Override
	public void markCompleted(UUID identifier, Instant completionDate) {
		this.jdbc.sql("UPDATE " + TABLE + " SET status = 'COMPLETED', completion_date = ? WHERE id = ?")
			.params(EventTimes.format(completionDate), identifier.toString())
			.update();
	}

	@Override
	public void markFailed(UUID identifier) {
		updateStatus(identifier, Status.FAILED);
	}

	@Override
	public boolean markResubmitted(UUID identifier, Instant resubmissionDate) {
		int rows = this.jdbc.sql("UPDATE " + TABLE + " SET status = 'RESUBMITTED', "
				+ "completion_attempts = completion_attempts + 1, last_resubmission_date = ? "
				+ "WHERE id = ? AND completion_date IS NULL AND (status IS NULL OR status != 'RESUBMITTED')")
			.params(EventTimes.format(resubmissionDate), identifier.toString())
			.update();
		return rows == 1;
	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublications() {
		return query("WHERE completion_date IS NULL OR status != 'COMPLETED' ORDER BY publication_date ASC");
	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {
		return query("WHERE (completion_date IS NULL OR status = 'PROCESSING') AND publication_date < ? "
				+ "ORDER BY publication_date ASC", EventTimes.format(instant));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(Object event,
			PublicationTargetIdentifier targetIdentifier) {
		return query("WHERE serialized_event = ? AND listener_id = ? AND (completion_date IS NULL OR status = 'FAILED') "
				+ "ORDER BY publication_date", serialize(event), targetIdentifier.getValue())
			.stream()
			.findFirst();
	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findCompletedPublications() {
		return query("WHERE completion_date IS NOT NULL OR status = 'COMPLETED' ORDER BY publication_date ASC");
	}

	@Override
	public void deletePublications(List<UUID> identifiers) {
		for (int from = 0; from < identifiers.size(); from += 100) {
			List<String> batch = identifiers.subList(from, Math.min(from + 100, identifiers.size()))
				.stream()
				.map(UUID::toString)
				.toList();
			String placeholders = String.join(", ", batch.stream().map((id) -> "?").toList());
			this.jdbc.sql("DELETE FROM " + TABLE + " WHERE id IN (" + placeholders + ")").params(batch).update();
		}
	}

	@Override
	public void deleteCompletedPublications() {
		this.jdbc.sql("DELETE FROM " + TABLE + " WHERE completion_date IS NOT NULL OR status = 'COMPLETED'").update();
	}

	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {
		this.jdbc.sql("DELETE FROM " + TABLE + " WHERE completion_date < ? AND (status = 'COMPLETED' OR status IS NULL)")
			.param(EventTimes.format(instant))
			.update();
	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findFailedPublications(FailedCriteria criteria) {
		StringBuilder sql = new StringBuilder("WHERE (status = 'FAILED' OR (status IS NULL AND completion_date IS NULL))");
		List<Object> args = new ArrayList<>();
		if (criteria.getPublicationDateReference() != null) {
			sql.append(" AND publication_date < ?");
			args.add(EventTimes.format(criteria.getPublicationDateReference()));
		}
		sql.append(" ORDER BY publication_date ASC");
		if (criteria.getMaxItemsToRead() != -1) {
			sql.append(" LIMIT ").append(criteria.getMaxItemsToRead());
		}
		return query(sql.toString(), args.toArray());
	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findByStatus(Status status) {
		return query("WHERE status = ? ORDER BY publication_date ASC", status.name());
	}

	@Override
	@Transactional(readOnly = true)
	public int countByStatus(Status status) {
		return this.jdbc.sql("SELECT count(*) FROM " + TABLE + " WHERE status = ?")
			.param(status.name())
			.query(Integer.class)
			.single();
	}

	private void updateStatus(UUID identifier, Status status) {
		this.jdbc.sql("UPDATE " + TABLE + " SET status = ? WHERE id = ? AND completion_date IS NULL "
				+ "AND (status IS NULL OR status != ?)")
			.params(status.name(), identifier.toString(), status.name())
			.update();
	}

	private String serialize(Object event) {
		return this.serializer.serialize(event).toString();
	}

	private List<TargetEventPublication> query(String where, Object... args) {
		List<TargetEventPublication> result = new ArrayList<>();
		this.jdbc.sql("SELECT " + COLUMNS + " FROM " + TABLE + " " + where).params(args).query((rs) -> {
			TargetEventPublication publication = map(rs);
			if (publication != null) {
				result.add(publication);
			}
		});
		return result;
	}

	private @Nullable TargetEventPublication map(ResultSet rs) throws SQLException {
		UUID id = UUID.fromString(rs.getString("id"));
		String type = rs.getString("event_type");
		Class<?> eventClass;
		try {
			eventClass = ClassUtils.forName(type, this.classLoader);
		}
		catch (ClassNotFoundException ex) {
			logger.warn("[cozy] 발행 기록 " + id + "의 이벤트 타입 " + type + "를 찾을 수 없습니다. 이벤트 클래스의 FQN을 바꾸지 "
					+ "마세요 (docs/conventions/events.md)");
			return null;
		}
		String serialized = rs.getString("serialized_event");
		String status = rs.getString("status");
		return new SqlitePublication(id, EventTimes.parse(rs.getString("publication_date")),
				rs.getString("listener_id"), () -> this.serializer.deserialize(serialized, eventClass),
				EventTimes.parse(rs.getString("completion_date")), (status != null) ? Status.valueOf(status) : null,
				EventTimes.parse(rs.getString("last_resubmission_date")), rs.getInt("completion_attempts"));
	}

	private static final class SqlitePublication implements TargetEventPublication {

		private final UUID id;

		private final Instant publicationDate;

		private final String listenerId;

		private final Supplier<Object> eventSupplier;

		private final @Nullable Instant lastResubmissionDate;

		private final int completionAttempts;

		private @Nullable Instant completionDate;

		private @Nullable Object event;

		private Status status;

		SqlitePublication(UUID id, Instant publicationDate, String listenerId, Supplier<Object> eventSupplier,
				@Nullable Instant completionDate, @Nullable Status status, @Nullable Instant lastResubmissionDate,
				int completionAttempts) {
			this.id = id;
			this.publicationDate = publicationDate;
			this.listenerId = listenerId;
			this.eventSupplier = eventSupplier;
			this.completionDate = completionDate;
			this.status = (status != null) ? status : (completionDate != null) ? Status.COMPLETED : Status.PROCESSING;
			this.lastResubmissionDate = lastResubmissionDate;
			this.completionAttempts = completionAttempts;
		}

		@Override
		public UUID getIdentifier() {
			return this.id;
		}

		@Override
		public Object getEvent() {
			if (this.event == null) {
				this.event = this.eventSupplier.get();
			}
			return this.event;
		}

		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(this.listenerId);
		}

		@Override
		public Instant getPublicationDate() {
			return this.publicationDate;
		}

		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.ofNullable(this.completionDate);
		}

		@Override
		public void markCompleted(Instant instant) {
			this.completionDate = instant;
			this.status = Status.COMPLETED;
		}

		@Override
		public Status getStatus() {
			return this.status;
		}

		@Override
		public @Nullable Instant getLastResubmissionDate() {
			return this.lastResubmissionDate;
		}

		@Override
		public int getCompletionAttempts() {
			return this.completionAttempts;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof SqlitePublication that && this.id.equals(that.id)
					&& Objects.equals(this.completionDate, that.completionDate);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.id, this.completionDate);
		}
	}
}
