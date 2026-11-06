# 템플릿 경계 규칙 (cozy-rules)

템플릿이 어느 폴더에 있는지가 곧 그 템플릿의 소속이다(PRD §7, ADR-0029). 모듈 템플릿은 Java 패키지와 같이 `api/`(화면), `spi/`(다른 모듈용 fragment), `internal/`(조각)로 나눈다. 배치 예시는 [structure.md](structure.md)에 있다. 허용 의존은 Java 모듈의 `@ApplicationModule(allowedDependencies = ...)`에서만 읽고, `resources`에는 경계 정보를 두지 않는다. `CozyArchitectureTest`(검사 C2)가 빌드 때마다 이 규칙을 검사한다.

## 폴더와 참조 가능한 대상

| 경로 | 소유 | 참조할 수 있는 것 |
| --- | --- | --- |
| `templates/cozy/{layout,pattern,component,icon}/` | 프레임워크(cozy-ui jar). 앱은 쓰지 않는다 | `cozy/` 안의 아래 계층 |
| `templates/fragments/{layout,pattern,component,icon}/` | 앱 공용 | `cozy/`와 `fragments/`의 아래 계층 |
| `templates/{모듈}/api/` | 컨트롤러가 렌더링하는 화면. 모델을 읽는다 | 자기 모듈 전체, 선언한 모듈의 `spi/`, `cozy/`, `fragments/` |
| `templates/{모듈}/spi/` | 다른 모듈이 부르는 fragment. 파라미터로만 받는다 | 자기 `internal/`·`spi/`, 선언한 모듈의 `spi/`, `cozy/`, `fragments/` |
| `templates/{모듈}/internal/` | 같은 모듈의 api·spi가 조합하는 조각. 파라미터로만 받는다 | 자기 `internal/`, `cozy/`, `fragments/` |
| `templates/error/` | Boot 오류 화면 | `cozy/`, `fragments/` |

## 규칙

| ID | 규칙 | 위반 예 |
| --- | --- | --- |
| R1 | 최상위 폴더는 Java 모듈 이름이거나 예약어(`fragments`, `error`)여야 한다. 앱은 `cozy/`에 쓰지 않는다. `cozy/`와 `fragments/` 바로 아래에는 `layout`, `pattern`, `component`, `icon` 폴더만 둔다. 모듈 템플릿은 `api/`, `spi/`, `internal/` 아래에 둔다 | `templates/vocc/`, `templates/memo/list.html` |
| R2 | `templates/` 바로 아래에 `.html`을 두지 않는다 | `templates/index.html` |
| R3 | 다른 모듈의 템플릿은 `spi/` 아래 것만 참조한다. 이때 내 모듈의 allowedDependencies에 `"{모듈} :: spi"` 또는 `"{모듈}"`이 있어야 한다 | voc/api/list가 `wiki/api/detail :: body`를 참조 |
| R4 | `cozy/`와 `fragments/` 안에서는 layout → pattern → component → icon 방향으로만 참조한다. 같은 계층끼리는 기본으로 금지다 | component가 pattern을 참조 |
| R5 | `cozy/`는 `cozy/` 밖을 참조하지 않는다. `fragments/`는 모듈 템플릿을 참조하지 않는다 | `fragments/pattern/x`가 `memo/spi/y`를 참조 |
| R6 | 참조한 템플릿과 fragment가 실제로 있고, 넘긴 인자 개수가 선언과 같아야 한다 | `~{cozy/component/badge :: badg(x)}` |
| R7 | 동적 참조는 금지한다. 검사할 수 없는 것은 실패로 닫는다 | `th:replace="~{${view} :: body}"` |
| R8 | `cozy/`와 `fragments/` 계층 폴더의 모든 fragment는 매니페스트에 있어야 한다. 매니페스트의 모든 항목은 실제 fragment와 맞아야 한다 | 매니페스트에 없는 `fragments/component/chip` |
| R9 | 같은 모듈 안에서는 api → spi → internal 방향으로만 참조한다. spi는 자기 api를, internal은 자기 spi·api를 참조하지 않는다. internal은 다른 모듈의 템플릿을 참조하지 않는다 | `memo/spi/board`가 `memo/api/list :: result`를 참조 |
| R10 | 앱 모듈의 `spi/`·`internal/` fragment는 파라미터와 지역 변수만 읽는다. 부르는 화면의 모델을 읽지 않는다 | `card(summary)`가 `${memos.size()}`를 읽음 |
| R11 | 한 요소에 `th:each`와 `th:replace`(`th:insert`)를 같이 쓰지 않는다 | `<tr th:each="m : ${memos}" th:replace="~{memo/internal/row :: row(${m})}">` |
| R12 | fragment 이름은 같은 파일에 있는 요소 이름과 겹치지 않는다. Thymeleaf가 `~{t :: form}`으로 `th:fragment="form"`과 `<form>` 요소를 모두 고르기 때문이다 | 삭제 `<form>`이 있는 파일의 `th:fragment="form"` |
| R13 | `cozy/icon/tabler :: icon('이름')`, `iconFilled('이름')`에 글자로 쓴 이름은 Tabler Icons에 있어야 한다 | `icon('pluss')` |
| R14 | 앱 템플릿은 색 클래스를 직접 쓰지 않는다. 색은 tone을 받는 컴포넌트가 고른다. 글자로 쓴 tone은 neutral, info, success, warning, critical 중 하나다 ([design.md](design.md) §4) | `class="badge bg-blue-lt"`, `badge('고정', 'blue')` |
| R15 | 버튼 묶음(`btn-list`)에 주 버튼(`btn-primary`, `btn-danger`, 확인 창의 삭제)은 하나만, 맨 오른쪽(마지막)에 둔다 ([design.md](design.md) §1) | `[저장] [취소]` |

### 세부 기준

- **추출 대상**: `th:replace`, `th:insert`, `th:include`, `th:substituteby`, 그리고 모든 속성 값 안의 `~{...}`. `data-th-*` 형식도 같다. HTML은 AttoParser로 요소와 속성을 순회해 읽는다.
- **같은 파일 참조**: `~{:: x}`와 `~{this :: x}`는 같은 파일 참조다. 같은 파일 안의 참조는 R3~R5의 경계 검사를 받지 않는다(ADR-0021).
- **선택자**: fragment 이름, `#id`, `.class`, 요소 이름(`main`), `tag#id`, `tag.class`만 검사할 수 있다. 다른 마크업 선택자(`span[@id='x']`, `//div`)는 R7이다.
- **슬롯**: `th:replace="${p}"`는 `p`가 감싸는 `th:fragment`의 파라미터일 때만 허용한다. fragment를 받아 끼워 넣는 슬롯이기 때문이다. 다른 변수를 쓰면 R7이다.
- **R7로 닫는 것**:
  - 템플릿 경로나 fragment 이름에 `${}`, `*{}`, `|...|`, `__...__`가 들어간 참조
  - 본문 텍스트 안의 `~{}`
  - 프로토타입 전용 주석(`<!--/*/ ... /*/-->`) 안의 마크업
  - HTML로 읽을 수 없는 파일
- **`th:include` 사용**: R7 **경고**다. 빌드는 실패하지 않고 보고만 한다. `th:replace`나 `th:insert`를 쓴다.
- **선언하지 않은 의존**: `allowedDependencies`를 선언하지 않은 모듈(open)은 다른 모듈의 템플릿을 참조할 수 없다. Modulith는 선언이 없으면 모두 허용하지만, 템플릿 경계는 실패로 닫는다(ADR-0022).
- **같은 경로의 템플릿**: 앱과 프레임워크에 같은 경로의 템플릿이 있으면 참조는 앱 파일로 해석한다(예: `error/4xx.html`). 둘 다 검사한다.
- **매니페스트(R8)에 적을 수 있는 것**: `cozy/`와 `fragments/`의 계층 폴더에 있는 fragment, 그리고 모듈 `spi/`의 fragment. 모듈 공개 fragment가 매니페스트에 있는지는 검사 C5가 본다.
- **R10 범위**: fragment 선언(`th:fragment`)부터 그 요소가 끝날 때까지의 모든 `${...}`, `*{...}`, `[[...]]`, `[(...)]`를 본다. 읽을 수 있는 이름은 다음과 같다.
  - fragment 파라미터, 그 안의 `th:each`(반복 변수와 상태 변수 `이름Stat` 또는 지정한 이름), `th:with`로 만든 지역 변수
  - 유틸리티 `#strings`, `#lists`, `#temporals` 등. 단 `#vars`, `#ctx`, `#root`, `#request`, `#session`, `#response`, `#servletContext`, `#application`은 모델이나 요청을 직접 읽으므로 금지다
  - 이름이 `cozy`로 시작하는 프레임워크 전역, `_csrf`
  - `@빈` 참조와 `param`(요청 파라미터)은 금지다. `*{...}`는 fragment 안의 `th:object` 아래에서만 쓴다
- **R10과 R11**: 같은 요소의 th:each + th:replace는 R11 하나로 보고한다. 반복 변수를 R10으로 한 번 더 보고하지 않는다.
- **R12 범위**: 그 요소가 fragment 자신뿐이어서 지금은 해롭지 않아도 실패로 닫는다. 같은 요소가 하나 더 생기는 순간 선택이 깨지기 때문이다. 이름은 `memoForm`, `memoTable`처럼 짓는다.
- **R14 범위**:
  - 앱 템플릿(`origin APP`)의 `class`와, `th:classappend`·`th:class` 식 안의 글자 그대로인 클래스를 본다.
  - 대상은 `bg|text|border|link|badge|alert|avatar|btn|btn-outline|btn-ghost|status|ribbon`에 색 이름(primary, secondary, success, info, warning, danger, light, dark, muted, white, black, blue, azure, indigo, purple, pink, red, orange, yellow, lime, green, teal, cyan, gray)이 붙은 것이다.
  - 허용: `text-secondary`, `text-muted`, `btn-primary`.
  - 식으로 이어 붙인 색 클래스(`|bg-${color}-lt|`)는 확인할 수 없으므로 실패로 닫는다.
  - 프레임워크 템플릿은 tone을 구현하므로 대상이 아니다.
  - tone 확인은 `badge`의 둘째 인자, `alert`·`toast`의 첫째 인자를 글자로 쓴 경우만 본다.
- **R15 범위**:
  - `btn-list` 요소 안의 동작을 문서 순서로 센다. 동작은 `btn` 클래스가 있는 요소나 `th:replace`·`th:insert`로 바꿔 끼우는 요소다(무엇이 들어올지 모르므로 보조 동작 하나로 센다).
  - 동작 안쪽은 세지 않는다.
  - 주 버튼이 없는 묶음은 통과한다.
- **R13 범위**: 글자로 쓴 이름만 본다. 변수로 넘긴 이름(`icon(${link.icon})`)은 Java 쪽이 확인한다(`CozyIcons.requireOutline`, `CozyNavItem`). 아이콘 목록이 없으면(cozy-ui가 클래스패스에 없으면) 글자로 쓴 아이콘은 모두 실패한다.
- **속성 순서**: 검사는 Thymeleaf의 속성 우선순위(th:insert/replace → th:each → th:if·th:unless·th:switch·th:case → th:object·th:with → 나머지)대로 지역 변수가 생기는 시점을 따른다.

## 실패 메시지

규칙 ID, 위치, 참조, 고치는 방법을 한 줄에 담는다. 작은 LLM이 메시지만 보고도 고칠 수 있어야 하기 때문이다.

```text
[R3] templates/voc/api/list.html:2 → wiki/api/detail :: body — wiki/api/는 wiki 안에서만 씁니다. 다른 모듈에는 wiki/spi/ 아래 fragment만 공개됩니다. wiki/spi/에 공개 fragment를 추가하세요.
[R9] templates/memo/spi/board.html:2 → memo/api/list :: result — spi는 같은 모듈의 api를 참조하지 않습니다. 화면과 같이 쓰는 조각은 memo/internal/로 옮기고 api와 spi가 둘 다 그것을 조합하게 하세요.
[R10] templates/memo/spi/card.html:2 → ${memos.size()} → memos — 'memos'는 card(summary)의 파라미터나 지역 변수가 아닙니다. spi·internal fragment는 부르는 화면의 모델을 읽지 않습니다. card(summary)에 파라미터를 더하고 부르는 쪽이 값을 넘기게 하세요.
[R12] templates/memo/api/detail.html:3 → th:fragment="form" — 같은 파일에 <form> 요소가 있습니다. Thymeleaf는 'form'를 고를 때 그 이름의 fragment와 요소를 모두 가져옵니다. HTML 요소 이름과 다른 이름으로 바꾸세요 (예: form → memoForm, table → memoTable).
[R13] templates/memo/api/list.html:2 → cozy/icon/tabler :: icon(1개 인자) — 아이콘 'pluss'가 Tabler Icons에 없습니다. 비슷한 이름: plus, …
[R14] templates/memo/api/list.html:2 → class="bg-blue-lt" — 앱 화면은 색을 직접 고르지 않습니다. 색은 의미(tone: neutral, info, success, warning, critical)를 받는 cozy 컴포넌트가 고릅니다 (예: badge('고정', 'neutral'), alert('critical', …), deleteConfirm(…)). 허용: text-secondary, btn-primary.
[R15] templates/memo/internal/fields.html:2 → class="btn-list" — 주 버튼(btn-primary, btn-danger)은 버튼 묶음의 맨 오른쪽(마지막)에 둡니다. 예: [취소] [저장], [삭제] [수정].
[R11] templates/memo/internal/table.html:2 → th:each + th:replace — 같은 요소에 쓰면 th:replace(th:insert)가 th:each보다 먼저 실행되어 반복 변수가 아직 없습니다. <th:block th:each="..."> 안쪽 요소에 th:replace를 두세요.
[R6] templates/memo/api/form.html:14 → cozy/component/badge :: badge(1개 인자) — badge(text, color)는 파라미터가 2개인데 1개를 넘겼습니다. 선언과 같은 개수로 넘기세요.
[R7 경고] templates/memo/api/list.html:3 → th:include="..." — th:include는 Thymeleaf 3에서 권장하지 않습니다. th:replace나 th:insert를 쓰세요.
```

## 설정

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `cozy.rules.allow-same-layer` | `false` | `true`면 같은 계층 fragment 참조를 허용한다(R4). 앱 `application.yml`에 둔다 |

## API (cozy-rules)

cozy-rules는 JDK와 AttoParser에만 의존한다. 앱은 직접 쓰지 않고, cozy-test가 다음과 같이 호출한다.

```java
List<Violation> violations = TemplateRules.check(new RulesInput(
        templates,        // List<TemplateFile>: 앱(APP)과 cozy-ui(FRAMEWORK) 템플릿
        moduleModel,      // ModuleModel: 모듈 이름 → allowedDependencies, Named Interface
        manifest,         // List<FragmentDeclaration>: 프레임워크와 앱 매니페스트 항목
        allowSameLayer));
```

- `check`는 예외를 던지지 않고 위반 목록(`Violation`: 위치, 줄, 규칙 ID, 심각도, 참조, 메시지)만 돌려준다.
- `TemplateFile.fromDirectory(root, origin, prefix)`는 디렉터리 아래의 `.html`을 모두 읽는다.
- `TemplateRules.declaredFragments(file)`는 그 템플릿이 선언한 fragment 이름을 돌려준다.
- 모듈 이름은 Modulith 규칙을 따른다. 메인 패키지 바로 아래 패키지의 단순 이름이고, `@ApplicationModule(id = ...)`이 있으면 그 이름이다.

규칙마다 통과하는 경우와 위반하는 경우의 테스트가 있다(`cozy-rules`의 `TemplateRulesTest`). `samples/violations/rules/`에는 규칙별 위반 픽스처가 있다.
