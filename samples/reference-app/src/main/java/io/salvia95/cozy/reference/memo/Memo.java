package io.salvia95.cozy.reference.memo;

import java.time.Instant;

/** 메모 한 건. 모듈 밖에 공개하는 읽기 모델이다. */
public record Memo(long id, String title, String body, boolean pinned, Instant createdAt, Instant updatedAt) {
}
