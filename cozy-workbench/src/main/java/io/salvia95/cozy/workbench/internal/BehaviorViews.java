package io.salvia95.cozy.workbench.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.FrameworkBehaviors;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine.Attribute;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine.Behavior;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine.Binding;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine.Entry;

/**
 * 속성 표의 "화면 안의 동작" 구획에 보일 것: 요소마다 붙은 컴포넌트와 그 명세, 이벤트, 묶인 값, htmx 요청. 앱 컴포넌트의 명세는
 * {@code cozy describe}의 {@code behaviors[]}, 프레임워크 컴포넌트({@code cozy…})의 명세는 cozy-ui의 {@code cozy/behaviors.yml}이다.
 */
final class BehaviorViews {

	private BehaviorViews() {
	}

	/** 속성 표에서 더하고 뗄 수 있는 것 ({@code cozy attach}의 옵션) */
	static final List<String> OPTIONS = List.of("on", "text", "show", "bind", "model", "cloak", "data");

	/** 명세의 구획 하나 ({@code 상태}, {@code 계산}, …) */
	record Section(String title, List<Entry> entries) {
	}

	/**
	 * 컴포넌트 하나와 그 명세.
	 *
	 * @param handlers 이벤트가 부를 수 있는 이름 (동작, 메서드)
	 * @param values 묶을 수 있는 이름 (상태와 계산, 메서드)
	 */
	record Component(String name, String purpose, boolean framework, List<Section> sections, List<String> handlers,
			List<String> values) {

		/** 붙이기 폼 아래의 안내 */
		public String hint() {
			return "이벤트가 부를 이름: " + (this.handlers.isEmpty() ? "없음" : String.join(", ", this.handlers)) + " · 묶을 이름: "
					+ (this.values.isEmpty() ? "없음" : String.join(", ", this.values));
		}
	}

	/**
	 * 요소에 붙은 것 한 줄.
	 *
	 * @param label 사람이 읽는 종류 ({@code 이벤트}, {@code 글자})
	 * @param text 보일 글 ({@code input → count})
	 * @param option 떼는 {@code cozy attach}의 옵션. 이 명령으로 뗄 수 없는 속성이면 {@code null}
	 * @param key 옵션의 이름 부분 ({@code input}, {@code disabled}, {@code limit}). 없으면 빈 문자열
	 * @param value 옵션의 값 부분 ({@code count}). 없으면 빈 문자열
	 */
	record Row(String label, String text, String option, String key, String value) {

		public boolean detachable() {
			return this.option != null;
		}
	}

	/**
	 * 속성 표에 보일 요소 하나.
	 *
	 * @param component 감싸는 컴포넌트의 명세. 모르면 {@code null}
	 * @param editable 속성 표에서 {@code cozy attach}로 더하고 뗄 수 있는가 (앱 템플릿이고 컴포넌트의 명세가 있음)
	 */
	record Element(Binding binding, Component component, List<Row> rows, List<Row> htmx, boolean editable) {

		/** {@code templates/memo/internal/fields.html:7} */
		public String location() {
			return "templates/" + this.binding.template() + ".html:" + this.binding.line();
		}

		/** {@code cozy attach}의 {@code --at} */
		public String at() {
			return this.binding.template() + ":" + this.binding.line();
		}
	}

	/** cozy-ui의 프레임워크 컴포넌트 명세. 읽을 수 없으면 빈 명세 (요소는 명세 없이 보인다) */
	static FrameworkBehaviors framework() {
		ClassLoader loader = BehaviorViews.class.getClassLoader();
		try (InputStream in = loader.getResourceAsStream("cozy/behaviors.yml")) {
			if (in == null) {
				return new FrameworkBehaviors(Map.of());
			}
			return FrameworkBehaviors.read(new String(in.readAllBytes(), StandardCharsets.UTF_8), "cozy/behaviors.yml");
		}
		catch (IOException | CliException ex) {
			return new FrameworkBehaviors(Map.of());
		}
	}

	/** 이름 → 명세. 앱 컴포넌트와 프레임워크 컴포넌트 */
	static Map<String, Component> components(List<Behavior> behaviors, FrameworkBehaviors framework) {
		Map<String, Component> components = new LinkedHashMap<>();
		for (Behavior b : behaviors) {
			List<String> values = new ArrayList<>();
			b.state().forEach((e) -> values.add(e.name()));
			b.computed().forEach((e) -> values.add(e.name()));
			List<Entry> emits = b.emits().stream().map((e) -> new Entry(e.name(), e.text() + " 동작 뒤")).toList();
			List<Entry> listens = b.listens().stream().map((e) -> new Entry(e.name(), "→ " + e.text())).toList();
			components.put(b.name(), new Component(b.name(), b.purpose(), false,
					List.of(new Section("상태", b.state()), new Section("계산", b.computed()), new Section("동작", b.actions()),
							new Section("받는 화면 이벤트", listens), new Section("내는 화면 이벤트", emits)),
					b.actions().stream().map(Entry::name).toList(), List.copyOf(values)));
		}
		for (FrameworkBehaviors.Component c : framework.components().values()) {
			List<Entry> data = new ArrayList<>();
			c.data().forEach((name, meaning) -> data.add(new Entry("data-" + name, meaning)));
			List<Entry> methods = new ArrayList<>();
			c.methods().values().forEach((m) -> methods.add(new Entry(m.signature(), m.description())));
			// 템플릿에 적는 모양: copy, toggle('이름')
			List<String> names = c.methods()
				.values()
				.stream()
				.map((m) -> (m.argument() != null) ? m.name() + "('" + m.argument() + "')" : m.name())
				.toList();
			components.put(c.name(), new Component(c.name(), c.purpose(), true,
					List.of(new Section("받는 data-*", data), new Section("메서드", methods)), names, names));
		}
		return components;
	}

	static List<Element> elements(List<Binding> bindings, Map<String, Component> components) {
		List<Element> elements = new ArrayList<>();
		for (Binding b : bindings) {
			List<Row> rows = new ArrayList<>();
			for (Attribute a : b.alpine()) {
				Row row = row(a);
				if (row != null) {
					rows.add(row);
				}
			}
			List<Row> htmx = b.htmx()
				.stream()
				.map((a) -> new Row("htmx", a.name() + ((a.value() != null) ? " = " + a.value() : ""), null, "", ""))
				.toList();
			Component component = (b.component() != null) ? components.get(b.component()) : null;
			boolean editable = component != null && !b.template().startsWith("cozy/");
			elements.add(new Element(b, component, List.copyOf(rows), htmx, editable));
		}
		return elements;
	}

	/** 속성 하나를 한 줄로. {@code x-data}는 컴포넌트 줄이 따로 보이므로 뺀다 */
	static Row row(Attribute a) {
		String name = a.name();
		String value = (a.value() != null) ? a.value() : "";
		if (name.equals("x-data")) {
			return null;
		}
		if (name.startsWith("x-on:")) {
			String event = name.substring("x-on:".length());
			return new Row("이벤트", event + " → " + value, "on", event, value);
		}
		if (name.startsWith("x-bind:")) {
			String attribute = name.substring("x-bind:".length());
			return new Row("속성", attribute + " = " + value, "bind", attribute, value);
		}
		return switch (name) {
			case "x-text" -> new Row("글자", value, "text", "", value);
			case "x-show" -> new Row("보임", value, "show", "", value);
			case "x-model" -> new Row("입력값", value, "model", "", value);
			case "x-cloak" -> new Row("준비 전 숨김", "x-cloak", "cloak", "", "");
			default -> {
				if (name.startsWith("data-") || name.startsWith("th:data-")) {
					String key = name.substring(name.indexOf("data-") + "data-".length());
					yield new Row("넘기는 값", name + " = " + value, "data", key, value);
				}
				// 줄인 표기(@click, :disabled)와 이 명령이 쓰지 않는 속성(x-init, x-ref …)은 보이기만 한다
				yield new Row(name, value.isEmpty() ? name : value, null, "", "");
			}
		};
	}

	/**
	 * 구조 트리의 줄마다 그 줄에 보이는 요소. 요소의 화면 줄({@code screenLine})을 담는 가장 안쪽 줄에 붙인다. 어느 줄에도 담기지 않는 요소는
	 * 화면의 속성에서만 보인다.
	 */
	static List<ScreenTree.Item> attach(List<ScreenTree.Item> items, List<Element> elements) {
		List<List<Element>> found = new ArrayList<>();
		items.forEach((i) -> found.add(new ArrayList<>()));
		for (Element e : elements) {
			int line = e.binding().screenLine();
			int at = -1;
			for (int i = 0; i < items.size(); i++) {
				if (items.get(i).line() <= line && line <= items.get(i).endLine()) {
					at = i;
				}
			}
			if (at >= 0) {
				found.get(at).add(e);
			}
		}
		List<ScreenTree.Item> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			result.add(items.get(i).withBehaviors(List.copyOf(found.get(i))));
		}
		return result;
	}
}
