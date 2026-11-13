# ADR-0040 화면 상호작용: 서버를 거치면 htmx, 화면 안의 상태는 Alpine.js(CSP 빌드)

- 상태: 승인 (2026-11-13, 사용자 결정). "정할 것" 1~4는 권장안, 5와 6은 다른 안(기존 `data-cozy-*`를 이번에 없앤다, 워크벤치를 먼저 바꾼다)
- 출처: 사용자 지시 "워크벤치, cozy-ui, 생성 앱까지 넓힐꺼야 제외되고 있던 htmx, alpinejs를 모두 적용하는 방향으로 adr 개정부터 시작하자". 선택지는 정할 것 페이지(https://claude.ai/artifact/AzWbVuYD89VcZRuptc2duh)에 있다
- 바꾸는 결정:
  - ADR-0014(PRD §3 D14): UI 구성에 Alpine.js(MIT)를 더한다. Tabler, htmx, Pretendard, Thymeleaf fragment는 그대로다.
  - ADR-0017 버전 표: `@alpinejs/csp` 3.17.5를 더한다.
  - ADR-0026: 제3자 라이선스 목록의 웹 자산이 4개에서 5개가 된다. 허용 목록은 그대로다(MIT).
  - ADR-0036 §1 "새 라이브러리를 들이지 않는다": 워크벤치도 htmx와 Alpine으로 만든다(§6).
  - `docs/conventions/ui.md`의 공개 속성 `data-cozy-confirm`: 없애고 `cozy` 컴포넌트로 바꾼다. 호환이 깨지므로 이 결정이 들어간 첫 릴리스는 2.0.0이다(§6).
- 바꾸지 않는 것: CSP(`script-src 'self'`, eval 금지), htmx 설정(`allowEval`·`allowScriptTags` 끔), ADR-0021의 htmx 리다이렉트, ADR-0022의 허용 목록 방식, ADR-0032의 분담(구조는 cozy 명령, 로직은 에이전트)

## 배경

- **기록에는 처음부터 Alpine이 없었다.** PRD의 스택 표(처음 작성본 포함), ADR-0014, ADR-0017 표, ADR-0036, git 이력 76개 커밋 어디에도 없다. 중간에 빠진 것이 아니다.
- **htmx는 화면마다 쓰는 정도가 다르다.** cozy-ui 패턴과 생성 앱(reference-app, 레시피, 아키타입)은 이미 `hx-*`로 부분 갱신을 한다. 워크벤치 템플릿에는 `hx-*`가 하나도 없다. ADR-0036 §1은 htmx를 쓴다고 정했지만, 서버를 거치는 동작은 모두 전체 페이지를 다시 그리는 GET·POST 폼이다. ADR-0038 M30은 "htmx 갱신"을 범위 밖으로 미뤘다.
- **화면 안의 상태를 다루는 선언적 수단이 없다.** 동작마다 `data-cozy-*` 표시와 손으로 쓴 JS가 한 쌍이다. `cozy.js` 152줄(확인 창, 아이콘 거르기, 오류 칸 포커스, 떠날 때 확인), `workbench.js` 632줄(칸 접기, 상태 비교 창, 모듈별 선택지, 아이콘 미리보기, 요소 선택)이다. 템플릿만 봐서는 어떤 동작이 붙는지 알 수 없다. 에이전트와 cozy 명령은 동작을 만들 수 없고 JS를 새로 써야 한다.
- **CSP 때문에 Alpine 표준 빌드는 동작하지 않는다.** 표준 빌드는 식을 `Function`으로 만들어 `unsafe-eval`이 필요하다. CSP 빌드 `@alpinejs/csp`는 자체 파서로 식을 해석해서 eval 없이 동작한다. 2026-11-13에 npm에서 확인한 최신 GA는 `alpinejs`, `@alpinejs/csp` 모두 3.17.5(2026-10-20, MIT)다. htmx는 2.0.11이 최신 GA 그대로다(4.x는 `next`).
- **CSP 빌드는 식의 일부만 지원한다.** 객체·배열 리터럴, 연산, 비교, 삼항, 변수 대입(`count = 0`, `count++`), 메서드 호출은 된다. 속성 대입(`user.name = 'x'`), 화살표 함수, 템플릿 문자열, 구조 분해, 전개, 전역(`console`, `document`, `Math`, `JSON`), `x-html`은 안 된다(Alpine 문서 `advanced/csp`, v3.17.5).
- **vnu는 `x-*` 속성을 오류로 본다.** `hx-*`와 같은 문제이고, ADR-0022가 허용 목록으로 풀었다.

## 결정

### 1. 역할 분담

| 도구 | 맡는 것 | 예 |
| --- | --- | --- |
| htmx | 서버를 거치는 모든 것. 서버가 돌려준 fragment가 기준이다 | 조회, 필터, 페이지 이동, 저장, 검증 오류 폼, 삭제 |
| Alpine.js | 서버를 거치지 않는 화면 안의 상태 | 펼치기·접기, 탭, 선택 수에 따른 버튼 활성, 입력 미리보기, 복사, 떠날 때 확인 |
| Tabler(Bootstrap) JS | 이미 있는 컴포넌트 | 모달, 드롭다운, 툴팁. Alpine으로 다시 만들지 않는다 |

- 판단 기준은 하나다. **새로고침해도 남아야 하는 값이면 서버(htmx)이고, 아니면 Alpine이다.**
- Alpine 컴포넌트는 서버에 요청하지 않는다(`fetch`, `XMLHttpRequest`, `htmx.ajax`를 쓰지 않는다). 서버가 필요하면 이벤트를 보내고(`$dispatch`), htmx가 `hx-trigger`로 받는다.
- 서버 응답이 Alpine 상태를 바꿔야 하면 `HX-Trigger` 헤더의 이벤트로 알린다. 토스트(`cozy:toast`)와 같은 방식이다.
- 스크립트가 없어도 화면의 기본 동작(보기, 저장)은 된다. htmx와 Alpine은 그 위에 얹는다.

### 2. 반입과 로드

- 벤더 자산 `cozy/vendor/alpinejs-csp/3.17.5/`에 `cdn.min.js`와 `LICENSE`를 둔다. `distribution/vendor-assets/fetch.sh`가 받고 `vendor.lock`의 SHA-256으로 검증한다(ADR-0014 결과와 같다).
- 표준 빌드(`alpinejs`)는 반입하지 않는다. CSP에 `unsafe-eval`을 넣지 않는다.
- **폐쇄망 반입 영향**: 자산은 cozy-ui jar 안에 들어간다. 오프라인 번들과 MSI는 jar를 통해 함께 들어가므로 따로 할 일이 없다. npm과 프론트 빌드 도구는 여전히 쓰지 않는다.
- `base` 레이아웃은 Tabler → htmx → `cozy.js` → Alpine 순서로 `defer`로 싣는다. `cozy.js`가 `alpine:init`에서 공용 컴포넌트를 등록하고, Alpine이 마지막에 시작한다.
- htmx가 바꾼 영역은 Alpine이 새 요소로 초기화한다. 바뀐 영역의 Alpine 상태는 사라지고, 서버가 다시 그린 상태가 기준이다. `historyCacheSize: 0`은 그대로다.

### 3. 템플릿 작성 규칙

- **속성은 긴 이름만 쓴다.** `x-on:click`, `x-bind:class`처럼 쓰고, 줄임 표기(`@click`, `:class`)는 쓰지 않는다. `th:`과 헷갈리지 않고, 검색과 검사가 한 가지 모양만 보면 된다.
- **`x-data`에는 등록된 컴포넌트 이름만 쓴다.** `x-data="cozyDisclosure"`처럼 쓰고, 인라인 객체(`x-data="{ open: false }"`)는 쓰지 않는다. 상태와 로직이 JS 한 곳에 있고, 규칙이 이름을 검사할 수 있다.
- **서버 값은 `data-*`로 넘긴다.** 컴포넌트가 `this.$el.dataset`에서 읽는다. Alpine 식 안에 Thymeleaf 식을 넣지 않는다(`th:attr`나 `th:x-*`로 `x-*` 값을 만들지 않는다). 사용자 입력이 식으로 해석되는 길을 막고, 식을 정적으로 검사할 수 있게 한다.
- **쓰는 디렉티브**: `x-data`, `x-init`, `x-show`, `x-bind:*`, `x-on:*`, `x-model`, `x-text`, `x-ref`, `x-cloak`, `x-transition`, `x-if`, `x-for`, `x-id`, `x-effect`, `x-ignore`.
- **쓰지 않는 디렉티브**: `x-html`(HTML 주입, CSP 빌드도 지원하지 않는다), `x-teleport`(요소를 fragment 밖으로 옮겨 htmx 대상과 템플릿 위치가 어긋난다).
- **식은 짧게 쓴다.** 속성 읽기, 메서드 호출, 변수 대입, 비교만 쓴다. 조건이 두 개를 넘거나 목록을 거르는 로직은 컴포넌트의 getter와 메서드로 옮긴다.

### 4. 검사

- **R16 (템플릿 규칙, cozy-rules)**: 아래를 오류로 본다. 판단할 수 없는 것은 실패로 닫는다.
  - 줄임 표기 `@…`, `:…` 속성
  - 쓰지 않는 디렉티브와 목록에 없는 `x-*`
  - `x-data` 값이 등록된 컴포넌트 이름이 아님. 앱 템플릿은 `cozy` 컴포넌트와 자기 모듈의 컴포넌트만 쓴다(§5).
  - Thymeleaf로 만든 `x-*` 값(`th:attr`, `th:x-*`)
  - CSP 빌드가 해석하지 못하는 식: `=>`, 백틱, 전개(`...`), 전역 이름(`window`, `document`, `console`, `Math`, `JSON`, `fetch`)
  - 없앤 동작 속성(`data-cozy-confirm` 등, §6). 고치는 명령으로 `cozy migrate alpine`을 보인다.
- **등록된 이름은 JS 파일에서 찾는다.** `Alpine.data('이름', …)`의 글자 이름을 읽는다. 이름이 글자가 아니면 오류다. rules는 JDK와 AttoParser 의존을 그대로 지킨다.
- **C4 (vnu)**: `CozyHtmlValidator`의 허용 목록에 §3의 디렉티브를 더한다. `x-on:`, `x-bind:`, `x-transition`은 접두로 맞춘다. 목록 밖의 `x-*`는 그대로 오류다.
- `cozy check`와 워크벤치 검사 화면이 R16을 같은 문구로 보인다.

### 5. 컴포넌트의 위치와 생성

- **프레임워크 컴포넌트**는 cozy-ui의 `cozy.js`가 등록한다. 이름은 `cozy`로 시작한다(`cozyDisclosure`, `cozyCopy`, `cozyDirtyForm`). 목록, 받는 `data-*`, 보내는 이벤트는 `docs/conventions/ui.md`에 적는다.
- **앱 컴포넌트**는 모듈마다 `src/main/resources/static/{모듈}/{모듈}.js`에 둔다. 이름은 모듈 이름으로 시작한다(`memoEditor`). 템플릿과 같이 모듈 소속이다(ADR-0009, ADR-0029).
- `base` 레이아웃은 앱의 모듈 JS를 런타임이 알려 주는 목록으로 싣는다. 템플릿에 경로를 직접 적지 않는다.
- **cozy 명령이 뼈대를 만든다(ADR-0032).** 새 레시피 `cozy add behavior {모듈} {이름}`이 컴포넌트 등록 코드와 빈칸(`cozy:slot`, C9)을 만들고, 에이전트가 로직을 채운다. 화면 레시피(`add screen`, `add crud`)는 필요한 곳에 `cozy` 컴포넌트를 붙인 템플릿을 만든다.
- `cozy describe`의 화면 구조에 화면이 쓰는 Alpine 컴포넌트를 더한다. 워크벤치와 에이전트가 같은 정보를 받는다(ADR-0036 §3).

### 6. 적용 범위

- **워크벤치를 먼저 바꾼다.** 처음 문제가 드러난 곳이기 때문이다.
  - 서버를 거치는 동작(상태 바꾸기, 변경 파일 확인과 적용, 샘플 데이터의 행 추가·삭제, 매개변수 수정, 검색)을 htmx 부분 갱신으로 바꾼다.
  - 화면 안의 동작(칸 접기, 상태 비교 창, 모듈별 선택지, 아이콘 미리보기, 축소 미리보기, 복사)을 Alpine 컴포넌트로 바꾼다.
  - **앱 화면에도 쓸 동작은 처음부터 cozy-ui의 `cozy` 컴포넌트로 만든다**(칸 접기 → `cozyDisclosure`, 복사 → `cozyCopy`). 워크벤치는 `ui`에 의존하므로 가져다 쓸 수 있다. 워크벤치에만 있는 동작(상태 비교 창, 요소 선택)만 워크벤치 컴포넌트(`workbench…`)다. 워크벤치용으로 만든 것을 나중에 cozy-ui로 옮기는 일을 줄이기 위해서다.
  - 요소 선택(iframe 안의 위치 계산)은 로직이 크므로 컴포넌트 하나(`workbenchPicker`)의 메서드로 옮긴다.
- **cozy-ui**: `cozy.js`의 동작 가운데 화면 안의 상태(확인 창, 아이콘 거르기와 복사, 떠날 때 확인, 미리보기 높이)를 `cozy` 컴포넌트로 옮긴다. htmx 전역 설정(CSRF, 오류 토스트, 진행 막대, 오류 칸 포커스)은 `cozy.js`에 그대로 둔다. 패턴과 컴포넌트는 필요한 곳에 Alpine을 쓴다.
- **동작을 붙이던 `data-cozy-*` 속성은 이번에 없앤다.** 방식을 하나만 남긴다.
  - 앱이 쓰도록 공개한 것은 `data-cozy-confirm`(폼 제출 전 확인)이다. `data-cozy-autosize`, `data-cozy-icon-filter`, 워크벤치의 동작 속성은 프레임워크 템플릿만 쓰므로 함께 바꾼다.
  - 컴포넌트가 읽는 데이터 속성(워크벤치의 `data-cozy-src`, `data-cozy-line` 등)은 동작을 붙이지 않으므로 남긴다. §3의 "서버 값은 `data-*`로 넘긴다"에 해당한다.
  - **새 명령 `cozy migrate alpine`**이 앱 템플릿의 없앤 속성을 `cozy` 컴포넌트 모양으로 바꾼다. `add`처럼 계획(`--dry-run --json`)을 보이고 원자적으로 적용한다(ADR-0032). 두 번 실행해도 결과가 같다.
  - `cozy check`(R16)가 남은 속성을 오류로 보이고, 고치는 명령으로 `cozy migrate alpine`을 보인다.
  - 호환이 깨지므로 이 결정이 들어간 첫 릴리스는 2.0.0이다. 릴리스 노트에 `cozy migrate alpine`을 적는다.
- **생성 앱**: 아키타입의 `home`, 화면 레시피의 템플릿, reference-app이 §1~§3을 따른다. 앱의 에이전트 문서(`AGENTS.md`, `cozy-helper` 플레이북)에 htmx와 Alpine의 분담과 작성 규칙을 영어로 적는다(ADR-0034).

### 7. 문서

- `docs/conventions/ui.md`: 분담, 작성 규칙, `cozy` 컴포넌트 목록
- `docs/conventions/rules.md`: R16
- `docs/conventions/testing.md`: C4 허용 목록
- `docs/conventions/structure.md`: `static/{모듈}/`
- `docs/conventions/cli.md`: `cozy add behavior`, `cozy migrate alpine`, `describe`의 컴포넌트
- `docs/conventions/release.md`: 2.0.0의 옮기는 절차
- PRD §3 D14 행과 기술 스택 표: 승인 뒤 고친다.

## 정한 것

1. CSP를 그대로 두고 CSP 빌드 `@alpinejs/csp`를 쓴다.
2. `x-data`에는 등록된 컴포넌트 이름만 쓴다.
3. 속성은 긴 이름(`x-on:`, `x-bind:`)만 쓴다.
4. 앱 컴포넌트는 `static/{모듈}/{모듈}.js`에 두고, 이름은 모듈 이름으로 시작한다. `cozy add behavior`가 뼈대를 만든다.
5. 동작을 붙이던 `data-cozy-*` 속성은 이번에 없애고, `cozy migrate alpine`이 앱 템플릿을 옮긴다. 첫 릴리스는 2.0.0이다.
6. 기반 → 워크벤치 → cozy-ui → 앱·명령 순서로 진행한다.

## 단계

| 마일스톤 | 내용 | 완료 기준 |
| --- | --- | --- |
| M31 기반 | 벤더 자산 반입(fetch.sh, vendor.lock, 라이선스 목록, NOTICE), `base` 로드 순서, 첫 `cozy` 컴포넌트(`cozyCopy`, reference-app 메모 상세의 본문 복사), C4 허용 목록, R16(없앤 속성 항목은 M34), ui.md·rules.md·testing.md | reference-app의 Alpine 동작이 CSP 위반 없이 된다(runtime-verifier, 콘솔 오류 0). R16의 항목마다 위반 픽스처가 빌드를 실패시킨다. `x-on:click`이 C4를 통과하고 목록 밖의 `x-*`는 실패한다 |
| M32 워크벤치 htmx | 서버를 거치는 동작을 htmx 부분 갱신으로 | 스크립트를 끄면 지금처럼 전체 페이지로 동작한다. 켜면 바뀐 영역만 다시 그린다. 결과 파일은 터미널의 같은 명령과 바이트 단위로 같다(ADR-0036 M20 기준 유지) |
| M33 워크벤치 Alpine | `workbench.js`의 동작을 컴포넌트로. 앱에도 쓸 동작은 `cozy` 컴포넌트로 cozy-ui에(칸 접기 → `cozyDisclosure`) | `workbench.js`에 `querySelectorAll(…).forEach(… addEventListener …)` 방식의 직접 등록이 남지 않는다. 모든 워크벤치 화면이 옮기기 전과 같게 동작한다(runtime-verifier). 워크벤치 컴포넌트에 앱 화면에도 쓰는 동작이 없다 |
| M34 cozy-ui | `cozy.js`의 화면 안 동작을 `cozy` 컴포넌트로. 패턴·컴포넌트 적용. 동작 속성 `data-cozy-*` 제거, `cozy migrate alpine`, R16의 없앤 속성 항목 | 카탈로그의 모든 항목과 reference-app의 모든 상태가 C3·C4를 통과한다. 옮기기 전과 같은 동작을 runtime-verifier로 확인한다. 옛 속성을 쓰는 픽스처 앱이 `cozy check`에서 실패하고, `cozy migrate alpine` 뒤 `mvn verify`를 통과한다. 두 번째 실행은 바꾸는 파일이 없다 |
| M35 앱 컴포넌트와 명령 | `static/{모듈}/`, 레이아웃의 모듈 JS 로드, `cozy add behavior`, `describe`의 컴포넌트, 화면 레시피와 아키타입, 에이전트 문서, 2.0.0 옮기는 절차 | 레시피 픽스처와 아키타입 3조합이 `mvn verify`를 통과한다. `add behavior` 결과는 빈칸이 남아 C9가 실패하고, 채우면 통과한다. 다른 모듈의 컴포넌트를 쓰면 R16이 실패한다 |

- 화면이 바뀌는 마일스톤마다 runtime-verifier로 확인한다(ADR-0030).
- 마일스톤을 시작할 때 Alpine과 htmx의 최신 GA를 다시 확인한다(ADR-0017).

## 위험

- **vnu와 Thymeleaf가 `x-on:click`의 콜론을 어떻게 다루는지 확인하지 않았다.** vnu가 허용 목록으로 거를 수 없는 다른 문구(XML 직렬화 경고 등)를 낼 수 있다. Thymeleaf는 `x-on`을 방언 접두로 보지 않을 것으로 예상하지만 M31에서 먼저 확인한다. 안 되면 이 결정을 다시 연다.
- **CSP 빌드가 해석하지 못하는 식은 실행할 때 콘솔 오류로만 드러난다.** R16은 알려진 모양만 막는다. `mvn verify`에는 브라우저가 없으므로 나머지는 runtime-verifier가 잡는다.
- **JS 단위 테스트 도구가 없다.** npm과 테스트 러너를 들이지 않으므로 컴포넌트 로직은 브라우저에서만 확인된다. 컴포넌트를 작게 유지하고 로직은 서버 쪽에 둔다.
- **htmx가 영역을 바꾸면 그 안의 Alpine 상태가 사라진다.** 남아야 하는 값은 서버 상태여야 한다(§1의 판단 기준). 바꾸는 영역을 좁게 잡는다.
- **2.0.0으로 올리는 앱은 템플릿을 옮겨야 한다.** `cozy migrate alpine`이 공개 속성을 옮기지만, 앱이 문서에 없는 프레임워크 속성(`data-cozy-autosize` 등)을 직접 썼다면 명령이 모양을 알 수 없다. 그런 속성도 R16이 오류로 보이고, 고치는 법은 글로 안내한다.
- **워크벤치를 먼저 바꾸므로 `cozy` 컴포넌트의 모양이 워크벤치에 맞춰질 수 있다.** M34에서 앱 화면에 적용하면서 컴포넌트를 바꾸게 되면 워크벤치도 함께 고친다. 같은 레포, 같은 빌드에서 테스트가 잡는다.
- **에이전트가 Alpine 표준 문법(인라인 객체, 줄임 표기)을 쓰기 쉽다.** 학습 데이터 대부분이 그 모양이다. R16이 고치는 법을 함께 보이고, 플레이북에 예시를 둔다. 작은 모델이 잘 따르는지는 사용자의 내부 평가로 확인한다.

## M31 (2026-11-13, 사용자 지시 "바로 진행해줘")

- **콜론 속성 확인**: Thymeleaf 3.1.5는 `x-on:click`, `x-transition:enter.duration.200ms`, `@click`, `:class`를 바꾸지 않고 그대로 출력한다. vnu 26.11.4는 모두 `Attribute “…” not allowed on element “…” at this point.` 하나로 보고한다. 그래서 htmx와 같은 허용 목록으로 거른다. 위험 항목의 첫째가 풀렸다.
- **목록 하나**: R16과 C4가 같은 디렉티브 목록(`AlpineAttributes`)을 쓴다. cozy-test는 이미 cozy-rules에 의존하므로 의존 방향은 그대로다.
- **컴포넌트 이름의 출처**: 프레임워크는 `META-INF/resources/cozy/*.js`(벤더 제외), 앱은 `static/**/*.js`의 `Alpine.data('이름', …)`이다. `RulesInput`에 `scripts`를 더했다. 앱 컴포넌트의 위치와 이름 규칙(§5)도 지금 검사한다. layout이 앱 JS를 싣는 것은 M35다.
- **`cozyDisclosure`는 M33으로 옮겼다.** reference-app에 펼치기를 쓸 자연스러운 자리가 없다. 쓰는 화면 없이 만들면 브라우저에서 확인할 수 없어서, 처음 쓰는 워크벤치 칸 접기와 함께 만든다. M31의 첫 컴포넌트는 메모 상세의 본문 복사에 쓰는 `cozyCopy`다.
- **`cozyCopy`는 `data-copy-text`를 읽는다.** 아이콘 카탈로그의 `[data-copy]` 클릭 처리(`cozy.js`)가 문서 전체에서 동작하므로 이름이 겹치지 않게 했다. 카탈로그는 M34에서 옮긴다.
- **Tabler 1.7.5**: 시작할 때 확인한 최신 GA다(ADR-0017).

