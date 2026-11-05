# 템플릿 경계 규칙 (cozy-rules)

템플릿이 어느 폴더에 있는지가 곧 그 템플릿의 소속이다(PRD §7). 허용 의존은 Java 모듈의 `@ApplicationModule(allowedDependencies = ...)`에서만 읽고, `resources`에는 경계 정보를 두지 않는다. `CozyArchitectureTest`(검사 C2)가 빌드 때마다 이 규칙을 검사한다.

## 폴더와 참조 가능한 대상

| 경로 | 소유 | 참조할 수 있는 것 |
| --- | --- | --- |
| `templates/cozy/{layout,pattern,component}/` | 프레임워크(cozy-ui jar). 앱은 쓰지 않는다 | `cozy/` 안의 아래 계층 |
| `templates/fragments/{layout,pattern,component}/` | 앱 공용 | `cozy/`와 `fragments/`의 아래 계층 |
| `templates/{모듈}/` | 그 모듈의 내부 | 자기 모듈, `cozy/`, `fragments/`, 허용된 모듈의 `api/` |
| `templates/{모듈}/api/` | 그 모듈의 공개 영역 | 위와 같다 |
| `templates/error/` | Boot 오류 화면 | `cozy/`, `fragments/` |

## 규칙

| ID | 규칙 | 위반 예 |
| --- | --- | --- |
| R1 | 최상위 폴더는 Java 모듈 이름이거나 예약어(`fragments`, `error`)여야 한다. 앱은 `cozy/`에 쓰지 않는다. `cozy/`와 `fragments/` 바로 아래에는 `layout`, `pattern`, `component` 폴더만 둔다 | `templates/vocc/` |
| R2 | `templates/` 바로 아래에 `.html`을 두지 않는다 | `templates/index.html` |
| R3 | 다른 모듈의 템플릿은 `api/` 아래 것만 참조한다. 이때 내 모듈의 allowedDependencies에 `{모듈}::api` 또는 `{모듈}`이 있어야 한다 | voc/list가 `wiki/detail :: body`를 참조 |
| R4 | `cozy/`와 `fragments/` 안에서는 layout → pattern → component 방향으로만 참조한다. 같은 계층끼리는 기본으로 금지다 | component가 pattern을 참조 |
| R5 | `cozy/`는 `cozy/` 밖을 참조하지 않는다. `fragments/`는 모듈 템플릿을 참조하지 않는다 | `fragments/pattern/x`가 `memo/api/y`를 참조 |
| R6 | 참조한 템플릿과 fragment가 실제로 있고, 넘긴 인자 개수가 선언과 같아야 한다 | `~{cozy/component/badge :: badg(x)}` |
| R7 | 동적 참조는 금지한다. 검사할 수 없는 것은 실패로 닫는다 | `th:replace="~{${view} :: body}"` |
| R8 | `cozy/`와 `fragments/` 계층 폴더의 모든 fragment는 매니페스트에 있어야 한다. 매니페스트의 모든 항목은 실제 fragment와 맞아야 한다 | 매니페스트에 없는 `fragments/component/chip` |

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
- **매니페스트(R8)에 적을 수 있는 것**: `cozy/`와 `fragments/`의 계층 폴더에 있는 fragment, 그리고 모듈 `api/`의 fragment. 모듈 공개 fragment가 매니페스트에 있는지는 검사 C5가 본다.

## 실패 메시지

규칙 ID, 위치, 참조, 고치는 방법을 한 줄에 담는다. 작은 LLM이 메시지만 보고도 고칠 수 있어야 하기 때문이다.

```text
[R3] templates/voc/list.html:2 → wiki/detail :: body — wiki의 내부 템플릿입니다. wiki/api/ 아래 fragment를 쓰거나 wiki에 공개 fragment를 추가하세요.
[R6] templates/memo/form.html:14 → cozy/component/badge :: badge(1개 인자) — badge(text, color)는 파라미터가 2개인데 1개를 넘겼습니다. 선언과 같은 개수로 넘기세요.
[R7 경고] templates/memo/list.html:3 → th:include="..." — th:include는 Thymeleaf 3에서 권장하지 않습니다. th:replace나 th:insert를 쓰세요.
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
