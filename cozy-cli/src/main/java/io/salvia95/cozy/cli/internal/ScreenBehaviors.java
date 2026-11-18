package io.salvia95.cozy.cli.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.salvia95.cozy.rules.ServerEvents;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec;
import io.salvia95.cozy.cli.internal.behavior.Expr;
import io.salvia95.cozy.cli.internal.behavior.TemplateTags;
import io.salvia95.cozy.cli.internal.behavior.TemplateTags.Attr;
import io.salvia95.cozy.cli.internal.behavior.TemplateTags.Tag;

/**
 * describe의 화면 안 동작: 템플릿 요소마다 붙은 Alpine·htmx 속성, 모듈의 동작 명세와 붙인 곳, 화면 이벤트를 내는 곳과 받는 곳. 소스의 글자를
 * 읽기만 하고 고치지 않는다.
 */
final class ScreenBehaviors {

	private ScreenBehaviors() {
	}

	static final String TEMPLATES = "src/main/resources/templates";

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

	/** 화면 이벤트 이름 ({@code memo:archived}) */
	private static final Pattern EVENT = ServerEvents.EVENT;

	/** 읽은 템플릿: 여는 태그들 */
	record Parsed(String template, List<Tag> tags) {
	}

	static Parsed parse(String template, String content) {
		return new Parsed(template, TemplateTags.read(content));
	}

	static boolean alpine(String name) {
		return name.startsWith("x-") || name.startsWith("@") || name.startsWith(":");
	}

	static boolean htmx(String name) {
		return name.startsWith("hx-") || name.startsWith("th:hx-") || name.startsWith("data-hx-");
	}

	private static boolean data(String name) {
		return name.startsWith("data-") || name.startsWith("th:data-");
	}

	/** 가장 가까운 {@code x-data}(이 요소부터 위로)의 이름. 이름이 아니거나 없으면 {@code null} */
	static String component(List<Tag> tags, Tag tag) {
		for (int i = tag.index(); i >= 0; i = tags.get(i).parent()) {
			Attr data = tags.get(i).attr("x-data");
			if (data != null) {
				String value = (data.value() != null) ? data.value().strip() : "";
				return IDENTIFIER.matcher(value).matches() ? value : null;
			}
		}
		return null;
	}

	/** 사람이 알아볼 여는 태그: 이름, id, class ({@code <p class="form-hint">}) */
	static String opening(Tag tag) {
		StringBuilder out = new StringBuilder("<").append(tag.name());
		for (String name : List.of("id", "class")) {
			Attr a = tag.attr(name);
			if (a != null && a.value() != null && !a.value().isBlank()) {
				out.append(' ').append(name).append("=\"").append(a.value().strip().replaceAll("\\s+", " ")).append('"');
			}
		}
		return out.append('>').toString();
	}

	/**
	 * 요소 하나에 붙은 것. Alpine 속성({@code x-*}, 컴포넌트의 자리면 {@code data-*}도)이나 htmx 속성이 없으면 {@code null}.
	 *
	 * @return {@code template}, {@code line}, {@code element}, {@code component}, {@code root}, {@code alpine[]}, {@code htmx[]}
	 */
	static Map<String, Object> element(Parsed parsed, Tag tag) {
		boolean root = tag.attr("x-data") != null;
		List<Object> alpine = new ArrayList<>();
		List<Object> htmx = new ArrayList<>();
		for (Attr a : tag.attrs()) {
			if (alpine(a.name()) || (root && data(a.name()))) {
				alpine.add(Describe.ordered("attribute", a.name(), "value", a.value()));
			}
			else if (htmx(a.name())) {
				htmx.add(Describe.ordered("attribute", a.name(), "value", a.value()));
			}
		}
		if (alpine.isEmpty() && htmx.isEmpty()) {
			return null;
		}
		Map<String, Object> element = new LinkedHashMap<>();
		element.put("template", parsed.template());
		element.put("line", tag.line());
		element.put("element", opening(tag));
		element.put("component", component(parsed.tags(), tag));
		element.put("root", root);
		element.put("alpine", alpine);
		element.put("htmx", htmx);
		return element;
	}

	/** 모듈 템플릿({@code templates/{모듈}/**.html})을 경로 순서로 */
	static List<Parsed> moduleTemplates(Path project, String module) {
		return templates(project.resolve(TEMPLATES), module + "/");
	}

	/** 앱의 모든 템플릿을 경로 순서로 */
	static List<Parsed> appTemplates(Path project) {
		return templates(project.resolve(TEMPLATES), "");
	}

	private static List<Parsed> templates(Path root, String prefix) {
		Path dir = root.resolve(prefix);
		List<Parsed> parsed = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return parsed;
		}
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path file : files.filter((f) -> f.toString().endsWith(".html")).sorted().toList()) {
				String relative = root.relativize(file).toString().replace('\\', '/');
				parsed.add(parse(relative.substring(0, relative.length() - ".html".length()),
						Files.readString(file, StandardCharsets.UTF_8)));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return parsed;
	}

	// ── 동작 명세 ───────────────────────────────────────────────

	/**
	 * 모듈의 동작 명세 {@code behaviors/{모듈}.yml}의 컴포넌트와 붙인 곳. 명세를 읽을 수 없으면 비우고 그 이유를 {@code problems}에 적는다.
	 */
	static List<Object> behaviors(Path project, String module, List<Parsed> templates, List<String> problems) {
		String file = "src/main/resources/behaviors/" + module + ".yml";
		Path path = project.resolve(file);
		List<Object> result = new ArrayList<>();
		if (!Files.isRegularFile(path)) {
			return result;
		}
		BehaviorSpec spec;
		try {
			spec = BehaviorSpec.read(module, Files.readString(path, StandardCharsets.UTF_8), file);
		}
		catch (CliException ex) {
			problems.add(ex.getMessage());
			return result;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		for (BehaviorSpec.Component c : spec.components().values()) {
			String name = c.name(module);
			Map<String, Object> b = new LinkedHashMap<>();
			b.put("name", name);
			b.put("purpose", c.purpose());
			List<Object> state = new ArrayList<>();
			c.state().forEach((n, s) -> state.add(Describe.ordered("name", n, "type", s.type().word(), "initial", s.initial(), "from", s.data())));
			b.put("state", state);
			List<Object> computed = new ArrayList<>();
			c.computed().forEach((n, e) -> computed.add(Describe.ordered("name", n, "expression", e.print())));
			b.put("computed", computed);
			List<Object> actions = new ArrayList<>();
			List<Object> emits = new ArrayList<>();
			c.actions().forEach((n, steps) -> {
				actions.add(Describe.ordered("name", n, "steps", steps.stream().map(Expr::print).toList()));
				Set<String> sent = new LinkedHashSet<>();
				steps.forEach((step) -> emitted(step, sent));
				sent.forEach((event) -> emits.add(Describe.ordered("event", event, "action", n)));
			});
			b.put("actions", actions);
			b.put("emits", emits);
			List<Object> listens = new ArrayList<>();
			c.listens().forEach((event, action) -> listens.add(Describe.ordered("event", event, "action", action)));
			b.put("listens", listens);
			List<Object> attached = new ArrayList<>();
			for (Parsed t : templates) {
				for (Tag tag : t.tags()) {
					Map<String, Object> element = element(t, tag);
					if (element != null && name.equals(element.get("component")) && !((List<?>) element.get("alpine")).isEmpty()) {
						attached.add(element);
					}
				}
			}
			b.put("attached", attached);
			result.add(b);
		}
		return result;
	}

	/** 단계 안의 {@code emit('모듈:이름', …)} (when, within 안도) */
	private static void emitted(Expr expr, Set<String> sent) {
		if (expr instanceof Expr.Call call) {
			if (call.function().equals("emit") && !call.args().isEmpty() && call.args().getFirst() instanceof Expr.Text text) {
				sent.add(text.value());
			}
			call.args().forEach((arg) -> emitted(arg, sent));
		}
	}

	// ── 화면 이벤트 ─────────────────────────────────────────────

	/**
	 * 화면 이벤트({@code 모듈:이름})마다 내는 곳과 받는 곳. 이름의 앞이 앱 모듈인 것만 싣는다.
	 * <ul>
	 * <li>내는 곳: 동작 명세의 {@code emit}, 서버의 {@code HX-Trigger}(그 머리를 쓰는 Java 파일의 글자에 있는 이름)</li>
	 * <li>받는 곳: 동작 명세의 {@code listens}, 템플릿의 {@code hx-trigger}, {@code x-on:모듈:이름}</li>
	 * </ul>
	 */
	@SuppressWarnings("unchecked")
	static List<Object> uiEvents(AppProject project, List<Object> modules, List<Parsed> templates) {
		Set<String> moduleNames = project.modules().keySet();
		TreeMap<String, List<Object>> emitted = new TreeMap<>();
		TreeMap<String, List<Object>> received = new TreeMap<>();
		for (Object item : modules) {
			Map<String, Object> m = (Map<String, Object>) item;
			for (Object o : (List<Object>) m.get("behaviors")) {
				Map<String, Object> b = (Map<String, Object>) o;
				for (Object e : (List<Object>) b.get("emits")) {
					Map<String, Object> emit = (Map<String, Object>) e;
					add(emitted, moduleNames, (String) emit.get("event"),
							Describe.ordered("kind", "behavior", "module", m.get("name"), "component", b.get("name"), "action", emit.get("action")));
				}
				for (Object l : (List<Object>) b.get("listens")) {
					Map<String, Object> listen = (Map<String, Object>) l;
					add(received, moduleNames, (String) listen.get("event"), Describe.ordered("kind", "behavior", "module", m.get("name"),
							"component", b.get("name"), "action", listen.get("action")));
				}
			}
		}
		for (JavaSources.Unit unit : project.sources().units()) {
			String source;
			try {
				source = unit.tree().getSourceFile().getCharContent(true).toString();
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			Set<String> names = ServerEvents.events(source);
			names.forEach((name) -> add(emitted, moduleNames, name, Describe.ordered("kind", "server", "file", unit.relative())));
		}
		for (Parsed t : templates) {
			for (Tag tag : t.tags()) {
				for (Attr a : tag.attrs()) {
					if (a.name().endsWith("hx-trigger") && htmx(a.name()) && a.value() != null) {
						for (String part : a.value().split(",")) {
							String first = part.strip().replaceAll("^['|]+", "").split("\\s+")[0].replaceFirst("\\[.*$", "")
								.replaceAll("['|]+$", "");
							add(received, moduleNames, first, Describe.ordered("kind", "htmx", "template", t.template(), "line", tag.line()));
						}
					}
					else if (a.name().startsWith("x-on:") || a.name().startsWith("@")) {
						String event = a.name().substring(a.name().startsWith("@") ? 1 : "x-on:".length());
						int dot = event.indexOf('.');
						add(received, moduleNames, (dot < 0) ? event : event.substring(0, dot),
								Describe.ordered("kind", "alpine", "template", t.template(), "line", tag.line()));
					}
				}
			}
		}
		Set<String> all = new java.util.TreeSet<>();
		all.addAll(emitted.keySet());
		all.addAll(received.keySet());
		List<Object> events = new ArrayList<>();
		for (String event : all) {
			events.add(Describe.ordered("event", event, "module", event.substring(0, event.indexOf(':')), "emittedBy",
					emitted.getOrDefault(event, List.of()), "receivedBy", received.getOrDefault(event, List.of())));
		}
		return events;
	}

	private static void add(Map<String, List<Object>> map, Set<String> modules, String event, Map<String, Object> where) {
		Matcher m = EVENT.matcher(event);
		if (!m.matches() || !modules.contains(m.group(1))) {
			return;
		}
		List<Object> list = map.computeIfAbsent(event, (k) -> new ArrayList<>());
		if (!list.contains(where)) {
			list.add(where);
		}
	}
}
