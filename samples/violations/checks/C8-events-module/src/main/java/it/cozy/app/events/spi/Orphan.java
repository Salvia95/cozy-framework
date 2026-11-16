package it.cozy.app.events.spi;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/** 주인 모듈의 폴더가 없는 이벤트 */
public record Orphan(UUID eventId, int schemaVersion) implements CozyEvent {
}
