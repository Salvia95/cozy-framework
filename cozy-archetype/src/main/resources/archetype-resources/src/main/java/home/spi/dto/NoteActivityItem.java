package ${package}.home.spi.dto;

import java.time.Instant;

/** 최근 활동 한 줄 (모듈 사이 DTO). */
public record NoteActivityItem(String message, Instant at) {
}
