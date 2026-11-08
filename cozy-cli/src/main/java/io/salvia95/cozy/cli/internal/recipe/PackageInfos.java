package io.salvia95.cozy.cli.internal.recipe;

import java.util.ArrayList;
import java.util.List;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewArrayTree;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 모듈 {@code package-info.java}의 {@code @ApplicationModule(allowedDependencies = ...)}에 의존을 더한다 (cozy link). 알아보는 형식은
 * {@code {}}, {@code "x"}, {@code { "a", "b" }}이고 결과는 항상 배열 형식이다. 다른 형식이면 추측하지 않고 실패한다.
 */
final class PackageInfos {

	private PackageInfos() {
	}

	/**
	 * @param source package-info.java의 내용
	 * @param dependencies 더할 의존 ({@code memo :: spi})
	 * @return 바뀐 내용. 이미 모두 있으면 그대로
	 */
	static String addDependencies(String file, String source, List<String> dependencies) {
		JavaEdits.Parsed parsed = JavaEdits.parse(file, source);
		AnnotationTree annotation = parsed.unit().getPackage() == null ? null
				: parsed.unit().getPackage()
					.getAnnotations()
					.stream()
					.filter((a) -> a.getAnnotationType().toString().endsWith("ApplicationModule"))
					.findFirst()
					.orElse(null);
		if (annotation == null) {
			throw new CliException(file + "에 @ApplicationModule이 없습니다. 의존을 선언하지 않은 모듈(open)은 모든 모듈을 쓸 수 있다는 뜻이라 "
					+ "cozy link가 바꾸지 않습니다. @ApplicationModule(allowedDependencies = {})를 먼저 적으세요.");
		}
		ExpressionTree value = null;
		for (ExpressionTree argument : annotation.getArguments()) {
			if (argument instanceof AssignmentTree assignment
					&& assignment.getVariable().toString().equals("allowedDependencies")) {
				value = assignment.getExpression();
			}
		}
		List<String> current = new ArrayList<>();
		if (value instanceof LiteralTree literal && literal.getValue() instanceof String s) {
			current.add(s);
		}
		else if (value instanceof NewArrayTree array) {
			for (ExpressionTree item : (array.getInitializers() != null) ? array.getInitializers() : List.<ExpressionTree>of()) {
				if (!(item instanceof LiteralTree literal) || !(literal.getValue() instanceof String s)) {
					throw unreadable(file);
				}
				current.add(s);
			}
		}
		else if (value != null) {
			throw unreadable(file);
		}
		List<String> next = new ArrayList<>(current);
		for (String dependency : dependencies) {
			String module = dependency.replaceAll("\\s*::.*", "").strip();
			boolean covered = current.stream()
				.map((c) -> c.replaceAll("\\s+", ""))
				.anyMatch((c) -> c.equals(module) || c.equals(dependency.replaceAll("\\s+", "")));
			if (!covered && !next.contains(dependency)) {
				next.add(dependency);
			}
		}
		if (next.equals(current) && value != null) {
			return source;
		}
		String array = next.isEmpty() ? "{}"
				: "{ " + String.join(", ", next.stream().map((d) -> "\"" + d + "\"").toList()) + " }";
		if (value != null) {
			int start = parsed.start(value);
			int end = parsed.end(value);
			return source.substring(0, start) + array + source.substring(end);
		}
		int end = parsed.end(annotation);
		String text = source.substring(parsed.start(annotation), end);
		String replaced;
		if (!text.contains("(")) {
			replaced = text + "(allowedDependencies = " + array + ")";
		}
		else if (annotation.getArguments().isEmpty()) {
			replaced = text.substring(0, text.lastIndexOf(')')) + "allowedDependencies = " + array + ")";
		}
		else {
			replaced = text.substring(0, text.lastIndexOf(')')) + ", allowedDependencies = " + array + ")";
		}
		int start = parsed.start(annotation);
		return source.substring(0, start) + replaced + source.substring(end);
	}

	private static CliException unreadable(String file) {
		return new CliException(file + "의 allowedDependencies를 읽을 수 없습니다. 글자 그대로의 문자열 배열로 적어야 cozy link가 바꿀 수 "
				+ "있습니다 (예: allowedDependencies = { \"memo :: spi\" }).");
	}
}
