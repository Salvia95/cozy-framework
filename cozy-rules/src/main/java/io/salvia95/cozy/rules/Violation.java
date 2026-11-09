package io.salvia95.cozy.rules;

/**
 * 규칙 위반 하나.
 *
 * <pre>
 * [R3] templates/voc/list.html:42 → wiki/detail :: body — wiki의 내부 템플릿입니다. ...
 * [C8] it.cozy.app.memo.MemoService — 모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다. ...
 * [R3] templates/voc/api/list.html:7 → wiki/spi/card :: card — voc의 allowedDependencies에 wiki가 없습니다. ... 고치는 명령: cozy link voc --uses wiki
 * </pre>
 *
 * @param location 파일 위치, 또는 C8이면 타입·패키지 이름
 * @param line 줄 번호 (1부터, 모르면 0)
 * @param ruleId 템플릿 규칙 {@code R1}~{@code R15}, 모듈 구조 {@code C8}, 빈칸 {@code C9}
 * @param severity 오류 또는 경고
 * @param reference 문제가 된 참조나 경로. 위치 자체가 문제면 {@code null}
 * @param message 무엇이 잘못됐고 어떻게 고치는지
 * @param command 이 위반을 고치는 cozy 명령. 고치는 레시피가 없으면 {@code null}
 */
public record Violation(String location, int line, String ruleId, Severity severity, String reference, String message,
		String command) {

	/** 고치는 명령이 없는 위반 */
	public Violation(String location, int line, String ruleId, Severity severity, String reference, String message) {
		this(location, line, ruleId, severity, reference, message, null);
	}

	public boolean isError() {
		return this.severity == Severity.ERROR;
	}

	/**
	 * 한 줄 메시지. 작은 LLM이 이것만 보고 고칠 수 있게 규칙 ID, 위치, 참조, 고치는 방법을 담는다. 고치는 명령이 있으면 끝에
	 * {@code 고치는 명령: cozy …}를 붙인다.
	 */
	public String format() {
		String where = (this.line > 0) ? this.location + ":" + this.line : this.location;
		String prefix = (this.severity == Severity.WARNING) ? "[" + this.ruleId + " 경고] " : "[" + this.ruleId + "] ";
		String target = (this.reference != null) ? " → " + this.reference : "";
		String fix = (this.command != null) ? " 고치는 명령: " + this.command : "";
		return prefix + where + target + " — " + this.message + fix;
	}

	@Override
	public String toString() {
		return format();
	}
}
