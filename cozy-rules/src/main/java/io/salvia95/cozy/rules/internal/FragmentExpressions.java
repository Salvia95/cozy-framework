package io.salvia95.cozy.rules.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Thymeleaf fragment 표현식({@code ~{template :: selector(args)}})을 정적으로 해석한다.
 *
 * <p>검사할 수 없는 형태(동적 템플릿 이름, 동적 선택자, 복잡한 마크업 선택자)는 {@link Ref#problem()}에 이유를 담아
 * 돌려준다. 호출하는 쪽은 이것을 R7로 보고한다.
 */
final class FragmentExpressions {

	private static final Pattern TEMPLATE_NAME = Pattern.compile("[A-Za-z0-9_\\-./]+");

	private static final Pattern FRAGMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_\\-]*");

	private static final Pattern ID_SELECTOR = Pattern.compile("(?:[A-Za-z][A-Za-z0-9\\-]*)?#([A-Za-z0-9_\\-]+)");

	private static final Pattern CLASS_SELECTOR = Pattern.compile("(?:[A-Za-z][A-Za-z0-9\\-]*)?\\.([A-Za-z0-9_\\-]+)");

	private static final Pattern DYNAMIC = Pattern.compile("\\$\\{|\\*\\{|#\\{|@\\{|~\\{|\\||__");

	private FragmentExpressions() {
	}

	/** 선택자 종류 */
	enum SelectorKind {

		/** 템플릿 전체 */
		WHOLE,
		/** {@code th:fragment} 이름 또는 요소 이름 */
		NAME,
		/** {@code #id} */
		ID,
		/** {@code .class} */
		CLASS

	}

	/**
	 * @param raw 원문 ({@code ~{...}})
	 * @param template 템플릿 이름. 같은 파일이면 {@code null}
	 * @param kind 선택자 종류
	 * @param selector fragment 이름, id, class 이름
	 * @param argCount 괄호 안 인자 개수. 괄호가 없으면 {@code null}
	 * @param problem 검사할 수 없는 이유 (R7). 검사할 수 있으면 {@code null}
	 * @param args 괄호 안 인자 원문(최상위 쉼표로 나눈 것). 괄호가 없으면 빈 목록
	 */
	record Ref(String raw, String template, SelectorKind kind, String selector, Integer argCount, String problem,
			List<String> args) {

		Ref(String raw, String template, SelectorKind kind, String selector, Integer argCount, String problem) {
			this(raw, template, kind, selector, argCount, problem, List.of());
		}

		boolean sameFile() {
			return this.template == null;
		}

		/** 메시지에 쓸 참조 표기 ({@code wiki/detail :: body}) */
		String display(String currentTemplate) {
			String t = (this.template != null) ? this.template : currentTemplate;
			if (this.kind == SelectorKind.WHOLE) {
				return t;
			}
			String sel = switch (this.kind) {
				case ID -> "#" + this.selector;
				case CLASS -> "." + this.selector;
				default -> this.selector;
			};
			return t + " :: " + sel + ((this.argCount != null) ? "(" + this.argCount + "개 인자)" : "");
		}
	}

	/**
	 * 속성 값 안의 모든 {@code ~{...}}을 찾는다. 인자 안에 중첩된 표현식도 각각 돌려준다. 빈 fragment {@code ~{}}는
	 * 제외한다.
	 */
	static List<Ref> findAll(String value) {
		List<Ref> refs = new ArrayList<>();
		int from = 0;
		while (true) {
			int start = indexOutsideQuotes(value, "~{", from);
			if (start < 0) {
				return refs;
			}
			int end = matchingBrace(value, start + 1);
			if (end < 0) {
				refs.add(new Ref(value.substring(start), null, SelectorKind.WHOLE, null, null,
						"닫는 '}'가 없는 fragment 표현식입니다"));
				return refs;
			}
			String content = value.substring(start + 2, end);
			Ref ref = parse("~{" + content + "}", content);
			if (ref != null) {
				refs.add(ref);
			}
			from = start + 2;
		}
	}

	/** {@code ~{}} 없이 쓴 th:replace/th:insert 값({@code cozy/x :: y})을 해석한다. */
	static Ref parseUnwrapped(String value) {
		return parse(value, value);
	}

	/** @return 빈 fragment면 {@code null} */
	private static Ref parse(String raw, String content) {
		String body = content.strip();
		if (body.isEmpty()) {
			return null;
		}
		int sep = indexOutsideQuotes(body, "::", 0);
		String templatePart = (sep >= 0) ? body.substring(0, sep).strip() : body;
		String selectorPart = (sep >= 0) ? body.substring(sep + 2).strip() : null;

		String template = templatePart;
		if (template.length() >= 2 && template.startsWith("'") && template.endsWith("'")) {
			template = template.substring(1, template.length() - 1);
		}
		if (DYNAMIC.matcher(template).find() || (!template.isEmpty() && !TEMPLATE_NAME.matcher(template).matches())) {
			return problem(raw, "템플릿 경로가 동적입니다");
		}
		if (template.isEmpty() || template.equals("this")) {
			template = null;
		}
		if (selectorPart == null) {
			if (template == null) {
				return null;
			}
			return new Ref(raw, template, SelectorKind.WHOLE, null, null, null);
		}
		if (selectorPart.isEmpty()) {
			return problem(raw, "'::' 뒤에 fragment 이름이 없습니다");
		}

		int open = indexOutsideQuotes(selectorPart, "(", 0);
		String name = (open >= 0) ? selectorPart.substring(0, open).strip() : selectorPart;
		Integer argCount = null;
		List<String> args = List.of();
		if (open >= 0) {
			int close = matchingParen(selectorPart, open);
			if (close < 0 || !selectorPart.substring(close + 1).isBlank()) {
				return problem(raw, "fragment 인자의 괄호가 맞지 않습니다");
			}
			args = splitArgs(selectorPart.substring(open + 1, close));
			argCount = args.size();
		}
		if (DYNAMIC.matcher(name).find()) {
			return problem(raw, "fragment 이름이 동적입니다");
		}
		if (FRAGMENT_NAME.matcher(name).matches()) {
			return new Ref(raw, template, SelectorKind.NAME, name, argCount, null, args);
		}
		var id = ID_SELECTOR.matcher(name);
		if (id.matches() && argCount == null) {
			return new Ref(raw, template, SelectorKind.ID, id.group(1), null, null);
		}
		var cls = CLASS_SELECTOR.matcher(name);
		if (cls.matches() && argCount == null) {
			return new Ref(raw, template, SelectorKind.CLASS, cls.group(1), null, null);
		}
		return problem(raw, "검사할 수 없는 선택자입니다 (fragment 이름, #id, .class만 씁니다)");
	}

	private static Ref problem(String raw, String why) {
		return new Ref(raw, null, SelectorKind.WHOLE, null, null, why);
	}

	/** 최상위 쉼표로 나눈 인자 원문 (앞뒤 공백 제거). 비어 있으면 빈 목록 */
	static List<String> splitArgs(String args) {
		List<String> parts = new ArrayList<>();
		if (args.isBlank()) {
			return parts;
		}
		int depth = 0;
		int start = 0;
		boolean quoted = false;
		for (int i = 0; i < args.length(); i++) {
			char c = args.charAt(i);
			if (quoted) {
				if (c == '\\') {
					i++;
				}
				else if (c == '\'') {
					quoted = false;
				}
				continue;
			}
			switch (c) {
				case '\'' -> quoted = true;
				case '(', '{', '[' -> depth++;
				case ')', '}', ']' -> depth--;
				case ',' -> {
					if (depth == 0) {
						parts.add(args.substring(start, i).strip());
						start = i + 1;
					}
				}
				default -> {
				}
			}
		}
		parts.add(args.substring(start).strip());
		return parts;
	}

	private static int indexOutsideQuotes(String s, String token, int from) {
		boolean quoted = false;
		for (int i = from; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quoted) {
				if (c == '\\') {
					i++;
				}
				else if (c == '\'') {
					quoted = false;
				}
				continue;
			}
			if (c == '\'') {
				quoted = true;
				continue;
			}
			if (s.startsWith(token, i)) {
				return i;
			}
		}
		return -1;
	}

	/** {@code s.charAt(open) == '{'}에 맞는 '}'의 위치 */
	private static int matchingBrace(String s, int open) {
		return matching(s, open, '{', '}');
	}

	private static int matchingParen(String s, int open) {
		return matching(s, open, '(', ')');
	}

	private static int matching(String s, int open, char opening, char closing) {
		int depth = 0;
		boolean quoted = false;
		for (int i = open; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quoted) {
				if (c == '\\') {
					i++;
				}
				else if (c == '\'') {
					quoted = false;
				}
				continue;
			}
			if (c == '\'') {
				quoted = true;
			}
			else if (c == opening) {
				depth++;
			}
			else if (c == closing) {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}
}
