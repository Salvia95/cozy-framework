package ${package}.events.spi.home;

import java.util.UUID;

import io.salvia95.cozy.events.CozyEvent;

/**
 * 메모를 추가했다. 주인은 home이고, home의 internal.app.command에서만 만든다. 받는 모듈은 "events :: spi"만 선언하고
 * home에는 의존하지 않는다.
 * 필드는 추가만 한다. 추가하면 schemaVersion을 올리고 src/test/resources/events/NoteAdded/v{n}.json 샘플을 더한다.
 */
public record NoteAdded(UUID eventId, int schemaVersion, long noteId, String title) implements CozyEvent {

	public static NoteAdded of(long noteId, String title) {
		return new NoteAdded(UUID.randomUUID(), 1, noteId, title);
	}
}
