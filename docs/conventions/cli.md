# cozy 명령 (cozy-cli)

앱의 구조를 보여 주고(`describe`), 컴파일 없이 빠르게 검사하고(`check`), 구조를 만든다(`add`, `link`, `enable`, 목록은 `recipes`). 앱의 에이전트 문서는 `sync agents`로 맞춘다. 결정은 [ADR-0032](../adr/0032-agent-cli.md)와 2단계 레시피의 [ADR-0033](../adr/0033-stage-two-recipes.md)에 있다. 구조는 명령이 만들고, 에이전트는 명령이 남긴 빈칸만 채운다.

- **지금 쓸 수 있는 것**: `describe`, `check`, `recipes`, 1단계 레시피 `add module|crud|screen|fragment|behavior|event|listener|migration`, `link`, 2단계 레시피 `add dto|query|command|event-version|field`, `enable events|workbench`, `sync agents|behaviors`, `attach`, `remove behavior`, `rename behavior`, `migrate alpine|events|behaviors`, `channel set|show|add|release`, `upgrade`(`--check`, 별칭 `update`)
- **실행**: 오프라인 번들을 설치한 PC에서는 `cozy`(Windows는 `cozy.cmd`)가 `describe`, `check`, `recipes`, `add`, `link`, `enable`, `sync`, `attach`, `remove`, `rename` 등을 `java -jar <설치 폴더>/lib/cli/cozy-cli.jar`로 넘긴다(M11). 저장소는 `COZY_REPOSITORY`가 없으면 사용자 저장소(Windows `%LOCALAPPDATA%\Cozy\repository`, Linux `~/.local/share/cozy/repository`)와 `<설치 폴더>/repository`이고, 이 순서로 찾는다. 네트워크를 쓰지 않는다. 앱의 `AGENTS.md`는 개요만 싣고, 작업 순서와 명령별 사용법은 `cozy-helper` 스킬(`.agents/skills/cozy-helper/`)에 있다(ADR-0034).
- **레포에서 실행**: jar로 실행한다.

```bash
cd my-app && cozy add module voc                    # 번들을 설치한 PC
mvn -o package -pl cozy-cli                         # 레포: target/cozy-cli-*.jar + target/lib/
cd my-app && java -jar <레포>/cozy-cli/target/cozy-cli-1.1.0-SNAPSHOT.jar check
```

- **`--ui <jar나 폴더>`**: `check`, 아이콘을 확인하는 레시피, 프레임워크 컴포넌트를 붙이는 `attach`는 cozy-ui의 프레임워크 자원(템플릿, 매니페스트, 아이콘, 프레임워크 컴포넌트의 명세)을 읽는다. 기본은 `--repository`에서 앱의 cozy 버전으로 jar를 찾는다. `--ui`를 주면 저장소를 찾지 않고 그 jar나 풀어 놓은 폴더(`target/classes`)를 읽는다. 워크벤치가 실행 중인 cozy-ui를 넘기는 데 쓴다. 없는 경로면 종료 코드 2다.
- **JDK**: Java 소스를 JDK의 javac 파서로 읽으므로 JRE가 아니라 JDK 25로 실행한다. 번들 명령은 `java`가 25 이상인지 확인한다.

## 형식

`cozy help`가 보이는 도움말과 같은 영어 형식이다. 옵션과 출력은 아래 절에 있다.

```text
cozy <action> [options]

  describe [--json]                  Show the app map: modules, dependencies, spi contracts, events, screens, behaviors, public fragments, open slots
  check [--json]                     Run the fast checks: templates R1-R16, modules C8, slots C9, behavior specs C12, screen behaviors C13
  recipes [--json]                   List the commands that create structure
  add <recipe> …                     Create structure [--dry-run] [--json]
  link <module> --uses <module>[,…]  Declare a module dependency [--dry-run] [--json]
  sync agents [--force] | sync behaviors   Update the agent docs, regenerate module scripts from behavior specs [--dry-run] [--json]
  set arg <template> --line … --fragment … --index … --from … --value …   Change one string argument of a fragment call [--dry-run] [--json]
  migrate alpine                     Replace removed data-cozy-* attributes with cozy components [--dry-run] [--json]
  migrate events                     Move events from {module}/spi/event/ into the events module [--dry-run] [--json]
  migrate behaviors [--purpose <component>=<purpose> …]   Move hand-written module scripts into behavior specs [--dry-run] [--json]
  attach <component> --at <template>:<line> …   Attach a component to one element of a template, or detach it [--dry-run] [--json]
  remove behavior <module> <behavior> […] | rename behavior <module> <behavior> <new-behavior>   Remove or rename a behavior where it is attached too [--dry-run] [--json]
  channel set|show|add|release …     Set the update channel, or build one (admin, release workflow)
  upgrade --check [--to <version>] [--json] | upgrade [--to <version>]   Show newer releases, or install one
```

| 옵션 | 기본값 | 뜻 |
| --- | --- | --- |
| `--project <폴더>` | 현재 폴더 | 앱 폴더. 위로 올라가며 `pom.xml`과 `src/main/java`가 함께 있는 폴더를 찾는다 |
| `--repository <폴더>` | `COZY_REPOSITORY`, 없으면 `~/.m2/repository` | cozy jar가 있는 Maven 저장소(저장소 배치). `check`가 앱이 쓰는 판의 cozy-ui jar를 여기서 찾는다. `COZY_REPOSITORY`에는 경로 구분자(Windows `;`, 그 밖 `:`)로 여러 저장소를 적고, 앞에서부터 찾는다 |
| `--json` | 끔 | 기계가 읽는 출력 (아래 형식) |
| `--dry-run` | 끔 | (`add`, `link`, `enable`, `sync`, `set`, `migrate`, `attach`, `remove`, `rename`) 계획만 보이고 파일을 쓰지 않는다 |

| 종료 코드 | 뜻 |
| --- | --- |
| 0 | 이상 없음 |
| 1 | `check`에서 오류가 하나 이상 있음 |
| 2 | 사용법 오류이거나 판단할 수 없음. 앱을 찾지 못함, cozy-ui jar가 없음, 아직 없는 동작, 레시피를 적용할 수 없음(이미 다른 내용이 있음, 순환, 없는 이벤트 등). 이유는 표준 오류에 한 줄로 쓴다. 레시피가 실패하면 아무 파일도 바뀌지 않는다 |

## describe

소스와 자원 파일을 읽어 앱의 구조를 짧게 보여 준다. 작은 모델이 파일을 다 읽지 않고도 무엇이 어디 있는지 알게 하려는 것이다. reference-app의 결과가 [기대 파일](../../cozy-cli/src/test/resources/expected/reference-app.describe.txt)이다.

```text
모듈 memo (io.salvia95.cozy.reference.memo)
  의존: 없음
  쓰기 MemoFacade
    long create(String title, String body, boolean pinned)
  읽기 MemoFinder
    Optional<MemoItem> find(long id)
  DTO MemoItem(long id, String title, String body, boolean pinned, Instant createdAt, Instant updatedAt)
  화면 안의 동작 memoBodyCount — 본문을 쓰는 동안 글자 수와 한도를 보인다 · 붙인 곳 memo/internal/fields:7, memo/internal/fields:9
  화면 memo/api/list — src/main/resources/specs/memo-list.yml: default, empty, noResults, paged
  공개 fragment memo/summary: memoSummary(summary)

이벤트 memo.MemoCreated(long memoId, String title) 발행: src/main/java/.../MemoFacadeImpl.java → activity(MemoCreatedListener)

화면 이벤트 memo:archived 내는 곳: src/main/java/.../MemoController.java → 받는 곳: memoSelection.clear, memo/api/list:12(htmx)
```

`--json`의 키는 다음과 같다.

| 키 | 값 |
| --- | --- |
| `app` | `basePackage`, `mainClass`, `cozyVersion`(cozy-parent 버전), `starters`(pom에 직접 적은 cozy 의존. 간접 의존은 빠진다) |
| `modules[]` | `name`, `package`, `dependsOn`(선언한 의존. 선언하지 않았으면 `null` = open), `facades[]`·`finders[]`(`type`, `methods[]` 시그니처), `dtos[]`(`type`, `fields[]`), `listeners[]`(`type`, `method`, `on` = `모듈.이벤트`), `controllers[]`, `components[]`(모듈이 `static/{모듈}/{모듈}.js`에 등록한 Alpine 컴포넌트), `behaviors[]`(동작 명세의 컴포넌트. 아래 "화면 안의 동작"), `screens[]`(`template`, `spec`, `states[]`, `components[]`: 화면이 쓰는 Alpine 컴포넌트. 아래 "화면의 컴포넌트". `bindings[]`: 화면의 요소마다 붙은 Alpine·htmx 속성, `outline[]`: 화면의 구조), `fragments[]`(공개 fragment `id`, `signature`) |
| `events[]` | 앱의 이벤트(모듈 순서): `type`, `owner`(주인 모듈), `fields[]`(`eventId`·`schemaVersion` 제외), `publishedIn[]`(발행하는 파일), `listeners[]`(`module`, `type` 받는 리스너 클래스). 이벤트 계약 모듈과 옛 자리(`{주인}/spi/event/`)의 이벤트를 모두 싣는다 |
| `uiEvents[]` | 화면 이벤트(`모듈:이름`, 이름 순서): `event`, `module`, `emittedBy[]`(내는 곳), `receivedBy[]`(받는 곳). 아래 "화면 이벤트" |
| `slots[]` | 남은 일: `check`(`C9` 빈칸, `C13` 이어지지 않은 화면 안의 동작), `file`, `line`, `slot`(빈칸의 id, C13은 `memoPanel`·`memoSelection.drop`·`memoSelection → memo:picked` 같은 대상), `what` |
| `problems[]` | 읽지 못한 것(아래 "판단할 수 없는 것") |

- **이벤트**: 모듈 항목이 아니라 앱의 `events[]`에 한 번씩 싣는다. 주인은 이벤트 계약 모듈의 폴더 이름이고(옛 자리 `{모듈}/spi/event/`에 남은 이벤트면 그 모듈), 주인 폴더에 맞는 모듈이 없는 이벤트는 싣지 않는다(C8이 알린다). 리스너의 `on`도 `주인.이벤트`다. 글 출력은 모듈 다음에 `이벤트 memo.MemoCreated(long memoId, String title) 발행: … → activity(MemoCreatedListener)`이다.
- **발행하는 파일**: 소스에서 `이벤트.of(`나 `new 이벤트(`를 찾는다. 이 목록이 비면 아직 발행하지 않는 이벤트다.
- **모듈 판정**: Spring Modulith와 같다. 메인 클래스 패키지 바로 아래 패키지가 모듈이고, `@ApplicationModule(id)`가 있으면 그 이름을 쓴다. 의존 선언(`allowedDependencies`)이 없으면 open이다.

### 화면의 컴포넌트 (`screens[].components`)

화면이 쓰는 `x-data`를 쓴 곳과 함께 문서 순서로 싣는다. 항목은 `name`(컴포넌트), `template`(`x-data`를 쓴 템플릿), `line`(그 줄)이다. 글 출력은 `· 컴포넌트 memoBodyCount(memo/internal/fields:6)`다.

- 화면 템플릿에서 시작해, 앱 템플릿을 넣는 `th:replace`·`th:insert`를 따라간다. 같은 파일 안의 fragment(`~{::memoHint}`)도 따라간다.
- fragment 이름이 있으면 그 선언의 줄 범위만 보고, 없으면(`~{memo/internal/panel}`) 템플릿 전체를 본다. 그 안의 삽입도 다시 따라간다.
- 같은 fragment를 두 번 넣어도 한 번만 싣고, 서로 넣는 fragment에서 멈춘다.
- 프레임워크 fragment(`cozy/…`)는 따라가지 않는다. `form-page`의 `cozyDirtyForm`처럼 패턴이 붙이는 것은 [ui.md](ui.md)에 있다. 없는 템플릿도 따라가지 않는다(`cozy check`가 알린다). 주석 안은 보지 않는다.

### 화면 안의 동작 (`modules[].behaviors[]`)

모듈의 동작 명세(`behaviors/{모듈}.yml`, [ui.md](ui.md)의 동작 명세)를 컴포넌트마다 싣는다. 글 출력은 `화면 안의 동작 memoBodyCount — 목적 · 붙인 곳 memo/internal/fields:7, …`(붙인 곳이 없으면 `없음`)이다.

| 키 | 값 |
| --- | --- |
| `name`, `purpose` | 등록 이름(`memoBodyCount`), 목적 한 문장 |
| `state[]` | `name`, `type`(`bool`, `number`, `text`, `list`), `initial`(적은 처음 값. 없으면 `null`), `from`(읽는 `data-*`. 없으면 `null`) |
| `computed[]` | `name`, `expression`(cozy가 정리한 식) |
| `actions[]` | `name`, `steps[]`(단계마다 정리한 식) |
| `emits[]` | `event`, `action`: 그 동작의 단계에 있는 `emit('모듈:이름', …)`(`when`, `within` 안도) |
| `listens[]` | `event`, `action` |
| `attached[]` | 붙인 곳: 모듈 템플릿(`templates/{모듈}/…`)에서 이 컴포넌트가 감싸는 요소 가운데 Alpine 속성이 있는 것. 모양은 아래 `bindings[]`의 항목과 같다(`screenLine` 없음) |

- 명세를 읽을 수 없으면(형식 오류) 이 목록은 비고 이유가 `problems[]`에 들어간다. describe는 실패하지 않는다(`cozy check`의 C12가 알린다).

### 화면의 요소에 붙은 것 (`screens[].bindings[]`)

화면에 보이는 요소 가운데 Alpine 속성이나 htmx 속성이 붙은 것을, `components[]`와 같은 길(화면 템플릿과 넣은 앱 fragment)로 문서 순서대로 싣는다. 워크벤치의 구조 트리와 속성 표가 이것을 쓴다.

| 키 | 값 |
| --- | --- |
| `template`, `line` | 요소를 쓴 템플릿과 여는 태그의 줄. `cozy attach --at`에 그대로 쓴다 |
| `screenLine` | 화면 템플릿에서 이 요소가 보이는 줄. 화면 템플릿의 요소면 `line`과 같고, 넣은 템플릿의 요소면 화면 템플릿에서 넣은 줄이다 |
| `element` | 알아볼 여는 태그: 이름, `id`, `class`(`<div class="form-hint">`) |
| `component` | 감싸는 컴포넌트(같은 템플릿 안에서 가장 가까운 `x-data`의 이름). 없으면 `null` |
| `root` | 이 요소에 `x-data`가 있는가 |
| `alpine[]` | `attribute`, `value`(값이 없는 `x-cloak`이면 `null`): `x-*`, `@*`, `:*` 속성. `root`면 `data-*`, `th:data-*`도 |
| `htmx[]` | `attribute`, `value`: `hx-*`, `th:hx-*`, `data-hx-*` 속성. 읽어서 보이기만 한다(붙이는 명령은 없다) |

### 화면 이벤트 (`uiEvents[]`)

이름의 앞이 앱 모듈인 화면 이벤트(`memo:archived`)마다 내는 곳과 받는 곳을 잇는다. 앱 이벤트(`events[]`, `CozyEvent`)와는 따로다.

| `kind` | 어디서 읽는가 | 키 |
| --- | --- | --- |
| `behavior` | 내는 곳: 동작 명세의 `emit`. 받는 곳: `listens` | `module`, `component`, `action` |
| `server` | 내는 곳: 응답 머리 `"HX-Trigger"`(`-After-Swap`, `-After-Settle` 포함)를 요청 머리 읽기(`getHeader`) 밖에서 쓰는 Java 파일의 글자들에 든 이벤트 이름(`"{\"memo:archived\": true}"`) | `file` |
| `htmx` | 받는 곳: 템플릿의 `hx-trigger`(쉼표로 나눈 각 트리거의 첫 낱말) | `template`, `line` |
| `alpine` | 받는 곳: 템플릿의 `x-on:모듈:이름`(수식어 앞까지) | `template`, `line` |

- 글 출력은 `화면 이벤트 memo:archived 내는 곳: … → 받는 곳: memoSelection.clear, memo/api/list:12(htmx)`이고, 없으면 `없음`이다.
- 서버 쪽은 글자를 찾을 뿐이다. 그 파일에서 다른 뜻으로 쓴 같은 모양의 글자도 내는 곳으로 읽고, 글자를 이어 붙여 만든 이름은 찾지 못한다. 찾지 못한 이름을 받는 `listens`는 C13이 실패로 닫는다(cozy-rules `ServerEvents`, [testing.md](testing.md)).

### 화면의 구조 (`--json`의 `screens[].outline`)

화면 템플릿마다 fragment 선언과 삽입을 문서 순서대로 싣는다. 글 출력에는 없다. 워크벤치의 구조 트리가 이것을 쓴다.

| 키 | 뜻 |
| --- | --- |
| `line` | 요소가 시작하는 줄 (1부터) |
| `endLine` | 요소가 끝나는 줄. 닫는 태그가 없으면 `line`과 같다 |
| `depth` | 이 마디를 감싸는 마디의 수 |
| `kind` | `replace`, `insert`, `fragment`(선언), `slot`(감싸는 삽입이 `~{::#id}`로 넘기는 요소) |
| `target` | 삽입하는 템플릿. 같은 템플릿이면 빈 문자열, 읽을 수 없는 표현식이면 원문 |
| `fragment` | fragment 이름. 템플릿 전체를 넣거나 읽을 수 없으면 `null` |
| `id` | 요소의 `id`. 없으면 `null` |
| `args` | 선언이면 파라미터 이름, 삽입이면 넘기는 인자의 원문 |

- 한 요소가 자리이면서 선언이나 삽입이면 마디가 여럿 나오고, 뒤의 것이 한 단계 깊다.
- 클래스나 요소 이름으로 넘기는 인자(`~{::.row}`, `~{::main}`)는 자리로 읽지 않는다.

## check

`mvn verify` 전에 몇 초 안에 끝나는 검사다. 판단은 cozy-test와 같은 cozy-rules가 한다. 그래서 같은 위반은 같은 메시지로 나온다.

| 검사 | 입력 | cozy-test와 다른 점 |
| --- | --- | --- |
| C2 (R1~R16) | 앱 템플릿, cozy-ui jar의 템플릿·매니페스트·아이콘, 앱 매니페스트, `cozy.rules.allow-same-layer` | 없음 |
| C8 | Java 소스(javac 파서). 이름은 import, 같은 패키지, 와일드카드 import로 푼다 | 메타 애너테이션을 모른다. 알려진 스프링 애너테이션(`@Controller`, `@RestController`, `@Component`, `@Service`, `@Repository`, `@Configuration`, `@ControllerAdvice`)만 본다 |
| C9 | `src/main` 아래 모든 파일 | 없음 |
| C12 | 동작 명세와 만든 JS(`src/main/resources`) | 없음 |
| C13 | 만든 JS의 `cozy:component` 줄, 앱 템플릿, `src/main/java`의 `HX-Trigger` | 없음 |

- **하지 않는 검사**: C1(Modulith), C3~C5(렌더링·스펙), C6(이벤트 호환), C7(뷰 이름)은 앱을 컴파일하고 띄워야 한다. 이것은 `mvn verify`가 한다. `check`가 통과해도 `mvn verify`는 실패할 수 있다.
- **cozy-ui jar**: 앱이 cozy-ui를 쓰면 `--repository`에서 `io/salvia95/cozy/cozy-ui/{cozy-parent 버전}/`의 jar를 찾는다. jar는 zip으로만 읽고 클래스는 쓰지 않는다. jar가 없으면 프레임워크 템플릿 없이 판단할 수 없으므로 종료 코드 2로 끝난다(앱 폴더에서 `mvn -o dependency:resolve`를 한 번 실행하면 생긴다).

글 출력은 위반마다 `Violation.format()` 한 줄이고, 마지막 줄이 요약이다.

```text
[C8] it.cozy.app.memo.MemoService — 모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다. ...
[cozy check] 오류 7건 (R1~R16, C8, C9, C12, C13). 컴파일, 렌더링, 스크립트 검사(C1, C3~C7, C10)는 mvn verify가 합니다.
```

`--json`은 다음과 같다.

```json
{
  "ok": false,
  "errors": 7,
  "warnings": 0,
  "violations": [
    {
      "id": "C8",
      "severity": "error",
      "location": "it.cozy.app.memo.MemoService",
      "file": "src/main/java/it/cozy/app/memo/MemoService.java",
      "line": 0,
      "target": null,
      "message": "모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다. ...",
      "command": null,
      "text": "[C8] it.cozy.app.memo.MemoService — ..."
    }
  ]
}
```

| 키 | 값 |
| --- | --- |
| `id` | `R1`~`R16`, `C8`, `C9`, `C12`, `C13`, `CLI`(읽지 못한 것) |
| `severity` | `error` 또는 `warning`(`th:include`의 R7, 손으로 쓴 모듈 JS의 C12). 경고는 종료 코드에 영향을 주지 않는다 |
| `location` | 메시지의 위치: 템플릿 경로, 소스 경로, C8이면 타입·패키지 이름 |
| `file` | 고칠 파일의 프로젝트 기준 경로. C8의 타입은 그 소스 파일, 패키지는 그 폴더. 프레임워크 jar 안이면 `null` |
| `line` | 줄 번호. 모르면 0 |
| `target` | 문제가 된 참조. 위치 자체가 문제면 `null` |
| `command` | 이 위반을 고치는 cozy 명령. 고치는 레시피가 없으면 `null`. 아래 표 |
| `text` | 글 출력과 같은 한 줄 |

### 고치는 명령

위반을 고치는 레시피가 있으면 위반에 명령을 붙인다(ADR-0032 §6). 글 출력은 메시지 끝에 `고치는 명령: …`을 붙이고, `--json`은 `command`에 넣는다. 에이전트는 그 명령을 그대로 실행한다. `mvn verify`의 같은 검사도 같은 명령을 낸다(cozy-rules의 `Violation.command`).

| 위반 | 명령 | 어디서 |
| --- | --- | --- |
| R3: 다른 모듈의 spi 템플릿을 참조하는데 allowedDependencies에 그 모듈이 없다 | `cozy link {내 모듈} --uses {그 모듈}` | `cozy check`, `mvn verify`(C2) |
| C1: 다른 모듈의 spi 클래스를 쓰는데 선언하지 않았다(Modulith의 `depends on named interface(s) 'b :: spi'`) | `cozy link {내 모듈} --uses {그 모듈}` | `mvn verify`만 (컴파일이 필요하다) |

```text
[R3] templates/voc/api/list.html:1 → wiki/spi/card :: card(1개 인자) — voc의 allowedDependencies에 wiki가 없습니다. voc/package-info.java의 @ApplicationModule(allowedDependencies = "wiki :: spi")에 추가하세요. 고치는 명령: cozy link voc --uses wiki
```

- 다른 모듈의 internal이나 api를 쓴 위반은 명령이 없다. 의존을 선언해도 고쳐지지 않기 때문이다. spi를 쓰도록 바꾼다.
- 명령을 실행해도 실패할 수 있다(순환, 그 모듈에 spi 없음). 그때는 `cozy link`의 실패 이유를 따른다.

## 판단할 수 없는 것

추측하지 않고 `CLI` 오류로 알린다. `check`는 실패하고 `describe`는 `problems`에 적는다.

| 경우 | 메시지 |
| --- | --- |
| Java 문법 오류 | `[CLI] src/main/java/.../Broken.java:4 — Java 문법 오류: ...` |
| `allowedDependencies`가 글자 그대로의 문자열이 아님(상수, 식) | `allowedDependencies를 읽을 수 없습니다. 글자 그대로의 문자열로 적으세요 ...` |
| `@SpringBootApplication` 클래스가 없거나 둘 이상 | `@SpringBootApplication 클래스를 찾지 못했습니다 ...` |
| 매니페스트 형식 오류 | `[R8] src/main/resources/cozy/fragments.yml — fragment 매니페스트 오류 ...` |

## recipes

구조를 만드는 명령의 목록이다. 1단계는 [ADR-0032 부록](../adr/0032-agent-cli-recipes.md), 2단계는 [ADR-0033](../adr/0033-stage-two-recipes.md)과 같다. 글 출력은 영어이고 쓸 수 있는 레시피를 먼저, 준비 중인 레시피(`Not ready yet`)를 뒤에 싣는다. 글 출력 머리에 스킬의 플레이북 위치를 적는다. 레시피마다 `references/<플레이북>/PLAYBOOK.md`가 있고, 그 `## Syntax`가 `usage`와 글자까지 같다(`AgentDocsTest`가 확인한다). `--json`은 레시피마다 `name`, `stage`(1 또는 2), `usage`, `does`, `slots`, `available`, `command`, `params`를 낸다. `usage`(명령 형식), `does`(하는 일), `slots`(빈칸. 없으면 `none`으로 시작한다)는 영어이고, 워크벤치 화면의 말인 `title`, `group`, `params[].label`은 한국어다.

- **`title`, `group`**: 사람에게 보일 제목(`CRUD 화면`)과 분류(`화면`)다. 워크벤치가 생성 항목 목록에 쓴다. 최상위 `groups`는 분류를 표시할 순서다(화면, 모듈, 비즈니스 로직, 이벤트, 데이터베이스, 설정). 글 출력에는 없다.
- **`command`**: `cozy` 뒤에 오는 동작이다(`["add", "crud"]`, `["link"]`, `["enable", "events"]`).
- **`params`**: 인자의 구조다. `usage`와 같은 순서이고, 워크벤치가 폼을 만드는 데 쓴다. `CozyCliTest`가 `usage`와 어긋나지 않는지 본다.

  | 키 | 뜻 |
  | --- | --- |
  | `name` | 옵션이면 `--` 뒤의 이름, 위치 인자면 자리 이름(`모듈`) |
  | `label` | 사람에게 보일 이름과 형식 |
  | `option` | 옵션(`--이름 값`)이면 `true`, 위치 인자면 `false` |
  | `required` | 꼭 있어야 하는가 |
  | `repeatable` | 여러 번 쓸 수 있는가 (`--field`, `--param`) |
  | `choices` | 고를 수 있는 값. 자유롭게 적는 값이면 빈 목록 |
  | `example` | 예시 값 |
  | `choiceLabels` | 고를 값의 사람이 읽는 이름(`{"text": "한 줄 텍스트"}`). 없으면 빈 객체 |
  | `from` | 앱에서 가져오는 선택지의 출처(아래 `sources`). 없으면 `null` |
  | `strict` | `from`의 값만 받는가. `false`면 앱의 값은 제안이고 새 값도 받는다(`add crud`·`add screen`의 모듈은 없으면 만든다). `from`이 없으면 `false` |
  | `separator` | 값 하나에 여럿을 고를 때 사이에 넣는 글자(`--uses memo,activity`, `--publish created,deleted`). 하나만 고르면 `null` |
  | `parts` | 값의 구성(아래). 통째로 적는 값이면 빈 목록. 여러 번 쓰는 옵션은 모두 구성이 있다 |

- **`parts`**: `--field 이름:종류[!][:라벨]`처럼 칸 여럿을 이은 값의 구성 요소다. 워크벤치는 구성 요소마다 그리드의 열을 만들고, 행 하나를 값 하나로 잇는다.

  | 키 | 뜻 |
  | --- | --- |
  | `name`, `label` | 구성 요소의 이름(`kind`)과 사람에게 보일 이름(`종류`) |
  | `required` | 꼭 있어야 하는가. 선택 요소는 끝에만 온다 |
  | `choices`, `choiceLabels` | 고를 값과 그 이름. 자유롭게 적으면 빈 목록 |
  | `from` | 앱에서 가져오는 선택지나 제안하는 낱말 |
  | `strict` | `from`의 값만 받는가. `false`면 그 값은 제안이고 다른 글도 받는다(동작의 상태 종류, 식, 단계). `from`이 없으면 `false` |
  | `flag` | 켜면 앞 요소 바로 뒤에 붙는 글자(`!`). 플래그가 아니면 `null` |
  | `example` | 예시 값. 플래그는 켜면 그 글자, 끄면 빈 글자 |

  - **잇는 규칙**: 구성 요소를 순서대로 `:`로 잇는다. 플래그는 `:` 없이 앞 요소에 붙인다. 비어 있는 끝의 선택 요소는 뺀다. `{title, text, !, 제목}`은 `title:text!:제목`, `{done, bool, (끔), (빈 라벨)}`은 `done:bool`이다.
  - 구성 요소의 예시를 이으면 `example`이 된다(`CozyCliTest`가 확인한다).
- **`sources`**: 최상위 객체다. `from`이 가리킬 수 있는 출처와 그 뜻이다. "선택한 모듈"은 같은 레시피의 위치 인자 `모듈`이다. 값은 워크벤치가 `cozy describe --json`과 cozy-ui에서 얻는다.

  | 출처 | 값 | `describe`에서 |
  | --- | --- | --- |
  | `modules` | 앱의 모듈 | `modules[].name` |
  | `entities` | 선택한 모듈의 CRUD 엔티티 | `E`: `{E}Facade`, `{E}Finder`, DTO `{E}Item`, `{E}Controller`가 모두 있다 |
  | `events` | 선택한 모듈의 이벤트 | `events[]` 가운데 `owner`가 그 모듈인 것의 `type` |
  | `app-events` | 앱의 모든 이벤트 `모듈.이벤트` | `events[]`의 `owner.type` |
  | `finders`, `facades` | 선택한 모듈의 조회·명령 인터페이스 | `modules[].finders[].type`, `facades[].type` |
  | `dtos` | 선택한 모듈의 DTO와 그 목록 `Record`, `list:Record` | `modules[].dtos[].type` |
  | `icons` | Tabler Icons의 외곽선 아이콘 이름 | (cozy-ui의 스프라이트) |
  | `behavior-types` | 상태의 종류 | (`vocabulary`의 `type`) |
  | `behavior-values` | 계산의 함수와 고치는 컴포넌트의 상태·계산 | (`vocabulary`의 `function`, `modules[].behaviors[]`) |
  | `behavior-steps` | 동작의 단계, 이벤트 값과 고치는 컴포넌트의 상태·계산 | (`vocabulary`의 `step`, `event`, `modules[].behaviors[]`) |

- **`vocabulary`**: `behavior` 레시피에만 있다. 동작 명세의 낱말 목록이고 원본은 JS를 만드는 코드의 목록(`BehaviorVocabulary`) 하나다. [ui.md](ui.md)의 동작 명세 표와 같은지 `BehaviorDocsTest`가 본다. 워크벤치가 낱말 표와 칸의 제안에 쓴다.

  | 키 | 뜻 |
  | --- | --- |
  | `kind` | `type`(상태의 종류), `event`(이벤트 값), `function`(계산 함수), `step`(동작 단계) |
  | `name`, `shape` | 이름(`gt`)과 적는 모양(`gt(a, b)`) |
  | `text` | 한 줄 설명 |

## add, link, enable

레시피 하나를 실행한다. 레시피마다 만드는 파일, 빈칸, 실패하는 경우는 [ADR-0032 부록](../adr/0032-agent-cli-recipes.md) §2(1단계)와 [ADR-0033](../adr/0033-stage-two-recipes.md) §5(2단계)에 있다. `field`는 [ADR-0037](../adr/0037-add-field.md)에 있다.

```bash
cozy add crud memo --entity Memo --label 메모 --icon notes \
  --field title:text!:제목 --field body:longtext:본문 --field pinned:bool:고정 --publish created
cozy add module activity --label 활동
cozy add migration activity create_activity
cozy add listener activity --on memo.MemoCreated
cozy add fragment activity recent-activity --param items:list:ActivityItem --purpose "최근 활동 목록"
cozy add behavior memo body-count --purpose "본문을 쓰는 동안 글자 수를 보인다" --state length:number@data-length \
  --computed "label:format('{length}자', 'ko-KR')" --action "count:within('textarea', set(length, length(event.value)))"
cozy add screen dashboard index --pattern overview --title 대시보드 --nav 대시보드:dashboard
cozy link dashboard --uses memo,activity
cozy add event report ReportSent --field reportId:long --field sentOn:date
cozy add dto activity ActivityItem --field "message:text:무엇이 있었는지" --field "at:text:언제 (yyyy-MM-dd HH:mm)"
cozy add query activity recent --param limit:int --returns list:ActivityItem
cozy add command activity record --param eventId:text --param message:text --sql insert
cozy add event-version memo MemoCreated --field pinned:bool
cozy add field memo --entity Memo --field dueOn:date:마감일
cozy add field memo --entity Memo --field code:text!:코드 --default 없음
cozy enable events
cozy enable workbench
```

- **필드**: `이름:종류[!][:라벨]`. 종류는 `text`, `longtext`, `int`, `long`, `bool`, `date`, `instant`이고 `!`는 필수다. 라벨에 공백이 있으면 따옴표로 묶는다(`--field "dueOn:date:마감 날짜"`).
- **라벨은 DTO의 Javadoc에도 남는다**: `add crud`는 화면 DTO record(`Row`, `ListResult`, `Detail`)의 `@param`과 폼·필터 필드 위의 `/** 라벨 */`을 쓴다. `add field`는 폼 필드에 `/** 라벨 */`을 붙이고, `@param` 줄이 있는 record에는 새 구성 요소의 `@param` 줄을 더한다. 워크벤치의 샘플 데이터가 이것을 속성 이름으로 보인다([workbench.md](workbench.md)).
- **dto 필드**: `이름:종류[:설명]`. 종류는 위와 같고, 같은 모듈 `spi/dto`의 record(`이름:Record`)와 그 목록(`이름:list:Record`)도 쓴다. `!`는 받지 않는다. 설명은 record의 `@param`이 된다. 타입은 `text`→`String`, `int`→`int`, `long`→`long`, `bool`→`boolean`, `date`→`LocalDate`, `instant`→`Instant`, `list:R`→`List<R>`이다.
- **이벤트의 자리**: `add event`와 `crud --publish`는 이벤트를 이벤트 계약 모듈의 `events/spi/{주인 모듈}/`에 만든다. `events`의 `package-info.java`(`allowedDependencies = {}`)와 주인 폴더의 `@NamedInterface("spi")`가 없으면 함께 만들고, 주인 모듈에 `"events :: spi"`를 더한다([structure.md](structure.md)의 이벤트 계약 모듈).
  - `add listener`는 받는 모듈에 `"events :: spi"`만 더하고 보내는 모듈에는 의존하지 않는다. 그래서 두 모듈이 서로의 이벤트를 받아도 순환하지 않는다.
  - `event-version`과 `add listener --on`은 이벤트 계약 모듈에서 찾는다. 이벤트가 옛 자리(보내는 모듈의 `spi/event/`)에 있으면 `먼저 cozy migrate events로 … 옮기세요`로 실패한다.
  - `events`는 예약한 이름이다. `add module events`, `add crud events …`, `add event events …`는 실패한다.
- **listener와 Facade**: 리스너는 늘 자기 모듈의 `{M}Facade`를 생성자로 받는다(`activity` → `ActivityFacade`). 없으면 빈 `{M}Facade`와 `internal/app/command/{M}FacadeImpl`을 함께 만든다. 그래서 리스너 파일은 앱 상태와 상관없이 같다(ADR-0033 §5.6). 그 빈 Facade·구현은 나중에 같은 모듈의 `cozy add crud`(엔티티 이름이 모듈 이름과 같을 때)가 채운다(`바꿈 … listener가 만든 빈 Facade를 crud로 채움`). 메서드를 더한 Facade는 덮어쓰지 않고 실패한다.
- **query** (ADR-0033 §5.2): `--returns`는 `long`, `int`, `bool`, `text`, `<Record>`(Finder는 `Optional<Record>`), `list:<Record>`(`List<Record>`)다. 네 곳에 더한다.

  | 곳 | 더하는 것 (예: `recent --param limit:int --returns list:ActivityItem`) |
  | --- | --- |
  | `spi/{X}Finder` | `List<ActivityItem> recent(int limit);` |
  | `internal/app/query/{X}FinderImpl` | `@Override` 메서드. Mapper를 부르고 행을 record로 옮긴다(같은 이름 구성 요소) |
  | `internal/infra/{X}Mapper` | `List<ActivityItemRow> recent(@Param("limit") int limit);` |
  | `mapper/{m}/{X}Mapper.xml` | `<select id="recent" resultType="…ActivityItemRow">`. 본문은 빈칸 `query-sql` |

  - 행 `internal/infra/{Record}Row`는 없으면 record와 같은 구성 요소로 만든다. 있으면 구성 요소 **이름**이 같아야 한다(타입은 에이전트가 저장 형식에 맞춰 바꿀 수 있다). record에 행으로 옮길 수 없는 구성 요소(다른 record, 목록)가 있으면 실패한다.
  - 원시 값을 돌려주면 행을 만들지 않는다(`resultType`은 `long`, `int`, `boolean`, `string`).
  - 출력의 "다음"에 마이그레이션에서 읽은 이 모듈의 테이블과 열을 적는다.
- **command** (ADR-0033 §5.3): Facade 메서드(`--returns`가 없으면 `void`)와 구현을 더한다. 구현 본문은 빈칸 `command-body`다. `--sql insert|update|delete`면 Mapper 메서드(`int`, 영향받은 행 수)와 `<insert>` 등을 더하고, 문의 본문은 빈칸 `command-sql`이다. 앱에 이벤트 스타터가 있는데 구현이 `ApplicationEventPublisher`를 받지 않으면 출력이 알린다.
- **대상 고르기** (ADR-0033 §4): 모듈 `spi`의 `*Finder`(`*Facade`)가 하나면 그것, 둘 이상이면 `--in <이름>`, 없으면 `{M}Finder`와 `internal/app/query/{M}FinderImpl`(`{M}Facade`와 `{M}FacadeImpl`)을 만든다. 구현은 그 인터페이스를 `implements`하는 `app.query`(`app.command`)의 클래스 하나다.
- **Mapper 고르기**: 구현의 `*Mapper` 필드(이 모듈 `internal/infra`의 것) 하나를 쓴다. 구현에 생성자도 필드도 없으면(명령이 만든 빈 구현) `{M}Mapper`를 받는 필드와 생성자를 본문 맨 앞에 더하고, Mapper와 XML이 없으면 만든다(`바꿈 … 생성자 += ActivityMapper`). 이미 생성자가 있는데 Mapper가 없으면 고치지 않고 실패한다.
- **다시 실행** (ADR-0033 §3): 네 곳(command는 두 곳이나 네 곳)의 시그니처를 비교한다. 모두 있으면 바뀐 것 없음(본문과 SQL은 비교하지 않으므로 빈칸을 채운 뒤에도 같다), 모두 없으면 더한다. 일부만 있으면 있는 곳과 없는 곳을 알리고 실패하고, 같은 이름이 다른 시그니처로 있으면 실패한다(오버로드를 만들지 않는다).
- **event-version** (ADR-0033 §5.4): 이벤트 record 끝에 필드를 박싱 타입(`String`, `Integer`, `Long`, `Boolean`, `LocalDate`, `Instant`)으로 더한다. 옛 샘플을 읽으면 그 필드는 `null`이다.
  - `of()`: `return new Ev(UUID.randomUUID(), N, <파라미터>…);` 한 줄인 static `of()`를 하나 찾는다. 그것을 새 필드를 `null`로 넘기는 위임(`return of(memoId, title, null);`)으로 바꾸고, 새 필드를 받고 `N + 1`을 쓰는 `of()`를 더한다. 그래서 발행하는 곳(crud의 `*FacadeImpl` 등)은 그대로 컴파일된다. 이 모양의 `of()`가 하나가 아니면 실패한다.
  - 샘플: 가장 높은 `v{n}.json`에 새 필드의 샘플 값을 더하고 `schemaVersion`을 `n + 1`로 바꿔 `v{n+1}.json`을 만든다. 옛 샘플은 그대로 둔다. `of()`의 버전과 가장 높은 샘플 번호가 다르면 실패한다.
  - 다시 실행: 필드가 모두 같은 타입으로 있고 지금의 `of()`와 가장 높은 샘플이 그 필드를 가지면 바뀐 것 없음이다. 일부만 있거나 같은 이름이 다른 타입이면 실패한다.
  - 출력의 "다음"은 이 이벤트의 리스너(`@ApplicationModuleListener`)를 나열한다. 리스너는 옛 이벤트의 새 필드가 `null`일 때도 처리해야 한다.
  - 이벤트 파일이 바뀌므로 그 뒤에 같은 `cozy add event`(또는 `--publish`가 있는 `crud`)를 다시 실행하면 "내용이 다릅니다"로 실패한다. 빈칸을 채운 뒤의 1단계 명령과 같다.
- **behavior** (ADR-0043): 화면 안의 상태를 맡는 Alpine 컴포넌트 하나를 모듈의 동작 명세 `behaviors/{모듈}.yml`에 선언하거나 고치고, 그 명세에서 `static/{모듈}/{모듈}.js`를 만든다. 형식과 낱말은 [ui.md](ui.md)의 "동작 명세"에 있다.
  - 이름은 소문자와 하이픈(`body-count`)이고 컴포넌트는 모듈 이름 + 카멜 표기(`memoBodyCount`)다. 명세의 키는 `bodyCount`다.
  - 새 동작에는 `--purpose`(목적 한 문장)가 필요하다. 있는 동작에 다시 쓰면 적은 항목만 더하거나 바꾼다(`상태 limit`, `계산 바꿈 label`). 같은 이름이 다른 칸(상태, 계산, 동작)에 있으면 실패한다.
  - `--state 이름:종류[=처음 값][@data-이름]`, `--computed 이름:식`, `--action 이름:단계[; 단계]`, `--listen 동작:모듈:이벤트`. 여러 번 쓴다. 식에 공백이나 따옴표가 있으면 셸에서 큰따옴표로 묶는다.
  - 명세 전체를 확인한 뒤(이름, 종류, 낱말, 계산의 순환) 명세와 JS를 함께 쓴다. 하나라도 틀리면 아무 파일도 바꾸지 않는다. 같은 명령을 다시 실행하면 바뀐 것 없음이다.
  - 빈칸을 남기지 않는다. JS는 명세에서 만드는 파일이라 손으로 고치지 않는다(C12, [testing.md](testing.md)).
  - 모듈의 JS가 손으로 쓴 파일(명세에서 만든 표시가 없음)이면 덮어쓰지 않고 실패한다.
- **field** (ADR-0037): `cozy add crud`가 만든 한 벌에 필드 하나를 더한다. 형식은 crud의 필드와 같고 `instant`는 받지 않는다. 남기는 빈칸이 없다.

  | 곳 | 더하는 것 (예: `--field dueOn:date:마감일`) |
  | --- | --- |
  | 새 마이그레이션 `V{시각}__{모듈}_add_{테이블}_{열}.sql` | `ALTER TABLE memo ADD COLUMN due_on TEXT;` |
  | 도메인 `{E}`, `spi/dto/{E}Item` | 구성 요소. `createdAt` 앞 |
  | `{E}Row`, `{E}Form` | 필드와 접근자. 폼에는 검증 애너테이션도 |
  | `{E}Views` | `Row`(`updatedAt` 앞, `longtext`는 빼고)와 `Detail`(`createdAt` 앞, `longtext`는 `List<String> {이름}Lines`)의 구성 요소, `formOf`, `unchanged` |
  | `{E}Facade`, `{E}FacadeImpl` | `create`·`update`의 파라미터(끝), `row.set…`(`row.setCreatedAt(…)` 앞) |
  | `{E}Mapper`, `{E}Mapper.xml` | `update`의 `@Param`(`updatedAt` 앞), `columns`·`insert`·`update`의 열 |
  | `templates/{모듈}/internal/fields.html` | 입력 칸 한 줄. fragment `{e}Fields`의 닫는 태그 앞 |
  | 스펙 | 모델 타입이 `{E}Views.Detail`·`{E}Views.ListResult`·`{E}Form`인 항목의 상태마다 샘플 값. 흐름 표기와 블록 표기를 그대로 따른다 |
  | 부르는 곳 | 아래 |

  - **필수 필드**: `!`면 `--default <값>`이 있어야 한다(`bool` 제외). 이미 있는 행이 그 값을 갖는다. 필수가 아니면 `--default`를 받지 않는다. 값은 종류에 맞아야 한다(숫자, `yyyy-MM-dd`, 빈 글자 아님).
  - **부르는 곳**: `src/main/java`와 `src/test/java`에서 `{E}Facade` 타입 변수의 `create`·`update`, `{E}Mapper` 타입 변수의 `update`, `new {E}Item(…)`·`new {E}(…)`·`new Row(…)`·`new Detail(…)`을 찾아 인자를 더한다. 타입은 풀지 않고 같은 파일의 선언과 import로 찾는다.
    - 파라미터를 더한 메서드 안에서는 새 파라미터를 넘긴다(`longtext`를 저장할 때는 `nullToEmpty(…)`).
    - 그 밖의 `src/main`에서는 옆 인자가 `form.getTitle()`이나 `item.title()`처럼 한 객체에서 꺼낸 값이면 같은 객체에서 꺼낸다(`form.getDueOn()`, `item.dueOn()`). 아니면 실패한다.
    - 테스트에는 샘플 값을 넣는다. `{E}ScreenTest`에서는 폼의 속성을 모두 보내는 `.param(…)` 줄 끝에 `.param("dueOn", …)`을 더한다.
    - 인자 수가 지금 시그니처와 다른 호출이 있으면 실패한다.
  - **확인하는 것**: 파일 열두 개가 모두 있다(없으면 crud 한 벌이 아니라고 알린다). record의 끝이 `createdAt, updatedAt`이다. `<sql id="columns">`의 열 순서가 도메인 record와 같다. 자리를 하나라도 못 찾으면 파일과 기대한 모양을 알리고 실패한다.
  - **다시 실행**: 도메인, `Item`, 행, 폼, `Detail`, `Facade.create`, `columns` 일곱 곳을 본다. 모두 있으면 바뀐 것 없음, 모두 없으면 더한다. 일부에만 있으면 있는 곳과 없는 곳을 알리고 실패한다. 같은 이름이 다른 타입으로 있어도 실패한다.
  - **보이는 곳**: 목록(`api/list.html`)과 상세(`api/detail.html`)는 자리를 찾으면 고치고, 못 찾으면 실패하지 않고 "남은 일"로 알린다. 나머지는 그대로 적용한다.

    | 곳 | 넣는 자리 | 못 찾는 경우 |
    | --- | --- | --- |
    | 목록의 열 (`longtext`는 없음) | `${row.updatedAt}`을 쓰는 `td` 앞에 칸, `thead`의 같은 순서 자리에 `th` | 그 `td`가 하나가 아니다. `thead`가 하나가 아니다. `th` 수와 `td` 수가 다르다 |
    | 상세의 항목 | 지금 있는 필드를 보이는 마지막 `datagrid-item` 뒤. 없으면 `item.createdAt` 항목 앞 | `datagrid-item`이 자기 줄에 있지 않다. 두 항목 모두 없다 |
    | 상세의 긴 글 카드 (`longtext`) | `<div class="col-lg-8">`의 닫는 태그 앞 | 그 줄이 하나가 아니다 |

  - **하지 않는 것**: 목록 검색 조건(`searchCondition`)과 이벤트에는 넣지 않는다. 출력의 "다음"이 알린다.
- **enable workbench**: `pom.xml`의 `cozy-ui` 의존 블록 바로 뒤에 dev 전용 `cozy-workbench`를 더한다([workbench.md](workbench.md)). 이미 있으면 바뀐 것 없음이다. `cozy-ui`가 없거나 cozy-parent가 1.2.0보다 낮으면 실패한다.
- **enable events**: `pom.xml`의 `cozy-starter-sqlite` 의존 블록 바로 뒤에 `cozy-starter-events`를 더한다. 이미 있으면 바뀐 것 없음, `cozy-starter-sqlite`가 없으면 실패한다. 아키타입 `events=true`의 예시 코드는 만들지 않는다.
- **동작 순서**: 인자와 앱을 읽어 계획을 세운다 → 하나라도 안 되면 아무것도 쓰지 않고 종료 코드 2 → 계획을 출력 → 적용(`--dry-run`이면 건너뛴다) → 만든 파일에 남은 빈칸과 다음 할 일을 출력.
- **같은 명령 두 번**: 파일마다 같은 내용이면 `그대로`, 다른 내용이면 덮어쓰지 않고 실패한다. 모든 파일이 `그대로`면 "바뀐 것 없음"이다.
- **덧붙이는 곳**: `package-info.java`의 `allowedDependencies`, `cozy/fragments.yml`의 `fragments` 목록 끝, `internal/config/{M}Configuration.java`의 `// cozy:append nav` 표시 앞, `pom.xml`의 `cozy-starter-sqlite` 블록 뒤. 이 형식이 아니면 손으로 할 일을 알리고 실패한다.
- **2단계 편집기 (ADR-0033 §2)**: Java는 javac가 찾은 위치(타입 본문의 마지막 `}` 앞, 같은 import 그룹의 끝. 그룹은 java, javax, org, com, 그 밖 순서이고 이미 있는 import는 정렬하지 않는다), Mapper XML은 `</mapper>` 앞(루트와 `namespace` 확인), pom은 JDK XML 파서로 확인한 뒤 덧붙인다. 외부 DTD는 읽지 않는다. 덧붙인 결과를 다시 읽어 문법 오류가 없는지 본다. 메서드는 이름·반환 타입·파라미터 타입으로 비교하고 본문은 비교하지 않는다(같은 시그니처면 있음, 이름만 같으면 실패). `query`·`command`가 이 편집기를 쓴다.
- **아이콘**: `--icon`, `--nav`의 아이콘은 cozy-ui jar로 확인한다(`check`와 같은 `--repository`). 아이콘을 쓰는 레시피만 jar를 찾는다.

글 출력:

```text
[cozy] 계획
  바꿈  src/main/java/it/cozy/app/activity/package-info.java    allowedDependencies += "events :: spi"
  만듦  src/main/java/it/cozy/app/activity/spi/package-info.java
  만듦  src/main/java/it/cozy/app/activity/spi/ActivityFacade.java
  만듦  src/main/java/it/cozy/app/activity/internal/app/command/ActivityFacadeImpl.java
  만듦  src/main/java/it/cozy/app/activity/internal/app/command/MemoCreatedListener.java
[cozy] 적용했습니다. 빈칸 1곳
  src/main/java/it/cozy/app/activity/internal/app/command/MemoCreatedListener.java:24
    listener-body — MemoCreated를 받았을 때 할 일을 쓰세요. … 다 채우면 이 cozy:slot 표시를 지웁니다.
[cozy] 다음: 빈칸을 채우고 cozy check, 그다음 mvn verify. 빈칸 listener-body를 채웁니다. …
```

- 줄 머리: `만듦`(새 파일), `바꿈`(덧붙임, 뒤에 무엇을), `그대로`(이미 같음).
- **남은 일**: 명령이 고치지 못해 손으로 할 일이다(`add field`의 목록과 상세). 상태 줄에 `남은 일 N곳`이 붙고, 파일, 할 일, 넣을 마크업이 차례로 나온다. 빈칸과 달리 검사가 잡지 않는다.

```text
[cozy] 적용했습니다. 남은 일 1곳
  src/main/resources/templates/memo/api/list.html
    목록에 마감일 열을 더하세요. 머리 줄에 th, 행에 td입니다
      <th class="w-1">마감일</th>
      <td class="text-secondary text-nowrap" th:text="${row.dueOn} ?: '—'">—</td>
```
- 상태 줄: `적용했습니다`, `계획만 보였습니다(--dry-run)`, `바뀐 것 없음(이미 같은 내용이 있습니다)`.

`--json`:

```json
{
  "recipe": "listener",
  "applied": true,
  "plan": [
    { "action": "edit", "file": "src/main/java/it/cozy/app/activity/package-info.java", "detail": "allowedDependencies += \"events :: spi\"" },
    { "action": "create", "file": "src/main/java/it/cozy/app/activity/spi/ActivityFacade.java", "detail": null },
    …
    { "action": "create", "file": "src/main/java/it/cozy/app/activity/internal/app/command/MemoCreatedListener.java", "detail": null }
  ],
  "slots": [
    { "slot": "listener-body", "file": "src/main/java/it/cozy/app/activity/internal/app/command/MemoCreatedListener.java", "line": 24, "what": "…" }
  ],
  "manual": [],
  "next": "빈칸을 채우고 cozy check, 그다음 mvn verify. …"
}
```

| 키 | 값 |
| --- | --- |
| `applied` | 파일을 하나라도 썼으면 `true`. `--dry-run`이거나 바뀐 것이 없으면 `false` |
| `plan[].action` | `create`, `edit`, `delete`, `unchanged` |
| `plan[].after` | `--dry-run`일 때만: 만들거나 바꾸는 파일의 바뀐 뒤 글 전체. 256KB(UTF-8)가 넘으면 없고 `plan[].afterOmitted`가 `true`다. 워크벤치가 지금 파일과 줄 단위로 견준다 |
| `slots[]` | 이 명령이 만들거나 바꾼 파일에 남은 빈칸(앞서 남긴 것 포함). 앱 전체의 빈칸은 `cozy check`가 본다 |
| `manual[]` | 명령이 고치지 못해 손으로 할 일: `file`, `what`, `snippet`(넣을 내용). 없으면 빈 목록 |

- **시각**: 마이그레이션 이름의 시각은 지금 시각이다. 테스트는 시스템 속성 `cozy.cli.now`(`yyyyMMddHHmm`)로 고정한다.
- **검증**: `samples/recipes`의 픽스처가 레시피를 실제 앱에 적용하고 `mvn verify` 결과를 확인한다([ADR-0032 부록 §5](../adr/0032-agent-cli-recipes.md)).

## sync agents

앱의 `AGENTS.md`와 `.agents/skills/cozy-helper/`를 이 cozy 명령(jar)에 든 것과 같게 맞춘다([ADR-0035](../adr/0035-sync-agents.md)). 새 번들을 설치한 뒤나, 문서가 없는 앱에서 쓴다.

```bash
cozy sync agents --dry-run   # 바뀔 파일만 본다
cozy sync agents             # 스킬을 맞추고, AGENTS.md는 없을 때만 만든다
cozy sync agents --force     # 내용이 다른 AGENTS.md도 바꾼다
```

| 파일 | 없을 때 | 내용이 다를 때 | 명령에 없는 파일 |
| --- | --- | --- | --- |
| `.agents/skills/cozy-helper/**` | `만듦` | `바꿈` | `지움` |
| `AGENTS.md` | `만듦` | `그대로 … 내용이 달라 바꾸지 않음`. `--force`면 `바꿈` | — |

- **스킬 폴더는 cozy의 것이다.** 그 안에 따로 둔 파일은 지워진다. 다른 스킬 폴더는 건드리지 않는다.
- **`AGENTS.md`가 다르면** 그대로 두고 "다음"에서 `--force`를 알린다. 스킬은 맞추고 종료 코드는 0이다.
- **출력**: 다른 명령과 같은 계획 형식이다. 모두 같으면 "바뀐 것 없음"이다. `--json`의 `recipe`는 `sync agents`이고 `slots`는 늘 비어 있다.
- **인자**: `agents` 말고 다른 것을 적거나, `--force`를 다른 동작에 쓰면 종료 코드 2다.

## set arg

템플릿의 fragment 호출(`th:replace`, `th:insert`)에 넘긴 인자 가운데 글자 그대로의 문자열(`'고정'`) 하나를 바꾼다. 워크벤치의 속성 패널이 이 명령으로 매개변수를 고친다([ADR-0038](../adr/0038-workbench-redesign.md) §7). 그 인자의 글자만 바뀌고 줄바꿈, 들여쓰기, 다른 인자는 그대로다.

```bash
cozy set arg memo/api/list --line 47 --fragment badge --index 2 --from neutral --value info --dry-run
```

| 인자 | 뜻 |
| --- | --- |
| `<template>` | 앱 템플릿의 뷰 이름(`memo/api/list` → `src/main/resources/templates/memo/api/list.html`) |
| `--line` | 호출이 있는 요소가 시작하는 줄 (`cozy describe --json`의 `outline[].line`). 인자는 다음 줄에 있어도 된다 |
| `--fragment` | 부르는 fragment 이름 (`badge`) |
| `--index` | 몇 번째 인자인가 (1부터) |
| `--from` | 지금 값. 템플릿의 값과 다르면 거절한다 |
| `--value` | 새 값. 빈 글자는 `--value ""` |

- **고치지 않는 것**: 표현식(`${memo.title}`, `'/memo/' + ${memo.id}`)과 선택자(`~{::#filter}`)는 거절한다. 템플릿에서 직접 고친다.
- **정한 값**: 매니페스트가 그 파라미터의 `choices`를 정했으면([ui.md](ui.md)) 그 가운데 하나여야 한다. 프레임워크 fragment는 cozy-ui의 매니페스트(`--ui`나 저장소의 jar), 앱 fragment는 앱의 매니페스트를 본다.
- **글자**: 큰따옴표와 `&`는 문자 참조(`&quot;`, `&amp;`), 역슬래시는 `\\`로 적는다. 작은따옴표와 줄바꿈은 거절한다(Thymeleaf가 fragment 인자의 `\'`를 읽지 못한다).
- **거절하는 경우**: 그 줄에 그 fragment의 호출이 없거나 둘 이상, 인자 번호가 범위 밖, 작은따옴표로 감싼 속성, 지금 값이 다름. 종료 코드 2이고 파일은 바뀌지 않는다.
- **출력**: 다른 명령과 같은 계획 형식이다. `바꿈  {템플릿 파일}    badge(…) 2번째 인자 tone: 'neutral' → 'info'`. `--json`의 `recipe`는 `set arg`이고 `slots`는 늘 비어 있다.

## sync behaviors

```bash
cozy sync behaviors --dry-run
cozy sync behaviors
```

모든 동작 명세(`src/main/resources/behaviors/{모듈}.yml`)에서 모듈의 JS를 다시 만든다. C12(`cozy check`, `mvn verify`)의 고치는 명령이다.

- 명세를 읽어 확인하고, 정리한 모양(값은 큰따옴표, 식은 같은 글자)으로 다시 쓴다. 손으로 고친 명세도 내용이 맞으면 받는다.
- 손으로 고친 JS는 명세에서 다시 만든 것으로 바뀐다. 명세가 없어진 모듈의 만든 JS는 지운다(`지움`).
- 명세 폴더에 모듈 이름의 `.yml`이 아닌 파일이 있거나, 명세의 모듈이 없거나, 명세가 있는 모듈의 JS를 손으로 썼으면 아무것도 바꾸지 않고 실패한다.
- `--force`는 받지 않는다(`sync agents`에만 쓴다).
- 워크벤치의 검사 화면에서 C12 항목의 **변경 파일 확인**이 이 명령의 계획과 적용이다([workbench.md](workbench.md)).

## attach

```bash
cozy attach memoBodyCount --at memo/internal/fields:7 --data 'length=${bodyLength}' --data limit=5000 --on input:count
cozy attach memoBodyCount --at memo/internal/fields:9 --text label --cloak
cozy attach cozyDisclosure --at memo/api/list:12 --data disclosure-closed=filter --on "click:toggle('filter')" \
  --bind "aria-expanded:isOpen('filter')"
cozy attach memoBodyCount --at memo/internal/fields:9 --detach
```

템플릿의 요소 하나에 화면 안 동작(Alpine 컴포넌트)을 붙인다. 템플릿의 글자는 그대로 두고 그 요소의 여는 태그만 고친다. 앱 템플릿의 `x-*` 속성은 이 명령으로 쓴다.

- **`--at <template>:<line>`**: 템플릿은 `templates/` 아래 경로이고 `.html`은 빼도 된다(`memo/internal/fields`, `templates/memo/internal/fields.html`). 줄은 요소가 시작하는 줄이다. 그 줄에서 시작하는 가장 바깥 요소가 하나여야 한다(버튼 안의 아이콘처럼 같은 줄의 안쪽 요소는 세지 않는다). `th:replace`와 `th:block` 요소는 그려질 때 다른 것으로 바뀌어 속성이 남지 않으므로 거절한다.
- **컴포넌트**: 이름이 `cozy`로 시작하면 프레임워크 컴포넌트이고 cozy-ui의 `cozy/behaviors.yml`([ui.md](ui.md)의 프레임워크 컴포넌트)에서 찾는다. 아니면 모듈의 동작 명세(`behaviors/{모듈}.yml`)에서 찾는다. 모듈 컴포넌트는 그 모듈의 템플릿(`templates/{모듈}/…`)에만 붙인다.
- **붙이는 자리**: 요소에 `x-data`가 없고 감싸는 요소도 그 컴포넌트가 아니면, 그 요소가 컴포넌트의 자리가 되어 `x-data`를 받는다. 감싸는 요소가 그 컴포넌트면 안쪽 요소이고 이벤트와 값만 받는다. 이미 다른 컴포넌트를 붙인 요소에는 붙이지 않는다.

| 옵션 | 쓰는 속성 | 받는 이름 |
| --- | --- | --- |
| `--data 이름=값` (여러 번) | `data-이름`. 값에 Thymeleaf 식(`${…}`, `*{…}`, `#{…}`, `@{…}`, `~{…}`)이 있으면 `th:data-이름` | 컴포넌트가 읽는 `data-*`(명세의 `@data-*`, 프레임워크 명세의 `data`). 컴포넌트의 자리에만 둔다 |
| `--on 이벤트:동작` (여러 번) | `x-on:이벤트`. 이벤트에 수식어를 붙인다(`keydown.window`, `htmx:after-settle.camel`) | 동작(명세의 `actions`, 프레임워크 명세의 `methods`) |
| `--text 이름`, `--show 이름` | `x-text`, `x-show` | 상태와 계산, 프레임워크 메서드 |
| `--bind 속성:이름` (여러 번) | `x-bind:속성` | 상태와 계산, 프레임워크 메서드 |
| `--model 상태` | `x-model` | `text`와 `bool` 상태 |
| `--cloak` | `x-cloak` | 스크립트가 있어야만 쓸모 있는 요소에만 쓴다 |

- **값**: 이름 하나이거나, 글자 인자 하나를 받는 프레임워크 메서드면 `이름('글자')`다(`toggle('filter')`). 식(`label.length`, `!open`)은 받지 않는다. 그런 로직은 명세의 계산과 동작에 둔다(`cozy add behavior`).
- **다시 실행하기**: 같은 명령을 다시 실행하면 `그대로`다. 같은 속성에 다른 값을 주면 그 자리에서 바꾼다(`바꿈 data-limit: "5000" → "200"`). 새 속성은 마지막 속성 뒤에 `x-data`, `data-*`, `x-on:*`, `x-bind:*`, `x-show`, `x-text`, `x-model`, `x-cloak`의 순서로 붙인다.
- **`--detach`**: 적은 옵션의 속성만 뗀다(값은 무엇이든 된다: `--data limit=x --detach`). 옵션 없이 쓰면 그 요소에서 그 컴포넌트의 것을 모두 뗀다. 컴포넌트의 자리에서 모두 떼려면 안쪽 요소의 것을 먼저 떼어야 한다(남아 있으면 그 줄을 보이며 실패한다).
- **거절하는 경우**: 없는 컴포넌트, 컴포넌트에 없는 이름, 읽지 않는 `data-*`, 인자 수가 맞지 않는 메서드, 안쪽 요소의 `--data`, 줄에 요소가 없거나 둘 이상. 종료 코드 2이고 파일은 바뀌지 않는다.
- **가까운 이름**: 없는 이름을 적으면 가장 가까운 이름 하나를 함께 보인다(`memoBodyCount에 없는 상태나 계산입니다: lable. label인가요? 있는 것: …`). 글자 하나를 넣고, 빼고, 바꾸고, 이웃한 두 글자를 맞바꾸는 것을 한 번으로 세어 이름 길이의 3분의 1 안의 것이다. `cozy add behavior`, `remove behavior`, `rename behavior`, R16도 같다. 오류의 이름은 조사를 붙이지 않고 `…입니다: 이름` 모양으로 끝에 둔다.
- **출력**: `바꿈  {템플릿 파일}    붙임 <div class="form-hint">: x-text="label", x-cloak`. `--json`의 `recipe`는 `attach`이고 `slots`는 늘 비어 있다.
- **검사**: 붙인 이름은 `cozy check`(R16)가 다시 본다. 손으로 고친 속성이 감싸는 컴포넌트에 없는 이름을 부르면 오류다([testing.md](testing.md)).

## remove behavior

```bash
cozy remove behavior memo body-count                       # 컴포넌트를 지우고 붙인 곳에서 뗀다
cozy remove behavior memo body-count --computed over --state draft   # 항목만 지운다
```

- 항목 옵션(`--state`, `--computed`, `--action` 이름, `--listen 모듈:이벤트`) 없이 쓰면 컴포넌트를 지운다. 모듈 템플릿에서 그 컴포넌트를 붙인 곳(`x-data`, 읽는 `data-*`, 안쪽 요소의 이벤트와 값)을 모두 떼고 JS를 다시 만든다. 마지막 컴포넌트면 명세와 JS를 지운다.
- 항목을 지울 때 템플릿이 그 이름을 부르고 있으면 그 줄을 보이며 실패한다. 먼저 `cozy attach … --detach`로 뗀다. 명세의 다른 항목이 그 이름을 쓰고 있어도 실패한다(명세 확인).

## rename behavior

```bash
cozy rename behavior memo body-count body-length
```

명세의 키를 바꾸고 JS를 다시 만들며, 모듈 템플릿의 `x-data="memoBodyCount"`를 모두 `x-data="memoBodyLength"`로 바꾼다. 새 이름이 이미 있으면 실패한다.

## 명령 여럿을 한 계획으로 (워크벤치)

워크벤치의 컴포넌트 고치기는 `add behavior`, `remove behavior`, `rename behavior`, `attach`를 여럿 묶어 한 계획으로 부른다(`CozyCli.runTogether(명령들, 공통 옵션, …)`. 공통 옵션은 `--project`, `--ui`, `--repository`, `--json`, `--dry-run`). 터미널 명령은 따로 없다.

- 뒤의 명령은 앞의 명령이 계획한 내용을 읽는다. 하나라도 거절하면 아무 파일도 쓰지 않는다(종료 코드 2).
- 결과 파일은 같은 명령을 터미널에서 같은 순서로 하나씩 실행한 것과 글자까지 같다(`BehaviorTogetherTest`, reference-app `WorkbenchBehaviorEditTest`).
- `--json`의 `recipe`는 명령 이름을 쉼표로 이은 것이고(`add behavior, remove behavior`), 같은 파일은 계획에 한 번 나오며 `detail`을 `; `로 잇는다. `next`는 마지막 명령의 것이다.

## migrate alpine

2.0.0에서 동작을 붙이던 `data-cozy-*` 속성을 없애고 같은 뜻의 cozy 컴포넌트(Alpine.js)로 바꿨다([ui.md](ui.md)의 화면 안의 상태). 이 명령이 앱 템플릿(`src/main/resources/templates/**/*.html`)의 옛 속성을 새 모양으로 바꾼다. 남은 옛 속성은 `cozy check`(R16)가 오류로 보이고 이 명령을 알려 준다.

```bash
cozy migrate alpine --dry-run   # 바꿀 파일만 본다
cozy migrate alpine
cozy check
```

| 옛 속성 | 새 모양 |
| --- | --- |
| `data-cozy-confirm="…"` | `x-data="cozyConfirm" x-on:submit="ask" data-confirm-message="…"` |
| `data-cozy-copy="…"` | `x-data="cozyCopy" x-on:click="copy" data-copy-text="…"` |

- **그대로 두는 것**: `th:`·`data-th-` 접두(`th:data-cozy-confirm="${…}"` → `th:data-confirm-message="${…}"`), 따옴표, 줄바꿈, 다른 속성. 주석 안은 보지 않는다.
- **손으로 할 일**: 요소에 `x-data`가 이미 있음(한 요소에 컴포넌트는 하나다. 감싸는 요소를 하나 더 둔다), 값이 없는 속성, 바꾸는 모양이 정해지지 않은 프레임워크 속성(`data-cozy-autosize`, `data-cozy-icon-filter`. 문서에 없던 속성이다). 출력의 "남은 일"에 `파일:줄`과 고치는 법이 나온다. 그 요소는 바꾸지 않는다.
- **원자적이고 멱등이다**: 다른 명령처럼 계획을 보인 뒤 한 번에 적용한다. 두 번째 실행은 "바뀐 것 없음"이다.
- **출력**: `바꿈  {템플릿 파일}    data-cozy-confirm → cozyConfirm 1곳`. `--json`의 `recipe`는 `migrate alpine`이고, 손으로 할 일은 `manual`에 있다.

## migrate events

2.0.0에서 이벤트의 자리를 주인 모듈의 `spi/event/`에서 이벤트 계약 모듈의 `events/spi/{주인 모듈}/`로 옮겼다([structure.md](structure.md)의 이벤트 계약 모듈). 옛 자리의 타입은 C8이 오류로 보이고 이 명령을 알려 준다. 이 명령이 이벤트를 옮기고 그 이름을 가리키는 곳을 모두 고친다.

```bash
cozy migrate events --dry-run   # 바꿀 파일만 본다
cozy migrate events
cozy check
mvn clean verify                # 옮기기 전의 클래스가 target/에 남으면 이름이 겹친다
```

| 하는 일 | 내용 |
| --- | --- |
| 이벤트 파일 | `src/main/java`와 `src/test/java`의 `{모듈}/spi/event/*.java`를 `events/spi/{모듈}/`로 옮기고 `package`를 고친다. 옛 `package-info.java`는 지우고 `events`와 주인 폴더의 `package-info.java`를 만든다 |
| 이름 | 앱 전체 Java 파일에서 옛 패키지 뒤에 타입 이름이나 `*`가 오는 곳(import, static import, 전체 이름)을 새 패키지로 바꾼다 |
| 의존 | 이벤트를 내거나 받는 모듈에 `"events :: spi"`를 더한다. 받는 모듈의 `"{주인} :: spi"`는 그 모듈의 Java, 템플릿(`templates/{모듈}/`), Mapper XML(`mapper/{모듈}/`)이 주인의 `spi`를 더 쓰지 않을 때만 지운다 |
| 저장된 이름 | `db/migration/V{시각}__cozy_move_event_contracts.sql`을 만든다. 아래 SQL이다 |

- **저장된 이름을 옮기는 SQL**: 발행 기록은 이벤트 클래스 이름(`event_type`)과 리스너 id(`…(이벤트 FQN)`로 끝난다)를, 인박스는 리스너 id를 저장한다([events.md](events.md)). 옮긴 이벤트마다 세 줄이다. 업데이트한 PC에 남아 있던 미완료 발행 기록이 다음 기동 때 새 클래스로 다시 발행되고, 이미 처리한 이벤트는 인박스가 계속 건너뛴다.

```sql
-- it.cozy.app.memo.spi.event.MemoCreated → it.cozy.app.events.spi.memo.MemoCreated
UPDATE cozy_event_publication SET event_type = '{새 이름}' WHERE event_type = '{옛 이름}';
UPDATE cozy_event_publication SET listener_id = replace(listener_id, '({옛 이름})', '({새 이름})') WHERE instr(listener_id, '({옛 이름})') > 0;
UPDATE cozy_inbox SET listener_id = replace(listener_id, '({옛 이름})', '({새 이름})') WHERE instr(listener_id, '({옛 이름})') > 0;
```

- **실패로 닫는 경우**: 옛 자리에 이벤트(`CozyEvent` record)가 아닌 타입이 있거나 하위 폴더가 있으면 아무것도 바꾸지 않는다. 메시지가 옮길 곳(`spi/dto`)을 알려 준다. 앱에 cozy-starter-events가 없어도 실패한다.
- **손으로 할 일**: 받는 모듈이 주인을 `"memo"`나 `"memo :: *"`처럼 다른 형식으로 적었으면 지우지 않고 알린다. 자원 파일(`src/main/resources`, `src/test/resources`의 글 파일)에 옛 이름이 남아 있어도 알린다. 이미 적용된 마이그레이션(`db/migration`)은 보지 않는다.
- **원자적이고 멱등이다**: 다른 명령처럼 계획을 보인 뒤 한 번에 적용한다. 옛 자리에 이벤트가 없으면 "바뀐 것 없음"이라서 두 번째 실행도 그렇다.
- **출력**: `만듦  …/events/spi/memo/MemoCreated.java`, `지움  …/memo/spi/event/MemoCreated.java    → …`, `바꿈  {파일}    이벤트 이름을 이벤트 계약 모듈(events.spi)로`. `--json`의 `recipe`는 `migrate events`다.

## migrate behaviors

2.2.0부터 모듈의 화면 안 동작은 동작 명세(`behaviors/{모듈}.yml`)가 원본이고 JS는 cozy가 만든다([ui.md](ui.md)의 동작 명세). 그 전에 손으로 쓴 모듈 JS(`static/{모듈}/{모듈}.js`)는 2.x에서 그대로 싣고 `cozy check`와 `mvn verify`가 C12 경고로 이 명령을 알린다([testing.md](testing.md)). 이 명령이 그 파일의 컴포넌트를 낱말로 읽어 명세로 옮기고, 그 명세에서 JS를 다시 만든다.

```bash
cozy migrate behaviors --dry-run                          # 옮길 파일과 남은 일만 본다
cozy migrate behaviors --purpose "memoPanel=패널을 바꾼다"   # 목적 주석이 없는 컴포넌트
cozy check
```

- 워크벤치의 검사 화면에서 C12 경고의 **변경 파일 확인**이 이 명령의 계획과 적용이다. 남은 일(`파일:줄`, 무엇이 낱말 밖인지, 그 코드)도 그 화면에 보인다([workbench.md](workbench.md)). 남은 일의 문구는 이름을 조사 없이 끝에 둔다(`두 번 받는 화면 이벤트입니다: memo:saved`).
- **읽는 모양**: 옛 `cozy add behavior`가 만든 모양이다. 파일에는 `document.addEventListener('alpine:init', function () { … })`와 그 안의 `window.Alpine.data('이름', function () { var root = null; return { … }; })`만 둔다(화살표 함수도 읽는다). 등록 바로 위의 주석 첫 줄이 목적이다. 없으면 `--purpose 컴포넌트=목적`으로 준다.

| JS | 명세 |
| --- | --- |
| `open: false`, `limit: 0`, `title: ''`, `ids: []` | 상태와 처음 값 (`bool`, `number`, `text`, `list`) |
| `init`의 `this.limit = parseInt(root.dataset.limit, 10) \|\| 5000`, `Number(…)`, `root.dataset.x === 'true'`, `root.dataset.x \|\| '글자'` | `number=5000@data-limit` 같은 `data-*` 상태 (`this.$root.dataset`, `this.$el.dataset`도) |
| `init`의 `window.addEventListener('memo:archived', function () { self.close(); })` | `listens` |
| `get label() { return this.open ? '접기' : '펼치기'; }` | 계산 `if(open, '접기', '펼치기')` |
| `!`, `&&`, `\|\|`, `===`·`!==`·`>`·`<`·`>=`·`<=`, `? :`, `.length`, `.includes(v)`, `Number(…)`, getter 안의 지역 변수 | `not`, `and`, `or`, `eq`·`ne`·`gt`·`lt`·`ge`·`le`, `if`, `length`·`count`, `includes`, `number` |
| `this.title + ' · ' + this.limit.toLocaleString('ko-KR') + '자'` | `format('{title} · {limit}자', 'ko-KR')` |
| `this.open = !this.open`, `this.n += 1`·`this.n++`·`this.n = this.n - 2`, `this.ids = []`(처음 값), `this.x = 값` | `toggle`, `increment`·`decrement`, `reset`, `set` |
| `if (조건) { … }`, `this.$dispatch('memo:closed', { open: this.open })` | `when`, `emit('memo:closed', open)` |
| `event.target.value`, `event.target.checked`, `event.target.dataset.id`, `event.detail.ids` | `event.value`, `event.checked`, `event.data.id`, `event.detail.ids` |

- **옮기지 않는 것**: 그 밖의 모든 JS다. 다른 메서드 부르기, `else`, 느슨한 비교(`==`, `!=`. `===`로 바꾼 뒤 다시 실행한다), 반복문, 화살표 함수로 거르기, 숫자 더하기, 화면의 다른 요소 읽기(`querySelector`), `destroy`, 템플릿 문자열, `null`, 서버 요청이 그렇다. 한 컴포넌트라도 낱말 밖이면 그 모듈은 바꾸지 않는다(한 모듈에 손으로 쓴 JS와 만든 JS를 함께 둘 수 없다). 출력의 "남은 일"에 `파일:줄`, 컴포넌트, 무엇이 낱말 밖인지, 그 자리의 JS가 나온다. 그런 동작은 프레임워크 컴포넌트(`cozy…`)나 새 낱말이 필요하다(ADR-0043 §7).
- **뜻이 조금 달라지는 곳**: `parseInt`로 읽던 `data-*`는 소수점 아래도 읽는다(만든 JS는 `parseFloat`). 받는 화면 이벤트는 만든 JS가 `destroy`에서 뗀다. 이벤트의 `data-*`(`event.target.dataset.id`)는 그 요소에 없으면 컴포넌트 안의 감싸는 요소에서 찾는다. `Number(…)`로 읽을 수 없는 글자는 `NaN` 대신 `0`이 된다. `data-*`가 없을 때는 `||` 뒤의 값이 처음 값이다.
- **그대로 두는 것**: 템플릿. 이름(컴포넌트, 상태, 계산, 동작)이 같으므로 손으로 붙인 속성이 그대로 맞는다. 옮긴 뒤에는 R16이 그 이름을 확인하고 C13이 붙인 곳과 화면 이벤트를 본다.
- **이미 명세가 있는 모듈**: 명세에 컴포넌트를 더한다. 같은 이름이 있으면 그 모듈은 바꾸지 않고 알린다(`cozy rename behavior`로 한쪽 이름을 바꾼다).
- **원자적이고 멱등이다**: 다른 명령처럼 계획을 보인 뒤 한 번에 적용한다. 손으로 쓴 모듈 JS가 없으면 "바뀐 것 없음"이라서 두 번째 실행도 그렇다.
- **출력**: `만듦  src/main/resources/behaviors/memo.yml`, `바꿈  src/main/resources/static/memo/memo.js    손으로 쓴 JS를 동작 명세에서 만든 JS로 바꿈`. `--json`의 `recipe`는 `migrate behaviors`이고, 손으로 할 일은 `manual`에 있다.

## channel

개발 도구의 업데이트 채널을 정하고 만든다. 형식과 서버별 예는 [channel.md](channel.md)에 있다. 앱 폴더 밖에서도 실행하고, 네트워크를 쓰지 않는다.

| 명령 | 하는 일 |
| --- | --- |
| `cozy channel set <주소> [--header <이름>]` | 채널 주소(`cozy-channel.json`)를 사용자 설정 파일에 둔다. 형식(UNC, 절대 경로, `file:`, `http:`, `https:`)과 이름만 본다 |
| `cozy channel show [--json]` | 채널, 출처(`COZY_CHANNEL`이나 설정 파일), 머리 이름, 토큰이 있는지. 토큰은 보이지 않는다 |
| `cozy channel add <채널 폴더> <릴리스 폴더> [--dry-run]` | (관리자) 받은 릴리스의 파일을 SHA-256으로 확인하고 `<채널 폴더>/{판}/`에 복사한 뒤 `index/cozy-channel.json`에 더한다 |
| `cozy channel release <릴리스 폴더> <channel.yml> [--previous <폴더>]` | (릴리스 워크플로) 그 릴리스와 게시한 이전 릴리스들의 `cozy-channel.json`을 만든다 |

- 판단할 수 없으면(모르는 주소 형식, 읽을 수 없는 색인, SHA-256이 다른 파일, 같은 판의 다른 파일) 아무것도 바꾸지 않고 종료 코드 2로 끝낸다.

## upgrade --check

`cozy upgrade --check [--to <version>] [--json]`은 채널 색인을 읽어 지금 판과 새 판을 비교해 보인다. 아무것도 바꾸지 않는다. `--dry-run`도 같다. 지금 판은 번들의 cozy 명령이 알려 주는 설치 폴더(`COZY_HOME`)의 `COZY_VERSION`이다. 출력, `--json`의 키, 웹 서버의 인증과 인증서는 [channel.md](channel.md#cozy-upgrade---check와-새-판-알림)에 있다.

- `cozy update`는 2.x 동안 이것의 별칭이다(같은 인자와 출력, 오류 출력에 바뀐 이름 한 줄). 3.0.0에서 없어진다.
- 채널을 정하지 않았거나, 지금 판을 모르거나(설치 폴더 밖의 cozy-cli jar, 개발 판), 색인을 읽을 수 없으면 종료 코드 2로 끝낸다.
- **새 판 알림**: 채널을 정했으면 앱에 쓰는 명령(`describe`, `check`, `recipes`, `add`, `link`, `enable`, `sync`, `set`, `migrate`) 뒤에 하루에 한 번 채널을 확인하고, 새 판이 있으면 오류 출력에 한 줄을 보인다. `--json` 출력 뒤에는 하지 않는다. 명령의 출력과 종료 코드는 바뀌지 않는다.

## upgrade

`cozy upgrade [--to <version>]`은 채널에서 판을 받아 크기와 SHA-256을 확인하고, 이 명령이 끝난 뒤에 도는 설치 스크립트에 설치를 맡긴다. 설치 방법은 설치한 모양을 따른다(MSI는 `msiexec`, zip은 받은 번들의 `cozy install`). 올릴 판, 받을 파일, 이전 판의 파일, 결과를 보이는 방법은 [channel.md](channel.md#cozy-upgrade)에 있다.

- 올릴 판은 주 버전이 같은 가장 새 판이다. 호환이 깨지는 판은 `--to`로 적는다. 낮은 판으로는 가지 않는다.
- 맡긴 설치의 결과는 다음 cozy-cli 명령이 처음에 오류 출력에 한 번 보인다(`[cozy] 판을 올렸습니다: 2.1.0 → 2.1.1.`). 명령의 출력과 종료 코드는 바뀌지 않는다.
- 판단할 수 없으면(채널이나 지금 판을 모름, 크기나 SHA-256이 다른 파일, 맞는 파일이 없음, 설치하는 중) 아무것도 설치하지 않고 종료 코드 2로 끝낸다. 사용법(`cozy upgrade [--check] [--to <version>] [--json]`)에 없는 인자와 `--check` 없는 `--json`도 사용법 전체를 보이고 종료 코드 2로 끝낸다.

## 의존

cozy-cli는 cozy-rules와 SnakeYAML(Boot BOM), JDK에만 의존한다(ADR-0016 개정). Java 소스는 JDK의 javac 파서(`com.sun.source`)로 읽으므로 JRE가 아니라 JDK 25로 실행한다. 이 경계는 모듈의 enforcer(`cozy-module-boundary`)가 검사한다. 테스트만 cozy-ui jar를 저장소 배치로 복사해 쓴다.
