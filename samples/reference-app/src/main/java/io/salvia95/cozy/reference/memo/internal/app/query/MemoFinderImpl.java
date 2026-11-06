package io.salvia95.cozy.reference.memo.internal.app.query;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.memo.internal.domain.Memo;
import io.salvia95.cozy.reference.memo.internal.infra.MemoMapper;
import io.salvia95.cozy.reference.memo.spi.MemoFinder;
import io.salvia95.cozy.reference.memo.spi.dto.MemoItem;
import io.salvia95.cozy.reference.memo.spi.dto.MemoPage;
import io.salvia95.cozy.reference.memo.spi.dto.MemoSummary;

/** 메모 읽기. 도메인 모델을 spi DTO로 바꿔서 내보낸다. 읽기 트랜잭션(BEGIN DEFERRED)이다. */
@Service
@Transactional(readOnly = true)
class MemoFinderImpl implements MemoFinder {

	private final MemoMapper mapper;

	private final MemoSummaryProjection summaries;

	MemoFinderImpl(MemoMapper mapper, MemoSummaryProjection summaries) {
		this.mapper = mapper;
		this.summaries = summaries;
	}

	@Override
	public Optional<MemoItem> find(long id) {
		return Optional.ofNullable(this.mapper.findById(id)).map(MemoFinderImpl::item);
	}

	@Override
	public List<MemoItem> list() {
		return this.mapper.findAll().stream().map(MemoFinderImpl::item).toList();
	}

	@Override
	public MemoPage search(String query, boolean pinnedOnly, long offset, int limit) {
		String like = (query == null || query.isBlank()) ? null : "%" + escapeLike(query.trim()) + "%";
		List<MemoItem> items = this.mapper.search(like, pinnedOnly, offset, limit).stream().map(MemoFinderImpl::item).toList();
		return new MemoPage(items, this.mapper.count(like, pinnedOnly));
	}

	@Override
	public MemoSummary summary() {
		return this.summaries.summary();
	}

	private static MemoItem item(Memo memo) {
		return new MemoItem(memo.id(), memo.title(), memo.body(), memo.pinned(), memo.createdAt(), memo.updatedAt());
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
