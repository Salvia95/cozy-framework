package io.salvia95.cozy.rules.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Thymeleaf 속성 값에서 변수 표현식({@code ${...}})과 선택 표현식({@code *{...}})을 찾고, 표현식이 읽는 최상위 이름을
 * 뽑는다 (R10). SpEL을 실행하지 않고 글자로 훑는다.
 *
 * <ul>
 * <li>{@code a.b.c}에서는 {@code a}만 최상위 이름이다. {@code a[b]}의 {@code b}도 최상위 이름이다.</li>
 * <li>{@code #lists} 같은 유틸리티는 {@code #이름}, 스프링 빈 참조는 {@code @이름}으로 돌려준다.</li>
 * <li>문자열 리터럴, {@code T(...)} 타입 참조, {@code new} 뒤의 클래스 이름, 선택·투영({@code .?[...]},
 * {@code .![...]}) 안쪽(원소 기준 이름), 인라인 맵의 키는 건너뛴다.</li>
 * </ul>
 */
final class ExpressionReads {

	private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "and", "or", "not", "eq", "ne", "lt",
			"gt", "le", "ge", "div", "mod", "instanceof", "matches", "between", "new", "T");

	private ExpressionReads() {
	}

	/**
	 * @param text 원문 ({@code ${memo.title}})
	 * @param body 괄호 안 ({@code memo.title})
	 * @param selection {@code *{...}}이면 true
	 */
	record Expression(String text, String body, boolean selection) {
	}

	/** 값 안의 모든 {@code ${...}}, {@code *{...}} (중첩된 것은 바깥 것 하나로) */
	static List<Expression> find(String value) {
		List<Expression> found = new ArrayList<>();
		int i = 0;
		while (i < value.length() - 1) {
			char c = value.charAt(i);
			if ((c == '$' || c == '*') && value.charAt(i + 1) == '{') {
				int end = closing(value, i + 2);
				if (end < 0) {
					found.add(new Expression(value.substring(i), value.substring(i + 2), c == '*'));
					return found;
				}
				found.add(new Expression(value.substring(i, end + 1), value.substring(i + 2, end), c == '*'));
				i = end + 1;
			}
			else {
				i++;
			}
		}
		return found;
	}

	/** {@code from}부터 짝이 맞는 '}'의 위치. 리터럴 안의 괄호는 세지 않는다 */
	private static int closing(String value, int from) {
		int depth = 1;
		int i = from;
		while (i < value.length()) {
			char c = value.charAt(i);
			if (c == '\'' || c == '"') {
				i = skipLiteral(value, i);
				continue;
			}
			if (c == '{') {
				depth++;
			}
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
			i++;
		}
		return -1;
	}

	/** 리터럴 끝 다음 위치. SpEL은 따옴표 두 개로 따옴표를 쓴다 */
	private static int skipLiteral(String value, int start) {
		char quote = value.charAt(start);
		int i = start + 1;
		while (i < value.length()) {
			if (value.charAt(i) == quote) {
				if (i + 1 < value.length() && value.charAt(i + 1) == quote) {
					i += 2;
					continue;
				}
				return i + 1;
			}
			i++;
		}
		return i;
	}

	/** 표현식이 읽는 최상위 이름 ({@code memo}, {@code #lists}, {@code @memoService}) */
	static Set<String> roots(String body) {
		Set<String> roots = new LinkedHashSet<>();
		int i = 0;
		char previous = 0;
		int braces = 0;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if (c == '\'' || c == '"') {
				i = skipLiteral(body, i);
				previous = 'L';
				continue;
			}
			if (c == '[' && (previous == '?' || previous == '!' || previous == '^' || previous == '$')) {
				i = skipBracket(body, i);
				previous = ']';
				continue;
			}
			if (Character.isJavaIdentifierStart(c)) {
				int start = i;
				while (i < body.length() && Character.isJavaIdentifierPart(body.charAt(i))) {
					i++;
				}
				String name = body.substring(start, i);
				int next = nextNonSpace(body, i);
				if (previous == '.') {
					previous = 'a';
					continue;
				}
				if (previous == '#' || previous == '@') {
					roots.add(previous + name);
					previous = 'a';
					continue;
				}
				if (name.equals("T") && next < body.length() && body.charAt(next) == '(') {
					i = skipParen(body, next);
					previous = ')';
					continue;
				}
				if (name.equals("new")) {
					i = skipQualifiedName(body, i);
					previous = 'a';
					continue;
				}
				boolean mapKey = braces > 0 && next < body.length() && body.charAt(next) == ':';
				if (!KEYWORDS.contains(name) && !mapKey && !Character.isDigit(name.charAt(0))) {
					roots.add(name);
				}
				previous = 'a';
				continue;
			}
			if (Character.isDigit(c)) {
				while (i < body.length() && (Character.isLetterOrDigit(body.charAt(i)) || body.charAt(i) == '.')) {
					i++;
				}
				previous = '0';
				continue;
			}
			if (c == '{') {
				braces++;
			}
			else if (c == '}' && braces > 0) {
				braces--;
			}
			previous = c;
			i++;
		}
		return roots;
	}

	private static int nextNonSpace(String body, int from) {
		int i = from;
		while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
			i++;
		}
		return i;
	}

	private static int skipBracket(String body, int open) {
		return skipPair(body, open, '[', ']');
	}

	private static int skipParen(String body, int open) {
		return skipPair(body, open, '(', ')');
	}

	private static int skipPair(String body, int open, char left, char right) {
		int depth = 0;
		int i = open;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (c == '\'' || c == '"') {
				i = skipLiteral(body, i);
				continue;
			}
			if (c == left) {
				depth++;
			}
			else if (c == right) {
				depth--;
				if (depth == 0) {
					return i + 1;
				}
			}
			i++;
		}
		return i;
	}

	/** {@code new java.util.Date(...)}의 클래스 이름을 건너뛴다 (인자는 그대로 훑는다) */
	private static int skipQualifiedName(String body, int from) {
		int i = nextNonSpace(body, from);
		while (i < body.length() && (Character.isJavaIdentifierPart(body.charAt(i)) || body.charAt(i) == '.')) {
			i++;
		}
		return i;
	}

	/** {@code th:with="a=${x}, b=${y}"}을 최상위 쉼표로 나눈다 */
	static List<String[]> assignments(String value) {
		List<String[]> result = new ArrayList<>();
		int depth = 0;
		int start = 0;
		int i = 0;
		while (i <= value.length()) {
			char c = (i < value.length()) ? value.charAt(i) : ',';
			if (i < value.length() && (c == '\'' || c == '"')) {
				i = skipLiteral(value, i);
				continue;
			}
			if (c == '{' || c == '(' || c == '[') {
				depth++;
			}
			else if (c == '}' || c == ')' || c == ']') {
				depth--;
			}
			else if (c == ',' && depth <= 0) {
				String part = value.substring(start, Math.min(i, value.length()));
				int equals = part.indexOf('=');
				if (equals > 0) {
					result.add(new String[] { part.substring(0, equals).strip(), part.substring(equals + 1) });
				}
				start = i + 1;
			}
			i++;
		}
		return result;
	}
}
