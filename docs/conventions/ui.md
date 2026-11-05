# 화면 (cozy-ui)

Tabler, htmx, Pretendard와 Thymeleaf fragment로 화면을 만든다(PRD §6.4, §10, ADR-0021). 새 화면은 먼저 패턴 카탈로그(`/cozy/dev/catalog`, dev 프로필)에서 쓸 패턴을 찾는다. 없을 때만 새로 만든다.

## 화면 한 장의 모양

```html
<!DOCTYPE html>
<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
<body>
<main>
  <section th:replace="~{cozy/pattern/list-with-filter :: listWithFilter('메모', ~{::#memo-actions}, ~{::#filter}, ~{::#result})}">
    <a id="memo-actions" ...>새 메모</a>
    <form id="filter" th:hx-get="@{/memo}" hx-target="#result" hx-swap="outerHTML" ...> ... </form>
    <div id="result" th:fragment="result"> ... </div>
  </section>
</main>
</body>
</html>
```

- **최상위**: `<html>`은 `appShell` 레이아웃으로 바꾼다. 본문은 `<main>` 하나다.
- **패턴 인자**: 패턴에 넘기는 조각은 같은 파일 안의 id(`~{::#id}`)나 class(`~{::.row}`)로 고른다.
- **htmx로 바꿀 영역**: `th:fragment`와 `id`를 함께 둔다. 컨트롤러는 `CozyViews.pageOrFragment("memo/list", "result")`를 반환한다.
- **쓰지 않는 것**: `<link>`, `<script>`, `style=`. 자산은 레이아웃이 모두 넣고, CSP가 인라인 스크립트와 인라인 style을 막는다.
- **`th:replace`와 `th:if`**: `th:replace`가 `th:if`보다 먼저 실행된다. 조건부로 넣으려면 `<th:block th:if="…">`로 감싼다.
- **예약 이름**: `cozy`로 시작하는 모델 속성 이름은 쓰지 않는다. 프레임워크가 쓰는 이름이다.

## 컨트롤러 규약 (PRD §10)

```java
@GetMapping
String list(@ModelAttribute("filter") MemoFilter filter, Model model) {
    model.addAttribute("result", new ListResult(rows, CozyPage.of(page, 10, total)));
    return CozyViews.pageOrFragment("memo/list", "result");
}

@PostMapping
String create(@Valid @ModelAttribute("form") MemoForm form, BindingResult errors, RedirectAttributes flash) {
    if (errors.hasErrors()) {
        return CozyViews.pageOrFragment("memo/form", "form");   // htmx면 폼만 다시 그린다
    }
    flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("저장했습니다"));
    return CozyViews.redirect("/memo/" + id);                   // htmx면 HX-Redirect
}
```

- **뷰 이름**: 첫 경로는 자기 모듈 이름이다(`memo/...`). 어기면 테스트에서 C7로 실패한다.
- **화면 스펙**: 화면 템플릿마다 `specs/{모듈}-{화면}.yml`을 둔다. 형식은 [testing.md](testing.md)에 있다.
- **입력과 출력**: 입력은 getter와 setter가 있는 폼 클래스로 받고, 출력은 `view/` 패키지의 record로 넘긴다. 도메인 객체를 템플릿에 직접 넘기지 않는다.
- **리다이렉트**: `CozyViews.redirect`는 `"redirect:" + path`와 같다. htmx 요청이면 인터셉터가 200 빈 응답과 `HX-Redirect`로 바꾸고, 플래시도 전달된다.
- **htmx 헤더**: `HtmxRequest`를 메서드 인자로 받으면 htmx 헤더를 읽을 수 있다(`htmx()`, `partial()`, `target()`, `trigger()`). boost 요청은 부분 갱신으로 보지 않는다.
- **내비 등록**: `CozyNavItem("메모", "/memo", 10)` 빈을 등록한다. 요청 경로가 그 경로로 시작하면 활성으로 표시한다.

## fragment 목록 (v1)

| id | 사용 | 메모 |
| --- | --- | --- |
| `layout/base` | `cozy/layout/base :: base(제목, ~{::main})` | 내비 없는 문서 |
| `layout/app-shell` | `cozy/layout/base :: appShell(제목, ~{::main})` | 앱 화면 기본 |
| `pattern/list-with-filter` | `listWithFilter(제목, 버튼, 필터폼, 결과)` | 결과는 `th:fragment` + `id` |
| `pattern/form-page` | `formPage(제목, 폼)` | 등록 |
| `pattern/detail-edit` | `detailEdit(제목, 상세, 폼)` | 상세 + 수정 |
| `pattern/master-detail` | `masterDetail(제목, 목록, 상세)` | 상세 영역 id는 `detail` |
| `pattern/polling-panel` | `pollingPanel(제목, url, 초, 본문)` | `every N s` 갱신 |
| `component/field` | `field('title', '제목', 'text')` | `<form th:object>` 안. text, textarea, checkbox, number, date, email |
| `component/data-table` | `dataTable(~{::#head}, ~{::.row})` | `<tr>` 머리행과 행들 |
| `component/pagination` | `pagination(${page}, '/memo', '#result', '#filter')` | `CozyPage` |
| `component/badge` | `badge('고정', 'blue')` | |
| `component/alert` | `alert('success', '저장했습니다')` | 플래시는 app-shell이 그린다 |
| `component/empty-state` | `emptyState('없습니다', '설명')` | |
| `component/modal` | `modal('id', '제목', ~{::#body})` | `data-bs-target="#id"`로 연다 |
| `component/toast` | `toast('success', '저장했습니다')` | 스크립트는 `Cozy.toast(message, kind)` |
| `component/loading` | `loading()` | `htmx-indicator` |

## 매니페스트 `cozy/fragments.yml`

프레임워크는 cozy-ui jar에, 앱은 자기 `resources/cozy/fragments.yml`에 둔다. 앱 매니페스트에는 `templates/fragments/` 아래의 공용 fragment와 모듈의 공개 fragment(`templates/{모듈}/api/`)를 같은 형식으로 적는다. 패턴 카탈로그에는 `api` 묶음으로 보인다. 템플릿과 매니페스트가 어긋나면 R8([rules.md](rules.md)), 공개 fragment가 빠지면 C5([testing.md](testing.md))로 빌드가 실패한다. 파서는 `io.salvia95.cozy.ui.catalog.FragmentManifest`다.

```yaml
fragments:
  - id: component/badge                 # 고유 이름
    template: cozy/component/badge      # 템플릿 경로
    signature: badge(text, color)       # th:fragment와 같게
    purpose: 상태나 분류를 짧게 표시한다   # 용도 한 줄
    document: false                     # 완전한 HTML 문서를 그리면 true (레이아웃)
    params:                             # signature와 같은 순서
      - { name: text, type: String, required: true, description: 표시할 글자 }
      - { name: color, type: String, required: false, description: Tabler 색 이름 }
    samples:                            # 상태 이름 → 값. 카탈로그와 렌더링 테스트가 쓴다
      default: { text: 고정, color: blue }
```

- **type**: `String`, `boolean`, `int`, `long`, `fragment` 또는 클래스 FQN이다. FQN이면 샘플 맵을 그 타입으로 변환한다.
- **fragment 파라미터의 샘플 값**: fragment 이름을 쓴다. `template :: selector` 형식이고, 템플릿을 생략하면 `cozy/dev/catalog`에서 찾는다. 샘플 값이 없으면 자리 표시가 들어간다.
- **폼 샘플**: `_form` 맵은 `th:object`가 되고, `_errors` 맵(필드 → 메시지)은 검증 오류가 된다.
- **이름 규칙**: fragment를 받는 fragment는 모든 파라미터 이름이 `cozy`로 시작해야 한다(ADR-0021).
- **YAML 따옴표**: `{ }` 안의 값에 `,`, `?`, `{}`, `: `가 들어가면 따옴표로 감싼다.

## 설정과 자산

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `cozy.dev.catalog.enabled` | dev 프로필이면 `true`, 아니면 `false` | `/cozy/dev/catalog` 등록. 매니페스트 fragment와 화면 스펙(`specs/*.yml`)을 상태별로 미리 본다 |
| `cozy.ui.view-module-check` | `warn` (cozy-test가 있는 테스트에서는 `fail`) | 컨트롤러의 모듈과 뷰 이름의 첫 경로가 다르면 경고하거나(`warn`) 요청을 실패시킨다(`fail`). 검사 C7 |

- **자산 경로**: 모든 자산은 `/cozy/vendor/{이름}/{버전}/`에서 서빙된다. Tabler 1.7.3, htmx 2.0.11, Pretendard 1.3.9이고, 폴더마다 LICENSE가 있다.
- **자산 버전 바꾸기**: `distribution/vendor-assets/assets.conf`를 고치고 `fetch.sh`를 실행한다. 그다음 `base.html`의 경로를 맞춘다. 빌드가 `cozy-ui/vendor.lock`과 대조한다.
- **cozy.js**: 변경 요청에 CSRF 헤더를 넣는다. 4xx·5xx 응답과 연결 실패는 토스트로 알리고, 요청 중에는 맨 위 진행 막대를 보인다. 서버는 `HX-Trigger: {"cozy:toast": {"message": "…", "kind": "success"}}`로 토스트를 띄울 수 있다.
