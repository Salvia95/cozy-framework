package io.salvia95.cozy.cli.internal.behavior;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Component;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.State;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Type;
import io.salvia95.cozy.cli.internal.behavior.Expr.Bool;
import io.salvia95.cozy.cli.internal.behavior.Expr.Call;
import io.salvia95.cozy.cli.internal.behavior.Expr.EventRef;
import io.salvia95.cozy.cli.internal.behavior.Expr.Name;
import io.salvia95.cozy.cli.internal.behavior.Expr.Num;
import io.salvia95.cozy.cli.internal.behavior.Expr.Text;
import io.salvia95.cozy.rules.BehaviorRules;

/**
 * 동작 명세를 확인하고 그 명세에서 모듈의 JS를 만든다. 같은 명세는 같은 JS가 된다. 만든 JS는 지금 규칙을 그대로 따른다: {@code alpine:init}의
 * {@code Alpine.data} 등록, 이름은 모듈 이름으로 시작, 서버 값은 {@code x-data} 요소의 {@code data-*}에서 읽음, 서버에 요청하지 않음.
 *
 * <p>낱말: docs/conventions/ui.md의 "동작 명세".
 */
public final class BehaviorScript {

	/** 계산과 동작의 값에 쓰는 함수 → 인자 수(-1은 둘 이상) */
	static final Map<String, Integer> VALUES = ordered("not", 1, "and", -1, "or", -1, "eq", 2, "ne", 2, "gt", 2, "lt", 2, "ge", 2,
			"le", 2, "empty", 1, "count", 1, "length", 1, "includes", 2, "number", 1, "format", -1, "if", 3);

	/** 동작의 단계 */
	static final List<String> STEPS = List.of("set", "toggle", "increment", "decrement", "addItem", "removeItem", "toggleItem",
			"reset", "emit", "when", "within");

	/** 컴포넌트 객체에서 쓰지 않는 이름 */
	static final Set<String> RESERVED = Set.of("init", "destroy", "root", "initial", "listeners", "constructor", "toString",
			"valueOf", "hasOwnProperty", "isPrototypeOf", "propertyIsEnumerable", "toLocaleString", "break", "case", "catch",
			"class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends", "finally", "for",
			"function", "if", "import", "in", "instanceof", "let", "new", "return", "super", "switch", "this", "throw", "try",
			"typeof", "var", "void", "while", "with", "yield", "await", "enum", "implements", "interface", "package", "private",
			"protected", "public", "static", "null", "true", "false", "undefined", "event");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{|\\}\\}|\\{([^{}]*)\\}|[{}]");

	private static final Pattern LOCALE = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");

	private BehaviorScript() {
	}

	/** 명세의 이름, 종류, 낱말을 확인한다. 틀리면 무엇이 틀렸는지 알리고 실패한다 */
	public static void check(BehaviorSpec spec, String file) {
		for (Component c : spec.components().values()) {
			new Checker(spec.module(), c, file + " 컴포넌트 " + c.key()).check();
		}
	}

	/**
	 * 모듈의 JS. 머리 주석의 마지막 줄이 {@link BehaviorRules#stampLine}이다.
	 *
	 * @param specText 명세 파일의 내용 ({@link BehaviorSpec#write()})
	 */
	public static String generate(BehaviorSpec spec, String specText) {
		check(spec, BehaviorRules.specPath(spec.module()));
		Set<String> helpers = new LinkedHashSet<>();
		List<String> components = new ArrayList<>();
		for (Component c : spec.components().values()) {
			StringBuilder component = new StringBuilder();
			new Writer(spec.module(), c, helpers, component).write();
			components.add(component.toString());
		}
		List<String> blocks = new ArrayList<>();
		for (String helper : Helpers.withDependencies(helpers)) {
			blocks.add(Helpers.CODE.get(helper));
		}
		blocks.addAll(components);
		String body = "document.addEventListener('alpine:init', function () {\n" + String.join("\n", blocks) + "});\n";
		String module = spec.module();
		String head = "/* " + module + " 모듈의 화면 안 동작. cozy가 동작 명세(" + BehaviorRules.specPath(module)
				+ ")에서 만든 파일이라 손으로 고치지 않는다.\n   동작은 cozy add behavior " + module
				+ " <동작> …로 바꾸고, 다시 만들 때는 cozy sync behaviors를 쓴다.\n   ";
		return head + BehaviorRules.stampLine(specText, body) + body;
	}

	// ── 확인 ──────────────────────────────────────────────

	/** 컴포넌트 하나의 이름과 종류 */
	private static final class Checker {

		private final String module;

		private final Component component;

		private final String where;

		private final Map<String, Type> computedTypes = new LinkedHashMap<>();

		private final Set<String> visiting = new HashSet<>();

		Checker(String module, Component component, String where) {
			this.module = module;
			this.component = component;
			this.where = where;
		}

		void check() {
			Set<String> seen = new HashSet<>();
			for (String section : List.of("상태", "계산", "동작")) {
				Set<String> names = switch (section) {
					case "상태" -> this.component.state().keySet();
					case "계산" -> this.component.computed().keySet();
					default -> this.component.actions().keySet();
				};
				for (String name : names) {
					if (!BehaviorSpec.NAME.matcher(name).matches()) {
						throw fail(section + " " + name, "이름은 영문 소문자로 시작하는 카멜 표기입니다");
					}
					if (RESERVED.contains(name)) {
						throw fail(section + " " + name, "'" + name + "'은 쓸 수 없는 이름입니다(Alpine과 JavaScript가 쓰는 이름)");
					}
					if (!seen.add(name)) {
						throw fail(section + " " + name, "같은 이름이 상태, 계산, 동작 가운데 둘 이상에 있습니다. 템플릿이 이름 하나로 부르므로 이름을 나눕니다");
					}
				}
			}
			for (String name : this.component.computed().keySet()) {
				computedType(name);
			}
			this.component.actions().forEach((name, steps) -> {
				for (Expr step : steps) {
					step(step, "동작 " + name);
				}
			});
			this.component.listens().forEach((event, action) -> {
				if (!BehaviorSpec.EVENT.matcher(event).matches()) {
					throw fail("listens " + event, "화면 이벤트의 이름은 모듈:이름 모양입니다 (예: memo:saved)");
				}
				if (!this.component.actions().containsKey(action)) {
					throw fail("listens " + event, "동작 '" + action + "'이 없습니다. 있는 동작: " + this.component.actions().keySet());
				}
			});
		}

		private Type computedType(String name) {
			Type known = this.computedTypes.get(name);
			if (known != null) {
				return known;
			}
			if (!this.visiting.add(name)) {
				throw fail("계산 " + name, "계산이 돌고 돌아 자기 자신을 씁니다");
			}
			Type type = value(this.component.computed().get(name), "계산 " + name, false);
			this.visiting.remove(name);
			this.computedTypes.put(name, type);
			return type;
		}

		/** 값의 종류. 이벤트 값은 동작에서만 쓴다 */
		Type value(Expr expr, String what, boolean inAction) {
			return switch (expr) {
				case Num n -> Type.NUMBER;
				case Bool b -> Type.BOOL;
				case Text t -> Type.TEXT;
				case EventRef e -> {
					if (!inAction) {
						throw fail(what, "이벤트 값(" + e.print() + ")은 동작에서만 씁니다. 계산은 상태와 다른 계산만 읽습니다");
					}
					yield switch (e.kind()) {
						case "checked" -> Type.BOOL;
						case "detail" -> Type.ANY;
						default -> Type.TEXT;
					};
				}
				case Name n -> name(n.name(), what);
				case Call c -> call(c, what, inAction);
			};
		}

		private Type name(String name, String what) {
			State state = this.component.state().get(name);
			if (state != null) {
				return state.type();
			}
			if (this.component.computed().containsKey(name)) {
				return computedType(name);
			}
			if (this.component.actions().containsKey(name)) {
				throw fail(what, "'" + name + "'은 동작이라 값으로 읽을 수 없습니다");
			}
			throw fail(what, "이름 '" + name + "'이 없습니다. 상태: " + this.component.state().keySet() + ", 계산: "
					+ this.component.computed().keySet());
		}

		private Type call(Call c, String what, boolean inAction) {
			String f = c.function();
			if (STEPS.contains(f)) {
				throw fail(what, f + "(…)는 동작의 단계라 값으로 쓸 수 없습니다");
			}
			Integer arity = VALUES.get(f);
			if (arity == null) {
				throw fail(what, "함수 " + f + "(…)를 모릅니다. 값: " + String.join(", ", VALUES.keySet()) + ". 단계: " + String.join(", ", STEPS));
			}
			List<Expr> args = c.args();
			if (f.equals("format")) {
				return format(c, what);
			}
			if (arity >= 0 && args.size() != arity) {
				throw fail(what, f + "(…)는 인자 " + arity + "개를 받습니다: " + c.print());
			}
			if (arity < 0 && args.size() < 2) {
				throw fail(what, f + "(…)는 인자 둘 이상을 받습니다: " + c.print());
			}
			List<Type> types = new ArrayList<>();
			for (Expr arg : args) {
				types.add(value(arg, what, inAction));
			}
			return switch (f) {
				case "not", "and", "or" -> {
					types.forEach((t) -> expect(t, Type.BOOL, what, c));
					yield Type.BOOL;
				}
				case "eq", "ne" -> Type.BOOL;
				case "gt", "lt", "ge", "le" -> {
					types.forEach((t) -> expect(t, Type.NUMBER, what, c));
					yield Type.BOOL;
				}
				case "empty" -> {
					if (types.get(0) != Type.TEXT && types.get(0) != Type.LIST && types.get(0) != Type.ANY) {
						throw fail(what, "empty(…)는 text나 list를 받습니다: " + c.print());
					}
					yield Type.BOOL;
				}
				case "count" -> {
					expect(types.get(0), Type.LIST, what, c);
					yield Type.NUMBER;
				}
				case "length" -> {
					expect(types.get(0), Type.TEXT, what, c);
					yield Type.NUMBER;
				}
				case "includes" -> {
					expect(types.get(0), Type.LIST, what, c);
					if (types.get(1) == Type.LIST) {
						throw fail(what, "includes(목록, 값)의 값은 목록이 아닙니다: " + c.print());
					}
					yield Type.BOOL;
				}
				case "number" -> Type.NUMBER;
				case "if" -> {
					expect(types.get(0), Type.BOOL, what, c);
					Type a = types.get(1);
					Type b = types.get(2);
					if (a != b && a != Type.ANY && b != Type.ANY) {
						throw fail(what, "if(조건, 참일 때, 거짓일 때)의 두 값은 같은 종류입니다(" + a.word() + ", " + b.word() + "): " + c.print());
					}
					yield (a == Type.ANY) ? b : a;
				}
				default -> throw new IllegalStateException(f);
			};
		}

		private Type format(Call c, String what) {
			List<Expr> args = c.args();
			if (args.isEmpty() || args.size() > 2 || !(args.get(0) instanceof Text template)) {
				throw fail(what, "format('글자 {이름}', 'ko-KR')의 첫 인자는 작은따옴표 글자이고, 둘째(숫자 형식)는 고를 수 있습니다: " + c.print());
			}
			if (args.size() == 2 && !(args.get(1) instanceof Text locale && LOCALE.matcher(locale.value()).matches())) {
				throw fail(what, "format의 둘째 인자는 숫자 형식(예: 'ko-KR')입니다: " + c.print());
			}
			for (FormatPart part : parts(template.value(), what)) {
				if (part.name() != null) {
					name(part.name(), what);
				}
			}
			return Type.TEXT;
		}

		private void expect(Type actual, Type expected, String what, Call c) {
			if (actual != expected && actual != Type.ANY) {
				throw fail(what, c.function() + "(…)는 " + expected.word() + "를 받는데 " + actual.word() + "를 받았습니다: " + c.print());
			}
		}

		private void step(Expr step, String what) {
			if (!(step instanceof Call c) || !STEPS.contains(c.function())) {
				throw fail(what, "단계는 " + String.join(", ", STEPS) + " 가운데 하나입니다: " + step.print());
			}
			List<Expr> args = c.args();
			switch (c.function()) {
				case "set" -> {
					arity(c, 2, what);
					Type target = target(args.get(0), what, null);
					Type value = value(args.get(1), what, true);
					if (value != target && value != Type.ANY) {
						throw fail(what, "set(" + args.get(0).print() + ", …)의 값은 " + target.word() + "입니다(" + value.word() + "를 받음). 글자를 숫자로 "
								+ "바꿀 때는 number(…)를 씁니다");
					}
				}
				case "toggle" -> {
					arity(c, 1, what);
					target(args.get(0), what, Type.BOOL);
				}
				case "increment", "decrement" -> {
					if (args.isEmpty() || args.size() > 2) {
						throw fail(what, c.function() + "(상태[, 숫자])입니다: " + c.print());
					}
					target(args.get(0), what, Type.NUMBER);
					if (args.size() == 2 && !(args.get(1) instanceof Num)) {
						throw fail(what, c.function() + "의 둘째 인자는 숫자입니다: " + c.print());
					}
				}
				case "addItem", "removeItem", "toggleItem" -> {
					arity(c, 2, what);
					target(args.get(0), what, Type.LIST);
					if (value(args.get(1), what, true) == Type.LIST) {
						throw fail(what, c.function() + "(목록, 값)의 값은 목록이 아닙니다: " + c.print());
					}
				}
				case "reset" -> {
					arity(c, 1, what);
					target(args.get(0), what, null);
				}
				case "emit" -> {
					if (args.isEmpty() || !(args.get(0) instanceof Text event)) {
						throw fail(what, "emit('" + this.module + ":이름'[, 상태나 계산 …])입니다: " + c.print());
					}
					if (!BehaviorSpec.EVENT.matcher(event.value()).matches() || !event.value().startsWith(this.module + ":")) {
						throw fail(what, "내는 화면 이벤트의 이름은 " + this.module + ":소문자-하이픈 모양입니다(이 모듈의 이름으로 시작): " + event.value());
					}
					Set<String> keys = new HashSet<>();
					for (Expr arg : args.subList(1, args.size())) {
						if (!(arg instanceof Name n)) {
							throw fail(what, "emit이 함께 보내는 값은 상태나 계산의 이름입니다: " + arg.print());
						}
						name(n.name(), what);
						if (!keys.add(n.name())) {
							throw fail(what, "emit에 같은 이름을 두 번 적었습니다: " + n.name());
						}
					}
				}
				case "when" -> {
					if (args.size() < 2) {
						throw fail(what, "when(조건, 단계 …)입니다: " + c.print());
					}
					Type condition = value(args.get(0), what, true);
					if (condition != Type.BOOL && condition != Type.ANY) {
						throw fail(what, "when의 조건은 bool입니다: " + c.print());
					}
					args.subList(1, args.size()).forEach((s) -> step(s, what));
				}
				case "within" -> {
					if (args.size() < 2 || !(args.get(0) instanceof Text selector) || selector.value().isBlank()
							|| selector.value().contains("\n")) {
						throw fail(what, "within('선택자', 단계 …)입니다. 이벤트가 그 선택자의 요소 안에서 일어났을 때만 단계를 합니다: " + c.print());
					}
					args.subList(1, args.size()).forEach((s) -> step(s, what));
				}
				default -> throw new IllegalStateException(c.function());
			}
		}

		private void arity(Call c, int count, String what) {
			if (c.args().size() != count) {
				throw fail(what, c.function() + "(…)는 인자 " + count + "개를 받습니다: " + c.print());
			}
		}

		/** 바꿀 상태. 계산과 동작은 바꿀 수 없다 */
		private Type target(Expr expr, String what, Type expected) {
			if (!(expr instanceof Name n)) {
				throw fail(what, "바꿀 것은 상태의 이름입니다: " + expr.print());
			}
			State state = this.component.state().get(n.name());
			if (state == null) {
				String section = this.component.sectionOf(n.name());
				throw fail(what, (section != null) ? "'" + n.name() + "'은 " + section + "이라 바꿀 수 없습니다. 바꿀 수 있는 것은 상태입니다"
						: "상태 '" + n.name() + "'이 없습니다. 있는 상태: " + this.component.state().keySet());
			}
			if (expected != null && state.type() != expected) {
				throw fail(what, "상태 '" + n.name() + "'은 " + state.type().word() + "입니다. 이 단계는 " + expected.word() + " 상태를 바꿉니다");
			}
			return state.type();
		}

		private CliException fail(String what, String why) {
			return new CliException(this.where + " " + what + ": " + why);
		}
	}

	/** format의 조각: 글자나 이름 */
	record FormatPart(String text, String name) {
	}

	static List<FormatPart> parts(String template, String what) {
		List<FormatPart> parts = new ArrayList<>();
		Matcher m = PLACEHOLDER.matcher(template);
		int last = 0;
		StringBuilder text = new StringBuilder();
		while (m.find()) {
			text.append(template, last, m.start());
			last = m.end();
			String token = m.group();
			if (token.equals("{{")) {
				text.append('{');
			}
			else if (token.equals("}}")) {
				text.append('}');
			}
			else if (m.group(1) != null && BehaviorSpec.NAME.matcher(m.group(1)).matches()) {
				if (!text.isEmpty()) {
					parts.add(new FormatPart(text.toString(), null));
					text.setLength(0);
				}
				parts.add(new FormatPart(null, m.group(1)));
			}
			else {
				throw new CliException(what + ": format의 {…}에는 상태나 계산의 이름을 적습니다. 중괄호 글자는 {{, }}로 적습니다: " + template);
			}
		}
		text.append(template.substring(last));
		if (!text.isEmpty()) {
			parts.add(new FormatPart(text.toString(), null));
		}
		return parts;
	}

	// ── 만들기 ──────────────────────────────────────────────

	/** 컴포넌트 하나의 등록 */
	private static final class Writer {

		private final String module;

		private final Component c;

		private final Set<String> helpers;

		private final StringBuilder out;

		Writer(String module, Component component, Set<String> helpers, StringBuilder out) {
			this.module = module;
			this.c = component;
			this.helpers = helpers;
			this.out = out;
		}

		void write() {
			String name = this.c.name(this.module);
			this.helpers.add("copy");
			line(2, "// " + name + ": " + this.c.purpose());
			line(2, "window.Alpine.data(" + js(name) + ", function () {");
			line(4, "var root = null;");
			line(4, "var initial = {};");
			if (!this.c.listens().isEmpty()) {
				line(4, "var listeners = [];");
				this.helpers.add("listen");
				this.helpers.add("unlisten");
			}
			line(4, "return {");
			List<String> members = new ArrayList<>();
			this.c.state().forEach((key, state) -> members.add(indent(6) + key + ": " + initial(state)));
			members.add(init());
			if (!this.c.listens().isEmpty()) {
				members.add(indent(6) + "destroy: function () {\n" + indent(8) + "unlisten(listeners);\n" + indent(6) + "}");
			}
			this.c.computed()
				.forEach((key, expr) -> members.add(indent(6) + "get " + key + "() {\n" + indent(8) + "return " + value(expr) + ";\n"
						+ indent(6) + "}"));
			this.c.actions().forEach((key, steps) -> {
				StringBuilder method = new StringBuilder(indent(6) + key + ": function (event) {\n");
				for (Expr step : steps) {
					step(step, 8, method);
				}
				members.add(method.append(indent(6)).append('}').toString());
			});
			this.out.append(String.join(",\n", members)).append('\n');
			line(4, "};");
			line(2, "});");
		}

		private String init() {
			StringBuilder init = new StringBuilder(indent(6) + "init: function () {\n");
			init.append(indent(8)).append("root = this.$root;\n");
			this.c.state().forEach((key, state) -> {
				if (state.data() != null) {
					String reader = switch (state.type()) {
						case BOOL -> "readBool";
						case NUMBER -> "readNumber";
						case TEXT -> "readText";
						default -> "readList";
					};
					this.helpers.add(reader);
					init.append(indent(8)).append("this.").append(key).append(" = ").append(reader).append("(root, ")
						.append(js(dataset(state.data()))).append(", ").append(initial(state)).append(");\n");
				}
				init.append(indent(8)).append("initial.").append(key).append(" = copy(this.").append(key).append(");\n");
			});
			if (!this.c.listens().isEmpty()) {
				init.append(indent(8)).append("var self = this;\n");
				this.c.listens().forEach((event, action) -> init.append(indent(8)).append("listen(listeners, ").append(js(event))
					.append(", function (event) {\n").append(indent(10)).append("self.").append(action).append("(event);\n")
					.append(indent(8)).append("});\n"));
			}
			return init.append(indent(6)).append('}').toString();
		}

		private void step(Expr step, int depth, StringBuilder out) {
			Call call = (Call) step;
			List<Expr> args = call.args();
			String target = (args.isEmpty() || !(args.get(0) instanceof Name n)) ? null : "this." + n.name();
			switch (call.function()) {
				case "set" -> {
					State state = this.c.state().get(((Name) args.get(0)).name());
					String value = value(args.get(1));
					if (state.type() == Type.LIST) {
						value = "copy(" + value + ")";
					}
					out.append(indent(depth)).append(target).append(" = ").append(value).append(";\n");
				}
				case "toggle" -> out.append(indent(depth)).append(target).append(" = !").append(target).append(";\n");
				case "increment", "decrement" -> out.append(indent(depth)).append(target).append(" = ").append(target)
					.append(call.function().equals("increment") ? " + " : " - ")
					.append((args.size() == 2) ? ((Num) args.get(1)).text() : "1").append(";\n");
				case "addItem", "removeItem", "toggleItem" -> {
					String helper = switch (call.function()) {
						case "addItem" -> "withItem";
						case "removeItem" -> "withoutItem";
						default -> "toggledItem";
					};
					this.helpers.add(helper);
					out.append(indent(depth)).append(target).append(" = ").append(helper).append("(").append(target).append(", ")
						.append(value(args.get(1))).append(");\n");
				}
				case "reset" -> out.append(indent(depth)).append(target).append(" = copy(initial.")
					.append(((Name) args.get(0)).name()).append(");\n");
				case "emit" -> {
					List<String> detail = new ArrayList<>();
					for (Expr arg : args.subList(1, args.size())) {
						String key = ((Name) arg).name();
						detail.add(key + ": copy(this." + key + ")");
					}
					out.append(indent(depth)).append("this.$dispatch(").append(js(((Text) args.get(0)).value())).append(", ")
						.append(detail.isEmpty() ? "{}" : "{ " + String.join(", ", detail) + " }").append(");\n");
				}
				case "when", "within" -> {
					String condition;
					if (call.function().equals("when")) {
						condition = value(args.get(0));
					}
					else {
						this.helpers.add("within");
						condition = "within(event, root, " + js(((Text) args.get(0)).value()) + ")";
					}
					out.append(indent(depth)).append("if (").append(condition).append(") {\n");
					for (Expr inner : args.subList(1, args.size())) {
						step(inner, depth + 2, out);
					}
					out.append(indent(depth)).append("}\n");
				}
				default -> throw new IllegalStateException(call.function());
			}
		}

		private String value(Expr expr) {
			return switch (expr) {
				case Num n -> n.text();
				case Bool b -> Boolean.toString(b.value());
				case Text t -> js(t.value());
				case Name n -> "this." + n.name();
				case EventRef e -> switch (e.kind()) {
					case "value" -> use("eventValue", "eventValue(event)");
					case "checked" -> use("eventChecked", "eventChecked(event)");
					case "data" -> use("eventData", "eventData(event, root, " + js(e.name()) + ")");
					default -> use("eventDetail", "eventDetail(event, " + js(e.name()) + ")");
				};
				case Call c -> call(c);
			};
		}

		private String call(Call c) {
			List<String> args = new ArrayList<>();
			if (!c.function().equals("format")) {
				c.args().forEach((a) -> args.add(value(a)));
			}
			return switch (c.function()) {
				case "not" -> "!" + args.get(0);
				case "and" -> "(" + String.join(" && ", args) + ")";
				case "or" -> "(" + String.join(" || ", args) + ")";
				case "eq" -> "(" + args.get(0) + " === " + args.get(1) + ")";
				case "ne" -> "(" + args.get(0) + " !== " + args.get(1) + ")";
				case "gt" -> "(" + args.get(0) + " > " + args.get(1) + ")";
				case "lt" -> "(" + args.get(0) + " < " + args.get(1) + ")";
				case "ge" -> "(" + args.get(0) + " >= " + args.get(1) + ")";
				case "le" -> "(" + args.get(0) + " <= " + args.get(1) + ")";
				case "empty" -> use("isEmpty", "isEmpty(" + args.get(0) + ")");
				case "count" -> use("countOf", "countOf(" + args.get(0) + ")");
				case "length" -> use("lengthOf", "lengthOf(" + args.get(0) + ")");
				case "includes" -> use("includes", "includes(" + args.get(0) + ", " + args.get(1) + ")");
				case "number" -> use("toNumber", "toNumber(" + args.get(0) + ")");
				case "if" -> "(" + args.get(0) + " ? " + args.get(1) + " : " + args.get(2) + ")";
				case "format" -> format(c);
				default -> throw new IllegalStateException(c.function());
			};
		}

		private String format(Call c) {
			String locale = (c.args().size() == 2) ? js(((Text) c.args().get(1)).value()) : "null";
			List<String> pieces = new ArrayList<>();
			for (FormatPart part : parts(((Text) c.args().get(0)).value(), "format")) {
				if (part.name() != null) {
					pieces.add(use("show", "show(this." + part.name() + ", " + locale + ")"));
				}
				else {
					pieces.add(js(part.text()));
				}
			}
			if (pieces.isEmpty()) {
				return "''";
			}
			return (pieces.size() == 1) ? pieces.get(0) : "(" + String.join(" + ", pieces) + ")";
		}

		private String use(String helper, String code) {
			this.helpers.add(helper);
			return code;
		}

		private String initial(State state) {
			return switch (state.type()) {
				case BOOL -> (state.initial() != null) ? state.initial() : "false";
				case NUMBER -> (state.initial() != null) ? state.initial() : "0";
				case TEXT -> js((state.initial() != null) ? state.initial() : "");
				default -> "[" + String.join(", ", state.items().stream().map(BehaviorScript::js).toList()) + "]";
			};
		}

		private void line(int depth, String text) {
			this.out.append(indent(depth)).append(text).append('\n');
		}
	}

	/** {@code data-copy-text} → {@code copyText} */
	static String dataset(String attribute) {
		String[] parts = attribute.substring("data-".length()).split("-");
		StringBuilder out = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			out.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
		}
		return out.toString();
	}

	/** JS의 작은따옴표 글자 */
	static String js(String value) {
		StringBuilder out = new StringBuilder("'");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '\'' -> out.append("\\'");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case ' ' -> out.append("\\u2028");
				case ' ' -> out.append("\\u2029");
				case '<' -> out.append("\\x3c");
				default -> out.append(c);
			}
		}
		return out.append('\'').toString();
	}

	private static String indent(int depth) {
		return " ".repeat(depth);
	}

	private static Map<String, Integer> ordered(Object... pairs) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	/** 만든 JS가 쓰는 작은 함수들. 쓰는 것만 정해 둔 순서로 넣는다 */
	static final class Helpers {

		static final Map<String, String> CODE = new LinkedHashMap<>();

		static final Map<String, List<String>> NEEDS = Map.of("toggledItem", List.of("includes", "withItem", "withoutItem"),
				"withItem", List.of("includes"), "readList", List.of("copy"));

		static {
			add("readBool", """
					  function readBool(root, name, fallback) {
					    var value = root.dataset[name];
					    return (value === undefined) ? fallback : value === 'true';
					  }
					""");
			add("readNumber", """
					  function readNumber(root, name, fallback) {
					    var value = parseFloat(root.dataset[name]);
					    return isNaN(value) ? fallback : value;
					  }
					""");
			add("readText", """
					  function readText(root, name, fallback) {
					    var value = root.dataset[name];
					    return (value === undefined) ? fallback : value;
					  }
					""");
			add("readList", """
					  function readList(root, name, fallback) {
					    var value = root.dataset[name];
					    if (value === undefined) {
					      return copy(fallback);
					    }
					    return value.split(/\\s+/).filter(function (item) {
					      return item !== '';
					    });
					  }
					""");
			add("copy", """
					  function copy(value) {
					    return Array.isArray(value) ? value.slice() : value;
					  }
					""");
			add("eventValue", """
					  function eventValue(event) {
					    var target = event && event.target;
					    return (target && target.value !== undefined && target.value !== null) ? String(target.value) : '';
					  }
					""");
			add("eventChecked", """
					  function eventChecked(event) {
					    return !!(event && event.target && event.target.checked);
					  }
					""");
			add("eventData", """
					  function eventData(event, root, name) {
					    var element = event && event.target;
					    while (element && element.nodeType === 1) {
					      if (element.dataset && element.dataset[name] !== undefined) {
					        return element.dataset[name];
					      }
					      if (element === root) {
					        break;
					      }
					      element = element.parentElement;
					    }
					    return '';
					  }
					""");
			add("eventDetail", """
					  function eventDetail(event, name) {
					    return (event && event.detail && event.detail[name] !== undefined) ? event.detail[name] : '';
					  }
					""");
			add("within", """
					  function within(event, root, selector) {
					    var target = event && event.target;
					    var element = (target && target.closest) ? target.closest(selector) : null;
					    return element !== null && root.contains(element);
					  }
					""");
			add("toNumber", """
					  function toNumber(value) {
					    var number = parseFloat(value);
					    return isNaN(number) ? 0 : number;
					  }
					""");
			add("isEmpty", """
					  function isEmpty(value) {
					    return value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0);
					  }
					""");
			add("countOf", """
					  function countOf(value) {
					    return Array.isArray(value) ? value.length : 0;
					  }
					""");
			add("lengthOf", """
					  function lengthOf(value) {
					    return (value === null || value === undefined) ? 0 : String(value).length;
					  }
					""");
			add("includes", """
					  function includes(list, value) {
					    return Array.isArray(list) && list.indexOf(value) >= 0;
					  }
					""");
			add("show", """
					  function show(value, locale) {
					    if (Array.isArray(value)) {
					      return value.join(', ');
					    }
					    if (typeof value === 'number' && locale) {
					      return value.toLocaleString(locale);
					    }
					    return (value === null || value === undefined) ? '' : String(value);
					  }
					""");
			add("withItem", """
					  function withItem(list, value) {
					    return includes(list, value) ? list.slice() : list.concat([value]);
					  }
					""");
			add("withoutItem", """
					  function withoutItem(list, value) {
					    return list.filter(function (item) {
					      return item !== value;
					    });
					  }
					""");
			add("toggledItem", """
					  function toggledItem(list, value) {
					    return includes(list, value) ? withoutItem(list, value) : withItem(list, value);
					  }
					""");
			add("listen", """
					  function listen(listeners, name, handler) {
					    window.addEventListener(name, handler);
					    listeners.push([name, handler]);
					  }
					""");
			add("unlisten", """
					  function unlisten(listeners) {
					    listeners.forEach(function (entry) {
					      window.removeEventListener(entry[0], entry[1]);
					    });
					    listeners.length = 0;
					  }
					""");
		}

		private static void add(String name, String code) {
			CODE.put(name, code);
		}

		/** 쓰는 함수와 그 함수가 쓰는 함수. 순서는 {@link #CODE}의 순서 */
		static List<String> withDependencies(Set<String> used) {
			Set<String> all = new TreeSet<>(used);
			boolean grew = true;
			while (grew) {
				grew = false;
				for (String name : List.copyOf(all)) {
					for (String need : NEEDS.getOrDefault(name, List.of())) {
						grew |= all.add(need);
					}
				}
			}
			return CODE.keySet().stream().filter(all::contains).toList();
		}
	}
}
