package io.salvia95.cozy.reference.memo.api;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.memo.Memo;
import io.salvia95.cozy.reference.memo.internal.MemoMapper;

/** 다른 모듈이 쓰는 메모 요약 조회. */
@Service
public class MemoSummaries {

	private final MemoMapper mapper;

	MemoSummaries(MemoMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public MemoSummary summary() {
		List<Memo> latest = this.mapper.search(null, false, 0, 1);
		return new MemoSummary(this.mapper.count(null, false), this.mapper.count(null, true),
				latest.isEmpty() ? null : latest.get(0).title());
	}
}
