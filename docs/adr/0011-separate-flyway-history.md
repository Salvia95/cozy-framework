# ADR-0011 프레임워크 테이블은 별도 Flyway 이력

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D11, §4 조사 결과(ADR-0018)로 보완

## 결정

프레임워크가 소유한 테이블은 `cozy_schema_history`로, 앱 테이블은 기본 이력 테이블로 따로 관리한다. 실행 순서는 프레임워크 → 앱이다.

## 근거

- 앱의 마이그레이션 버전과 충돌하지 않고 프레임워크 스키마만 올릴 수 있다.

## 결과

- Boot 4는 자동 설정이 모듈로 나뉘어 있어 `spring-boot-starter-flyway`가 필요하다. SQLite 지원은 flyway-core에 들어 있다.
- 스타터는 자기 마이그레이션 위치를 `CozyMigrationLocation` 빈으로 등록한다. 위치는 `classpath:db/cozy/` 아래여야 한다. 와일드카드 위치에 의존하지 않는다.

## 구현 규칙 (M2, 2026-11-06)

| 항목 | 규칙 |
| --- | --- |
| 프레임워크 파일 이름 | `V{스타터 번호}_{순번}__{설명}.sql`. 스타터 번호는 1 = cozy-starter-sqlite(예약), 2 = cozy-starter-events이고, 새 스타터는 다음 번호를 받는다. |
| 프레임워크 Flyway | `cozy_schema_history`, `outOfOrder=true`. 스타터마다 번호대가 달라서, 한 스타터가 나중에 낮은 번호를 추가할 수 있다. `failOnMissingLocations=false` |
| 앱 파일 이름 | `V{yyyyMMddHHmm}__{module}_{설명}.sql`. 기동할 때 검사하고, 어기면 아무것도 적용하지 않고 실패한다. 날짜도 실제 날짜인지 검사한다. R__ 반복 마이그레이션과 Java 마이그레이션은 쓰지 않는다. |
| 기준선 | 두 Flyway 모두 `baselineOnMigrate=true`, `baselineVersion=0`이다. 먼저 실행된 쪽이 테이블을 만들어 스키마가 비어 있지 않으므로, 이력이 없는 쪽은 0번 기준선에서 시작해 모든 파일을 적용한다. 앱 버전은 12자리 날짜라 0보다 크다. |
| 백업 | 적용할 마이그레이션이 있고 사용자 테이블이 이미 있을 때만, `VACUUM INTO`로 `{dataDir}/backup/{db}-{yyyyMMdd-HHmmss-SSS}.db`를 만든다. 최근 `cozy.sqlite.backup.keep`(5)개만 남긴다. 백업이 실패하면 마이그레이션을 멈춘다. |
| 체크섬 | Flyway의 `validateOnMigrate` 기본값을 유지한다. 적용된 파일이 바뀌면 기동이 멈춘다. |

MigrationTest가 검증하는 것:
- 프레임워크 마이그레이션을 추가해도 앱 이력의 행(순번, 버전, 체크섬, 적용 시각)이 그대로다.
- 첫 설치에서는 백업하지 않고, 변경이 있을 때만 백업한다.
- 백업은 최근 5개만 남는다.
- 체크섬이 바뀌면 기동이 실패한다.
- 이름 규칙을 어기면 기동이 실패한다.
