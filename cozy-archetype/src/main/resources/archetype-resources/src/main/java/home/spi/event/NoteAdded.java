package ${package}.home.spi.event;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/**
 * 메모를 추가했다. 다른 모듈이 받을 수 있게 모듈 사이 계약(spi.event)에 둔다.
 * 필드는 추가만 한다. 추가하면 schemaVersion을 올리고 src/test/resources/events/NoteAdded/v{n}.json 샘플을 더한다.
 */
public record NoteAdded(UUID eventId, int schemaVersion, long noteId, String title) implements CozyEvent {

	public static NoteAdded of(long noteId, String title) {
		return new NoteAdded(UUID.randomUUID(), 1, noteId, title);
	}
}
