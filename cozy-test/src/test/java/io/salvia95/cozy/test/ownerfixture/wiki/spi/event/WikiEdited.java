package io.salvia95.cozy.test.ownerfixture.wiki.spi.event;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/** 옛 자리(주인 모듈의 spi.event)에 있는 이벤트. 주인은 wiki다 */
public record WikiEdited(UUID eventId, int schemaVersion, long wikiId) implements CozyEvent {

	public static WikiEdited of(long wikiId) {
		return new WikiEdited(UUID.randomUUID(), 1, wikiId);
	}
}
