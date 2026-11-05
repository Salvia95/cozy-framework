# ADR-0006 이벤트 발행 기록을 SQLite에 직접 구현

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D6, §4 조사 결과(ADR-0018)로 보완

## 결정

`EventPublicationRepository`를 SQLite용으로 직접 구현한다(`SqliteEventPublicationRepository`, JdbcClient 사용). Modulith 공식 이벤트 저장소 모듈은 빌드에서 차단한다.

## 근거

- 업무 데이터와 같은 트랜잭션에 기록해야 아웃박스가 보장된다.
- Modulith 2.1.2의 공식 JDBC 스키마는 H2, HSQLDB, MySQL, MariaDB, PostgreSQL, SQL Server, Oracle을 지원한다. SQLite는 없다.
- 지원 DB 목록(`DatabaseType`)은 package-private enum이라 외부에서 확장할 수 없다.

## 결과

- 공식 v2 스키마의 컬럼을 그대로 따르고, 타입만 SQLite에 맞게 바꾼다(UUID와 시각은 TEXT).
- 2.x의 상태 메서드도 모두 구현한다: markProcessing, markFailed, markResubmitted, findFailedPublications, findByStatus, countByStatus, findCompletedPublications. 이 메서드의 기본 구현은 아무 일도 하지 않으므로, 빠뜨리면 재시도와 장기 미처리 감지가 오류 없이 동작하지 않는다.
- 공식 `JdbcEventPublicationRepositoryV2`(Apache-2.0)를 참고해 옮긴다.
