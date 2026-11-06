package io.salvia95.cozy.reference.memo.spi.dto;

import java.util.List;

/** 검색 결과 한 페이지와 전체 건수. */
public record MemoPage(List<MemoItem> items, long total) {
}
