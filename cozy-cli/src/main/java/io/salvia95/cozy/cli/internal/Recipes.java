package io.salvia95.cozy.cli.internal;

import io.salvia95.cozy.cli.internal.behavior.BehaviorVocabulary;
import io.salvia95.cozy.cli.internal.recipe.BehaviorCommands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code cozy recipes}: 구조를 만드는 명령 목록. 에이전트는 파일을 만들거나 옮기지 않고 이 명령을 쓴다.
 * 아직 만들지 않은 레시피는 {@code available: false}이고, 실행하면 종료 코드 2로 알린다.
 */
public final class Recipes {

	private Recipes() {
	}

	/**
	 * 레시피 하나.
	 *
	 * @param name {@code cozy add} 뒤의 이름, 또는 {@code link}
	 * @param stage 1단계(새 파일과 의존 선언) 또는 2단계(이미 있는 파일에 덧붙임)
	 * @param usage 명령 형식
	 * @param does 무엇을 만드는지
	 * @param slots 에이전트가 채울 빈칸. 없으면 만든 직후 {@code mvn verify}가 통과한다
	 * @param available 지금 쓸 수 있는가
	 */
	public record Recipe(String name, int stage, String usage, String does, String slots, boolean available) {

		/** 사람에게 보일 제목 ({@code CRUD 화면}). 폼을 만드는 쪽(워크벤치)이 목록에 쓴다 */
		public String title() {
			return TITLES.get(this.name).get(0);
		}

		/** 목록에서 묶는 분류 ({@link #GROUPS} 가운데 하나) */
		public String group() {
			return TITLES.get(this.name).get(1);
		}

		/** 이 레시피의 인자. 사용법({@link #usage()})과 같은 순서다 */
		public List<Param> params() {
			return PARAMS.getOrDefault(this.name, List.of());
		}

		/** {@code cozy} 뒤에 오는 동작: {@code [add, crud]}, {@code [link]}, {@code [enable, events]} */
		public List<String> command() {
			if (this.name.equals("link")) {
				return List.of("link");
			}
			return this.name.startsWith("enable ") ? List.of(this.name.split(" ")) : List.of("add", this.name);
		}

		/** 앱의 cozy-helper 스킬에서 이 레시피의 플레이북 폴더 ({@code references/<이것>/PLAYBOOK.md}) */
		public String playbook() {
			return switch (this.name) {
				case "link" -> "link";
				default -> this.name.startsWith("enable ") ? this.name.replace(' ', '-') : "add-" + this.name;
			};
		}
	}

	/**
	 * 레시피의 인자 하나. 폼을 만드는 쪽(워크벤치)이 읽는다.
	 *
	 * @param name 옵션이면 {@code --} 뒤의 이름, 위치 인자면 자리 이름({@code 모듈})
	 * @param label 사람에게 보일 이름
	 * @param option 옵션({@code --이름 값})인가. 아니면 위치 인자다
	 * @param required 꼭 있어야 하는가
	 * @param repeatable 여러 번 쓸 수 있는가
	 * @param choices 고를 수 있는 값. 자유롭게 적는 값이면 빈 목록
	 * @param example 예시 값
	 * @param choiceLabels 고를 값의 사람이 읽는 이름 (값 → 이름). 없으면 빈 맵
	 * @param from 앱에서 가져오는 선택지({@link #SOURCES} 가운데 하나). 없으면 {@code null}
	 * @param separator 값 하나에 여럿을 고를 때 사이에 넣는 글자({@code ,}). 하나만 고르면 {@code null}
	 * @param parts 값의 구성({@code 이름:종류[!][:라벨]}). 통째로 적는 값이면 빈 목록
	 * @param strict {@code from}의 값만 받는가. 거짓이면 앱의 값은 제안이고 새 값도 받는다({@code add crud}의 모듈은 없으면 만든다)
	 */
	public record Param(String name, String label, boolean option, boolean required, boolean repeatable, List<String> choices,
			String example, Map<String, String> choiceLabels, String from, String separator, List<Part> parts, boolean strict) {

		Param withFrom(String source) {
			return new Param(this.name, this.label, this.option, this.required, this.repeatable, this.choices, this.example,
					this.choiceLabels, source, this.separator, this.parts, true);
		}

		/** 앱의 값을 제안하지만 새 값도 받는다 */
		Param withSuggestions(String source) {
			return new Param(this.name, this.label, this.option, this.required, this.repeatable, this.choices, this.example,
					this.choiceLabels, source, this.separator, this.parts, false);
		}

		Param withLabels(Map<String, String> labels) {
			return new Param(this.name, this.label, this.option, this.required, this.repeatable, this.choices, this.example, labels,
					this.from, this.separator, this.parts, this.strict);
		}

		Param withSeparator(String text) {
			return new Param(this.name, this.label, this.option, this.required, this.repeatable, this.choices, this.example,
					this.choiceLabels, this.from, text, this.parts, this.strict);
		}

		Param withParts(Part... list) {
			return new Param(this.name, this.label, this.option, this.required, this.repeatable, this.choices, this.example,
					this.choiceLabels, this.from, this.separator, List.of(list), this.strict);
		}
	}

	/**
	 * 값의 구성 요소 하나. 값은 구성 요소를 {@code :}로 이은 것이다. 플래그({@code flag})는 앞 요소 바로 뒤에 붙고, 비어 있는 끝의 선택 요소는 빠진다.
	 *
	 * @param name 구성 요소의 이름 ({@code kind})
	 * @param label 사람에게 보일 이름 ({@code 종류})
	 * @param required 꼭 있어야 하는가. 선택 요소는 끝에만 온다
	 * @param choices 고를 수 있는 값. 자유롭게 적는 값이면 빈 목록
	 * @param choiceLabels 고를 값의 사람이 읽는 이름
	 * @param from 앱에서 가져오는 선택지. 없으면 {@code null}
	 * @param flag 켜면 앞 요소 뒤에 붙는 글자({@code !}). 플래그가 아니면 {@code null}
	 * @param example 예시 값. 플래그는 켜면 그 글자, 끄면 빈 글자
	 * @param strict {@code from}의 값만 받는가. 거짓이면 앱의 값과 낱말은 제안이고 다른 글도 받는다(동작의 식)
	 */
	public record Part(String name, String label, boolean required, List<String> choices, Map<String, String> choiceLabels,
			String from, String flag, String example, boolean strict) {

		/** 적는 칸이고 이 출처의 값을 제안한다 */
		Part suggesting(String source) {
			return new Part(this.name, this.label, this.required, this.choices, this.choiceLabels, source, this.flag, this.example, false);
		}
	}

	/**
	 * 선택지의 출처({@code from}). 앱의 값은 {@code cozy describe}에서, 아이콘은 cozy-ui에서 얻는다. "선택한 모듈"은 같은 레시피의 위치 인자
	 * {@code 모듈}이다.
	 */
	public static final Map<String, String> SOURCES = ordered("modules", "앱의 모듈", "entities", "선택한 모듈의 CRUD 엔티티", "events",
			"선택한 모듈의 이벤트", "app-events", "앱의 모든 이벤트 (모듈.이벤트)", "finders", "선택한 모듈의 조회 인터페이스", "facades",
			"선택한 모듈의 명령 인터페이스", "dtos", "선택한 모듈의 DTO와 그 목록 (Record, list:Record)", "icons", "Tabler Icons의 외곽선 아이콘",
			"behavior-types", "상태의 종류 (vocabulary의 type)", "behavior-values", "계산의 함수(vocabulary의 function)와 고치는 컴포넌트의 상태·계산",
			"behavior-steps", "동작의 단계(vocabulary의 step), 이벤트 값(event)과 고치는 컴포넌트의 상태·계산");

	private static Param positional(String name, String label, String example) {
		return new Param(name, label, false, true, false, List.of(), example, Map.of(), null, null, List.of(), false);
	}

	private static Param option(String name, String label, boolean required, String example) {
		return new Param(name, label, true, required, false, List.of(), example, Map.of(), null, null, List.of(), false);
	}

	private static Param choice(String name, String label, boolean required, String... choices) {
		return new Param(name, label, true, required, false, List.of(choices), choices[0], Map.of(), null, null, List.of(), false);
	}

	private static Param choices(String name, String label, boolean required, String example, List<String> choices) {
		return new Param(name, label, true, required, false, choices, example, Map.of(), null, null, List.of(), false);
	}

	private static Param repeated(String name, String label, boolean required, String example) {
		return new Param(name, label, true, required, true, List.of(), example, Map.of(), null, null, List.of(), false);
	}

	private static Part text(String name, String label, boolean required, String example) {
		return new Part(name, label, required, List.of(), Map.of(), null, null, example, true);
	}

	/** 필드나 파라미터의 이름 */
	private static Part nameOf(String example) {
		return text("name", "이름", true, example);
	}

	/** 필드 종류 */
	private static Part kind(List<String> kinds, String example) {
		return new Part("kind", "종류", true, kinds, labels(kinds), null, null, example, true);
	}

	/** 종류나 같은 모듈의 DTO */
	private static Part type(String example) {
		return new Part("type", "타입", true, KINDS, labels(KINDS), "dtos", null, example, true);
	}

	/** {@code !}: 필수 */
	private static Part requiredFlag(boolean example) {
		return new Part("required", "필수", false, List.of(), Map.of(), null, "!", example ? "!" : "", true);
	}

	/** 필드 종류. crud 한 벌은 instant를 쓰지 않는다 */
	private static final List<String> KINDS = List.of("text", "longtext", "int", "long", "bool", "date", "instant");

	private static final List<String> CRUD_KINDS = KINDS.subList(0, KINDS.size() - 1);

	/** 돌려주는 원시 값 */
	private static final List<String> RETURNS = List.of("long", "int", "bool", "text");

	/** 고를 값 → 사람이 읽는 이름 */
	private static final Map<String, String> CHOICE_LABELS = ordered("text", "한 줄 텍스트", "longtext", "여러 줄 텍스트", "int", "정수", "long",
			"64비트 정수", "bool", "참/거짓", "date", "날짜", "instant", "날짜와 시각", "overview", "대시보드", "details", "상세 정보 확인", "created",
			"생성", "updated", "수정", "deleted", "삭제", "insert", "추가", "update", "수정", "delete", "삭제");

	private static Map<String, String> labels(List<String> choices) {
		Map<String, String> labels = new LinkedHashMap<>();
		choices.forEach((c) -> labels.put(c, CHOICE_LABELS.get(c)));
		return Collections.unmodifiableMap(labels);
	}

	private static Map<String, String> ordered(String... keyValues) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return Collections.unmodifiableMap(map);
	}

	private static final Param MODULE = positional("모듈", "모듈", "memo").withFrom("modules");

	/** 없으면 만드는 모듈 (crud, screen) */
	private static final Param NEW_OR_EXISTING_MODULE = positional("모듈", "모듈 (없으면 만든다)", "memo").withSuggestions("modules");

	/** 분류. 목록은 이 순서로 묶는다 */
	public static final List<String> GROUPS = List.of("화면", "모듈", "비즈니스 로직", "이벤트", "데이터베이스", "설정");

	/** 레시피 이름 → 제목, 분류 */
	private static final Map<String, List<String>> TITLES = Map.ofEntries(Map.entry("crud", List.of("CRUD 화면", "화면")),
			Map.entry("screen", List.of("조회 화면", "화면")), Map.entry("field", List.of("필드 추가", "화면")),
			Map.entry("fragment", List.of("공개 프래그먼트", "화면")), Map.entry("behavior", List.of("화면 동작", "화면")),
			Map.entry("module", List.of("모듈", "모듈")),
			Map.entry("link", List.of("모듈 의존성", "모듈")), Map.entry("query", List.of("조회 메서드", "비즈니스 로직")),
			Map.entry("command", List.of("명령 메서드", "비즈니스 로직")), Map.entry("dto", List.of("DTO", "비즈니스 로직")),
			Map.entry("event", List.of("이벤트", "이벤트")), Map.entry("listener", List.of("이벤트 리스너", "이벤트")),
			Map.entry("event-version", List.of("이벤트 버전", "이벤트")), Map.entry("migration", List.of("마이그레이션", "데이터베이스")),
			Map.entry("enable events", List.of("이벤트 기능 활성화", "설정")),
			Map.entry("enable workbench", List.of("워크벤치 활성화", "설정")));

	/** 레시피 이름 → 인자 */
	private static final Map<String, List<Param>> PARAMS = Map.ofEntries(
			Map.entry("module", List.of(positional("모듈", "새 모듈 이름 (소문자)", "voc"), option("label", "라벨", false, "고객의 소리"))),
			Map.entry("link", List.of(positional("모듈", "쓰는 모듈", "dashboard").withFrom("modules"),
					option("uses", "쓰려는 모듈", true, "memo,activity").withFrom("modules").withSeparator(","))),
			Map.entry("crud", List.of(NEW_OR_EXISTING_MODULE, option("entity", "엔티티 이름 (PascalCase)", true, "Memo"),
					option("label", "화면에 보일 이름", true, "메모"),
					option("icon", "내비게이션 아이콘 (Tabler Icons 이름)", true, "notes").withFrom("icons"),
					repeated("field", "필드", true, "title:text!:제목").withParts(nameOf("title"), kind(CRUD_KINDS, "text"),
							requiredFlag(true), text("label", "라벨", false, "제목")),
					choices("publish", "발행할 이벤트", false, "created", List.of("created", "updated", "deleted"))
						.withLabels(labels(List.of("created", "updated", "deleted")))
						.withSeparator(","))),
			Map.entry("screen", List.of(NEW_OR_EXISTING_MODULE, positional("화면", "화면 이름 (소문자)", "index"),
					choice("pattern", "패턴", true, "overview", "details").withLabels(labels(List.of("overview", "details"))),
					option("title", "제목", true, "대시보드"),
					repeated("field", "화면 DTO의 필드", false, "total:long:전체").withParts(nameOf("total"), kind(KINDS, "long"),
							text("label", "라벨", false, "전체")),
					option("nav", "내비게이션", false, "대시보드:dashboard").withParts(text("label", "라벨", true, "대시보드"),
							new Part("icon", "아이콘", true, List.of(), Map.of(), "icons", null, "dashboard", true)))),
			Map.entry("fragment", List.of(MODULE, positional("이름", "fragment 이름 (소문자와 -)", "recent-activity"),
					repeated("param", "파라미터", true, "items:list:ActivityItem").withParts(nameOf("items"), type("list:ActivityItem")),
					option("purpose", "용도 한 줄", true, "최근 활동 목록"))),
			Map.entry("behavior", List.of(MODULE, positional("동작", "동작 이름 (소문자와 -). 컴포넌트는 모듈 이름 + 동작", "body-count"),
					option("purpose", "목적 한 문장 (새 동작에 필요)", false, "본문을 쓰는 동안 글자 수를 보인다"),
					repeated("state", "상태", false, "length:number@data-length").withParts(nameOf("length"),
							text("declaration", "종류[=처음 값][@data-이름]", true, "number@data-length").suggesting("behavior-types")),
					repeated("computed", "계산", false, "label:format('{length}자', 'ko-KR')").withParts(nameOf("label"),
							text("expression", "식", true, "format('{length}자', 'ko-KR')").suggesting("behavior-values")),
					repeated("action", "동작", false, "count:set(length, length(event.value))").withParts(nameOf("count"),
							text("steps", "단계 (;로 잇는다)", true, "set(length, length(event.value))").suggesting("behavior-steps")),
					repeated("listen", "받는 화면 이벤트", false, "count:memo:saved").withParts(text("action", "동작", true, "count"),
							text("event", "화면 이벤트 (모듈:이름)", true, "memo:saved")))),
			Map.entry("event", List.of(MODULE, positional("이벤트", "이벤트 이름 (PascalCase, 과거형)", "ReportSent"),
					repeated("field", "필드", true, "reportId:long").withParts(nameOf("reportId"), kind(KINDS, "long")))),
			Map.entry("listener", List.of(positional("모듈", "받는 모듈", "activity").withFrom("modules"),
					option("on", "받을 이벤트 (보내는 모듈.이벤트)", true, "memo.MemoCreated").withFrom("app-events"),
					option("name", "리스너 이름", false, "MemoCreatedListener"))),
			Map.entry("migration", List.of(MODULE, positional("설명", "설명 (소문자와 _)", "create_activity"))),
			Map.entry("dto", List.of(MODULE, positional("Record", "record 이름 (PascalCase)", "ActivityItem"),
					repeated("field", "필드", true, "message:text:무엇이 있었는지").withParts(nameOf("message"), type("text"),
							text("description", "설명", false, "무엇이 있었는지")))),
			Map.entry("query", List.of(MODULE, positional("이름", "메서드 이름", "recent"),
					repeated("param", "파라미터", false, "limit:int").withParts(nameOf("limit"), kind(KINDS, "int")),
					choices("returns", "돌려주는 값", true, "list:ActivityItem", RETURNS).withLabels(labels(RETURNS)).withFrom("dtos"),
					option("in", "조회 인터페이스 (둘 이상일 때)", false, "ActivityFinder").withFrom("finders"))),
			Map.entry("command", List.of(MODULE, positional("이름", "메서드 이름", "record"),
					repeated("param", "파라미터", false, "message:text").withParts(nameOf("message"), kind(KINDS, "text")),
					choices("returns", "돌려주는 값 (없으면 void)", false, "long", RETURNS).withLabels(labels(RETURNS)),
					choice("sql", "쓰기 SQL 문도 만든다", false, "insert", "update", "delete")
						.withLabels(labels(List.of("insert", "update", "delete"))),
					option("in", "명령 인터페이스 (둘 이상일 때)", false, "ActivityFacade").withFrom("facades"))),
			Map.entry("field", List.of(MODULE, option("entity", "crud의 엔티티", true, "Memo").withFrom("entities"),
					option("field", "더할 필드", true, "dueOn:date:마감일").withParts(nameOf("dueOn"), kind(CRUD_KINDS, "date"),
							requiredFlag(false), text("label", "라벨", false, "마감일")),
					option("default", "필수 필드(!)일 때 이미 있는 행이 가질 값", false, "2026-01-01"))),
			Map.entry("event-version", List.of(MODULE, positional("이벤트", "이벤트 이름", "MemoCreated").withFrom("events"),
					repeated("field", "더할 필드", true, "pinned:bool").withParts(nameOf("pinned"), kind(KINDS, "bool")))));

	/** 레시피가 아닌 명령의 플레이북: 형식과 폴더 */
	public static final Map<String, String> OTHER_PLAYBOOKS = Map.of("describe", "cozy describe [--json]", "check",
			"cozy check [--json]", "sync-agents", "cozy sync agents [--force]", "sync-behaviors", "cozy sync behaviors", "attach", BehaviorCommands.ATTACH_USAGE, "remove-behavior",
			BehaviorCommands.REMOVE_USAGE, "rename-behavior", BehaviorCommands.RENAME_USAGE);

	/** 명령이 아닌 주제의 플레이북 */
	public static final List<String> TOPIC_PLAYBOOKS = List.of("screens", "screen-layout", "screen-states", "inputs", "wording",
			"upgrade");

	public static final List<Recipe> ALL = List.of(
			new Recipe("module", 1, "cozy add module <module> [--label <label>]", "An empty module (package-info.java, allowedDependencies = {})",
					"none", true),
			new Recipe("link", 1, "cozy link <module> --uses <other-module>[,…]", "Adds \"<module> :: spi\" to allowedDependencies in package-info.java",
					"none", true),
			new Recipe("crud", 1,
					"cozy add crud <module> --entity <Entity> --label <label> --icon <icon> --field <name>:<type>[!][:<label>] … [--publish created,updated,deleted]",
					"List, create, details, edit and delete screens for one entity (spi, internal, api, SQL, templates, specs, tests)", "none", true),
			new Recipe("screen", 1,
					"cozy add screen <module> <screen> --pattern overview|details --title <title> [--field …] [--nav <label>:<icon>]",
					"One read-only screen (controller, view DTO, template, spec, navigation). Lists and forms come from crud", "controller view-model", true),
			new Recipe("fragment", 1, "cozy add fragment <module> <name> --param <name>:<type> … --purpose \"<purpose>\"",
					"A fragment that other modules can use, and its manifest entry", "template fragment-body", true),
			new Recipe("behavior", 1,
					"cozy add behavior <module> <behavior> [--purpose <purpose>] [--state <name>:<type>[=<initial>][@data-<name>] …] [--computed <name>:<expression> …] [--action <name>:<step>[; <step>] …] [--listen <action>:<module>:<event> …]",
					"Declares or changes one Alpine component ({module}{Behavior}) for in-page state in behaviors/{module}.yml, and generates static/{module}/{module}.js from it. The layout loads that file",
					"none (the script is generated from the spec)", true),
			new Recipe("event", 1, "cozy add event <module> <event> --field <name>:<type> …", "A CozyEvent record and its serialized sample v1",
					"none (the agent adds the line that publishes the event)", true),
			new Recipe("listener", 1, "cozy add listener <module> --on <sender-module>.<event> [--name <name>]",
					"An event listener that receives its module's {module}Facade (creates an empty Facade and implementation if missing). Adds the link when needed", "listener-body", true),
			new Recipe("migration", 1, "cozy add migration <module> <description>", "V{timestamp}__{module}_{description}.sql", "migration-sql", true),
			new Recipe("dto", 2, "cozy add dto <module> <Record> --field <name>:<type>[:<description>] …",
					"One record in spi/dto for values passed between modules. Types are the crud types plus record and list:record of the same module", "none", true),
			new Recipe("enable events", 2, "cozy enable events", "Adds cozy-starter-events to the pom (needs cozy-starter-sqlite)", "none",
					true),
			new Recipe("enable workbench", 2, "cozy enable workbench",
					"Adds the dev-only cozy-workbench to the pom (needs cozy-ui). A dev run serves /cozy/dev/workbench", "none", true),
			new Recipe("query", 2,
					"cozy add query <module> <name> [--param <name>:<type> …] --returns long|int|bool|text|<Record>|list:<Record> [--in <Finder>]",
					"A Finder method and implementation, a Mapper method, a row record and a <select>. Creates the Finder if missing", "query-sql", true),
			new Recipe("command", 2,
					"cozy add command <module> <name> [--param <name>:<type> …] [--returns long|int|bool|text] [--sql insert|update|delete] [--in <Facade>]",
					"A Facade method and implementation. With --sql, also a Mapper method and the write statement. Creates the Facade if missing", "command-body, and command-sql with --sql", true),
			new Recipe("event-version", 2, "cozy add event-version <module> <event> --field <name>:<type> …",
					"Adds event fields (boxed types), raises schemaVersion, keeps the old of() passing null for the new fields, and adds sample v{n+1}", "none", true),
			new Recipe("field", 2, "cozy add field <module> --entity <Entity> --field <name>:<type>[!][:<label>] [--default <value>]",
					"Adds one field to a crud set: ALTER TABLE migration, domain, DTO and form, Facade and Mapper, SQL, input, spec values, and arguments at call sites", "none",
					true));

	public static Map<String, Object> model() {
		List<Object> recipes = new ArrayList<>();
		for (Recipe r : ALL) {
			List<Object> params = new ArrayList<>();
			for (Param p : r.params()) {
				List<Object> parts = new ArrayList<>();
				for (Part part : p.parts()) {
					parts.add(Describe.ordered("name", part.name(), "label", part.label(), "required", part.required(), "choices",
							new ArrayList<Object>(part.choices()), "choiceLabels", new LinkedHashMap<String, Object>(part.choiceLabels()),
							"from", part.from(), "flag", part.flag(), "example", part.example(), "strict", part.from() != null && part.strict()));
				}
				params.add(Describe.ordered("name", p.name(), "label", p.label(), "option", p.option(), "required", p.required(),
						"repeatable", p.repeatable(), "choices", new ArrayList<Object>(p.choices()), "example", p.example(),
						"choiceLabels", new LinkedHashMap<String, Object>(p.choiceLabels()), "from", p.from(), "strict",
						p.from() != null && p.strict(), "separator", p.separator(), "parts", parts));
			}
			Map<String, Object> recipe = Describe.ordered("name", r.name(), "stage", r.stage(), "title", r.title(), "group", r.group(),
					"usage", r.usage(), "does", r.does(), "slots", r.slots(), "available", r.available(), "command",
					new ArrayList<Object>(r.command()), "params", params);
			if (r.name().equals("behavior")) {
				List<Object> words = new ArrayList<>();
				BehaviorVocabulary.WORDS.forEach((w) -> words.add(Describe.ordered("kind", w.kind(), "name", w.name(), "shape", w.shape(),
						"text", w.text())));
				recipe.put("vocabulary", words);
			}
			recipes.add(recipe);
		}
		return Describe.ordered("recipes", recipes, "groups", new ArrayList<Object>(GROUPS), "sources",
				new LinkedHashMap<String, Object>(SOURCES));
	}

	/** 쓸 수 있는 것을 먼저, 준비 중인 것을 뒤에 둔다. 쓸 수 있는 부분은 {@link #availableText()}와 같다 */
	public static String text() {
		StringBuilder out = new StringBuilder("Change structure with these commands, then fill only the slots they leave (cozy:slot).\n"
				+ "Each command in detail: .agents/skills/cozy-helper/references/<name, such as add-crud>/PLAYBOOK.md\n\n");
		out.append(availableText());
		if (ALL.stream().anyMatch((r) -> !r.available())) {
			out.append("\nNot ready yet (exit code 2)\n");
			ALL.stream().filter((r) -> !r.available()).forEach((r) -> append(out, r));
		}
		return out.toString();
	}

	/** 지금 쓸 수 있는 레시피만 */
	public static String availableText() {
		StringBuilder out = new StringBuilder();
		ALL.stream().filter(Recipe::available).forEach((r) -> append(out, r));
		return out.toString();
	}

	private static void append(StringBuilder out, Recipe r) {
		out.append("  ").append(r.usage()).append(r.available() ? "" : "  (not ready)").append('\n');
		out.append("      ").append(r.does()).append(". Slots: ").append(r.slots()).append('\n');
	}
}
