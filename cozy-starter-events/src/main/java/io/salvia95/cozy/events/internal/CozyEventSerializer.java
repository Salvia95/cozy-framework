package io.salvia95.cozy.events.internal;

import org.springframework.modulith.events.core.EventSerializer;

import io.salvia95.cozy.events.CozyEventMapper;

/**
 * 발행 기록의 이벤트 직렬화. Modulith 기본 구성은 앱의 JsonMapper를 쓰므로, 이 빈을 등록해 전용 매퍼로 바꾼다 (ADR-0018).
 */
public class CozyEventSerializer implements EventSerializer {

	@Override
	public Object serialize(Object event) {
		return CozyEventMapper.get().writeValueAsString(event);
	}

	@Override
	public <T> T deserialize(Object serialized, Class<T> type) {
		return CozyEventMapper.get().readValue(serialized.toString(), type);
	}
}
