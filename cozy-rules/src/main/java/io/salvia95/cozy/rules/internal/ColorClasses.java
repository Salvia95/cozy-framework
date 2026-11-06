package io.salvia95.cozy.rules.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R14: 앱 템플릿이 직접 고르면 안 되는 색 클래스 (docs/conventions/design.md). 색은 tone을 받는 cozy 컴포넌트가 고른다.
 * 앱에 허용하는 것은 중립(text-secondary 등)과 주 동작 버튼(btn-primary)뿐이다.
 */
final class ColorClasses {

	private static final String COLORS = "primary|secondary|success|info|warning|danger|light|dark|muted|white|black|"
			+ "blue|azure|indigo|purple|pink|red|orange|yellow|lime|green|teal|cyan|gray(?:-\\d+)?";

	private static final Pattern COLOR = Pattern
		.compile("(?:bg|text|border|link|badge|alert|avatar|btn|btn-outline|btn-ghost|status|ribbon)-(?:" + COLORS
				+ ")(?:-lt|-fg|-subtle|-emphasis)?");

	/** 식으로 이어 붙인 색 클래스 (bg-${color}-lt) */
	private static final Pattern BUILT = Pattern.compile("(?:bg|text|border|badge|alert|avatar|btn|btn-outline|btn-ghost|status)-\\$\\{.*");

	private static final Set<String> ALLOWED = Set.of("text-secondary", "text-muted", "btn-primary");

	private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

	/** |...| 리터럴 치환 안의 글자 */
	private static final Pattern SUBSTITUTION = Pattern.compile("\\|([^|]*)\\|");

	private ColorClasses() {
	}

	static boolean forbidden(String token) {
		if (token.isEmpty() || ALLOWED.contains(token)) {
			return false;
		}
		return COLOR.matcher(token).matches() || BUILT.matcher(token).matches();
	}

	/** th:classappend·th:class 식에서 글자 그대로 쓴 클래스 */
	static List<String> tokens(String expression) {
		List<String> tokens = new ArrayList<>();
		for (Pattern pattern : List.of(LITERAL, SUBSTITUTION)) {
			Matcher m = pattern.matcher(expression);
			while (m.find()) {
				for (String token : m.group(1).strip().split("\\s+")) {
					if (!token.isEmpty()) {
						tokens.add(token);
					}
				}
			}
		}
		return tokens;
	}
}
