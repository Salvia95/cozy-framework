package io.salvia95.cozy.cli.internal.recipe;

import java.util.Set;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;

/** 레시피의 이름 규칙과 표기 바꾸기 (ADR-0032 부록 §1) */
public final class Names {

	private static final Pattern MODULE = Pattern.compile("[a-z][a-z0-9]*");

	private static final Pattern PASCAL = Pattern.compile("[A-Z][A-Za-z0-9]*");

	private static final Pattern KEBAB = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

	private static final Pattern SNAKE = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");

	/** 템플릿 폴더 예약어(R1)와 Java 예약어 */
	private static final Set<String> RESERVED = Set.of("cozy", "fragments", "error", "abstract", "assert", "boolean", "break",
			"byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
			"final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
			"native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
			"switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "record",
			"var", "yield", "sealed", "permits", "true", "false", "null");

	/** HTML 요소 이름과 겹치는 fragment 이름 (R12) */
	private static final Set<String> HTML_ELEMENTS = Set.of("a", "abbr", "address", "article", "aside", "audio", "b", "body",
			"button", "canvas", "caption", "code", "col", "data", "dd", "details", "dialog", "div", "dl", "dt", "em", "fieldset",
			"figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hr", "html", "i", "img", "input",
			"label", "legend", "li", "link", "main", "menu", "meta", "nav", "ol", "option", "output", "p", "picture", "pre",
			"progress", "q", "s", "section", "select", "small", "span", "strong", "summary", "table", "tbody", "td",
			"template", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "u", "ul", "video");

	private Names() {
	}

	public static String module(String name) {
		if (name == null || !MODULE.matcher(name).matches()) {
			throw new CliException("모듈 이름 '" + name + "'는 영문 소문자로 시작하는 소문자·숫자여야 합니다 (예: memo, voc2).");
		}
		if (RESERVED.contains(name)) {
			throw new CliException("'" + name + "'는 예약어라 모듈 이름으로 쓸 수 없습니다.");
		}
		return name;
	}

	public static String pascal(String name, String what) {
		if (name == null || !PASCAL.matcher(name).matches()) {
			throw new CliException(what + " 이름 '" + name + "'는 PascalCase여야 합니다 (예: Memo, MemoCreated).");
		}
		return name;
	}

	public static String kebab(String name, String what) {
		if (name == null || !KEBAB.matcher(name).matches()) {
			throw new CliException(what + " 이름 '" + name + "'는 소문자와 하이픈이어야 합니다 (예: list, recent-activity).");
		}
		return name;
	}

	public static String snake(String name, String what) {
		if (name == null || !SNAKE.matcher(name).matches()) {
			throw new CliException(what + " '" + name + "'는 소문자·숫자와 밑줄이어야 합니다 (예: create_activity).");
		}
		return name;
	}

	/** {@code recent-activity} → {@code recentActivity} */
	public static String camelOfKebab(String kebab) {
		StringBuilder out = new StringBuilder();
		boolean upper = false;
		for (char c : kebab.toCharArray()) {
			if (c == '-') {
				upper = true;
			}
			else {
				out.append(upper ? Character.toUpperCase(c) : c);
				upper = false;
			}
		}
		return out.toString();
	}

	/** {@code recent-activity} → {@code RecentActivity} */
	public static String pascalOfKebab(String kebab) {
		String camel = camelOfKebab(kebab);
		return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
	}

	/** {@code MemoItem} → {@code memoItem} */
	public static String camel(String pascal) {
		return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
	}

	/** {@code MemoItem} → {@code memo_item} */
	public static String snakeOf(String pascal) {
		return pascal.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
	}

	/** {@code MemoItem} → {@code memo-item} */
	public static String kebabOf(String pascal) {
		return snakeOf(pascal).replace('_', '-');
	}

	/** {@code memo} → {@code Memo} */
	public static String capitalize(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	/** fragment 이름이 같은 파일의 요소 이름과 겹치지 않게 (R12) */
	public static String fragment(String name) {
		if (HTML_ELEMENTS.contains(name.toLowerCase())) {
			throw new CliException("fragment 이름 '" + name + "'는 HTML 요소 이름과 같아 쓸 수 없습니다(R12). 모듈 이름을 붙이세요 (예: memoForm).");
		}
		return name;
	}

	/** 화면 문구(라벨, 제목). 템플릿 식과 Java 문자열에 그대로 넣으므로 따옴표·꺾쇠·중괄호·$·역슬래시를 쓰지 않는다 */
	public static String label(String text, String what) {
		if (text == null || text.isBlank() || text.matches(".*['\"<>{}$\\\\;].*")) {
			throw new CliException(what + " '" + text + "'에는 따옴표, <>, {}, $, \\, ;를 쓰지 않습니다.");
		}
		return text.strip();
	}

	// ── 한국어 조사 ───────────────────────────────────────────────

	/** 라벨 마지막 글자의 받침으로 고른다. 한글이 아니면 둘을 함께 쓴다 ("VOC을(를)") */
	public static String objectParticle(String label) {
		return label + particle(label, "을", "를");
	}

	public static String topicParticle(String label) {
		return label + particle(label, "은", "는");
	}

	public static String subjectParticle(String label) {
		return label + particle(label, "이", "가");
	}

	private static String particle(String label, String withFinal, String withoutFinal) {
		char last = label.charAt(label.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return withFinal + "(" + withoutFinal + ")";
		}
		return ((last - 0xAC00) % 28 != 0) ? withFinal : withoutFinal;
	}
}
