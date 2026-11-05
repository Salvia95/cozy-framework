# ADR-0018 §4 확인 항목 조사 결과

- 상태: 승인 (2026-11-05)
- 출처: PRD §4 "구현 초기에 확인할 것"
- 조사 방법: Maven Central의 POM과 sources jar, npm 레지스트리를 직접 열어 확인했다. Jackson의 동작은 실제 실행으로 확인했다.

PRD §3의 결정과 충돌하는 항목은 없다. 다만 아래 보완 사항이 생겨 ADR-0006과 ADR-0011에 반영했다.

## 1. Boot 4.x와 호환되는 Modulith 버전, EventPublicationRepository, 공식 JDBC 스키마

- Boot 4.1.2에 맞는 버전은 **Spring Modulith 2.1.2**다. 2.1.2는 `spring-boot.version` 4.1.2로 빌드되었다. Boot 4.2와 짝을 이루는 Modulith 2.2는 아직 RC다.
- `org.springframework.modulith.events.core.EventPublicationRepository`의 메서드(2.1.2):
  - 반드시 구현해야 하는 것:
    - `create(TargetEventPublication)`
    - `markCompleted(Object event, PublicationTargetIdentifier, Instant)`
    - `markCompleted(UUID, Instant)`
    - `findIncompletePublications()`
    - `findIncompletePublicationsPublishedBefore(Instant)`
    - `findIncompletePublicationsByEventAndTargetIdentifier(Object, PublicationTargetIdentifier)`
    - `deletePublications(List<UUID>)`
    - `deleteCompletedPublications()`
    - `deleteCompletedPublicationsBefore(Instant)`
  - 기본 구현이 아무 일도 하지 않거나 예외를 던지는 것:
    - `markProcessing(UUID)`, `markFailed(UUID)`
    - `markResubmitted(UUID, Instant)`
    - `findCompletedPublications()`
    - `findFailedPublications(FailedCriteria)`
    - `findByStatus(Status)`, `countByStatus(Status)`
  - 기본 구현이 있는 메서드도 Modulith가 실제로 호출한다. `DefaultEventPublicationRegistry`의 재제출과 장기 미처리(staleness) 처리, `CompletionRegisteringAdvisor`의 상태 전이가 호출하는 곳이다.
- 공식 JDBC 스키마는 v1과 v2가 있다. 현재 구현 기준인 **v2**의 컬럼은 다음과 같다(PostgreSQL 기준):
  `id UUID PK, listener_id TEXT, event_type TEXT, serialized_event TEXT, publication_date TIMESTAMPTZ, completion_date TIMESTAMPTZ, status TEXT, completion_attempts INT, last_resubmission_date TIMESTAMPTZ`
  - 인덱스: completion_date, 그리고 (listener_id, serialized_event). PostgreSQL은 serialized_event에 hash 인덱스를 쓴다.
- 지원 DB는 H2, HSQLDB, MySQL, MariaDB, PostgreSQL, SQL Server, Oracle이다. `DatabaseType`이 package-private enum이라 SQLite를 끼워 넣을 수 없다. 따라서 D6(직접 구현)이 그대로 유효하다.
- 설정 속성: PRD가 쓴 `spring.modulith.events.republish-outstanding-events-on-restart`가 현재 이름이다. 최상위의 `spring.modulith.republish-outstanding-events-on-restart`는 deprecated다.

**보완 (ADR-0006)**:
- cozy_event_publication은 v2 컬럼을 따른다.
- 상태 메서드를 모두 구현하고, 계약 테스트에 FAILED 전이와 재제출을 추가한다.

## 2. Modulith의 Jackson 직렬화 모듈과 Boot 4의 Jackson

- 버전은 맞다. `spring-modulith-events-jackson` 2.1.2는 Jackson 3(`tools.jackson`) 3.1.7을 compile로, Jackson 2(`com.fasterxml`) 2.21.7을 optional로 쓴다. 둘 다 Boot 4.1.2 BOM과 같은 버전이다. Jackson 2용 설정은 deprecated다.
- **분리되지 않는다.** `JacksonEventSerializationConfiguration`은 `@ConditionalOnMissingBean(EventSerializer.class)`이고, 앱에 `JsonMapper` 빈이 있으면 그 빈을 그대로 쓴다. PRD §8.2의 "앱의 일반 ObjectMapper와 분리"는 기본 구성으로는 성립하지 않는다.
- **Jackson 3의 기본값이 바뀌었다.**
  - `FAIL_ON_UNKNOWN_PROPERTIES`는 기본이 false다.
  - `FAIL_ON_NULL_FOR_PRIMITIVES`는 기본이 **true**다.
  - 실행해서 확인한 결과, record 이벤트에 `int`나 `boolean` 필드를 추가하면 과거 JSON을 역직렬화할 때 `MismatchedInputException`이 난다. 이 옵션을 끄면 0과 false로 채워진다.

**보완 (cozy-starter-events, M5)**:
- `CozyEventSerializer`를 `EventSerializer` 빈으로 등록한다. 이 빈은 이벤트 전용 `JsonMapper`를 쓴다.
  - 설정: `FAIL_ON_UNKNOWN_PROPERTIES=false`(명시), `FAIL_ON_NULL_FOR_PRIMITIVES=false`
- 이벤트 호환 테스트(PRD §8.4)도 같은 mapper를 쓰고, 기본형 필드를 추가하는 경우를 계약 테스트에 넣는다.

## 3. Flyway의 SQLite 지원

- flyway-core 12.4.0에 `org.flywaydb.core.internal.database.sqlite`가 들어 있다. `flyway-database-sqlite` 아티팩트는 존재하지 않는다. 별도 모듈은 필요 없다.
- 다만 Boot 4는 자동 설정이 모듈로 나뉘어 있어서, Flyway 자동 설정을 쓰려면 `spring-boot-starter-flyway`(spring-boot-flyway)가 필요하다.

**보완 (ADR-0011, M2)**: cozy-starter-sqlite는 `spring-boot-starter-flyway`에 의존한다.

## 4. Boot 4의 SnakeYAML 포함 여부

- `spring-boot-starter` 4.1.2가 `org.yaml:snakeyaml` 2.6을 compile 의존성으로 포함한다. 스펙과 매니페스트 파싱에 별도 의존성은 필요 없다.

## 그 밖에 확인한 것

- **sqlite-jdbc 3.54.1.0**: `org/sqlite/native/Windows/x86_64/sqlitejdbc.dll`을 포함한다.
- **Thymeleaf**: Boot 4.1.2에서 3.1.5.RELEASE이고 아티팩트 이름은 여전히 `thymeleaf-spring6`다. AttoParser는 Boot BOM이 관리하지 않으므로 cozy-parent에서 고정한다.
- **테스트 프레임워크**: Boot 4 BOM은 JUnit 6(6.0.3)이다. PRD §4는 JUnit 5로 적었지만 BOM을 따른다.
- **vnu 26.11.4 (MIT)**의 전이 의존성: log4j 1.2.17, Jetty 12.0.x, commons-fileupload2 2.0.0-M5, jakarta.servlet-api, Saxon-HE 9.6, icu4j.
  - Jetty 12.0.x는 Boot가 관리하는 Jetty 버전과 다르다.
  - 이 의존성들은 dependencyConvergence와 라이선스 허용 목록에도 걸릴 수 있다.
  - M4에서 서버용 의존성을 제외한 뒤 검증기가 동작하는지 먼저 확인한다. 제외할 수 없으면 대안을 들고 멈춰서 보고한다.
- **Boot 4의 모듈형 스타터**: spring-boot-starter-webmvc, -thymeleaf, -security, -jdbc, -flyway가 있고, 테스트 스타터도 모듈별로 있다. 마일스톤마다 필요한 것만 추가한다.
