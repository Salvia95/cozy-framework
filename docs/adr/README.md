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
| [0008](0008-feature-packages.md) | 기능형 패키지 | D8 (+0029) |
| [0009](0009-template-module-ownership.md) | 템플릿 디렉터리 = 모듈 소속 | D9 (+0029) |
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
| [0025](0025-archetype-and-offline-bundle.md) | 아키타입과 오프라인 번들: 옵션 처리, 검증, 번들 구성, SQLite 네이티브 반입. 보완: Windows x64 번들에 JDK | M7 |
| [0026](0026-third-party-licenses.md) | 제3자 라이선스 목록과 허용 목록 | PRD §11 |
| [0027](0027-framework-license.md) | 프레임워크 라이선스: Apache-2.0 | 레포 공개 |
| [0028](0028-release.md) | 릴리스: GitHub Release의 오프라인 번들, 버전 고정, 번들 라이선스. 보완: 릴리스 자산과 JDK 소스 | 배포 방식 |
| [0029](0029-module-layers.md) | 모듈 안의 구조: api · spi · internal, 템플릿도 같은 3분할 (0008, 0009 개정) | 사용자 지시 |
| [0030](0030-ui-quality.md) | 화면 품질: Tabler 레이아웃 구조, Tabler Icons 전체, 화면 확인(runtime-verifier) | 사용자 지적 |
| [0031](0031-design-system.md) | 디자인 시스템: Tabler 구현 + Polaris 원칙 + Cloudscape CRUD 흐름, 색 tone, 버튼 순서(R14, R15) | 사용자 지시 |
| [0032](0032-agent-cli.md) | 에이전트 작업 분담: 구조는 cozy 명령이, 로직은 에이전트가. 빈칸 검사 C9, cozy-cli (0016 개정). 부록: [레시피 목록](0032-agent-cli-recipes.md) | 사용자 지시 |
| [0033](0033-stage-two-recipes.md) | 2단계 레시피: dto, query, command, event-version, enable events. 파서가 찾은 위치에 덧붙이기, 시그니처로 비교 (0032 §2 개정) | 사용자 지시 |
| [0034](0034-agent-skill.md) | 앱의 에이전트 문서: 가벼운 AGENTS.md와 cozy-helper 스킬(명령마다 PLAYBOOK.md), 영어·lean 작성 규칙 (0032 §7 개정) | 사용자 지시 |
| [0035](0035-sync-agents.md) | `cozy sync agents`: 이미 있는 앱의 AGENTS.md와 cozy-helper 스킬을 설치된 cozy 명령의 것으로 맞추기 (0034 §3에 더함) | 사용자 지시 |
| [0036](0036-workbench.md) | 워크벤치: 앱 안의 dev 도구 `/cozy/dev/workbench`, dev 전용 모듈 `cozy-workbench`, 엔진은 같은 cozy-cli (0016 개정) | 사용자 결정 |
| [0037](0037-add-field.md) | `cozy add field`: crud 한 벌에 필드 하나. 파서가 찾은 위치, 꼭 고칠 곳과 보이는 곳, `--default`, 호출하는 곳 고치기 (0033 정한 것 4·§1 개정) | 사용자 지시 |
| [0038](0038-workbench-redesign.md) | 워크벤치 디자인 개선: 용어와 표시 이름(KRDS 명칭 참고), 워드마크, 3단 구성, 모듈 의존성 다이어그램, 구조화된 입력, 매개변수 수정과 행 추가·삭제 (0031·0036 개정) | 사용자 결정 |
| [0039](0039-msi-installer.md) | 개발 도구 설치 파일(MSI): 사용자별 설치 `%LOCALAPPDATA%\Programs\Cozy`, 영구 사용자 PATH, 위치와 상관없는 Maven 설정, WiX v7, Windows 러너 검증 (0025·0028 개정) | 사용자 결정 |
