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
		List<Object> modules = new ArrayList<>();
		for (AppProject.Module module : project.modules().values()) {
			modules.add(module(project, module, specs, appManifest));
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
		List<Object> problems = project.problems().stream().map((v) -> (Object) v.format()).toList();

		Map<String, Object> model = new LinkedHashMap<>();
		model.put("app", app);
		model.put("modules", modules);
		model.put("slots", slots);
		model.put("problems", problems);
		return model;
	}

	private static Map<String, Object> module(AppProject project, AppProject.Module module, List<AppResources.Spec> specs,
			List<AppResources.ManifestEntry> manifest) {
		JavaSources sources = project.sources();
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", module.name());
		m.put("package", module.basePackage());
		m.put("dependsOn", module.allowedDependencies());
		List<Object> facades = new ArrayList<>();
		List<Object> finders = new ArrayList<>();
		List<Object> dtos = new ArrayList<>();
		List<Object> events = new ArrayList<>();
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
			else if (layer.isPresent() && layer.get() == Layer.SPI_EVENT && javaType.has(JavaType.Trait.COZY_EVENT)) {
				Map<String, Object> event = new LinkedHashMap<>();
				event.put("type", type.simpleName());
				event.put("fields", fields(type.tree())
					.stream()
					.filter((f) -> !f.endsWith(" eventId") && !f.endsWith(" schemaVersion"))
					.toList());
				event.put("publishedIn", publishers(project, type));
				events.add(event);
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
		m.put("events", events);
		m.put("listeners", listeners);
		m.put("controllers", controllers);
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
					.map((event) -> project.moduleOf(event.pkg()).map((em) -> em.name() + ".").orElse("")
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
				screen.put("outline", outline(file, template));
				screens.add(screen);
			}
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
		return screens;
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
			for (Object e : (List<Object>) m.get("events")) {
				Map<String, Object> event = (Map<String, Object>) e;
				out.append("  이벤트 ").append(event.get("type")).append('(')
					.append(join((List<Object>) event.get("fields"), "")).append(") 발행: ")
					.append(join((List<Object>) event.get("publishedIn"), "없음(아직 발행하지 않음)")).append('\n');
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
			for (Object s : (List<Object>) m.get("screens")) {
				Map<String, Object> screen = (Map<String, Object>) s;
				out.append("  화면 ").append(screen.get("template")).append(" — ")
					.append((screen.get("spec") != null) ? screen.get("spec") + ": " + join((List<Object>) screen.get("states"), "")
							: "스펙 없음")
					.append('\n');
			}
			for (Object f : (List<Object>) m.get("fragments")) {
				Map<String, Object> fragment = (Map<String, Object>) f;
				out.append("  공개 fragment ").append(fragment.get("id")).append(": ").append(fragment.get("signature"))
					.append('\n');
			}
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
