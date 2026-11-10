package io.salvia95.cozy.workbench.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 워크벤치 화면에 보이는 이름. 식별자는 소스에서 다시 찾아야 하므로 지우지 않고, 사람이 읽는 이름을 앞에 둔다.
 */
final class WorkbenchTerms {

	private WorkbenchTerms() {
	}

	/** 화면 스펙의 상태 이름 가운데 관례로 쓰는 것 */
	private static final Map<String, String> STATES = Map.of("default", "기본", "empty", "데이터 없음", "noResults", "검색 결과 없음", "paged",
			"여러 페이지", "invalid", "입력 오류");

	/** 샘플 데이터 값의 종류 */
	static final Map<String, String> KINDS = Map.of("text", "텍스트", "int", "정수", "number", "숫자", "bool", "true/false", "null", "빈 값");

	/** 상태의 표시 이름. 관례에 없는 이름은 그대로 둔다 */
	static String state(String name) {
		return STATES.getOrDefault(name, name);
	}

	/** 상태 이름 → 표시 이름 (받은 순서) */
	static Map<String, String> states(Set<String> names) {
		Map<String, String> labels = new LinkedHashMap<>();
		names.forEach((name) -> labels.put(name, state(name)));
		return labels;
	}
}
