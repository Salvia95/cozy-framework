# ADR-0020 쓰기 트랜잭션은 BEGIN IMMEDIATE

- 상태: 승인 (2026-11-06)
- 출처: PRD §13 "쓰기 트랜잭션 모드" 미결 항목. 기본값은 DEFERRED였고, 교착이 나면 "쓰기 트랜잭션만 IMMEDIATE로 전환 검토"로 되어 있었다.

## 결정

Spring의 쓰기 트랜잭션(`@Transactional`)은 `BEGIN IMMEDIATE`로, 읽기 전용 트랜잭션(`@Transactional(readOnly = true)`)은 `BEGIN DEFERRED`로 시작한다. 트랜잭션 밖의 자동 커밋 문장은 바뀌지 않는다.

## 근거

M2의 동시 쓰기 테스트(스레드 8개 × 250건) 결과는 다음과 같다.

| 트랜잭션 | DEFERRED (기본) | IMMEDIATE (이 결정) |
| --- | --- | --- |
| 쓰기만 함 (INSERT) | 실패 0건 | 실패 0건 |
| 읽은 뒤 씀 (SELECT → INSERT) | **2000건 중 1109건 실패** (SQLITE_BUSY) | 실패 0건 |

- DEFERRED 트랜잭션은 첫 읽기에서 읽기 잠금을 잡는다. 그 사이 다른 연결이 먼저 쓰면, 쓰기로 올라갈 때 `busy_timeout`을 기다리지 않고 바로 SQLITE_BUSY(BUSY_SNAPSHOT)로 실패한다. SQLite가 일관성을 지키려고 하는 정상 동작이다.
- 조회한 뒤 수정하는 패턴은 CRUD에서 가장 흔하다. 이벤트 리스너(M5)도 인박스를 확인한 뒤 쓴다.
- IMMEDIATE는 시작할 때 쓰기 잠금을 잡는다. 그래서 쓰기 트랜잭션끼리는 `busy_timeout`(5초) 안에서 줄을 서고 실패하지 않는다. 단일 사용자 데스크톱 앱이라 쓰기를 직렬화해도 처리량 손해가 작다.

## 구현

- `CozySqliteTransactionManager`(`JdbcTransactionManager` 상속)가 Boot의 트랜잭션 매니저를 대신한다.
- sqlite-jdbc는 `setAutoCommit(false)`를 호출하는 순간 연결 설정의 모드로 BEGIN을 실행한다. 그래서 쓰기 트랜잭션은 다음 순서로 시작한다.
  1. 시작 직후의 빈 DEFERRED 트랜잭션을 닫는다. 이 시점에는 아직 잠금이 없다.
  2. `BEGIN IMMEDIATE`로 다시 연다.
- 트랜잭션이 끝나면 연결의 모드를 DEFERRED로 되돌린다.
- `BEGIN IMMEDIATE`가 `busy_timeout` 안에 잠금을 못 잡으면 `CozyDatabaseBusyException`을 던진다. 이때 sqlite-jdbc에 남는 "자동 커밋 꺼짐" 표시를 지워서, 연결이 깨진 채로 풀에 돌아가지 않게 한다. 이 버그는 M2 테스트에서 발견했고 회귀 테스트로 고정했다.
- 커밋 예외도 같은 `SqliteExceptionTranslator`로 번역한다.
- DataSource는 교체하지 않는다. 교체하면 Boot의 Hikari 설정 바인딩을 다시 구현해야 하기 때문이다.

## 결과

- **읽기 전용 표시**: 읽기만 하는 서비스 메서드는 `@Transactional(readOnly = true)`로 표시해야 쓰기와 줄을 서지 않는다. 이 규칙은 docs/conventions/sqlite.md와 AGENTS.md(M7)에 적는다.
- **오래 걸리는 작업**: 쓰기 트랜잭션 안에서 외부 호출처럼 오래 걸리는 작업을 하면 다른 쓰기가 모두 기다린다. 이것은 원래 이벤트 규칙(PRD §8.3)이 금지하는 패턴이다.
- **검증**: TransactionModeTest가 다음을 검사한다.
  - 쓰기는 IMMEDIATE, 읽기 전용은 DEFERRED로 시작하고, 끝나면 모드가 되돌아간다.
  - 쓰기 트랜잭션끼리는 줄을 선다.
  - 읽기 전용 트랜잭션은 쓰기를 막지 않는다.
  - 잠금을 못 잡으면 분류된 예외를 던지고, 그 뒤에도 풀이 정상이다.
