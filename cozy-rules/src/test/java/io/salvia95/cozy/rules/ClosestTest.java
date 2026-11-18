package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 없는 이름을 적었을 때 가까운 이름 하나를 제안한다 */
class ClosestTest {

	@Test
	void aSwapAMissingLetterOrACaseSlipFindsTheName() {
		List<String> names = List.of("length", "limit", "label", "over");

		assertThat(Closest.to("lable", names)).contains("label");
		assertThat(Closest.to("lenght", names)).contains("length");
		assertThat(Closest.to("limt", names)).contains("limit");
		assertThat(Closest.to("Over", names)).contains("over");
		assertThat(Closest.hint("lable", names)).isEqualTo(" label인가요?");
	}

	@Test
	void farNamesAndTheSameNameAreNotSuggested() {
		List<String> names = List.of("length", "limit", "label");

		assertThat(Closest.to("nothing", names)).isEmpty();
		assertThat(Closest.to("x", names)).isEmpty();
		assertThat(Closest.to("label", names)).isEmpty();
		assertThat(Closest.hint("nothing", names)).isEmpty();
		assertThat(Closest.to("", names)).isEmpty();
	}

	@Test
	void theNearestWinsAndTiesKeepTheFirst() {
		assertThat(Closest.to("toogle", List.of("toggleItem", "toggle"))).contains("toggle");
		assertThat(Closest.to("ab", List.of("ac", "ad"))).contains("ac");
	}
}
