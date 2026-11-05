package io.salvia95.cozy.reference.memo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.memo.internal.MemoMapper;
import io.salvia95.cozy.reference.memo.internal.MemoRow;

/**
 * 메모 CRUD. 쓰기는 {@code @Transactional}, 읽기는 {@code @Transactional(readOnly = true)}로 표시한다
 * (쓰기 트랜잭션은 BEGIN IMMEDIATE, docs/conventions/sqlite.md).
 */
@Service
public class MemoService {

	private final MemoMapper mapper;

	private final Clock clock = Clock.systemUTC();

	MemoService(MemoMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional
	public long create(String title, String body) {
		Instant now = Instant.now(this.clock);
		MemoRow row = new MemoRow();
		row.setTitle(title);
		row.setBody(nullToEmpty(body));
		row.setPinned(false);
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		this.mapper.insert(row);
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
