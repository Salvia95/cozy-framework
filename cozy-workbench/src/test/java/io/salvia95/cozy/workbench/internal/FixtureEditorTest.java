package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.workbench.internal.FixtureEditor.Field;

/**
 * 화면 스펙 YAML에서 상태의 값 하나하나를 읽고, 그 값의 자리만 바꿔 쓴다. 주석, 키 순서, 들여쓰기, 따옴표가 없는 다른 값은 글자 그대로 남는다.
 */
class FixtureEditorTest {

	static final String SPEC = """
			# 메모 목록 화면 스펙. 첫 줄 주석
			screen: memo-list
			template: memo/api/list
			model:
			  filter: it.MemoFilter
			  result: it.ListResult
			states:
			  default:
			    filter: { q: "", pinned: false }   # 조건 없음
			    result:
			      rows:
			        - { id: 1, title: 회의록 정리, pinned: true, updatedAt: "2026-11-06 10:00" }
			        - { id: 2, title: 주간 보고, pinned: false, updatedAt: "2026-11-05 17:20" }
			      page: { number: 1, size: 10, totalElements: 2 }
			  empty:
			    filter: { q: "", pinned: false }
			    result:
			      rows: []
			      page: { number: 1, size: 10, totalElements: 0 }
			""";

	static Field field(List<Field> fields, String path) {
		return fields.stream().filter((f) -> f.path().equals(path)).findFirst().orElseThrow(() -> new AssertionError(path));
	}

	@Test
	void listsEveryLeafValueOfAStateWithItsPath() {
		List<Field> fields = FixtureEditor.fields(SPEC, "default");
		assertThat(fields).extracting(Field::path)
			.containsExactly("filter/q", "filter/pinned", "result/rows/0/id", "result/rows/0/title", "result/rows/0/pinned",
					"result/rows/0/updatedAt", "result/rows/1/id", "result/rows/1/title", "result/rows/1/pinned",
					"result/rows/1/updatedAt", "result/page/number", "result/page/size", "result/page/totalElements");
		assertThat(field(fields, "result/rows/0/title").value()).isEqualTo("회의록 정리");
		assertThat(field(fields, "result/rows/0/updatedAt").value()).isEqualTo("2026-11-06 10:00");
		assertThat(field(fields, "filter/q").value()).isEmpty();
		assertThat(field(fields, "filter/pinned")).extracting(Field::value, Field::kind).containsExactly("false", "bool");
		assertThat(field(fields, "result/rows/0/id").kind()).isEqualTo("int");
		assertThat(field(fields, "result/rows/0/title").kind()).isEqualTo("text");
		assertThat(FixtureEditor.fields(SPEC, "empty")).extracting(Field::path)
			.containsExactly("filter/q", "filter/pinned", "result/page/number", "result/page/size", "result/page/totalElements");
		assertThat(FixtureEditor.states(SPEC)).containsExactly("default", "empty");
	}

	@Test
	void changingAValueTouchesOnlyThatValue() {
		String changed = FixtureEditor.set(SPEC, "default", Map.of("result/rows/0/title", "분기 계획"));
		assertThat(changed).isEqualTo(SPEC.replace("title: 회의록 정리", "title: 분기 계획"));
		assertThat(changed).contains("# 메모 목록 화면 스펙. 첫 줄 주석").contains("# 조건 없음");

		String several = FixtureEditor.set(SPEC, "default",
				Map.of("result/rows/1/pinned", "true", "result/page/totalElements", "42", "filter/q", "보고"));
		assertThat(several).isEqualTo(SPEC.replace("title: 주간 보고, pinned: false", "title: 주간 보고, pinned: true")
			.replace("size: 10, totalElements: 2 }", "size: 10, totalElements: 42 }")
			.replaceFirst("q: \"\", pinned: false }   # 조건 없음", "q: \"보고\", pinned: false }   # 조건 없음"));
		assertThat(FixtureEditor.set(SPEC, "default", Map.of("filter/pinned", "false"))).as("같은 값이면 그대로").isEqualTo(SPEC);
	}

	@Test
	void textThatWouldBreakTheYamlIsQuoted() {
		String changed = FixtureEditor.set(SPEC, "default", Map.of("result/rows/0/title", "회의: 안건, {초안} #1 \"인용\""));
		assertThat(changed).contains("title: \"회의: 안건, {초안} #1 \\\"인용\\\"\", pinned: true");
		assertThat(field(FixtureEditor.fields(changed, "default"), "result/rows/0/title").value())
			.isEqualTo("회의: 안건, {초안} #1 \"인용\"");
		assertThat(FixtureEditor.fields(changed, "default")).as("다른 값은 그대로 읽힌다").hasSize(13);
	}

	@Test
	void textStaysTextEvenWhenItLooksLikeANumberOrABoolean() {
		String changed = FixtureEditor.set(SPEC, "default", Map.of("result/rows/0/title", "2026", "result/rows/1/title", "true"));
		assertThat(changed).contains("title: \"2026\"").contains("title: \"true\"");
		assertThat(field(FixtureEditor.fields(changed, "default"), "result/rows/0/title").kind()).isEqualTo("text");
	}

	@Test
	void anEmptyTextIsAnEmptyString() {
		String changed = FixtureEditor.set(SPEC, "default", Map.of("result/rows/0/title", ""));
		assertThat(changed).contains("title: \"\", pinned: true");
	}

	@Test
	void valuesAfterWideCharactersAreStillReplacedInPlace() {
		String spec = SPEC.replace("title: 회의록 정리", "title: \"회의 😀\"");
		String changed = FixtureEditor.set(spec, "default", Map.of("result/rows/0/updatedAt", "2026-12-01 09:00"));
		assertThat(changed).isEqualTo(spec.replace("2026-11-06 10:00", "2026-12-01 09:00"));
	}

	@Test
	void blockScalarsAndEmptyValuesAreNotEditable() {
		String spec = """
				screen: memo-detail
				template: memo/api/detail
				model:
				  memo: it.Memo
				states:
				  default:
				    memo:
				      title: 제목
				      body: |
				        첫 줄
				        둘째 줄
				      note:
				  empty:
				    memo: {}
				""";
		List<Field> fields = FixtureEditor.fields(spec, "default");
		assertThat(field(fields, "memo/title").editable()).isTrue();
		assertThat(field(fields, "memo/body")).extracting(Field::editable, Field::value).containsExactly(false, "첫 줄\n둘째 줄\n");
		assertThat(field(fields, "memo/note").editable()).isFalse();
		assertThatThrownBy(() -> FixtureEditor.set(spec, "default", Map.of("memo/body", "x")))
			.hasMessageContaining("memo/body")
			.hasMessageContaining("고칠 수 없습니다");
		assertThat(FixtureEditor.fields(spec, "empty")).isEmpty();
	}

	@Test
	void unknownStatesAndPathsAreRefused() {
		assertThatThrownBy(() -> FixtureEditor.fields(SPEC, "no-such")).hasMessageContaining("no-such");
		assertThatThrownBy(() -> FixtureEditor.set(SPEC, "default", Map.of("result/rows/9/title", "x")))
			.hasMessageContaining("result/rows/9/title");
	}

	@Test
	void duplicatingAStateCopiesItsTextUnderANewName() {
		String changed = FixtureEditor.duplicate(SPEC, "default", "many");
		assertThat(FixtureEditor.states(changed)).containsExactly("default", "many", "empty");
		assertThat(FixtureEditor.fields(changed, "many")).extracting(Field::path, Field::value)
			.isEqualTo(FixtureEditor.fields(SPEC, "default").stream()
				.map((f) -> org.assertj.core.groups.Tuple.tuple(f.path(), f.value()))
				.toList());
		assertThat(changed).startsWith(SPEC.substring(0, SPEC.indexOf("  empty:")));
		assertThat(changed).endsWith(SPEC.substring(SPEC.indexOf("  empty:")));
		assertThat(changed).contains("  many:\n    filter: { q: \"\", pinned: false }   # 조건 없음\n");

		String last = FixtureEditor.duplicate(SPEC, "empty", "none");
		assertThat(FixtureEditor.states(last)).containsExactly("default", "empty", "none");
		assertThat(last).startsWith(SPEC).endsWith("      page: { number: 1, size: 10, totalElements: 0 }\n");
	}

	@Test
	void stateNamesMustBeNewAndSimple() {
		assertThatThrownBy(() -> FixtureEditor.duplicate(SPEC, "default", "empty")).hasMessageContaining("이미 있습니다");
		assertThatThrownBy(() -> FixtureEditor.duplicate(SPEC, "default", "많은 것")).hasMessageContaining("영문");
		assertThatThrownBy(() -> FixtureEditor.duplicate(SPEC, "no-such", "x")).hasMessageContaining("no-such");
	}

	@Test
	void aRowIsAddedDuplicatedAndDeletedLineByLine() {
		String added = FixtureEditor.addRow(SPEC, "default", "result/rows", "{ id: 0, title: \"\", pinned: false, updatedAt: \"\" }");
		assertThat(added).isEqualTo(SPEC.replace("        - { id: 2, title: 주간 보고, pinned: false, updatedAt: \"2026-11-05 17:20\" }\n",
				"        - { id: 2, title: 주간 보고, pinned: false, updatedAt: \"2026-11-05 17:20\" }\n        - { id: 0, title: \"\", pinned: false, updatedAt: \"\" }\n"));

		String duplicated = FixtureEditor.duplicateRow(SPEC, "default", "result/rows", 0);
		String first = "        - { id: 1, title: 회의록 정리, pinned: true, updatedAt: \"2026-11-06 10:00\" }\n";
		assertThat(duplicated).isEqualTo(SPEC.replace(first, first + first));

		String deleted = FixtureEditor.deleteRow(SPEC, "default", "result/rows", 0);
		assertThat(deleted).isEqualTo(SPEC.replace(first, ""));
		assertThat(FixtureEditor.fields(deleted, "default")).extracting(Field::path).contains("result/rows/0/title").doesNotContain("result/rows/1/title");
		String last = FixtureEditor.deleteRow(SPEC, "default", "result/rows", 1);
		assertThat(FixtureEditor.fields(last, "default")).extracting(Field::path).contains("result/rows/0/title").doesNotContain("result/rows/1/id");
		assertThat(last).contains("# 조건 없음").contains("      page: { number: 1, size: 10, totalElements: 2 }");
	}

	@Test
	void deletingTheLastRowLeavesAnEmptyListAndAddingToAnEmptyListMakesABlockList() {
		String none = FixtureEditor.deleteRow(FixtureEditor.deleteRow(SPEC, "default", "result/rows", 0), "default", "result/rows", 0);
		assertThat(none).contains("      rows: []\n      page: { number: 1, size: 10, totalElements: 2 }");
		assertThat(FixtureEditor.values(none, "default").get("result")).isEqualTo(Map.of("rows", List.of(),
				"page", Map.of("number", 1, "size", 10, "totalElements", 2)));

		String first = FixtureEditor.addRow(SPEC, "empty", "result/rows", "{ id: 0, title: \"\" }");
		assertThat(first).contains("    result:\n      rows:\n        - { id: 0, title: \"\" }\n      page: { number: 1, size: 10, totalElements: 0 }");
		assertThat(FixtureEditor.fields(first, "empty")).extracting(Field::path).contains("result/rows/0/id");
		assertThat(FixtureEditor.states(first)).containsExactly("default", "empty");
	}

	@Test
	void flowListsAreEditedInsideTheirBrackets() {
		String spec = """
				screen: memo-detail
				template: memo/api/detail
				model:
				  memo: it.Detail
				states:
				  default:
				    memo: { id: 1, bodyLines: [첫 줄, 둘째 줄], tags: [] }   # 메모
				  empty:
				    memo: { id: 2, bodyLines: [], tags: [] }
				""";
		assertThat(FixtureEditor.addRow(spec, "default", "memo/bodyLines", "\"\"")).contains("bodyLines: [첫 줄, 둘째 줄, \"\"], tags: [] }   # 메모");
		assertThat(FixtureEditor.addRow(spec, "default", "memo/tags", "\"\"")).as("흐름 맵 안의 빈 목록은 흐름 목록으로")
			.contains("tags: [\"\"] }   # 메모");
		assertThat(FixtureEditor.duplicateRow(spec, "default", "memo/bodyLines", 0)).contains("bodyLines: [첫 줄, 첫 줄, 둘째 줄]");
		assertThat(FixtureEditor.deleteRow(spec, "default", "memo/bodyLines", 0)).contains("bodyLines: [둘째 줄]");
		assertThat(FixtureEditor.deleteRow(spec, "default", "memo/bodyLines", 1)).contains("bodyLines: [첫 줄]");
		assertThat(FixtureEditor.deleteRow(FixtureEditor.deleteRow(spec, "default", "memo/bodyLines", 1), "default", "memo/bodyLines", 0))
			.contains("bodyLines: [], tags: [] }   # 메모");
	}

	@Test
	void rowsThatDoNotExistAreRefused() {
		assertThatThrownBy(() -> FixtureEditor.deleteRow(SPEC, "default", "result/rows", 5)).hasMessageContaining("6행이 없습니다");
		assertThatThrownBy(() -> FixtureEditor.addRow(SPEC, "default", "result/page", "{}")).hasMessageContaining("목록이 아닙니다");
		assertThatThrownBy(() -> FixtureEditor.duplicateRow(SPEC, "default", "result/none", 0)).hasMessageContaining("result/none");
	}
}
