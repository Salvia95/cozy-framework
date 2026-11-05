package io.salvia95.cozy.events.testapp.sample;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

public record SampleEvent(UUID eventId, int schemaVersion, String name) implements CozyEvent {

	public static SampleEvent of(String name) {
		return new SampleEvent(UUID.randomUUID(), 1, name);
	}
}
