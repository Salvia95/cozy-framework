package io.salvia95.cozy.test.ownerfixture.events.spi.memo;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/** 주인이 memo인 이벤트 (events 모듈) */
public record MemoCreated(UUID eventId, int schemaVersion, long memoId, String title) implements CozyEvent {

	public static MemoCreated of(long memoId, String title) {
		return new MemoCreated(UUID.randomUUID(), 1, memoId, title);
	}
}
