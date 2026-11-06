package io.salvia95.cozy.reference.memo.internal.app.query;

import io.salvia95.cozy.reference.memo.spi.dto.MemoSummary;

/** 집계 조회: 메모 수, 고정한 메모 수, 최근 메모 제목. */
public interface MemoSummaryProjection {

	MemoSummary summary();
}
