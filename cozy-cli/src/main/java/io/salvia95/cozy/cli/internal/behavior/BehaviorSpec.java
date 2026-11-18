package io.salvia95.cozy.cli.internal.behavior;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 모듈 하나의 동작 명세 {@code behaviors/{모듈}.yml}. 컴포넌트마다 목적, 상태, 계산, 동작, 받는 이벤트를 적는다. 읽을 때는 모르는 키와 형식이
 * 틀린 값을 실패로 닫고, 쓸 때는 같은 내용을 같은 글자로 쓴다({@link #write()}).
 *
 * <pre>
 * components:
 *   bodyCount:
 *     purpose: "본문을 쓰는 동안 글자 수와 한도를 보인다"
 *     state:
 *       length: "number@data-length"
 *     computed:
 *       label: "format('{length}자', 'ko-KR')"
 *     actions:
 *       count: "within('textarea', set(length, length(event.value)))"
 *     listens:
 *       "memo:saved": "count"
 * </pre>
 */
public record BehaviorSpec(String module, Map<String, Component> components) {

	/** 컴포넌트 이름(모듈 이름 뒤), 상태·계산·동작의 이름 */
	public static final Pattern NAME = Pattern.compile("[a-z][A-Za-z0-9]*");

	/** 화면 이벤트의 이름: {@code 모듈:이름} */
	public static final Pattern EVENT = Pattern.compile("[a-z][a-z0-9]*:[a-z][a-z0-9]*(-[a-z0-9]+)*");

	/** 상태의 {@code from}: {@code data-이름} */
	private static final Pattern DATA = Pattern.compile("data-[a-z][a-z0-9]*(-[a-z0-9]+)*");

	private static final Set<String> COMPONENT_KEYS = Set.of("purpose", "state", "computed", "actions", "listens");

	/** 상태의 종류 */
	public enum Type {

		BOOL("bool"), NUMBER("number"), TEXT("text"), LIST("list"),

		/** 이벤트의 값처럼 미리 알 수 없는 것. 상태에는 쓰지 않는다 */
		ANY("any");

		private final String word;

		Type(String word) {
			this.word = word;
		}

		public String word() {
			return this.word;
		}

		static Type of(String word) {
			for (Type type : values()) {
				if (type != ANY && type.word.equals(word)) {
					return type;
				}
			}
			return null;
		}
	}

	/**
	 * 상태 하나: {@code 종류[=처음 값][@data-이름]}.
	 *
	 * @param initial 정리한 처음 값. 적지 않았으면 {@code null}(종류의 빈 값)
	 * @param data {@code x-data} 요소에서 읽을 속성({@code data-limit}). 없으면 {@code null}
	 */
	public record State(Type type, String initial, String data) {

		/** {@code number=0@data-limit} */
		public String print() {
			return this.type.word() + ((this.initial != null) ? "=" + this.initial : "")
					+ ((this.data != null) ? "@" + this.data : "");
		}

		/** 처음 값의 목록 (list) */
		public List<String> items() {
			return (this.initial == null || this.initial.isEmpty()) ? List.of() : List.of(this.initial.split(","));
		}

		public static State parse(String source, String where) {
			String rest = source.strip();
			String data = null;
			int at = rest.lastIndexOf("@data-");
			if (at >= 0) {
				data = rest.substring(at + 1);
				rest = rest.substring(0, at);
				if (!DATA.matcher(data).matches()) {
					throw new CliException(where + ": 읽을 속성은 data-소문자-하이픈 모양입니다 (예: @data-limit): " + data);
				}
			}
			String initial = null;
			int eq = rest.indexOf('=');
			if (eq >= 0) {
				initial = rest.substring(eq + 1);
				rest = rest.substring(0, eq);
			}
			Type type = Type.of(rest.strip());
			if (type == null) {
				throw new CliException(where + ": 모르는 종류입니다: " + rest.strip() + "."
						+ io.salvia95.cozy.rules.Closest.hint(rest.strip(), BehaviorVocabulary.names(BehaviorVocabulary.TYPE))
						+ " bool, number, text, list를 씁니다 (예: length:number=0@data-length)");
			}
			return new State(type, (initial != null) ? initial(type, initial, where) : null, data);
		}

		private static String initial(Type type, String value, String where) {
			return switch (type) {
				case BOOL -> {
					if (!value.equals("true") && !value.equals("false")) {
						throw new CliException(where + ": bool의 처음 값은 true나 false입니다: " + value);
					}
					yield value;
				}
				case NUMBER -> {
					if (!value.matches("-?[0-9]+(\\.[0-9]+)?")) {
						throw new CliException(where + ": number의 처음 값은 숫자입니다: " + value);
					}
					yield Expr.canonicalNumber(value);
				}
				case TEXT -> {
					if (value.contains("@") || value.contains("\n")) {
						throw new CliException(where + ": text의 처음 값에는 @와 줄바꿈을 쓰지 않습니다: " + value);
					}
					yield value;
				}
				case LIST -> {
					for (String item : value.split(",", -1)) {
						if (value.isEmpty()) {
							break;
						}
						if (item.isEmpty() || !item.matches("[^\\s,@]+")) {
							throw new CliException(where + ": list의 처음 값은 빈칸 없는 값을 쉼표로 잇습니다 (예: ids:list=a,b): " + value);
						}
					}
					yield value;
				}
				case ANY -> throw new IllegalStateException();
			};
		}
	}

	/**
	 * 컴포넌트 하나.
	 *
	 * @param key 명세의 이름 ({@code bodyCount}). 등록 이름은 모듈 이름 + 이 이름({@code memoBodyCount})
	 * @param actions 동작 → 차례로 할 단계
	 * @param listens 화면 이벤트 → 동작
	 */
	public record Component(String key, String purpose, Map<String, State> state, Map<String, Expr> computed,
			Map<String, List<Expr>> actions, Map<String, String> listens) {

		public Component {
			state = new LinkedHashMap<>(state);
			computed = new LinkedHashMap<>(computed);
			actions = new LinkedHashMap<>(actions);
			listens = new LinkedHashMap<>(listens);
		}

		public static Component empty(String key, String purpose) {
			return new Component(key, purpose, Map.of(), Map.of(), Map.of(), Map.of());
		}

		/** 등록 이름 */
		public String name(String module) {
			return module + Character.toUpperCase(this.key.charAt(0)) + this.key.substring(1);
		}

		/** 이 이름을 쓰는 곳 (상태, 계산, 동작). 없으면 {@code null} */
		public String sectionOf(String name) {
			if (this.state.containsKey(name)) {
				return "상태";
			}
			if (this.computed.containsKey(name)) {
				return "계산";
			}
			return this.actions.containsKey(name) ? "동작" : null;
		}
	}

	public static BehaviorSpec empty(String module) {
		return new BehaviorSpec(module, new LinkedHashMap<>());
	}

	/**
	 * 명세 파일을 읽는다. 식은 문법만 본다. 이름과 종류는 {@link BehaviorScript#check}가 본다.
	 *
	 * @param file 메시지에 보일 파일 ({@code src/main/resources/behaviors/memo.yml})
	 */
	public static BehaviorSpec read(String module, String text, String file) {
		Object root;
		try {
			LoaderOptions options = new LoaderOptions();
			options.setAllowDuplicateKeys(false);
			root = new Yaml(new SafeConstructor(options)).load(text);
		}
		catch (YAMLException ex) {
			throw new CliException(file + "을 YAML로 읽을 수 없습니다: " + ex.getMessage().lines().findFirst().orElse(""));
		}
		Map<String, Component> components = new LinkedHashMap<>();
		if (root == null) {
			return new BehaviorSpec(module, components);
		}
		Map<?, ?> top = map(root, file, "파일");
		for (Object key : top.keySet()) {
			if (!"components".equals(key)) {
				throw new CliException(file + ": 맨 위에는 components만 둡니다 (모르는 키: " + key + ")");
			}
		}
		Object list = top.get("components");
		if (list == null) {
			return new BehaviorSpec(module, components);
		}
		for (Map.Entry<?, ?> entry : map(list, file, "components").entrySet()) {
			String key = String.valueOf(entry.getKey());
			String where = file + " 컴포넌트 " + key;
			if (!NAME.matcher(key).matches()) {
				throw new CliException(where + ": 이름은 영문 소문자로 시작하는 카멜 표기입니다 (예: bodyCount)");
			}
			Map<?, ?> body = map(entry.getValue(), file, "컴포넌트 " + key);
			for (Object k : body.keySet()) {
				if (!COMPONENT_KEYS.contains(String.valueOf(k))) {
					throw new CliException(where + ": 모르는 키 " + k + ". purpose, state, computed, actions, listens를 씁니다");
				}
			}
			String purpose = text(body.get("purpose"), where + " purpose");
			if (purpose == null || purpose.isBlank()) {
				throw new CliException(where + ": purpose(목적 한 문장)가 없습니다");
			}
			Map<String, State> state = new LinkedHashMap<>();
			entries(body.get("state"), where, "state")
				.forEach((name, value) -> state.put(name, State.parse(value, where + " 상태 " + name)));
			Map<String, Expr> computed = new LinkedHashMap<>();
			entries(body.get("computed"), where, "computed")
				.forEach((name, value) -> computed.put(name, Expr.parse(value, where + " 계산 " + name)));
			Map<String, List<Expr>> actions = new LinkedHashMap<>();
			entries(body.get("actions"), where, "actions")
				.forEach((name, value) -> actions.put(name, Expr.parseSteps(value, where + " 동작 " + name)));
			Map<String, String> listens = new LinkedHashMap<>();
			Object listenMap = body.get("listens");
			if (listenMap != null) {
				for (Map.Entry<?, ?> e : map(listenMap, file, "listens").entrySet()) {
					listens.put(String.valueOf(e.getKey()), text(e.getValue(), where + " listens " + e.getKey()));
				}
			}
			components.put(key, new Component(key, purpose.strip(), state, computed, actions, listens));
		}
		return new BehaviorSpec(module, components);
	}

	/** 같은 내용은 같은 글자로 */
	public String write() {
		StringBuilder out = new StringBuilder();
		out.append("# ").append(this.module).append(" 모듈의 화면 안 동작(동작 명세). cozy add behavior가 고치고, 이 명세에서 ")
			.append("static/").append(this.module).append('/').append(this.module).append(".js를 만든다\n");
		out.append("components:");
		if (this.components.isEmpty()) {
			return out.append(" {}\n").toString();
		}
		out.append('\n');
		for (Component c : this.components.values()) {
			out.append("  ").append(c.key()).append(":\n");
			out.append("    purpose: ").append(quote(c.purpose())).append('\n');
			section(out, "state", c.state(), State::print);
			section(out, "computed", c.computed(), Expr::print);
			section(out, "actions", c.actions(), Expr::print);
			section(out, "listens", c.listens(), (v) -> v);
		}
		return out.toString();
	}

	private static <T> void section(StringBuilder out, String name, Map<String, T> entries,
			java.util.function.Function<T, String> print) {
		if (entries.isEmpty()) {
			return;
		}
		out.append("    ").append(name).append(":\n");
		entries.forEach((key, value) -> out.append("      ")
			.append(NAME.matcher(key).matches() ? key : quote(key))
			.append(": ")
			.append(quote(print.apply(value)))
			.append('\n'));
	}

	/** YAML의 큰따옴표 글자 */
	static String quote(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\t' -> out.append("\\t");
				default -> out.append(c);
			}
		}
		return out.append('"').toString();
	}

	private static Map<?, ?> map(Object value, String file, String what) {
		if (!(value instanceof Map<?, ?> map)) {
			throw new CliException(file + ": 키와 값의 묶음이어야 합니다: " + what);
		}
		return map;
	}

	private static String text(Object value, String where) {
		if (value == null) {
			return null;
		}
		if (!(value instanceof String text)) {
			throw new CliException(where + ": 값은 글자로 적습니다 (큰따옴표로 감쌉니다): " + value);
		}
		return text;
	}

	private static Map<String, String> entries(Object value, String where, String section) {
		Map<String, String> result = new LinkedHashMap<>();
		if (value == null) {
			return result;
		}
		if (!(value instanceof Map<?, ?> map)) {
			throw new CliException(where + ": 이름과 값의 묶음이어야 합니다: " + section);
		}
		for (Map.Entry<?, ?> e : map.entrySet()) {
			String name = String.valueOf(e.getKey());
			if (!NAME.matcher(name).matches()) {
				throw new CliException(where + " " + section + ": 이름은 영문 소문자로 시작하는 카멜 표기입니다: " + name);
			}
			String text = text(e.getValue(), where + " " + section + " " + name);
			if (text == null || text.isBlank()) {
				throw new CliException(where + " " + section + " " + name + ": 값이 없습니다");
			}
			result.put(name, text);
		}
		return result;
	}

	/** {@code 이름:값}이나 {@code 이름=값}을 나눈다 */
	public static List<String> split(String source, char separator, String option, String example) {
		int at = source.indexOf(separator);
		if (at <= 0 || at == source.length() - 1) {
			throw new CliException(option + "는 " + example + " 모양입니다: " + source);
		}
		List<String> parts = new ArrayList<>();
		parts.add(source.substring(0, at).strip());
		parts.add(source.substring(at + 1).strip());
		return parts;
	}
}
