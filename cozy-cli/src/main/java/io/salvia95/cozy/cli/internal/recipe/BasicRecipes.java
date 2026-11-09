package io.salvia95.cozy.cli.internal.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Describe;
import io.salvia95.cozy.cli.internal.JavaSources;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Role;

/** 1단계 레시피: module, link, event, listener, migration, fragment */
final class BasicRecipes {

	private BasicRecipes() {
	}

	// ── module ────────────────────────────────────────────────

	static String module(Context ctx, Args args) {
		args.only("module", 1, "label");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String label = Names.label(Optional.ofNullable(args.option("label")).orElse(module), "라벨");
		String file = ctx.packageInfo(ctx.modulePackage(module));
		if (ctx.plan().exists(file)) {
			// 이미 있는 모듈은 그대로 둔다 (의존 선언은 cozy link가 바꾼다)
			ctx.plan().edit(file, ctx.plan().current(file), "모듈 " + module);
		}
		else {
			createModule(ctx, module, label);
		}
		return "다음: 화면은 cozy add crud " + module + " 또는 cozy add screen " + module + ", 다른 모듈의 spi를 쓰려면 cozy link " + module
				+ " --uses <모듈>";
	}

	static void createModule(Context ctx, String module, String label) {
		String pkg = ctx.modulePackage(module);
		ctx.plan()
			.create(ctx.packageInfo(pkg), new Template().put("label", sentence(label))
				.put("module", module)
				.put("pkg", pkg)
				.render("module-package-info.java"));
	}

	// ── link ──────────────────────────────────────────────────

	static String link(Context ctx, Args args) {
		args.only("link", 1, "uses");
		String module = Names.module(args.positional(0, "모듈 이름"));
		List<String> targets = List.of(args.required("uses").split("\\s*,\\s*"));
		link(ctx, module, targets);
		return "다음: " + module + "에서 " + String.join(", ", targets) + "의 spi(Finder, DTO, 이벤트, 공개 fragment)를 씁니다. 쓰기는 이벤트로 합니다";
	}

	/** 모듈의 allowedDependencies에 "대상 :: spi"를 더한다 */
	static void link(Context ctx, String module, List<String> targets) {
		ctx.module(module);
		Map<String, Set<String>> graph = new LinkedHashMap<>();
		ctx.project().modules().values().forEach((m) -> {
			Set<String> deps = new LinkedHashSet<>();
			if (m.allowedDependencies() != null) {
				m.allowedDependencies().forEach((d) -> deps.add(d.replaceAll("\\s*::.*", "").strip()));
			}
			graph.put(m.name(), deps);
		});
		List<String> dependencies = new ArrayList<>();
		for (String target : targets) {
			Names.module(target);
			if (target.equals(module)) {
				throw new CliException(module + "가 자기 자신을 쓰도록 선언할 수는 없습니다.");
			}
			AppProject.Module other = ctx.module(target);
			boolean spi = other.namedInterfaces().contains("spi")
					|| ctx.plan().exists(ctx.packageInfo(ctx.rolePackage(target, Layer.SPI_DTO)))
					|| ctx.plan().exists(ctx.packageInfo(ctx.rolePackage(target, Layer.SPI_EVENT)))
					|| ctx.plan().exists(ctx.packageInfo(ctx.rolePackage(target, Layer.SPI)));
			if (!spi) {
				throw new CliException(target + "에 공개된 것(spi)이 없습니다. 먼저 cozy add crud, cozy add event, cozy add fragment로 "
						+ target + "의 spi를 만드세요.");
			}
			graph.computeIfAbsent(module, (k) -> new LinkedHashSet<>()).add(target);
			List<String> cycle = path(graph, target, module, new LinkedHashSet<>());
			if (cycle != null) {
				throw new CliException("의존이 순환합니다: " + module + " → " + String.join(" → ", cycle)
						+ ". 순환을 끊으려면 한쪽은 이벤트(spi/event)를 받게 하세요.");
			}
			dependencies.add(target + " :: spi");
		}
		String file = ctx.packageInfo(ctx.modulePackage(module));
		String current = ctx.plan().current(file);
		if (current == null) {
			throw new CliException(file + "가 없습니다. 의존을 선언할 수 없습니다.");
		}
		ctx.plan()
			.edit(file, PackageInfos.addDependencies(file, current, dependencies),
					"allowedDependencies += " + String.join(", ", dependencies.stream().map((d) -> "\"" + d + "\"").toList()));
	}

	private static List<String> path(Map<String, Set<String>> graph, String from, String to, Set<String> seen) {
		if (from.equals(to)) {
			return new ArrayList<>(List.of(to));
		}
		if (!seen.add(from)) {
			return null;
		}
		for (String next : graph.getOrDefault(from, Set.of())) {
			List<String> rest = path(graph, next, to, seen);
			if (rest != null) {
				rest.addFirst(from);
				return rest;
			}
		}
		return null;
	}

	// ── event ─────────────────────────────────────────────────

	static String event(Context ctx, Args args) {
		args.only("event", 2, "field");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String event = Names.pascal(args.positional(1, "이벤트 이름"), "이벤트");
		ctx.requireStarter("cozy-starter-events", "이벤트");
		ctx.module(module);
		List<Field> fields = Field.parseAll(args.all("field"));
		createEvent(ctx, module, event, fields, event + " 이벤트");
		return "다음: " + module + "의 쓰기 구현(*FacadeImpl) 트랜잭션 안에서 this.events.publishEvent(" + event + ".of(...)); 한 줄로 "
				+ "발행합니다. 받는 쪽은 cozy add listener <모듈> --on " + module + "." + event;
	}

	static void createEvent(Context ctx, String module, String event, List<Field> fields, String what) {
		ctx.ensureNamedInterface(module, Layer.SPI);
		ctx.ensureNamedInterface(module, Layer.SPI_EVENT);
		String pkg = ctx.rolePackage(module, Layer.SPI_EVENT);
		Set<String> imports = new TreeSet<>(Set.of("java.util.UUID"));
		fields.stream().map(Field::importName).filter((i) -> i != null).forEach(imports::add);
		String components = String.join("", fields.stream().map((f) -> ", " + f.eventType() + " " + f.name()).toList());
		String params = String.join(", ", fields.stream().map((f) -> f.eventType() + " " + f.name()).toList());
		String argsText = String.join("", fields.stream().map((f) -> ", " + f.name()).toList());
		ctx.plan()
			.create(ctx.roleFile(module, Role.EVENT, event), new Template().put("pkg", pkg)
				.put("imports", String.join("", imports.stream().map((i) -> "import " + i + ";\n").toList()))
				.put("what", what)
				.put("Event", event)
				.put("components", components)
				.put("params", params)
				.put("args", argsText)
				.render("event.java"));
		Map<String, String> sample = new java.util.TreeMap<>();
		sample.put("eventId", "\"7d4b3c2a-1e0f-4a5b-9c8d-6e7f8a9b0c1d\"");
		sample.put("schemaVersion", "1");
		fields.forEach((f) -> sample.put(f.name(), f.sample()));
		String json = "{" + String.join(",", sample.entrySet().stream().map((e) -> "\"" + e.getKey() + "\":" + e.getValue()).toList())
				+ "}\n";
		ctx.plan().create("src/test/resources/events/" + event + "/v1.json", json);
	}

	// ── listener ──────────────────────────────────────────────

	static String listener(Context ctx, Args args) {
		args.only("listener", 1, "on", "name");
		String module = Names.module(args.positional(0, "모듈 이름"));
		ctx.module(module);
		String on = args.required("on");
		Matcher m = Pattern.compile("([a-z][a-z0-9]*)\\.([A-Z][A-Za-z0-9]*)").matcher(on);
		if (!m.matches()) {
			throw new CliException("--on은 <보내는 모듈>.<이벤트> 형식입니다 (예: memo.MemoCreated).");
		}
		String source = m.group(1);
		String event = m.group(2);
		ctx.module(source);
		String eventName = ctx.rolePackage(source, Layer.SPI_EVENT) + "." + event;
		if (!ctx.project().sources().types().containsKey(eventName)) {
			throw new CliException(source + "에 이벤트 " + event + "가 없습니다(" + eventName + "). 먼저 cozy add event " + source + " "
					+ event + "로 만드세요.");
		}
		String name = Names.pascal(Optional.ofNullable(args.option("name")).orElse(event), "리스너") ;
		if (!source.equals(module)) {
			link(ctx, module, List.of(source));
		}
		String pkg = ctx.rolePackage(module, Layer.APP_COMMAND);
		// 리스너는 늘 자기 모듈의 {M}Facade를 받는다. 앱 상태에 따라 내용이 달라지면 다시 실행할 때 충돌한다
		String facade = ensureFacade(ctx, module);
		Template t = new Template().put("pkg", pkg)
			.put("eventImport", eventName)
			.put("source", source)
			.put("Event", event)
			.put("Name", name)
			.put("facadeImport", "import " + ctx.rolePackage(module, Layer.SPI) + "." + facade + ";\n")
			.put("facadeField", "\n\tprivate final " + facade + " facade;\n\n\t" + name + "Listener(" + facade
					+ " facade) {\n\t\tthis.facade = facade;\n\t}\n")
			.put("use", "자기 모듈의 " + facade + "(this.facade)를 부르세요");
		ctx.plan().create(ctx.javaFile(pkg, name + "Listener"), t.render("listener.java"));
		return "다음: 빈칸 listener-body를 채웁니다. 다른 모듈의 Facade는 부르지 않습니다(C8)";
	}

	/**
	 * 모듈의 {@code {M}Facade}. 없으면 빈 Facade와 구현을 만든다. 이미 있으면(crud나 손으로 만든 것) 그대로 쓴다.
	 *
	 * @return Facade의 단순 이름
	 */
	static String ensureFacade(Context ctx, String module) {
		String M = Names.capitalize(module);
		String facade = M + "Facade";
		String spi = ctx.rolePackage(module, Layer.SPI);
		String file = ctx.roleFile(module, Role.FACADE, M);
		if (ctx.project().sources().types().containsKey(spi + "." + facade) || ctx.plan().exists(file)) {
			return facade;
		}
		ctx.ensureNamedInterface(module, Layer.SPI);
		Template t = new Template().put("module", module).put("M", M);
		ctx.plan().create(file, t.put("pkg", spi).render("facade.java"));
		ctx.plan()
			.create(ctx.roleFile(module, Role.FACADE_IMPL, M), new Template().putAll(t)
				.put("pkg", ctx.rolePackage(module, Layer.APP_COMMAND))
				.put("facadeImport", spi + "." + facade)
				.render("facade-impl.java"));
		return facade;
	}

	/** listener가 만든 빈 Facade·구현인가 (crud가 같은 이름으로 채워도 된다) */
	static boolean isEmptyFacade(Context ctx, String module, String file, String content) {
		String M = Names.capitalize(module);
		String spi = ctx.rolePackage(module, Layer.SPI);
		Template t = new Template().put("module", module).put("M", M);
		return content.equals(new Template().putAll(t).put("pkg", spi).render("facade.java"))
				|| content.equals(new Template().putAll(t)
					.put("pkg", ctx.rolePackage(module, Layer.APP_COMMAND))
					.put("facadeImport", spi + "." + M + "Facade")
					.render("facade-impl.java"));
	}

	// ── migration ─────────────────────────────────────────────

	static String migration(Context ctx, Args args) {
		args.only("migration", 2);
		String module = Names.module(args.positional(0, "모듈 이름"));
		String description = Names.snake(args.positional(1, "설명"), "설명");
		ctx.requireStarter("cozy-starter-sqlite", "마이그레이션");
		ctx.module(module);
		String suffix = module + "_" + description;
		String file = ctx.existingMigration(suffix)
			.orElse("src/main/resources/db/migration/V" + ctx.nextMigrationTime() + "__" + suffix + ".sql");
		ctx.plan()
			.create(file, new Template().put("module", module).put("description", description).render("migration.sql"));
		return "다음: 빈칸 migration-sql에 DDL을 씁니다. 이미 배포한 마이그레이션은 고치지 않고 새 마이그레이션을 더합니다";
	}

	// ── fragment ──────────────────────────────────────────────

	private static final Pattern PARAM = Pattern.compile("([a-z][A-Za-z0-9]*):(list:)?([A-Za-z][A-Za-z0-9]*)");

	static String fragment(Context ctx, Args args) {
		args.only("fragment", 2, "param", "purpose");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String name = Names.kebab(args.positional(1, "fragment 이름"), "fragment");
		String purpose = sentence(args.required("purpose"));
		ctx.module(module);
		String fragment = Names.fragment(Names.camelOfKebab(name));
		List<String> params = new ArrayList<>();
		List<String> paramEntries = new ArrayList<>();
		Map<String, String> defaults = new LinkedHashMap<>();
		Map<String, String> empties = new LinkedHashMap<>();
		boolean unknownSample = false;
		for (String text : args.all("param")) {
			Matcher m = PARAM.matcher(text);
			if (!m.matches()) {
				throw new CliException("--param '" + text + "'는 이름:타입 형식입니다. 타입은 text·int·long·bool·date 같은 종류, "
						+ module + ".spi.dto의 record 이름, 또는 list:record 이름입니다.");
			}
			String param = m.group(1);
			boolean list = m.group(2) != null;
			String type = m.group(3);
			String manifestType;
			String sample;
			String empty;
			String description;
			if (!list && Character.isLowerCase(type.charAt(0))) {
				Field field = Field.parse(param + ":" + type);
				manifestType = field.manifestType();
				sample = field.sample();
				empty = field.emptySample();
				description = param;
			}
			else {
				String record = ctx.rolePackage(module, Layer.SPI_DTO) + "." + type;
				JavaSources.SourceType source = ctx.project().sources().types().get(record);
				if (source == null) {
					throw new CliException(record + "가 없습니다. 공개 fragment의 파라미터는 원시 값이나 " + module
							+ ".spi.dto의 record입니다(C5). 먼저 record를 만드세요.");
				}
				Map<String, String> values = new LinkedHashMap<>();
				for (String component : Describe.fields(source.tree())) {
					String componentType = component.substring(0, component.lastIndexOf(' '));
					String componentName = component.substring(component.lastIndexOf(' ') + 1);
					String value = sampleOf(componentType);
					unknownSample |= value == null;
					values.put(componentName, (value != null) ? value : "null");
				}
				String object = "{ " + String.join(", ", values.entrySet().stream().map((e) -> e.getKey() + ": " + e.getValue()).toList())
						+ " }";
				manifestType = list ? "List" : record;
				sample = list ? "[" + object + "]" : object;
				empty = list ? "[]" : object;
				description = list ? type + " 목록" : type;
			}
			params.add(param);
			paramEntries.add("    - { name: " + param + ", type: " + manifestType + ", required: true, description: \"" + description
					+ "\" }");
			defaults.put(param, sample);
			empties.put(param, empty);
		}
		String signature = fragment + "(" + String.join(", ", params) + ")";
		// 공개 fragment를 쓰는 모듈은 "모듈 :: spi"를 선언한다. 그 Named Interface가 있어야 Modulith가 선언을 받아들인다
		ctx.ensureNamedInterface(module, Layer.SPI);
		String template = module + "/spi/" + name;
		String id = module + "/" + name;
		ctx.plan()
			.create("src/main/resources/templates/" + template + ".html", new Template().put("module", module)
				.put("purpose", purpose)
				.put("id", id)
				.put("signature", signature)
				.put("params", params.isEmpty() ? "없음" : String.join(", ", params))
				.render("fragment.html"));
		StringBuilder entry = new StringBuilder();
		entry.append("- id: ").append(id).append('\n');
		entry.append("  template: ").append(template).append('\n');
		entry.append("  signature: ").append(signature).append('\n');
		entry.append("  purpose: ").append(purpose).append('\n');
		entry.append("  params:").append(paramEntries.isEmpty() ? " []" : "").append('\n');
		paramEntries.forEach((p) -> entry.append(p).append('\n'));
		entry.append("  samples:\n");
		if (unknownSample) {
			entry.append("    # cozy:slot fragment-samples — null로 둔 값을 화면 확인용 예시 값으로 바꾸세요\n");
		}
		entry.append("    default: ").append(object(defaults)).append('\n');
		entry.append("    empty: ").append(object(empties)).append('\n');
		String current = ctx.plan().current(ManifestEdits.FILE);
		String next = ManifestEdits.append(current, id, template, signature, entry.toString());
		if (current == null) {
			ctx.plan().create(ManifestEdits.FILE, next);
		}
		else {
			ctx.plan().edit(ManifestEdits.FILE, next, "fragments += " + id);
		}
		return "다음: 빈칸 fragment-body를 채웁니다. 다른 모듈은 cozy link <모듈> --uses " + module + " 뒤에 ~{" + template + " :: "
				+ signature + "}로 부릅니다";
	}

	private static String object(Map<String, String> values) {
		return "{ " + String.join(", ", values.entrySet().stream().map((e) -> e.getKey() + ": " + e.getValue()).toList()) + " }";
	}

	/** record 구성 요소 타입의 샘플 값. 모르는 타입은 null(빈칸으로 알린다) */
	static String sampleOf(String javaType) {
		String type = javaType.replaceAll("<.*>", "");
		return switch (type) {
			case "String" -> "\"예시\"";
			case "int", "long", "Integer", "Long", "short", "double", "float", "BigDecimal" -> "1";
			case "boolean", "Boolean" -> "true";
			case "Instant" -> "\"2026-11-06T01:00:00Z\"";
			case "LocalDate" -> "\"2026-11-06\"";
			case "LocalDateTime" -> "\"2026-11-06T10:00:00\"";
			case "List", "Set", "Collection" -> "[]";
			default -> null;
		};
	}

	/** 마침표로 끝나는 문장 */
	static String sentence(String text) {
		String t = text.strip();
		return (t.endsWith(".") || t.endsWith("다")) ? (t.endsWith(".") ? t : t + ".") : t + ".";
	}
}
