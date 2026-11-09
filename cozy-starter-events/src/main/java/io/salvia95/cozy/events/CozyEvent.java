package io.salvia95.cozy.events;

import java.util.UUID;

/**
 * 모듈 사이의 이벤트. 모든 {@code @ApplicationModuleListener}의 파라미터는 이것을 구현해야 한다(cozy-test가
 * 검사한다).
 *
 * <pre>
 * public record MemoCreated(UUID eventId, int schemaVersion, long memoId, String title) implements CozyEvent {
 *     public static MemoCreated of(long memoId, String title) {
 *         return new MemoCreated(UUID.randomUUID(), 1, memoId, title);
 *     }
 * }
 * </pre>
 *
 * 스키마 진화 규칙(필드 추가만, 기본값, schemaVersion 올리기, FQN 유지, 버전별 샘플)은 docs/conventions/events.md에 있다.
 */
public interface CozyEvent {

	/** 멱등성 키. 발행할 때 만든다. 인박스가 이 값으로 중복 처리를 막는다 */
	UUID eventId();

	/** 1부터 시작한다. 필드를 추가하면 올린다 */
	int schemaVersion();

}
