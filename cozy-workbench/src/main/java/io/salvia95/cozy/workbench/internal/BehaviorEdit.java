package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 컴포넌트를 고치는 폼. "화면 동작"({@code cozy add behavior}) 폼을 컴포넌트의 지금 항목으로 채우고, 보낸 폼을 지금 항목과 견주어 cozy 명령으로 바꾼다.
 *
 * <ul>
 * <li>바꾸거나 더한 행: {@code cozy add behavior <모듈> <동작> --state 이름:종류 …}. 바뀌지 않은 행은 넘기지 않는다.</li>
 * <li>지운 행(지금 있는데 폼에 없는 이름): {@code cozy remove behavior <모듈> <동작> --state 이름 …}.</li>
 * <li>두 명령은 한 계획이다({@link WorkbenchEngine#executeTogether}). 더하기가 먼저여서 지울 항목을 읽던 계산을 같은 폼에서 고칠 수 있다. 지운 이름을 다른
 * 칸에 다시 쓰면(상태 → 계산) 지우기가 먼저다.</li>
 * </ul>
 * 폼의 이름은 {@link CommandForm}과 같다: {@code arg.모듈}, {@code arg.동작}, {@code opt.purpose}, {@code opt.state.0.name} 등.
 */
final class BehaviorEdit {

	/** 그리드 → 구성 요소: 이름 칸, 값 칸 */
	private static final Map<String, List<String>> GRIDS = Map.of("state", List.of("name", "declaration"), "computed",
			List.of("name", "expression"), "action", List.of("name", "steps"), "listen", List.of("event", "action"));

	private static final List<String> ORDER = List.of("state", "computed", "action", "listen");

	private BehaviorEdit() {
	}

	/** 동작 이름: 컴포넌트 이름에서 모듈을 뺀 카멜 표기를 소문자와 -로 ({@code memoBodyCount} → {@code body-count}) */
	static String action(String module, String component) {
		String key = component.substring(module.length());
		StringBuilder kebab = new StringBuilder();
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (Character.isUpperCase(c)) {
				if (i > 0) {
					kebab.append('-');
				}
				kebab.append(Character.toLowerCase(c));
			}
			else {
				kebab.append(c);
			}
		}
		return kebab.toString();
	}

	/** 컴포넌트의 지금 항목으로 채운 폼 */
	static Map<String, List<String>> values(WorkbenchEngine.Behavior behavior) {
		Map<String, List<String>> values = new LinkedHashMap<>();
		values.put("arg.모듈", List.of(behavior.module()));
		values.put("arg.동작", List.of(action(behavior.module(), behavior.name())));
		values.put("opt.purpose", List.of(behavior.purpose()));
		for (String grid : ORDER) {
			List<WorkbenchEngine.Entry> entries = entries(behavior, grid);
			List<String> parts = GRIDS.get(grid);
			for (int i = 0; i < entries.size(); i++) {
				WorkbenchEngine.Entry e = entries.get(i);
				// 받는 화면 이벤트는 폼에서 "동작:이벤트" 순서다 (cozy add behavior --listen 동작:모듈:이벤트)
				boolean listen = grid.equals("listen");
				values.put("opt." + grid + "." + i + "." + (listen ? "action" : parts.get(0)), List.of(listen ? e.text() : e.name()));
				values.put("opt." + grid + "." + i + "." + (listen ? "event" : parts.get(1)), List.of(listen ? e.name() : e.text()));
			}
		}
		return values;
	}

	/**
	 * 보낸 폼을 지금 항목과 견준 명령. 바뀐 것이 없으면 빈 목록이다.
	 *
	 * @return 명령마다 {@code cozy} 뒤의 인자, 실행할 순서대로
	 */
	static List<List<String>> commands(WorkbenchEngine.Behavior behavior, Map<String, List<String>> form) {
		String module = behavior.module();
		String action = action(module, behavior.name());
		List<String> add = new ArrayList<>(List.of("add", "behavior", module, action));
		List<String> remove = new ArrayList<>(List.of("remove", "behavior", module, action));
		String purpose = first(form, "opt.purpose");
		if (!purpose.isEmpty() && !purpose.equals(behavior.purpose())) {
			add.addAll(List.of("--purpose", purpose));
		}
		Set<String> added = new LinkedHashSet<>();
		Set<String> removed = new LinkedHashSet<>();
		for (String grid : ORDER) {
			Map<String, String> now = new LinkedHashMap<>();
			for (WorkbenchEngine.Entry e : entries(behavior, grid)) {
				now.put(e.name(), e.text());
			}
			Map<String, String> sent = rows(form, grid);
			sent.forEach((name, value) -> {
				if (!value.equals(now.get(name))) {
					add.addAll(List.of("--" + grid, grid.equals("listen") ? value + ":" + name : name + ":" + value));
					added.add(name);
				}
			});
			for (String name : now.keySet()) {
				if (!sent.containsKey(name)) {
					remove.addAll(List.of("--" + grid, name));
					removed.add(name);
				}
			}
		}
		List<List<String>> commands = new ArrayList<>();
		boolean adds = add.size() > 4;
		boolean removes = remove.size() > 4;
		boolean moved = added.stream().anyMatch(removed::contains);
		if (removes && (moved || !adds)) {
			commands.add(List.copyOf(remove));
		}
		if (adds) {
			commands.add(List.copyOf(add));
		}
		if (removes && adds && !moved) {
			commands.add(List.copyOf(remove));
		}
		return List.copyOf(commands);
	}

	/** 그리드의 행: 이름 → 값 (받는 화면 이벤트는 이벤트 → 동작). 두 칸이 모두 빈 행은 빈 행이다 */
	private static Map<String, String> rows(Map<String, List<String>> form, String grid) {
		Pattern cell = Pattern.compile("opt\\." + grid + "\\.(\\d{1,4})\\.([A-Za-z]+)");
		TreeMap<Integer, Map<String, String>> sent = new TreeMap<>();
		form.forEach((name, list) -> {
			Matcher m = cell.matcher(name);
			if (m.matches() && !list.isEmpty()) {
				sent.computeIfAbsent(Integer.parseInt(m.group(1)), (i) -> new LinkedHashMap<>()).put(m.group(2), list.getLast().strip());
			}
		});
		List<String> parts = GRIDS.get(grid);
		Map<String, String> rows = new LinkedHashMap<>();
		for (Map<String, String> row : sent.values()) {
			String key = row.getOrDefault(parts.get(0), "");
			String value = row.getOrDefault(parts.get(1), "");
			if (!key.isEmpty() || !value.isEmpty()) {
				rows.put(key, value);
			}
		}
		return rows;
	}

	private static List<WorkbenchEngine.Entry> entries(WorkbenchEngine.Behavior behavior, String grid) {
		return switch (grid) {
			case "state" -> behavior.state();
			case "computed" -> behavior.computed();
			case "action" -> behavior.actions();
			default -> behavior.listens();
		};
	}

	private static String first(Map<String, List<String>> form, String name) {
		List<String> values = form.getOrDefault(name, List.of());
		return values.isEmpty() ? "" : values.getFirst().strip();
	}
}
