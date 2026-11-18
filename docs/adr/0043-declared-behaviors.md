# ADR-0043 화면 안의 동작을 선언으로: 동작 명세가 원본이고 JS는 cozy가 만든다

- 상태: 승인 (2026-11-17, 사용자 결정 "권장안대로 승인할게 m46부터 진행해줘"). "정할 것" 1~5는 권장안
- 출처: 사용자 요청 "내가 원하는 상태는 에이전트가 직접 코드를 수정하게 하는게 아니라 cozy cli만 이용해서 작업이 가능하게 하는거야, 이 경우에는 워크벤치에서 각 컴포넌트에 어떤 이벤트가 걸려있는지 알 수 있고 관련 문서화(지금은 없겠지만) 등 부가적인 사항들이 자동으로 이루어져 기능 추가, 수정의 완결성을 보장할 수 있게하는거야"
- 바꾸는 결정:
  - ADR-0032 "구조는 cozy 명령, 로직은 에이전트": 화면 안의 동작(Alpine)은 로직까지 cozy 명령이 만든다. 서버 쪽 로직(Java, SQL)은 그대로 에이전트가 채운다.
  - ADR-0040 §5 "`cozy add behavior`가 등록과 빈칸을 만들고 에이전트가 로직을 채운다": 빈칸을 남기지 않는다. 모듈의 동작 명세가 원본이고, `static/{모듈}/{모듈}.js`는 cozy가 만드는 파일이다(§2).
  - ADR-0040 §3: 앱 템플릿의 `x-*` 속성은 cozy 명령이 붙인다(§3). 작성 규칙(긴 이름, 이름만 쓰는 `x-data`, CSP 빌드의 식)은 그대로이고, 손으로 쓴 속성도 R16을 통과하면 읽는다.
- 바꾸지 않는 것: htmx와 Alpine의 분담(ADR-0040 §1, 새로고침해도 남아야 하면 서버), CSP와 CSP 빌드, 프레임워크 컴포넌트(`cozy…`)와 워크벤치 컴포넌트(`workbench…`)는 손으로 쓴 JS, 모듈 JS를 싣는 방법(`/cozy/modules.js`), 의존(rules는 JDK와 AttoParser, cli는 rules와 SnakeYAML)

## 배경

- **동작의 로직이 JS 빈칸에 있다.** `cozy add behavior`는 `Alpine.data(…)` 등록과 빈칸 `behavior-{컴포넌트}`를 만들고, 상태와 메서드는 에이전트가 JS로 쓴다. 템플릿의 `x-on:input="count"`, `x-text="label"`도 에이전트가 손으로 단다.
- **그래서 cozy가 동작을 모른다.** `cozy describe`는 화면이 쓰는 컴포넌트 이름과 `x-data`의 줄만 안다(`screens[].components`). 컴포넌트가 가진 상태, 어떤 요소의 어떤 이벤트가 어떤 메서드를 부르는지, 컴포넌트가 내고 받는 이벤트는 모른다. 워크벤치의 구조 트리와 속성 표도 그렇다.
- **완결성을 확인할 수 없다.** 템플릿이 부르는 메서드가 JS에 있는지, 만든 컴포넌트를 어디에 붙였는지, `$dispatch`한 이벤트를 누가 받는지 빌드가 보지 않는다. R16은 `x-data`의 이름까지만 본다. 잘못되면 화면에서 조용히 아무 일도 일어나지 않는다.
- **문서가 없다.** 앱 컴포넌트의 상태와 동작은 JS 주석이 전부다. 프레임워크 컴포넌트는 `ui.md`의 표가 있지만 손으로 맞춘다.
- **화면 안의 동작은 대부분 몇 가지 모양이다.** 펼치기·접기, 탭, 선택한 행 수와 버튼 활성, 입력 글자 수와 미리보기, 목록 거르기, 다른 영역에 알리기다. reference-app의 `memoBodyCount`(글자 수)와 워크벤치의 동작 대부분이 이 모양이다. 이 모양을 정해 둔 낱말로 적을 수 있으면 cozy가 JS를 만들고, 붙이고, 읽고, 확인할 수 있다.

## 결정

### 1. 동작 명세 `behaviors/{모듈}.yml`

모듈의 앱 컴포넌트는 `src/main/resources/behaviors/{모듈}.yml`에 선언한다. 이 파일이 원본이다.

```yaml
# memo 모듈의 화면 안 동작. cozy add behavior와 cozy attach가 고친다
components:
  bodyCount:                         # 컴포넌트 memoBodyCount
    purpose: 본문을 쓰는 동안 글자 수와 한도를 보인다
    state:
      length: { type: number, initial: 0 }
      limit:  { type: number, from: data-limit }
    computed:
      over:  { gt: [length, limit] }
      label: { format: "{length} / {limit}자", numbers: ko-KR }
    actions:
      count: { set: length, to: { length: event.value } }
  selection:
    purpose: 고른 행 수에 따라 묶음 버튼을 켠다
    state:
      ids: { type: list, initial: [] }
    computed:
      count: { count: ids }
      none:  { empty: ids }
    actions:
      pick:  { toggleItem: ids, value: event.data.id }
      clear: { reset: ids }
    emits:
      memo:selection-changed: { after: [pick, clear], detail: [ids] }
    listens:
      memo:archived: clear           # 서버의 HX-Trigger
```

- **상태(`state`)**: `bool`, `number`, `text`, `list`(글자 목록). 처음 값은 `initial`이나 `x-data` 요소의 `data-*`(`from`)다.
- **계산(`computed`)**: 정해 둔 연산만 쓴다. `not`, `and`, `or`, `eq`, `gt`, `lt`, `empty`, `count`, `length`, `includes`, `format`(자리 `{이름}`, 숫자 형식). 연산은 겹쳐 쓸 수 있다.
- **동작(`actions`)**: `set`(값, 계산, `event.value`, `event.checked`, `event.data.{이름}`), `toggle`, `increment`·`decrement`, `addItem`·`removeItem`·`toggleItem`, `reset`. 한 동작에 여러 단계를 적을 수 있다.
- **내고 받는 이벤트(`emits`, `listens`)**: 이름은 `{모듈}:{이름}`이다. `emits`는 동작 뒤에 `$dispatch`한다. `listens`는 창의 이벤트(다른 컴포넌트의 `emits`, 서버의 `HX-Trigger`)에 동작을 잇는다. htmx는 `hx-trigger="memo:selection-changed from:window"`로 받는다.
- **목적(`purpose`)**: 컴포넌트마다 한 문장이 필수다. 문서와 워크벤치가 이 문장을 보인다(§5).
- 낱말에 없는 것은 적을 수 없다. 형식이 틀리거나 모르는 키는 실패로 닫는다.

### 2. JS는 cozy가 만든다

- `static/{모듈}/{모듈}.js`는 명세에서 만든다. 파일 머리에 "cozy가 동작 명세에서 만든 파일. 고치지 않는다"와 명세의 SHA-256을 적는다.
- 같은 명세는 같은 JS가 된다. 명령은 명세와 JS를 함께, 원자적으로 바꾼다(ADR-0032).
- **새 검사 C12**: 명세와 JS가 맞지 않으면(손으로 고친 JS, 고친 명세) `mvn verify`와 `cozy check`가 실패한다. 고치는 명령은 `cozy sync behaviors`(명세에서 JS를 다시 만든다)다.
- 만든 JS는 지금 규칙을 그대로 따른다: `alpine:init`의 `Alpine.data` 등록, 이름은 모듈 이름으로 시작, `init`에서 `$root`의 `dataset`을 읽음, 서버 요청 없음. C10(문법)과 R16(이름)을 그대로 통과한다.

### 3. 템플릿에 붙이는 것도 cozy가 한다: `cozy attach`

```bash
cozy add behavior memo body-count --purpose "본문을 쓰는 동안 글자 수와 한도를 보인다" \
  --state length:number=0 --state limit:number@data-limit \
  --computed "label=format:{length} / {limit}자" --action "count=set:length,length(event.value)"
cozy attach memoBodyCount --at memo/internal/fields:4 --data limit=5000 --on input:count
cozy attach memoBodyCount --at memo/internal/fields:6 --text label --show "not over" --cloak
cozy attach cozyCopy --at memo/api/detail:12 --data "copy-text=${#strings.listJoin(memo.bodyLines, '&#10;')}" --on click:copy
```

- **`cozy add behavior`**: 명세에 컴포넌트 하나를 더한다. 이미 있는 컴포넌트에는 `cozy add behavior memo body-count --action …`처럼 항목을 더한다. 지우기와 이름 바꾸기는 `cozy remove behavior`, `cozy rename behavior`다(붙인 곳도 함께 바꾼다).
- **`cozy attach <컴포넌트> --at <템플릿>:<줄>`**: 그 줄의 요소에 `x-data`와 `data-*`(감싸는 요소), `x-on:*`, `x-text`, `x-show`, `x-bind:*`, `x-model`, `x-cloak`을 단다. `--detach`는 뗀다.
  - 위치는 `cozy describe`와 워크벤치가 보이는 `템플릿:줄`이다. 그 줄에 요소가 하나가 아니거나, 붙일 이름이 명세에 없거나, 안쪽 요소인데 감싸는 `x-data`가 그 컴포넌트가 아니면 실패한다.
  - 서버 값은 `--data 이름=Thymeleaf 식`으로 주면 `th:data-*`가 된다. Alpine 식에는 Thymeleaf 식을 넣지 않는다(ADR-0040 §3).
  - 프레임워크 컴포넌트(`cozyCopy`, `cozyDisclosure` 등)도 같은 명령으로 붙인다. 받는 `data-*`와 메서드는 프레임워크 컴포넌트의 명세(§5)에서 읽는다.
- 두 명령 모두 `--dry-run`, `--json`, 계획 → 적용, 두 번 실행해도 같음, 실패하면 아무 파일도 바꾸지 않음(ADR-0032).
- 워크벤치의 "코드 생성"과 속성 표가 같은 명령을 폼으로 부른다(ADR-0036 §3, ADR-0038).

### 4. 읽기: `cozy describe`와 워크벤치

- `cozy describe --json`의 `modules[].behaviors[]`: 컴포넌트마다 `name`, `purpose`, `state[]`, `computed[]`, `actions[]`, `emits[]`, `listens[]`, `attached[]`(`template`, `line`, `bindings[]`: `x-on:input → count`, `x-text = label`).
- `screens[].bindings[]`: 화면의 요소마다 붙은 동작. Alpine 바인딩과 함께 htmx 속성(`hx-get`, `hx-post`, `hx-trigger`, `hx-target`)도 읽기 전용으로 싣는다. 화면에서 "이 요소에 무엇이 걸려 있는지"를 한 곳에서 본다.
- `events[]`(앱 이벤트)와 따로 `uiEvents[]`: 화면 이벤트마다 내는 곳(`emits`, 서버의 `HX-Trigger`), 받는 곳(`listens`, `hx-trigger … from:window`).
- **워크벤치**:
  - 구조 트리의 줄에 동작 표시(번개 아이콘과 수)가 붙는다. 이름 앞에 붙고 글자로도 읽힌다(`동작 2`).
  - 속성 표에 "동작" 구획: 컴포넌트, 이벤트(`input → count`), 묶인 값(`글자 = label`, `보임 = not over`), htmx 요청. 컴포넌트를 누르면 그 명세(상태, 계산, 동작, 이벤트)가 보인다.
  - 모듈 화면에 "화면 안의 동작" 목록(컴포넌트, 목적, 붙인 화면 수)과 화면 이벤트의 흐름(내는 곳 → 받는 곳)이 보인다.
  - 속성 표에서 이벤트와 묶인 값을 더하고 떼면 `cozy attach … --dry-run`의 결과가 보이고 적용한다(매개변수 수정과 같은 흐름).

### 5. 문서는 명세에서 나온다

- 앱 쪽 문서는 따로 쓰지 않는다. 동작 명세의 `purpose`와 구조가 문서이고, `cozy describe`(글과 `--json`), 워크벤치, 카탈로그가 같은 원본을 보인다. 손으로 맞출 문서가 없으므로 어긋나지 않는다.
- **프레임워크 컴포넌트도 명세를 갖는다.** `cozy/behaviors.yml`(cozy-ui)에 `cozy…` 컴포넌트마다 `purpose`, 받는 `data-*`, 메서드, 내는 이벤트를 적는다. JS는 지금처럼 손으로 쓰고, 테스트가 JS의 등록과 명세가 맞는지 확인한다. `ui.md`의 컴포넌트 표는 이 명세에서 만들고 테스트가 같은지 본다.
- 에이전트 문서: `add-behavior` 플레이북을 새 형식으로 다시 쓰고, `attach` 플레이북을 더한다. 에이전트는 JS와 `x-*` 속성을 손으로 쓰지 않는다(`AGENTS.md`의 Rules).

### 6. 완결성 검사

| 검사 | 실패하는 경우 |
| --- | --- |
| C12 명세와 JS | 만든 JS가 명세와 다름 |
| R16 이름 (넓힘) | 템플릿의 `x-on`·`x-text`·`x-show`·`x-bind`·`x-model`이 부르는 이름이 감싸는 컴포넌트의 상태, 계산, 동작에 없음. 지금은 `x-data`의 이름만 본다 |
| C13 붙이지 않은 동작 | 명세의 컴포넌트를 어느 템플릿에도 붙이지 않음, 동작을 어느 요소도 부르지 않음 |
| C13 화면 이벤트 | `emits`를 받는 곳이 없음, `listens`를 내는 곳이 없음(서버의 `HX-Trigger`는 컨트롤러의 글자 이름을 찾는다. 찾을 수 없으면 실패) |

- 모두 `cozy check`가 같은 문구로 보이고, 고치는 명령(`cozy attach …`, `cozy sync behaviors`)이 있으면 함께 보인다.
- 워크벤치 개요의 "미구현 항목"에 C13을 더한다.

### 7. 낱말로 안 되는 동작

- 앱 모듈에 손으로 쓴 JS 컴포넌트를 두는 길은 남기지 않는다(정할 것 1). 낱말로 적을 수 없으면 명령이 실패로 닫고, 필요한 모양을 알린다.
- 그런 동작은 프레임워크 컴포넌트(`cozy…`)나 낱말 추가로 푼다. 프레임워크를 고치는 일이므로 이 레포에서 ADR이나 마일스톤으로 한다.
- 이미 있는 앱의 손으로 쓴 모듈 JS(명세가 없는 `static/{모듈}/{모듈}.js`)는 정할 것 2를 따른다.

## 정할 것

| | 선택지 | 권장 |
| --- | --- | --- |
| 1 | 낱말로 안 되는 동작: 프레임워크 컴포넌트나 낱말을 더한다 / 앱에 손으로 쓴 컴포넌트를 표시를 붙여 허용한다 | 프레임워크에서 더한다. 손으로 쓴 길이 있으면 에이전트가 그 길로 가고, 완결성 검사가 빈다 |
| 2 | 이미 있는 손으로 쓴 모듈 JS: 2.x에서는 "직접 쓴 코드"로 읽기만 하고 경고, `cozy migrate behaviors`가 낱말로 옮길 수 있는 것을 옮긴다 / 바로 오류(3.0.0) | 2.x에서 경고와 옮기는 명령. 다음 주 버전에서 오류로 바꾼다 |
| 3 | 문서: 명세의 `purpose`와 describe·워크벤치에서 보기 / 앱에 Markdown 문서를 만들고 검사 | 명세와 보기. 따로 만든 파일은 또 하나의 맞출 것이다 |
| 4 | `cozy attach`의 위치: describe·워크벤치의 `템플릿:줄` / 요소의 `id`가 있어야 함 | `템플릿:줄`. 워크벤치가 이미 그 위치를 보이고, 줄이 하나의 요소가 아니면 실패로 닫는다 |
| 5 | htmx 속성: 읽어서 보이기만 / `cozy attach`로 붙이기까지 | 읽어서 보이기만. htmx 속성은 화면 레시피가 만들고, 붙이는 명령은 다음 ADR로 둔다 |

## 이번에 하지 않는 것

- htmx 속성을 붙이고 고치는 명령(정할 것 5).
- 화면 명세의 상태에 Alpine 상태를 적어 미리보기를 그 상태로 그리기(펼친 패널, 고른 행). 다음 ADR로 둔다.
- 워크벤치 자신과 cozy-ui의 손으로 쓴 컴포넌트를 명세에서 만들기. 이번에는 명세를 문서와 검사에만 쓴다(§5).

## 단계

| 마일스톤 | 완료 기준 |
| --- | --- |
| M46 명세와 만들기 | `behaviors/{모듈}.yml` 형식과 낱말, JS 만들기, `cozy add behavior`(새 형식), `cozy sync behaviors`, C12. 낱말마다 만든 JS가 브라우저에서 동작한다(runtime-verifier). 같은 명세는 같은 JS. 모르는 키와 낱말 밖은 실패 |
| M47 붙이기 | `cozy attach`·`--detach`, `remove`·`rename behavior`, 프레임워크 컴포넌트의 명세 `cozy/behaviors.yml`과 `ui.md` 표, R16 이름 넓힘. reference-app의 `memoBodyCount`와 화면 레시피가 명령만으로 같은 화면이 된다 |
| M48 읽기 | `describe`의 `behaviors[]`, `bindings[]`, `uiEvents[]`. 워크벤치의 구조 트리 표시, 속성 표의 "동작" 구획과 attach 폼, 모듈 화면의 동작 목록과 이벤트 흐름(runtime-verifier) |
| M49 완결성과 옮기기 | C13, `cozy migrate behaviors`, 손으로 쓴 모듈 JS의 경고. 위반 픽스처마다 빌드가 실패하고, 레시피 픽스처와 아키타입 3조합이 통과한다 |
| M50 문서 | `docs/conventions/`(ui.md의 동작 명세와 낱말, cli.md의 명령, rules.md·testing.md의 검사, workbench.md), 에이전트 문서(`add-behavior` 다시 쓰기, `attach` 플레이북, `AGENTS.md` Rules), 릴리스 노트의 옮기는 절차 |

## 위험

- **낱말이 모자라면 일이 막힌다.** 손으로 쓰는 길을 막았으므로 낱말 밖의 동작은 프레임워크를 고칠 때까지 만들 수 없다. 처음 낱말은 reference-app, 워크벤치, 화면 레시피에 있는 동작을 모두 적을 수 있는지로 정하고, M46에서 적어 보며 확인한다.
- **줄 위치는 바뀐다.** `cozy attach --at`의 줄은 명령을 부르기 직전의 describe나 워크벤치에서 읽어야 한다. 줄이 다른 요소를 가리키면 실패로 닫지만, 같은 모양의 요소가 그 줄로 옮겨 오면 막지 못한다. 계획(`--dry-run`)이 붙일 요소의 여는 태그를 보인다.
- **C13이 개발 중에 시끄러울 수 있다.** 컴포넌트를 선언하고 아직 붙이지 않은 동안 빌드가 실패한다. `cozy:slot`(C9)과 같은 정책이다. 선언과 붙이기를 한 작업으로 하라고 플레이북에 적는다.
- **만든 JS가 커진다.** 낱말을 일반적인 코드로 풀어 손으로 쓴 것보다 길다. 묶음 하나로 싣고 내용 해시로 캐시하므로(ADR-0040) 화면마다 늘지 않는다.

## M46 (2026-11-17, 사용자 지시 "권장안대로 승인할게 m46부터 진행해줘")

- 시작할 때 최신 GA: 같은 날 확인한 그대로다(ADR-0017 2026-11-17). 새 의존성은 없다(명세는 cozy-cli의 SnakeYAML, C12는 JDK의 `MessageDigest`).
- 만든 것: 동작 명세 `behaviors/{모듈}.yml`, 식 파서와 JS 만들기(cozy-cli `internal/behavior`), 새 `cozy add behavior`, `cozy sync behaviors`, C12(cozy-rules `BehaviorRules`, `cozy check`와 `CozyArchitectureTest.behaviorScriptsMatchTheirSpecs`). 형식과 낱말은 `docs/conventions/ui.md`의 "동작 명세"에 있다.
- 계획과 달라진 것:
  - **식의 표기**: §1의 예는 연산마다 YAML 묶음(`{ gt: [length, limit] }`)이었다. 함수 호출 모양의 글자 하나(`gt(length, limit)`)로 바꿨다. 명령줄(`--computed over:gt(length, limit)`), 명세, 워크벤치가 같은 글자를 보이고, cozy가 정리한 모양으로 다시 쓴다(같은 식은 같은 글자). 낱말 밖은 똑같이 실패로 닫는다.
  - **내는 이벤트**: §1의 `emits`(동작 뒤에 낼 이벤트 목록) 대신 동작의 단계 `emit('memo:이름', 상태 …)`로 했다. 언제 내는지가 동작 안에 보인다. describe가 내는 곳을 단계에서 읽는다(M48).
  - **명령줄 모양**: `--computed 이름:식`, `--action 이름:단계`, `--listen 동작:모듈:이벤트`. 워크벤치 폼이 여러 번 쓰는 옵션을 `:`로 이은 구성으로 다루기 때문이다(`--field 이름:종류`와 같은 모양). §3의 예(`=`)와 다르다.
  - **낱말을 적어 보며 더한 것**(위험의 "M46에서 적어 보며 확인한다"): 계산 `ne`, `ge`, `le`, `number`, `if`, 단계 `when`, `within`, 이벤트 값 `event.detail.이름`. reference-app의 `memoBodyCount`(textarea에서만 세고, 한도를 넘으면 문구를 붙인다)를 적는 데 `within`과 `if`가 필요했다.
  - **처음 값**: `memoBodyCount`는 시작할 때 textarea의 값을 읽었다. 명세는 DOM을 읽지 않고 서버가 `data-length`로 넘긴다(ADR-0040 §3 "서버 값은 data-*"). R10 때문에 `memoFields()`가 파라미터 `bodyLength`를 받고 만들기·수정 화면이 넘긴다.
- 확인:
  - `BehaviorRulesTest` 10개: 맞는 파일, CRLF와 BOM, 손으로 고친 JS, 다시 만들지 않은 명세, 한쪽만 있음, 명세 옆의 손으로 쓴 JS, 명세 폴더의 다른 파일.
  - `BehaviorScriptTest` 23개: 낱말 전부를 쓰는 `demo.yml` → `demo.js`(글자까지 같음), 모양만 다른 명세가 같은 파일이 됨, 표시 줄, 서버 요청 없음, 식을 같은 글자로, 낱말 밖·없는 이름·종류·순환·이벤트 값의 자리·예약 이름·이름 겹침·다른 모듈의 이벤트·format의 이름과 중괄호, 파일 형식(모르는 키, 목적 없음, 같은 키 두 번).
  - `BehaviorRecipeTest` 11개: 명세와 JS를 만들고 빈칸 없음, 다시 실행하면 바뀐 것 없음, 항목 더하기와 바꾸기, 두 동작이 한 명세, 손으로 고친 JS는 C12와 `sync behaviors`, 손으로 고친 명세는 정리, 명세를 지우면 JS를 지움, 손으로 쓴 모듈 JS는 덮어쓰지 않음, 실패한 명령은 아무 파일도 바꾸지 않음.
  - 위반 픽스처 `C12-behaviors`, 레시피 픽스처 `behavior`(명령만으로 선언, 모양만 다른 명세의 C12와 `sync behaviors`, 다른 모듈 컴포넌트의 R16, 만든 JS의 C10).
  - 브라우저(runtime-verifier, 2026-11-17): `demo.html`의 낱말 16개 항목(within의 거름, event.data가 안쪽 요소에서 올라가 찾음, toggleItem·addItem·removeItem, emit의 detail, listens의 창 이벤트와 reset, when, number, event.checked, format의 ko-KR과 목록과 중괄호)이 Alpine CSP 빌드에서 동작한다. reference-app 메모 만들기·수정의 글자 수(처음 값, 제목 칸은 세지 않음, 한도 넘음, 1280·390px)도 같다. 콘솔 오류와 CSP 위반은 0이다.
  - 레시피 픽스처의 cozy 명령 클래스패스(`target/cli/*`)에 clean 없이 쌓인 지난 판의 cozy-cli가 먼저 읽혀 새 옵션을 모르던 문제를 찾았다. 복사할 때 판 번호를 떼도록(`stripVersion`) 고쳤다.
  - 아키텍처 검사가 하나 늘어(C12) 테스트 수를 세는 위반·레시피 픽스처의 기대 값을 1씩 올렸다.

## M47 (2026-11-17, 사용자 지시 "다음 진행해줘")

- 시작할 때 최신 GA: 같은 날 확인한 그대로다. 새 의존성은 없다(템플릿의 여는 태그는 cozy-cli가 글자로 읽고, 프레임워크 명세는 SnakeYAML로 읽는다).
- 만든 것:
  - `cozy attach`(`--detach`), `cozy remove behavior`, `cozy rename behavior`(cozy-cli `BehaviorCommands`, `behavior/Attach`, `behavior/TemplateTags`). 형식은 `docs/conventions/cli.md`의 attach, remove behavior, rename behavior에 있다.
  - 프레임워크 컴포넌트의 명세 `cozy/behaviors.yml`(cozy-ui). `ui.md`의 프레임워크 컴포넌트 표는 이 명세에서 만들고 표지 주석 사이에 둔다.
  - R16 이름 검사, 같은 템플릿 안에 감싸는 `x-data`가 없는 앱 템플릿의 바인딩은 오류.
  - 플레이북 `attach`, `remove-behavior`, `rename-behavior`. `AGENTS.md`는 `x-*` 속성도 손으로 쓰지 않는다고 적는다.
- 계획과 달라진 것:
  - **R16이 이름을 읽는 곳**: cozy-rules는 YAML을 읽지 못한다(JDK와 AttoParser만). 그래서 컴포넌트가 내놓는 이름을 JS 파일의 한 줄 주석 `// cozy:component 이름 members=… data=…`(`ComponentContract`)로 적는다. 만든 JS는 cozy가 이 줄을 쓰고, `cozy.js`는 손으로 쓰되 `FrameworkBehaviorsTest`가 프레임워크 명세와 같은지 확인한다. 줄이 없는 컴포넌트(워크벤치의 `workbench…`)는 이름을 보지 않는다.
  - **이전 판이 만든 JS**: M46의 JS에는 이 줄이 없다. 해시가 맞아도 C12가 실패하고 `cozy sync behaviors`를 알린다(판단할 수 없는 것은 실패로 닫는다).
  - **줄 고르기**: §3은 "그 줄에 요소가 하나"였다. 버튼과 그 안의 아이콘처럼 한 줄에 시작하는 안쪽 요소가 흔해서, 그 줄에서 시작하는 가장 바깥 요소가 하나면 받는다. 나란히 놓인 요소가 둘 이상이면 실패한다. `th:replace`와 `th:block`은 그려질 때 속성이 남지 않아 거절한다.
  - **값은 이름만**: §3의 예 `--show "not over"`처럼 식을 받지 않는다. 이름 하나이거나 글자 인자 하나를 받는 프레임워크 메서드의 `이름('글자')`다. 로직은 명세의 계산에 둔다.
  - **프레임워크 명세의 "내는 이벤트"**: 지금 `cozy…` 컴포넌트는 화면 이벤트를 내지 않아 적지 않았다. 메서드 이름 뒤의 `(이름)`이 글자 인자 하나를 받는다는 표시다(`toggle(이름)`).
  - **속성 순서**: 새 속성은 마지막 속성 뒤에 `x-data`, `data-*`, `x-on`, `x-bind`, `x-show`, `x-text`, `x-model`, `x-cloak` 순서로 붙인다. reference-app의 두 곳(`memo/internal/fields`의 글자 수, `memo/api/detail`의 복사 버튼)을 명령으로 떼고 다시 붙여 이 순서가 되었다.
  - **떼면 줄이 합쳐질 수 있다**: 따로 줄에 있던 속성을 떼면 그 줄바꿈도 지운다(복사 버튼의 두 줄이 한 줄이 되었다). 플레이북은 `--detach` 뒤에 줄 번호를 다시 읽으라고 적는다.
- 확인:
  - `AttachCommandTest` 15개: 자리와 안쪽 요소, 다시 실행하면 그대로, 값 바꿈, `--dry-run`, 없는 이름·읽지 않는 `data-*`·식·안쪽의 `--data`, 줄 고르기와 `th:replace`, 한 요소에 한 컴포넌트와 모듈 밖, 프레임워크 컴포넌트와 인자, 손으로 고친 바인딩의 R16, 일부 떼기, 뗀 뒤 처음 템플릿과 같음, 컴포넌트를 지우면 붙인 곳도 떼고 빈 명세를 지움, 템플릿이나 다른 항목이 쓰는 항목은 지우지 않음, 이름 바꾸기, reference-app의 바인딩을 모두 떼고 명령으로 다시 붙이면 글자까지 같음.
  - `FrameworkBehaviorsTest` 5개: 명세와 `cozy.js`의 등록, `cozy:component` 줄, 메서드 정의, `ui.md` 표가 같음, 형식.
  - `TemplateRulesTest`에 R16 이름 3개(감싸는 컴포넌트의 이름, `x-for`와 객체 키와 `$event`, 같은 템플릿에 없는 `x-data`, 줄이 없는 컴포넌트), `BehaviorRulesTest`에 이전 판의 JS 1개.
  - 레시피 픽스처 `behavior`: 손으로는 마크업(글자 수를 보일 칸)만 두고 `cozy attach`로 붙인다. 없는 이름과 다른 모듈의 컴포넌트는 거절하고, 다시 실행하면 바뀐 것 없음.

## M48 (2026-11-18, 사용자 지시 "다음으로 넘어가줘")

- 시작할 때 최신 GA: 전날 확인한 그대로다. 새 의존성은 없다(템플릿은 M47의 `TemplateTags`로, 프레임워크 명세는 cozy-cli의 SnakeYAML로 읽는다).
- 만든 것:
  - `cozy describe --json`의 `modules[].behaviors[]`(상태, 계산, 동작, 내고 받는 화면 이벤트, 붙인 곳), `screens[].bindings[]`(요소마다 Alpine·htmx 속성, 화면에서 보이는 줄), `uiEvents[]`(화면 이벤트마다 내는 곳과 받는 곳). 글 출력에 `화면 안의 동작 …`, `화면 이벤트 …` 줄. 형식은 `docs/conventions/cli.md`의 describe에 있다.
  - 워크벤치: 구조 트리의 `동작 N`, 속성 표의 "화면 안의 동작" 구획(요소, 펼치면 명세가 보이는 컴포넌트, 붙은 것 한 줄씩, htmx 요청), 떼기와 붙이기 폼(`POST /cozy/dev/workbench/attach` → `cozy attach`, 변경 파일 확인 → 적용), 모듈 화면의 "화면 안의 동작"과 "화면 이벤트". 형식은 `docs/conventions/workbench.md`에 있다.
- 계획과 달라진 것:
  - **트리 표시의 자리**: §4는 "이름 앞"이었다. 이름 앞에 두면 같은 깊이의 이름이 줄마다 다른 자리에서 시작해 훑어 읽기 어렵다. 식별자 뒤에 둔다.
  - **센 것**: 붙인 속성이 아니라 요소의 수다(`동작 2`는 요소 둘). htmx 속성만 있는 요소도 센다. "이 요소에 무엇이 걸려 있는지"를 한 곳에서 보이려는 것이라서다.
  - **어느 줄에 붙는가**: 넣은 fragment의 요소는 화면 템플릿에서 넣은 줄(`screenLine`)의 가장 안쪽 마디에 붙는다. 어느 마디에도 담기지 않는 요소가 있어, 아무것도 고르지 않았을 때의 화면 속성에 모든 요소를 싣는다.
  - **프레임워크 명세를 읽는 곳**: describe는 cozy-ui jar 없이 돈다. 그래서 `cozy…` 컴포넌트의 명세는 describe에 싣지 않고, 워크벤치가 실행 중인 cozy-ui의 `cozy/behaviors.yml`을 읽는다.
  - **서버의 `HX-Trigger`**: 글자 `"HX-Trigger"`가 있는 Java 파일의 글자들에서 `모듈:이름`을 찾는다. 이어 붙여 만든 이름은 찾지 못한다. C13(M49)이 이 목록을 쓰므로, 찾지 못하는 모양은 그때 실패로 닫을지 정한다.
  - **받는 곳**: `hx-trigger`와 함께 템플릿의 `x-on:모듈:이름`도 읽는다(`alpine`).
  - **붙이기 폼의 범위**: 이미 컴포넌트가 감싸는 요소에서 이벤트와 값을 더하고 떼는 데까지다. 새 요소에 컴포넌트를 처음 붙이는 일(`x-data`와 `--data`)은 터미널의 `cozy attach`로 한다. 미리보기에서 고른 아무 요소나 받으려면 화면 줄에서 템플릿 줄을 거꾸로 찾아야 하는데, 넣은 fragment 안의 요소는 화면 줄만으로 정할 수 없다.
  - **이름 칸**: 선택 칸이 아니라 글자 칸과 쓸 수 있는 이름의 안내다. 프레임워크 메서드는 `toggle('filter')`처럼 인자를 함께 적기 때문이다. 틀린 이름은 명령이 거절한다.
  - **명세를 읽을 수 없을 때**: describe는 실패하지 않고 그 모듈의 `behaviors[]`를 비우고 이유를 `problems[]`에 싣는다. 실패로 닫는 일은 `cozy check`(C12)가 한다.
  - `screens[].components[]`는 `bindings[]`와 같은 길에서 모은다(정규식 대신 여는 태그를 읽는다). 결과는 그대로다(`DescribeComponentsTest`).
- 확인:
  - `DescribeBehaviorsTest` 5개: 명세와 붙인 곳(`when` 안의 `emit`), 넣은 fragment의 요소와 화면 줄, htmx만 있는 요소, 붙인 자리의 `data-*`, 프레임워크 컴포넌트, 서버의 `HX-Trigger`·`listens`·`hx-trigger`로 이은 화면 이벤트, 글 출력, 읽을 수 없는 명세는 `problems[]`.
  - reference-app의 describe 기대 파일(글과 JSON)을 다시 만들었다.
  - `WorkbenchBehaviorTest` 6개(reference-app): 트리의 `동작 2`와 속성 표(요소, 명세, 이벤트, 묶인 값, 넘기는 값, htmx), 프레임워크 명세, 붙이기 변경 파일 → 적용 → 떼면 처음과 같음, 없는 이름은 거절하고 파일은 그대로, CSRF·Origin·`step`·`option`·없는 화면, 모듈 화면의 목록.

## M49 (2026-11-18, 사용자 지시 "진행해줘")

- 시작할 때 최신 GA: 전날 확인한 그대로다. 새 의존성은 없다(C13은 cozy-rules의 템플릿 스캐너와 JDK, 옛 JS는 cozy-cli가 낱말로 나눠 읽는다).
- 만든 것:
  - C13(cozy-rules `BehaviorWiring`, `cozy check`와 `CozyArchitectureTest.behaviorsAreWired`): 붙이지 않은 컴포넌트, 부르는 요소도 받는 화면 이벤트도 없는 동작, 받는 곳이 없는 `emit`, 내는 곳이 없는 `listens`. 위치는 명세의 컴포넌트 줄이다. `cozy describe`의 `slots[]`에 `check: C13`으로 실려 워크벤치의 미구현 항목에 보인다.
  - 손으로 쓴 모듈 JS의 경고(C12 경고, 고치는 명령 `cozy migrate behaviors`).
  - `cozy migrate behaviors`(cozy-cli `behavior/LegacyScript`, `recipe/MigrateBehaviors`). 형식과 옮기는 모양은 `docs/conventions/cli.md`의 migrate behaviors에 있다.
  - 서버의 `HX-Trigger`를 찾는 일을 cozy-rules `ServerEvents`로 옮겨 describe의 `uiEvents[]`와 C13이 같은 판단을 쓴다.
- 계획과 달라진 것:
  - **C13이 읽는 곳**: cozy-rules는 YAML을 읽지 못한다. 만든 JS의 `cozy:component` 줄에 `actions=… emits=… listens=이벤트:동작`을 더해 읽는다(M47의 이름 줄과 같은 방법). 이 줄이 없는 만든 JS(M46~M48 개발 중의 판)는 C12가 `cozy sync behaviors`를 알린다. 프레임워크 컴포넌트의 줄은 그대로다(C13 대상이 아니다).
  - **서버 이름을 찾지 못하면**(M48의 남은 위험): 실패로 닫는다. `listens`의 이벤트를 내는 곳이 없으면 오류이고, 메시지가 이름을 `HX-Trigger` 값에 글자 그대로 적으라고 알린다. 요청 머리를 읽기만 하는 `getHeader("HX-Trigger")`는 내는 곳으로 세지 않는다.
  - **"부른다"의 뜻**: 감싸는 `x-data`가 그 컴포넌트인 요소(같은 템플릿)의 Alpine 식에 동작의 이름이 있거나, 그 컴포넌트의 `listens`가 그 동작을 부른다. 붙이지 않은 컴포넌트는 동작마다 따로 알리지 않는다(한 줄로 충분하다).
  - **받는 곳**: `hx-trigger`는 `th:hx-trigger`의 글자 식(`'memo:picked from:window'`)도 읽는다. describe도 같이 고쳤다.
  - **고치는 명령**: C13은 `command`를 비운다. 붙일 템플릿의 줄을 cozy가 정할 수 없어서다. 메시지 안에 `cozy attach … --at <템플릿>:<줄>`, `--listen`, `cozy remove behavior …`를 적는다.
  - **경고의 자리**: 손으로 쓴 모듈 JS의 경고는 C12의 경고로 냈다(명세와 JS의 관계를 보는 검사라서). `mvn verify`는 경고를 표준 출력에 보고만 한다.
  - **옮기는 범위**(정할 것 2의 "옮길 수 있는 것"): 옛 `cozy add behavior`의 모양(`alpine:init` 안의 `Alpine.data` 등록, `var root = null`, `return { … }`)에 채운 상태, getter, 메서드 가운데 낱말과 뜻이 같은 JS만 읽는다. 한 컴포넌트라도 낱말 밖이면 그 모듈은 바꾸지 않는다(한 모듈에 손으로 쓴 JS와 만든 JS를 함께 둘 수 없다). `else`는 앞의 `if`가 조건의 상태를 바꾸면 뜻이 달라져 옮기지 않는다. `parseInt`로 읽던 `data-*`는 만든 JS가 `parseFloat`로 읽는다(cli.md에 적었다).
  - **2.1.0까지의 reference-app `memoBodyCount`**: 시작할 때 화면의 textarea를 읽어(`querySelector`) 옮기지 못한다. M46에서 손으로 바꾼 일(서버가 `data-length`로 넘김)이 그대로 필요하다. 이 모양을 테스트로 남겼다.
  - **레시피 픽스처 `behavior`**: 선언 뒤의 `cozy check`가 C13으로 실패하도록 순서를 바꿨다. 붙이지 않는 note의 컴포넌트는 마지막에 `cozy remove behavior`로 지운다.
- 확인:
  - `BehaviorWiringTest` 9개(cozy-rules): 붙인 곳과 부르는 곳, 붙이지 않음(명세의 줄, 다른 모듈의 템플릿은 세고 프레임워크 템플릿은 세지 않음), 부르지 않는 동작(받는 화면 이벤트는 부르는 것, 글자와 다른 객체의 속성은 아님), 컴포넌트 밖의 요소, 받는 곳(`hx-trigger`, `th:hx-trigger`, `data-hx-trigger`의 거르기, `x-on`, 다른 컴포넌트의 `listens`), 내는 곳(서버의 JSON 글자, 이어 붙인 이름은 실패, `getHeader`는 아님), 손으로 쓴 JS와 프레임워크 JS는 보지 않음, `ServerEvents`.
  - `BehaviorRulesTest`: 손으로 쓴 모듈 JS의 경고, 동작과 이벤트가 없는 이름 줄은 다시 만들게 함, 이름 줄의 형식.
  - `MigrateBehaviorsTest` 7개: 옛 모양의 컴포넌트 하나가 명세(상태, `data-*`, 계산, 동작, `if`, `$dispatch`, `listens`)로 옮겨지고 다시 실행하면 바뀐 것 없음, 낱말 밖인 모듈은 그대로 두고 `파일:줄`과 JS를 알림(옛 reference-app의 글자 수), 낱말 밖의 모양 10가지(느슨한 비교 `==` 포함), `||` 뒤의 값이 처음 값, 파일의 모양과 목적(`--purpose`), 명세의 같은 이름, 할 일이 없을 때.
  - `DescribeBehaviorsTest`에 C13의 `slots[]` 1개. `BehaviorRecipeTest`, `AttachCommandTest`는 선언 뒤 C13, 붙인 뒤 통과로 바꿨다.
  - 위반 픽스처 `C13-behaviors`(4곳), 레시피 픽스처 `behavior`(순서), `migrate-behaviors`(새 픽스처: 경고 → 옮기기 → 다시 실행 → 경고 없이 통과, mvn verify의 렌더링과 C10). 아키텍처 검사가 하나 늘어 테스트 수를 세는 위반·레시피 픽스처의 기대 값을 1씩 올렸다.
  - 화면(runtime-verifier, 2026-11-18, reference-app의 복사본에 붙이지 않은 컴포넌트를 하나 더해): 워크벤치 개요의 미구현 항목(숫자 카드 `1건`, 메시지와 `memo.yml:14`), 검사 화면(파일별 묶음, `오류 C13`, 수정 명령은 "직접 수정"), 모듈 화면의 미구현 항목, 위반이 없을 때 검사 화면의 새 문구. 1280·390px에서 가로 넘침, 콘솔 오류, CSP 위반 0. 390px의 검사 표에서 내용 칸이 좁아 긴 메시지가 짧게 끊기는 것은 이전부터의 표 모양이다.
  - 전체 `mvn -o install` 통과(10분 58초): 위반 픽스처 18개, 레시피 픽스처 12개, 아키타입 3조합.

## M50 (2026-11-18, 사용자 지시 "진행해줘")

- 시작할 때 최신 GA: 같은 날 확인한 그대로다. 새 의존성은 없다.
- 완료 기준을 테스트로: cozy-cli `BehaviorDocsTest` 6개. 이 테스트가 다음을 본다.
  - `ui.md`의 동작 명세와 `add-behavior` 플레이북의 Syntax가 JS를 만드는 코드의 낱말 목록(계산 함수, 동작 단계)과 이벤트 값, 상태의 종류를 빠짐없이 싣는다.
  - `cli.md`에 동작 명령마다 절이 있고, describe가 `behaviors[]`·`bindings[]`·`uiEvents[]`·C13의 `slots[]`를 적는다.
  - `testing.md`·`rules.md`·`workbench.md`·`check` 플레이북이 C12·C13과 경고를 적는다.
  - `release.md`에 "앱을 2.2.0으로 올리기"가 있고, `upgrade` 플레이북이 `cozy migrate behaviors`를 적는다.
  - `AGENTS.md`가 명령, JS를 손으로 쓰지 않는 규칙, C13을 적는다.
- 고친 문서:
  - 규약: `ui.md`(붙이기와 완결성, 2.2.0 전의 손으로 쓴 모듈 JS), `structure.md`(`behaviors/{모듈}.yml`의 자리), `release.md`(2.2.0 절과 앱을 올리는 절차), `distribution/release/channel.yml`(다음 릴리스 2.2.0의 요약과 `appSteps`).
  - M46~M49에서 함께 쓴 문서: `cli.md`의 명령, `rules.md`·`testing.md`의 검사, `workbench.md`.
  - 에이전트 문서: `AGENTS.md`(선언과 붙이기를 한 작업으로, C12 경고와 `cozy migrate behaviors`), `describe` 플레이북(`behaviors[]`, `bindings[]`, `uiEvents[]`, 화면 안의 동작과 화면 이벤트 줄, C13의 `slots[]`), `add-behavior`(붙이기까지의 예, 화면 이벤트를 htmx와 서버에 잇는 법), `attach`·`sync-behaviors`·`check`·`upgrade`의 실패 표와 다음 일.
- 계획과 달라진 것:
  - **`add-behavior` 다시 쓰기와 `attach` 플레이북**: M46·M47에서 새 형식으로 이미 썼다. 이번에는 C13과 붙이기까지의 흐름을 더했다.
  - **릴리스 노트**: 릴리스 노트 초안(`release-notes.sh`)의 "변경 내용"은 게시 전에 손으로 채운다. 그래서 옮기는 절차는 2.0.0 때처럼 `release.md`의 "앱을 2.2.0으로 올리기"에 두고, 절차가 그 절을 노트에 옮기라고 적는다. 2.2.0은 호환이 깨지지 않는 판이다(손으로 쓴 모듈 JS는 경고). 그래도 R16이 같은 템플릿 안의 `x-data`를 요구하게 된 것(M47)은 올릴 때 손이 갈 수 있어 그 절에 적었다.
  - **`channel.yml`**: 절차는 릴리스 때 고치라고 하지만 이 판의 `appSteps`(`cozy sync agents`, `cozy migrate behaviors`)를 정한 지금 적었다. 판 확인은 태그 빌드에서만 한다.
  - **`AGENTS.md`의 길이**: 늘 읽히는 파일이라 40줄 이하다(`AgentDocsTest`). 붙이기에 관한 두 줄을 한 줄로 합쳐 새 규칙 두 줄을 넣었다.
- 확인: `BehaviorDocsTest` 6개, `AgentDocsTest`·`SyncAgentsTest`, 전체 `mvn -o install` 통과(10분 31초, 위반 픽스처 18개, 레시피 픽스처 12개, 아키타입 3조합). 화면은 바꾸지 않았다.
