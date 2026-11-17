package io.salvia95.cozy.cli.internal.behavior;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 프레임워크 컴포넌트의 명세 {@code cozy/behaviors.yml}(cozy-ui). 컴포넌트마다 목적, 받는 {@code data-*}, 템플릿이 부르는 메서드를 적는다.
 * 메서드 이름 뒤의 {@code (이름)}은 글자 인자 하나를 받는다는 뜻이다({@code toggle('filter')}).
 */
public record FrameworkBehaviors(Map<String, Component> components) {

	private static final Pattern METHOD = Pattern.compile("([a-z][A-Za-z0-9]*)(\\(([^()]+)\\))?");

	/**
	 * @param argument 글자 인자 하나를 받으면 그 인자의 이름, 아니면 {@code null}
	 */
	public record Method(String name, String argument, String description) {

		/** 문서에 보일 모양: {@code toggle(이름)}, {@code copy()} */
		public String signature() {
			return this.name + "(" + ((this.argument != null) ? this.argument : "") + ")";
		}
	}

	/** @param data {@code data-} 뒤의 이름 → 뜻 */
	public record Component(String name, String purpose, Map<String, String> data, Map<String, Method> methods) {
	}

	public static FrameworkBehaviors read(String text, String file) {
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
		if (!(root instanceof Map<?, ?> top) || !(top.get("components") instanceof Map<?, ?> list)) {
			throw new CliException(file + ": 맨 위에 components가 있어야 합니다");
		}
		for (Map.Entry<?, ?> entry : list.entrySet()) {
			String name = String.valueOf(entry.getKey());
			if (!(entry.getValue() instanceof Map<?, ?> body) || !(body.get("purpose") instanceof String purpose)) {
				throw new CliException(file + " 컴포넌트 " + name + ": purpose가 있어야 합니다");
			}
			Map<String, String> data = new LinkedHashMap<>();
			if (body.get("data") instanceof Map<?, ?> d) {
				d.forEach((k, v) -> data.put(String.valueOf(k), String.valueOf(v)));
			}
			Map<String, Method> methods = new LinkedHashMap<>();
			if (body.get("methods") instanceof Map<?, ?> m) {
				for (Map.Entry<?, ?> e : m.entrySet()) {
					Matcher method = METHOD.matcher(String.valueOf(e.getKey()));
					if (!method.matches()) {
						throw new CliException(file + " 컴포넌트 " + name + ": 메서드 " + e.getKey() + "는 이름이나 이름(인자) 모양입니다");
					}
					methods.put(method.group(1), new Method(method.group(1), method.group(3), String.valueOf(e.getValue())));
				}
			}
			components.put(name, new Component(name, purpose, data, methods));
		}
		return new FrameworkBehaviors(components);
	}

	/** docs/conventions/ui.md의 "프레임워크 컴포넌트" 표 (머리 포함) */
	public String table() {
		List<String> rows = new ArrayList<>();
		rows.add("| 이름 | 받는 `data-*` | 메서드 | 동작 |");
		rows.add("| --- | --- | --- | --- |");
		for (Component c : this.components.values()) {
			List<String> data = new ArrayList<>();
			c.data().forEach((name, meaning) -> data.add("`data-" + name + "`: " + meaning));
			List<String> methods = new ArrayList<>();
			c.methods().values().forEach((m) -> methods.add("`" + m.signature() + "`: " + m.description()));
			rows.add("| `" + c.name() + "` | " + (data.isEmpty() ? "없음" : String.join("<br>", data)) + " | "
					+ String.join("<br>", methods) + " | " + c.purpose() + " |");
		}
		return String.join("\n", rows) + "\n";
	}
}
