package io.salvia95.cozy.ui.internal;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 매니페스트 샘플과 화면 스펙 fixture를 선언된 타입으로 바꾼다 (PRD §6.5: 타입과 어긋나면 실패).
 *
 * <p>앱의 JsonMapper와 분리한 엄격한 설정이다. 모르는 필드, 빠진 생성자(record) 필드, primitive의 null은 모두 실패한다.
 */
final class FixtureMapper {

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
		.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.build();

	private FixtureMapper() {
	}

	/**
	 * @param value YAML에서 읽은 값
	 * @param typeName FQN. 중첩 클래스는 {@code Outer.Inner}와 {@code Outer$Inner} 모두 된다
	 * @param what 메시지에 쓸 설명
	 */
	static Object convert(Object value, String typeName, String what) {
		Class<?> type = loadType(typeName, what);
		try {
			return MAPPER.convertValue(value, type);
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("[cozy] " + what + "의 값이 " + type.getName() + "와 맞지 않습니다: "
					+ ex.getMessage(), ex);
		}
	}

	static Class<?> loadType(String typeName, String what) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String candidate = typeName;
		while (true) {
			try {
				return Class.forName(candidate, false, classLoader);
			}
			catch (ClassNotFoundException ex) {
				int dot = candidate.lastIndexOf('.');
				if (dot < 0) {
					throw new IllegalStateException("[cozy] " + what + "의 타입 " + typeName + "를 찾을 수 없습니다", ex);
				}
				candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
			}
		}
	}
}
