package io.salvia95.cozy.rules;

import java.util.Map;

/**
 * 규칙 ID에 붙이는 짧은 이름. 검사 결과를 사람에게 보일 때 ID 앞에 쓴다. 규칙의 내용은 docs/conventions/rules.md와 testing.md에 있다.
 */
public final class RuleNames {

	private RuleNames() {
	}

	private static final Map<String, String> NAMES = Map.ofEntries(Map.entry("R1", "템플릿 폴더 위치"), Map.entry("R2", "최상위 템플릿 금지"),
			Map.entry("R3", "다른 모듈 템플릿 참조"), Map.entry("R4", "계층 참조 방향"), Map.entry("R5", "공용 템플릿 참조 범위"),
			Map.entry("R6", "참조 대상과 인자 개수"), Map.entry("R7", "동적 참조 금지"), Map.entry("R8", "매니페스트 등록"),
			Map.entry("R9", "모듈 내부 참조 방향"), Map.entry("R10", "프래그먼트 변수 범위"), Map.entry("R11", "반복과 삽입 동시 사용"),
			Map.entry("R12", "프래그먼트 이름 충돌"), Map.entry("R13", "아이콘 이름"), Map.entry("R14", "색상 클래스 사용"),
			Map.entry("R15", "주 버튼 위치"), Map.entry("C8", "모듈 내부 구조"), Map.entry("C9", "미구현 항목"));

	/** 규칙의 이름. 모르는 ID면 빈 문자열 */
	public static String of(String id) {
		return NAMES.getOrDefault(id, "");
	}
}
