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
| [0020](0020-sqlite-immediate-write-transactions.md) | 쓰기 트랜잭션은 BEGIN IMMEDIATE | PRD §13 |
| [0021](0021-ui-composition.md) | UI 구성 규칙: 레이아웃 조합, 예약 이름, 카탈로그, htmx 리다이렉트 | M3 |
| [0022](0022-rules-and-test-base.md) | 규칙 검사와 테스트 기반: 실패로 닫는 기준, vnu 구성, 검사 픽스처 | M4 |
| [0023](0023-events-runtime.md) | 이벤트 실행 구성: 인박스 위치, 실행기, 보관, 호환 검사 | M5 |
| [0024](0024-distribution-and-update.md) | 배포 장치: 패키징 도구, AOT 캐시, 교체 스크립트, 롤백 | M6 |
| [0025](0025-archetype-and-offline-bundle.md) | 아키타입과 오프라인 번들: 옵션 처리, 검증, 번들 구성, SQLite 네이티브 반입 | M7 |
| [0026](0026-third-party-licenses.md) | 제3자 라이선스 목록과 허용 목록 | PRD §11 |
