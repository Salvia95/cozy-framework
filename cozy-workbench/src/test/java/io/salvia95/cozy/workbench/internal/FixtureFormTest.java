package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.workbench.internal.FixtureForm.Block;
import io.salvia95.cozy.workbench.internal.FixtureForm.Column;
import io.salvia95.cozy.workbench.internal.FixtureForm.Input;
import io.salvia95.cozy.workbench.internal.FixtureForm.Prop;
import io.salvia95.cozy.workbench.internal.FixtureForm.Section;

/**
 * 상태의 샘플 데이터를 화면 명세의 모델 타입에 맞춰 묶는다. 객체는 속성 표, 객체의 목록은 그리드가 되고, 값마다 타입에 맞는 입력을 쓴다.
 */
class FixtureFormTest {

	enum Status {
		OPEN, CLOSED
	}

	public record Row(long id, String title, boolean pinned, Status status, LocalDate due) {
	}

	public record Page(int number, int size, long totalElements) {
	}

	public record ListResult(List<Row> rows, Page page, List<String> tags) {
	}

	/** getter·setter가 있는 폼 클래스 */
	public static class Filter {

		private String q;

		private boolean pinned;

		private Integer page;

		private BigDecimal rate;

		private LocalDateTime since;

		public void setQ(String q) {
			this.q = q;
		}

		public void setPinned(boolean pinned) {
			this.pinned = pinned;
		}

		public void setPage(Integer page) {
			this.page = page;
		}

		public void setRate(BigDecimal rate) {
			this.rate = rate;
		}

		public void setSince(LocalDateTime since) {
			this.since = since;
		}

	}

	static final String OWNER = FixtureFormTest.class.getName() + ".";

	static final Map<String, String> MODEL = Map.of("filter", OWNER + "Filter", "result", OWNER + "ListResult");

	static final String SPEC = """
			screen: memo-list
			template: memo/api/list
			model:
			  filter: x.Filter
			  result: x.ListResult
			states:
			  default:
			    filter: { q: "", pinned: false, rate: 1.5, since: "2026-11-06T10:00" }
			    result:
			      rows:
			        - { id: 1, title: 회의록 정리, pinned: true, status: OPEN, due: "2026-11-30" }
			        - { id: 2, title: 주간 보고, pinned: false, status: CLOSED }
			      page: { number: 1, size: 10, totalElements: 2 }
			      tags: [회의, 보고]
			  empty:
			    filter: {}
			    result:
			      rows: []
			      page: { number: 1, size: 10, totalElements: 0 }
			      tags: []
			    _errors:
			      filter: { q: 검색어를 입력하세요 }
			""";

	static List<Section> form(String state) {
		return FixtureForm.of(SPEC, state, MODEL, Map.of());
	}

	static Section section(List<Section> sections, String name) {
		return sections.stream().filter((s) -> s.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
	}

	static Block block(Section section, String title) {
		return section.blocks()
			.stream()
			.filter((b) -> b.title().equals(title))
			.findFirst()
			.orElseThrow(() -> new AssertionError(section.name() + " " + title));
	}

	static Input prop(Block block, String name) {
		return block.props().stream().filter((p) -> p.name().equals(name)).findFirst().map(Prop::input).orElseThrow(() -> new AssertionError(name));
	}

	@Test
	void anObjectIsAPropertyTableAndAListOfObjectsIsAGrid() {
		List<Section> sections = form("default");
		assertThat(sections).extracting(Section::name, Section::type).containsExactly(
				org.assertj.core.groups.Tuple.tuple("filter", "Filter"), org.assertj.core.groups.Tuple.tuple("result", "ListResult"));

		Section result = section(sections, "result");
		assertThat(result.blocks()).extracting(Block::title, Block::grid, Block::type)
			.as("객체 안의 객체와 목록은 경로를 제목으로 따로 보인다")
			.containsExactly(org.assertj.core.groups.Tuple.tuple("rows", true, "List<Row>"),
					org.assertj.core.groups.Tuple.tuple("page", false, "Page"), org.assertj.core.groups.Tuple.tuple("tags", true, "List<String>"));

		Block rows = block(result, "rows");
		assertThat(rows.columns()).extracting(Column::name).as("열은 타입의 속성 순서").containsExactly("id", "title", "pinned", "status", "due");
		assertThat(rows.columns()).extracting(Column::kind).containsExactly("정수", "텍스트", "true/false", "Status", "날짜");
		assertThat(rows.rows()).hasSize(2);
		assertThat(rows.rows().get(0).cells()).extracting(Input::path)
			.containsExactly("result/rows/0/id", "result/rows/0/title", "result/rows/0/pinned", "result/rows/0/status", "result/rows/0/due");
		assertThat(rows.rows().get(0).cells()).extracting(Input::value).containsExactly("1", "회의록 정리", "true", "OPEN", "2026-11-30");
		assertThat(rows.rows().get(1).cells().get(4)).as("행에 없는 값은 없음으로 보인다")
			.extracting(Input::present, Input::editable, Input::control)
			.containsExactly(false, false, "absent");

		Block page = block(result, "page");
		assertThat(page.props()).extracting(Prop::name).containsExactly("number", "size", "totalElements");
		assertThat(prop(page, "totalElements")).extracting(Input::path, Input::value).containsExactly("result/page/totalElements", "2");

		Block tags = block(result, "tags");
		assertThat(tags.columns()).extracting(Column::name, Column::kind).containsExactly(org.assertj.core.groups.Tuple.tuple("값", "텍스트"));
		assertThat(tags.rows()).extracting((r) -> r.cells().getFirst().path()).containsExactly("result/tags/0", "result/tags/1");
	}

	@Test
	void eachValueGetsAnInputThatFitsItsType() {
		Section filter = section(form("default"), "filter");
		Block root = block(filter, "");
		assertThat(root.props()).extracting(Prop::name).as("폼 클래스는 필드 순서").containsExactly("q", "pinned", "page", "rate", "since");
		assertThat(prop(root, "q")).extracting(Input::control, Input::value).containsExactly("text", "");
		assertThat(prop(root, "pinned")).extracting(Input::control, Input::checked).containsExactly("checkbox", false);
		assertThat(prop(root, "rate")).extracting(Input::control, Input::step).containsExactly("number", "any");
		assertThat(prop(root, "since")).extracting(Input::control, Input::value).containsExactly("datetime-local", "2026-11-06T10:00");

		List<Input> row = block(section(form("default"), "result"), "rows").rows().getFirst().cells();
		assertThat(row.get(0)).extracting(Input::control, Input::step).containsExactly("number", "1");
		assertThat(row.get(2)).extracting(Input::control, Input::checked).containsExactly("checkbox", true);
		assertThat(row.get(3)).extracting(Input::control, Input::options).containsExactly("select", List.of("OPEN", "CLOSED"));
		assertThat(row.get(4).control()).isEqualTo("date");
		assertThat(prop(root, "page")).as("명세에 없는 속성").extracting(Input::present, Input::control).containsExactly(false, "absent");
	}

	@Test
	void anEmptyListKeepsItsColumnsAndAnEmptyObjectShowsWhatIsMissing() {
		List<Section> sections = form("empty");
		Block rows = block(section(sections, "result"), "rows");
		assertThat(rows.rows()).isEmpty();
		assertThat(rows.columns()).extracting(Column::name).containsExactly("id", "title", "pinned", "status", "due");
		assertThat(block(section(sections, "filter"), "").props()).extracting(Prop::name, (p) -> p.input().present())
			.containsOnly(org.assertj.core.groups.Tuple.tuple("q", false), org.assertj.core.groups.Tuple.tuple("pinned", false),
					org.assertj.core.groups.Tuple.tuple("page", false), org.assertj.core.groups.Tuple.tuple("rate", false),
					org.assertj.core.groups.Tuple.tuple("since", false));
	}

	@Test
	void validationErrorsAndUnknownTypesFollowTheShapeOfTheYaml() {
		Section errors = section(form("empty"), "_errors");
		assertThat(errors.label()).isEqualTo("검증 오류");
		assertThat(errors.blocks()).extracting(Block::title).containsExactly("filter");
		assertThat(prop(block(errors, "filter"), "q")).extracting(Input::path, Input::control, Input::value)
			.containsExactly("_errors/filter/q", "text", "검색어를 입력하세요");

		List<Section> untyped = FixtureForm.of(SPEC, "default", Map.of("filter", "no.such.Type", "result", "java.util.Map"), Map.of());
		Section filter = section(untyped, "filter");
		assertThat(filter.note()).contains("no.such.Type");
		assertThat(block(filter, "").props()).extracting(Prop::name).containsExactly("q", "pinned", "rate", "since");
		assertThat(prop(block(filter, ""), "pinned").control()).as("YAML의 참·거짓").isEqualTo("checkbox");
		Block rows = block(section(untyped, "result"), "rows");
		assertThat(rows.columns()).extracting(Column::name).as("행의 키를 모은 열").containsExactly("id", "title", "pinned", "status", "due");
		assertThat(rows.rows().get(1).cells().get(4).present()).isFalse();
	}

	@Test
	void aDraftValueIsShownAndAValueThatDoesNotFitFallsBackToText() {
		List<Section> sections = FixtureForm.of(SPEC, "default", MODEL,
				Map.of("result/rows/0/pinned", "아마도", "result/rows/0/title", "적던 제목", "result/rows/0/status", "LATER"));
		List<Input> row = block(section(sections, "result"), "rows").rows().getFirst().cells();
		assertThat(row.get(1).value()).isEqualTo("적던 제목");
		assertThat(row.get(2)).extracting(Input::control, Input::value).containsExactly("text", "아마도");
		assertThat(row.get(3)).extracting(Input::control, Input::value).containsExactly("text", "LATER");
	}

	@Test
	void nullValuesAreEmptyInputsAndBlockValuesAreReadOnly() {
		String spec = """
				screen: x-detail
				template: x/api/detail
				model:
				  memo: x.Memo
				states:
				  default:
				    memo:
				      title: null
				      body: |
				        첫 줄
				        둘째 줄
				""";
		Block memo = block(section(FixtureForm.of(spec, "default", Map.of("memo", "java.lang.Object"), Map.of()), "memo"), "");
		assertThat(prop(memo, "title")).extracting(Input::value, Input::placeholder, Input::editable).containsExactly("", "null", true);
		assertThat(prop(memo, "body")).extracting(Input::control, Input::value).containsExactly("fixed", "첫 줄\n둘째 줄\n");
	}

	@Test
	void valuesAreCheckedAgainstTheirTypeBeforeSaving() {
		List<Input> inputs = FixtureForm.inputs(form("default"));
		assertThat(FixtureForm.changes(inputs, Map.of("result/rows/0/title", "회의록 정리", "filter/q", ""))).as("같은 값은 바뀐 것이 아니다")
			.isEmpty();
		assertThat(FixtureForm.changes(inputs, Map.of("filter/since", "2026-11-06T10:00:00"))).as("같은 시각").isEmpty();
		assertThat(FixtureForm.changes(inputs, Map.of("result/rows/0/title", "분기 계획"))).containsEntry("result/rows/0/title", "분기 계획");

		assertThat(FixtureForm.problem(inputs, Map.of("result/rows/0/pinned", "아마도")))
			.isEqualTo("result/rows/0/pinned: true나 false만 씁니다 (적은 값: 아마도)");
		assertThat(FixtureForm.problem(inputs, Map.of("result/rows/0/id", "1.5"))).contains("정수만 씁니다");
		assertThat(FixtureForm.problem(inputs, Map.of("result/rows/0/status", "LATER"))).contains("OPEN, CLOSED");
		assertThat(FixtureForm.problem(inputs, Map.of("result/rows/0/due", "11월 30일"))).contains("날짜");
		assertThat(FixtureForm.problem(inputs, Map.of("result/rows/0/title", "아무 글", "result/rows/0/id", "7"))).isNull();
	}

	@Test
	void namesComeFromTheDtoDocsAndTheIdentifierStays() {
		Map<String, String> rowDocs = Map.of("title", "제목", "due", "마감일 (yyyy-MM-dd)");
		List<Section> sections = FixtureForm.of(SPEC, "default", MODEL, Map.of(),
				(type) -> type == Row.class ? rowDocs : type == Filter.class ? Map.of("q", "검색어") : Map.of());
		Block rows = block(section(sections, "result"), "rows");
		assertThat(rows.columns()).extracting(Column::name, Column::title, Column::description)
			.contains(org.assertj.core.groups.Tuple.tuple("title", "제목", "제목"),
					org.assertj.core.groups.Tuple.tuple("due", "마감일", "마감일 (yyyy-MM-dd)"),
					org.assertj.core.groups.Tuple.tuple("id", "", ""));
		assertThat(rows.columns().get(4).spoken()).isEqualTo("마감일");
		assertThat(rows.columns().get(0).spoken()).as("이름이 없으면 식별자").isEqualTo("id");
		Prop q = block(section(sections, "filter"), "").props().getFirst();
		assertThat(q).extracting(Prop::name, Prop::title).containsExactly("q", "검색어");

		assertThat(FixtureForm.of(SPEC, "default", MODEL, Map.of(), (type) -> {
			throw new IllegalStateException("소스를 읽지 못함");
		})).as("설명을 읽지 못해도 화면은 그린다").hasSize(2);
	}

	@Test
	void eachGridKnowsItsPathAndTheRowToAdd() {
		List<Section> sections = form("default");
		Block rows = block(section(sections, "result"), "rows");
		assertThat(rows).extracting(Block::path, Block::anchor, Block::addable)
			.containsExactly("result/rows", "fixture-result-rows", true);
		assertThat(rows.newItem()).startsWith("{ id: 0, title: \"\", pinned: false, status: OPEN, due: \"").endsWith("\" }");
		assertThat(block(section(sections, "result"), "tags").newItem()).isEqualTo("\"\"");
		assertThat(block(section(sections, "result"), "page").path()).isEqualTo("result/page");
		assertThat(FixtureForm.grid(sections, "result/tags")).isPresent();
		assertThat(FixtureForm.grid(sections, "result/page")).as("표는 그리드가 아니다").isEmpty();

		List<Section> untyped = FixtureForm.of(SPEC, "empty", Map.of("filter", "java.util.Map", "result", "java.util.Map"), Map.of());
		Block empty = block(section(untyped, "result"), "rows");
		assertThat(empty).extracting(Block::newItem, Block::addable).as("타입도 행도 없으면 더할 수 없다").containsExactly("", false);
		assertThat(FixtureForm.newItem(Page.class)).isEqualTo("{ number: 0, size: 0, totalElements: 0 }");
	}
}
