package io.salvia95.cozy.cli.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import io.salvia95.cozy.rules.JavaType;

/**
 * 앱의 Java 소스를 javac 파서(JDK의 {@code com.sun.source})로 읽는다. 컴파일하지 않고 클래스패스도 쓰지 않으므로, 타입 이름은
 * import, 같은 패키지, 와일드카드 import로만 푼다. 앱 밖의 타입은 import에 적힌 이름 그대로 쓴다.
 *
 * <p>바이트코드로 보는 cozy-test와 다른 점: 메타 애너테이션(직접 만든 {@code @Controller} 묶음)은 모른다. 알려진 스프링
 * 애너테이션만 본다. 같은 판단이 필요하면 {@code mvn verify}가 최종이다.
 */
public final class JavaSources {

	static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";

	static final String APPLICATION_MODULE = "org.springframework.modulith.ApplicationModule";

	static final String NAMED_INTERFACE = "org.springframework.modulith.NamedInterface";

	static final String COZY_EVENT = "io.salvia95.cozy.events.CozyEvent";

	/** {@code @Controller}를 뜻하는 애너테이션 */
	static final Set<String> CONTROLLERS = Set.of("org.springframework.stereotype.Controller",
			"org.springframework.web.bind.annotation.RestController");

	/** 스프링 빈을 뜻하는 애너테이션 ({@code @Component}의 메타 애너테이션) */
	static final Set<String> COMPONENTS = Set.of("org.springframework.stereotype.Component",
			"org.springframework.stereotype.Service", "org.springframework.stereotype.Repository",
			"org.springframework.stereotype.Controller", "org.springframework.web.bind.annotation.RestController",
			"org.springframework.web.bind.annotation.ControllerAdvice",
			"org.springframework.web.bind.annotation.RestControllerAdvice",
			"org.springframework.context.annotation.Configuration");

	static final Set<String> LISTENERS = Set.of("org.springframework.modulith.events.ApplicationModuleListener",
			"org.springframework.context.event.EventListener",
			"org.springframework.transaction.event.TransactionalEventListener");

	/** C8이 보는 앱 밖의 타입 (app.command의 직접 SQL) */
	static final Set<String> JDBC_TYPES = Set.of("org.springframework.jdbc.core.JdbcTemplate",
			"org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
			"org.springframework.jdbc.core.simple.JdbcClient", "org.apache.ibatis.session.SqlSession");

	private final Path project;

	private final Path root;

	private final List<Unit> units = new ArrayList<>();

	private final Map<String, SourceType> types = new TreeMap<>();

	private final Map<String, PackageInfo> packageInfos = new TreeMap<>();

	private final List<Problem> problems = new ArrayList<>();

	private JavaSources(Path project) {
		this.project = project.toAbsolutePath().normalize();
		this.root = this.project.resolve("src/main/java");
	}

	/** 소스 파일 하나 */
	public record Unit(Path file, String relative, CompilationUnitTree tree, String pkg, Map<String, String> imports,
			List<String> wildcards, Map<String, String> local, LineMap lines, SourcePositions positions) {

		int line(Tree node) {
			long position = this.positions.getStartPosition(this.tree, node);
			return (position < 0) ? 0 : (int) this.lines.getLineNumber(position);
		}
	}

	/**
	 * 앱에 선언된 타입 하나.
	 *
	 * @param name 바이너리 이름 ({@code a.b.Outer$Inner})
	 * @param canonical 소스 이름 ({@code a.b.Outer.Inner})
	 * @param topLevel 감싸는 최상위 타입의 이름
	 */
	public record SourceType(String name, String canonical, String topLevel, Tree.Kind kind, ClassTree tree, Unit unit) {

		public boolean isInterface() {
			return this.kind == Tree.Kind.INTERFACE || this.kind == Tree.Kind.ANNOTATION_TYPE;
		}

		public String simpleName() {
			return this.tree.getSimpleName().toString();
		}

		public String pkg() {
			return this.unit.pkg();
		}

		public int line() {
			return this.unit.line(this.tree);
		}
	}

	/** {@code package-info.java} 하나의 애너테이션 */
	public record PackageInfo(String pkg, Unit unit, List<AnnotationTree> annotations) {
	}

	/** 읽지 못한 것. 추측하지 않고 알린다 */
	public record Problem(String location, int line, String message) {
	}

	/** @param project 앱 프로젝트 폴더. {@code src/main/java}가 없으면 빈 결과 */
	public static JavaSources read(Path project) {
		JavaSources sources = new JavaSources(project);
		if (Files.isDirectory(sources.root)) {
			sources.parse();
		}
		return sources;
	}

	public List<Problem> problems() {
		return List.copyOf(this.problems);
	}

	public Map<String, SourceType> types() {
		return this.types;
	}

	public Map<String, PackageInfo> packageInfos() {
		return this.packageInfos;
	}

	public List<Unit> units() {
		return this.units;
	}

	/** 모든 소스가 있는 패키지 (package-info만 있는 패키지 포함) */
	public Set<String> packages() {
		Set<String> packages = new LinkedHashSet<>();
		this.units.forEach((u) -> packages.add(u.pkg()));
		return packages;
	}

	// ── 읽기 ──────────────────────────────────────────────────

	private void parse() {
		List<Path> files;
		try (Stream<Path> walk = Files.walk(this.root)) {
			files = walk.filter((p) -> p.toString().endsWith(".java") && Files.isRegularFile(p)).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		if (files.isEmpty()) {
			return;
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("[cozy] JDK의 javac가 없습니다. JRE가 아니라 JDK 25로 실행하세요.");
		}
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (StandardJavaFileManager files0 = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			JavacTask task = (JavacTask) compiler.getTask(null, files0, diagnostics, List.of("-proc:none"), null,
					files0.getJavaFileObjectsFromPaths(files));
			Iterable<? extends CompilationUnitTree> parsed = task.parse();
			SourcePositions positions = Trees.instance(task).getSourcePositions();
			for (CompilationUnitTree tree : parsed) {
				addUnit(tree, positions);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
			if (d.getKind() == Diagnostic.Kind.ERROR && d.getSource() != null) {
				this.problems.add(new Problem(relative(Path.of(d.getSource().toUri())), (int) d.getLineNumber(),
						"Java 문법 오류: " + d.getMessage(Locale.ROOT)));
			}
		}
	}

	private void addUnit(CompilationUnitTree tree, SourcePositions positions) {
		Path file = Path.of(tree.getSourceFile().toUri());
		String pkg = (tree.getPackageName() != null) ? tree.getPackageName().toString() : "";
		Map<String, String> imports = new LinkedHashMap<>();
		List<String> wildcards = new ArrayList<>();
		for (ImportTree imp : tree.getImports()) {
			if (imp.isStatic()) {
				continue;
			}
			String text = imp.getQualifiedIdentifier().toString();
			if (text.endsWith(".*")) {
				wildcards.add(text.substring(0, text.length() - 2));
			}
			else {
				imports.put(text.substring(text.lastIndexOf('.') + 1), text);
			}
		}
		Map<String, String> local = new LinkedHashMap<>();
		Unit unit = new Unit(file, relative(file), tree, pkg, imports, wildcards, local, tree.getLineMap(), positions);
		this.units.add(unit);
		if (file.getFileName().toString().equals("package-info.java")) {
			List<AnnotationTree> annotations = (tree.getPackage() != null)
					? List.copyOf(tree.getPackage().getAnnotations()) : List.of();
			this.packageInfos.put(pkg, new PackageInfo(pkg, unit, annotations));
			return;
		}
		for (Tree decl : tree.getTypeDecls()) {
			if (decl instanceof ClassTree type) {
				String name = pkg.isEmpty() ? type.getSimpleName().toString() : pkg + "." + type.getSimpleName();
				addType(unit, type, name, name, name);
			}
		}
	}

	private void addType(Unit unit, ClassTree tree, String binary, String canonical, String topLevel) {
		this.types.put(canonical, new SourceType(binary, canonical, topLevel, tree.getKind(), tree, unit));
		unit.local().putIfAbsent(tree.getSimpleName().toString(), canonical);
		for (Tree member : tree.getMembers()) {
			if (member instanceof ClassTree nested) {
				addType(unit, nested, binary + "$" + nested.getSimpleName(), canonical + "." + nested.getSimpleName(),
						topLevel);
			}
		}
	}

	/** 프로젝트 기준 경로 ({@code src/main/java/...}) */
	private String relative(Path file) {
		Path normal = file.toAbsolutePath().normalize();
		return normal.startsWith(this.project) ? this.project.relativize(normal).toString().replace('\\', '/')
				: normal.toString();
	}

	// ── 이름 풀기 ───────────────────────────────────────────────

	/** 소스에 쓴 타입 이름(단순 이름이나 점으로 이은 이름)이 가리킬 수 있는 전체 이름들. 앞의 것이 우선이다 */
	List<String> candidates(Unit unit, String written) {
		List<String> result = new ArrayList<>();
		String head = written.contains(".") ? written.substring(0, written.indexOf('.')) : written;
		String tail = written.contains(".") ? written.substring(written.indexOf('.')) : "";
		if (unit.local().containsKey(head)) {
			result.add(unit.local().get(head) + tail);
		}
		if (unit.imports().containsKey(head)) {
			result.add(unit.imports().get(head) + tail);
		}
		if (!unit.pkg().isEmpty()) {
			result.add(unit.pkg() + "." + written);
		}
		for (String wildcard : unit.wildcards()) {
			result.add(wildcard + "." + written);
		}
		if (written.contains(".")) {
			result.add(written);
		}
		return result;
	}

	/** 앱 타입 이름으로 풀거나, 없으면 비어 있다 */
	Optional<SourceType> resolve(Unit unit, String written) {
		for (String candidate : candidates(unit, written)) {
			SourceType type = this.types.get(candidate);
			if (type != null) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}

	/** 애너테이션이나 인터페이스가 알려진 이름 중 하나인가 */
	boolean refersTo(Unit unit, Tree typeTree, Set<String> names) {
		String written = typeName(typeTree);
		return candidates(unit, written).stream().anyMatch(names::contains);
	}

	static String typeName(Tree tree) {
		Tree t = tree;
		if (t instanceof ParameterizedTypeTree parameterized) {
			t = parameterized.getType();
		}
		if (t instanceof AnnotationTree annotation) {
			t = annotation.getAnnotationType();
		}
		return t.toString();
	}

	/** 애너테이션의 속성 하나. 글자 그대로의 문자열(또는 그 배열)만 읽는다. 다른 식이면 비어 있다 */
	static Optional<List<String>> stringValues(AnnotationTree annotation, String attribute) {
		for (ExpressionTree argument : annotation.getArguments()) {
			String name = "value";
			ExpressionTree value = argument;
			if (argument instanceof AssignmentTree assignment) {
				name = assignment.getVariable().toString();
				value = assignment.getExpression();
			}
			if (!name.equals(attribute)) {
				continue;
			}
			List<String> values = new ArrayList<>();
			if (value instanceof LiteralTree literal && literal.getValue() instanceof String s) {
				values.add(s);
			}
			else if (value instanceof NewArrayTree array && array.getInitializers() != null) {
				for (ExpressionTree item : array.getInitializers()) {
					if (!(item instanceof LiteralTree literal) || !(literal.getValue() instanceof String s)) {
						return Optional.empty();
					}
					values.add(s);
				}
			}
			else {
				return Optional.empty();
			}
			return Optional.of(values);
		}
		return Optional.of(List.of());
	}

	/** 속성을 적었는가 */
	static boolean hasAttribute(AnnotationTree annotation, String attribute) {
		for (ExpressionTree argument : annotation.getArguments()) {
			String name = (argument instanceof AssignmentTree assignment) ? assignment.getVariable().toString() : "value";
			if (name.equals(attribute)) {
				return true;
			}
		}
		return false;
	}

	Optional<AnnotationTree> annotation(Unit unit, List<? extends AnnotationTree> annotations, String name) {
		return annotations.stream().filter((a) -> refersTo(unit, a, Set.of(name))).map((a) -> (AnnotationTree) a).findFirst();
	}

	// ── C8 입력 ─────────────────────────────────────────────────

	/** 모든 앱 타입을 cozy-rules의 JavaType으로. 의존은 최상위 타입에 모은다(C8은 최상위 타입 기준으로 보고한다) */
	public List<JavaType> javaTypes() {
		List<JavaType> result = new ArrayList<>();
		for (SourceType type : this.types.values()) {
			Set<JavaType.Trait> traits = traits(type);
			List<JavaType.Ref> interfaces = new ArrayList<>();
			for (Tree implemented : implemented(type.tree())) {
				interfaces.add(ref(type.unit(), typeName(implemented), true));
			}
			List<JavaType.Ref> dependencies = type.name().equals(type.topLevel()) ? dependencies(type) : List.of();
			result.add(new JavaType(type.name(), type.topLevel(), type.isInterface(), traits, interfaces, dependencies));
		}
		return result;
	}

	/** {@code @NamedInterface}를 선언한 패키지 */
	public Set<String> namedInterfacePackages() {
		Set<String> result = new LinkedHashSet<>();
		this.packageInfos.values().forEach((info) -> {
			if (annotation(info.unit(), info.annotations(), NAMED_INTERFACE).isPresent()) {
				result.add(info.pkg());
			}
		});
		return result;
	}

	private Set<JavaType.Trait> traits(SourceType type) {
		Set<JavaType.Trait> traits = EnumSet.noneOf(JavaType.Trait.class);
		List<? extends AnnotationTree> annotations = type.tree().getModifiers().getAnnotations();
		if (annotations.stream().anyMatch((a) -> refersTo(type.unit(), a, CONTROLLERS))) {
			traits.add(JavaType.Trait.CONTROLLER);
		}
		if (annotations.stream().anyMatch((a) -> refersTo(type.unit(), a, COMPONENTS))) {
			traits.add(JavaType.Trait.COMPONENT);
		}
		if (isCozyEvent(type, new LinkedHashSet<>())) {
			traits.add(JavaType.Trait.COZY_EVENT);
		}
		for (Tree member : type.tree().getMembers()) {
			if (member instanceof MethodTree method && method.getModifiers()
				.getAnnotations()
				.stream()
				.anyMatch((a) -> refersTo(type.unit(), a, LISTENERS))) {
				traits.add(JavaType.Trait.LISTENER);
			}
		}
		return traits;
	}

	boolean isCozyEvent(SourceType type, Set<String> seen) {
		if (!seen.add(type.canonical())) {
			return false;
		}
		for (Tree implemented : implemented(type.tree())) {
			if (refersTo(type.unit(), implemented, Set.of(COZY_EVENT))) {
				return true;
			}
			Optional<SourceType> app = resolve(type.unit(), typeName(implemented));
			if (app.isPresent() && isCozyEvent(app.get(), seen)) {
				return true;
			}
		}
		return false;
	}

	/** 구현하는 인터페이스. javac는 인터페이스의 extends 목록도 implements 절로 돌려준다 */
	static List<? extends Tree> implemented(ClassTree tree) {
		return tree.getImplementsClause();
	}

	private JavaType.Ref ref(Unit unit, String written, boolean assumeInterface) {
		Optional<SourceType> app = resolve(unit, written);
		if (app.isPresent()) {
			return new JavaType.Ref(app.get().name(), app.get().topLevel(), app.get().isInterface());
		}
		String name = candidates(unit, written).getFirst();
		return new JavaType.Ref(name, name, assumeInterface);
	}

	/** 최상위 타입(중첩 타입 포함)이 이름으로 가리키는 앱 타입, JDBC 타입, java.sql 타입 */
	private List<JavaType.Ref> dependencies(SourceType type) {
		List<JavaType.Ref> refs = new ArrayList<>();
		Unit unit = type.unit();
		new TreeScanner<Void, Void>() {

			@Override
			public Void visitIdentifier(IdentifierTree node, Void unused) {
				add(node.getName().toString());
				return null;
			}

			@Override
			public Void visitMemberSelect(MemberSelectTree node, Void unused) {
				add(node.toString());
				return super.visitMemberSelect(node, unused);
			}

			private void add(String written) {
				Optional<SourceType> app = resolve(unit, written);
				if (app.isPresent()) {
					refs.add(new JavaType.Ref(app.get().name(), app.get().topLevel(), app.get().isInterface()));
					return;
				}
				for (String candidate : candidates(unit, written)) {
					if (JDBC_TYPES.contains(candidate) || candidate.startsWith("java.sql.")
							&& Character.isUpperCase(candidate.charAt("java.sql.".length()))) {
						refs.add(new JavaType.Ref(candidate, candidate, false));
						return;
					}
				}
			}
		}.scan(type.tree(), null);
		return refs;
	}
}
