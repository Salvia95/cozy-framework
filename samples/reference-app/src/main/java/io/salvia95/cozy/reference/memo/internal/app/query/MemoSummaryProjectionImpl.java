package io.salvia95.cozy.reference.memo.internal.app.query;

import org.springframework.stereotype.Service;

import io.salvia95.cozy.reference.memo.internal.domain.Memo;
import io.salvia95.cozy.reference.memo.internal.infra.MemoMapper;
import io.salvia95.cozy.reference.memo.spi.dto.MemoSummary;

/** 트랜잭션은 부르는 Finder가 연다. */
@Service
class MemoSummaryProjectionImpl implements MemoSummaryProjection {

	private final MemoMapper mapper;

	MemoSummaryProjectionImpl(MemoMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public MemoSummary summary() {
		Memo latest = this.mapper.findLatestCreated();
		return new MemoSummary(this.mapper.count(null, false), this.mapper.count(null, true),
				(latest != null) ? latest.title() : null);
	}
}
