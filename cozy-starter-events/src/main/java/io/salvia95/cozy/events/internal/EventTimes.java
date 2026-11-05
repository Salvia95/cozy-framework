package io.salvia95.cozy.events.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 시각 ↔ TEXT. cozy-starter-sqlite의 TypeHandler와 같은 고정 폭 UTC 형식이라 문자열 비교가 시간 순서와 같다. */
final class EventTimes {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter
		.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", Locale.ROOT)
		.withZone(ZoneOffset.UTC);

	private EventTimes() {
	}

	static String format(Instant instant) {
		return (instant != null) ? FORMAT.format(instant) : null;
	}

	static Instant parse(String text) {
		return (text != null) ? Instant.parse(text) : null;
	}
}
