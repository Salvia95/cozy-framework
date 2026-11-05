package io.salvia95.cozy.rules;

import java.util.List;

import io.salvia95.cozy.rules.internal.RuleChecker;

/**
 * 템플릿 경계 규칙 R1~R8을 검사한다 (PRD §6.2, §7).
 *
 * <pre>
 * List&lt;Violation&gt; violations = TemplateRules.check(new RulesInput(templates, modules, manifest, false));
 * </pre>
 */
public final class TemplateRules {

	private TemplateRules() {
	}

	/**
	 * @return 위반 목록 (위치, 줄 순). 예외를 던지지 않는다. 읽을 수 없는 템플릿도 R7 위반으로 돌려준다
	 */
	public static List<Violation> check(RulesInput input) {
		return RuleChecker.check(input);
	}

	/** 템플릿이 선언한 {@code th:fragment} 이름 (선언 순서) */
	public static List<String> declaredFragments(TemplateFile file) {
		return RuleChecker.declaredFragments(file);
	}
}
