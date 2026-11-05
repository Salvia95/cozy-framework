package io.salvia95.cozy.sqlite;

/**
 * 프레임워크 스타터가 소유한 마이그레이션 위치 (ADR-0011).
 *
 * <p>각 스타터는 이 타입의 빈을 하나 등록한다. 등록된 위치는 모두 {@code cozy_schema_history} 이력으로,
 * 앱 마이그레이션보다 먼저 실행된다. 스타터마다 버전 접두 번호를 따로 쓴다(예: events는 {@code V2_1__...}).
 * 앱은 이 타입을 쓰지 않는다. 앱 마이그레이션은 {@code classpath:db/migration}에 둔다.
 *
 * @param location Flyway 위치 (예: {@code classpath:db/cozy/events})
 */
public record CozyMigrationLocation(String location) {

	public CozyMigrationLocation {
		if (location == null || !location.startsWith("classpath:db/cozy/")) {
			throw new IllegalArgumentException(
					"[cozy] 프레임워크 마이그레이션 위치는 classpath:db/cozy/ 아래여야 합니다: " + location);
		}
	}
}
