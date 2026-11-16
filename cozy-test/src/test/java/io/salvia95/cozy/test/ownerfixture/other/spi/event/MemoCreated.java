package io.salvia95.cozy.test.ownerfixture.other.spi.event;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/** 다른 모듈에 같은 단순 이름의 이벤트가 있다 */
public record MemoCreated(UUID eventId, int schemaVersion) implements CozyEvent {
}
