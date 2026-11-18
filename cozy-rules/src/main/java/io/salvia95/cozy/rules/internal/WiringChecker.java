package io.salvia95.cozy.rules.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.rules.ComponentContract;
import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.ScriptFile;
import io.salvia95.cozy.rules.ServerEvents;
import io.salvia95.cozy.rules.Severity;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.Violation;
import io.salvia95.cozy.rules.internal.ParsedTemplate.AlpineAttribute;
import io.salvia95.cozy.rules.internal.ParsedTemplate.Problem;

/**
 * C13: 동작 명세의 컴포넌트가 화면에 이어졌는가. 동작 명세에서 만든 JS의 {@code cozy:component} 줄(동작, 내고 받는 화면 이벤트)과 앱 템플릿, 서버의
 * {@code HX-Trigger}를 맞춘다.
 *
 * <ul>
 * <li>컴포넌트를 어느 앱 템플릿에도 붙이지 않았다({@code x-data})</li>
 * <li>동작을 부르는 요소가 없고 받는 화면 이벤트에도 잇지 않았다</li>
 * <li>내는 화면 이벤트를 받는 곳이 없다(다른 컴포넌트의 listens, {@code hx-trigger}, {@code x-on:모듈:이름})</li>
 * <li>받는 화면 이벤트를 내는 곳이 없다(다른 컴포넌트의 emit, 서버의 {@code HX-Trigger})</li>
 * </ul>
 */
public final class WiringChecker {

	private static final String RULE = "C13";

	private static final Pattern STRING = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"");

	private static final Pattern WORD = Pattern.compile("(?<![.$\\w])[A-Za-z_$][A-Za-z0-9_$]*");

	private record Component(String module, String name, ComponentContract contract) {

		/** 명세의 이름 ({@code bodyCount}) */
		String key() {
			String rest = this.name.substring(this.module.length());
			return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
		}

		/** 명령줄의 이름 ({@code body-count}) */
		String kebab() {
			return key().replaceAll("([A-Z])", "-$1").toLowerCase(java.util.Locale.ROOT);
		}
	}

	private WiringChecker() {
	}

	/**
	 * @param main 앱의 {@code src/main}
	 * @param displayPrefix 위치 앞에 붙일 경로 ({@code src/main/})
	 */
	public static List<Violation> check(List<TemplateFile> templates, List<ScriptFile> scripts, Path main, String displayPrefix) {
		List<Component> components = new ArrayList<>();
		for (ScriptFile script : scripts) {
			if (script.origin() != Origin.APP) {
				continue;
			}
			int slash = script.path().indexOf('/');
			String module = (slash > 0) ? script.path().substring(0, slash) : null;
			if (module == null || !script.path().equals(module + "/" + module + ".js")) {
				continue;
			}
			for (ComponentContract contract : ComponentContract.parse(script.content()).values()) {
				if (contract.wiring() != null && contract.name().startsWith(module) && contract.name().length() > module.length()) {
					components.add(new Component(module, contract.name(), contract));
				}
			}
		}
		if (components.isEmpty()) {
			return List.of();
		}
		Set<String> attached = new HashSet<>();
		Map<String, Set<String>> called = new HashMap<>();
		Set<String> received = new HashSet<>();
		for (TemplateFile file : templates) {
			if (file.origin() != Origin.APP) {
				continue;
			}
			ParsedTemplate parsed = TemplateScanner.scan(file);
			for (AlpineAttribute attribute : parsed.alpine) {
				String name = attribute.name();
				if (name.equals("x-data")) {
					attached.add(attribute.value().strip());
					continue;
				}
				if (name.startsWith("x-on:") || name.startsWith("@")) {
					String event = name.substring(name.startsWith("@") ? 1 : "x-on:".length());
					int dot = event.indexOf('.');
					received.add((dot < 0) ? event : event.substring(0, dot));
				}
				if (attribute.scope() != null && (name.startsWith("x-") || name.startsWith("@") || name.startsWith(":"))) {
					Matcher word = WORD.matcher(STRING.matcher(attribute.value()).replaceAll("''"));
					Set<String> names = called.computeIfAbsent(attribute.scope(), (k) -> new HashSet<>());
					while (word.find()) {
						names.add(word.group());
					}
				}
			}
			for (Problem trigger : parsed.triggers) {
				for (String part : trigger.text().split(",")) {
					// th:hx-trigger의 글자 식('…', |…|)도 같은 이름이다
					String first = part.strip().replaceAll("^['|]+", "").split("\\s+")[0];
					received.add(first.replaceFirst("\\[.*$", "").replaceAll("['|]+$", ""));
				}
			}
		}
		Set<String> emitted = new HashSet<>(ServerEvents.scan(main.resolve("java"), displayPrefix + "java/").keySet());
		for (Component c : components) {
			emitted.addAll(c.contract().wiring().emits());
			received.addAll(c.contract().wiring().listens().keySet());
		}
		List<Violation> violations = new ArrayList<>();
		Map<String, List<String>> specs = new HashMap<>();
		for (Component c : components) {
			String spec = displayPrefix + "resources/behaviors/" + c.module() + ".yml";
			List<String> lines = specs.computeIfAbsent(c.module(), (m) -> lines(main.resolve("resources/behaviors/" + m + ".yml")));
			int line = lines.indexOf("  " + c.key() + ":") + 1;
			String remove = "cozy remove behavior " + c.module() + " " + c.kebab();
			ComponentContract.Wiring wiring = c.contract().wiring();
			if (!attached.contains(c.name())) {
				violations.add(error(spec, line, c.name(), "컴포넌트 " + c.name() + "을 어느 템플릿에도 붙이지 않았습니다. 선언과 붙이기는 한 작업으로 합니다. "
						+ "화면의 요소에 cozy attach " + c.name() + " --at <템플릿>:<줄>로 붙이거나, 쓰지 않으면 " + remove + "로 지웁니다"));
			}
			else {
				Set<String> names = called.getOrDefault(c.name(), Set.of());
				for (String action : wiring.actions()) {
					if (!names.contains(action) && !wiring.listens().containsValue(action)) {
						violations.add(error(spec, line, c.name() + "." + action, "동작 " + action + "을 부르는 요소가 없습니다. 요소의 이벤트에 잇거나(cozy attach "
								+ c.name() + " --at <템플릿>:<줄> --on <이벤트>:" + action + "), 받는 화면 이벤트에 잇거나(cozy add behavior " + c.module() + " "
								+ c.kebab() + " --listen " + action + ":<모듈>:<이벤트>), 쓰지 않으면 지웁니다(" + remove + " --action " + action + ")"));
					}
				}
			}
			for (String event : sorted(wiring.emits())) {
				if (!received.contains(event)) {
					violations.add(error(spec, line, c.name() + " → " + event, "화면 이벤트 " + event + "를 받는 곳이 없습니다. 받을 컴포넌트의 동작에 잇거나"
							+ "(cozy add behavior <모듈> <동작> --listen <동작>:" + event + "), htmx 요청이 받게 하거나(hx-trigger=\"" + event
							+ " from:window\"), 내지 않으면 그 emit 단계를 지웁니다(cozy add behavior " + c.module() + " " + c.kebab() + " --action …)"));
				}
			}
			for (String event : sorted(wiring.listens().keySet())) {
				if (!emitted.contains(event)) {
					violations.add(error(spec, line, c.name() + " ← " + event, "화면 이벤트 " + event + "를 내는 곳이 없습니다. 다른 컴포넌트의 동작이 emit('"
							+ event + "')로 내거나, 서버가 내면 컨트롤러가 응답 머리 HX-Trigger의 값에 이름을 글자 그대로(\"" + event
							+ "\") 적습니다. 이어 붙여 만든 이름은 찾지 못합니다. 받지 않으면 " + remove + " --listen " + event + "로 지웁니다"));
				}
			}
		}
		return violations;
	}

	private static List<String> sorted(Set<String> names) {
		return names.stream().sorted().toList();
	}

	private static List<String> lines(Path file) {
		try {
			return Files.isRegularFile(file) ? List.of(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\\R", -1))
					: List.of();
		}
		catch (IOException ex) {
			return List.of();
		}
	}

	private static Violation error(String location, int line, String reference, String message) {
		return new Violation(location, line, RULE, Severity.ERROR, reference, message);
	}
}
