package io.salvia95.cozy.cli.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import javax.lang.model.element.Modifier;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import io.salvia95.cozy.rules.JavaType;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.SlotRules;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.TemplateOutline;
import io.salvia95.cozy.rules.Violation;

/**
 * {@code cozy describe}: 앱의 지도. 작은 모델이 파일을 다 읽지 않고도 모듈, 의존, 계약(spi), 이벤트, 화면, 공개 fragment, 남은 빈칸을 알
 * 수 있게 한다. 소스에서 읽고, 알아볼 수 없는 것은 추측하지 않고 {@code problems}에 적는다.
 */
public final class Describe {

	private Describe() {
	}

	/** describe 결과. JSON과 글 출력이 같은 맵을 쓴다 */
	public static Map<String, Object> model(AppProject project, List<AppResources.ManifestEntry> appManifest) {
		Map<String, Object> app = new LinkedHashMap<>();
		app.put("basePackage", project.basePackage());
		app.put("mainClass", project.mainClass().map(JavaSources.SourceType::simpleName).orElse(null));
		app.put("cozyVersion", project.cozyVersion());
		app.put("starters", List.of("cozy-starter-runtime", "cozy-starter-sqlite", "cozy-starter-events", "cozy-ui")
			.stream()
			.filter(project::uses)
			.toList());

		List<AppResources.Spec> specs = AppResources.specs(project.dir());
		List<String> behaviorProblems = new ArrayList<>();
		List<Object> modules = new ArrayList<>();
		for (AppProject.Module module : project.modules().values()) {
			modules.add(module(project, module, specs, appManifest, behaviorProblems));
		}
		List<Object> slots = new ArrayList<>();
		for (Violation slot : SlotRules.check(project.dir().resolve("src/main"), "src/main/")) {
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("file", slot.location());
			s.put("line", slot.line());
			s.put("slot", slot.reference().replace(SlotRules.MARKER, "").strip());
			s.put("what", slot.message());
			slots.add(s);
		}
		List<Object> problems = new ArrayList<>(project.problems().stream().map((v) -> (Object) v.format()).toList());
		problems.addAll(behaviorProblems);

		Map<String, Object> model = new LinkedHashMap<>();
		model.put("app", app);
		model.put("modules", modules);
		model.put("events", events(project, modules));
		model.put("uiEvents", ScreenBehaviors.uiEvents(project, modules, ScreenBehaviors.appTemplates(project.dir())));
		model.put("slots", slots);
		model.put("problems", problems);
		return model;
	}

	private static Map<String, Object> module(AppProject project, AppProject.Module module, List<AppResources.Spec> specs,
			List<AppResources.ManifestEntry> manifest, List<String> problems) {
		JavaSources sources = project.sources();
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", module.name());
		m.put("package", module.basePackage());
		m.put("dependsOn", module.allowedDependencies());
		List<Object> facades = new ArrayList<>();
		List<Object> finders = new ArrayList<>();
		List<Object> dtos = new ArrayList<>();
		List<Object> listeners = new ArrayList<>();
		List<Object> controllers = new ArrayList<>();
		Map<String, JavaType> javaTypes = new LinkedHashMap<>();
		sources.javaTypes().forEach((t) -> javaTypes.put(t.name(), t));
		for (JavaSources.SourceType type : sources.types().values()) {
			if (!type.name().equals(type.topLevel()) || !project.moduleOf(type.pkg()).map(module::equals).orElse(false)) {
				continue;
			}
			Optional<Layer> layer = Layer.of(module.basePackage(), type.pkg());
			JavaType javaType = javaTypes.get(type.name());
			if (layer.isPresent() && layer.get() == Layer.SPI && type.isInterface()) {
				Map<String, Object> contract = new LinkedHashMap<>();
				contract.put("type", type.simpleName());
				contract.put("methods", methods(type.tree()));
				(type.simpleName().endsWith("Facade") ? facades : finders).add(contract);
			}
			else if (layer.isPresent() && layer.get() == Layer.SPI_DTO) {
				dtos.add(ordered("type", type.simpleName(), "fields", fields(type.tree())));
			}
			if (javaType.has(JavaType.Trait.LISTENER)) {
				listeners.addAll(listenerMethods(project, type));
			}
			if (javaType.has(JavaType.Trait.CONTROLLER)) {
				controllers.add(type.simpleName());
			}
		}
		m.put("facades", facades);
		m.put("finders", finders);
		m.put("dtos", dtos);
		m.put("listeners", listeners);
		m.put("controllers", controllers);
		m.put("components", registeredComponents(project, module));
		m.put("behaviors", ScreenBehaviors.behaviors(project.dir(), module.name(), ScreenBehaviors.moduleTemplates(project.dir(), module.name()),
				problems));
		m.put("screens", screens(project, module, specs));
		m.put("fragments", manifest.stream()
			.filter((e) -> e.template().startsWith(module.name() + "/spi/"))
			.map((e) -> (Object) ordered("id", e.id(), "signature", e.signature()))
			.toList());
		return m;
	}

	/** 메서드 시그니처 ({@code long create(String title, String body, boolean pinned)}) */
	static List<String> methods(ClassTree type) {
		List<String> methods = new ArrayList<>();
		for (Tree member : type.getMembers()) {
			if (member instanceof MethodTree method && method.getReturnType() != null
					&& !method.getModifiers().getFlags().contains(Modifier.STATIC)) {
				List<String> params = method.getParameters().stream().map((p) -> p.getType() + " " + p.getName()).toList();
				methods.add(method.getReturnType() + " " + method.getName() + "(" + String.join(", ", params) + ")");
			}
		}
		return methods;
	}

	/** record의 구성 요소, 클래스면 인스턴스 필드 ({@code long id}) */
	public static List<String> fields(ClassTree type) {
		List<String> fields = new ArrayList<>();
		for (Tree member : type.getMembers()) {
			if (member instanceof VariableTree field && !field.getModifiers().getFlags().contains(Modifier.STATIC)) {
				fields.add(field.getType() + " " + field.getName());
			}
		}
		return fields;
	}

	/**
	 * 앱의 이벤트: 이벤트 계약 모듈({@code events/spi/{주인}/})과 옛 자리({@code {주인}/spi/event/})의 CozyEvent. 모듈 순서, 그다음 소스
	 * 순서다. 받는 곳은 모듈의 리스너 가운데 {@code on}이 {@code 주인.이벤트}인 것이다.
	 */
	@SuppressWarnings("unchecked")
	private static List<Object> events(AppProject project, List<Object> modules) {
		Map<String, List<Object>> receivers = new LinkedHashMap<>();
		for (Object item : modules) {
			Map<String, Object> m = (Map<String, Object>) item;
			for (Object l : (List<Object>) m.get("listeners")) {
				Map<String, Object> listener = (Map<String, Object>) l;
				receivers.computeIfAbsent(String.valueOf(listener.get("on")), (k) -> new ArrayList<>())
					.add(ordered("module", m.get("name"), "type", listener.get("type")));
			}
		}
		Map<String, JavaType> javaTypes = new LinkedHashMap<>();
		project.sources().javaTypes().forEach((t) -> javaTypes.put(t.name(), t));
		List<Object> events = new ArrayList<>();
		for (AppProject.Module owner : project.modules().values()) {
			String contracts = project.modules().containsKey(AppProject.EVENTS)
					? project.modules().get(AppProject.EVENTS).basePackage() + ".spi." + owner.name() : null;
			for (JavaSources.SourceType type : project.sources().types().values()) {
				JavaType javaType = javaTypes.get(type.name());
				boolean placed = type.pkg().equals(contracts)
						|| Layer.of(owner.basePackage(), type.pkg()).filter((l) -> l == Layer.SPI_EVENT).isPresent();
				if (!type.name().equals(type.topLevel()) || !placed || javaType == null || !javaType.has(JavaType.Trait.COZY_EVENT)
						|| !project.eventOwner(type.pkg()).map(owner::equals).orElse(false)) {
					continue;
				}
				Map<String, Object> event = new LinkedHashMap<>();
				event.put("type", type.simpleName());
				event.put("owner", owner.name());
				event.put("fields", fields(type.tree())
					.stream()
					.filter((f) -> !f.endsWith(" eventId") && !f.endsWith(" schemaVersion"))
					.toList());
				event.put("publishedIn", publishers(project, type));
				event.put("listeners", receivers.getOrDefault(owner.name() + "." + type.simpleName(), List.of()));
				events.add(event);
			}
		}
		return events;
	}

	private static List<String> publishers(AppProject project, JavaSources.SourceType event) {
		Pattern use = Pattern.compile("\\b" + Pattern.quote(event.simpleName()) + "\\s*\\.\\s*of\\s*\\(|\\bnew\\s+"
				+ Pattern.quote(event.simpleName()) + "\\s*\\(");
		List<String> files = new ArrayList<>();
		for (JavaSources.Unit unit : project.sources().units()) {
			if (unit.file().equals(event.unit().file())) {
				continue;
			}
			try {
				if (use.matcher(unit.tree().getSourceFile().getCharContent(true)).find()) {
					files.add(unit.relative());
				}
			}
			catch (IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			}
		}
		return files;
	}

	private static List<Object> listenerMethods(AppProject project, JavaSources.SourceType type) {
		List<Object> result = new ArrayList<>();
		for (Tree member : type.tree().getMembers()) {
			if (!(member instanceof MethodTree method)) {
				continue;
			}
			boolean listener = false;
			for (AnnotationTree a : method.getModifiers().getAnnotations()) {
				listener |= project.sources().refersTo(type.unit(), a, JavaSources.LISTENERS);
			}
			if (!listener) {
				continue;
			}
			Map<String, Object> l = new LinkedHashMap<>();
			l.put("type", type.simpleName());
			l.put("method", method.getName().toString());
			String on = null;
			if (method.getParameters().size() == 1) {
				String written = JavaSources.typeName(method.getParameters().getFirst().getType());
				on = project.sources()
					.resolve(type.unit(), written)
					.map((event) -> project.eventOwner(event.pkg()).map((em) -> em.name() + ".").orElse("")
							+ event.simpleName())
					.orElse(written);
			}
			l.put("on", on);
			result.add(l);
		}
		return result;
	}

	private static List<Object> screens(AppProject project, AppProject.Module module, List<AppResources.Spec> specs) {
		Path dir = project.dir().resolve("src/main/resources/templates/" + module.name() + "/api");
		List<Object> screens = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return screens;
		}
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path file : files.filter((f) -> f.toString().endsWith(".html")).sorted().toList()) {
				String relative = dir.relativize(file).toString().replace('\\', '/');
				String template = module.name() + "/api/" + relative.substring(0, relative.length() - ".html".length());
				Map<String, Object> screen = new LinkedHashMap<>();
				screen.put("template", template);
				AppResources.Spec spec = specs.stream().filter((s) -> template.equals(s.template())).findFirst().orElse(null);
				screen.put("spec", (spec != null) ? spec.file() : null);
				screen.put("states", (spec != null) ? spec.states() : List.of());
				List<Object> bindings = new ArrayList<>();
				screen.put("components", usedComponents(project.dir().resolve("src/main/resources/templates"), template, bindings));
				screen.put("bindings", bindings);
				screen.put("outline", outline(file, template));
				screens.add(screen);
			}
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
		return screens;
	}

	private static final java.util.regex.Pattern REGISTRATION = java.util.regex.Pattern
		.compile("Alpine\\s*\\.\\s*data\\s*\\(\\s*([\"'])([A-Za-z_$][\\w$]*)\\1");

	/**
	 * 화면이 쓰는 Alpine 컴포넌트({@code x-data}의 이름)와 쓴 곳. 화면 템플릿과, 거기서 넣는 앱 템플릿의 fragment를 따라가며 문서
	 * 순서로 모은다. fragment 이름이 있으면 그 선언의 줄 범위만, 없으면 템플릿 전체를 본다. 이미 본 fragment는 다시 보지 않는다.
	 * 프레임워크 fragment({@code cozy/…})와 없는 템플릿은 따라가지 않는다. 주석은 보지 않는다.
	 */
	static List<Object> usedComponents(Path templates, String template) {
		return usedComponents(templates, template, new ArrayList<>());
	}

	/**
	 * @param bindings 같은 순서로 모은, Alpine이나 htmx 속성이 붙은 요소. {@code screenLine}은 화면 템플릿에서 그 요소가 보이는 줄이다(화면
	 * 템플릿의 요소면 그 줄, 넣은 템플릿의 요소면 화면 템플릿에서 넣은 줄)
	 */
	static List<Object> usedComponents(Path templates, String template, List<Object> bindings) {
		List<Object> found = new ArrayList<>();
		collect(templates, template, template, null, null, new java.util.HashSet<>(), new LinkedHashMap<>(), found, bindings);
		return found;
	}

	private static void collect(Path templates, String screen, String template, String fragment, Integer via,
			java.util.Set<String> seen, Map<String, String> read, List<Object> found, List<Object> bindings) {
		if (template.startsWith("cozy/") || !seen.add(template + "::" + fragment)) {
			return;
		}
		Path file = templates.resolve(template + ".html");
		if (!Files.isRegularFile(file)) {
			return;
		}
		String content = read.computeIfAbsent(template, (t) -> {
			try {
				return Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			}
		});
		List<TemplateOutline.Node> nodes = TemplateOutline
			.of(new TemplateFile(template + ".html", Origin.APP, "templates/" + template + ".html", content));
		int from = 1;
		int to = Integer.MAX_VALUE;
		if (fragment != null) {
			TemplateOutline.Node declaration = nodes.stream()
				.filter((n) -> n.kind().equals("fragment") && fragment.equals(n.fragment()))
				.findFirst()
				.orElse(null);
			if (declaration == null) {
				return;
			}
			from = declaration.line();
			to = declaration.endLine();
		}
		boolean own = template.equals(screen);
		// 요소와 삽입을 줄 순서로 (같은 줄이면 요소 먼저)
		java.util.TreeMap<Integer, List<Runnable>> steps = new java.util.TreeMap<>();
		ScreenBehaviors.Parsed parsed = ScreenBehaviors.parse(template, content);
		for (io.salvia95.cozy.cli.internal.behavior.TemplateTags.Tag tag : parsed.tags()) {
			if (tag.line() < from || tag.line() > to) {
				continue;
			}
			Map<String, Object> element = ScreenBehaviors.element(parsed, tag);
			if (element == null) {
				continue;
			}
			int line = tag.line();
			element.put("screenLine", own ? line : via);
			steps.computeIfAbsent(line, (l) -> new ArrayList<>()).add(() -> {
				bindings.add(element);
				if (Boolean.TRUE.equals(element.get("root")) && element.get("component") != null) {
					found.add(ordered("name", element.get("component"), "template", template, "line", line));
				}
			});
		}
		for (TemplateOutline.Node node : nodes) {
			if ((node.kind().equals("replace") || node.kind().equals("insert")) && node.line() >= from && node.line() <= to
					&& node.target().matches("[A-Za-z0-9_/\\-]*")) {
				String target = node.target().isEmpty() ? template : node.target();
				Integer next = own ? Integer.valueOf(node.line()) : via;
				steps.computeIfAbsent(node.line(), (l) -> new ArrayList<>())
					.add(() -> collect(templates, screen, target, node.fragment(), next, seen, read, found, bindings));
			}
		}
		steps.values().forEach((list) -> list.forEach(Runnable::run));
	}

	/** 모듈이 static/{모듈}/{모듈}.js에 등록한 Alpine 컴포넌트 (레이아웃이 싣는 파일) */
	private static List<Object> registeredComponents(AppProject project, AppProject.Module module) {
		Path file = project.dir().resolve("src/main/resources/static/" + module.name() + "/" + module.name() + ".js");
		List<Object> names = new ArrayList<>();
		if (!Files.isRegularFile(file)) {
			return names;
		}
		try {
			java.util.regex.Matcher m = REGISTRATION.matcher(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
			while (m.find()) {
				names.add(m.group(2));
			}
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
		return names;
	}

	/** 화면 템플릿의 구조: fragment 선언과 삽입을 겹친 깊이와 줄 번호로. 글 출력에는 싣지 않는다 */
	private static List<Object> outline(Path file, String template) throws IOException {
		String content = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
		List<Object> outline = new ArrayList<>();
		for (TemplateOutline.Node node : TemplateOutline
			.of(new TemplateFile(template + ".html", Origin.APP, "templates/" + template + ".html", content))) {
			outline.add(ordered("line", node.line(), "endLine", node.endLine(), "depth", node.depth(), "kind", node.kind(), "target", node.target(),
					"fragment", node.fragment(), "id", node.id(), "args", new ArrayList<Object>(node.args())));
		}
		return outline;
	}

	// ── 글 출력 ─────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	public static String text(Map<String, Object> model) {
		StringBuilder out = new StringBuilder();
		Map<String, Object> app = (Map<String, Object>) model.get("app");
		out.append("앱 ").append(app.get("basePackage")).append(" (메인 ").append(app.get("mainClass"))
			.append(", cozy ").append(app.get("cozyVersion")).append(")\n");
		out.append("스타터: ").append(join((List<Object>) app.get("starters"), "없음")).append('\n');
		for (Object item : (List<Object>) model.get("modules")) {
			Map<String, Object> m = (Map<String, Object>) item;
			out.append('\n').append("모듈 ").append(m.get("name")).append(" (").append(m.get("package")).append(")\n");
			List<Object> deps = (List<Object>) m.get("dependsOn");
			out.append("  의존: ").append((deps == null) ? "선언 안 함(open)" : join(deps, "없음")).append('\n');
			contracts(out, "쓰기", (List<Object>) m.get("facades"));
			contracts(out, "읽기", (List<Object>) m.get("finders"));
			for (Object d : (List<Object>) m.get("dtos")) {
				Map<String, Object> dto = (Map<String, Object>) d;
				out.append("  DTO ").append(dto.get("type")).append('(').append(join((List<Object>) dto.get("fields"), ""))
					.append(")\n");
			}
			for (Object l : (List<Object>) m.get("listeners")) {
				Map<String, Object> listener = (Map<String, Object>) l;
				out.append("  리스너 ").append(listener.get("type")).append('.').append(listener.get("method"))
					.append(" ← ").append(listener.get("on")).append('\n');
			}
			List<Object> controllers = (List<Object>) m.get("controllers");
			if (!controllers.isEmpty()) {
				out.append("  컨트롤러 ").append(join(controllers, "")).append('\n');
			}
			List<Object> components = (List<Object>) m.get("components");
			if (!components.isEmpty()) {
				out.append("  컴포넌트 ").append(join(components, "")).append('\n');
			}
			for (Object b : (List<Object>) m.get("behaviors")) {
				Map<String, Object> behavior = (Map<String, Object>) b;
				List<Object> attached = ((List<Object>) behavior.get("attached")).stream()
					.map((a) -> (Map<String, Object>) a)
					.map((a) -> (Object) (a.get("template") + ":" + a.get("line")))
					.toList();
				out.append("  화면 안의 동작 ").append(behavior.get("name")).append(" — ").append(behavior.get("purpose"))
					.append(" · 붙인 곳 ").append(join(attached, "없음")).append('\n');
			}
			for (Object s : (List<Object>) m.get("screens")) {
				Map<String, Object> screen = (Map<String, Object>) s;
				out.append("  화면 ").append(screen.get("template")).append(" — ")
					.append((screen.get("spec") != null) ? screen.get("spec") + ": " + join((List<Object>) screen.get("states"), "")
							: "스펙 없음")
					.append(((List<Object>) screen.get("components")).isEmpty() ? ""
							: " · 컴포넌트 " + String.join(", ", ((List<Object>) screen.get("components")).stream()
								.map((c) -> (Map<String, Object>) c)
								.map((c) -> c.get("name") + "(" + c.get("template") + ":" + c.get("line") + ")")
								.toList()))
					.append('\n');
			}
			for (Object f : (List<Object>) m.get("fragments")) {
				Map<String, Object> fragment = (Map<String, Object>) f;
				out.append("  공개 fragment ").append(fragment.get("id")).append(": ").append(fragment.get("signature"))
					.append('\n');
			}
		}
		List<Object> events = (List<Object>) model.get("events");
		if (!events.isEmpty()) {
			out.append('\n');
		}
		for (Object e : events) {
			Map<String, Object> event = (Map<String, Object>) e;
			List<Object> listeners = (List<Object>) event.get("listeners");
			out.append("이벤트 ").append(event.get("owner")).append('.').append(event.get("type")).append('(')
				.append(join((List<Object>) event.get("fields"), "")).append(") 발행: ")
				.append(join((List<Object>) event.get("publishedIn"), "없음(아직 발행하지 않음)"))
				.append(" → ")
				.append(listeners.isEmpty() ? "받는 곳 없음" : String.join(", ", listeners.stream()
					.map((l) -> (Map<String, Object>) l)
					.map((l) -> l.get("module") + "(" + l.get("type") + ")")
					.toList()))
				.append('\n');
		}
		List<Object> uiEvents = (List<Object>) model.get("uiEvents");
		if (!uiEvents.isEmpty()) {
			out.append('\n');
		}
		for (Object e : uiEvents) {
			Map<String, Object> event = (Map<String, Object>) e;
			out.append("화면 이벤트 ").append(event.get("event")).append(" 내는 곳: ")
				.append(join(((List<Object>) event.get("emittedBy")).stream().map((w) -> (Object) where((Map<String, Object>) w)).toList(), "없음"))
				.append(" → 받는 곳: ")
				.append(join(((List<Object>) event.get("receivedBy")).stream().map((w) -> (Object) where((Map<String, Object>) w)).toList(), "없음"))
				.append('\n');
		}
		List<Object> slots = (List<Object>) model.get("slots");
		out.append('\n').append("빈칸: ").append(slots.isEmpty() ? "없음" : slots.size() + "곳").append('\n');
		for (Object s : slots) {
			Map<String, Object> slot = (Map<String, Object>) s;
			out.append("  ").append(slot.get("file")).append(':').append(slot.get("line")).append(' ').append(slot.get("slot"))
				.append(" — ").append(slot.get("what")).append('\n');
		}
		List<Object> problems = (List<Object>) model.get("problems");
		if (!problems.isEmpty()) {
			out.append('\n').append("읽지 못한 것 ").append(problems.size()).append("건\n");
			problems.forEach((p) -> out.append("  ").append(p).append('\n'));
		}
		return out.toString();
	}

	/** 화면 이벤트를 내거나 받는 곳 한 군데: {@code memoSelection.clear}, {@code memo/api/list:2(htmx)}, 파일 */
	private static String where(Map<String, Object> w) {
		return switch (String.valueOf(w.get("kind"))) {
			case "behavior" -> w.get("component") + "." + w.get("action");
			case "server" -> String.valueOf(w.get("file"));
			default -> w.get("template") + ":" + w.get("line") + "(" + w.get("kind") + ")";
		};
	}

	@SuppressWarnings("unchecked")
	private static void contracts(StringBuilder out, String label, List<Object> contracts) {
		for (Object c : contracts) {
			Map<String, Object> contract = (Map<String, Object>) c;
			out.append("  ").append(label).append(' ').append(contract.get("type")).append('\n');
			for (Object method : (List<Object>) contract.get("methods")) {
				out.append("    ").append(method).append('\n');
			}
		}
	}

	/** 순서가 고정된 맵 (Map.of는 순회 순서가 실행마다 다를 수 있다) */
	public static Map<String, Object> ordered(Object... keyValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put((String) keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	private static String join(List<Object> items, String empty) {
		return items.isEmpty() ? empty : String.join(", ", items.stream().map(String::valueOf).toList());
	}
}
