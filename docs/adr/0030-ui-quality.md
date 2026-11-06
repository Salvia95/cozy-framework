# ADR-0030 화면 품질: Tabler 레이아웃 구조, Tabler Icons 전체, 화면 확인(runtime-verifier)

- 상태: 승인, 일부 개정 (ADR-0031이 `detailEdit`을 없애고 `details` + 수정 화면으로 나눴다. `badge`·`stat`·`pageHeader`·`formPage` 시그니처도 바뀌었다)
- 결정: 2026-11-06 (사용자 결정: 아이콘 "전체 세트 추가", 범위 "전체 개편", 확인 방식 "runtime-verifier 서브 에이전트 + agent-browser")
- 출처: 사용자 지적 "Tabler가 적용된 거 맞아? 컴포넌트가 불완전해 보이고 품질이 떨어진다". ADR-0014(Tabler·htmx), ADR-0017(버전), ADR-0021(UI 구성)을 잇는다.

## 배경

- **CSS는 정상이었다**: 같은 Tabler 1.7.3 CSS로 Tabler 구조의 마크업을 그리면 Tabler답게 보였다(비교 목업으로 확인).
- **마크업과 아이콘이 원인이었다**:
  - 아이콘 세트가 없어 내비·버튼·알림·빈 상태가 글자뿐이었다.
  - 화면 틀이 Tabler 구조(`page-header` + `page-body`, `.page-wrapper` 안의 푸터)와 달랐다. 그래서 푸터가 고정 사이드바에 가렸다.
  - 대시보드 숫자 카드가 다른 카드 안에 여백 없이 들어가 있었다.
- **눈으로 확인하는 절차가 없었다**: 검사(C3 렌더링, C4 vnu)는 HTML이 맞는지만 본다. 화면을 눈으로 확인하는 절차가 없었다.

## 결정

### 1. Tabler Icons 전체를 벤더 자산으로 넣는다

- **자산**: `@tabler/icons-sprite` 3.50.0(MIT)의 스프라이트 두 개를 넣는다.
  - 외곽선 5,203개: `tabler-sprite.svg`, 2.2MB
  - 채운 아이콘 1,054개: `tabler-sprite-filled.svg`, 0.7MB
  - 위치: `/cozy/vendor/tabler-icons/3.50.0/`. `fetch.sh`가 npm sha512로 검증하고 `vendor.lock`에 SHA-256을 남긴다.
- **방식**: 같은 출처의 `<svg><use href="…sprite.svg#tabler-이름">`. CSP는 그대로 둔다. 브라우저는 스프라이트를 한 번 받아 캐시한다.
- **템플릿 계층**: 새 계층 `icon`을 둔다. 순서는 layout → pattern → component → icon이다.
  - 사용법: `~{cozy/icon/tabler :: icon('plus')}`, `iconFilled('star')`
  - 컴포넌트가 아이콘을 쓰려면 아이콘이 컴포넌트보다 아래 계층이어야 한다(R4).
- **이름 확인 (실패로 닫는다)**:
  - 템플릿에 글자로 쓴 이름은 R13이 확인한다. 오타면 편집 거리로 비슷한 이름을 제안한다.
  - Java에서 넘기는 이름은 `CozyIcons.requireOutline`이 확인한다. `CozyNavItem`의 아이콘이 여기에 해당한다.
- **오프라인에서 찾기**: `/cozy/dev/catalog/icons`가 전체 이름을 검색하고, 누르면 이름을 복사한다.

### 2. 화면 틀을 Tabler 구조로 바꾼다

- **appShell**:
  - Tabler 세로 내비. 접힘 버튼, 아이콘 + 이름 항목, 아래쪽 앱 메뉴(진단 내보내기·종료)가 있다.
  - `.page-wrapper` 안에 `<main>`과 푸터를 둔다.
- **패턴의 루트**: `th:block`이다. `page-header`(제목 + 동작 버튼) → `page-body`(플래시 + 본문)를 그린다.
- **플래시**: appShell이 아니라 패턴이 `page-body` 맨 위에 그린다(`component/alert :: flash()`).
- **컴포넌트를 Tabler 전체 마크업으로 맞춘다**:
  - alert: 종류별 아이콘
  - empty-state: 아이콘, 버튼이 붙는 `emptyStateAction`
  - pagination: 번호 5개와 화살표
  - field: `switch` 추가
  - 새 컴포넌트: `stat`(숫자 카드), `page-header`
  - 새 패턴: `overview`(대시보드)
- **바뀐 시그니처**:
  - `detailEdit(제목, 버튼, 상세, 폼)`: 동작 버튼은 머리 오른쪽에 둔다.
  - `emptyState(아이콘, 제목, 설명)`
  - `CozyNavItem(이름, 경로, 순서, 아이콘)`

### 3. fragment 이름은 요소 이름과 겹치지 않는다 (R12)

- **원인**: Thymeleaf는 `~{t :: form}`을 고를 때 `th:fragment="form"`과 `<form>` 요소를 모두 가져온다.
- **실제 결함**: 메모 상세에서 저장이 검증 오류로 돌아오면, 응답에 삭제 폼까지 들어가 수정 칸에 "삭제" 버튼이 하나 더 생겼다.
- **규칙**: 같은 파일에 같은 이름의 요소가 있으면 R12로 실패한다. 지금은 해롭지 않은 경우(그 요소가 fragment 자신뿐인 경우)도 실패로 닫는다. 나중에 같은 요소가 하나 더 생기면 바로 깨지기 때문이다.

### 4. 화면을 바꾸면 runtime-verifier로 확인한다

- **runtime-verifier**: 서브 에이전트 `.claude/agents/runtime-verifier.md`. nklcbdt의 runtime-verifier를 따랐다(영문, lean).
  - 실제 앱을 띄우고 agent-browser(Vercel, 개발 PC 도구)로 사용자 흐름을 따라간다.
  - 화면 × 상태 × 화면 폭마다 스크린샷을 찍는다.
  - DOM 측정값을 근거로 PASS / FAIL / UNSURE를 낸다.
  - 저장소는 읽기만 한다.
- **언제 쓰는가**: cozy-ui, 앱 템플릿, cozy.css·cozy.js를 바꾼 작업은 마일스톤 보고 전에 이 확인을 거친다. 보고에는 스크린샷을 붙인다.
- **빌드와의 관계**: agent-browser와 Node는 Maven 빌드와 오프라인 번들에 들어가지 않는다. 개발 PC에만 필요하다.

## 근거

- **아이콘을 고른 이유**: 아이콘이 없는 것이 품질 차이의 가장 큰 원인이었다. Tabler와 같은 프로젝트의 MIT 아이콘이라 모양이 맞다.
- **전체 세트를 넣은 이유**: 사용자 결정이다. 앱이 아이콘을 추가할 때 다시 반입할 필요가 없다. 대신 jar와 app-image가 약 2.9MB 커진다.
- **스프라이트를 고른 이유**: 웹폰트보다 접근성과 렌더링이 안정적이다. CSP를 바꾸지 않고, 같은 파일을 한 번만 받는다.

## 결과

- **ADR-0017 표**: Tabler Icons 3.50.0과 agent-browser 0.41.2(개발 PC 도구)를 추가한다.
- **cozy-rules**: R1·R4에 `icon` 계층을 넣는다. R12와 R13을 새로 둔다. `RulesInput`이 아이콘 이름을 받는다(`CozyProject.icons()`).
- **이행**: reference-app, 아키타입 home, 위반 픽스처(R12, R13 추가)를 새 구조로 옮긴다.
- **문서**: ui.md, rules.md, AGENTS.md, CLAUDE.md
