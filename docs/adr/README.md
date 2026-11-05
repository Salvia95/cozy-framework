# ADR 목록

0001~0015는 PRD §3의 결정(D1~D15)이다. 0016 이후는 구현하면서 생긴 결정이다. 결정을 바꿀 때는 구현 전에 해당 ADR을 개정한다.

| ADR | 제목 | 출처 |
| --- | --- | --- |
| [0001](0001-jdk25-jpackage.md) | JDK 25와 jpackage 런타임 번들 | D1 |
| [0002](0002-no-graalvm-native.md) | GraalVM 네이티브 이미지 미사용 | D2 |
| [0003](0003-maven-multimodule.md) | Maven 멀티모듈 | D3 |
| [0004](0004-mybatis-sqlite.md) | MyBatis와 SQLite, JPA 미사용 | D4 |
| [0005](0005-modulith-always.md) | Spring Modulith 상시 포함, 이벤트는 옵션 | D5 |
| [0006](0006-sqlite-event-publication.md) | 이벤트 발행 기록을 SQLite에 직접 구현 | D6 (+0018) |
| [0007](0007-inbox-idempotency.md) | 수신 측 인박스로 멱등성 보장 | D7 |
| [0008](0008-feature-packages.md) | 기능형 패키지 | D8 |
| [0009](0009-template-module-ownership.md) | 템플릿 디렉터리 = 모듈 소속 | D9 |
| [0010](0010-user-data-outside-app.md) | 사용자 데이터는 앱 폴더 밖 | D10 |
| [0011](0011-separate-flyway-history.md) | 프레임워크 테이블은 별도 Flyway 이력 | D11 (+0018) |
| [0012](0012-self-update.md) | v1부터 자체 업데이트 장치 포함 | D12 |
| [0013](0013-framework-as-dependency.md) | 프레임워크는 의존성으로 배포 | D13 |
| [0014](0014-ui-tabler-htmx.md) | UI는 Tabler, htmx, Pretendard와 Thymeleaf fragment | D14 |
| [0015](0015-csrf-spring-security.md) | CSRF는 Spring Security로 처리 | D15 |
| [0016](0016-repo-and-coordinates.md) | 레포 위치와 Maven 좌표 | 구현 전 확인 |
| [0017](0017-version-policy.md) | 버전 정책: 구현 중에는 최신 GA | 구현 전 확인 |
| [0018](0018-s4-verification.md) | §4 확인 항목 조사 결과 | PRD §4 |
| [0019](0019-build-verification.md) | 빌드 규칙의 검증 방식 | PRD §5, §6.1 |
