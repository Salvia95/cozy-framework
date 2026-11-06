# 영속성 (cozy-starter-sqlite)

SQLite, MyBatis, Flyway를 묶은 스타터다(PRD §6.3). 이 스타터에 의존하면 DB 파일 위치, 연결 설정, 트랜잭션 모드, 마이그레이션 순서, 시간 타입 저장 형식, 예외 분류가 정해진다.

## 설정 속성

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `cozy.sqlite.file` | `{dataDir}/data/app.db` | DB 파일. 상대 경로면 데이터 루트 기준이다. |
| `cozy.sqlite.backup.keep` | `5` | 마이그레이션 전 백업을 몇 개 남길지 |

Cozy가 정하는 기본값은 모두 앱 설정으로 바꿀 수 있다.

| 속성 | 값 |
| --- | --- |
| `spring.datasource.url` | `jdbc:sqlite:{DB 파일}` |
| `spring.datasource.hikari.maximum-pool-size` | `4` |
| `spring.datasource.hikari.data-source-properties[journal_mode / busy_timeout / foreign_keys / synchronous]` | `WAL` / `5000` / `true` / `NORMAL` |
| `spring.flyway.baseline-on-migrate`, `baseline-version` | `true`, `0` (ADR-0011) |
| `mybatis.mapper-locations` | `classpath*:mapper/*/*.xml` |
| `mybatis.configuration.map-underscore-to-camel-case` | `true` |
| `mybatis.configuration.arg-name-based-constructor-auto-mapping` | `true` (record로 바로 매핑) |

연결 설정은 풀의 모든 연결에 적용된다. 기동할 때 실제 값을 한 줄 남긴다.

```
[cozy] SQLite …/data/app.db {journal_mode=wal, busy_timeout=5000, foreign_keys=1, synchronous=1, sqlite_version=3.54.1}
```

## 트랜잭션 (ADR-0020)

```java
@Transactional                     // 쓰기: BEGIN IMMEDIATE. 쓰기끼리 busy_timeout 안에서 줄을 선다
public long create(...) { ... }

@Transactional(readOnly = true)    // 읽기: BEGIN DEFERRED. 쓰기를 막지 않는다
public List<Memo> list() { ... }
```

- 읽기만 하는 메서드에는 반드시 `readOnly = true`를 붙인다. 붙이지 않으면 쓰기 트랜잭션으로 취급되어 다른 쓰기와 줄을 선다.
- 쓰기 트랜잭션 안에서 외부 API 호출처럼 오래 걸리는 일을 하지 않는다. 그동안 다른 쓰기가 모두 기다린다.

## 마이그레이션

| 구분 | 위치 | 이력 테이블 | 파일 이름 |
| --- | --- | --- | --- |
| 앱 | `classpath:db/migration` | `flyway_schema_history` | `V{yyyyMMddHHmm}__{module}_{설명}.sql` |
| 프레임워크 | `classpath:db/cozy/{스타터}` (`CozyMigrationLocation` 빈) | `cozy_schema_history` | `V{스타터 번호}_{순번}__{설명}.sql` |

예: `V202611061200__memo_create_memo.sql`

- **실행 순서**: 이름 검사 → (변경이 있고 테이블이 있으면) 백업 → 프레임워크 → 앱
- **이름 검사**: 앱 파일 이름이 규칙과 다르면 아무것도 적용하지 않고 기동이 실패한다. 메시지에 고칠 파일과 형식이 나온다.
- **적용된 파일**: 적용된 파일은 고치지 않는다. 체크섬이 바뀌면 기동이 멈춘다. 고칠 내용은 새 파일로 추가한다.
- **백업**: `{dataDir}/backup/app-{yyyyMMdd-HHmmss-SSS}.db`. `VACUUM INTO`로 만든 일관된 사본이다.

## MyBatis

- **Mapper 인터페이스**: `{module}/internal/infra/`에 `@Mapper`로 둔다. app.command는 Mapper를 infra의 저장소나 Mapper로만 쓰고 JDBC·SqlSession을 직접 쓰지 않는다(C8). 결과로 받는 도메인 모델은 `internal/domain/`, 행 클래스는 `internal/infra/`에 둔다.
- **SQL**: `resources/mapper/{module}/{Mapper}.xml`에 둔다. namespace는 인터페이스 FQN이다.
- **결과 매핑**: record로 받는다. 컬럼 `created_at`은 컴포넌트 `createdAt`으로 매핑된다(컴파일 옵션 `-parameters`는 cozy-parent가 켠다).
- **생성 키 받기**: getter와 setter가 있는 행 클래스를 쓰고 `useGeneratedKeys="true" keyProperty="id"`를 지정한다.

## 저장 형식 (TypeHandler, 자동 등록)

| Java | SQLite | 예 |
| --- | --- | --- |
| `Instant` | TEXT, UTC, 소수점 9자리 고정 | `2026-11-06T01:02:03.123456789Z` |
| `LocalDateTime` | TEXT, 소수점 9자리 고정 | `2026-11-06T10:02:03.000000000` |
| `LocalDate` | TEXT | `2026-11-06` |
| `UUID` | TEXT, 소문자 | `0f8fad5b-d9cb-469f-a165-70867728950e` |
| `boolean`, `Boolean` | INTEGER 0/1 | `1` |

- **정렬**: 자릿수가 고정이라 문자열 정렬이 시간 순서와 같다.
- **읽기**: SQLite의 `CURRENT_TIMESTAMP` 형식(`2026-11-06 01:02:03`, UTC로 해석)과 소수점 자릿수가 다른 값도 받아들인다.
- **NULL과 기본형**: NULL 컬럼은 기본형(`boolean`, `long`) 컴포넌트에 담을 수 없다. NULL이 가능하면 래퍼 타입(`Boolean`, `Long`)을 쓴다.

## 예외 분류

Spring의 sql-error-codes.xml에는 SQLite가 없다. 그래서 Cozy가 SQLite 결과 코드로 직접 분류한다. JdbcTemplate, JdbcClient, MyBatis, 트랜잭션 커밋이 같은 번역기를 쓴다.

| SQLite 코드 | 예외 |
| --- | --- |
| SQLITE_BUSY, SQLITE_LOCKED (확장 코드 포함) | `CozyDatabaseBusyException` (`CannotAcquireLockException`, 일시적 오류) |
| PRIMARY KEY, UNIQUE, ROWID 위반 | `DuplicateKeyException` |
| NOT NULL, FOREIGN KEY, CHECK 위반 | `DataIntegrityViolationException` |
