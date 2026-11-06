# ADR-0017 버전 정책: 구현 중에는 최신 GA

- 상태: 승인 (2026-11-05)
- 출처: 구현 전 확인 사항. PRD §4의 "최신 GA 패치를 고정"을 확장한다.

## 결정

- 구현하는 동안 모든 구성 요소는 **최신 GA**를 쓴다. RC, milestone, npm `next` 태그는 제외한다.
- 안정화 단계에서 그 시점의 버전 집합을 스냅샷으로 고정한다.
- 마일스톤을 시작할 때마다 최신 GA를 다시 확인하고 cozy-parent의 버전을 올린다.
- **Boot BOM이 관리하는 라이브러리**는 Boot 버전을 따르고 개별 버전을 덮어쓰지 않는다. Spring 계열, Thymeleaf, Jackson, Flyway, Hikari, JUnit 등이다.
  - 예: Flyway의 최신 GA는 13.x이지만 Boot 4.1.2는 12.4.0을 검증하고 관리한다.
  - Boot 자체를 최신 GA로 올리면 이 라이브러리들도 함께 따라온다.
- **Boot가 관리하는 Maven 플러그인**은 더 새로운 GA가 있으면 cozy-parent에서 덮어쓴다.
- **Boot BOM 밖의 라이브러리와 자산**은 각자의 최신 GA로 고정한다.

## 2026-11-06 기준 버전 (M4 시작 때 다시 확인)

| 구성 | 버전 | 관리 |
| --- | --- | --- |
| JDK | 25.0.3 (Temurin) | 빌드 PC |
| Maven | 3.10.0 (4.0.0은 RC) | 빌드 PC |
| Spring Boot | 4.1.2 (4.2.0은 RC2) | parent |
| Spring Framework / Security | 7.0.10 / 7.1.2 | Boot BOM |
| Thymeleaf | 3.1.5.RELEASE (thymeleaf-spring6) | Boot BOM |
| Jackson | 3.1.7 (Jackson 2는 2.21.7) | Boot BOM |
| Flyway | 12.4.0 | Boot BOM |
| HikariCP | 7.0.2 | Boot BOM |
| JUnit | 6.0.3 | Boot BOM |
| SnakeYAML | 2.6 | Boot BOM |
| Hibernate Validator (spring-boot-starter-validation) | Boot BOM | Boot BOM. cozy-ui, `@Valid` 규약 (M3, 승인 2026-11-06) |
| log4j-over-slf4j | 2.0.20 | Boot BOM. cozy-test, vnu의 log4j 1.x 대체 (ADR-0022) |
| Spring Modulith (api, core, events-core) | 2.1.2 (core는 ArchUnit 1.4.2를 가져온다) | cozy-parent |
| mybatis-spring-boot-starter | 4.1.0 | cozy-parent |
| sqlite-jdbc | 3.54.1.0 | cozy-parent |
| AttoParser | 2.0.10.RELEASE (Thymeleaf 기본은 2.0.7) | cozy-parent |
| Nu Html Checker | 26.11.4 | cozy-parent |
| maven-enforcer-plugin | 3.7.0 (Boot 기본 3.6.3) | cozy-parent |
| maven-invoker-plugin | 3.10.1 (Boot 기본 3.9.1) | cozy-parent |
| maven-surefire-plugin, maven-failsafe-plugin | 3.6.1 (Boot 기본 3.5.6) | cozy-parent |
| maven-dependency-plugin | 3.11.0 (Boot 기본 3.10.0) | cozy-parent |
| maven-archetype-plugin | 3.4.1 | cozy-parent |
| exec-maven-plugin | 3.6.4 | cozy-parent |
| license-maven-plugin | 2.7.1 | cozy-parent dist 프로필. 제3자 라이선스 목록 (ADR-0026, 승인 2026-11-06) |
| GitHub Actions: checkout 7.0.1, setup-java 6.2.0, upload-artifact 7.0.2, download-artifact 8.0.2 | 커밋 SHA로 고정 | 릴리스 워크플로 (ADR-0028, 제안) |
| Tabler | 1.7.3 | cozy-ui 벤더 자산 |
| htmx | 2.0.11 (4.x는 `next` 태그) | cozy-ui 벤더 자산 |
| Pretendard | 1.3.9 | cozy-ui 벤더 자산 |
| Tabler Icons (`@tabler/icons-sprite`) | 3.50.0 | cozy-ui 벤더 자산. 전체 세트 (ADR-0030, 승인 2026-11-06) |
| agent-browser | 0.41.2 | 개발 PC 도구. runtime-verifier의 화면 확인 (ADR-0030). 빌드·번들에 들어가지 않는다 |

## 근거

- 안정화 전에는 최신 버전에서 생기는 호환 문제를 일찍 발견하는 편이 낫다.
- Boot BOM이 관리하는 라이브러리를 따로 올리면 Boot가 검증한 조합이 깨진다.

## 결과

- Boot의 마이너 버전(4.2 등)이 GA가 되면 Boot와 그에 맞는 Modulith(2.2)를 함께 올린다. 이벤트 계약 테스트(PRD §8.5)가 인터페이스 변경을 잡아낸다.
- 버전을 올린 내용은 이 ADR의 표에 반영한다.
- 2026-11-06(M4): Maven 3.10.0이 2026-09-27에 GA로 나와 있었다(M0 조사에서 놓침). 빌드 PC를 3.10.0으로 올렸다. 3.10은 같은 의존성의 중복 선언을 오류로 본다. 금지 의존성에 `log4j:log4j`를 추가했다(ADR-0022).
- 2026-11-06(M7): 시작할 때 다시 확인했다. 바뀐 GA는 없다(Boot 4.1.2, Modulith 2.1.2, maven-archetype-plugin 3.4.1, Maven 3.10.0, sqlite-jdbc 3.54.1.0).
- 2026-11-06(릴리스 준비): 다시 확인했다. 바뀐 GA는 없다. 1.0.0부터는 최신 GA를 릴리스 준비 때 확인하고, 이 표가 그 릴리스의 고정 버전 세트가 된다(ADR-0028, 제안).
