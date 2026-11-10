package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Entry;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Param;
import io.salvia95.cozy.workbench.internal.ScreenTree.Item;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine.OutlineNode;

/** describe의 화면 구조를 보이는 트리로 바꾼다: 삽입은 매니페스트의 표시 이름으로, 영역은 그 파라미터의 표시 이름으로. 식별자는 따로 남는다. */
class ScreenTreeTest {

	static final FragmentManifest MANIFEST = new FragmentManifest(List.of(
			new Entry("pattern/list-with-filter", "cozy/pattern/list-with-filter",
					"listWithFilter(cozyHeading, cozyActions, cozyFilter, cozyResult)", "목록", "목록 탐색",
					List.of(new Param("cozyHeading", "String", true, "", ""), new Param("cozyActions", "fragment", false, "", ""),
							new Param("cozyFilter", "fragment", true, "", "필터"), new Param("cozyResult", "fragment", true, "", "결과")),
					java.util.Map.of(), false, "test", 1),
			new Entry("component/badge", "cozy/component/badge", "badge(text, tone)", "표시", "", List.of(), java.util.Map.of(),
					false, "test", 1)));

	static OutlineNode node(int line, int depth, String kind, String target, String fragment, String id, String... args) {
		return new OutlineNode(line, line + 2, depth, kind, target, fragment, id, List.of(args));
	}

	@Test
	void namesInsertionsFromTheManifestAndSlotsFromItsParameters() {
		List<Item> items = ScreenTree.of(List.of(
				node(7, 0, "replace", "cozy/pattern/list-with-filter", "listWithFilter", null, "'메모'", "~{::#actions}",
						"~{::#filter}", "~{::#result}"),
				node(8, 1, "slot", "", null, "actions"), node(8, 2, "replace", "cozy/icon/tabler", "icon", null, "'plus'"),
				node(10, 1, "slot", "", null, "filter"), node(28, 1, "slot", "", null, "result"),
				node(28, 2, "fragment", "", "result", "result"),
				node(47, 3, "replace", "cozy/component/badge", "badge", null, "'고정'", "'neutral'"),
				node(60, 3, "replace", "memo/spi/card", "card", null)), MANIFEST, "templates/memo/api/list.html");
		assertThat(items).extracting(Item::label, Item::detail, Item::kindLabel, Item::depth)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("목록 탐색", "list-with-filter", "패턴", 0),
					org.assertj.core.groups.Tuple.tuple("영역", "cozyActions", "영역", 1),
					org.assertj.core.groups.Tuple.tuple("필터 영역", "cozyFilter", "영역", 1),
					org.assertj.core.groups.Tuple.tuple("결과 영역", "cozyResult", "영역", 1),
					org.assertj.core.groups.Tuple.tuple("프래그먼트", "result", "프래그먼트", 2),
					org.assertj.core.groups.Tuple.tuple("컴포넌트", "badge", "컴포넌트", 3),
					org.assertj.core.groups.Tuple.tuple("프래그먼트", "card", "프래그먼트", 3));
		assertThat(items).extracting(Item::code)
			.as("에이전트에게 넘기는 이름은 식별자다")
			.containsExactly("list-with-filter (패턴)", "cozyActions (#actions)", "cozyFilter (#filter)", "cozyResult (#result)",
					"result (fragment 선언)", "badge (컴포넌트)", "card (memo/spi/card)");
		assertThat(items.get(0).location()).isEqualTo("templates/memo/api/list.html:7");
		assertThat(items.get(0)).extracting(Item::line, Item::endLine, Item::target)
			.containsExactly(7, 9, "cozy/pattern/list-with-filter");
		assertThat(items.get(1).target()).as("영역과 선언은 삽입 대상이 없다").isEmpty();
	}

	@Test
	void aSlotWhoseParameterIsUnknownKeepsItsId() {
		List<Item> items = ScreenTree.of(
				List.of(node(3, 0, "replace", "memo/spi/panel", "panel", null, "~{::#body}"), node(4, 1, "slot", "", null, "body")),
				MANIFEST, "templates/x.html");
		assertThat(items).extracting(Item::label, Item::detail)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("프래그먼트", "panel"),
					org.assertj.core.groups.Tuple.tuple("영역", "#body"));
	}
}
