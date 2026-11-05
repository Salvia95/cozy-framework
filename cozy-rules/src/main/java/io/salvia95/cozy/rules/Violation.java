package io.salvia95.cozy.rules;

/**
 * 규칙 위반 하나 (PRD §7 실패 메시지 형식).
 *
 * <pre>[R3] templates/voc/list.html:42 → wiki/detail :: body — wiki의 내부 템플릿입니다. ...</pre>
 *
 * @param location 파일 위치
 * @param line 줄 번호 (1부터, 모르면 0)
 * @param ruleId {@code R1}~{@code R8}
 * @param severity 오류 또는 경고
 * @param reference 문제가 된 참조나 경로
 * @param message 무엇이 잘못됐고 어떻게 고치는지
 */
public record Violation(String location, int line, String ruleId, Severity severity, String reference,
		String message) {

	public boolean isError() {
		return this.severity == Severity.ERROR;
	}

	/** 한 줄 메시지. 작은 LLM이 이것만 보고 고칠 수 있게 규칙 ID, 위치, 참조, 고치는 방법을 담는다. */
	public String format() {
		String where = (this.line > 0) ? this.location + ":" + this.line : this.location;
		String prefix = (this.severity == Severity.WARNING) ? "[" + this.ruleId + " 경고] " : "[" + this.ruleId + "] ";
		return prefix + where + " → " + this.reference + " — " + this.message;
	}

	@Override
	public String toString() {
		return format();
	}
}
