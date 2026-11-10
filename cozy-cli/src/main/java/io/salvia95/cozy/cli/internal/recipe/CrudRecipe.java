package io.salvia95.cozy.cli.internal.recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Role;

/**
 * {@code cozy add crud}: 목록·만들기·상세·수정·삭제 한 벌. reference-app의 memo와 같은 구조다. 흐름과 문구는
 * docs/conventions/design.md §2·§3을 따른다. 빈칸이 없어서 만든 직후 {@code mvn verify}가 통과한다(생성된 테스트 포함).
 */
final class CrudRecipe {

	static final Set<String> PUBLISH = Set.of("created", "updated", "deleted");

	private CrudRecipe() {
	}

	static String run(Context ctx, Args args) {
		args.only("crud", 1, "entity", "label", "icon", "field", "publish");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String E = Names.pascal(args.required("entity"), "엔티티");
		String L = Names.label(args.required("label"), "라벨");
		String icon = args.required("icon");
		List<Field> fields = Field.parseAll(args.all("field"));
		if (fields.isEmpty()) {
			throw new CliException("--field가 하나 이상 필요합니다 (예: --field title:text!:제목).");
		}
		Field name = fields.stream()
			.filter((f) -> f.kind() == Field.Kind.TEXT && f.required())
			.findFirst()
			.orElseThrow(() -> new CliException("목록과 상세에 이름으로 보일 필수 text 필드(예: title:text!:제목)가 하나 필요합니다."));
		if (fields.stream().anyMatch((f) -> f.kind() == Field.Kind.INSTANT)) {
			throw new CliException("crud 필드에는 instant를 쓰지 않습니다. 만든 시각과 수정한 시각은 명령이 넣습니다(createdAt, updatedAt). "
					+ "날짜는 date를 씁니다.");
		}
		Set<String> publish = new LinkedHashSet<>();
		if (args.option("publish") != null) {
			for (String p : args.option("publish").split("\\s*,\\s*")) {
				if (!PUBLISH.contains(p)) {
					throw new CliException("--publish는 created, updated, deleted 중에서 쉼표로 고릅니다: " + p);
				}
				publish.add(p);
			}
			ctx.requireStarter("cozy-starter-events", "--publish(이벤트)");
		}
		ctx.requireStarter("cozy-starter-sqlite", "crud(SQLite 저장)");
		if (!ctx.hasModule(module)) {
			BasicRecipes.createModule(ctx, module, L);
		}
		boolean events = ctx.project().uses("cozy-starter-events");
		Model m = new Model(ctx, module, E, L, fields, name, publish, events);
		ctx.requireIcon(icon);
		m.t.put("icon", icon);

		ctx.ensureNamedInterface(module, Layer.SPI);
		ctx.ensureNamedInterface(module, Layer.SPI_DTO);
		m.java(Role.FACADE, E, "crud/Facade.java");
		m.java(Role.FINDER, E, "crud/Finder.java");
		m.java(Role.SPI_DTO, E + "Item", "crud/Item.java");
		m.java(Role.SPI_DTO, E + "Page", "crud/Page.java");
		m.java(Role.DOMAIN, E, "crud/Domain.java");
		m.java(Role.MAPPER, E, "crud/Mapper.java");
		m.java(Role.ROW, E, "crud/Row.java");
		m.java(Role.FACADE_IMPL, E, "crud/FacadeImpl.java");
		m.java(Role.FINDER_IMPL, E, "crud/FinderImpl.java");
		m.java(Role.CONTROLLER, E, "crud/Controller.java");
		m.java(Role.FORM, E, "crud/Form.java");
		m.java(Role.FILTER, E, "crud/Filter.java");
		m.java(Role.VIEWS, E, "crud/Views.java");
		for (String p : publish) {
			String event = E + Names.capitalize(p);
			List<Field> eventFields = new ArrayList<>();
			eventFields.add(new Field(m.e + "Id", Field.Kind.LONG, true, L + " id"));
			if (!p.equals("deleted")) {
				fields.stream().filter((f) -> f.kind() == Field.Kind.TEXT && f.required()).forEach(eventFields::add);
			}
			BasicRecipes.createEvent(ctx, module, event, eventFields,
					Names.objectParticle(L) + " " + switch (p) {
						case "created" -> "만들었다";
						case "updated" -> "수정했다";
						default -> "삭제했다";
					});
		}
		String resources = "src/main/resources/";
		ctx.plan().create(resources + "mapper/" + module + "/" + E + "Mapper.xml", m.render("crud/Mapper.xml"));
		String migration = ctx.existingMigration(module + "_create_" + m.table)
			.orElse(resources + "db/migration/V" + ctx.nextMigrationTime() + "__" + module + "_create_" + m.table + ".sql");
		ctx.plan().create(migration, m.render("crud/create.sql"));
		for (String screen : List.of("list", "form", "detail", "edit")) {
			ctx.plan().create(resources + "templates/" + module + "/api/" + screen + ".html", m.render("crud/" + screen + ".html"));
			ctx.plan().create(resources + "specs/" + module + "-" + screen + ".yml", m.render("crud/" + screen + ".yml"));
		}
		ctx.plan().create(resources + "templates/" + module + "/internal/fields.html", m.render("crud/fields.html"));
		String testPkg = ctx.modulePackage(module);
		ctx.plan().create(ctx.testFile(testPkg, E + "CrudTest"), m.render("crud/CrudTest.java"));
		ctx.plan().create(ctx.testFile(testPkg, E + "ScreenTest"), m.render("crud/ScreenTest.java"));
		Navigation.add(ctx, module, m.e, L, "/" + module, icon);
		return "다음: mvn verify로 확인합니다. 업무 규칙(검증, 기본값, 정렬)은 만든 코드를 고칩니다. 다른 모듈은 cozy link <모듈> --uses " + module
				+ " 뒤에 " + E + "Finder로 읽습니다";
	}

	/** 템플릿에 채울 값 */
	static final class Model {

		final Context ctx;

		final String module;

		final String E;

		final String e;

		final String table;

		final Template t = new Template();

		Model(Context ctx, String module, String E, String L, List<Field> fields, Field name, Set<String> publish, boolean events) {
			this.ctx = ctx;
			this.module = module;
			this.E = E;
			this.e = Names.camel(E);
			this.table = Names.snakeOf(E);
			List<Field> longtexts = fields.stream().filter((f) -> f.kind() == Field.Kind.LONGTEXT).toList();
			List<Field> rowFields = fields.stream().filter((f) -> f.kind() != Field.Kind.LONGTEXT).toList();
			List<Field> searchable = fields.stream().filter((f) -> f.kind() == Field.Kind.TEXT || f.kind() == Field.Kind.LONGTEXT).toList();
			List<Field> others = fields.stream().filter((f) -> f != name && f.kind() != Field.Kind.LONGTEXT).toList();
			String base = ctx.modulePackage(module);
			t.put("module", module).put("M", Names.capitalize(module)).put("E", E).put("e", this.e).put("eKebab", Names.kebabOf(E))
				.put("table", this.table).put("L", L).put("Lo", Names.objectParticle(L)).put("Ls", Names.subjectParticle(L))
				.put("base", base).put("appBase", ctx.basePackage())
				.put("apiPkg", Layer.API.packageIn(base)).put("dtoPkg", Layer.API_DTO.packageIn(base))
				.put("spiPkg", Layer.SPI.packageIn(base)).put("spiDtoPkg", Layer.SPI_DTO.packageIn(base))
				.put("eventPkg", Layer.SPI_EVENT.packageIn(base)).put("commandPkg", Layer.APP_COMMAND.packageIn(base))
				.put("queryPkg", Layer.APP_QUERY.packageIn(base)).put("domainPkg", Layer.DOMAIN.packageIn(base))
				.put("infraPkg", Layer.INFRA.packageIn(base))
				.put("name", name.name()).put("Name", name.pascal()).put("nameLabel", name.label())
				.put("nameColumn", name.column())
				.put("nameRequired", Names.objectParticle(name.label()) + " 입력하세요")
				.put("nameSample", name.sample());

			// Java 선언과 인자
			t.put("params", join(fields, (f) -> f.javaType() + " " + f.name(), ", "));
			t.put("args", join(fields, Field::name, ", "));
			t.put("formArgs", join(fields, (f) -> "form." + f.getter() + "()", ", "));
			t.put("updateArgs", join(fields, (f) -> f.kind() == Field.Kind.LONGTEXT ? "nullToEmpty(" + f.name() + ")" : f.name(), ", "));
			t.put("components", "long id, " + join(fields, (f) -> f.javaType() + " " + f.name(), ", ")
					+ ", Instant createdAt, Instant updatedAt");
			t.put("itemArgs", "entity.id(), " + join(fields, (f) -> "entity." + f.name() + "()", ", ")
					+ ", entity.createdAt(), entity.updatedAt()");
			t.put("mapperParams", join(fields, (f) -> "@Param(\"" + f.name() + "\") " + f.javaType() + " " + f.name(), ", "));
			t.put("rowSets", join(fields, (f) -> "\t\trow.set" + f.pascal() + "("
					+ (f.kind() == Field.Kind.LONGTEXT ? "nullToEmpty(" + f.name() + ")" : f.name()) + ");\n", ""));
			t.put("nullToEmpty", longtexts.isEmpty() ? "" : "\n\tprivate static String nullToEmpty(String value) {\n"
					+ "\t\treturn (value != null) ? value : \"\";\n\t}\n");
			t.put("rowFields", join(fields, (f) -> "\tprivate " + f.javaType() + " " + f.name() + ";\n\n", ""));
			t.put("rowAccessors", join(fields, Model::accessors, ""));
			t.put("formProperties", formProperties(fields));
			t.put("searchLabels", join(searchable, Field::label, "·"));

			// import
			Set<String> time = new TreeSet<>();
			fields.stream().map(Field::importName).filter((i) -> i != null).forEach(time::add);
			t.put("timeImports", imports(time));
			Set<String> withInstant = new TreeSet<>(time);
			withInstant.add("java.time.Instant");
			t.put("instantImports", imports(withInstant));
			Set<String> mapperImports = new TreeSet<>(withInstant);
			mapperImports.add("java.util.List");
			t.put("mapperImports", imports(mapperImports));
			Set<String> formImports = new TreeSet<>(time);
			t.put("formJavaImports", imports(formImports));
			boolean dates = fields.stream().anyMatch((f) -> f.kind() == Field.Kind.DATE);
			Set<String> formAnnotations = new TreeSet<>();
			if (dates) {
				formAnnotations.add("org.springframework.format.annotation.DateTimeFormat");
			}
			if (fields.stream().anyMatch((f) -> f.required() && f.kind() != Field.Kind.TEXT && f.kind() != Field.Kind.LONGTEXT
					&& f.kind() != Field.Kind.BOOL)) {
				formAnnotations.add("jakarta.validation.constraints.NotNull");
			}
			if (fields.stream().anyMatch((f) -> f.required() && (f.kind() == Field.Kind.TEXT || f.kind() == Field.Kind.LONGTEXT))) {
				formAnnotations.add("jakarta.validation.constraints.NotBlank");
			}
			if (!searchable.isEmpty()) {
				formAnnotations.add("jakarta.validation.constraints.Size");
			}
			t.put("formImports", imports(formAnnotations.stream().filter((i) -> i.startsWith("jakarta")).toList())
					+ imports(formAnnotations.stream().filter((i) -> i.startsWith("org")).toList()));

			// 이벤트
			Set<String> eventImports = new TreeSet<>();
			publish.forEach((p) -> eventImports.add(Layer.SPI_EVENT.packageIn(base) + "." + E + Names.capitalize(p)));
			t.put("eventImports", String.join("", eventImports.stream().map((i) -> "import " + i + ";\n").toList()));
			t.put("publisherImport", events ? "import org.springframework.context.ApplicationEventPublisher;\n" : "");
			t.put("eventsField", events ? "\n\tprivate final ApplicationEventPublisher events;\n" : "");
			t.put("eventsParam", events ? ", ApplicationEventPublisher events" : "");
			t.put("eventsAssign", events ? "\n\t\tthis.events = events;" : "");
			String eventArgs = join(fields.stream().filter((f) -> f.kind() == Field.Kind.TEXT && f.required()).toList(), (f) -> ", " + f.name(), "");
			t.put("publishCreated", publish.contains("created")
					? "\t\t// 같은 트랜잭션에 발행 기록이 남는다. 커밋되면 받는 모듈의 리스너가 비동기로 받는다 (docs/conventions/events.md)\n"
							+ "\t\tthis.events.publishEvent(" + E + "Created.of(row.getId()" + eventArgs + "));\n" : "");
			t.put("publishUpdated", publish.contains("updated")
					? "\t\tif (updated) {\n\t\t\tthis.events.publishEvent(" + E + "Updated.of(id" + eventArgs + "));\n\t\t}\n" : "");
			t.put("publishDeleted", publish.contains("deleted")
					? "\t\tif (deleted) {\n\t\t\tthis.events.publishEvent(" + E + "Deleted.of(id));\n\t\t}\n" : "");
			t.put("eventDoc", publish.isEmpty() ? "" : " " + join(publish.stream().map((p) -> switch (p) {
				case "created" -> "만들기";
				case "updated" -> "수정";
				default -> "삭제";
			}).toList(), (x) -> x, "·") + "가 성공하면 같은 트랜잭션 안에서 이벤트를 발행한다("
					+ join(publish.stream().map((p) -> E + Names.capitalize(p)).toList(), (s) -> s, ", ") + ").");
			t.put("eventsNote", events && publish.isEmpty()
					? "\n * 이벤트를 발행하려면 쓰기 메서드 안에서 this.events.publishEvent(...)를 부른다(cozy add event)." : "");

			// SQL
			t.put("columnsList", "id, " + join(fields, Field::column, ", ") + ", created_at, updated_at");
			t.put("insertColumns", join(fields, Field::column, ", ") + ", created_at, updated_at");
			t.put("insertValues", join(fields, (f) -> "#{" + f.name() + "}", ", ") + ", #{createdAt}, #{updatedAt}");
			t.put("searchCondition", join(searchable, (f) -> f.column() + " LIKE #{like} ESCAPE '\\'", " OR "));
			t.put("updateSets", join(fields, (f) -> f.column() + " = #{" + f.name() + "}", ", ") + ", updated_at = #{updatedAt}");
			t.put("ddlColumns", join(fields, (f) -> String.format("    %-10s %s,%n", f.column(), f.sql()).replace("\r", ""), ""));

			// 화면 DTO
			t.put("rowComponents", "long id, " + join(rowFields, (f) -> f.javaType() + " " + f.name(), ", ") + ", String updatedAt");
			t.put("rowArgs", "item.id(), " + join(rowFields, (f) -> "item." + f.name() + "()", ", ") + ", format(item.updatedAt())");
			t.put("detailComponents", "long id, " + join(fields, (f) -> f.kind() == Field.Kind.LONGTEXT ? "List<String> " + f.name() + "Lines"
					: f.javaType() + " " + f.name(), ", ") + ", String createdAt, String updatedAt");
			t.put("detailArgs", "item.id(), " + join(fields, (f) -> f.kind() == Field.Kind.LONGTEXT ? "item." + f.name() + "().lines().toList()"
					: "item." + f.name() + "()", ", ") + ", format(item.createdAt()), format(item.updatedAt())");
			t.put("formOf", join(fields, (f) -> "\t\tform.set" + f.pascal() + "(item." + f.name() + "());\n", ""));
			t.put("unchanged", join(fields, (f) -> switch (f.kind()) {
				case BOOL -> "item." + f.name() + "() == form." + f.getter() + "()";
				case TEXT, LONGTEXT -> "Objects.equals(nullToEmpty(item." + f.name() + "()), nullToEmpty(form." + f.getter() + "()))";
				default -> "Objects.equals(item." + f.name() + "(), form." + f.getter() + "())";
			}, "\n\t\t\t\t&& "));
			Set<String> viewsImports = new TreeSet<>(List.of("java.time.Instant", "java.time.ZoneId",
					"java.time.format.DateTimeFormatter", "java.util.List", "java.util.Locale", "java.util.Objects"));
			viewsImports.addAll(time);
			t.put("viewsTimeImports", imports(viewsImports));

			// 템플릿
			t.put("headCells", join(others, (f) -> "<th class=\"w-1\">" + f.label() + "</th>", ""));
			t.put("rowCells", join(others, (f) -> "\n                " + cell("row", f), ""));
			t.put("formFields", join(fields, (f) -> "  <div th:replace=\"~{cozy/component/field :: field('" + f.name() + "', '" + f.label()
					+ "', '" + f.input() + "')}\"></div>\n", ""));
			t.put("detailBody", detailBody(longtexts, others));

			// 스펙 값
			t.put("rowSample1", yaml(withId(1, rowFields, (f) -> f.sample()), "updatedAt: \"2026-11-06 10:00\""));
			t.put("rowSample2", yaml(withId(2, rowFields, (f) -> f == name ? Field.quote("둘째 " + L)
					: (f.kind() == Field.Kind.BOOL) ? "false" : f.sample()),
					"updatedAt: \"2026-11-05 17:20\""));
			t.put("rowSample11", yaml(withId(11, rowFields, (f) -> f == name ? Field.quote(L + " 11") : f.sample()),
					"updatedAt: \"2026-11-06 09:00\""));
			t.put("detailSample", yaml(withId(1, fields, (f) -> f.kind() == Field.Kind.LONGTEXT ? null : f.sample(), true),
					"createdAt: \"2026-11-06 10:00\", updatedAt: \"2026-11-06 10:30\""));
			t.put("detailEmpty", yaml(withId(2, fields, (f) -> f.kind() == Field.Kind.LONGTEXT ? null : f.emptySample(), false),
					"createdAt: \"2026-11-06 10:00\", updatedAt: \"2026-11-06 10:00\""));
			t.put("formSample", "{ " + join(fields, (f) -> f.name() + ": " + f.sample(), ", ") + " }");
			t.put("formEmptySample", "{ " + join(fields, (f) -> f.name() + ": " + f.emptySample(), ", ") + " }");
			t.put("formInvalid", "{ " + join(fields, (f) -> f.name() + ": " + (f == name ? "\"\"" : f.sample()), ", ") + " }");

			// 테스트
			t.put("createSample", join(fields, Field::javaSample, ", "));
			t.put("createRenamed", join(fields, (f) -> f == name ? "\"바꾼 " + L + "\"" : f.javaSample(), ", "));
			t.put("createBlankName", join(fields, (f) -> f == name ? "\"\"" : f.javaSample(), ", "));
			t.put("testImports", imports(time));
			t.put("postParams", join(fields, (f) -> ".param(\"" + f.name() + "\", " + paramValue(f) + ")", ""));
			t.put("postRenamed", join(fields, (f) -> ".param(\"" + f.name() + "\", " + (f == name ? "\"바꾼 " + L + "\"" : paramValue(f)) + ")", ""));
		}

		void java(Role role, String typeName, String resource) {
			String file = this.ctx.javaFile(role.layer().packageIn(this.ctx.modulePackage(this.module)),
					role.suffix().isEmpty() ? typeName : role.typeName(typeName));
			String current = this.ctx.plan().current(file);
			if ((role == Role.FACADE || role == Role.FACADE_IMPL) && current != null
					&& BasicRecipes.isEmptyFacade(this.ctx, this.module, file, current)) {
				// listener가 만든 빈 Facade는 crud가 채운다
				this.ctx.plan().edit(file, render(resource), "listener가 만든 빈 Facade를 crud로 채움");
				return;
			}
			this.ctx.plan().create(file, render(resource));
		}

		String render(String resource) {
			return this.t.render(resource);
		}

		static String cell(String object, Field f) {
			String ref = object + "." + f.name();
			if (f.kind() == Field.Kind.BOOL) {
				return "<td class=\"text-nowrap\">\n                  <th:block th:if=\"${" + ref + "}\"><span th:replace=\"~{cozy/component/badge :: badge('"
						+ f.label() + "', 'neutral')}\"></span></th:block>\n                  <span class=\"text-secondary\" th:unless=\"${" + ref
						+ "}\">—</span>\n                </td>";
			}
			// 빈 글자로 저장된 글도 —로 보인다
			return "<td class=\"text-secondary text-nowrap\" th:text=\"" + ScreenRecipe.display(object, f) + "\">—</td>";
		}

		private static String detailBody(List<Field> longtexts, List<Field> others) {
			StringBuilder out = new StringBuilder();
			out.append("      <div class=\"col-lg-8\">\n");
			if (longtexts.isEmpty()) {
				out.append(ScreenRecipe.card(null, "정보", grid(others, "            "), "        "));
			}
			for (Field f : longtexts) {
				out.append(longtextCard(f, "        "));
			}
			out.append("      </div>\n      <div class=\"col-lg-4\">\n");
			List<Field> side = longtexts.isEmpty() ? List.of() : others;
			StringBuilder grid = new StringBuilder();
			grid.append("            <div class=\"datagrid\">\n");
			for (Field f : side) {
				grid.append(gridItem(f));
			}
			grid.append("              <div class=\"datagrid-item\">\n                <div class=\"datagrid-title\">만든 시각</div>\n"
					+ "                <div class=\"datagrid-content\" th:text=\"${item.createdAt}\">2026-11-06 10:00</div>\n              </div>\n");
			grid.append("              <div class=\"datagrid-item\">\n                <div class=\"datagrid-title\">수정한 시각</div>\n"
					+ "                <div class=\"datagrid-content\" th:text=\"${item.updatedAt}\">2026-11-06 10:00</div>\n              </div>\n");
			grid.append("            </div>\n");
			out.append(ScreenRecipe.card(null, longtexts.isEmpty() ? "기록" : "정보", grid.toString(), "        "));
			out.append("      </div>\n");
			return out.toString();
		}

		/** 상세의 긴 글 카드 하나: 줄마다 문단 */
		static String longtextCard(Field f, String indent) {
			String items = indent + "    <th:block th:if=\"${item." + f.name() + "Lines.isEmpty()}\"><p class=\"text-secondary mb-0\">"
					+ Names.subjectParticle(f.label()) + " 없습니다.</p></th:block>\n" + indent + "    <p class=\"mb-2\" th:each=\"line : ${item."
					+ f.name() + "Lines}\" th:text=\"${line}\">" + f.label() + "</p>\n";
			return ScreenRecipe.card(null, f.label(), items, indent);
		}

		private static String grid(List<Field> fields, String indent) {
			if (fields.isEmpty()) {
				return indent + "<p class=\"text-secondary mb-0\">더 볼 정보가 없습니다.</p>\n";
			}
			StringBuilder out = new StringBuilder(indent + "<div class=\"datagrid\">\n");
			fields.forEach((f) -> out.append(gridItem(f)));
			return out.append(indent).append("</div>\n").toString();
		}

		private static String gridItem(Field f) {
			return gridItem(f, "              ");
		}

		/** 상세의 값 하나 (datagrid 항목) */
		static String gridItem(Field f, String indent) {
			return indent + "<div class=\"datagrid-item\">\n" + indent + "  <div class=\"datagrid-title\">" + f.label() + "</div>\n" + indent
					+ "  <div class=\"datagrid-content\" th:text=\"" + ScreenRecipe.display("item", f) + "\">—</div>\n" + indent + "</div>\n";
		}

		private static String formProperties(List<Field> fields) {
			StringBuilder decl = new StringBuilder();
			StringBuilder access = new StringBuilder();
			for (Field f : fields) {
				decl.append(formDeclaration(f));
				access.append(accessors(f));
			}
			return (decl.toString() + access).replaceAll("\n\n$", "\n");
		}

		/** 폼의 속성 하나: 검증 애너테이션과 필드 선언. 빈 줄로 끝난다 */
		static String formDeclaration(Field f) {
			StringBuilder decl = new StringBuilder();
			switch (f.kind()) {
				case TEXT, LONGTEXT -> {
					if (f.required()) {
						decl.append("\t@NotBlank(message = \"").append(Names.objectParticle(f.label())).append(" 입력하세요\")\n");
					}
					int max = (f.kind() == Field.Kind.TEXT) ? 200 : 5000;
					decl.append("\t@Size(max = ").append(max).append(", message = \"").append(Names.topicParticle(f.label())).append(' ')
						.append(max).append("자까지 쓸 수 있습니다\")\n");
				}
				case INT, LONG, DATE -> {
					if (f.required()) {
						decl.append("\t@NotNull(message = \"").append(Names.objectParticle(f.label())).append(" 입력하세요\")\n");
					}
					if (f.kind() == Field.Kind.DATE) {
						decl.append("\t@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)\n");
					}
				}
				default -> {
				}
			}
			return decl.append("\tprivate ").append(f.javaType()).append(' ').append(f.name()).append(";\n\n").toString();
		}

		/** 폼의 속성 하나가 쓰는 애너테이션의 완전한 이름 */
		static List<String> formAnnotations(Field f) {
			List<String> names = new ArrayList<>();
			boolean text = f.kind() == Field.Kind.TEXT || f.kind() == Field.Kind.LONGTEXT;
			if (text) {
				names.add("jakarta.validation.constraints.Size");
			}
			if (f.required() && text) {
				names.add("jakarta.validation.constraints.NotBlank");
			}
			if (f.required() && !text && f.kind() != Field.Kind.BOOL) {
				names.add("jakarta.validation.constraints.NotNull");
			}
			if (f.kind() == Field.Kind.DATE) {
				names.add("org.springframework.format.annotation.DateTimeFormat");
			}
			return names;
		}

		static String accessors(Field f) {
			return "\tpublic " + f.javaType() + " " + f.getter() + "() {\n\t\treturn this." + f.name() + ";\n\t}\n\n" + "\tpublic void set"
					+ f.pascal() + "(" + f.javaType() + " " + f.name() + ") {\n\t\tthis." + f.name() + " = " + f.name() + ";\n\t}\n\n";
		}

		static String paramValue(Field f) {
			return switch (f.kind()) {
				case TEXT -> f.sample();
				case LONGTEXT -> "\"첫 줄\\n둘째 줄\"";
				case INT -> "\"3\"";
				case LONG -> "\"1\"";
				case BOOL -> "\"true\"";
				case DATE -> "\"2026-11-06\"";
				case INSTANT -> throw new IllegalStateException();
			};
		}

		private static List<String> withId(int id, List<Field> fields, Function<Field, String> value) {
			return withId(id, fields, value, false);
		}

		/** id와 필드 값. 값이 null이면 longtext 줄 목록 */
		private static List<String> withId(int id, List<Field> fields, Function<Field, String> value, boolean lines) {
			List<String> pairs = new ArrayList<>();
			pairs.add("id: " + id);
			for (Field f : fields) {
				String v = value.apply(f);
				if (v == null) {
					pairs.add(f.name() + "Lines: " + (lines ? "[첫 줄, 둘째 줄]" : "[]"));
				}
				else {
					pairs.add(f.name() + ": " + v);
				}
			}
			return pairs;
		}

		private static String yaml(List<String> pairs, String tail) {
			return "{ " + String.join(", ", pairs) + ", " + tail + " }";
		}

		private static String imports(java.util.Collection<String> names) {
			if (names.isEmpty()) {
				return "";
			}
			return String.join("", names.stream().map((i) -> "import " + i + ";\n").toList()) + "\n";
		}

		private static <T> String join(java.util.Collection<T> items, Function<T, String> map, String separator) {
			return String.join(separator, items.stream().map(map).toList());
		}
	}
}
