package io.salvia95.cozy.cli.internal.recipe;

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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;

import com.sun.source.tree.Tree;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.JavaSources;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Role;

/**
 * 2단계 레시피 query, command. 이미 있는 Finder·Facade, 구현, Mapper, Mapper XML에 메서드와 문을 더한다.
 *
 * <ul>
 * <li>대상: 모듈 spi의 {@code *Finder}(또는 {@code *Facade})가 하나면 그것, 둘 이상이면 {@code --in}, 없으면 {@code {M}Finder}와 구현을
 * 만든다.</li>
 * <li>Mapper: 구현의 {@code *Mapper} 필드. 구현에 생성자와 필드가 하나도 없으면(명령이 만든 빈 구현) {@code {M}Mapper}를 받는 생성자를
 * 더한다. 이미 생성자가 있는데 Mapper가 없으면 고치지 않고 실패한다.</li>
 * <li>다시 실행: 네 곳(인터페이스, 구현, Mapper, XML)의 시그니처를 비교한다. 모두 있으면 바뀐 것 없음, 모두 없으면 더하고, 일부만 있거나 같은 이름이
 * 다른 시그니처로 있으면 실패한다.</li>
 * </ul>
 */
final class MethodRecipes {

	private MethodRecipes() {
	}

	private static final Pattern METHOD = Pattern.compile("[a-z][A-Za-z0-9]*");

	private static final Pattern PARAM = Pattern.compile("([a-z][A-Za-z0-9]*):([a-z]+)");

	/** 행 record로 옮길 수 있는 구성 요소 타입 */
	private static final Set<String> ROW_TYPES = Set.of("String", "int", "long", "boolean", "double", "Integer", "Long",
			"Boolean", "Double", "LocalDate", "LocalDateTime", "Instant");

	/** 메서드 파라미터 하나 */
	record Param(String name, String type, String importName) {

		String declaration() {
			return this.type + " " + this.name;
		}

		String mapperDeclaration() {
			return "@Param(\"" + this.name + "\") " + this.type + " " + this.name;
		}
	}

	static List<Param> params(List<String> texts) {
		List<Param> params = new ArrayList<>();
		Set<String> names = new LinkedHashSet<>();
		for (String text : texts) {
			Matcher m = PARAM.matcher(text);
			if (!m.matches()) {
				throw new CliException("--param '" + text + "'는 이름:종류 형식입니다. 종류는 text·longtext·int·long·bool·date·instant입니다 "
						+ "(예: --param limit:int).");
			}
			String name = m.group(1);
			if (SourceVersion.isKeyword(name)) {
				throw new CliException("파라미터 이름 '" + name + "'는 Java 예약어라 쓸 수 없습니다.");
			}
			if (!names.add(name)) {
				throw new CliException("같은 이름의 파라미터가 둘 이상입니다: " + name);
			}
			Field field = new Field(name, Field.Kind.of(m.group(2)), false, name);
			params.add(new Param(name, field.eventType(), field.importName()));
		}
		return params;
	}

	static String method(String name) {
		if (name == null || !METHOD.matcher(name).matches() || SourceVersion.isKeyword(name)) {
			throw new CliException("메서드 이름 '" + name + "'는 소문자로 시작하는 camelCase여야 합니다 (예: recent, countPinned).");
		}
		return name;
	}

	// ── 대상 ──────────────────────────────────────────────────

	/** 인터페이스와 그 구현 */
	record Target(String iface, String ifaceFile, String impl, String implFile) {
	}

	/**
	 * @param role {@link Role#FINDER} 또는 {@link Role#FACADE}
	 */
	static Target target(Context ctx, String module, Role role, String in) {
		String suffix = role.suffix();
		String spi = ctx.rolePackage(module, Layer.SPI);
		List<String> found = ctx.project()
			.sources()
			.types()
			.values()
			.stream()
			.filter((t) -> t.isInterface() && t.pkg().equals(spi) && t.name().equals(t.topLevel())
					&& t.simpleName().endsWith(suffix))
			.map(JavaSources.SourceType::simpleName)
			.sorted()
			.toList();
		String option = "--in " + String.join("|", found);
		String iface;
		if (in != null) {
			if (!found.contains(in)) {
				throw new CliException(module + ".spi에 " + in + "가 없습니다. " + (found.isEmpty() ? "--in을 빼면 "
						+ Names.capitalize(module) + suffix + "를 만듭니다." : "고를 수 있는 것: " + String.join(", ", found)));
			}
			iface = in;
		}
		else if (found.size() > 1) {
			throw new CliException(module + ".spi에 " + suffix + "가 둘 이상입니다(" + String.join(", ", found) + "). " + option
					+ "로 하나를 고르세요.");
		}
		else if (found.size() == 1) {
			iface = found.getFirst();
		}
		else {
			iface = (role == Role.FACADE) ? BasicRecipes.ensureFacade(ctx, module) : ensureFinder(ctx, module);
			String M = Names.capitalize(module);
			return new Target(iface, ctx.roleFile(module, role, M), M + suffix + "Impl",
					ctx.roleFile(module, (role == Role.FACADE) ? Role.FACADE_IMPL : Role.FINDER_IMPL, M));
		}
		String implPkg = ctx.rolePackage(module, (role == Role.FACADE) ? Layer.APP_COMMAND : Layer.APP_QUERY);
		List<JavaSources.SourceType> impls = ctx.project()
			.sources()
			.types()
			.values()
			.stream()
			.filter((t) -> !t.isInterface() && t.pkg().equals(implPkg) && t.name().equals(t.topLevel()))
			.filter((t) -> t.tree()
				.getImplementsClause()
				.stream()
				.map(Tree::toString)
				.anyMatch((i) -> i.equals(iface) || i.endsWith("." + iface)))
			.toList();
		if (impls.size() != 1) {
			throw new CliException(module + "의 " + implPkg.substring(ctx.modulePackage(module).length() + 1) + "에서 " + iface
					+ "를 구현한 클래스를 하나로 찾지 못했습니다(" + impls.size() + "개). 구현은 손으로 맞추세요.");
		}
		String impl = impls.getFirst().simpleName();
		return new Target(iface, ctx.javaFile(spi, iface), impl, ctx.javaFile(implPkg, impl));
	}

	/** {@code {M}Finder}와 빈 구현 */
	static String ensureFinder(Context ctx, String module) {
		String M = Names.capitalize(module);
		String spi = ctx.rolePackage(module, Layer.SPI);
		String file = ctx.roleFile(module, Role.FINDER, M);
		if (!ctx.plan().exists(file)) {
			ctx.ensureNamedInterface(module, Layer.SPI);
			Template t = new Template().put("module", module).put("M", M);
			ctx.plan().create(file, new Template().putAll(t).put("pkg", spi).render("finder.java"));
			ctx.plan()
				.create(ctx.roleFile(module, Role.FINDER_IMPL, M), new Template().putAll(t)
					.put("pkg", ctx.rolePackage(module, Layer.APP_QUERY))
					.put("finderImport", spi + "." + M + "Finder")
					.render("finder-impl.java"));
		}
		return M + "Finder";
	}

	/** 구현이 쓰는 Mapper */
	record MapperUse(String mapper, String field, String mapperFile, String xmlFile, String namespace) {
	}

	/**
	 * 구현의 Mapper. 생성자와 필드가 없는 구현에는 {@code {M}Mapper}를 받는 생성자를 더한다(계획에 넣는다).
	 */
	static MapperUse mapper(Context ctx, String module, Target target) {
		String infra = ctx.rolePackage(module, Layer.INFRA);
		String source = ctx.plan().current(target.implFile());
		JavaEdits.Parsed impl = JavaEdits.parse(target.implFile(), source);
		Map<String, String> fields = impl.fields(target.impl());
		List<Map.Entry<String, String>> mappers = fields.entrySet().stream().filter((e) -> e.getValue().endsWith("Mapper")).toList();
		if (mappers.size() > 1) {
			throw new CliException(target.implFile() + "에 Mapper 필드가 둘 이상입니다. 어느 것에 더할지 정할 수 없습니다. 손으로 쓰세요.");
		}
		if (mappers.size() == 1) {
			String mapper = mappers.getFirst().getValue();
			if (!ctx.project().sources().types().containsKey(infra + "." + mapper)
					&& !ctx.plan().exists(ctx.javaFile(infra, mapper))) {
				throw new CliException(target.implFile() + "의 " + mapper + "가 " + infra + "에 없습니다. 이 모듈의 Mapper만 고칩니다.");
			}
			return use(ctx, module, mapper, mappers.getFirst().getKey());
		}
		if (!fields.isEmpty() || impl.constructors(target.impl()) > 0) {
			throw new CliException(target.implFile() + "에 Mapper가 없습니다. 이미 있는 생성자는 고치지 않습니다. 생성자에 "
					+ "이 모듈의 *Mapper를 받도록 손으로 고친 뒤 다시 실행하세요.");
		}
		String mapper = Names.capitalize(module) + "Mapper";
		String mapperFile = ctx.javaFile(infra, mapper);
		if (!ctx.plan().exists(mapperFile)) {
			ctx.plan().create(mapperFile, new Template().put("pkg", infra).put("module", module).put("Mapper", mapper).render("mapper.java"));
		}
		String injected = JavaEdits.addFirstMember(target.implFile(), source, target.impl(), "\tprivate final " + mapper
				+ " mapper;\n\n\t" + target.impl() + "(" + mapper + " mapper) {\n\t\tthis.mapper = mapper;\n\t}");
		injected = JavaEdits.addImports(target.implFile(), injected, List.of(infra + "." + mapper));
		ctx.plan().edit(target.implFile(), injected, "생성자 += " + mapper);
		return use(ctx, module, mapper, "mapper");
	}

	private static MapperUse use(Context ctx, String module, String mapper, String field) {
		String infra = ctx.rolePackage(module, Layer.INFRA);
		String xml = "src/main/resources/mapper/" + module + "/" + mapper + ".xml";
		String namespace = infra + "." + mapper;
		if (!ctx.plan().exists(xml)) {
			ctx.plan().create(xml, new Template().put("namespace", namespace).render("mapper.xml"));
		}
		return new MapperUse(mapper, field, ctx.javaFile(infra, mapper), xml, namespace);
	}

	// ── 네 곳에 더하기 ────────────────────────────────────────

	/** 한 곳에 더할 메서드 */
	record JavaAddition(String file, String type, JavaEdits.Signature signature, String member, Set<String> imports) {
	}

	/** Mapper XML에 더할 문 */
	record XmlAddition(String file, String namespace, String kind, String id, String statement) {
	}

	/**
	 * 모두 있으면 그대로, 모두 없으면 더하고, 나머지는 실패한다.
	 *
	 * @param what 출력에 적을 이름 ({@code query recent})
	 */
	static void apply(Context ctx, String what, List<JavaAddition> java, XmlAddition xml) {
		List<String> present = new ArrayList<>();
		List<String> absent = new ArrayList<>();
		List<String> conflicts = new ArrayList<>();
		for (JavaAddition a : java) {
			JavaEdits.Parsed parsed = JavaEdits.parse(a.file(), ctx.plan().current(a.file()));
			switch (parsed.presence(a.type(), a.signature())) {
				case PRESENT -> present.add(a.type() + "." + a.signature().name());
				case ABSENT -> absent.add(a.type() + "." + a.signature().name());
				case CONFLICT -> conflicts.add(a.type() + "에 " + String.join(", ", parsed.signaturesNamed(a.type(), a.signature().name()))
						+ " (원하는 것: " + a.signature().text() + ")");
			}
		}
		if (xml != null) {
			List<XmlEdits.Statement> same = XmlEdits.statements(xml.file(), ctx.plan().current(xml.file()), xml.namespace())
				.stream()
				.filter((s) -> s.id().equals(xml.id()))
				.toList();
			String where = xml.file() + "의 <" + xml.kind() + " id=\"" + xml.id() + "\">";
			if (same.isEmpty()) {
				absent.add(where);
			}
			else if (same.size() == 1 && same.getFirst().kind().equals(xml.kind())) {
				present.add(where);
			}
			else {
				conflicts.add(xml.file() + "에 id=\"" + xml.id() + "\"인 <" + same.getFirst().kind() + "> (원하는 것: <" + xml.kind() + ">)");
			}
		}
		if (!conflicts.isEmpty()) {
			throw new CliException("같은 이름이 다른 시그니처로 있습니다: " + String.join("; ", conflicts)
					+ ". 오버로드를 만들지 않습니다. 다른 이름을 쓰거나 있는 것을 고치세요.");
		}
		if (!present.isEmpty() && !absent.isEmpty()) {
			throw new CliException(what + "의 일부만 있습니다. 있는 곳: " + String.join(", ", present) + ". 없는 곳: " + String.join(", ", absent)
					+ ". 없는 곳을 손으로 맞추거나 있는 것을 지운 뒤 다시 실행하세요.");
		}
		boolean add = present.isEmpty();
		for (JavaAddition a : java) {
			String current = ctx.plan().current(a.file());
			if (!add) {
				ctx.plan().edit(a.file(), current, what);
				continue;
			}
			String next = JavaEdits.addImports(a.file(), current, a.imports());
			next = JavaEdits.addMember(a.file(), next, a.type(), a.member());
			ctx.plan().edit(a.file(), next, a.type() + " += " + a.signature().text());
		}
		if (xml != null) {
			String current = ctx.plan().current(xml.file());
			ctx.plan()
				.edit(xml.file(), add ? XmlEdits.addStatement(xml.file(), current, xml.namespace(), xml.statement()) : current,
						add ? "<" + xml.kind() + " id=\"" + xml.id() + "\">" : what);
		}
	}

	// ── query ─────────────────────────────────────────────────

	private static final Pattern RETURNS = Pattern.compile("(long|int|bool|text)|(list:)?([A-Z][A-Za-z0-9]*)");

	static String query(Context ctx, Args args) {
		args.only("query", 2, "param", "returns", "in");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String name = method(args.positional(1, "메서드 이름"));
		ctx.requireStarter("cozy-starter-sqlite", "query(SQL)");
		ctx.module(module);
		List<Param> params = params(args.all("param"));
		String returnsText = args.required("returns");
		Matcher r = RETURNS.matcher(returnsText);
		if (!r.matches()) {
			throw new CliException("--returns는 long, int, bool, text, <Record>, list:<Record> 중 하나입니다 (예: --returns list:"
					+ Names.capitalize(module) + "Item).");
		}
		String spiDto = ctx.rolePackage(module, Layer.SPI_DTO);
		String infra = ctx.rolePackage(module, Layer.INFRA);
		Set<String> paramImports = new TreeSet<>();
		params.stream().map(Param::importName).filter((i) -> i != null).forEach(paramImports::add);

		String finderType;
		String mapperType;
		String resultType;
		String body;
		Set<String> finderImports = new TreeSet<>(paramImports);
		Set<String> implImports = new TreeSet<>(paramImports);
		Set<String> mapperImports = new TreeSet<>(paramImports);
		List<String> columns = new ArrayList<>();
		String rowName = null;
		String call = "this.{{field}}." + name + "(" + String.join(", ", params.stream().map(Param::name).toList()) + ")";
		if (r.group(1) != null) {
			finderType = switch (r.group(1)) {
				case "long" -> "long";
				case "int" -> "int";
				case "bool" -> "boolean";
				default -> "String";
			};
			mapperType = finderType;
			resultType = switch (r.group(1)) {
				case "bool" -> "boolean";
				case "text" -> "string";
				default -> r.group(1);
			};
			body = "\t\treturn " + call + ";";
		}
		else {
			boolean list = r.group(2) != null;
			String record = r.group(3);
			JavaSources.SourceType dto = ctx.project().sources().types().get(spiDto + "." + record);
			if (dto == null) {
				throw new CliException(spiDto + "." + record + "가 없습니다. 먼저 cozy add dto " + module + " " + record + " --field …로 만드세요.");
			}
			List<String[]> components = new ArrayList<>();
			for (String component : io.salvia95.cozy.cli.internal.Describe.fields(dto.tree())) {
				String type = component.substring(0, component.lastIndexOf(' '));
				String cname = component.substring(component.lastIndexOf(' ') + 1);
				if (!ROW_TYPES.contains(type)) {
					throw new CliException(record + "의 " + cname + "(" + type + ")는 행으로 옮길 수 없습니다. 행에는 원시 값과 날짜만 둡니다. "
							+ "이 query는 손으로 쓰세요.");
				}
				components.add(new String[] { type, cname });
				columns.add(cname);
			}
			rowName = record + "Row";
			String mapping = "(row) -> new " + record + "(" + String.join(", ", components.stream().map((c) -> "row." + c[1] + "()").toList())
					+ ")";
			finderImports.add(spiDto + "." + record);
			implImports.add(spiDto + "." + record);
			implImports.add(infra + "." + rowName);
			if (list) {
				finderType = "List<" + record + ">";
				mapperType = "List<" + rowName + ">";
				finderImports.add("java.util.List");
				implImports.add("java.util.List");
				mapperImports.add("java.util.List");
				body = "\t\treturn " + call + ".stream().map(" + mapping + ").toList();";
			}
			else {
				finderType = "Optional<" + record + ">";
				mapperType = rowName;
				finderImports.add("java.util.Optional");
				implImports.add("java.util.Optional");
				body = "\t\treturn Optional.ofNullable(" + call + ").map(" + mapping + ");";
			}
			resultType = infra + "." + rowName;
			row(ctx, module, record, rowName, components);
		}
		if (!params.isEmpty()) {
			mapperImports.add("org.apache.ibatis.annotations.Param");
		}

		Target target = target(ctx, module, Role.FINDER, args.option("in"));
		MapperUse mapper = mapper(ctx, module, target);
		List<String> types = params.stream().map(Param::type).toList();
		String declared = String.join(", ", params.stream().map(Param::declaration).toList());
		List<JavaAddition> java = List.of(
				new JavaAddition(target.ifaceFile(), target.iface(), new JavaEdits.Signature(name, finderType, types),
						"\t" + finderType + " " + name + "(" + declared + ");", finderImports),
				new JavaAddition(target.implFile(), target.impl(), new JavaEdits.Signature(name, finderType, types),
						"\t@Override\n\tpublic " + finderType + " " + name + "(" + declared + ") {\n"
								+ body.replace("{{field}}", mapper.field()) + "\n\t}",
						implImports),
				new JavaAddition(mapper.mapperFile(), mapper.mapper(), new JavaEdits.Signature(name, mapperType, types),
						"\t" + mapperType + " " + name + "("
								+ String.join(", ", params.stream().map(Param::mapperDeclaration).toList()) + ");",
						mapperImports));
		String paramsText = params.isEmpty() ? "없음" : String.join(", ", params.stream().map((p) -> "#{" + p.name() + "}").toList());
		String columnsText = columns.isEmpty() ? "값 하나" : String.join(", ", columns) + " (행 " + rowName + "의 구성 요소)";
		String statement = "  <select id=\"" + name + "\" resultType=\"" + resultType + "\">\n    <!-- cozy:slot query-sql — SELECT 문을 쓰세요. "
				+ "결과 열: " + columnsText + ". 파라미터: " + paramsText + " -->\n  </select>";
		apply(ctx, "query " + name, java, new XmlAddition(mapper.xmlFile(), mapper.namespace(), "select", name, statement));
		return "다음: 빈칸 query-sql에 SELECT를 씁니다. " + tables(ctx, module) + ". 다른 모듈은 cozy link <모듈> --uses " + module + " 뒤에 "
				+ target.iface() + "." + name + "(…)을 부릅니다";
	}

	/** 행 record. 이미 있으면 구성 요소 이름이 같아야 한다 (타입은 에이전트가 저장 형식에 맞춰 바꿀 수 있다) */
	private static void row(Context ctx, String module, String record, String rowName, List<String[]> components) {
		String pkg = ctx.rolePackage(module, Layer.INFRA);
		String file = ctx.javaFile(pkg, rowName);
		String current = ctx.plan().current(file);
		if (current != null) {
			List<String> names = JavaEdits.parse(file, current)
				.recordComponents(rowName)
				.stream()
				.map((c) -> c.substring(0, c.indexOf(':')))
				.toList();
			List<String> wanted = components.stream().map((c) -> c[1]).toList();
			if (!names.equals(wanted)) {
				throw new CliException(file + "가 이미 있고 구성 요소 이름(" + String.join(", ", names) + ")이 " + record + "("
						+ String.join(", ", wanted) + ")와 다릅니다. 행을 " + record + "에 맞추거나 다른 record를 쓰세요.");
			}
			ctx.plan().edit(file, current, "행 " + rowName);
			return;
		}
		Set<String> imports = new TreeSet<>();
		components.forEach((c) -> {
			if (c[0].equals("LocalDate") || c[0].equals("LocalDateTime") || c[0].equals("Instant")) {
				imports.add("java.time." + c[0]);
			}
		});
		ctx.plan()
			.create(file, new Template().put("pkg", pkg)
				.put("imports", imports.isEmpty() ? "" : String.join("", imports.stream().map((i) -> "import " + i + ";\n").toList()) + "\n")
				.put("Record", record)
				.put("components", String.join(", ", components.stream().map((c) -> c[0] + " " + c[1]).toList()))
				.render("row.java"));
	}

	// ── command ───────────────────────────────────────────────

	static String command(Context ctx, Args args) {
		args.only("command", 2, "param", "returns", "sql", "in");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String name = method(args.positional(1, "메서드 이름"));
		ctx.module(module);
		List<Param> params = params(args.all("param"));
		String returns = "void";
		if (args.option("returns") != null) {
			returns = switch (args.option("returns")) {
				case "long" -> "long";
				case "int" -> "int";
				case "bool" -> "boolean";
				case "text" -> "String";
				default -> throw new CliException("command의 --returns는 long, int, bool, text 중 하나입니다. 빼면 void입니다.");
			};
		}
		String sql = args.option("sql");
		if (sql != null && !Set.of("insert", "update", "delete").contains(sql)) {
			throw new CliException("--sql은 insert, update, delete 중 하나입니다.");
		}
		if (sql != null) {
			ctx.requireStarter("cozy-starter-sqlite", "command --sql(SQL)");
		}
		Set<String> imports = new TreeSet<>();
		params.stream().map(Param::importName).filter((i) -> i != null).forEach(imports::add);
		Target target = target(ctx, module, Role.FACADE, args.option("in"));
		MapperUse mapper = (sql != null) ? mapper(ctx, module, target) : null;
		List<String> types = params.stream().map(Param::type).toList();
		String declared = String.join(", ", params.stream().map(Param::declaration).toList());
		String argsText = String.join(", ", params.stream().map(Param::name).toList());
		String what = (mapper != null)
				? "this." + mapper.field() + "." + name + "(" + argsText + ")로 SQL을 실행하고(영향받은 행 수를 돌려준다) 필요한 규칙을 씁니다"
				: name + "의 본문을 씁니다";
		String impl = "\t@Override\n\tpublic " + returns + " " + name + "(" + declared + ") {\n\t\t// cozy:slot command-body — " + what
				+ ". 이 구현은 이미 @Transactional입니다. 다른 모듈에 알릴 일은 이벤트로 발행합니다\n"
				+ "\t\tthrow new UnsupportedOperationException(\"cozy:slot command-body\");\n\t}";
		List<JavaAddition> java = new ArrayList<>(List.of(
				new JavaAddition(target.ifaceFile(), target.iface(), new JavaEdits.Signature(name, returns, types),
						"\t" + returns + " " + name + "(" + declared + ");", imports),
				new JavaAddition(target.implFile(), target.impl(), new JavaEdits.Signature(name, returns, types), impl, imports)));
		XmlAddition xml = null;
		if (mapper != null) {
			Set<String> mapperImports = new TreeSet<>(imports);
			if (!params.isEmpty()) {
				mapperImports.add("org.apache.ibatis.annotations.Param");
			}
			java.add(new JavaAddition(mapper.mapperFile(), mapper.mapper(), new JavaEdits.Signature(name, "int", types),
					"\tint " + name + "(" + String.join(", ", params.stream().map(Param::mapperDeclaration).toList()) + ");",
					mapperImports));
			String paramsText = params.isEmpty() ? "없음"
					: String.join(", ", params.stream().map((p) -> "#{" + p.name() + "}").toList());
			xml = new XmlAddition(mapper.xmlFile(), mapper.namespace(), sql, name, "  <" + sql + " id=\"" + name + "\">\n"
					+ "    <!-- cozy:slot command-sql — " + sql.toUpperCase() + " 문을 쓰세요. 파라미터: " + paramsText + " -->\n  </" + sql + ">");
		}
		apply(ctx, "command " + name, java, xml);
		StringBuilder next = new StringBuilder("다음: 빈칸 command-body");
		if (xml != null) {
			next.append("와 command-sql을 채웁니다. ").append(tables(ctx, module));
		}
		else {
			next.append("를 채웁니다");
		}
		String implSource = ctx.plan().current(target.implFile());
		if (ctx.project().uses("cozy-starter-events") && !implSource.contains("ApplicationEventPublisher")) {
			next.append(". 이벤트를 발행하려면 ").append(target.impl()).append("의 생성자가 ApplicationEventPublisher를 받아야 합니다(명령은 "
					+ "이미 있는 생성자를 고치지 않습니다)");
		}
		next.append(". 이 모듈의 컨트롤러와 리스너가 ").append(target.iface()).append('.').append(name).append("(…)을 부릅니다");
		return next.toString();
	}

	// ── 테이블 안내 ───────────────────────────────────────────

	private static final Pattern CREATE = Pattern.compile("(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*?)\\)\\s*;");

	private static final Pattern ALTER = Pattern.compile("(?is)ALTER\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+ADD\\s+(?:COLUMN\\s+)?([A-Za-z_][A-Za-z0-9_]*)");

	private static final Set<String> CONSTRAINTS = Set.of("PRIMARY", "UNIQUE", "CHECK", "FOREIGN", "CONSTRAINT");

	/** 마이그레이션에서 읽은 이 모듈의 테이블과 열 (출력 안내용. 파일에는 적지 않는다) */
	static String tables(Context ctx, String module) {
		Map<String, List<String>> tables = new LinkedHashMap<>();
		Path dir = ctx.project().dir().resolve("src/main/resources/db/migration");
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path file : files.filter((f) -> f.toString().endsWith(".sql")).sorted().toList()) {
					String sql = Files.readString(file, StandardCharsets.UTF_8).replaceAll("--[^\n]*", "");
					Matcher c = CREATE.matcher(sql);
					while (c.find()) {
						List<String> columns = tables.computeIfAbsent(c.group(1).toLowerCase(), (k) -> new ArrayList<>());
						for (String part : topLevel(c.group(2))) {
							String word = part.strip().split("\\s+")[0];
							if (!word.isEmpty() && !CONSTRAINTS.contains(word.toUpperCase())) {
								columns.add(word);
							}
						}
					}
					Matcher a = ALTER.matcher(sql);
					while (a.find()) {
						tables.computeIfAbsent(a.group(1).toLowerCase(), (k) -> new ArrayList<>()).add(a.group(2));
					}
				}
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		List<String> mine = tables.entrySet()
			.stream()
			.filter((e) -> e.getKey().equals(module) || e.getKey().startsWith(module + "_"))
			.map((e) -> e.getKey() + "(" + String.join(", ", e.getValue()) + ")")
			.toList();
		return mine.isEmpty() ? "마이그레이션에 " + module + " 테이블이 아직 없습니다(cozy add migration " + module + " <설명>으로 만듭니다)"
				: "테이블: " + String.join(", ", mine);
	}

	/** 괄호 밖의 쉼표로 나눈다 */
	private static List<String> topLevel(String body) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		StringBuilder part = new StringBuilder();
		for (char ch : body.toCharArray()) {
			if (ch == '(') {
				depth++;
			}
			else if (ch == ')') {
				depth--;
			}
			if (ch == ',' && depth == 0) {
				parts.add(part.toString());
				part.setLength(0);
			}
			else {
				part.append(ch);
			}
		}
		parts.add(part.toString());
		return parts;
	}
}
