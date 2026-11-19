package io.salvia95.cozy.cli.internal.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Type;
import io.salvia95.cozy.cli.internal.behavior.TemplateTags.Attr;
import io.salvia95.cozy.cli.internal.behavior.TemplateTags.Tag;

/**
 * 템플릿의 요소 하나에 컴포넌트를 붙이고 뗀다. 글자는 그대로 두고 그 요소의 여는 태그만 고친다. 붙일 이름은 컴포넌트가 내놓는 이름인지 확인한다.
 *
 * <ul>
 * <li>요소에 {@code x-data}가 없고 감싸는 요소도 그 컴포넌트가 아니면, 이 요소가 컴포넌트의 자리다({@code x-data}와 {@code data-*}).</li>
 * <li>감싸는 요소가 그 컴포넌트면 안쪽 요소다. 이벤트와 값만 붙인다.</li>
 * <li>이미 다른 컴포넌트를 붙인 요소에는 붙이지 않는다.</li>
 * </ul>
 */
public final class Attach {

	/** 서버 값: Thymeleaf 식이면 {@code th:data-*} */
	private static final Pattern THYMELEAF = Pattern.compile("[$*#@~]\\{");

	private static final Pattern VALUE = Pattern.compile("([a-z][A-Za-z0-9]*)(?:\\('((?:[^'\\\\]|\\\\.)*)'\\))?");

	private static final Pattern EVENT = Pattern.compile("[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*(?:\\.[a-z][a-z0-9-]*)*");

	private static final Pattern ATTRIBUTE = Pattern.compile("[a-z][a-z0-9-]*");

	private static final Pattern WORD = Pattern.compile("(?<![.$\\w])[A-Za-z_$][A-Za-z0-9_$]*");

	private static final List<String> ORDER = List.of("x-data", "data", "x-on:", "x-bind:", "x-show", "x-text", "x-model",
			"x-cloak");

	private Attach() {
	}

	/**
	 * 붙일 수 있는 이름.
	 *
	 * @param handlers {@code x-on}이 부르는 이름 (동작, 메서드)
	 * @param values {@code x-text}, {@code x-show}, {@code x-bind}가 읽는 이름 (상태, 계산, 메서드)
	 * @param models {@code x-model}에 쓰는 상태 (text, bool)
	 * @param data {@code x-data} 요소의 {@code data-*} 이름
	 * @param withArgument 글자 인자 하나를 받는 메서드
	 */
	public record Target(String name, Set<String> handlers, Set<String> values, Set<String> models, Set<String> data,
			Set<String> withArgument) {

		public static Target of(String module, BehaviorSpec.Component c) {
			Set<String> values = new LinkedHashSet<>(c.state().keySet());
			values.addAll(c.computed().keySet());
			Set<String> models = new LinkedHashSet<>();
			c.state().forEach((k, s) -> {
				if (s.type() == Type.TEXT || s.type() == Type.BOOL) {
					models.add(k);
				}
			});
			return new Target(c.name(module), new LinkedHashSet<>(c.actions().keySet()), values, models,
					new LinkedHashSet<>(BehaviorScript.data(c)), Set.of());
		}

		public static Target of(FrameworkBehaviors.Component c) {
			Set<String> methods = new LinkedHashSet<>(c.methods().keySet());
			Set<String> withArgument = new LinkedHashSet<>();
			c.methods().values().forEach((m) -> {
				if (m.argument() != null) {
					withArgument.add(m.name());
				}
			});
			return new Target(c.name(), methods, methods, Set.of(), new LinkedHashSet<>(c.data().keySet()), withArgument);
		}
	}

	/**
	 * @param data {@code 이름=값}
	 * @param bindings 속성 이름({@code x-on:input}) → 값({@code count})
	 * @param detach 떼는가. 무엇도 적지 않으면 이 컴포넌트의 것을 모두 뗀다
	 */
	public record Request(List<String[]> data, List<String[]> bindings, boolean cloak, boolean detach) {

		boolean empty() {
			return this.data.isEmpty() && this.bindings.isEmpty() && !this.cloak;
		}
	}

	/** @param detail 계획에 보일 한 줄. 바뀐 것이 없으면 {@code null} */
	public record Result(String text, String detail) {
	}

	/** {@code --on input:count} → {@code x-on:input}, {@code count} */
	public static String[] on(String option) {
		Matcher m = Pattern.compile("(.+?):(" + VALUE.pattern() + ")").matcher(option);
		if (!m.matches() || !EVENT.matcher(m.group(1)).matches()) {
			throw new CliException("--on은 이벤트:동작 모양입니다 (예: --on input:count, --on keydown.window:focusOnKey): " + option);
		}
		return new String[] { "x-on:" + m.group(1), m.group(2) };
	}

	/** {@code --bind disabled:none} → {@code x-bind:disabled}, {@code none} */
	public static String[] bind(String option) {
		int at = option.indexOf(':');
		if (at <= 0 || !ATTRIBUTE.matcher(option.substring(0, at)).matches()) {
			throw new CliException("--bind는 속성:이름 모양입니다 (예: --bind disabled:none): " + option);
		}
		return new String[] { "x-bind:" + option.substring(0, at), option.substring(at + 1) };
	}

	/**
	 * 그 줄의 요소와 지금 붙은 컴포넌트. 붙이기 전에 무엇을 고를 수 있는지 보는 데 쓴다.
	 *
	 * @param element 여는 태그를 짧게 ({@code <div class="form-hint">})
	 * @param component 요소에 붙은 컴포넌트({@code x-data})나 감싸는 요소의 컴포넌트. 없으면 {@code null}
	 * @param root 요소 자신에 {@code x-data}가 있는가
	 */
	public record Place(String element, String component, boolean root) {
	}

	/**
	 * 그 줄의 요소. {@link #apply}가 거절하는 줄(요소가 없거나 둘 이상, 그려질 때 바뀌는 요소)이면 같은 오류로 실패한다.
	 *
	 * @param where 오류에 보일 템플릿 이름
	 */
	public static Place place(String text, String where, int line) {
		List<Tag> tags = TemplateTags.read(text);
		Tag tag = element(tags, text, where, line);
		Attr own = tag.attr("x-data");
		if (own != null) {
			return new Place(open(text, tag), own.value(), true);
		}
		return new Place(open(text, tag), scope(tags, tag.parent()), false);
	}

	/** 그 줄에서 시작하는 가장 바깥 요소 하나 (버튼 안의 아이콘처럼 같은 줄에서 시작하는 안쪽 요소는 빼고) */
	private static Tag element(List<Tag> tags, String text, String where, int line) {
		List<Tag> starting = tags.stream()
			.filter((t) -> t.line() == line && (t.parent() < 0 || tags.get(t.parent()).line() != line))
			.toList();
		if (starting.size() != 1) {
			throw new CliException(where + ":" + line + "에서 시작하는 요소가 " + starting.size() + "개입니다. 요소 하나가 시작하는 줄을 적습니다"
					+ " (cozy describe나 워크벤치의 구조가 보이는 줄).");
		}
		Tag tag = starting.get(0);
		if (tag.name().equals("th:block") || tag.attr("th:replace") != null || tag.attr("data-th-replace") != null) {
			throw new CliException(where + ":" + line + "의 요소는 그려질 때 다른 것으로 바뀌어 속성이 남지 않습니다: " + open(text, tag) + ". 감싸는 요소나 "
					+ "그 안의 요소에 붙입니다.");
		}
		Attr data = tag.attr("x-data");
		if (data != null && (data.value() == null || data.value().isBlank())) {
			throw new CliException(where + ":" + line + "의 요소에 이름 없는 x-data가 있습니다: " + open(text, tag) + ". 컴포넌트는 이름으로 붙입니다. "
					+ "그 x-data를 지우거나 다른 요소에 붙입니다.");
		}
		return tag;
	}

	public static Result apply(String text, String where, int line, Target target, Request request) {
		List<Tag> tags = TemplateTags.read(text);
		Tag tag = element(tags, text, where, line);
		Attr own = tag.attr("x-data");
		String scope = scope(tags, tag.parent());
		boolean root;
		if (own != null) {
			if (!target.name().equals(own.value())) {
				throw new CliException(where + ":" + line + "의 요소에는 이미 다른 컴포넌트를 붙였습니다: " + own.value() + ". 다른 요소에 붙이거나 먼저 떼어 냅니다"
						+ " (cozy attach " + own.value() + " --at " + where + ":" + line + " --detach).");
			}
			root = true;
		}
		else {
			root = !target.name().equals(scope);
		}
		if (!root && !request.data().isEmpty()) {
			throw new CliException("--data는 컴포넌트를 붙인 요소(x-data=\"" + target.name() + "\")에 둡니다. 이 요소는 그 안쪽입니다.");
		}
		Edit edit = new Edit(text, tag);
		if (request.detach()) {
			detach(tags, tag, root, own != null, target, request, edit, where);
		}
		else {
			attach(tag, root && own == null, target, request, edit);
		}
		if (edit.changes.isEmpty()) {
			return new Result(text, null);
		}
		return new Result(edit.apply(), (request.detach() ? "" : "붙임 ") + open(text, tag) + ": " + String.join(", ", edit.changes));
	}

	private static void attach(Tag tag, boolean newRoot, Target target, Request request, Edit edit) {
		List<String[]> set = new ArrayList<>();
		if (newRoot) {
			set.add(new String[] { "x-data", target.name() });
		}
		for (String[] pair : request.data()) {
			if (!target.data().contains(pair[0])) {
				throw new CliException(target.name() + "에 없는 data-* 속성입니다: data-" + pair[0] + "." + io.salvia95.cozy.rules.Closest.hint(pair[0], target.data()) + " 읽는 것: "
						+ (target.data().isEmpty() ? "없음" : "data-" + String.join(", data-", target.data())));
			}
			boolean server = THYMELEAF.matcher(pair[1]).find();
			String name = (server ? "th:data-" : "data-") + pair[0];
			Attr other = tag.attr(server ? "data-" + pair[0] : "th:data-" + pair[0]);
			if (other != null) {
				edit.remove(other);
			}
			set.add(new String[] { name, pair[1] });
		}
		for (String[] binding : request.bindings()) {
			check(target, binding[0], binding[1]);
			set.add(binding);
		}
		set.sort(Comparator.comparingInt((String[] a) -> category(a[0])));
		for (String[] pair : set) {
			edit.set(pair[0], pair[1]);
		}
		if (request.cloak()) {
			edit.set("x-cloak", null);
		}
	}

	private static void detach(List<Tag> tags, Tag tag, boolean root, boolean hasData, Target target, Request request, Edit edit,
			String where) {
		if (request.empty()) {
			for (Attr a : tag.attrs()) {
				boolean mine = binding(a.name()) || a.name().equals("x-cloak")
						|| (root && hasData && (a.name().equals("x-data") || data(a.name(), target)));
				if (mine) {
					edit.remove(a);
				}
			}
			if (root && hasData) {
				List<Integer> inner = new ArrayList<>();
				for (Tag t : tags) {
					if (t != tag && tag.contains(t) && scopeTag(tags, t.parent()) == tag.index()
							&& t.attrs().stream().anyMatch((a) -> binding(a.name()) || a.name().equals("x-cloak"))) {
						inner.add(t.line());
					}
				}
				if (!inner.isEmpty()) {
					throw new CliException(target.name() + "의 안쪽 요소에 붙인 것이 남아 있습니다(" + where + " 줄 " + inner
							+ "). 그 요소들에서 먼저 뗍니다(cozy attach " + target.name() + " --at " + where + ":줄 --detach).");
				}
			}
			return;
		}
		for (String[] pair : request.data()) {
			for (String name : List.of("data-" + pair[0], "th:data-" + pair[0])) {
				Attr a = tag.attr(name);
				if (a != null) {
					edit.remove(a);
				}
			}
		}
		for (String[] binding : request.bindings()) {
			Attr a = tag.attr(binding[0]);
			if (a != null) {
				edit.remove(a);
			}
		}
		if (request.cloak() && tag.attr("x-cloak") != null) {
			edit.remove(tag.attr("x-cloak"));
		}
	}

	private static void check(Target target, String attribute, String value) {
		Matcher m = VALUE.matcher(value);
		if (!m.matches()) {
			throw new CliException(attribute + "=\"" + value + "\": 값은 이름이나 이름('글자')입니다. 로직은 컴포넌트의 계산과 동작에 둡니다.");
		}
		String name = m.group(1);
		boolean handler = attribute.startsWith("x-on:");
		boolean model = attribute.equals("x-model");
		java.util.Set<String> allowed = handler ? target.handlers() : model ? target.models() : target.values();
		if (!allowed.contains(name)) {
			String missing = model ? target.name() + "의 x-model에 쓸 상태(text, bool)가 아닙니다: " + name
					: target.name() + "에 없는 " + (handler ? "동작" : "상태나 계산") + "입니다: " + name;
			throw new CliException(missing + "." + io.salvia95.cozy.rules.Closest.hint(name, allowed) + " 있는 것: "
					+ (allowed.isEmpty() ? "없음" : String.join(", ", allowed)) + ". 앱 컴포넌트의 이름은 cozy add behavior로 더합니다.");
		}
		boolean argument = m.group(2) != null;
		if (target.withArgument().contains(name) != argument) {
			throw new CliException(target.withArgument().contains(name) ? "글자 인자 하나를 받는 이름입니다: " + name + " (예: " + name + "('filter'))."
					: "인자를 받지 않는 이름입니다: " + name + ". 이름만 씁니다.");
		}
	}

	/** 이벤트와 값을 붙이는 속성인가 */
	static boolean binding(String name) {
		return name.startsWith("x-on:") || name.startsWith("x-bind:") || name.startsWith("x-model") || name.equals("x-show")
				|| name.equals("x-text") || name.equals("x-effect") || name.equals("x-init") || name.equals("x-if");
	}

	private static boolean data(String name, Target target) {
		for (String d : target.data()) {
			if (name.equals("data-" + d) || name.equals("th:data-" + d)) {
				return true;
			}
		}
		return false;
	}

	private static int category(String name) {
		if (name.startsWith("data-") || name.startsWith("th:data-")) {
			return 1;
		}
		for (int i = 0; i < ORDER.size(); i++) {
			if (name.equals(ORDER.get(i)) || (ORDER.get(i).endsWith(":") && name.startsWith(ORDER.get(i)))) {
				return i;
			}
		}
		return ORDER.size();
	}

	/** 가장 가까운 x-data의 컴포넌트 (이 요소부터 위로) */
	static String scope(List<Tag> tags, int index) {
		int at = scopeTag(tags, index);
		return (at < 0) ? null : tags.get(at).attr("x-data").value();
	}

	static int scopeTag(List<Tag> tags, int index) {
		for (int i = index; i >= 0; i = tags.get(i).parent()) {
			Attr data = tags.get(i).attr("x-data");
			if (data != null && data.value() != null) {
				return i;
			}
		}
		return -1;
	}

	/** 그 컴포넌트를 감싼 요소 안에서 이름을 부르는 줄 (지울 이름이 쓰이는 곳) */
	public static List<Integer> uses(String text, String component, Set<String> names) {
		List<Tag> tags = TemplateTags.read(text);
		List<Integer> lines = new ArrayList<>();
		for (Tag t : tags) {
			if (!component.equals(scope(tags, t.index()))) {
				continue;
			}
			for (Attr a : t.attrs()) {
				if (binding(a.name()) && a.value() != null) {
					Matcher w = WORD.matcher(a.value().replaceAll("'(?:[^'\\\\]|\\\\.)*'", "''"));
					while (w.find()) {
						if (names.contains(w.group())) {
							lines.add(t.line());
							break;
						}
					}
				}
			}
		}
		return lines;
	}

	/**
	 * 파일 안에서 그 컴포넌트를 모두 뗀다: {@code x-data}, 읽는 {@code data-*}, 그 요소와 안쪽 요소(다른 컴포넌트 안은 빼고)의 이벤트와 값.
	 *
	 * @return 바뀐 글자. 그 컴포넌트가 없으면 같은 글자
	 */
	public static String detachEverywhere(String text, Target target) {
		List<Tag> tags = TemplateTags.read(text);
		List<Attr> remove = new ArrayList<>();
		for (Tag t : tags) {
			int scope = scopeTag(tags, t.index());
			if (scope < 0 || !target.name().equals(tags.get(scope).attr("x-data").value())) {
				continue;
			}
			for (Attr a : t.attrs()) {
				if (binding(a.name()) || a.name().equals("x-cloak")
						|| (scope == t.index() && (a.name().equals("x-data") || data(a.name(), target)))) {
					remove.add(a);
				}
			}
		}
		return removeAll(text, remove);
	}

	/** 파일 안의 {@code x-data="옛 이름"}을 새 이름으로 */
	public static String rename(String text, String from, String to) {
		List<Tag> tags = TemplateTags.read(text);
		StringBuilder out = new StringBuilder(text);
		List<Attr> data = new ArrayList<>();
		for (Tag t : tags) {
			Attr a = t.attr("x-data");
			if (a != null && from.equals(a.value())) {
				data.add(a);
			}
		}
		data.sort(Comparator.comparingInt(Attr::start).reversed());
		for (Attr a : data) {
			out.replace(a.start(), a.end(), "x-data=\"" + to + "\"");
		}
		return out.toString();
	}

	private static String removeAll(String text, List<Attr> remove) {
		StringBuilder out = new StringBuilder(text);
		remove.sort(Comparator.comparingInt(Attr::start).reversed());
		for (Attr a : remove) {
			int from = a.start();
			while (from > 0 && Character.isWhitespace(out.charAt(from - 1))) {
				from--;
			}
			out.delete(from, a.end());
		}
		return out.toString();
	}

	/** 여는 태그를 짧게: {@code <div class="form-hint">} */
	static String open(String text, Tag tag) {
		String open = text.substring(tag.start(), Math.min(text.length(), tag.close() + 1)).replaceAll("\\s+", " ");
		return (open.length() > 90) ? open.substring(0, 87) + "…>" : open;
	}

	/** 여는 태그 하나의 바뀜. 지움과 값 바꿈은 그 자리에서, 새 속성은 마지막 속성 뒤에 */
	private static final class Edit {

		private final String text;

		private final Tag tag;

		private final List<Attr> removed = new ArrayList<>();

		private final List<String[]> replaced = new ArrayList<>();

		private final List<String[]> added = new ArrayList<>();

		final List<String> changes = new ArrayList<>();

		Edit(String text, Tag tag) {
			this.text = text;
			this.tag = tag;
		}

		void remove(Attr a) {
			if (!this.removed.contains(a)) {
				this.removed.add(a);
				this.changes.add("뗌 " + a.name() + ((a.value() != null) ? "=\"" + a.value() + "\"" : ""));
			}
		}

		void set(String name, String value) {
			Attr existing = this.tag.attr(name);
			if (existing != null && !this.removed.contains(existing)) {
				if (java.util.Objects.equals(existing.value(), value)) {
					return;
				}
				this.replaced.add(new String[] { Integer.toString(existing.start()), Integer.toString(existing.end()), name,
						value });
				this.changes.add("바꿈 " + name + ": \"" + existing.value() + "\" → \"" + value + "\"");
				return;
			}
			this.added.add(new String[] { name, value });
			this.changes.add(name + ((value != null) ? "=\"" + value + "\"" : ""));
		}

		String apply() {
			StringBuilder out = new StringBuilder(this.text);
			int insert = this.tag.close();
			while (insert > this.tag.start() && Character.isWhitespace(out.charAt(insert - 1))) {
				insert--;
			}
			StringBuilder addition = new StringBuilder();
			for (String[] a : this.added) {
				addition.append(' ').append(attribute(a[0], a[1]));
			}
			// 뒤에서부터 고쳐 앞의 위치가 바뀌지 않게 한다
			List<Object[]> edits = new ArrayList<>();
			edits.add(new Object[] { insert, insert, addition.toString() });
			for (String[] r : this.replaced) {
				edits.add(new Object[] { Integer.parseInt(r[0]), Integer.parseInt(r[1]), attribute(r[2], r[3]) });
			}
			for (Attr a : this.removed) {
				int from = a.start();
				while (from > this.tag.start() && Character.isWhitespace(out.charAt(from - 1))) {
					from--;
				}
				edits.add(new Object[] { from, a.end(), "" });
			}
			edits.sort(Comparator.comparingInt((Object[] e) -> (Integer) e[0]).reversed());
			for (Object[] e : edits) {
				out.replace((Integer) e[0], (Integer) e[1], (String) e[2]);
			}
			return out.toString();
		}

		private static String attribute(String name, String value) {
			return (value == null) ? name : name + "=\"" + value.replace("\"", "&quot;") + "\"";
		}
	}
}
