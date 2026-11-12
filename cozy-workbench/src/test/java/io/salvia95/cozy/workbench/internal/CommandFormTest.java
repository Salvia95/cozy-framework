package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 코드 생성 폼: 인자를 입력으로, 보낸 값을 명령의 인자로 */
class CommandFormTest {

	static final List<WorkbenchEngine.Recipe> RECIPES = WorkbenchEngine.at(Path.of("target/no-such-project"), null).recipes();

	/** cozy describe --json의 모양 그대로 줄인 앱 */
	static final Map<String, Object> DESCRIBE = Map.of("app", Map.of("basePackage", "io.x"), "modules", List.of(
			Map.of("name", "memo", "facades", List.of(Map.of("type", "MemoFacade")), "finders", List.of(Map.of("type", "MemoFinder")),
					"dtos", List.of(Map.of("type", "MemoItem"), Map.of("type", "MemoPage")), "events",
					List.of(Map.of("type", "MemoCreated")), "controllers", List.of("MemoController")),
			Map.of("name", "activity", "facades", List.of(), "finders", List.of(Map.of("type", "ActivityFinder")), "dtos",
					List.of(Map.of("type", "ActivityItem")), "events", List.of(), "controllers", List.of())));

	static final Map<String, List<CommandForm.Group>> SOURCES = CommandForm.sources(DESCRIBE, List.of("notes", "checklist", "home"));

	static WorkbenchEngine.Recipe recipe(String name) {
		return RECIPES.stream().filter((r) -> r.name().equals(name)).findFirst().orElseThrow();
	}

	static CommandForm form(String recipe, Map<String, List<String>> values) {
		return CommandForm.of(recipe(recipe), values, SOURCES, null);
	}

	static Map<String, List<String>> values(String... nameValues) {
		Map<String, List<String>> values = new LinkedHashMap<>();
		for (int i = 0; i < nameValues.length; i += 2) {
			values.computeIfAbsent(nameValues[i], (k) -> new java.util.ArrayList<>()).add(nameValues[i + 1]);
		}
		return values;
	}

	@Test
	void singleArgumentsAreRowsOfAPropertyTableAndRepeatedOnesAreGrids() {
		CommandForm crud = form("crud", Map.of());
		assertThat(crud.fields()).extracting(CommandForm.Field::identifier)
			.containsExactly("<모듈>", "--entity", "--label", "--icon", "--publish");
		assertThat(crud.fields()).extracting((f) -> f.input().control())
			.as("모듈은 앱의 모듈을 제안(없으면 만든다), 아이콘은 아이콘 칸, 발행할 이벤트는 여럿 고르기")
			.containsExactly("suggest", "text", "text", "icon", "checks");
		assertThat(crud.fields().getFirst().input().groups().getFirst().options()).extracting(CommandForm.Option::value)
			.containsExactly("memo", "activity");
		assertThat(crud.fields().get(4).input().groups().getFirst().options()).extracting(CommandForm.Option::text)
			.containsExactly("생성 (created)", "수정 (updated)", "삭제 (deleted)");

		CommandForm.Grid fields = crud.grids().getFirst();
		assertThat(fields.identifier()).isEqualTo("--field");
		assertThat(fields.repeatable()).isTrue();
		assertThat(fields.columns()).extracting(CommandForm.Column::label).containsExactly("이름", "종류", "필수", "라벨");
		assertThat(fields.rows()).as("처음에는 빈 행 하나").hasSize(1);
		List<CommandForm.Input> cells = fields.rows().getFirst().cells();
		assertThat(cells).extracting(CommandForm.Input::control).containsExactly("text", "select", "flag", "text");
		assertThat(cells.get(1).value()).as("종류는 첫 값이 기본").isEqualTo("text");
		assertThat(cells.get(1).groups().getFirst().options()).extracting(CommandForm.Option::text)
			.as("crud는 instant를 쓰지 않는다")
			.containsExactly("한 줄 텍스트 (text)", "여러 줄 텍스트 (longtext)", "정수 (int)", "64비트 정수 (long)", "참/거짓 (bool)", "날짜 (date)");
		assertThat(cells.get(0).name()).isEqualTo("opt.field.0.name");
		assertThat(cells.get(0).label()).as("칸의 이름은 화면 낭독기가 읽는다").isEqualTo("필드 1행 이름");
		assertThat(crud.usesIcons()).isTrue();
	}

	@Test
	void theGridRowsAreJoinedBackIntoOneOptionEach() {
		CommandForm crud = form("crud", values("arg.모듈", "task", "opt.entity", "Task", "opt.label", "할 일", "opt.icon", "checklist",
				"opt.field.0.name", "title", "opt.field.0.kind", "text", "opt.field.0.required", "!", "opt.field.0.label", "제목",
				"opt.field.1.name", "", "opt.field.1.kind", "int", "opt.field.1.label", "",
				"opt.field.5.name", "dueOn", "opt.field.5.kind", "date", "opt.field.5.label", "마감일",
				"opt.field.7.name", " done ", "opt.field.7.kind", "bool", "opt.field.7.label", "",
				"opt.publish", "deleted", "opt.publish", "created"));
		assertThat(crud.missing()).isEmpty();
		assertThat(crud.args()).as("빈 행은 넘기지 않고, 플래그는 종류에 붙고, 빈 라벨은 뺀다. 고른 이벤트는 선택지 순서")
			.containsExactly("add", "crud", "task", "--entity", "Task", "--label", "할 일", "--icon", "checklist", "--field",
					"title:text!:제목", "--field", "dueOn:date:마감일", "--field", "done:bool", "--publish", "created,deleted");
		assertThat(crud.grids().getFirst().rows()).as("행 번호는 다시 매긴다").hasSize(4);
		assertThat(crud.grids().getFirst().rows().get(2).cells().getFirst().name()).isEqualTo("opt.field.2.name");
	}

	@Test
	void anEmptyRequiredPartOrGridIsReportedBeforeTheCommandRuns() {
		CommandForm noFields = form("crud", values("arg.모듈", "task", "opt.entity", "Task", "opt.label", "할 일", "opt.icon", "notes"));
		assertThat(noFields.missing()).containsExactly("필드");
		CommandForm noName = form("crud", values("arg.모듈", "task", "opt.entity", "Task", "opt.label", "할 일", "opt.icon", "notes",
				"opt.field.0.name", "title", "opt.field.0.kind", "text", "opt.field.1.name", "", "opt.field.1.kind", "text",
				"opt.field.1.label", "제목"));
		assertThat(noName.missing()).as("이름 없이 라벨만 적은 행").containsExactly("필드 2행의 이름");
		assertThat(form("module", Map.of()).missing()).containsExactly("새 모듈 이름 (소문자)");
	}

	@Test
	void rowsAreAddedDuplicatedAndDeletedWithoutLosingWhatWasTyped() {
		Map<String, List<String>> typed = values("opt.field.0.name", "title", "opt.field.0.kind", "text", "opt.field.0.required", "!",
				"opt.field.1.name", "body", "opt.field.1.kind", "longtext");
		WorkbenchEngine.Recipe crud = recipe("crud");
		CommandForm added = CommandForm.of(crud, typed, SOURCES, "add:opt.field");
		assertThat(rows(added)).containsExactly("title:text!", "body:longtext", ":text");
		CommandForm duplicated = CommandForm.of(crud, typed, SOURCES, "duplicate:opt.field/0");
		assertThat(rows(duplicated)).containsExactly("title:text!", "title:text!", "body:longtext");
		CommandForm deleted = CommandForm.of(crud, typed, SOURCES, "delete:opt.field/0");
		assertThat(rows(deleted)).containsExactly("body:longtext");
		CommandForm last = CommandForm.of(crud, values("opt.field.0.name", "title"), SOURCES, "delete:opt.field/0");
		assertThat(rows(last)).as("마지막 행을 지우면 빈 행 하나").containsExactly(":text");

		assertThatThrownBy(() -> CommandForm.of(crud, typed, SOURCES, "delete:opt.field/2")).hasMessageContaining("없는 행");
		assertThatThrownBy(() -> CommandForm.of(crud, typed, SOURCES, "move:opt.field/0")).hasMessageContaining("행 작업은");
		assertThatThrownBy(() -> CommandForm.of(recipe("screen"), Map.of(), SOURCES, "add:opt.nav"))
			.as("한 번만 쓰는 인자는 행을 더하지 않는다")
			.hasMessageContaining("행 작업은");
	}

	/** 행마다 이름:종류(플래그) */
	static List<String> rows(CommandForm form) {
		return form.grids().getFirst().rows().stream().map((row) -> {
			List<CommandForm.Input> cells = row.cells();
			return cells.get(0).value() + ":" + cells.get(1).value() + (cells.get(2).checked() ? "!" : "");
		}).toList();
	}

	@Test
	void aSingleArgumentWithPartsIsAOneRowGrid() {
		CommandForm screen = form("screen", values("arg.모듈", "memo", "arg.화면", "stats", "opt.pattern", "overview", "opt.title", "통계",
				"opt.nav.0.label", "통계", "opt.nav.0.icon", "home"));
		CommandForm.Grid nav = screen.grids().get(1);
		assertThat(nav.identifier()).isEqualTo("--nav");
		assertThat(nav.repeatable()).isFalse();
		assertThat(nav.rows().getFirst().cells()).extracting(CommandForm.Input::control).containsExactly("text", "icon");
		assertThat(screen.args()).containsSubsequence("--title", "통계", "--nav", "통계:home");
		assertThat(screen.fields().get(2).input().groups().getFirst().options()).extracting(CommandForm.Option::text)
			.containsExactly("대시보드 (overview)", "상세 정보 확인 (details)");
		CommandForm noNav = form("screen", values("arg.모듈", "memo", "arg.화면", "stats", "opt.pattern", "overview", "opt.title", "통계"));
		assertThat(noNav.missing()).isEmpty();
		assertThat(noNav.args()).as("빈 선택 그리드는 넘기지 않는다").doesNotContain("--nav");
	}

	@Test
	void valuesOfTheAppAreGroupedByModule() {
		assertThat(SOURCES.get("entities")).as("crud 한 벌만 엔티티").singleElement().satisfies((group) -> {
			assertThat(group.module()).isEqualTo("memo");
			assertThat(group.options()).extracting(CommandForm.Option::value).containsExactly("Memo");
		});
		assertThat(SOURCES.get("dtos")).extracting(CommandForm.Group::label).containsExactly("memo 모듈의 DTO", "activity 모듈의 DTO");
		assertThat(SOURCES.get("dtos").getFirst().options()).extracting(CommandForm.Option::value)
			.containsExactly("MemoItem", "MemoPage", "list:MemoItem", "list:MemoPage");
		assertThat(SOURCES.get("dtos").getFirst().options().get(2).text()).isEqualTo("MemoItem 목록 (list:MemoItem)");
		assertThat(SOURCES.get("app-events")).as("모든 모듈의 이벤트는 모듈을 골라도 거르지 않는다").singleElement().satisfies((group) -> {
			assertThat(group.module()).isEmpty();
			assertThat(group.options()).extracting(CommandForm.Option::value).containsExactly("memo.MemoCreated");
		});
		assertThat(SOURCES.get("finders")).extracting(CommandForm.Group::module).containsExactly("memo", "activity");

		CommandForm dto = form("dto", Map.of());
		List<CommandForm.Group> type = dto.grids().getFirst().rows().getFirst().cells().get(1).groups();
		assertThat(type).extracting(CommandForm.Group::label).containsExactly("종류", "memo 모듈의 DTO", "activity 모듈의 DTO");
		assertThat(type.getFirst().options()).extracting(CommandForm.Option::value).contains("instant");

		CommandForm query = form("query", Map.of());
		assertThat(query.fields()).extracting(CommandForm.Field::identifier).containsExactly("<모듈>", "<이름>", "--returns", "--in");
		assertThat(query.fields().get(2).input().groups()).extracting(CommandForm.Group::label)
			.as("돌려주는 값은 원시 값과 DTO")
			.containsExactly("종류", "memo 모듈의 DTO", "activity 모듈의 DTO");
		assertThat(form("field", Map.of()).fields()).extracting((f) -> f.input().control())
			.as("add field의 모듈과 엔티티는 앱에 있는 것만")
			.containsExactly("select", "select", "text");
		CommandForm.Input ghost = form("field", values("arg.모듈", "memo", "opt.entity", "Ghost")).fields().get(1).input();
		assertThat(ghost.groups().getFirst().options()).as("선택지에 없는 값도 잃지 않는다(명령이 거절한다)")
			.extracting(CommandForm.Option::value)
			.containsExactly("Ghost");
		assertThat(ghost.selected("Ghost")).isTrue();
	}

	@Test
	void withoutTheAppItsValuesAreTypedInstead() {
		Map<String, List<CommandForm.Group>> none = CommandForm.sources(Map.of(), List.of());
		CommandForm crud = CommandForm.of(recipe("crud"), Map.of(), none, null);
		assertThat(crud.fields()).extracting((f) -> f.input().control()).containsExactly("text", "text", "text", "text", "checks");
		assertThat(crud.usesIcons()).isFalse();
	}
}
