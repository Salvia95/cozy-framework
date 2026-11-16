package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * describe의 이벤트: 이벤트 계약 모듈(events/spi/{주인})의 이벤트는 주인 모듈에 싣고, 리스너가 받는 이벤트는 {@code 주인.이벤트}로 쓴다.
 */
class DescribeEventsTest {

	@TempDir
	Path dir;

	@Test
	void eventsInTheEventsModuleBelongToTheirOwner() throws IOException {
		App.of(this.dir)
			.module("memo", "\"events :: spi\"")
			.module("activity", "\"events :: spi\"")
			.module("events", "{}")
			.java("events/spi/memo/package-info.java", "@NamedInterface(\"spi\")\npackage it.cozy.app.events.spi.memo;\n\n"
					+ "import org.springframework.modulith.NamedInterface;\n")
			.java("events/spi/memo/MemoCreated.java", """
					package it.cozy.app.events.spi.memo;

					import java.util.UUID;

					import io.salvia95.cozy.events.CozyEvent;

					public record MemoCreated(UUID eventId, int schemaVersion, long memoId) implements CozyEvent {
						public static MemoCreated of(long memoId) {
							return new MemoCreated(UUID.randomUUID(), 1, memoId);
						}
					}
					""")
			.java("memo/internal/app/command/MemoFacadeImpl.java", """
					package it.cozy.app.memo.internal.app.command;

					import it.cozy.app.events.spi.memo.MemoCreated;

					class MemoFacadeImpl {
						Object create() {
							return MemoCreated.of(1);
						}
					}
					""")
			.java("activity/internal/app/command/MemoCreatedListener.java", """
					package it.cozy.app.activity.internal.app.command;

					import org.springframework.modulith.events.ApplicationModuleListener;

					import it.cozy.app.events.spi.memo.MemoCreated;

					class MemoCreatedListener {
						@ApplicationModuleListener
						void on(MemoCreated event) {
						}
					}
					""");
		Result text = CozyCliTest.run("describe", "--project", this.dir.toString());
		assertThat(text.code()).as(text.err()).isZero();
		assertThat(text.out()).contains("\n이벤트 memo.MemoCreated(long memoId) 발행: "
				+ "src/main/java/it/cozy/app/memo/internal/app/command/MemoFacadeImpl.java → activity(MemoCreatedListener)\n")
			.contains("리스너 MemoCreatedListener.on ← memo.MemoCreated")
			.doesNotContain("  이벤트 ");

		Result json = CozyCliTest.run("describe", "--json", "--project", this.dir.toString());
		String events = json.out().substring(json.out().indexOf("\n  \"events\": ["));
		assertThat(events).contains("\"type\": \"MemoCreated\"").contains("\"owner\": \"memo\"")
			.contains("\"module\": \"activity\"").contains("\"type\": \"MemoCreatedListener\"");
		assertThat(json.out().substring(0, json.out().indexOf("\n  \"events\": ["))).as("모듈 항목에는 이벤트가 없다")
			.doesNotContain("\"MemoCreated\"");
	}

	@Test
	void anEventWithoutAnOwnerIsNotListed() throws IOException {
		App.of(this.dir)
			.module("events", "{}")
			.java("events/spi/nobody/Orphan.java", """
					package it.cozy.app.events.spi.nobody;

					import java.util.UUID;

					import io.salvia95.cozy.events.CozyEvent;

					public record Orphan(UUID eventId, int schemaVersion) implements CozyEvent {
					}
					""");
		Result text = CozyCliTest.run("describe", "--project", this.dir.toString());
		assertThat(text.code()).as(text.err()).isZero();
		assertThat(text.out()).doesNotContain("Orphan");
	}
}
