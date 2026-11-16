package it.cozy.app.rental;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import it.cozy.app.book.spi.event.BookAdded;

/** 테스트 코드도 이벤트를 이름으로 가리킨다. 옮기면 이 import도 따라 바뀐다 */
class BookAddedSampleTest {

	@Test
	void eventsStartAtVersionOne() {
		assertThat(BookAdded.of(1, "자바 입문").schemaVersion()).isEqualTo(1);
	}
}
