package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 이미 있는 Java 파일에 덧붙이는 편집기. 표시(cozy:append) 대신 javac가 찾은 위치를 쓴다.
 *
 * <ul>
 * <li>멤버: 그 이름의 최상위 타입 본문의 마지막 {@code }} 바로 앞. 빈 줄 하나를 두고 넣는다.</li>
 * <li>import: 마지막 import 뒤(없으면 package 선언 뒤). 이미 있으면 더하지 않고 정렬하지 않는다.</li>
 * </ul>
 * 문법 오류가 있거나, 타입이 하나로 정해지지 않거나, 같은 단순 이름을 다른 패키지에서 import하고 있으면 추측하지 않고 실패한다. 고친 결과는 다시
 * javac로 읽어 본다.
 */
final class JavaEdits {

	private JavaEdits() {
	}

	/**
	 * 메서드 시그니처. 타입은 소스에 적힌 그대로(공백만 뺀다) 비교한다. 본문은 비교하지 않는다.
	 *
	 * @param name 메서드 이름
	 * @param returnType 반환 타입 ({@code void}, {@code List<ActivityItem>})
	 * @param parameterTypes 파라미터 타입
	 */
	record Signature(String name, String returnType, List<String> parameterTypes) {

		Signature {
			returnType = normalize(returnType);
			parameterTypes = parameterTypes.stream().map(JavaEdits::normalize).toList();
		}

		/** {@code List<ActivityItem> recent(int)} */
		String text() {
			return this.returnType + " " + this.name + "(" + String.join(", ", this.parameterTypes) + ")";
		}
	}

	/** 덧붙일 메서드가 이미 있는가 */
	enum Presence {

		/** 같은 이름, 같은 시그니처 */
		PRESENT,

		/** 그 이름의 메서드가 없다 */
		ABSENT,

		/** 같은 이름이 다른 시그니처로 있다. 오버로드를 만들지 않으므로 실패한다 */
		CONFLICT
	}

	/** 읽은 Java 파일 */
	static final class Parsed {

		private final String file;

		private final String source;

		private final CompilationUnitTree unit;

		private final SourcePositions positions;

		private Parsed(String file, String source, CompilationUnitTree unit, SourcePositions positions) {
			this.file = file;
			this.source = source;
			this.unit = unit;
			this.positions = positions;
		}

		CompilationUnitTree unit() {
			return this.unit;
		}

		SourcePositions positions() {
			return this.positions;
		}

		int start(Tree tree) {
			return (int) this.positions.getStartPosition(this.unit, tree);
		}

		int end(Tree tree) {
			return (int) this.positions.getEndPosition(this.unit, tree);
		}

		/** 이 이름의 최상위 타입. 하나가 아니면 실패한다 */
		ClassTree type(String simpleName) {
			List<ClassTree> found = new ArrayList<>();
			for (Tree tree : this.unit.getTypeDecls()) {
				if (tree instanceof ClassTree type && type.getSimpleName().contentEquals(simpleName)) {
					found.add(type);
				}
			}
			if (found.size() != 1) {
				throw new CliException(this.file + "에서 최상위 타입 " + simpleName + "를 하나로 찾지 못했습니다(" + found.size()
						+ "개). 이 파일은 손으로 고치세요.");
			}
			return found.getFirst();
		}

		/** 타입에 선언된 메서드 (생성자 제외) */
		List<Signature> methods(String simpleName) {
			List<Signature> methods = new ArrayList<>();
			for (Tree member : type(simpleName).getMembers()) {
				if (member instanceof MethodTree method && method.getReturnType() != null) {
					methods.add(new Signature(method.getName().toString(), method.getReturnType().toString(),
							method.getParameters().stream().map((p) -> p.getType().toString()).toList()));
				}
			}
			return methods;
		}

		/** 같은 이름의 메서드가 있는가, 있다면 시그니처가 같은가 */
		Presence presence(String simpleName, Signature wanted) {
			List<Signature> same = methods(simpleName).stream().filter((m) -> m.name().equals(wanted.name())).toList();
			if (same.isEmpty()) {
				return Presence.ABSENT;
			}
			return (same.size() == 1 && same.getFirst().equals(wanted)) ? Presence.PRESENT : Presence.CONFLICT;
		}

		/** 같은 이름의 메서드들의 시그니처 (실패 메시지용) */
		List<String> signaturesNamed(String simpleName, String name) {
			return methods(simpleName).stream().filter((m) -> m.name().equals(name)).map(Signature::text).toList();
		}

		/** static이 아닌 필드: 타입(소스 그대로) → 이름 */
		java.util.Map<String, String> fields(String simpleName) {
			java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
			for (Tree member : type(simpleName).getMembers()) {
				if (member instanceof com.sun.source.tree.VariableTree field
						&& !field.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)) {
					fields.put(field.getName().toString(), normalize(field.getType().toString()));
				}
			}
			return fields;
		}

		/** 선언한 생성자 수 */
		int constructors(String simpleName) {
			int count = 0;
			for (Tree member : type(simpleName).getMembers()) {
				if (member instanceof MethodTree method && method.getReturnType() == null) {
					count++;
				}
			}
			return count;
		}

		/** record 구성 요소 이름 (순서대로) */
		List<String> recordComponents(String simpleName) {
			ClassTree type = type(simpleName);
			if (type.getKind() != Tree.Kind.RECORD) {
				throw new CliException(this.file + "의 " + simpleName + "가 record가 아닙니다.");
			}
			List<String> names = new ArrayList<>();
			for (Tree member : type.getMembers()) {
				if (member instanceof com.sun.source.tree.VariableTree field
						&& !field.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)) {
					names.add(field.getName() + ":" + normalize(field.getType().toString()));
				}
			}
			return names;
		}

		/** 타입 본문을 여는 {@code {}의 위치 */
		int bodyOpen(String simpleName) {
			ClassTree type = type(simpleName);
			int from = end(type.getModifiers());
			List<Tree> heads = new ArrayList<>(type.getTypeParameters());
			heads.addAll(type.getImplementsClause());
			heads.addAll(type.getPermitsClause());
			if (type.getExtendsClause() != null) {
				heads.add(type.getExtendsClause());
			}
			for (Tree head : heads) {
				from = Math.max(from, end(head));
			}
			int open = this.source.indexOf('{', Math.max(from, start(type)));
			if (open < 0) {
				throw new CliException(this.file + "에서 " + simpleName + " 본문의 시작을 찾지 못했습니다. 이 파일은 손으로 고치세요.");
			}
			return open;
		}

		/** import한 이름 (static은 빼고) */
		List<String> imports() {
			List<String> names = new ArrayList<>();
			for (ImportTree tree : this.unit.getImports()) {
				if (!tree.isStatic()) {
					names.add(tree.getQualifiedIdentifier().toString());
				}
			}
			return names;
		}

		String packageName() {
			return (this.unit.getPackageName() != null) ? this.unit.getPackageName().toString() : "";
		}
	}

	static Parsed parse(String file, String source) {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new CliException("JDK의 javac가 없습니다. JRE가 아니라 JDK 25로 실행하세요.");
		}
		String name = file.endsWith("package-info.java") ? "package-info.java" : "Source.java";
		JavaFileObject object = new SimpleJavaFileObject(URI.create("string:///" + name), JavaFileObject.Kind.SOURCE) {
			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return source;
			}
		};
		List<String> errors = new ArrayList<>();
		JavacTask task = (JavacTask) compiler.getTask(null, null, (d) -> {
			if (d.getKind() == Diagnostic.Kind.ERROR) {
				errors.add(d.getLineNumber() + ": " + d.getMessage(Locale.ROOT));
			}
		}, List.of("-proc:none"), null, List.of(object));
		try {
			CompilationUnitTree unit = task.parse().iterator().next();
			if (!errors.isEmpty()) {
				throw new CliException(file + "를 읽을 수 없습니다(Java 문법 오류: " + errors.getFirst() + "). 문법 오류를 먼저 고치세요.");
			}
			return new Parsed(file, source, unit, Trees.instance(task).getSourcePositions());
		}
		catch (IOException ex) {
			throw new CliException(file + "를 읽을 수 없습니다: " + ex.getMessage());
		}
	}

	/**
	 * 타입 본문 끝에 멤버를 더한다.
	 *
	 * @param member 탭 하나로 들여쓴 멤버 (여러 줄이면 줄마다 들여쓴다). 끝에 줄바꿈을 두지 않는다
	 */
	static String addMember(String file, String source, String typeName, String member) {
		Parsed parsed = parse(file, source);
		ClassTree type = parsed.type(typeName);
		int close = parsed.end(type) - 1;
		if (close < 0 || source.charAt(close) != '}') {
			throw new CliException(file + "에서 " + typeName + " 본문의 끝을 찾지 못했습니다. 이 파일은 손으로 고치세요.");
		}
		String before = source.substring(0, close).stripTrailing();
		String result = before + "\n\n" + member.stripTrailing() + "\n" + source.substring(close);
		parse(file, result);
		return result;
	}

	/**
	 * 타입 본문 맨 앞에 멤버를 더한다 (필드와 생성자).
	 *
	 * @param member 탭 하나로 들여쓴 멤버. 끝에 줄바꿈을 두지 않는다
	 */
	static String addFirstMember(String file, String source, String typeName, String member) {
		Parsed parsed = parse(file, source);
		int open = parsed.bodyOpen(typeName);
		String rest = source.substring(open + 1).replaceFirst("^[ \\t]*\\n(?:[ \\t]*\\n)*", "");
		String result = source.substring(0, open + 1) + "\n\n" + member.stripTrailing() + (rest.startsWith("}") ? "\n" : "\n\n") + rest;
		parse(file, result);
		return result;
	}

	/**
	 * import를 더한다. 이미 있거나(같은 이름, 같은 패키지의 {@code *}) 같은 패키지의 타입이면 더하지 않는다.
	 *
	 * @param names 완전한 이름 ({@code java.util.List})
	 */
	static String addImports(String file, String source, Collection<String> names) {
		Parsed parsed = parse(file, source);
		List<String> existing = parsed.imports();
		List<String> added = new ArrayList<>();
		for (String name : names) {
			String pkg = name.substring(0, name.lastIndexOf('.'));
			String simple = name.substring(name.lastIndexOf('.') + 1);
			if (pkg.equals(parsed.packageName()) || pkg.equals("java.lang") || existing.contains(name)
					|| existing.contains(pkg + ".*") || added.contains(name)) {
				continue;
			}
			for (String other : existing) {
				if (other.endsWith("." + simple) && !other.equals(name)) {
					throw new CliException(file + "가 이미 " + other + "를 import하고 있어 " + name + "를 import할 수 없습니다(같은 단순 이름 "
							+ simple + "). 한쪽 이름을 바꾸세요.");
				}
			}
			added.add(name);
		}
		String result = source;
		for (String name : added) {
			result = insertImport(file, result, name);
		}
		return result;
	}

	/** import 그룹: java, javax, org, com, 그 밖(앱 패키지). 그룹 사이에는 빈 줄을 둔다 */
	static int group(String name) {
		for (String prefix : new String[] { "java.", "javax.", "org.", "com." }) {
			if (name.startsWith(prefix)) {
				return List.of("java.", "javax.", "org.", "com.").indexOf(prefix);
			}
		}
		return 4;
	}

	/** 같은 그룹 안에 넣는다(그룹이 정렬돼 있으면 정렬된 자리, 아니면 끝). 그룹이 없으면 앞 그룹 뒤(없으면 뒤 그룹 앞)에 빈 줄을 두고 넣는다 */
	private static String insertImport(String file, String source, String name) {
		Parsed parsed = parse(file, source);
		List<? extends ImportTree> imports = parsed.unit().getImports().stream().filter((i) -> !i.isStatic()).toList();
		String line = "import " + name + ";";
		int g = group(name);
		String result;
		if (imports.isEmpty()) {
			if (parsed.unit().getPackage() == null) {
				throw new CliException(file + "에 package 선언이 없습니다. 이 파일은 손으로 고치세요.");
			}
			int at = source.indexOf(';', parsed.start(parsed.unit().getPackage())) + 1;
			if (at <= 0) {
				throw new CliException(file + "의 package 선언 끝을 찾지 못했습니다. 이 파일은 손으로 고치세요.");
			}
			result = source.substring(0, at) + "\n\n" + line + source.substring(at);
		}
		else {
			ImportTree same = null;
			ImportTree before = null;
			ImportTree after = null;
			List<ImportTree> group = new ArrayList<>();
			for (ImportTree i : imports) {
				int ig = group(i.getQualifiedIdentifier().toString());
				if (ig == g) {
					same = i;
					group.add(i);
				}
				else if (ig < g) {
					before = i;
				}
				else if (after == null) {
					after = i;
				}
			}
			List<String> names = group.stream().map((i) -> i.getQualifiedIdentifier().toString()).toList();
			ImportTree next = names.equals(names.stream().sorted().toList())
					? group.stream().filter((i) -> i.getQualifiedIdentifier().toString().compareTo(name) > 0).findFirst().orElse(null)
					: null;
			if (next != null) {
				// 이미 정렬된 그룹이면 정렬된 자리에 넣는다
				int at = parsed.start(next);
				result = source.substring(0, at) + line + "\n" + source.substring(at);
			}
			else if (same != null) {
				int at = parsed.end(same);
				result = source.substring(0, at) + "\n" + line + source.substring(at);
			}
			else if (before != null) {
				int at = parsed.end(before);
				result = source.substring(0, at) + "\n\n" + line + source.substring(at);
			}
			else {
				int at = parsed.start(after);
				result = source.substring(0, at) + line + "\n\n" + source.substring(at);
			}
		}
		parse(file, result);
		return result;
	}

	static String normalize(String type) {
		return type.replaceAll("\\s+", "");
	}
}
