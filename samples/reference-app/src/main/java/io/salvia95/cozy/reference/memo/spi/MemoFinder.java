package io.salvia95.cozy.reference.memo.spi;

import java.util.List;
import java.util.Optional;

import io.salvia95.cozy.reference.memo.spi.dto.MemoItem;
import io.salvia95.cozy.reference.memo.spi.dto.MemoPage;
import io.salvia95.cozy.reference.memo.spi.dto.MemoSummary;

/** 메모 읽기. 정렬은 고정한 메모가 먼저, 그다음 최근에 고친 순서다. */
public interface MemoFinder {

	Optional<MemoItem> find(long id);

	List<MemoItem> list();

	/**
	 * 제목이나 본문에 {@code query}가 들어간 메모 한 페이지.
	 * @param query 비우면 전체
	 * @param pinnedOnly 고정한 메모만
	 */
	MemoPage search(String query, boolean pinnedOnly, long offset, int limit);

	/** 대시보드용 요약 */
	MemoSummary summary();
}
