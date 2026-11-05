package io.salvia95.cozy.events;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이벤트 직렬화 전용 JsonMapper (PRD §8.2, ADR-0018). 앱의 일반 JsonMapper와 분리한다.
 *
 * <ul>
 * <li>알 수 없는 필드는 무시한다. 새 버전이 쓴 필드를 옛 코드가 읽는 경우는 없지만, 지운 필드가 남은 기록을 읽을 수 있다.</li>
 * <li>primitive 필드가 없으면 0, false로 채운다. Jackson 3의 기본값(실패)을 끈다. 그래야 필드를 추가한 새 코드가 옛 기록을
 * 읽는다.</li>
 * <li>맵 키를 정렬한다. 발행 기록을 직렬화한 문자열로 찾으므로 같은 이벤트는 늘 같은 문자열이어야 한다.</li>
 * </ul>
 * cozy-test의 이벤트 호환 검사(C6)도 이 매퍼를 쓴다.
 */
public final class CozyEventMapper {

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
		.build();

	private CozyEventMapper() {
	}

	public static JsonMapper get() {
		return MAPPER;
	}
}
