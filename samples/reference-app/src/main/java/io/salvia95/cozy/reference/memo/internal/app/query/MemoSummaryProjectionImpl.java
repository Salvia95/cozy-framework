package io.salvia95.cozy.reference.memo.internal.app.query;

import java.util.List;

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
		List<Memo> latest = this.mapper.search(null, false, 0, 1);
		return new MemoSummary(this.mapper.count(null, false), this.mapper.count(null, true),
				latest.isEmpty() ? null : latest.get(0).title());
	}
}
