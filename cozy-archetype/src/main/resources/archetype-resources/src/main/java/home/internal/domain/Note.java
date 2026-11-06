package ${package}.home.internal.domain;

import java.time.Instant;

/** 메모 한 건 (도메인 모델). 모듈 밖으로 내보내지 않는다. 밖에는 spi.dto.NoteItem으로 바꿔서 준다. */
public record Note(long id, String title, Instant createdAt) {
}
