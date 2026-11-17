# 화면 (cozy-ui)

Tabler, Tabler Icons, htmx, Pretendard와 Thymeleaf fragment로 화면을 만든다(PRD §6.4, §10, ADR-0021, ADR-0030, ADR-0031). 화면 유형, 버튼 순서, 색 tone, 문구 원칙은 [design.md](design.md)에 있다. 새 화면은 먼저 패턴 카탈로그(`/cozy/dev/catalog`, dev 프로필)에서 쓸 패턴을 찾는다. 없을 때만 새로 만든다. 아이콘 이름은 `/cozy/dev/catalog/icons`에서 찾는다.

## 화면 한 장의 모양

```html
<!DOCTYPE html>
<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
<body>
<main>
  <section th:replace="~{cozy/pattern/list-with-filter :: listWithFilter('메모', ~{::#memo-actions}, ~{::#filter}, ~{::#result})}">
    <a id="memo-actions" class="btn btn-primary" ...><svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>메모 만들기</a>
    <form id="filter" th:hx-get="@{/memo}" hx-target="#result" hx-swap="outerHTML" ...> ... </form>
    <div id="result" th:fragment="result"> ... </div>
  </section>
</main>
</body>
</html>
```

- **최상위**: `<html>`은 `appShell` 레이아웃으로 바꾼다. 본문은 `<main>` 하나다.
- **화면 틀(Tabler 세로 레이아웃)**: `appShell`은 왼쪽 내비(아이콘 + 이름, 아래쪽 앱 메뉴)와 `.page-wrapper`를 그리고, `<main>`과 푸터를 `.page-wrapper` 안에 둔다. 패턴은 `page-header`(제목 + 오른쪽 동작 버튼)와 `page-body`(플래시 + 본문)를 그린다. 화면은 패턴을 쓰고, `section.container-xl` 같은 틀을 직접 만들지 않는다.
- **플래시**: `CozyFlash`는 패턴이 `page-body` 맨 위에 그린다(`cozy/component/alert :: flash()`). 패턴을 쓰지 않는 화면은 flash()를 직접 둔다. 종류는 tone(`success`, `info`, `warning`, `critical`)이다.
- **화면 유형**: 목록(`list-with-filter`), 만들기·수정(`form-page`, 가운데 좁게), 상세(`details`, 읽기 전용), 개요(`overview`). 상세에서 고치지 않고 수정 화면으로 간다. 삭제는 `deleteConfirm`으로 확인 창을 거친다([design.md](design.md) §3).
- **색**: 색 클래스(`bg-*`, `text-red`, `btn-outline-danger` 등)를 직접 쓰지 않는다(R14). tone을 받는 컴포넌트를 쓴다.
- **버튼 순서**: 버튼 묶음(`btn-list`)마다 주 버튼은 하나, 맨 오른쪽이다(R15). 폼은 `formActions`로 오른쪽 아래 [취소] [주 버튼]을 붙인다.
- **아이콘**: `<svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>`. 버튼 글자 앞에 두면 Tabler가 간격을 맞춘다. 채운 아이콘은 `iconFilled('star')`. 없는 이름은 R13으로 빌드가 실패한다. 아이콘만 있는 버튼에는 `aria-label`을 단다.
- **패턴 인자**: 패턴에 넘기는 조각은 같은 파일 안의 id(`~{::#id}`)나 class(`~{::.row}`)로 고른다.
- **htmx로 바꿀 영역**: `th:fragment`와 `id`를 함께 둔다. 컨트롤러는 `CozyViews.pageOrFragment("memo/api/list", "result")`를 반환한다. fragment 이름은 HTML 요소 이름(`form`, `table` 등)과 겹치지 않게 짓는다. Thymeleaf가 같은 이름의 요소까지 함께 고르기 때문이다(R12, 예: `memoForm`).
- **쓰지 않는 것**: `<link>`, `<script>`, `style=`. 자산은 레이아웃이 모두 넣고, CSP가 인라인 스크립트와 인라인 style을 막는다.
- **`th:replace`와 `th:if`·`th:each`**: `th:replace`가 `th:if`·`th:each`보다 먼저 실행된다. 조건부나 반복으로 넣으려면 `<th:block th:if="…">`, `<th:block th:each="…">`로 감싼다. 같은 요소의 th:each + th:replace는 R11이다.
- **예약 이름**: `cozy`로 시작하는 모델 속성 이름은 쓰지 않는다. 프레임워크가 쓰는 이름이다.

## 컨트롤러 규약 (PRD §10)

```java
@GetMapping
String list(@ModelAttribute("filter") MemoFilter filter, Model model) {
    model.addAttribute("result", new ListResult(rows, CozyPage.of(page, 10, total)));
    return CozyViews.pageOrFragment("memo/api/list", "result");
}

@PostMapping
String create(@Valid @ModelAttribute("form") MemoForm form, BindingResult errors, RedirectAttributes flash) {
    if (errors.hasErrors()) {
        return CozyViews.pageOrFragment("memo/api/form", "memoForm");   // htmx면 폼만 다시 그린다
    }
    flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 만들었습니다"));
    return CozyViews.redirect("/memo/" + id);                   // htmx면 HX-Redirect
}
```

- **뷰 이름**: 자기 모듈의 화면 `{모듈}/api/{화면}`(또는 htmx로 다른 모듈에 줄 fragment `{모듈}/spi/…`)이다. 어기면 테스트에서 C7로 실패한다.
- **템플릿 폴더**: 화면은 `api/`, 다른 모듈용 fragment는 `spi/`, 화면과 spi가 같이 쓰는 조각은 `internal/`에 둔다. spi·internal 조각은 값을 파라미터로만 받는다(R10). 예시는 [structure.md](structure.md)에 있다.
- **화면 스펙**: 화면 템플릿마다 `specs/{모듈}-{화면}.yml`을 둔다. 형식은 [testing.md](testing.md)에 있다.
- **입력과 출력**: 입력은 getter와 setter가 있는 폼 클래스로 받고, 출력은 `api/dto` 패키지의 record로 넘긴다. 컨트롤러는 spi(`*Facade`, `*Finder`)만 부르고 spi DTO를 화면 DTO로 바꾼다. 도메인 객체를 템플릿에 직접 넘기지 않는다.
- **리다이렉트**: `CozyViews.redirect`는 `"redirect:" + path`와 같다. htmx 요청이면 인터셉터가 200 빈 응답과 `HX-Redirect`로 바꾸고, 플래시도 전달된다.
  - 받은 화면에서 영역을 고르는 요소(`hx-select`, `hx-select-oob`)는 `hx-headers='{"X-Cozy-Redirect": "follow"}'`를 단다(`CozyViews.FOLLOW_REDIRECT_HEADER`). 그 요청은 바꾸지 않는다. 브라우저가 리다이렉트를 따라가 다음 화면(플래시 포함)을 받고, htmx가 고른 영역만 바꾼다. `hx-push-url="true"`면 주소는 리다이렉트된 주소가 된다. 워크벤치가 이렇게 쓴다.
- **htmx 헤더**: `HtmxRequest`를 메서드 인자로 받으면 htmx 헤더를 읽을 수 있다(`htmx()`, `partial()`, `target()`, `trigger()`). boost 요청은 부분 갱신으로 보지 않는다.
- **선택지 (`CozyOptions`)**: `selectField`, `checkboxGroup`, `radioGroup`에 넘기는 값과 이름의 목록이다. 컨트롤러가 화면 DTO나 모델에 담는다. 보이는 순서는 목록의 순서이고, 같은 값이 두 번 있거나 값·이름이 비면 예외다.

  ```java
  CozyOptions.of(Status.class, (s) -> switch (s) { case TODO -> "할 일"; case DONE -> "완료"; });   // 값은 상수 이름
  CozyOptions.of(labels);                                   // 순서가 있는 Map<값, 이름>
  new CozyOptions(List.of(new CozyOptions.Option("MAIL", "메일")));
  ```

  화면 명세의 샘플 값은 `{ items: [ { value: TODO, label: 할 일 } ] }`이다. 폼 속성은 셀렉트·라디오 버튼이면 값 하나(`String`이나 enum), 체크박스면 목록이다.
- **입력 그리드 (`CozyGrid`)**: 열(`CozyGrid.Column`)마다 입력 방식을 개발자가 고른다. 칸은 입력 이름·값·오류를 가진다. Spring 폼의 목록 속성이면 `CozyGrid.bind`가 칸 이름(`items[0].title`), 값, 검증 오류를 바인딩 결과에서 채운다.

  ```java
  List<CozyGrid.Column> columns = List.of(
      CozyGrid.Column.text("title", "이름").asRequired(),
      CozyGrid.Column.select("kind", "종류", kindOptions),      // 셀렉트(콤보박스)
      CozyGrid.Column.radio("priority", "중요도", priorityOptions),
      CozyGrid.Column.checkbox("required", "필수"),
      CozyGrid.Column.date("due", "마감"));                      // number, email도 있다
  model.addAttribute("fieldsGrid", CozyGrid.bind("items", columns, form.getItems(), errors));
  ```

  - 열의 입력 방식은 text, number, date, email, select, radio, checkbox다. select·radio 열에만 선택지(`CozyOptions`)가 있고, 그 두 열에는 꼭 있어야 한다. `asRequired()`는 머리에 *를 붙인다. 검사는 서버가 한다.
  - 체크박스 칸은 Spring의 꺼짐 표시(`_items[0].required`)를 함께 보내므로 끈 값도 `false`로 바인딩된다.
  - **행 추가·복제·삭제**: 버튼이 감싼 폼을 `row=add:items`, `row=duplicate:items/0`, `row=delete:items/0`으로 제출한다(행은 0부터). 컨트롤러는 검증하지 않고 목록만 고친 뒤 같은 화면을 다시 그린다.

    ```java
    @PostMapping(params = "row")
    String row(@RequestParam String row, @ModelAttribute("form") FieldsForm form, Model model) {
        CozyGrid.RowAction.parse(row).ifPresent((a) -> a.apply("items", form.getItems(), ItemRow::new, ItemRow::copy));
        model.addAttribute("fieldsGrid", CozyGrid.bind("items", columns, form.getItems(), null));
        return "memo/api/fields";
    }
    ```

    형식이 다른 `row` 값과 없는 행은 예외다. 행 수가 정해진 그리드는 `new CozyGrid(key, columns, rows, false)`로 만들면 행 작업 버튼이 없다.
  - 화면 명세의 샘플 값은 레코드 필드를 모두 적는다(`required`, `options`, `error`는 `false`·`null`이라도). 형식은 cozy-ui 매니페스트의 `component/input-grid` 샘플과 같다.
- **내비 등록**: `CozyNavItem("메모", "/memo", 10, "notes")` 빈을 등록한다. 마지막 인자는 Tabler Icons 이름이고, 없는 이름이면 기동이 실패한다. 요청 경로가 그 경로로 시작하면 활성으로 표시한다.

## fragment 목록

계층은 layout → pattern → component → icon이다. 위 계층만 아래 계층을 쓴다(R4).

| id | 사용 | 메모 |
| --- | --- | --- |
| `layout/base` | `cozy/layout/base :: base(제목, ~{::main})` | 내비 없는 문서 |
| `layout/app-shell` | `cozy/layout/base :: appShell(제목, ~{::main})` | 앱 화면 기본 (Tabler 세로 내비) |
| `pattern/list-with-filter` | `listWithFilter(제목, 버튼, 필터폼, 결과)` | 결과는 `th:fragment` + `id`. 비면 `emptyStateAction`, 조건 때문이면 `noResults` |
| `pattern/form-page` | `formPage(제목, 상위이름, 상위주소, 폼)` | 만들기·수정. 가운데 48rem, 폼은 `<form class="card">` + `formActions`. 상위가 없으면 `null` |
| `pattern/details` | `details(제목, 상위이름, 상위주소, 버튼, 본문)` | 상세(읽기 전용). 버튼은 `<div class="btn-list">` [deleteConfirm] [수정] |
| `pattern/master-detail` | `masterDetail(제목, 목록, 상세)` | 목록은 `list-group list-group-flush`, 상세 영역 id는 `detail` |
| `pattern/overview` | `overview(제목, 버튼, 숫자카드, 본문)` | 대시보드. 숫자 카드는 `row row-deck row-cards` + `stat`, 없으면 `~{}` |
| `pattern/polling-panel` | `pollingPanel(제목, url, 초, 본문)` | `every N s` 갱신. 본문은 `list-group-flush`나 `card-body` |
| `component/page-header` | `pageHeader(제목, 상위이름, 상위주소, 버튼)` | 패턴이 쓴다. 상위가 있으면 제목 위 경로 |
| `component/form-actions` | `formActions('/memo', 'plus', '메모 만들기')` | 폼 카드 아래 오른쪽 [취소] [주 버튼] |
| `component/delete-confirm` | `deleteConfirm('memo-delete', '메모', ${memo.title}, '/memo/1/delete')` | 삭제 버튼 + 확인 창 [취소] [삭제] |
| `component/field` | `field('title', '제목', 'text')` | `<form th:object>` 안. 직접 적는 입력(text, textarea, number, date, email)과 켜고 끄는 checkbox, switch. 정해진 값 중에서 고르는 칸은 field가 아니라 아래 선택 컴포넌트가 맡는다 |
| `component/select` | `selectField('status', '상태', ${statusOptions})` | 셀렉트. 정해진 값 가운데 하나를 펼침 목록에서 고른다. 맨 앞 "선택하세요"는 아직 고르지 않은 상태 |
| `component/checkbox-group` | `checkboxGroup('channels', '알림 받을 곳', ${channelOptions})` | 체크박스. 늘어놓고 여럿 고른다. 폼 속성은 목록 |
| `component/input-grid` | `inputGrid('필드', ${fieldsGrid})` | 입력 그리드. 같은 모양의 여러 행을 표에서 바로 입력한다. 열마다 입력 방식(text, number, date, email, select, radio, checkbox)을 `CozyGrid.Column`으로 고르고, 칸 전체가 입력이다. 폼 안에 둔다 |
| `component/radio-group` | `radioGroup('priority', '중요도', ${priorityOptions})` | 라디오 버튼. 늘어놓고 하나 고른다. 선택지가 많으면 `selectField` |
| `component/data-table` | `dataTable(~{::#head}, ~{::.row})` | `<tr>` 머리행과 행들 |
| `component/pagination` | `pagination(${page}, '/memo', '#result', '#filter')` | `CozyPage`. 이전·다음과 번호 5개(`CozyPage.window()`) |
| `component/badge` | `badge('고정', 'neutral')` | tone: neutral, info, success, warning, critical. 분류는 neutral |
| `component/alert` | `alert('success', '메모를 만들었습니다')` | kind: info, success, warning, critical. 종류별 아이콘. 같은 파일의 `flash()`가 플래시를 그린다 |
| `component/stat` | `stat('notes', '12개', '전체 메모')` | 숫자 카드. 색 없음 |
| `component/empty-state` | `emptyState('notes', '없습니다', '설명')` | 만들기 버튼까지: `emptyStateAction(…, ~{::#action})`. 조건에 맞는 것이 없으면 `noResults('/memo')` |
| `component/modal` | `modal('id', '제목', ~{::#body})` | `data-bs-target="#id"`로 연다. 삭제 확인은 `deleteConfirm`. 확인 창은 모두 같은 구조다: Tabler의 `modal modal-blur fade`(바탕 흐림, 페이드), `role="dialog"`, 제목 `h2.modal-title`(id는 `{id}-title`), `aria-labelledby`가 제목을 가리킨다 |
| `component/toast` | `toast('success', '복사했습니다')` | 사소한 확인만. 중립 바탕, critical만 빨강. 스크립트는 `Cozy.toast(message, kind)` |
| `component/loading` | `loading()` | `htmx-indicator` |
| `icon/tabler` | `icon('plus')`, `iconFilled('star')` | Tabler Icons 3.51.0 전체 (외곽선 5,233개, 채운 1,054개) |

## 매니페스트 `cozy/fragments.yml`

프레임워크는 cozy-ui jar에, 앱은 자기 `resources/cozy/fragments.yml`에 둔다. 앱 매니페스트에는 `templates/fragments/` 아래의 공용 fragment와 모듈의 공개 fragment(`templates/{모듈}/spi/`)를 같은 형식으로 적는다. 패턴 카탈로그에는 `spi` 묶음으로 보인다. 파라미터 타입은 원시 값이나 spi DTO(`{모듈}.spi.dto.*`)다. 템플릿과 매니페스트가 어긋나면 R8([rules.md](rules.md)), 공개 fragment가 빠지면 C5([testing.md](testing.md))로 빌드가 실패한다. 파서는 `io.salvia95.cozy.ui.catalog.FragmentManifest`다.

```yaml
fragments:
  - id: component/badge                 # 고유 이름
    label: 배지                          # 사람에게 보일 이름 (선택). 워크벤치의 구조 트리가 쓴다
    template: cozy/component/badge      # 템플릿 경로
    signature: badge(text, tone)        # th:fragment와 같게
    purpose: 상태나 분류를 짧게 표시한다   # 용도 한 줄
    document: false                     # 완전한 HTML 문서를 그리면 true (레이아웃)
    params:                             # signature와 같은 순서
      - { name: text, type: String, required: true, description: 표시할 글자 }
      - { name: tone, type: String, required: true, description: "neutral, info, success, warning, critical",
          choices: { neutral: 중립, info: 정보, success: 성공, warning: 경고, critical: 위험 } }
    samples:                            # 상태 이름 → 값. 카탈로그와 렌더링 테스트가 쓴다
      default: { text: 고정, tone: neutral }
```

- **label**: 항목과 파라미터에 적을 수 있다(선택). 항목의 `label`은 워크벤치가 식별자 앞에 표시하는 이름이다. 프레임워크 항목은 범정부 UI/UX 디자인 시스템(KRDS)에 같은 역할의 컴포넌트가 있으면 그 명칭을 쓴다. fragment를 받는 파라미터의 `label`(`필터`)은 구조 트리에 "필터 영역"으로 표시된다. 없으면 종류와 식별자로 표시된다.
- **type**: `String`, `boolean`, `int`, `long`, `fragment` 또는 클래스 FQN이다. FQN이면 샘플 맵을 그 타입으로 변환한다.
- **choices**: `String` 파라미터가 받을 수 있는 값이다(선택). 값의 목록(`[compact, wide]`)이나 값 → 이름의 맵(`{ info: 정보 }`)으로 적는다. 값에는 작은따옴표와 역슬래시를 쓰지 않는다. 워크벤치의 속성 패널은 이 값을 선택 칸으로 보이고(`정보 (info)`), `cozy set arg`는 이 밖의 값을 거절한다. 프레임워크는 `badge`의 `tone`, `alert`·`toast`의 `kind`, `field`의 `type`에 적었다. 템플릿에 직접 적은 값은 아직 검사하지 않는다.
- **fragment 파라미터의 샘플 값**: fragment 이름을 쓴다. `template :: selector` 형식이고, 템플릿을 생략하면 `cozy/dev/catalog`에서 찾는다. 샘플 값이 없으면 자리 표시가 들어간다.
- **폼 샘플**: `_form` 맵은 `th:object`가 되고, `_errors` 맵(필드 → 메시지)은 검증 오류가 된다.
- **이름 규칙**: fragment를 받는 fragment는 모든 파라미터 이름이 `cozy`로 시작해야 한다(ADR-0021).
- **YAML 따옴표**: `{ }` 안의 값에 `,`, `?`, `{}`, `: `가 들어가면 따옴표로 감싼다.

## 화면 안의 상태 (Alpine.js)

서버를 거치는 동작은 htmx가, 서버를 거치지 않는 화면 안의 상태는 Alpine.js가 맡는다. 모달, 드롭다운, 툴팁은 Tabler(Bootstrap) JS를 그대로 쓴다.

| 맡는 것 | 도구 | 예 |
| --- | --- | --- |
| 서버를 거치는 모든 것. 서버가 돌려준 fragment가 기준이다 | htmx | 조회, 필터, 페이지 이동, 저장, 검증 오류 폼, 삭제 |
| 서버를 거치지 않는 화면 안의 상태 | Alpine.js | 펼치기·접기, 탭, 선택 수에 따른 버튼 활성, 입력 미리보기, 복사 |
| 이미 있는 컴포넌트 | Tabler JS | 모달, 드롭다운, 툴팁 |

- **판단 기준**: 새로고침해도 남아야 하는 값이면 서버(htmx)이고, 아니면 Alpine이다.
- **Alpine 컴포넌트는 서버에 요청하지 않는다.** `fetch`, `XMLHttpRequest`, `htmx.ajax`를 쓰지 않는다. 서버가 필요하면 `$dispatch`로 이벤트를 보내고 htmx가 `hx-trigger`로 받는다. 서버가 Alpine 상태를 바꿔야 하면 `HX-Trigger` 헤더의 이벤트로 알린다.
- **htmx가 바꾼 영역**은 Alpine이 새 요소로 다시 초기화한다. 그 안의 Alpine 상태는 사라지고 서버가 다시 그린 상태가 기준이다.
- **CSP 빌드**: CSP(`script-src 'self'`)가 eval을 막으므로 Alpine의 CSP 빌드(`@alpinejs/csp`)를 쓴다. 표준 빌드는 쓰지 않는다.

### 작성 규칙 (R16이 검사한다)

앱 템플릿의 Alpine 속성은 `cozy attach`로 붙이고 뗀다([cli.md](cli.md)의 attach). 명령이 컴포넌트가 내놓는 이름인지 확인하고, 아래 규칙대로 쓴다.

- 속성은 긴 이름만 쓴다: `x-on:click`, `x-bind:class`. 줄임 표기(`@click`, `:class`)는 쓰지 않는다.
- `x-data`에는 등록된 컴포넌트 이름만 쓴다: `x-data="cozyCopy"`. 인라인 객체(`x-data="{ open: false }"`)와 인자는 쓰지 않는다.
- 이벤트와 값은 감싸는 컴포넌트가 내놓는 이름만 부른다. 같은 템플릿 안의 가장 가까운 `x-data`가 그 컴포넌트다. 앱 컴포넌트는 동작 명세의 상태·계산·동작, 프레임워크 컴포넌트는 아래 표의 메서드다.
- 서버 값은 `x-data` 요소의 `data-*` 속성으로 넘긴다(`th:data-copy-text="${…}"`). 컴포넌트가 `init`에서 `this.$root`를 잡아 두고 그 `dataset`에서 읽는다(다른 컴포넌트 안의 요소에서 부르면 `$root`가 그 안쪽 컴포넌트이기 때문이다). Thymeleaf로 `x-*` 값을 만들지 않는다(`th:attr`, `th:x-*`).
- 쓰는 디렉티브: `x-data`, `x-init`, `x-show`, `x-bind:*`, `x-on:*`, `x-model`, `x-text`, `x-ref`, `x-cloak`, `x-transition`, `x-if`, `x-for`, `x-id`, `x-effect`, `x-ignore`. `x-html`과 `x-teleport`는 쓰지 않는다.
- `iframe`과 `script` 요소에는 식이 있는 디렉티브를 달지 않는다. CSP 빌드가 실행하지 않고 그 컴포넌트의 나머지 초기화도 멈춘다. iframe의 `load`는 감싸는 요소의 `x-on:load.capture`로 받는다.
- 식은 짧게 쓴다. 속성 읽기, 메서드 호출, 변수 대입(`open = !open`), 비교만 쓴다. 화살표 함수, 템플릿 문자열, 전개, 속성 대입(`user.name = …`), 전역(`window`, `console`, `Math` 등)은 CSP 빌드가 읽지 못한다. 그런 로직은 컴포넌트의 getter와 메서드로 옮긴다.
- `x-cloak`은 Alpine이 시작할 때까지 요소를 숨긴다(`cozy.css`). 스크립트가 있어야만 쓸모 있는 요소(복사 버튼 등)에만 쓴다. 보기와 저장 같은 기본 동작은 스크립트 없이도 된다.

### 프레임워크 컴포넌트

`cozy.js`가 등록한다. 이름은 `cozy`로 시작한다. 목적, 받는 `data-*`, 메서드는 cozy-ui의 `cozy/behaviors.yml`(프레임워크 명세)에 있고, `cozy attach`가 이 명세로 이름을 확인한다. `cozy.js`는 컴포넌트마다 `// cozy:component 이름 members=… data=…` 줄을 둔다(R16이 읽는다). 아래 표는 명세에서 만든다(cozy-cli의 `FrameworkBehaviorsTest`가 명세, `cozy.js`, 이 표가 같은지 확인한다). 메서드 이름 뒤의 `(이름)`은 글자 인자 하나를 받는다는 뜻이다.

<!-- cozy:framework-behaviors (cozy-ui의 cozy/behaviors.yml에서 만든 표) -->
| 이름 | 받는 `data-*` | 메서드 | 동작 |
| --- | --- | --- | --- |
| `cozyCopy` | `data-copy-text`: 복사할 글자 | `copy()`: x-on:click. 글자를 복사한다 | 글자를 복사하고 "복사했습니다" 토스트를 띄운다. 복사할 수 없으면 직접 선택하라는 토스트를 띄운다 |
| `cozyDisclosure` | `data-disclosure-closed`: 처음에 접힌 부분의 이름(공백으로 구분)<br>`data-disclosure-remember`: 접은 부분을 브라우저에 기억할 이름(선택) | `toggle(이름)`: x-on:click. 그 부분을 펼치거나 접는다<br>`isOpen(이름)`: x-show, x-bind:aria-expanded. 펼쳤는가<br>`isClosed(이름)`: 접었는가 | 이름 붙인 부분을 펼치고 접는다. 한 요소 안에 여러 부분을 둘 수 있다. 기억하면 다시 열어도 그대로다(저장소를 쓸 수 없으면 기억하지 않는다) |
| `cozyFocusKey` | `data-focus-key`: 누를 글쇠(기본 /) | `focusOnKey()`: 칸의 x-on:keydown.window | 글을 적는 중이 아닐 때 그 글쇠를 누르면 이 칸으로 옮겨 간다 |
| `cozyConfirm` | `data-confirm-message`: 물을 문장 | `ask()`: 폼의 x-on:submit | 취소하면 내지 않는다. 일반 POST 폼에 쓴다(앱 메뉴의 종료, 업데이트). htmx로 내는 폼은 hx-confirm, 지우기는 component/delete-confirm(확인 창)을 쓴다 |
| `cozyDirtyForm` | 없음 | `mark()`: x-on:input, x-on:change. 입력이 바뀌었다<br>`clean()`: x-on:submit. 내는 중이다<br>`settled()`: x-on:htmx:after-settle.camel. 서버가 오류 폼을 돌려주었는지 본다<br>`warn()`: x-on:beforeunload.window | 안의 POST 폼에 저장하지 않은 입력이 있으면 떠날 때 브라우저가 확인한다. htmx가 오류 폼을 돌려주어도 저장되지 않은 것으로 본다. pattern/form-page가 붙이므로 화면이 따로 쓰지 않는다 |
| `cozyAutoHeight` | 없음 | `fit()`: 감싸는 요소의 x-on:load.capture(iframe에는 식을 달 수 없다) | 안의 iframe을 내용 높이에 맞추고, 내용이 바뀌면 다시 맞춘다. 카탈로그 미리보기가 쓴다 |
| `cozyIconList` | 없음 | `filter()`: 입력의 x-on:input<br>`copy()`: 목록의 x-on:click | 아이콘 목록을 거르고 누른 칸의 글자를 복사한다. 칸에는 data-name(거를 이름)과 data-copy-text(복사할 글자)를 둔다. 아이콘 목록이 쓴다 |
<!-- /cozy:framework-behaviors -->

```html
<button type="button" class="btn btn-sm" x-data="cozyCopy" th:data-copy-text="${#strings.listJoin(memo.bodyLines, '&#10;')}" x-on:click="copy" x-cloak>복사</button>

<div x-data="cozyDisclosure" data-disclosure-closed="filter">
  <button type="button" class="btn" x-on:click="toggle('filter')" x-bind:aria-expanded="isOpen('filter')" aria-controls="memo-filter">필터</button>
  <div id="memo-filter" x-show="isOpen('filter')" x-cloak>…</div>
</div>

<input class="form-control" type="search" name="q" aria-keyshortcuts="/" x-data="cozyFocusKey" x-on:keydown.window="focusOnKey">

<form method="post" th:action="@{/memo/archive}" x-data="cozyConfirm" x-on:submit="ask" data-confirm-message="모두 보관할까요?">…</form>
```

- **없앤 속성 (2.0.0)**: 동작을 붙이던 `data-cozy-confirm`, `data-cozy-copy`, `data-cozy-autosize`, `data-cozy-icon-filter`는 없앴다. R16이 오류로 보이고, `cozy migrate alpine`이 confirm과 copy를 새 모양으로 바꾼다([cli.md](cli.md)).

- **프레임워크 화면의 스크립트**: `alpine:init`에서 컴포넌트를 등록하는 스크립트는 Alpine보다 먼저 실려야 한다. 모델의 `cozyScripts`(경로 목록)가 있으면 `base` 레이아웃이 앱 모듈의 파일 다음, Alpine 앞에 싣는다. 워크벤치처럼 화면 하나에만 필요한 프레임워크 스크립트가 쓴다(`/cozy/workbench.js`). 앱 모듈은 아래 "앱 컴포넌트"처럼 레이아웃이 알아서 싣는다.

### 앱 컴포넌트

- **자리**: 모듈마다 `src/main/resources/static/{모듈}/{모듈}.js` 하나다. 이름은 모듈 이름으로 시작한다(`memoBodyCount`). 이 파일은 모듈의 동작 명세(`behaviors/{모듈}.yml`, 아래 "동작 명세")에서 cozy가 만든다. `cozy add behavior`가 명세와 파일을 함께 쓰고(지우기는 `cozy remove behavior`, 이름 바꾸기는 `cozy rename behavior`), 템플릿에는 `cozy attach`로 붙인다. JS를 손으로 고치면 C12가 빌드를 실패시킨다([cli.md](cli.md), [testing.md](testing.md)).
- **싣는 법**: `base` 레이아웃이 `cozy.js` 다음, Alpine 앞에 모든 모듈의 파일을 모듈 이름 순서로 이은 묶음 하나를 싣는다(`cozyModuleScript`). 주소는 `/cozy/modules.js?v={내용의 SHA-256 앞 12자}`다. 모듈 파일이 없으면 싣지 않는다.
  - **캐시**: `v`가 지금 내용과 같으면 `Cache-Control: public, max-age=31536000, immutable`, 다르거나 없으면 `no-cache`다. 앱을 업데이트해 내용이 바뀌면 주소가 바뀌므로 옛 파일이 남지 않는다.
  - **개발 중**: 파일이 빌드 결과 폴더(`target/classes`)에 있으면 화면을 그릴 때마다 다시 읽는다. 다시 빌드한 JS가 앱을 다시 시작하지 않아도 보인다.
  - **오류**: 각 부분 앞에 `/* static/memo/memo.js */` 주석을, 끝에 `;` 한 줄을 둔다(다음 파일과 이어 읽히지 않게). 한 파일의 문법 오류는 묶음 전체를 멈춰 모든 모듈의 컴포넌트가 없어진다.
    - **빌드에서 막는다**: `mvn verify`의 C10이 모듈마다 그 파일의 문법을 읽어 본다([testing.md](testing.md)).
    - **실행 중에 알린다**: 스크립트의 `data-cozy-parts`(`static/memo/memo.js:2;…`, 파일과 묶음에서의 첫 줄)로 `cozy.js`가 브라우저 오류의 묶음 줄을 원래 파일의 줄로 바꿔 콘솔에 낸다(`[cozy] static/memo/memo.js 7줄: …`). 문법 오류면 "화면 동작을 불러오지 못했습니다" 토스트도 띄운다.
  - 폴더 이름과 파일 이름이 다른 JS는 넣지 않는다. 모듈마다 따로 주는 주소는 없다(`/memo/memo.js`는 모듈의 화면 경로 `/memo/{id}`와 겹친다).
- **쓰는 곳**: 앱 템플릿은 `cozy` 컴포넌트와 자기 모듈의 컴포넌트만 쓴다. `fragments/`와 `error/`는 `cozy` 컴포넌트만 쓴다(R16).
- **예**: reference-app의 `memoBodyCount`(메모 본문 아래 글자 수)가 `memo/internal/fields`에서 쓴다. 처음 글자 수는 부르는 화면이 fragment 파라미터로 넘긴다(R10: internal fragment는 화면의 모델을 읽지 않는다).

```html
<div x-data="memoBodyCount" th:data-length="${bodyLength}" data-limit="5000" x-on:input="count">
  <div th:replace="~{cozy/component/field :: field('body', '본문', 'textarea')}"></div>
  <div class="form-hint mt-n2 mb-3" x-text="label" x-cloak></div>
</div>
```

### 동작 명세 (`behaviors/{모듈}.yml`)

모듈의 앱 컴포넌트는 동작 명세에 선언한다. 명세가 원본이고, `static/{모듈}/{모듈}.js`는 cozy가 이 명세에서 만든다(ADR-0043). 명세는 `cozy add behavior`가 쓰고, 손으로 고쳤으면 `cozy sync behaviors`가 확인하고 정리한 모양으로 다시 쓴다.

```yaml
# memo 모듈의 화면 안 동작(동작 명세). cozy add behavior가 고치고, 이 명세에서 static/memo/memo.js를 만든다
components:
  bodyCount:
    purpose: "본문을 쓰는 동안 글자 수와 한도를 보인다"
    state:
      length: "number@data-length"
      limit: "number@data-limit"
    computed:
      over: "and(gt(limit, 0), gt(length, limit))"
      text: "format('{length} / {limit}자', 'ko-KR')"
      label: "if(over, format('{text} · 한도를 넘었습니다'), text)"
    actions:
      count: "within('textarea', set(length, length(event.value)))"
```

| 키 | 값 |
| --- | --- |
| `components.{이름}` | 컴포넌트. 등록 이름은 모듈 이름 + 이 이름(`memoBodyCount`). 영문 소문자로 시작하는 카멜 표기 |
| `purpose` | 목적 한 문장. 꼭 있어야 한다. 만든 JS의 주석, `cozy describe`, 워크벤치가 보인다 |
| `state.{이름}` | `종류[=처음 값][@data-이름]`. 종류는 `bool`(처음 `false`), `number`(`0`), `text`(`''`), `list`(`[]`, 처음 값은 쉼표로 `=a,b`). `@data-limit`이면 `x-data` 요소의 `data-limit`에서 읽고, 없으면 처음 값이다. `list`는 공백으로 나눈다 |
| `computed.{이름}` | 식 하나. 상태와 다른 계산만 읽는다. 템플릿은 `x-text="label"`, `x-show="over"`처럼 이름으로 읽는다 |
| `actions.{이름}` | 단계를 `;`로 잇는다. 템플릿은 `x-on:input="count"`처럼 이름으로 부르고, Alpine이 이벤트를 넘긴다 |
| `listens.{모듈:이벤트}` | 창(window)에 그 화면 이벤트가 오면 부를 동작. 다른 컴포넌트의 `emit`과 서버의 `HX-Trigger` 머리가 보낸다 |

- 상태, 계산, 동작의 이름은 한 컴포넌트 안에서 겹치지 않는다(템플릿이 이름 하나로 부른다). `init`, `destroy`, `root`, `event`와 JavaScript 예약어는 쓰지 않는다.
- 모르는 키, 형식이 틀린 값, 낱말 밖의 함수, 없는 이름, 종류가 맞지 않는 인자, 돌고 도는 계산은 실패로 닫는다. 명령은 아무 파일도 바꾸지 않는다.
- 값은 큰따옴표로 감싼다(`: `나 `#`가 있는 식을 YAML이 다르게 읽지 않게). cozy가 쓰는 명세는 늘 그렇다.

**값**

| 모양 | 뜻 |
| --- | --- |
| `length`, `over` | 같은 컴포넌트의 상태나 계산 |
| `0`, `1.5`, `true`, `false` | 숫자, 참거짓 |
| `'list'` | 글자. 작은따옴표 안의 `'`는 `\'` |
| `event.value`, `event.checked` | 이벤트가 일어난 요소의 값(글자), 체크 여부. 동작에서만 쓴다 |
| `event.data.id` | 이벤트가 일어난 요소부터 `x-data` 요소까지 올라가며 처음 찾은 `data-id`(글자). 동작에서만 쓴다 |
| `event.detail.message` | 받은 화면 이벤트의 `detail.message`. 없으면 빈 글자. 동작에서만 쓴다 |

**계산 함수**

| 함수 | 결과 |
| --- | --- |
| `not(a)`, `and(a, b, …)`, `or(a, b, …)` | 참거짓. 인자도 참거짓 |
| `eq(a, b)`, `ne(a, b)` | 같음, 다름(종류까지 비교) |
| `gt`, `lt`, `ge`, `le` | 숫자 비교 (`>`, `<`, `>=`, `<=`) |
| `empty(a)` | 글자나 목록이 비었는가 |
| `count(list)`, `length(text)` | 목록의 수, 글자 수 |
| `includes(list, a)` | 목록에 그 값이 있는가 |
| `number(a)` | 글자를 숫자로(읽을 수 없으면 `0`). `set(n, number(event.value))` |
| `if(조건, 참일 때, 거짓일 때)` | 두 값은 같은 종류 |
| `format('글자 {이름}', 'ko-KR')` | `{이름}` 자리에 상태나 계산의 값. 둘째 인자가 있으면 숫자를 그 형식으로(`5,000`). 목록은 `, `로 잇는다. 중괄호 글자는 `{{`, `}}` |

**동작 단계**

| 단계 | 하는 일 |
| --- | --- |
| `set(상태, 값)` | 값을 넣는다. 종류가 같아야 한다 |
| `toggle(bool 상태)` | 참거짓을 뒤집는다 |
| `increment(number 상태[, 숫자])`, `decrement(…)` | 더하고 뺀다(기본 1) |
| `addItem(list, 값)`, `removeItem(list, 값)`, `toggleItem(list, 값)` | 목록에 넣고(이미 있으면 그대로), 빼고, 있으면 빼고 없으면 넣는다 |
| `reset(상태)` | 처음 값(`data-*`로 읽었으면 그 값)으로 되돌린다 |
| `emit('모듈:이름', 상태나 계산 …)` | 화면 이벤트를 낸다(Alpine `$dispatch`, 창까지 올라간다). 이름은 이 모듈의 이름으로 시작한다. `detail`에 적은 이름과 값이 들어간다. htmx는 `hx-trigger="memo:selection-changed from:window"`로 받는다 |
| `when(조건, 단계 …)` | 조건이 참일 때만 |
| `within('선택자', 단계 …)` | 이벤트가 `x-data` 요소 안의 그 선택자 요소에서 일어났을 때만(`within('textarea', …)`) |

- 낱말로 적을 수 없는 동작은 앱에 손으로 쓰지 않는다. `cozy` 컴포넌트나 낱말을 더하는 일은 프레임워크에서 한다(ADR-0043 §7).
- 만든 JS는 지금 규칙을 따른다: `alpine:init`의 `Alpine.data` 등록, 서버 값은 `data-*`, 서버에 요청하지 않음. 받는 이벤트는 컴포넌트가 없어질 때(htmx가 영역을 바꿀 때) 뗀다.
- 낱말 전부를 쓰는 예시는 cozy-cli의 `src/test/resources/behaviors/demo.yml`이고, 그 명세에서 만든 `demo.js`와 브라우저 시험 페이지 `demo.html`이 옆에 있다([testing.md](testing.md)).

## 설정과 자산

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `cozy.dev.catalog.enabled` | dev 프로필이면 `true`, 아니면 `false` | `/cozy/dev/catalog` 등록. 매니페스트 fragment와 화면 스펙(`specs/*.yml`)을 상태별로 미리 본다 |

- **화면 스펙의 원천 (`ScreenSpecs`)**: 카탈로그는 화면 미리보기를 그릴 때마다 `ScreenSpecs` 빈에 스펙을 묻는다. 기본 빈은 클래스패스의 `specs/*.yml`을 기동할 때 한 번 읽는다. 다른 `ScreenSpecs` 빈을 등록하면 그것을 쓴다(워크벤치는 소스 폴더에서 읽는다, [workbench.md](workbench.md)).
- **`ScreenSpec.verify()`**: 모든 상태의 fixture를 모델 타입으로 바꿔 본다. 렌더링할 때와 같은 변환이고, 맞지 않으면 상태와 모델 이름을 담아 실패한다.
| `cozy.ui.view-module-check` | `warn` (cozy-test가 있는 테스트에서는 `fail`) | 컨트롤러의 모듈과 뷰 이름의 첫 경로가 다르면 경고하거나(`warn`) 요청을 실패시킨다(`fail`). 검사 C7 |

- **자산 경로**: 모든 자산은 `/cozy/vendor/{이름}/{버전}/`에서 서빙된다. Tabler 1.7.5, Tabler Icons 3.51.0(스프라이트 2개, 약 2.9MB), htmx 2.0.11, Alpine.js CSP 빌드 3.17.5(`alpinejs-csp`), Pretendard 1.3.9이고, 폴더마다 LICENSE가 있다.
- **스크립트 순서**: layout은 Tabler → htmx → `cozy.js` → 앱 모듈의 컴포넌트 묶음(`/cozy/modules.js`) → 화면의 `cozyScripts` → Alpine 순서로 `defer`로 싣는다. `cozy.js`가 `alpine:init`에서 컴포넌트를 등록하고 Alpine이 마지막에 시작한다.
- **아이콘 방식**: `<svg><use href="/cozy/vendor/tabler-icons/3.51.0/tabler-sprite.svg#tabler-plus">`. 같은 출처의 스프라이트를 한 번 받아 캐시하므로 CSP를 바꾸지 않는다. Java에서 이름을 확인할 때는 `CozyIcons.outline()`, `CozyIcons.filled()`, `CozyIcons.requireOutline(name)`을 쓴다.
- **아이콘 목록**: `/cozy/dev/catalog/icons`(카탈로그가 켜진 앱)에서 이름으로 찾고, 칸을 누르면 `icon('이름')`이 복사된다.
- **자산 버전 바꾸기**: `distribution/vendor-assets/assets.conf`를 고치고 `fetch.sh`를 실행한다. 그다음 `base.html`의 경로를 맞춘다. 빌드가 `cozy-ui/vendor.lock`과 대조한다.
- **app-shell의 앱 메뉴**: 사이드 내비 아래에 "진단 내보내기"와 "종료"가 내비 항목 모양으로 있다. 런타임의 `POST /cozy/diagnostics`, `POST /cozy/shutdown`을 부른다([runtime.md](runtime.md)).
- **업데이트 배너**: 새 버전이 있으면 layout 맨 위에 배너가 뜬다. 필수 업데이트면 빨간색이다. 설치할 수 있으면 "지금 업데이트" 버튼이 있고, 없으면 이유가 보인다. 직전 업데이트의 결과(성공, 롤백)도 같은 자리에 한 번 보인다([distribution.md](distribution.md)).
- **cozy.js**:
  - 변경 요청에 CSRF 헤더를 넣는다.
  - 4xx·5xx 응답과 연결 실패는 토스트로 알리고, 요청 중에는 맨 위 진행 막대를 보인다.
  - 검증 오류(`.is-invalid`)가 있으면 첫 칸으로 포커스를 옮긴다.
  - 서버는 `HX-Trigger: {"cozy:toast": {"message": "…", "kind": "success"}}`로 토스트를 띄울 수 있다.
  - 화면 안의 동작(확인, 떠날 때 확인, 복사, 미리보기 높이, 아이콘 거르기)은 위의 cozy 컴포넌트다. 문서 전체에 이벤트를 다는 것은 htmx 전역 설정과 검증 오류 포커스뿐이다(`CozyScriptTest`).
