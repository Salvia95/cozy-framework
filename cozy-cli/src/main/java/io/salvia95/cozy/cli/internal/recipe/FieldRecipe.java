package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.Role;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.TemplateOutline;

/**
 * {@code cozy add field}: {@code cozy add crud}가 만든 한 벌에 필드 하나를 더한다. 표시를 쓰지 않고 파서가 찾은 자리에 넣는다.
 *
 * <ul>
 * <li>새 마이그레이션({@code ALTER TABLE … ADD COLUMN}), 도메인·spi DTO·행·폼·화면 DTO, Facade와 Mapper의 파라미터, Mapper XML,
 * 입력 칸, 스펙의 값.</li>
 * <li>{@code create}·{@code update}와 record 생성자를 부르는 곳의 인자. {@code src/main}에서는 옆 인자와 같은 객체에서 값을 꺼내고,
 * 테스트에는 샘플 값을 넣는다.</li>
 * </ul>
 * 자리를 하나라도 찾지 못하면 추측하지 않고 실패한다. 그때는 아무 파일도 바뀌지 않는다.
 */
final class FieldRecipe {

	private static final String HAND = " 이 필드는 손으로 더하거나, 그 자리를 cozy add crud가 만든 모양으로 되돌린 뒤 다시 실행하세요.";

	private FieldRecipe() {
	}

	static String run(Context ctx, Args args) {
		args.only("field", 1, "entity", "field", "default");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String entity = Names.pascal(args.required("entity"), "엔티티");
		Field field = Field.parse(args.required("field"));
		if (field.kind() == Field.Kind.INSTANT) {
			throw new CliException("crud 필드에는 instant를 쓰지 않습니다. 날짜는 date를 씁니다.");
		}
		ctx.module(module);
		String defaultSql = defaultSql(field, args.option("default"));
		return new FieldRecipe.Run(ctx, module, entity, field, defaultSql).apply();
	}

	/** {@code --default}를 SQL 글자로. 필수 필드가 아니면 {@code null} */
	private static String defaultSql(Field field, String value) {
		boolean needs = field.required() && field.kind() != Field.Kind.BOOL;
		if (!needs) {
			if (value != null) {
				throw new CliException("--default는 필수 필드(!)에만 씁니다. bool은 늘 false로 시작하고, 필수가 아닌 필드는 비어 있습니다.");
			}
			return null;
		}
		if (value == null) {
			throw new CliException("필수 필드(!)를 더하려면 --default <값>이 필요합니다. 이미 있는 행이 그 값을 갖습니다 (예: --field "
					+ field.name() + ":" + field.kind().word() + "! --default " + switch (field.kind()) {
						case INT, LONG -> "0";
						case DATE -> "2026-01-01";
						default -> "없음";
					} + ").");
		}
		switch (field.kind()) {
			case INT, LONG -> {
				if (!value.matches("-?\\d{1,18}")) {
					throw new CliException("--default는 숫자여야 합니다: " + value);
				}
				return value;
			}
			case DATE -> {
				try {
					LocalDate.parse(value);
				}
				catch (DateTimeParseException ex) {
					throw new CliException("--default는 yyyy-MM-dd 날짜여야 합니다: " + value);
				}
			}
			default -> {
				if (value.isEmpty()) {
					throw new CliException("--default는 빈 글자일 수 없습니다. 필수 글 필드는 빈 값을 받지 않습니다.");
				}
			}
		}
		return "'" + value.replace("'", "''") + "'";
	}

	/** 고칠 Java 파일 하나. 넣을 글자를 처음 읽은 위치로 모았다가 한 번에 넣는다 */
	private static final class Source {

		final String file;

		final String text;

		final JavaEdits.Parsed parsed;

		private final TreeMap<Integer, StringBuilder> inserts = new TreeMap<>();

		private final Set<String> imports = new LinkedHashSet<>();

		Source(String file, String text) {
			this.file = file;
			this.text = text;
			this.parsed = JavaEdits.parse(file, text);
		}

		void insert(int at, String added) {
			this.inserts.computeIfAbsent(at, (k) -> new StringBuilder()).append(added);
		}

		void use(String importName) {
			if (importName != null) {
				this.imports.add(importName);
			}
		}

		boolean changed() {
			return !this.inserts.isEmpty();
		}

		String result() {
			StringBuilder out = new StringBuilder(this.text);
			this.inserts.descendingMap().forEach((at, added) -> out.insert(at, added));
			String result = out.toString();
			JavaEdits.parse(this.file, result);
			return JavaEdits.addImports(this.file, result, this.imports);
		}

		int start(Tree tree) {
			return this.parsed.start(tree);
		}

		int end(Tree tree) {
			return this.parsed.end(tree);
		}

		long line(Tree tree) {
			return this.parsed.unit().getLineMap().getLineNumber(start(tree));
		}

		String at(Tree tree) {
			return this.file + ":" + line(tree);
		}

		String code(Tree tree) {
			return this.text.substring(start(tree), end(tree));
		}

		/** 그 위치가 있는 줄의 시작 */
		int lineStart(int offset) {
			return this.text.lastIndexOf('\n', offset - 1) + 1;
		}

		/** 타입 본문의 마지막 멤버 뒤 (닫는 중괄호 앞의 공백을 뺀 자리) */
		int bodyEnd(ClassTree type) {
			int close = end(type) - 1;
			while (close > 0 && Character.isWhitespace(this.text.charAt(close - 1))) {
				close--;
			}
			return close;
		}
	}

	/** 인자를 더할 호출의 종류 */
	private enum Call {

		FACADE_CREATE, FACADE_UPDATE, MAPPER_UPDATE, ITEM, DOMAIN, ROW, DETAIL
	}

	private static final class Run {

		private final Context ctx;

		private final String module;

		private final String entity;

		private final Field field;

		private final String defaultSql;

		private final String base;

		private final Map<String, Source> sources = new LinkedHashMap<>();

		private final Map<String, String> details = new LinkedHashMap<>();

		/** 파라미터를 더한 메서드. 그 안의 호출에는 새 파라미터를 그대로 넘긴다 */
		private final Set<MethodTree> extended = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

		private final Map<Call, Integer> arity = new java.util.EnumMap<>(Call.class);

		/** 고치기 전에 있던 필드 이름 (id와 시각은 빼고) */
		private List<String> existing = List.of();

		private final String domainFile;

		private final String itemFile;

		private final String rowFile;

		private final String mapperFile;

		private final String facadeFile;

		private final String facadeImplFile;

		private final String finderImplFile;

		private final String formFile;

		private final String viewsFile;

		private final String controllerFile;

		private final String xmlFile;

		private final String fieldsFile;

		Run(Context ctx, String module, String entity, Field field, String defaultSql) {
			this.ctx = ctx;
			this.module = module;
			this.entity = entity;
			this.field = field;
			this.defaultSql = defaultSql;
			this.base = ctx.modulePackage(module);
			this.domainFile = java(Role.DOMAIN, entity);
			this.itemFile = java(Role.SPI_DTO, entity + "Item");
			this.rowFile = java(Role.ROW, entity);
			this.mapperFile = java(Role.MAPPER, entity);
			this.facadeFile = java(Role.FACADE, entity);
			this.facadeImplFile = java(Role.FACADE_IMPL, entity);
			this.finderImplFile = java(Role.FINDER_IMPL, entity);
			this.formFile = java(Role.FORM, entity);
			this.viewsFile = java(Role.VIEWS, entity);
			this.controllerFile = java(Role.CONTROLLER, entity);
			this.xmlFile = "src/main/resources/mapper/" + module + "/" + entity + "Mapper.xml";
			this.fieldsFile = "src/main/resources/templates/" + module + "/internal/fields.html";
		}

		private String java(Role role, String typeName) {
			return this.ctx.javaFile(role.layer().packageIn(this.base), role.suffix().isEmpty() ? typeName : role.typeName(typeName));
		}

		private String pkg(Layer layer) {
			return layer.packageIn(this.base);
		}

		String apply() {
			List<String> missing = Stream
				.of(this.domainFile, this.itemFile, this.rowFile, this.mapperFile, this.facadeFile, this.facadeImplFile,
						this.finderImplFile, this.formFile, this.viewsFile, this.controllerFile, this.xmlFile, this.fieldsFile)
				.filter((file) -> this.ctx.plan().current(file) == null)
				.toList();
			if (!missing.isEmpty()) {
				throw new CliException(this.module + "의 " + this.entity + "는 crud 한 벌이 아닙니다. 없는 파일: " + String.join(", ", missing)
						+ ". cozy add field는 cozy add crud가 만든 한 벌에만 필드를 더합니다.");
			}
			if (alreadyThere()) {
				return "다음: 바꿀 것이 없습니다. " + this.field.name() + " 필드가 이미 있습니다";
			}
			String xml = this.ctx.plan().current(this.xmlFile);
			String table = table(xml);
			this.existing = components(source(this.domainFile).parsed.type(this.entity)).stream()
				.map((c) -> c.getName().toString())
				.filter((n) -> !Set.of("id", "createdAt", "updatedAt").contains(n))
				.toList();
			records();
			row();
			mapper();
			facade();
			facadeImpl();
			views();
			form();
			callers();
			screenTest();
			String newXml = xml(xml);
			String newFields = fields();
			Map<String, String> specs = specs();

			String column = this.field.column();
			String migration = "src/main/resources/db/migration/V" + this.ctx.nextMigrationTime() + "__" + this.module + "_add_" + table
					+ "_" + column + ".sql";
			String definition = this.field.sql().replaceAll(" +", " ");
			if (this.defaultSql != null) {
				definition = definition.replaceFirst("NOT NULL", "NOT NULL DEFAULT " + Matcher.quoteReplacement(this.defaultSql));
			}
			this.ctx.plan()
				.create(migration, "-- " + table + "." + column + " (cozy add field). 배포한 마이그레이션은 고치지 않고 새 마이그레이션을 더한다 "
						+ "(docs/conventions/sqlite.md)\nALTER TABLE " + table + " ADD COLUMN " + column + " " + definition + ";\n");
			String what = this.field.name() + " 필드";
			for (Source source : this.sources.values()) {
				if (source.changed()) {
					this.ctx.plan().edit(source.file, source.result(), this.details.getOrDefault(source.file, what));
				}
			}
			this.ctx.plan().edit(this.xmlFile, newXml, column + " 열");
			this.ctx.plan().edit(this.fieldsFile, newFields, this.field.label() + " 입력 칸");
			specs.forEach((file, content) -> this.ctx.plan().edit(file, content, this.field.name() + " 값"));
			screens();

			StringBuilder next = new StringBuilder("다음: mvn verify로 확인합니다. 검증과 기본값 같은 업무 규칙은 만든 코드를 고칩니다");
			if (this.field.kind() == Field.Kind.TEXT || this.field.kind() == Field.Kind.LONGTEXT) {
				next.append(". 목록 검색에는 넣지 않았습니다(넣으려면 ").append(this.entity).append("Mapper.xml의 searchCondition)");
			}
			if (this.ctx.project().uses("cozy-starter-events")) {
				next.append(". 이벤트에 실으려면 cozy add event-version ").append(this.module).append(" <이벤트> --field ")
					.append(this.field.name()).append(':').append(this.field.kind().word());
			}
			return next.toString();
		}

		private Source source(String file) {
			return this.sources.computeIfAbsent(file, (f) -> new Source(f, this.ctx.plan().current(f)));
		}

		private CliException unreadable(String file, String expected) {
			return new CliException(file + "에서 " + expected + "를 찾지 못했습니다." + HAND);
		}

		// ---- 이미 있는가

		private boolean alreadyThere() {
			String name = this.field.name();
			Source domain = source(this.domainFile);
			for (VariableTree component : components(domain.parsed.type(this.entity))) {
				String type = JavaEdits.normalize(component.getType().toString());
				if (component.getName().contentEquals(name) && !type.equals(this.field.javaType())) {
					throw new CliException(this.domainFile + "에 " + name + "가 이미 " + type + "로 있습니다(원하는 것: " + this.field.javaType()
							+ "). 다른 이름을 쓰세요.");
				}
			}
			Map<String, Boolean> present = new LinkedHashMap<>();
			present.put(this.domainFile, has(components(domain.parsed.type(this.entity)), name));
			present.put(this.itemFile, has(components(source(this.itemFile).parsed.type(this.entity + "Item")), name));
			present.put(this.rowFile, has(components(source(this.rowFile).parsed.type(this.entity + "Row")), name));
			present.put(this.formFile, has(components(source(this.formFile).parsed.type(this.entity + "Form")), name));
			Source views = source(this.viewsFile);
			ClassTree detail = nested(views, views.parsed.type(this.entity + "Views"), "Detail");
			present.put(this.viewsFile, has(components(detail), name) || has(components(detail), name + "Lines"));
			Source facade = source(this.facadeFile);
			present.put(this.facadeFile, method(facade, facade.parsed.type(this.entity + "Facade"), "create").getParameters()
				.stream()
				.anyMatch((p) -> p.getName().contentEquals(name)));
			present.put(this.xmlFile, columns(this.ctx.plan().current(this.xmlFile)).contains(this.field.column()));
			if (!present.containsValue(true)) {
				return false;
			}
			if (!present.containsValue(false)) {
				return true;
			}
			throw new CliException(name + " 필드가 일부에만 있습니다. 있는 곳: " + where(present, true) + ". 없는 곳: " + where(present, false)
					+ ". 없는 곳에 손으로 더하거나, 있는 곳에서 지운 뒤 다시 실행하세요.");
		}

		private static String where(Map<String, Boolean> present, boolean value) {
			return String.join(", ", present.entrySet().stream().filter((e) -> e.getValue() == value).map(Map.Entry::getKey).toList());
		}

		private static boolean has(List<VariableTree> variables, String name) {
			return variables.stream().anyMatch((v) -> v.getName().contentEquals(name));
		}

		// ---- Java 선언

		/** static이 아닌 필드. record면 구성 요소다 */
		private static List<VariableTree> components(ClassTree type) {
			List<VariableTree> result = new ArrayList<>();
			for (Tree member : type.getMembers()) {
				if (member instanceof VariableTree variable
						&& !variable.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)) {
					result.add(variable);
				}
			}
			return result;
		}

		private ClassTree nested(Source source, ClassTree outer, String name) {
			List<ClassTree> found = new ArrayList<>();
			for (Tree member : outer.getMembers()) {
				if (member instanceof ClassTree type && type.getSimpleName().contentEquals(name)) {
					found.add(type);
				}
			}
			if (found.size() != 1) {
				throw unreadable(source.file, outer.getSimpleName() + " 안의 record " + name);
			}
			return found.getFirst();
		}

		private MethodTree method(Source source, ClassTree type, String name) {
			List<MethodTree> found = new ArrayList<>();
			for (Tree member : type.getMembers()) {
				if (member instanceof MethodTree method && method.getName().contentEquals(name)) {
					found.add(method);
				}
			}
			if (found.size() != 1) {
				throw unreadable(source.file, "메서드 " + name + "(…) 하나(" + found.size() + "개 있음)");
			}
			return found.getFirst();
		}

		/** record의 구성 요소를 {@code before} 구성 요소 앞에 더한다. 고치기 전의 구성 요소 수를 돌려준다 */
		private int component(Source source, ClassTree record, String before, String declaration, List<String> tail) {
			List<VariableTree> components = components(record);
			List<String> names = components.stream().map((c) -> c.getName().toString()).toList();
			if (record.getKind() != Tree.Kind.RECORD || names.size() < tail.size()
					|| !names.subList(names.size() - tail.size(), names.size()).equals(tail)) {
				throw unreadable(source.file, "끝이 " + String.join(", ", tail) + "인 record " + record.getSimpleName());
			}
			VariableTree anchor = components.get(names.indexOf(before));
			source.insert(source.start(anchor), declaration + ", ");
			source.use(this.field.importName());
			docParam(source, record, before, declaration.substring(declaration.lastIndexOf(' ') + 1));
			return components.size();
		}

		/**
		 * record의 Javadoc에 {@code @param {before}} 줄이 있으면 그 앞에 새 구성 요소의 줄을 더한다. 설명은 필드의 라벨이다. 줄이 없으면(설명을 적지 않은
		 * record) 더하지 않는다.
		 */
		private void docParam(Source source, ClassTree record, String before, String name) {
			int declaration = source.lineStart(source.start(record));
			int close = source.text.lastIndexOf("*/", declaration);
			int open = source.text.lastIndexOf("/**", declaration);
			if (close < 0 || open < 0 || open > close || !source.text.substring(close + 2, declaration).isBlank()) {
				return;
			}
			java.util.regex.Matcher line = java.util.regex.Pattern.compile("(?m)^([ \\t]*\\*[ \\t]*)@param[ \\t]+" + before + "\\b")
				.matcher(source.text)
				.region(open, close);
			if (line.find()) {
				String label = (this.field.kind() == Field.Kind.LONGTEXT && name.endsWith("Lines")) ? this.field.label() + " (줄마다)"
						: this.field.label();
				source.insert(line.start(), line.group(1) + "@param " + name + " " + label + "\n");
			}
		}

		private void records() {
			String declaration = this.field.javaType() + " " + this.field.name();
			List<String> tail = List.of("createdAt", "updatedAt");
			Source domain = source(this.domainFile);
			this.arity.put(Call.DOMAIN, component(domain, domain.parsed.type(this.entity), "createdAt", declaration, tail));
			Source item = source(this.itemFile);
			this.arity.put(Call.ITEM, component(item, item.parsed.type(this.entity + "Item"), "createdAt", declaration, tail));
		}

		private void row() {
			Source source = source(this.rowFile);
			ClassTree type = source.parsed.type(this.entity + "Row");
			List<? extends Tree> members = type.getMembers();
			Tree lastField = null;
			Tree lastAccessor = null;
			for (int i = 1; i < members.size(); i++) {
				if (members.get(i) instanceof VariableTree v && v.getName().contentEquals("createdAt")
						&& members.get(i - 1) instanceof VariableTree) {
					lastField = members.get(i - 1);
				}
				if (members.get(i) instanceof MethodTree m && m.getName().contentEquals("getCreatedAt")
						&& members.get(i - 1) instanceof MethodTree) {
					lastAccessor = members.get(i - 1);
				}
			}
			if (lastField == null || lastAccessor == null) {
				throw unreadable(this.rowFile, "필드 createdAt과 getCreatedAt()(그 앞에 다른 필드와 접근자가 있는 모양)");
			}
			source.insert(source.end(lastField), "\n\n\tprivate " + this.field.javaType() + " " + this.field.name() + ";");
			source.insert(source.end(lastAccessor), "\n\n" + CrudRecipe.Model.accessors(this.field).stripTrailing());
			source.use(this.field.importName());
		}

		private void mapper() {
			Source source = source(this.mapperFile);
			MethodTree update = method(source, source.parsed.type(this.entity + "Mapper"), "update");
			List<? extends VariableTree> params = update.getParameters();
			if (params.isEmpty() || !params.getLast().getName().contentEquals("updatedAt")) {
				throw unreadable(this.mapperFile, "마지막 파라미터가 updatedAt인 update(…)");
			}
			source.insert(source.start(params.getLast()),
					"@Param(\"" + this.field.name() + "\") " + this.field.javaType() + " " + this.field.name() + ", ");
			source.use(this.field.importName());
			this.arity.put(Call.MAPPER_UPDATE, params.size());
		}

		/** 파라미터 목록의 끝에 더한다. 고치기 전의 파라미터 수를 돌려준다 */
		private int parameter(Source source, MethodTree method) {
			List<? extends VariableTree> params = method.getParameters();
			if (params.isEmpty()) {
				throw unreadable(source.file, "파라미터가 있는 " + method.getName() + "(…)");
			}
			source.insert(source.end(params.getLast()), ", " + this.field.javaType() + " " + this.field.name());
			source.use(this.field.importName());
			this.extended.add(method);
			return params.size();
		}

		private void facade() {
			Source source = source(this.facadeFile);
			ClassTree type = source.parsed.type(this.entity + "Facade");
			this.arity.put(Call.FACADE_CREATE, parameter(source, method(source, type, "create")));
			this.arity.put(Call.FACADE_UPDATE, parameter(source, method(source, type, "update")));
		}

		private void facadeImpl() {
			Source source = source(this.facadeImplFile);
			ClassTree type = source.parsed.type(this.entity + "FacadeImpl");
			MethodTree create = method(source, type, "create");
			MethodTree update = method(source, type, "update");
			if (parameter(source, create) != this.arity.get(Call.FACADE_CREATE)
					|| parameter(source, update) != this.arity.get(Call.FACADE_UPDATE)) {
				throw unreadable(this.facadeImplFile, this.entity + "Facade와 파라미터 수가 같은 create(…)와 update(…)");
			}
			List<ExpressionStatementTree> stamps = new ArrayList<>();
			new TreeScanner<Void, Void>() {
				@Override
				public Void visitExpressionStatement(ExpressionStatementTree node, Void unused) {
					if (node.getExpression() instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select
							&& select.getIdentifier().contentEquals("setCreatedAt")) {
						stamps.add(node);
					}
					return super.visitExpressionStatement(node, unused);
				}
			}.scan(create.getBody(), null);
			if (stamps.size() != 1) {
				throw unreadable(this.facadeImplFile, "create(…) 안의 row.setCreatedAt(…) 문 하나");
			}
			ExpressionStatementTree stamp = stamps.getFirst();
			String receiver = source.code(((MemberSelectTree) ((MethodInvocationTree) stamp.getExpression()).getMethodSelect()).getExpression());
			int lineStart = source.lineStart(source.start(stamp));
			String indent = source.text.substring(lineStart, source.start(stamp));
			source.insert(lineStart, indent + receiver + ".set" + this.field.pascal() + "(" + stored() + ");\n");
			if (this.field.kind() == Field.Kind.LONGTEXT && type.getMembers()
				.stream()
				.noneMatch((m) -> m instanceof MethodTree method && method.getName().contentEquals("nullToEmpty"))) {
				source.insert(source.bodyEnd(type), "\n\n\tprivate static String nullToEmpty(String value) {\n"
						+ "\t\treturn (value != null) ? value : \"\";\n\t}");
			}
		}

		/** 저장할 때 넘기는 식. 긴 글은 null을 빈 글자로 바꾼다 */
		private String stored() {
			return (this.field.kind() == Field.Kind.LONGTEXT) ? "nullToEmpty(" + this.field.name() + ")" : this.field.name();
		}

		private void views() {
			Source source = source(this.viewsFile);
			ClassTree type = source.parsed.type(this.entity + "Views");
			String name = this.field.name();
			boolean longtext = this.field.kind() == Field.Kind.LONGTEXT;
			if (!longtext) {
				this.arity.put(Call.ROW, component(source, nested(source, type, "Row"), "updatedAt",
						this.field.javaType() + " " + name, List.of("updatedAt")));
			}
			this.arity.put(Call.DETAIL, component(source, nested(source, type, "Detail"), "createdAt",
					longtext ? "List<String> " + name + "Lines" : this.field.javaType() + " " + name, List.of("createdAt", "updatedAt")));

			MethodTree formOf = method(source, type, "formOf");
			List<? extends StatementTree> statements = (formOf.getBody() != null) ? formOf.getBody().getStatements() : List.of();
			if (formOf.getParameters().size() != 1 || statements.isEmpty() || !(statements.getLast() instanceof ReturnTree last)
					|| !(last.getExpression() instanceof IdentifierTree form)) {
				throw unreadable(this.viewsFile, "파라미터가 하나이고 폼 변수를 돌려주며 끝나는 formOf(…)");
			}
			String item = formOf.getParameters().getFirst().getName().toString();
			int lineStart = source.lineStart(source.start(last));
			source.insert(lineStart, source.text.substring(lineStart, source.start(last)) + form.getName() + ".set" + this.field.pascal()
					+ "(" + item + "." + name + "());\n");

			MethodTree unchanged = method(source, type, "unchanged");
			List<? extends StatementTree> body = (unchanged.getBody() != null) ? unchanged.getBody().getStatements() : List.of();
			if (unchanged.getParameters().size() != 2 || body.size() != 1 || !(body.getFirst() instanceof ReturnTree same)) {
				throw unreadable(this.viewsFile, "파라미터가 둘이고 return 문 하나로 된 unchanged(…)");
			}
			String a = unchanged.getParameters().get(0).getName() + "." + name + "()";
			String b = unchanged.getParameters().get(1).getName() + "." + this.field.getter() + "()";
			source.insert(source.end(same.getExpression()), "\n\t\t\t\t&& " + switch (this.field.kind()) {
				case BOOL -> a + " == " + b;
				case TEXT, LONGTEXT -> "Objects.equals(nullToEmpty(" + a + "), nullToEmpty(" + b + "))";
				default -> "Objects.equals(" + a + ", " + b + ")";
			});
			source.use("java.util.Objects");
		}

		/** 폼에 지금 있는 속성 이름 */
		private List<String> formFields() {
			return components(source(this.formFile).parsed.type(this.entity + "Form")).stream().map((v) -> v.getName().toString()).toList();
		}

		private void form() {
			Source source = source(this.formFile);
			ClassTree type = source.parsed.type(this.entity + "Form");
			List<VariableTree> fields = components(type);
			if (fields.isEmpty()) {
				throw unreadable(this.formFile, "속성이 하나 이상 있는 " + this.entity + "Form");
			}
			source.insert(source.end(fields.getLast()), "\n\n" + CrudRecipe.Model.formDeclaration(this.field).stripTrailing());
			source.insert(source.bodyEnd(type), "\n\n" + CrudRecipe.Model.accessors(this.field).stripTrailing());
			source.use(this.field.importName());
			CrudRecipe.Model.formAnnotations(this.field).forEach(source::use);
		}

		// ---- 호출하는 곳

		private void callers() {
			for (String root : List.of("src/main/java", "src/test/java")) {
				for (String file : javaFiles(root)) {
					String text = this.ctx.plan().current(file);
					if (text != null && text.contains(this.entity)) {
						calls(source(file));
					}
				}
			}
		}

		private List<String> javaFiles(String root) {
			Path dir = this.ctx.project().dir().resolve(root);
			if (!Files.isDirectory(dir)) {
				return List.of();
			}
			try (Stream<Path> files = Files.walk(dir)) {
				return files.filter((p) -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java")
						&& !p.getFileName().toString().equals("package-info.java"))
					.map((p) -> this.ctx.project().dir().relativize(p).toString().replace('\\', '/'))
					.sorted()
					.toList();
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		private void calls(Source source) {
			Set<String> facades = new HashSet<>();
			Set<String> mappers = new HashSet<>();
			new TreeScanner<Void, Void>() {
				@Override
				public Void visitVariable(VariableTree node, Void unused) {
					if (node.getType() != null) {
						String type = simple(node.getType().toString());
						if (type.equals(Run.this.entity + "Facade")) {
							facades.add(node.getName().toString());
						}
						if (type.equals(Run.this.entity + "Mapper")) {
							mappers.add(node.getName().toString());
						}
					}
					return super.visitVariable(node, unused);
				}
			}.scan(source.parsed.unit(), null);
			new TreeScanner<Void, Void>() {

				private MethodTree method;

				@Override
				public Void visitMethod(MethodTree node, Void unused) {
					MethodTree outer = this.method;
					this.method = node;
					super.visitMethod(node, unused);
					this.method = outer;
					return null;
				}

				@Override
				public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
					if (node.getMethodSelect() instanceof MemberSelectTree select) {
						String name = select.getIdentifier().toString();
						String receiver = variable(select.getExpression());
						if (receiver != null && facades.contains(receiver) && name.equals("create")) {
							argument(source, node, node.getArguments(), Call.FACADE_CREATE, this.method);
						}
						else if (receiver != null && facades.contains(receiver) && name.equals("update")) {
							argument(source, node, node.getArguments(), Call.FACADE_UPDATE, this.method);
						}
						else if (receiver != null && mappers.contains(receiver) && name.equals("update")) {
							argument(source, node, node.getArguments(), Call.MAPPER_UPDATE, this.method);
						}
					}
					return super.visitMethodInvocation(node, unused);
				}

				@Override
				public Void visitNewClass(NewClassTree node, Void unused) {
					Call call = constructed(source, node.getIdentifier().toString());
					if (call != null && Run.this.arity.containsKey(call)) {
						argument(source, node, node.getArguments(), call, this.method);
					}
					return super.visitNewClass(node, unused);
				}
			}.scan(source.parsed.unit(), null);
		}

		private static String simple(String type) {
			String raw = type.contains("<") ? type.substring(0, type.indexOf('<')) : type;
			return raw.substring(raw.lastIndexOf('.') + 1);
		}

		/** {@code x} 또는 {@code this.x}의 x */
		private static String variable(ExpressionTree tree) {
			if (tree instanceof IdentifierTree identifier) {
				return identifier.getName().toString();
			}
			if (tree instanceof MemberSelectTree select && select.getExpression() instanceof IdentifierTree owner
					&& owner.getName().contentEquals("this")) {
				return select.getIdentifier().toString();
			}
			return null;
		}

		/** {@code new 이름(…)}이 이 crud의 record를 만드는가 */
		private Call constructed(Source source, String name) {
			String views = this.entity + "Views";
			if ((name.equals("Row") && source.file.equals(this.viewsFile)) || name.equals(views + ".Row") || name.endsWith("." + views + ".Row")) {
				return Call.ROW;
			}
			if ((name.equals("Detail") && source.file.equals(this.viewsFile)) || name.equals(views + ".Detail")
					|| name.endsWith("." + views + ".Detail")) {
				return Call.DETAIL;
			}
			if (names(source, name, pkg(Layer.SPI_DTO), this.entity + "Item")) {
				return Call.ITEM;
			}
			return names(source, name, pkg(Layer.DOMAIN), this.entity) ? Call.DOMAIN : null;
		}

		/** 이 파일에서 {@code written}이 {@code pkg.type}을 가리키는가: 완전한 이름, import, 같은 패키지 */
		private static boolean names(Source source, String written, String pkg, String type) {
			if (written.equals(pkg + "." + type)) {
				return true;
			}
			return written.equals(type) && (source.parsed.packageName().equals(pkg) || source.parsed.imports().contains(pkg + "." + type)
					|| source.parsed.imports().contains(pkg + ".*"));
		}

		private void argument(Source source, Tree call, List<? extends ExpressionTree> arguments, Call kind, MethodTree method) {
			int expected = this.arity.get(kind);
			if (arguments.size() != expected) {
				throw new CliException(source.at(call) + "의 호출은 인자가 " + arguments.size() + "개입니다(기대: " + expected
						+ "개). 어느 자리에 넣을지 정할 수 없습니다. 이 호출을 지금 시그니처에 맞게 고친 뒤 다시 실행하세요.");
			}
			// 넣을 자리: Facade는 끝, 그 밖에는 시각(createdAt, updatedAt) 앞
			int index = switch (kind) {
				case FACADE_CREATE, FACADE_UPDATE -> expected;
				case MAPPER_UPDATE, ROW -> expected - 1;
				case ITEM, DOMAIN, DETAIL -> expected - 2;
			};
			String value = value(source, call, arguments, index, kind, method);
			if (index == expected) {
				source.insert(source.end(arguments.getLast()), ", " + value);
			}
			else {
				source.insert(source.start(arguments.get(index)), value + ", ");
			}
		}

		private String value(Source source, Tree call, List<? extends ExpressionTree> arguments, int index, Call kind, MethodTree method) {
			String name = this.field.name();
			if (method != null && this.extended.contains(method)) {
				return (kind == Call.MAPPER_UPDATE) ? stored() : name;
			}
			if (source.file.startsWith("src/test/")) {
				source.use(this.field.importName());
				return this.field.javaSample();
			}
			for (int distance = 1; distance <= arguments.size(); distance++) {
				for (int at : new int[] { index - distance, index + distance - 1 }) {
					if (at < 0 || at >= arguments.size()) {
						continue;
					}
					String derived = derive(source, arguments.get(at), kind);
					if (derived != null) {
						return derived;
					}
				}
			}
			throw new CliException(source.at(call) + "의 호출에 넘길 값을 정할 수 없습니다. 옆 인자가 form.getTitle()이나 item.title()처럼 한 객체에서 "
					+ "꺼낸 값이 아닙니다. " + name + "에 넘길 값을 정할 수 있게 이 호출을 고치거나, 필드를 손으로 더하세요.");
		}

		/** 옆 인자가 {@code r.getX()}나 {@code r.x()}로 시작하면 같은 객체에서 새 필드를 꺼내는 식 */
		private String derive(Source source, ExpressionTree argument, Call kind) {
			ExpressionTree current = argument;
			String accessor = null;
			boolean noArguments = false;
			while (current instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select) {
				accessor = select.getIdentifier().toString();
				noArguments = call.getArguments().isEmpty();
				current = select.getExpression();
			}
			String owner = variable(current);
			if (accessor == null || !noArguments || owner == null || !Character.isLowerCase(owner.charAt(0))) {
				return null;
			}
			String receiver = source.code(current);
			if (accessor.matches("(get|is)[A-Z].*")) {
				return receiver + "." + this.field.getter() + "()";
			}
			String read = receiver + "." + this.field.name() + "()";
			return (kind == Call.DETAIL && this.field.kind() == Field.Kind.LONGTEXT) ? read + ".lines().toList()" : read;
		}

		/** 화면 테스트의 폼 전송: 지금 있는 속성을 모두 보내는 {@code .param(…)} 줄에 새 속성을 더한다 */
		private void screenTest() {
			String file = this.ctx.testFile(this.base, this.entity + "ScreenTest");
			if (this.ctx.plan().current(file) == null) {
				return;
			}
			Source source = source(file);
			List<MethodInvocationTree> params = new ArrayList<>();
			new TreeScanner<Void, Void>() {
				@Override
				public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
					if (paramName(node) != null) {
						params.add(node);
					}
					return super.visitMethodInvocation(node, unused);
				}
			}.scan(source.parsed.unit(), null);
			Set<MethodInvocationTree> inner = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			Map<MethodInvocationTree, Set<String>> sent = new IdentityHashMap<>();
			for (MethodInvocationTree param : params) {
				Set<String> names = new HashSet<>();
				ExpressionTree current = param;
				while (current instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select) {
					if (paramName(call) != null) {
						names.add(paramName(call));
						if (call != param) {
							inner.add(call);
						}
					}
					current = select.getExpression();
				}
				sent.put(param, names);
			}
			List<String> fields = formFields();
			int added = 0;
			for (MethodInvocationTree param : params) {
				if (!inner.contains(param) && sent.get(param).containsAll(fields)) {
					source.insert(source.end(param), ".param(\"" + this.field.name() + "\", " + CrudRecipe.Model.paramValue(this.field) + ")");
					added++;
				}
			}
			if (added == 0) {
				throw unreadable(file, "폼의 속성(" + String.join(", ", fields) + ")을 모두 보내는 .param(…) 줄");
			}
		}

		/** {@code x.param("이름", …)}의 이름 */
		private static String paramName(MethodInvocationTree call) {
			if (call.getMethodSelect() instanceof MemberSelectTree select && select.getIdentifier().contentEquals("param")
					&& call.getArguments().size() >= 2 && call.getArguments().getFirst() instanceof LiteralTree literal
					&& literal.getValue() instanceof String name) {
				return name;
			}
			return null;
		}

		// ---- Mapper XML

		/** {@code <tag id="id">}의 본문 범위 [시작, 끝) */
		private int[] body(String xml, String tag, String id) {
			Matcher open = Pattern.compile("<" + tag + "\\b[^>]*\\bid=\"" + id + "\"[^>]*>").matcher(xml);
			if (!open.find() || open.group().endsWith("/>")) {
				throw unreadable(this.xmlFile, "<" + tag + " id=\"" + id + "\">");
			}
			int start = open.end();
			int end = xml.indexOf("</" + tag + ">", start);
			if (open.find() || end < 0) {
				throw unreadable(this.xmlFile, "<" + tag + " id=\"" + id + "\"> 하나");
			}
			return new int[] { start, end };
		}

		/** 범위 안에서 한 번 나오는 글자의 위치 */
		private int once(String xml, int[] range, String regex, String what) {
			Matcher matcher = Pattern.compile(regex).matcher(xml).region(range[0], range[1]);
			if (!matcher.find()) {
				throw unreadable(this.xmlFile, what);
			}
			int at = matcher.start();
			if (matcher.find()) {
				throw unreadable(this.xmlFile, what + " 하나");
			}
			return at;
		}

		private List<String> columns(String xml) {
			int[] range = body(xml, "sql", "columns");
			return Stream.of(xml.substring(range[0], range[1]).split(",")).map(String::strip).toList();
		}

		private String table(String xml) {
			XmlEdits.statements(this.xmlFile, xml, pkg(Layer.INFRA) + "." + this.entity + "Mapper");
			int[] insert = body(xml, "insert", "insert");
			Matcher into = Pattern.compile("INSERT\\s+INTO\\s+([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE)
				.matcher(xml)
				.region(insert[0], insert[1]);
			if (!into.find()) {
				throw unreadable(this.xmlFile, "<insert id=\"insert\">의 INSERT INTO <테이블>");
			}
			return into.group(1);
		}

		private String xml(String xml) {
			List<String> expected = components(source(this.domainFile).parsed.type(this.entity)).stream()
				.map((c) -> Names.snakeOf(c.getName().toString()))
				.toList();
			if (!columns(xml).equals(expected)) {
				throw new CliException(this.xmlFile + "의 <sql id=\"columns\"> 열 순서가 도메인 record " + this.entity + "의 구성 요소 순서와 다릅니다(기대: "
						+ String.join(", ", expected) + ")." + HAND);
			}
			String column = this.field.column();
			String name = this.field.name();
			TreeMap<Integer, String> inserts = new TreeMap<>();
			inserts.put(once(xml, body(xml, "sql", "columns"), "\\bcreated_at\\b", "<sql id=\"columns\">의 created_at"), column + ", ");
			int[] insert = body(xml, "insert", "insert");
			inserts.put(once(xml, insert, "\\bcreated_at\\b", "<insert id=\"insert\">의 created_at"), column + ", ");
			inserts.put(once(xml, insert, "#\\{createdAt\\}", "<insert id=\"insert\">의 #{createdAt}"), "#{" + name + "}, ");
			inserts.put(once(xml, body(xml, "update", "update"), "\\bupdated_at\\s*=\\s*#\\{updatedAt\\}",
					"<update id=\"update\">의 updated_at = #{updatedAt}"), column + " = #{" + name + "}, ");
			StringBuilder out = new StringBuilder(xml);
			inserts.descendingMap().forEach(out::insert);
			XmlEdits.read(this.xmlFile, out.toString());
			return out.toString();
		}

		// ---- 입력 칸

		private String fields() {
			String html = this.ctx.plan().current(this.fieldsFile);
			String fragment = Names.camel(this.entity) + "Fields";
			List<TemplateOutline.Node> declared = TemplateOutline
				.of(new TemplateFile(this.module + "/internal/fields", Origin.APP, this.fieldsFile, html))
				.stream()
				.filter((n) -> n.kind().equals("fragment") && fragment.equals(n.fragment()))
				.toList();
			List<String> lines = new ArrayList<>(html.lines().toList());
			if (declared.size() != 1 || declared.getFirst().endLine() <= declared.getFirst().line()
					|| !lines.get(declared.getFirst().endLine() - 1).strip().startsWith("</")) {
				throw unreadable(this.fieldsFile, "여러 줄로 된 fragment " + fragment + "()(닫는 태그가 자기 줄에 있는 모양)");
			}
			int close = declared.getFirst().endLine() - 1;
			String indent = lines.get(close).substring(0, lines.get(close).indexOf('<'));
			lines.add(close, indent + "  <div th:replace=\"~{cozy/component/field :: field('" + this.field.name() + "', '" + this.field.label()
					+ "', '" + this.field.input() + "')}\"></div>");
			return String.join("\n", lines) + (html.endsWith("\n") ? "\n" : "");
		}

		// ---- 목록과 상세 (보이는 곳)

		/** 목록의 열과 상세의 항목. 자리를 찾지 못하면 실패하지 않고 손으로 할 일로 남긴다 */
		private void screens() {
			String templates = "src/main/resources/templates/" + this.module + "/api/";
			String label = this.field.label();
			String list = this.ctx.plan().current(templates + "list.html");
			if (list != null && this.field.kind() != Field.Kind.LONGTEXT) {
				String result = listColumn(list);
				if (result != null) {
					this.ctx.plan().edit(templates + "list.html", result, label + " 열");
				}
				else {
					this.ctx.manual(templates + "list.html", "목록에 " + label + " 열을 더하세요. 머리 줄에 th, 행에 td입니다",
							"<th class=\"w-1\">" + label + "</th>\n" + unindent(CrudRecipe.Model.cell("row", this.field)));
				}
			}
			String detail = this.ctx.plan().current(templates + "detail.html");
			if (detail != null) {
				boolean longtext = this.field.kind() == Field.Kind.LONGTEXT;
				String result = longtext ? detailCard(detail) : detailItem(detail);
				if (result != null) {
					this.ctx.plan().edit(templates + "detail.html", result, label + (longtext ? " 카드" : " 항목"));
				}
				else {
					this.ctx.manual(templates + "detail.html",
							longtext ? "상세에 " + label + " 카드를 더하세요" : "상세의 datagrid에 " + label + " 항목을 더하세요",
							(longtext ? CrudRecipe.Model.longtextCard(this.field, "") : CrudRecipe.Model.gridItem(this.field, "")).stripTrailing());
				}
			}
		}

		/** 여러 줄 마크업의 공통 들여쓰기를 뺀다 (첫 줄은 들여쓰지 않은 조각) */
		private static String unindent(String markup) {
			List<String> lines = markup.lines().toList();
			int common = lines.stream().skip(1).mapToInt((l) -> l.length() - l.stripLeading().length()).min().orElse(0);
			int base = Math.max(0, common - 2);
			StringBuilder out = new StringBuilder(lines.getFirst());
			lines.stream().skip(1).forEach((l) -> out.append('\n').append(l.substring(Math.min(base, l.length()))));
			return out.toString();
		}

		/** 수정한 시각 칸 앞에 칸을, 머리 줄의 같은 자리에 th를 넣는다. 모양을 알아볼 수 없으면 {@code null} */
		private String listColumn(String html) {
			Matcher stamp = Pattern.compile("<td\\b[^>]*\\$\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\.updatedAt\\s*}[^>]*>").matcher(html);
			if (!stamp.find()) {
				return null;
			}
			int cell = stamp.start();
			String row = stamp.group(1);
			if (stamp.find()) {
				return null;
			}
			int rowStart = html.lastIndexOf("<tr", cell);
			int rowEnd = html.indexOf("</tr>", cell);
			int headStart = html.indexOf("<thead");
			int headEnd = html.indexOf("</thead>");
			if (rowStart < 0 || rowEnd < 0 || headStart < 0 || headEnd < headStart || html.indexOf("<thead", headStart + 1) >= 0) {
				return null;
			}
			List<Integer> cells = starts(html, "<td\\b", rowStart, rowEnd);
			List<Integer> heads = starts(html, "<th\\b", headStart, headEnd);
			int index = cells.indexOf(cell);
			if (index < 0 || heads.size() != cells.size()) {
				return null;
			}
			int lineStart = html.lastIndexOf('\n', cell - 1) + 1;
			String indent = html.substring(lineStart, cell);
			String added = CrudRecipe.Model.cell(row, this.field);
			StringBuilder out = new StringBuilder(html);
			out.insert(cell, indent.isBlank() ? reindent(added, indent) + "\n" + indent : added);
			out.insert(heads.get(index), "<th class=\"w-1\">" + this.field.label() + "</th>");
			return out.toString();
		}

		/** crud가 쓰는 들여쓰기(16칸)로 만든 칸을 그 줄의 들여쓰기에 맞춘다 */
		private static String reindent(String cell, String indent) {
			List<String> lines = cell.lines().toList();
			StringBuilder out = new StringBuilder(lines.getFirst());
			for (String line : lines.subList(1, lines.size())) {
				out.append('\n').append(indent).append(line.startsWith(" ".repeat(16)) ? line.substring(16) : line.stripLeading());
			}
			return out.toString();
		}

		private static List<Integer> starts(String text, String regex, int from, int to) {
			List<Integer> result = new ArrayList<>();
			Matcher matcher = Pattern.compile(regex).matcher(text).region(from, to);
			while (matcher.find()) {
				result.add(matcher.start());
			}
			return result;
		}

		/** 줄 {@code open}에서 시작한 요소를 닫는 줄: 같은 들여쓰기의 {@code </div>}. 없으면 -1 */
		private static int closing(List<String> lines, int open) {
			String indent = lines.get(open).substring(0, lines.get(open).indexOf('<'));
			for (int i = open + 1; i < lines.size(); i++) {
				if (lines.get(i).equals(indent + "</div>")) {
					return i;
				}
			}
			return -1;
		}

		/** 마지막 필드 항목 뒤(없으면 만든 시각 항목 앞)에 datagrid 항목을 넣는다. 모양을 알아볼 수 없으면 {@code null} */
		private String detailItem(String html) {
			List<String> lines = new ArrayList<>(html.lines().toList());
			Pattern mine = Pattern.compile("\\bitem\\.(" + String.join("|", this.existing) + ")\\b");
			int after = -1;
			int before = -1;
			for (int i = 0; i < lines.size(); i++) {
				if (!lines.get(i).strip().equals("<div class=\"datagrid-item\">")) {
					continue;
				}
				int close = closing(lines, i);
				if (close < 0) {
					return null;
				}
				String block = String.join("\n", lines.subList(i, close + 1));
				if (!this.existing.isEmpty() && mine.matcher(block).find()) {
					after = close;
				}
				else if (block.contains("item.createdAt") && before < 0) {
					before = i;
				}
			}
			int at = (after >= 0) ? after + 1 : before;
			if (at < 0) {
				return null;
			}
			String anchor = lines.get((after >= 0) ? after : before);
			String indent = anchor.substring(0, anchor.indexOf('<'));
			lines.addAll(at, CrudRecipe.Model.gridItem(this.field, indent).lines().toList());
			return String.join("\n", lines) + (html.endsWith("\n") ? "\n" : "");
		}

		/** 넓은 칸({@code col-lg-8})의 끝에 긴 글 카드를 넣는다. 모양을 알아볼 수 없으면 {@code null} */
		private String detailCard(String html) {
			List<String> lines = new ArrayList<>(html.lines().toList());
			List<Integer> wide = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).strip().equals("<div class=\"col-lg-8\">")) {
					wide.add(i);
				}
			}
			int close = (wide.size() == 1) ? closing(lines, wide.getFirst()) : -1;
			if (close < 0) {
				return null;
			}
			String indent = lines.get(close).substring(0, lines.get(close).indexOf('<')) + "  ";
			lines.addAll(close, CrudRecipe.Model.longtextCard(this.field, indent).lines().toList());
			return String.join("\n", lines) + (html.endsWith("\n") ? "\n" : "");
		}

		// ---- 스펙

		/** 스펙에 값 하나를 넣을 자리 */
		private record Slot(List<Object> path, MappingNode map, String anchor, String key, String value) {
		}

		private Map<String, String> specs() {
			Map<String, String> changed = new LinkedHashMap<>();
			Path dir = this.ctx.project().dir().resolve("src/main/resources/specs");
			if (!Files.isDirectory(dir)) {
				return changed;
			}
			List<String> files;
			try (Stream<Path> paths = Files.list(dir)) {
				files = paths.map((p) -> p.getFileName().toString())
					.filter((n) -> n.endsWith(".yml") || n.endsWith(".yaml"))
					.sorted()
					.map((n) -> "src/main/resources/specs/" + n)
					.toList();
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			for (String file : files) {
				String text = this.ctx.plan().current(file);
				if (text.contains(this.entity + "Views") || text.contains(this.entity + "Form")) {
					String result = spec(file, text);
					if (!result.equals(text)) {
						changed.put(file, result);
					}
				}
			}
			return changed;
		}

		private String spec(String file, String text) {
			Node root;
			try {
				root = new Yaml().compose(new StringReader(text));
			}
			catch (RuntimeException ex) {
				throw new CliException(file + "를 읽을 수 없습니다(YAML 오류). 스펙을 먼저 고치세요.");
			}
			List<Slot> slots = new ArrayList<>();
			if (root instanceof MappingNode top && value(top, "model") instanceof MappingNode model
					&& value(top, "states") instanceof MappingNode states) {
				String dto = pkg(Layer.API_DTO) + "." + this.entity;
				for (NodeTuple entry : model.getValue()) {
					String name = scalar(entry.getKeyNode());
					String type = (scalar(entry.getValueNode()) != null) ? scalar(entry.getValueNode()).replace('$', '.') : "";
					for (NodeTuple state : states.getValue()) {
						String stateName = scalar(state.getKeyNode());
						Node value = (state.getValueNode() instanceof MappingNode map) ? value(map, name) : null;
						if (value == null) {
							continue;
						}
						if (type.equals(dto + "Views.Detail")) {
							slots.add(detail(file, List.of("states", stateName, name), value, stateName));
						}
						else if (type.equals(dto + "Views.ListResult") && this.field.kind() != Field.Kind.LONGTEXT) {
							rows(file, List.of("states", stateName, name), value, slots);
						}
						else if (type.equals(dto + "Form") && value instanceof MappingNode form && !form.getValue().isEmpty()) {
							slots.add(new Slot(List.of("states", stateName, name), form, null, this.field.name(),
									stateName.equals("empty") ? this.field.emptySample() : this.field.sample()));
						}
					}
				}
			}
			if (slots.isEmpty()) {
				return text;
			}
			TreeMap<Integer, String> inserts = new TreeMap<>();
			Object expected = new Yaml().load(text);
			for (Slot slot : slots) {
				String where = file + "의 " + slot.path().stream().map(String::valueOf).reduce((a, b) -> a + "." + b).orElse("");
				if (value(slot.map(), slot.key()) != null) {
					throw new CliException(where + "에 " + slot.key() + "가 이미 있습니다." + HAND);
				}
				insert(text, slot, inserts);
				Object target = expected;
				for (Object step : slot.path()) {
					target = (step instanceof Integer i) ? ((List<?>) target).get(i) : ((Map<?, ?>) target).get(step);
				}
				@SuppressWarnings("unchecked")
				Map<Object, Object> map = (Map<Object, Object>) target;
				map.put(slot.key(), new Yaml().load(slot.value()));
			}
			StringBuilder out = new StringBuilder(text);
			inserts.descendingMap().forEach(out::insert);
			Object actual;
			try {
				actual = new Yaml().load(out.toString());
			}
			catch (RuntimeException ex) {
				actual = null;
			}
			if (!expected.equals(actual)) {
				throw new CliException(file + "에 " + this.field.name() + " 값을 넣을 자리를 정하지 못했습니다(고친 결과가 기대와 다릅니다)." + HAND);
			}
			return out.toString();
		}

		private Slot detail(String file, List<Object> path, Node value, String state) {
			if (!(value instanceof MappingNode map) || map.getValue().isEmpty()) {
				throw unreadable(file, "맵으로 적은 " + path.getLast() + "(상태 " + state + ")");
			}
			boolean empty = state.equals("empty");
			if (this.field.kind() == Field.Kind.LONGTEXT) {
				return new Slot(path, map, "createdAt", this.field.name() + "Lines", empty ? "[]" : "[첫 줄, 둘째 줄]");
			}
			return new Slot(path, map, "createdAt", this.field.name(), empty ? this.field.emptySample() : this.field.sample());
		}

		private void rows(String file, List<Object> path, Node result, List<Slot> slots) {
			Node rows = (result instanceof MappingNode map) ? value(map, "rows") : null;
			if (!(rows instanceof SequenceNode sequence)) {
				throw unreadable(file, "rows 목록이 있는 " + path.getLast() + "(상태 " + path.get(1) + ")");
			}
			for (int i = 0; i < sequence.getValue().size(); i++) {
				if (!(sequence.getValue().get(i) instanceof MappingNode row) || row.getValue().isEmpty()) {
					throw unreadable(file, "맵으로 적은 rows의 항목(상태 " + path.get(1) + ")");
				}
				List<Object> rowPath = new ArrayList<>(path);
				rowPath.add("rows");
				rowPath.add(i);
				slots.add(new Slot(rowPath, row, "updatedAt", this.field.name(),
						(this.field.kind() == Field.Kind.BOOL && i % 2 == 1) ? "false" : this.field.sample()));
			}
		}

		/** 기준 키 앞에(없으면 마지막 값 뒤에) {@code 키: 값}을 넣는다. 흐름 표기와 블록 표기를 그대로 따른다 */
		private static void insert(String text, Slot slot, TreeMap<Integer, String> inserts) {
			boolean flow = slot.map().getFlowStyle() == DumperOptions.FlowStyle.FLOW;
			String pair = slot.key() + ": " + slot.value();
			Node anchor = null;
			for (NodeTuple tuple : slot.map().getValue()) {
				if (slot.anchor() != null && slot.anchor().equals(scalar(tuple.getKeyNode()))) {
					anchor = tuple.getKeyNode();
				}
			}
			if (anchor != null) {
				int at = text.offsetByCodePoints(0, anchor.getStartMark().getIndex());
				inserts.merge(at, flow ? pair + ", " : pair + "\n" + " ".repeat(anchor.getStartMark().getColumn()), String::concat);
				return;
			}
			Node last = slot.map().getValue().getLast().getValueNode();
			int at = text.offsetByCodePoints(0, last.getEndMark().getIndex());
			int column = slot.map().getValue().getFirst().getKeyNode().getStartMark().getColumn();
			inserts.merge(at, flow ? ", " + pair : "\n" + " ".repeat(column) + pair, String::concat);
		}

		private static Node value(MappingNode map, String key) {
			for (NodeTuple tuple : map.getValue()) {
				if (key.equals(scalar(tuple.getKeyNode()))) {
					return tuple.getValueNode();
				}
			}
			return null;
		}

		private static String scalar(Node node) {
			return (node instanceof ScalarNode scalar) ? scalar.getValue() : null;
		}
	}
}
