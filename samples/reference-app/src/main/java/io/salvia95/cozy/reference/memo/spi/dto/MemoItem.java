package io.salvia95.cozy.reference.memo.spi.dto;

import java.time.Instant;

/** 메모 한 건 (모듈 사이 DTO). */
public record MemoItem(long id, String title, String body, boolean pinned, Instant createdAt, Instant updatedAt) {
}
