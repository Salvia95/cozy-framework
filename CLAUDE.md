# Cozy 프레임워크 레포 작업 지침

Cozy는 폐쇄망 Windows PC에서 도는 내부 도구를 위한 경량 프레임워크다. 스택은 Spring MVC, Thymeleaf, MyBatis, SQLite, Tabler, htmx다.

- 기준 문서: PRD "Cozy 프레임워크 뼈대 PRD" (https://claude.ai/artifact/AmuNQaeQB6XQfBksbdvka5)
- 결정 기록: `docs/adr/`

## 진행 방식 (PRD §14)

- M0~M14를 순서대로, 한 번에 한 마일스톤씩 진행한다. M8~M11은 ADR-0032(구조는 cozy 명령, 로직은 에이전트), M12~M14는 ADR-0033(2단계 레시피)이다. 그 뒤는 ADR마다 마일스톤을 잇는다(M15 ADR-0035, M17~M21 ADR-0036, M22~M23 ADR-0037, M24~M30 ADR-0038).
- 완료 기준을 먼저 테스트로 쓰고, 테스트가 통과한 뒤 다음으로 넘어간다.
- 마일스톤이 끝나면 커밋한다. 그리고 변경 요약, 통과한 완료 기준, 남은 위험을 보고한다.
- PRD §3의 결정(`docs/adr/0001`~`0015`)은 바꾸지 않는다. 바꿔야 한다면 ADR 개정안을 먼저 제안한다.
- 새 의존성이나 플러그인이 `docs/adr/0017-version-policy.md` 표에 없으면 추가하지 않는다. 필요하면 이유, 라이선스, 폐쇄망 반입 영향을 적어 제안한다.
- 검사를 통과시키려고 규칙을 완화하거나 테스트를 건너뛰지 않는다. 판단할 수 없는 것은 실패로 닫는다.
- 공개 API(설정 속성, CozyApplication, CozyEvent, 테스트 베이스 클래스, 매니페스트와 스펙 형식)를 만들면 그때마다 `docs/conventions/`에 문서화한다.
- 테스트에서 외부 URL을 호출하지 않는다.
- 화면에 보이는 것(cozy-ui 템플릿·cozy.css·cozy.js, 앱 템플릿)을 바꾸면 보고 전에 `runtime-verifier` 서브 에이전트로 실제 화면을 확인하고, 보고에 스크린샷을 붙인다(ADR-0030). 마크업은 Tabler 구조를 따른다.
- 화면은 `docs/conventions/design.md`의 원칙(흐름, 화면 유형과 폭, 색 tone과 대비, 문구)을 따른다(ADR-0031). 원칙을 바꾸려면 ADR 개정을 먼저 제안한다.
- 코드(소스, 주석, 출력 문자열, 테스트, 설정, 스크립트, 픽스처)에 ADR·PRD 참조와 마일스톤 표기(M1 등)를 쓰지 않는다. 코드는 그 자체로 맞는 설명이어야 한다. 결정, 근거, 진행 이력은 `docs/`에만 둔다.
- 생성 앱의 에이전트 문서(`docs/agents/`: `AGENTS.md`, `cozy-helper` 스킬과 플레이북)는 영어로 lean하게 쓴다. 절은 독립적으로, 불릿은 마침표로 끝나는 완결된 문장으로 쓴다. cozy 명령의 한국어 메시지와 예시 값만 코드(백틱) 안에 둔다(ADR-0034, `AgentDocsTest`가 확인한다).

## 버전 (ADR-0017)

- 구현하는 동안은 최신 GA를 쓴다. RC, milestone, `next` 태그는 제외한다.
- 마일스톤을 시작할 때 최신 GA를 확인한다.
- Boot BOM이 관리하는 라이브러리는 Boot 버전을 따른다.

## 좌표와 구조 (ADR-0016)

- groupId와 패키지는 `io.salvia95.cozy`다. 모든 모듈은 `cozy-parent`를 상속한다.
- 루트 `pom.xml`은 aggregator(`cozy-build`)다.
- 모듈 의존 방향: events → sqlite → runtime, ui → runtime, test → rules (나머지 cozy 모듈은 optional로만), cli → rules (+ SnakeYAML, ADR-0032), workbench → ui, cli (dev 전용, ADR-0036). rules는 JDK와 AttoParser에만 의존한다.
- 이 규칙은 각 모듈의 enforcer(`cozy-module-boundary`)와 `PomBoundaryTest`가 검사한다.
- 앱 모듈 안의 구조(api · spi · internal, Java와 템플릿)는 ADR-0029와 `docs/conventions/structure.md`를 따른다. reference-app과 아키타입 `home`이 기준 예시다.

## 명령

```bash
source ~/.sdkman/bin/sdkman-init.sh   # JDK 25, Maven 3.10.0
mvn verify                            # 전체 빌드 + invoker 픽스처 + 테스트
mvn -o verify                         # 오프라인 (의존성을 한 번 받은 뒤)
mvn -pl cozy-rules -am test           # 모듈 하나만
mvn -o package -pl cozy-cli && java -jar cozy-cli/target/cozy-cli-*.jar check --project samples/reference-app   # cozy 명령 (describe | check | recipes | add | link | enable | sync)
mvn -o verify -pl samples/recipes -am            # 레시피 픽스처: 기본 앱에 cozy add·link·enable → mvn verify (몇 분)
mvn -o -Pe2e install                  # + 배포·업데이트 종단 검증 (몇 분, M6)
mvn -o -Pdist package -pl samples/reference-app   # 앱 하나를 배포 패키지로 (target/dist)
mvn -o verify -pl cozy-archetype -am             # 아키타입: 옵션 3조합 생성 → verify -Pdist (몇 분)
distribution/offline-bundle/build-bundle.sh       # 오프라인 번들 (인터넷 필요, build/offline-bundle)
distribution/offline-bundle/add-jdk.sh build/offline-bundle/cozy-offline-bundle-<버전>.zip windows-x64   # + Temurin JDK (linux-x64는 검증용)
distribution/offline-bundle/verify-offline.sh build/offline-bundle/cozy-offline-bundle-<버전>.zip   # 네트워크 없이 번들 검증 (-linux-x64.zip이면 시스템 JDK를 막고)
distribution/offline-bundle/cozy help                # 번들 사용자 명령: cozy install | new | version | help
distribution/release/set-version.sh 1.0.0         # 모든 cozy 모듈 버전 바꾸기 (릴리스 절차: docs/conventions/release.md)
gh workflow run release.yml                       # CI에서 번들 빌드·검증만 (태그 v* push면 릴리스 초안까지)
```

## 환경

- 개발은 WSL2(Linux)에서 한다.
- Windows 전용 수용 기준은 사용자가 Windows에서 직접 검증한다. 대상은 `%LOCALAPPDATA%`, 트레이, app-image, 업데이트 교체 스크립트, 오프라인 번들이다. 검증 절차는 `docs/conventions/windows-verification.md`에, 스크립트는 `distribution/`에 둔다.
- 셸 스크립트는 bash로 작성한다. 대화형 셸은 zsh라서 따옴표 없는 변수가 단어 단위로 나뉘지 않는다.
