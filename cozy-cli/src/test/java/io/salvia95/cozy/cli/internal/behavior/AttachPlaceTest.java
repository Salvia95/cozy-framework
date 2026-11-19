package io.salvia95.cozy.cli.internal.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.cli.internal.CliException;

/** 붙이기 전에 그 줄의 요소를 본다: 여는 태그, 붙은 컴포넌트나 감싸는 컴포넌트, cozy attach와 같은 거절 */
class AttachPlaceTest {

	static final String TEMPLATE = """
			<div class="card-body">
			  <div x-data="memoBodyCount" data-limit="5000">
			    <textarea name="body"></textarea>
			    <div class="form-hint"></div>
			  </div>
			  <p class="text-secondary">도움말</p>
			  <div th:replace="~{cozy/component/field :: field('a', 'b', 'text')}"></div>
			  <span></span><span></span>
			</div>
			""";

	@Test
	void anElementOutsideEveryComponentHasNone() {
		assertThat(Attach.place(TEMPLATE, "memo/internal/fields", 6))
			.isEqualTo(new Attach.Place("<p class=\"text-secondary\">", null, false));
	}

	@Test
	void anInnerElementNamesTheComponentAroundItAndTheRootNamesItsOwn() {
		assertThat(Attach.place(TEMPLATE, "memo/internal/fields", 4))
			.isEqualTo(new Attach.Place("<div class=\"form-hint\">", "memoBodyCount", false));
		assertThat(Attach.place(TEMPLATE, "memo/internal/fields", 2))
			.isEqualTo(new Attach.Place("<div x-data=\"memoBodyCount\" data-limit=\"5000\">", "memoBodyCount", true));
	}

	@Test
	void theLinesCozyAttachRefusesAreRefusedForTheSameReason() {
		assertThatThrownBy(() -> Attach.place(TEMPLATE, "memo/internal/fields", 7)).isInstanceOf(CliException.class)
			.hasMessageContaining("memo/internal/fields:7의 요소는 그려질 때 다른 것으로 바뀌어 속성이 남지 않습니다");
		assertThatThrownBy(() -> Attach.place(TEMPLATE, "memo/internal/fields", 8)).hasMessageContaining("시작하는 요소가 2개입니다");
		assertThatThrownBy(() -> Attach.place(TEMPLATE, "memo/internal/fields", 10)).hasMessageContaining("시작하는 요소가 0개입니다");
	}
}
