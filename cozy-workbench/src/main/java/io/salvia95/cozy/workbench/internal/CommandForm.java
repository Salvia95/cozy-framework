package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 코드 생성 폼. 레시피의 인자({@code cozy recipes --json}의 {@code params})를 입력으로 바꾸고, 보낸 값으로 cozy 명령의 인자를 만든다.
 *
 * <ul>
 * <li>통째로 적는 인자는 속성 표의 행 하나다. 글자 칸, 선택 칸(고를 값이나 앱의 값), 여럿 고르기(사이 글자가 있는 인자), 아이콘 칸이다.</li>
 * <li>구성이 있는 인자({@code --field 이름:종류[!][:라벨]})는 그리드다. 열이 구성 요소, 행이 값 하나다. 여러 번 쓰는 인자만 행을 더하고 뺀다.</li>
 * </ul>
 *
 * <p>
 * 폼의 이름: 통째로 적는 인자는 {@link WorkbenchEngine.Param#field()}({@code opt.label}), 그리드의 칸은
 * {@code {field}.{행}.{구성 요소}}({@code opt.field.0.kind})다. 구성 요소를 이을 때는 {@code :}를 넣고, 플래그는 앞 요소에 붙이고, 비어 있는 끝의
 * 선택 요소는 뺀다.
 */
final class CommandForm {

	/** 선택지 하나. text는 화면에 보일 글이다 */
	record Option(String value, String text) {
	}

	/**
	 * 선택지의 묶음.
	 *
	 * @param label 묶음 이름. 하나뿐인 묶음이면 빈 글자
	 * @param module 이 모듈을 골랐을 때만 보인다. 늘 보이면 빈 글자
	 */
	record Group(String label, String module, List<Option> options) {
	}

	/**
	 * 입력 하나.
	 *
	 * @param control {@code text}, {@code select}, {@code suggest}(앱의 값을 제안하는 글자 칸), {@code checks}(여럿 고르기),
	 *     {@code icon}, {@code flag}(체크박스)
	 * @param values 고른 값. 하나만 고르는 입력이면 많아야 하나
	 * @param groups 선택지 ({@code select}, {@code checks}, {@code icon})
	 * @param flag {@code flag}이면 켤 때 보내는 글자
	 * @param label 화면 낭독기가 읽을 이름
	 */
	record Input(String id, String name, String control, List<String> values, List<Group> groups, String flag, String placeholder,
			boolean required, String label) {

		public String value() {
			return this.values.isEmpty() ? "" : this.values.getFirst();
		}

		public boolean selected(String option) {
			return this.values.contains(option);
		}

		public boolean checked() {
			return this.flag != null && this.values.contains(this.flag);
		}

		/** 선택 칸 맨 위의 빈 선택지. 그리드의 칸은 첫 값을 기본으로 고르므로 없다 */
		public String empty() {
			return this.required ? "선택하세요" : "선택 안 함";
		}
	}

	/**
	 * 속성 표의 행.
	 *
	 * @param identifier 명령의 글({@code --entity}, {@code <모듈>})
	 */
	record Field(String label, String identifier, boolean required, Input input) {
	}

	record Column(String label, boolean required) {
	}

	record Row(int number, List<Input> cells) {
	}

	/**
	 * 그리드 하나.
	 *
	 * @param field 칸 이름의 앞부분 ({@code opt.field})
	 */
	record Grid(String field, String label, String identifier, boolean required, boolean repeatable, String example, List<Column> columns,
			List<Row> rows) {

		/** 행 작업 뒤에 돌아올 자리 */
		public String anchor() {
			return "command-" + this.field.replace('.', '-');
		}
	}

	/** 아이콘 선택지의 출처 */
	static final String ICONS = "icons";

	private final WorkbenchEngine.Recipe recipe;

	private final List<Field> fields = new ArrayList<>();

	private final List<Grid> grids = new ArrayList<>();

	private final List<String> missing = new ArrayList<>();

	private final List<String> args = new ArrayList<>();

	private CommandForm(WorkbenchEngine.Recipe recipe) {
		this.recipe = recipe;
	}

	/**
	 * @param recipe 레시피
	 * @param form 보낸 값 (이름 → 값들). 처음 열 때는 빈 맵
	 * @param sources 앱의 선택지 ({@link #sources})
	 * @param row 행 작업 ({@code add:opt.field}, {@code duplicate:opt.field/0}, {@code delete:opt.field/2}). 행 번호는 0부터다. 없으면
	 *     {@code null}
	 * @throws IllegalArgumentException 알 수 없는 행 작업
	 */
	static CommandForm of(WorkbenchEngine.Recipe recipe, Map<String, List<String>> form, Map<String, List<Group>> sources, String row) {
		CommandForm built = new CommandForm(recipe);
		built.args.addAll(recipe.command());
		boolean rowDone = (row == null);
		for (int index = 0; index < recipe.params().size(); index++) {
			WorkbenchEngine.Param param = recipe.params().get(index);
			String id = "command-p" + index;
			if (param.parts().isEmpty()) {
				built.field(param, id, form.getOrDefault(param.field(), List.of()), sources);
			}
			else {
				rowDone |= built.grid(param, id, form, sources, row);
			}
		}
		if (!rowDone) {
			throw new IllegalArgumentException("[cozy] 행 작업은 add:{그리드}, duplicate:{그리드}/{행}, delete:{그리드}/{행}입니다: " + row);
		}
		return built;
	}

	public List<Field> fields() {
		return List.copyOf(this.fields);
	}

	public List<Grid> grids() {
		return List.copyOf(this.grids);
	}

	/** 비어 있는 필수 항목 (사람이 읽는 이름) */
	public List<String> missing() {
		return List.copyOf(this.missing);
	}

	/** {@code cozy} 뒤의 인자. {@link #missing()}가 비어 있을 때만 쓴다 */
	public List<String> args() {
		return List.copyOf(this.args);
	}

	/** 아이콘 칸이 있는가 (아이콘 이름 목록을 한 번만 싣는다) */
	public boolean usesIcons() {
		return this.fields.stream().anyMatch((f) -> f.input().control().equals("icon")) || this.grids.stream()
			.flatMap((g) -> g.rows().stream())
			.flatMap((r) -> r.cells().stream())
			.anyMatch((c) -> c.control().equals("icon"));
	}

	public WorkbenchEngine.Recipe recipe() {
		return this.recipe;
	}

	// ── 통째로 적는 인자 ──────────────────────────────────────

	private void field(WorkbenchEngine.Param param, String id, List<String> given, Map<String, List<Group>> sources) {
		List<Group> groups = groups(param.choices(), param.choiceLabels(), param.from(), sources);
		String control = control(param.from(), groups);
		if (param.separator() != null && !control.equals("text")) {
			control = "checks";
		}
		else if (control.equals("select") && param.from() != null && !param.strict()) {
			control = "suggest";
		}
		List<String> values = new ArrayList<>();
		for (String value : given) {
			if (!value.strip().isEmpty()) {
				values.add(value.strip());
			}
		}
		if (!control.equals("checks") && values.size() > 1) {
			values = new ArrayList<>(List.of(String.join(" ", values)));
		}
		if (control.equals("select")) {
			groups = keeping(groups, values);
		}
		String identifier = param.option() ? "--" + param.name() : "<" + param.name() + ">";
		this.fields.add(new Field(param.label(), identifier, param.required(), new Input(id, param.field(), control, List.copyOf(values),
				groups, null, param.example().isEmpty() ? "" : "예: " + param.example(), param.required(), param.label())));

		String value;
		if (control.equals("checks")) {
			List<String> chosen = new ArrayList<>();
			for (Group group : groups) {
				for (Option option : group.options()) {
					if (values.contains(option.value()) && !chosen.contains(option.value())) {
						chosen.add(option.value());
					}
				}
			}
			value = String.join(param.separator(), chosen);
		}
		else {
			value = values.isEmpty() ? "" : values.getFirst();
		}
		if (value.isEmpty()) {
			if (param.required()) {
				this.missing.add(param.label());
			}
			return;
		}
		if (param.option()) {
			this.args.add("--" + param.name());
		}
		this.args.add(value);
	}

	// ── 구성이 있는 인자 ──────────────────────────────────────

	/** @return 행 작업이 이 그리드의 것이었는가 */
	private boolean grid(WorkbenchEngine.Param param, String id, Map<String, List<String>> form, Map<String, List<Group>> sources,
			String row) {
		String field = param.field();
		// 보낸 행: 번호 순서대로 (중간 번호가 비어도 된다)
		Pattern cell = Pattern.compile(Pattern.quote(field) + "\\.(\\d{1,4})\\.([A-Za-z]+)");
		TreeMap<Integer, Map<String, String>> sent = new TreeMap<>();
		form.forEach((name, list) -> {
			Matcher m = cell.matcher(name);
			if (m.matches() && !list.isEmpty()) {
				sent.computeIfAbsent(Integer.parseInt(m.group(1)), (i) -> new LinkedHashMap<>()).put(m.group(2), list.getLast());
			}
		});
		List<Map<String, String>> rows = new ArrayList<>(sent.values());
		boolean mine = false;
		if (row != null && param.repeatable()) {
			if (row.equals("add:" + field)) {
				rows.add(Map.of());
				mine = true;
			}
			else if (row.startsWith("delete:" + field + "/") || row.startsWith("duplicate:" + field + "/")) {
				String number = row.substring(row.indexOf(':') + 1 + field.length() + 1);
				if (!number.matches("\\d{1,4}") || Integer.parseInt(number) >= rows.size()) {
					throw new IllegalArgumentException("[cozy] 없는 행입니다: " + row);
				}
				int at = Integer.parseInt(number);
				if (row.startsWith("delete:")) {
					rows.remove(at);
				}
				else {
					rows.add(at + 1, rows.get(at));
				}
				mine = true;
			}
		}
		if (rows.isEmpty()) {
			rows.add(Map.of());
		}
		if (!param.repeatable()) {
			rows = new ArrayList<>(rows.subList(0, 1));
		}

		List<Column> columns = param.parts().stream().map((p) -> new Column(p.label(), p.required())).toList();
		List<Row> built = new ArrayList<>();
		List<String> values = new ArrayList<>();
		int filled = 0;
		for (int r = 0; r < rows.size(); r++) {
			Map<String, String> sentRow = rows.get(r);
			List<Input> cells = new ArrayList<>();
			for (WorkbenchEngine.Part part : param.parts()) {
				List<Group> groups = groups(part.choices(), part.choiceLabels(), part.from(), sources);
				String control = (part.flag() != null) ? "flag" : control(part.from(), groups);
				String value = sentRow.getOrDefault(part.name(), "").strip();
				if (value.isEmpty() && control.equals("select") && !sentRow.containsKey(part.name())) {
					value = first(groups);
				}
				if (control.equals("select")) {
					groups = keeping(groups, value.isEmpty() ? List.of() : List.of(value));
				}
				String spoken = param.label() + " " + (r + 1) + "행 " + part.label();
				cells.add(new Input(id + "-" + r + "-" + part.name(), field + "." + r + "." + part.name(), control,
						value.isEmpty() ? List.of() : List.of(value), groups, part.flag(),
						part.example().isEmpty() || part.flag() != null ? "" : "예: " + part.example(), part.required(), spoken));
			}
			built.add(new Row(r + 1, List.copyOf(cells)));
			if (!blank(cells)) {
				filled++;
				String value = value(param, r + 1, cells);
				if (value != null) {
					values.add(value);
				}
			}
		}
		this.grids.add(new Grid(field, param.label(), "--" + param.name(), param.required(), param.repeatable(), param.example(),
				columns, List.copyOf(built)));
		if (filled == 0 && param.required()) {
			this.missing.add(param.label());
		}
		for (String value : values) {
			this.args.add("--" + param.name());
			this.args.add(value);
		}
		return mine;
	}

	/** 글을 적는 칸이 모두 비면 빈 행이다. 선택 칸과 체크박스는 기본값이 있어 보지 않는다 */
	private static boolean blank(List<Input> cells) {
		return cells.stream()
			.filter((cell) -> cell.control().equals("text") || cell.control().equals("icon"))
			.allMatch((cell) -> cell.value().isEmpty());
	}

	/** 행 하나의 값. 필수 요소가 비면 {@link #missing}에 적고 {@code null}이다 */
	private String value(WorkbenchEngine.Param param, int number, List<Input> cells) {
		StringBuilder joined = new StringBuilder();
		String pending = "";
		boolean complete = true;
		for (int i = 0; i < cells.size(); i++) {
			WorkbenchEngine.Part part = param.parts().get(i);
			Input cell = cells.get(i);
			if (part.flag() != null) {
				joined.append(cell.checked() ? part.flag() : "");
				continue;
			}
			if (cell.value().isEmpty() && part.required()) {
				this.missing.add(param.label() + " " + number + "행의 " + part.label());
				complete = false;
			}
			pending += ((i == 0) ? "" : ":") + cell.value();
			if (!cell.value().isEmpty()) {
				joined.append(pending);
				pending = "";
			}
		}
		return complete ? joined.toString() : null;
	}

	// ── 선택지 ─────────────────────────────────────────────

	private static String control(String from, List<Group> groups) {
		boolean any = groups.stream().anyMatch((g) -> !g.options().isEmpty());
		if (ICONS.equals(from)) {
			return any ? "icon" : "text";
		}
		return any ? "select" : "text";
	}

	/** 보낸 값이 선택지에 없으면(지운 모듈 등) 맨 앞에 두어 잃지 않는다. 명령이 그 값을 거절한다 */
	private static List<Group> keeping(List<Group> groups, List<String> values) {
		List<Option> unknown = new ArrayList<>();
		for (String value : values) {
			boolean known = groups.stream().flatMap((g) -> g.options().stream()).anyMatch((o) -> o.value().equals(value));
			if (!known) {
				unknown.add(new Option(value, value));
			}
		}
		if (unknown.isEmpty()) {
			return groups;
		}
		List<Group> kept = new ArrayList<>();
		kept.add(new Group("", "", List.copyOf(unknown)));
		kept.addAll(groups);
		return List.copyOf(kept);
	}

	private static String first(List<Group> groups) {
		for (Group group : groups) {
			if (!group.options().isEmpty()) {
				return group.options().getFirst().value();
			}
		}
		return "";
	}

	/** 고를 값(이름이 있으면 "이름 (값)")과 앱의 선택지 */
	private static List<Group> groups(List<String> choices, Map<String, String> labels, String from, Map<String, List<Group>> sources) {
		List<Group> groups = new ArrayList<>();
		if (!choices.isEmpty()) {
			List<Option> options = new ArrayList<>();
			for (String choice : choices) {
				String label = labels.get(choice);
				options.add(new Option(choice, (label == null || label.isBlank()) ? choice : label + " (" + choice + ")"));
			}
			groups.add(new Group((from == null) ? "" : "종류", "", List.copyOf(options)));
		}
		if (from != null) {
			groups.addAll(sources.getOrDefault(from, List.of()));
		}
		return List.copyOf(groups);
	}

	/**
	 * 앱의 선택지 ({@code cozy recipes --json}의 {@code sources}). 모듈에 딸린 값은 모듈마다 묶음 하나다.
	 *
	 * @param describe {@code cozy describe --json}. 앱을 읽지 못했으면 빈 맵
	 * @param icons 아이콘 이름
	 */
	@SuppressWarnings("unchecked")
	static Map<String, List<Group>> sources(Map<String, Object> describe, Collection<String> icons) {
		Map<String, List<Group>> sources = new LinkedHashMap<>();
		List<Option> modules = new ArrayList<>();
		for (String key : List.of("entities", "events", "app-events", "finders", "facades", "dtos")) {
			sources.put(key, new ArrayList<>());
		}
		if (describe.get("modules") instanceof List<?> list) {
			for (Object item : list) {
				if (!(item instanceof Map<?, ?> raw)) {
					continue;
				}
				Map<String, Object> m = (Map<String, Object>) raw;
				String module = String.valueOf(m.get("name"));
				modules.add(new Option(module, module));
				List<String> facades = types(m.get("facades"));
				List<String> finders = types(m.get("finders"));
				List<String> dtos = types(m.get("dtos"));
				List<String> events = types(m.get("events"));
				List<String> controllers = strings(m.get("controllers"));
				List<Option> entities = new ArrayList<>();
				// crud 한 벌: {E}Facade, {E}Finder, DTO {E}Item, {E}Controller
				for (String facade : facades) {
					String entity = facade.endsWith("Facade") ? facade.substring(0, facade.length() - "Facade".length()) : "";
					if (!entity.isEmpty() && finders.contains(entity + "Finder") && dtos.contains(entity + "Item")
							&& controllers.contains(entity + "Controller")) {
						entities.add(new Option(entity, entity));
					}
				}
				List<Option> dtoOptions = new ArrayList<>();
				dtos.forEach((dto) -> dtoOptions.add(new Option(dto, dto)));
				dtos.forEach((dto) -> dtoOptions.add(new Option("list:" + dto, dto + " 목록 (list:" + dto + ")")));
				add(sources, "entities", module, module, entities);
				add(sources, "events", module, module, options(events));
				add(sources, "app-events", module, "", events.stream().map((e) -> new Option(module + "." + e, module + "." + e)).toList());
				add(sources, "finders", module, module, options(finders));
				add(sources, "facades", module, module, options(facades));
				add(sources, "dtos", module + " 모듈의 DTO", module, dtoOptions);
			}
		}
		sources.put("modules", modules.isEmpty() ? List.of() : List.of(new Group("", "", List.copyOf(modules))));
		sources.put(ICONS, icons.isEmpty() ? List.of() : List.of(new Group("", "", icons.stream().sorted().map((i) -> new Option(i, i)).toList())));
		sources.replaceAll((key, groups) -> List.copyOf(groups));
		return sources;
	}

	private static void add(Map<String, List<Group>> sources, String key, String label, String module, List<Option> options) {
		if (!options.isEmpty()) {
			sources.get(key).add(new Group(label, module, List.copyOf(options)));
		}
	}

	private static List<Option> options(List<String> names) {
		return names.stream().map((n) -> new Option(n, n)).toList();
	}

	/** {@code [{type: ...}]}의 type들 */
	private static List<String> types(Object list) {
		List<String> types = new ArrayList<>();
		if (list instanceof List<?> items) {
			for (Object item : items) {
				if (item instanceof Map<?, ?> m && m.get("type") != null) {
					types.add(String.valueOf(m.get("type")));
				}
			}
		}
		return types;
	}

	private static List<String> strings(Object list) {
		List<String> values = new ArrayList<>();
		if (list instanceof List<?> items) {
			items.forEach((i) -> values.add(String.valueOf(i)));
		}
		return values;
	}
}
