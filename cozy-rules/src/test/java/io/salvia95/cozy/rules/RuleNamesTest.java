package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RuleNamesTest {

	@Test
	void everyRuleThatCozyCheckReportsHasAName() {
		Stream.concat(IntStream.rangeClosed(1, 15).mapToObj((n) -> "R" + n), Stream.of("C8", "C9"))
			.forEach((id) -> assertThat(RuleNames.of(id)).as(id).isNotBlank());
		assertThat(RuleNames.of("X1")).isEmpty();
	}
}
