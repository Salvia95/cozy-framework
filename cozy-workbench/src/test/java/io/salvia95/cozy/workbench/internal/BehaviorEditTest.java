package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 컴포넌트를 고치는 폼: 지금 항목으로 채우고, 보낸 폼을 지금 항목과 견주어 명령으로 */
class BehaviorEditTest {

	static final WorkbenchEngine.Behavior BODY_COUNT = new WorkbenchEngine.Behavior("memo", "memoBodyCount", "글자 수를 보인다",
			List.of(entry("length", "number@data-length"), entry("limit", "number=5000@data-limit")),
			List.of(entry("over", "gt(length, limit)"), entry("label", "format('{length}자')")),
			List.of(entry("count", "set(length, length(event.value))")), List.of(), List.of(entry("memo:saved", "count")), List.of());

	static WorkbenchEngine.Entry entry(String name, String text) {
		return new WorkbenchEngine.Entry(name, text);
	}

	static Map<String, List<String>> filled() {
		return new LinkedHashMap<>(BehaviorEdit.values(BODY_COUNT));
	}

	@Test
	void theFormIsFilledWithTheComponentAsTheAddBehaviorFormNamesIt() {
		Map<String, List<String>> values = BehaviorEdit.values(BODY_COUNT);

		assertThat(values).containsEntry("arg.모듈", List.of("memo"))
			.containsEntry("arg.동작", List.of("body-count"))
			.containsEntry("opt.purpose", List.of("글자 수를 보인다"))
			.containsEntry("opt.state.1.name", List.of("limit"))
			.containsEntry("opt.state.1.declaration", List.of("number=5000@data-limit"))
			.containsEntry("opt.computed.0.expression", List.of("gt(length, limit)"))
			.containsEntry("opt.action.0.steps", List.of("set(length, length(event.value))"))
			.as("받는 화면 이벤트는 동작:이벤트 순서의 칸")
			.containsEntry("opt.listen.0.action", List.of("count"))
			.containsEntry("opt.listen.0.event", List.of("memo:saved"));
		assertThat(BehaviorEdit.action("memo", "memoBodyCount")).isEqualTo("body-count");
		assertThat(BehaviorEdit.action("activity", "activityList2")).isEqualTo("list2");
	}

	@Test
	void theFilledFormUnchangedRunsNothing() {
		assertThat(BehaviorEdit.commands(BODY_COUNT, filled())).isEmpty();
	}

	@Test
	void changedRowsAddAndRemovedRowsRemoveAfterwards() {
		Map<String, List<String>> form = filled();
		form.put("opt.computed.1.expression", List.of("format('{length}자', 'ko-KR')"));
		form.remove("opt.state.1.name");
		form.remove("opt.state.1.declaration");
		form.put("opt.computed.0.expression", List.of("gt(length, 5000)"));
		form.put("opt.action.1.name", List.of("clear"));
		form.put("opt.action.1.steps", List.of("reset(length)"));
		form.put("opt.listen.0.action", List.of(""));
		form.put("opt.listen.0.event", List.of(""));
		form.put("opt.purpose", List.of("본문의 글자 수를 보인다"));

		assertThat(BehaviorEdit.commands(BODY_COUNT, form)).containsExactly(
				List.of("add", "behavior", "memo", "body-count", "--purpose", "본문의 글자 수를 보인다", "--computed", "over:gt(length, 5000)",
						"--computed", "label:format('{length}자', 'ko-KR')", "--action", "clear:reset(length)"),
				List.of("remove", "behavior", "memo", "body-count", "--state", "limit", "--listen", "memo:saved"));
	}

	@Test
	void aNameMovedToAnotherSectionIsRemovedFirst() {
		Map<String, List<String>> form = filled();
		form.remove("opt.state.1.name");
		form.remove("opt.state.1.declaration");
		form.put("opt.computed.0.expression", List.of("gt(length, 5000)"));
		form.put("opt.computed.2.name", List.of("limit"));
		form.put("opt.computed.2.expression", List.of("5000"));

		assertThat(BehaviorEdit.commands(BODY_COUNT, form)).extracting(List::getFirst).containsExactly("remove", "add");
	}

	@Test
	void onlyRemovingRunsOnlyTheRemove() {
		Map<String, List<String>> form = filled();
		form.put("opt.listen.0.action", List.of(""));
		form.put("opt.listen.0.event", List.of(""));

		assertThat(BehaviorEdit.commands(BODY_COUNT, form))
			.containsExactly(List.of("remove", "behavior", "memo", "body-count", "--listen", "memo:saved"));
	}
}
