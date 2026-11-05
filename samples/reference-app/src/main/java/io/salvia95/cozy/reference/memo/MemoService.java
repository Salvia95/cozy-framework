package io.salvia95.cozy.reference.memo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.memo.api.MemoCreated;
import io.salvia95.cozy.reference.memo.internal.MemoMapper;
import io.salvia95.cozy.reference.memo.internal.MemoRow;

/**
 * 메모 CRUD. 메모를 만들면 {@link MemoCreated}를 발행한다. 쓰기는 {@code @Transactional}, 읽기는 {@code @Transactional(readOnly = true)}로 표시한다
 * (쓰기 트랜잭션은 BEGIN IMMEDIATE, docs/conventions/sqlite.md).
 */
@Service
public class MemoService {

	private final MemoMapper mapper;

	private final ApplicationEventPublisher events;

	private final Clock clock = Clock.systemUTC();

	MemoService(MemoMapper mapper, ApplicationEventPublisher events) {
		this.mapper = mapper;
		this.events = events;
	}

	@Transactional
	public long create(String title, String body) {
		return create(title, body, false);
	}

	@Transactional
	public long create(String title, String body, boolean pinned) {
		Instant now = Instant.now(this.clock);
		MemoRow row = new MemoRow();
		row.setTitle(title);
		row.setBody(nullToEmpty(body));
		row.setPinned(pinned);
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		this.mapper.insert(row);
		// 같은 트랜잭션에 발행 기록이 남는다. 커밋되면 다른 모듈의 리스너가 비동기로 받는다 (PRD §8)
		this.events.publishEvent(MemoCreated.of(row.getId(), title));
		return row.getId();
	}

	@Transactional(readOnly = true)
	public Optional<Memo> find(long id) {
		return Optional.ofNullable(this.mapper.findById(id));
	}

	/** 고정한 메모가 먼저, 그다음 최근에 수정한 순서. */
	@Transactional(readOnly = true)
	public List<Memo> list() {
		return this.mapper.findAll();
	}

	/**
	 * 제목이나 본문에 {@code query}가 들어간 메모. 정렬은 {@link #list()}와 같다.
	 * @param query 비우면 전체
	 * @param pinnedOnly 고정한 메모만
	 */
	@Transactional(readOnly = true)
	public MemoSearch search(String query, boolean pinnedOnly, long offset, int limit) {
		String like = (query == null || query.isBlank()) ? null : "%" + escapeLike(query.trim()) + "%";
		return new MemoSearch(this.mapper.search(like, pinnedOnly, offset, limit), this.mapper.count(like, pinnedOnly));
	}

	/** 검색 결과 한 페이지와 전체 건수. */
	public record MemoSearch(List<Memo> items, long total) {
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** @return 수정했으면 true, 없는 메모면 false */
	@Transactional
	public boolean update(long id, String title, String body, boolean pinned) {
		return this.mapper.update(id, title, nullToEmpty(body), pinned, Instant.now(this.clock)) == 1;
	}

	/** @return 지웠으면 true, 없는 메모면 false */
	@Transactional
	public boolean delete(long id) {
		return this.mapper.delete(id) == 1;
	}

	private static String nullToEmpty(String value) {
		return (value != null) ? value : "";
	}
}
