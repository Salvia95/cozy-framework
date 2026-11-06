package ${package}.home.spi.dto;

import java.time.Instant;

/** 메모 한 건 (모듈 사이 DTO). */
public record NoteItem(long id, String title, Instant createdAt) {
}
