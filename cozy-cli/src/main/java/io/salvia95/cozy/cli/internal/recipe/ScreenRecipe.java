package io.salvia95.cozy.cli.internal.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.rules.Layer;

/**
 * {@code cozy add screen}: 읽기 화면 하나 (ADR-0032 부록 §2.4). 컨트롤러, 화면 DTO, 템플릿(overview 또는 details 패턴), 화면 스펙, 원하면
 * 내비게이션 항목을 만든다. 컨트롤러의 화면 모델 만들기가 빈칸이다.
 */
final class ScreenRecipe {

	static final Set<String> PATTERNS = Set.of("overview", "details");

	private ScreenRecipe() {
	}

	static String run(Context ctx, Args args) {
		args.only("screen", 2, "pattern", "title", "field", "nav");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String screen = Names.kebab(args.positional(1, "화면 이름"), "화면");
		String pattern = args.required("pattern");
		if (!PATTERNS.contains(pattern)) {
			throw new CliException("--pattern은 overview(개요) 또는 details(상세)입니다. 목록·만들기·수정은 cozy add crud가 만듭니다.");
		}
		String title = Names.label(args.required("title"), "제목");
		List<Field> fields = Field.parseAll(args.all("field"));
		if (!ctx.hasModule(module)) {
			BasicRecipes.createModule(ctx, module, title);
		}

		String M = Names.capitalize(module);
		boolean index = screen.equals("index");
		String S = index ? M : M + Names.pascalOfKebab(screen);
		String controller = S + "Controller";
		String view = S + "View";
		String path = index ? "/" + module : "/" + module + "/" + screen;
		String viewName = module + "/api/" + screen;
		String id = Names.kebabOf(S);
		String apiPkg = ctx.rolePackage(module, Layer.API);
		String dtoPkg = ctx.rolePackage(module, Layer.API_DTO);

		List<String> finders = ctx.usableFinders(module);
		ctx.plan()
			.create(ctx.javaFile(apiPkg, controller), new Template().put("pkg", apiPkg)
				.put("Controller", controller)
				.put("viewImport", dtoPkg + "." + view)
				.put("View", view)
				.put("path", path)
				.put("method", index ? "index" : Names.camelOfKebab(screen))
				.put("viewName", viewName)
				.put("title", title)
				.render("screen/Controller.java"));

		Set<String> imports = new TreeSet<>();
		fields.stream().map(Field::importName).filter((i) -> i != null).forEach(imports::add);
		ctx.plan()
			.create(ctx.javaFile(dtoPkg, view), new Template().put("pkg", dtoPkg)
				.put("imports", imports.isEmpty() ? "" : String.join("", imports.stream().map((i) -> "import " + i + ";\n").toList()) + "\n")
				.put("title", title)
				.put("View", view)
				.put("components", String.join(", ", fields.stream().map((f) -> f.javaType() + " " + f.name()).toList()))
				.render("screen/View.java"));

		ctx.plan()
			.create("src/main/resources/templates/" + viewName + ".html", template(pattern, title, id, fields, view));
		ctx.plan()
			.create("src/main/resources/specs/" + module + "-" + screen + ".yml", new Template().put("title", title)
				.put("screen", module + "-" + screen)
				.put("template", viewName)
				.put("viewType", dtoPkg + "." + view)
				.put("default", values(fields, false))
				.put("empty", values(fields, true))
				.render("screen/spec.yml"));

		String nav = args.option("nav");
		if (nav != null) {
			String[] parts = nav.split(":");
			if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
				throw new CliException("--nav는 라벨:아이콘 형식입니다 (예: --nav 대시보드:layout-dashboard).");
			}
			Navigation.add(ctx, module, Names.camel(S), Names.label(parts[0], "내비게이션 라벨"), path, parts[1].strip());
		}
		return "빈칸 view-model을 채웁니다. 지금 쓸 수 있는 Finder: " + (finders.isEmpty() ? "없음" : String.join(", ", finders))
				+ ". 다른 모듈의 값이 필요하면 먼저 cozy link " + module + " --uses <모듈>";
	}

	private static String template(String pattern, String title, String id, List<Field> fields, String view) {
		List<Field> numbers = fields.stream().filter((f) -> f.kind() == Field.Kind.INT || f.kind() == Field.Kind.LONG).toList();
		List<Field> others = "overview".equals(pattern)
				? fields.stream().filter((f) -> !numbers.contains(f)).toList() : fields;
		String items = datagrid(others, "        ");
		Template t = new Template().put("title", title).put("id", id).put("View", view);
		if ("overview".equals(pattern)) {
			StringBuilder stats = new StringBuilder();
			for (Field f : numbers) {
				stats.append("      <div class=\"col-sm-6 col-lg-3\">\n        <div th:replace=\"~{cozy/component/stat :: stat('chart-bar', ${view.")
					.append(f.name()).append("} ?: 0, '").append(f.label()).append("')}\"></div>\n      </div>\n");
			}
			t.put("statsRef", numbers.isEmpty() ? "~{}" : "~{::#" + id + "-stats}")
				.put("stats", numbers.isEmpty() ? "" : "    <div id=\"" + id + "-stats\" class=\"row row-deck row-cards\">\n" + stats + "    </div>\n")
				.put("bodyRef", others.isEmpty() ? "~{}" : "~{::#" + id + "-body}")
				.put("body", others.isEmpty() ? "" : card(id + "-body", "요약", items, "    "));
			return t.render("screen/overview.html");
		}
		t.put("body", "    <div id=\"" + id + "-body\" class=\"row row-cards\">\n      <div class=\"col-lg-8\">\n"
				+ card(null, "정보", datagrid(others, "            "), "        ") + "      </div>\n    </div>\n");
		return t.render("screen/details.html");
	}

	/** 제목이 있는 카드. items는 card-body 안쪽 들여쓰기(indent + 4칸)로 이미 맞춘 내용 */
	static String card(String id, String heading, String items, String indent) {
		return indent + "<div" + ((id != null) ? " id=\"" + id + "\"" : "") + " class=\"card\">\n"
				+ indent + "  <div class=\"card-header\"><h3 class=\"card-title\">" + heading + "</h3></div>\n"
				+ indent + "  <div class=\"card-body\">\n" + items + indent + "  </div>\n" + indent + "</div>\n";
	}

	/** 값 목록 (Tabler datagrid). 비어 있으면 안내 문장 */
	static String datagrid(List<Field> fields, String indent) {
		if (fields.isEmpty()) {
			return indent + "<p class=\"text-secondary mb-0\">보여 줄 값이 없습니다.</p>\n";
		}
		StringBuilder out = new StringBuilder(indent + "<div class=\"datagrid\">\n");
		for (Field f : fields) {
			out.append(indent).append("  <div class=\"datagrid-item\">\n");
			out.append(indent).append("    <div class=\"datagrid-title\">").append(f.label()).append("</div>\n");
			out.append(indent).append("    <div class=\"datagrid-content\" th:text=\"").append(display("view", f)).append("\">—</div>\n");
			out.append(indent).append("  </div>\n");
		}
		return out.append(indent).append("</div>\n").toString();
	}

	/** 값 하나를 글자로: 없으면 —(글은 빈 글자도), 참·거짓은 예·아니요 */
	static String display(String object, Field f) {
		String ref = object + "." + f.name();
		return switch (f.kind()) {
			case BOOL -> "${" + ref + "} ? '예' : '아니요'";
			case TEXT, LONGTEXT -> "${#strings.isEmpty(" + ref + ")} ? '—' : ${" + ref + "}";
			default -> "${" + ref + "} ?: '—'";
		};
	}

	private static String values(List<Field> fields, boolean empty) {
		List<String> pairs = new ArrayList<>();
		for (Field f : fields) {
			pairs.add(f.name() + ": " + (empty ? f.emptySample() : f.sample()));
		}
		return pairs.isEmpty() ? "{}" : "{ " + String.join(", ", pairs) + " }";
	}
}
