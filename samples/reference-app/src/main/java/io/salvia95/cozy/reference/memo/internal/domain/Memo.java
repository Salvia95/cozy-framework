package io.salvia95.cozy.reference.memo.internal.domain;

import java.time.Instant;

/** 메모 한 건 (도메인 모델). 모듈 밖으로 내보내지 않는다. 밖에는 spi.dto.MemoItem으로 바꿔서 준다. */
public record Memo(long id, String title, String body, boolean pinned, Instant createdAt, Instant updatedAt) {
}
