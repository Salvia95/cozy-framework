package ${package}.home.internal.domain;

import java.time.Instant;

/** 최근 활동 한 줄 */
public record NoteActivity(String message, Instant at) {
}
