package {{pkg}};

{{imports}}
import io.salvia95.cozy.events.CozyEvent;

/**
 * {{what}} (cozy add event). 받는 쪽은 package-info.java에 이 모듈의 spi 의존을 선언하고 {@code @ApplicationModuleListener}로
 * 받는다. 스키마 진화 규칙: 필드 추가만, schemaVersion 올리기, 버전별 샘플(src/test/resources/events/{{Event}}/,
 * docs/conventions/events.md).
 */
public record {{Event}}(UUID eventId, int schemaVersion{{components}}) implements CozyEvent {

	public static {{Event}} of({{params}}) {
		return new {{Event}}(UUID.randomUUID(), 1{{args}});
	}
}
