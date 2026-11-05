# 공개 API와 규약

공개 API는 만들 때마다 이 폴더에 문서화한다(PRD §14). 대상은 스타터 설정 속성, CozyApplication, CozyEvent, 테스트 베이스 클래스, 매니페스트와 스펙 형식이다.

| 문서 | 내용 | 마일스톤 |
| --- | --- | --- |
| [runtime.md](runtime.md) | CozyApplication, `cozy.app.*`, `cozy.data-dir`, `cozy.runtime.*`, 데이터 경로, 외부 설정, 비밀 값, 보안 최소선 | M1 |
| [windows-verification.md](windows-verification.md) | Windows에서 직접 확인하는 수용 기준과 기록 | M1~ |
| [sqlite.md](sqlite.md) | `cozy.sqlite.*`, 연결 설정, 트랜잭션 모드, 마이그레이션 규칙, TypeHandler, 예외 분류 | M2 |
| [ui.md](ui.md) | 화면 구성, 컨트롤러 규약, fragment 목록, 매니페스트 형식, 카탈로그, 벤더 자산 | M3 |
| (예정) rules.md, specs.md | R1~R8, 화면 스펙 형식, 테스트 베이스 클래스 | M4 |
| (예정) events.md | CozyEvent, 스키마 진화 규칙, 외부 호출 리스너 규칙 | M5 |
| (예정) update.md, performance.md | 업데이트 매니페스트, 성능 측정 | M6 |
