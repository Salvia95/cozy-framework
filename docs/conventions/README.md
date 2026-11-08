# 공개 API와 규약

공개 API는 만들 때마다 이 폴더에 문서화한다(PRD §14). 대상은 스타터 설정 속성, CozyApplication, CozyEvent, 테스트 베이스 클래스, 매니페스트와 스펙 형식, cozy 명령이다.

| 문서 | 내용 | 마일스톤 |
| --- | --- | --- |
| [runtime.md](runtime.md) | CozyApplication, `cozy.app.*`, `cozy.data-dir`, `cozy.runtime.*`, 데이터 경로, 외부 설정, 비밀 값, 보안 최소선, 종료(트레이·화면 메뉴), 진단 내보내기, CozyDiagnosticsContributor | M1, M6 |
| [windows-verification.md](windows-verification.md) | Windows에서 직접 확인하는 수용 기준과 기록 | M1~ |
| [sqlite.md](sqlite.md) | `cozy.sqlite.*`, 연결 설정, 트랜잭션 모드, 마이그레이션 규칙, TypeHandler, 예외 분류 | M2 |
| [ui.md](ui.md) | 화면 구성(Tabler 세로 레이아웃), 컨트롤러 규약, fragment 목록, 아이콘(CozyIcons), 매니페스트 형식, 카탈로그, 벤더 자산 | M3, ADR-0030 |
| [design.md](design.md) | 디자인 원칙: 흐름(왼쪽 위 → 오른쪽 아래), 화면 유형과 폭, 만들기·상세·수정·삭제, 색 tone과 대비, 글자·간격, 문구, 피드백 | ADR-0031 |
| [structure.md](structure.md) | 모듈 구조: Java 패키지와 템플릿의 api · spi · internal, 레이어 방향(C8), fragment 조합 | ADR-0029 |
| [rules.md](rules.md) | 템플릿 경계·디자인 규칙 R1~R15, 실패 메시지, `cozy.rules.*`, cozy-rules API(자리 표 `Role`·`Layer`, C8·C9 판단 포함) | M4, M8 |
| [testing.md](testing.md) | CozyArchitectureTest, CozyRenderingTest, 검사 C1~C9, 빈칸 표시(`cozy:slot`), 화면 스펙 형식, 위반 픽스처 | M4, M8 |
| [cli.md](cli.md) | cozy 명령: `describe`, `check`, `recipes`, `add`·`link`(레시피), 옵션, 종료 코드, `--json` 형식, 고치는 명령, 번들에서 실행, 판단할 수 없는 것 | M9, M10, M11 |
| [events.md](events.md) | CozyEvent, 전달 보장, 인박스, 스키마 진화 규칙과 샘플, 외부 호출 리스너 규칙, `cozy.events.*` | M5 |
| [distribution.md](distribution.md) | `mvn -Pdist package`, `cozy.dist.*`, AOT 캐시, SQLite 네이티브 라이브러리, 설치 위치, `cozy.update.*`, 매니페스트 형식, 업데이트·롤백 흐름, 종단 검증 | M6, M7 |
| [getting-started.md](getting-started.md) | 새 앱 만들기: 아키타입 옵션(`appId`, `appName`, `persistence`, `events`), 생성 구조, 오프라인 번들 | M7 |
| [../AGENTS.md](../AGENTS.md) | 생성 앱에 들어가는 코딩 에이전트용 규칙: 일하는 순서, cozy 명령 목록(`cozy recipes`와 같다), 빈칸, 파일 위치, 금지, 검증 | M7, M11 |
| [performance.md](performance.md) | 비기능 목표(PRD §11) 측정: 기동 시간(AOT 유무), 힙, app-image 크기, 오프라인 빌드 | M7 |
| [release.md](release.md) | 프레임워크 릴리스 절차, 버전 바꾸기, 릴리스 워크플로, 번들 라이선스 목록과 예외 목록 형식 | 릴리스 |
