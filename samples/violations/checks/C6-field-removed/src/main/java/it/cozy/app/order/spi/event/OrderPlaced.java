package it.cozy.app.order.spi.event;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

public record OrderPlaced(UUID eventId, int schemaVersion, long orderId) implements CozyEvent {
}
