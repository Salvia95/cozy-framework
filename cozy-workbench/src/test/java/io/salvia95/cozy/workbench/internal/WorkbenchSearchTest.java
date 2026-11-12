package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.workbench.internal.WorkbenchSearch.Entry;
import io.salvia95.cozy.workbench.internal.WorkbenchSearch.Group;

/** 화면, 컴포넌트, 생성 항목, 모듈을 한글 이름과 식별자로 찾는다. 띄어쓰기와 구분 글자는 보지 않는다 */
class WorkbenchSearchTest {

	static final List<Entry> ENTRIES = List.of(
			new Entry("화면", "메모 목록", "memo-list", "", "memo", "/s?spec=memo-list", List.of("memo/api/list")),
			new Entry("화면", "메모 상세", "memo-detail", "", "memo", "/s?spec=memo-detail", List.of("memo/api/detail")),
			new Entry("컴포넌트", "배지", "component/badge", "컴포넌트", "", "/c#badge", List.of("상태나 분류를 짧게 표시한다")),
			new Entry("컴포넌트", "목록 탐색", "pattern/list-with-filter", "패턴", "", "/c#list", List.of("검색과 표")),
			new Entry("생성 항목", "CRUD 화면", "cozy add crud", "화면", "", "/r?recipe=crud", List.of("목록, 등록, 상세, 수정, 삭제 화면")),
			new Entry("모듈", "memo", "io.salvia95.app.memo", "", "", "/m?module=memo", List.of()));

	static List<String> ids(List<Group> groups) {
		return groups.stream().flatMap((g) -> g.hits().stream()).map((h) -> h.entry().id()).toList();
	}

	@Test
	void findsByKoreanNameOrIdentifierIgnoringSpacesAndSeparators() {
		assertThat(ids(WorkbenchSearch.search("메모목록", ENTRIES))).containsExactly("memo-list");
		assertThat(ids(WorkbenchSearch.search("MemoList", ENTRIES))).containsExactly("memo-list");
		assertThat(ids(WorkbenchSearch.search("badge", ENTRIES))).containsExactly("component/badge");
		assertThat(ids(WorkbenchSearch.search("add crud", ENTRIES))).containsExactly("cozy add crud");
	}

	@Test
	void everyWordMustMatchTheSameEntry() {
		assertThat(ids(WorkbenchSearch.search("메모 상세", ENTRIES))).containsExactly("memo-detail");
		assertThat(ids(WorkbenchSearch.search("메모 배지", ENTRIES))).isEmpty();
	}

	@Test
	void namesRankBeforeDescriptionsAndGroupsFollowTheMenuOrder() {
		List<Group> groups = WorkbenchSearch.search("목록", ENTRIES);
		assertThat(groups).extracting(Group::kind).containsExactly("화면", "컴포넌트", "생성 항목");
		assertThat(groups.get(1).hits()).extracting((h) -> h.entry().id()).containsExactly("pattern/list-with-filter");
		List<Group> memo = WorkbenchSearch.search("memo", ENTRIES);
		assertThat(memo).extracting(Group::kind).containsExactly("화면", "모듈");
		assertThat(memo.get(1).hits().getFirst().score()).as("이름과 같으면 가장 높다").isEqualTo(6);
		assertThat(ids(WorkbenchSearch.search("짧게", ENTRIES))).as("설명에서도 찾는다").containsExactly("component/badge");
	}

	@Test
	void anEmptyQueryFindsNothing() {
		assertThat(WorkbenchSearch.search("", ENTRIES)).isEmpty();
		assertThat(WorkbenchSearch.search("  - / ", ENTRIES)).isEmpty();
		assertThat(WorkbenchSearch.search(null, ENTRIES)).isEmpty();
		assertThat(WorkbenchSearch.terms("x".repeat(500))).singleElement().asString().hasSize(WorkbenchSearch.MAX_QUERY);
	}
}
