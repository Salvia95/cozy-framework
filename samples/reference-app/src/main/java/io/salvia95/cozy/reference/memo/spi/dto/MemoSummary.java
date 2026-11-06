package io.salvia95.cozy.reference.memo.spi.dto;

/**
 * 메모 요약 (대시보드용).
 *
 * @param total 전체 메모 수
 * @param pinned 고정한 메모 수
 * @param latestTitle 가장 최근에 만든 메모의 제목. 메모가 없으면 null
 */
public record MemoSummary(long total, long pinned, String latestTitle) {
}
