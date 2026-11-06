package io.salvia95.cozy.reference.memo.api;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/**
 * 메모를 만들었다. 스키마 진화 규칙: 필드 추가만, schemaVersion 올리기, 버전별 샘플
 * (src/test/resources/events/MemoCreated/, docs/conventions/events.md).
 */
public record MemoCreated(UUID eventId, int schemaVersion, long memoId, String title) implements CozyEvent {

	public static MemoCreated of(long memoId, String title) {
		return new MemoCreated(UUID.randomUUID(), 1, memoId, title);
	}
}
