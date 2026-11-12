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

## 설정과 자산

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `cozy.dev.catalog.enabled` | dev 프로필이면 `true`, 아니면 `false` | `/cozy/dev/catalog` 등록. 매니페스트 fragment와 화면 스펙(`specs/*.yml`)을 상태별로 미리 본다 |

- **화면 스펙의 원천 (`ScreenSpecs`)**: 카탈로그는 화면 미리보기를 그릴 때마다 `ScreenSpecs` 빈에 스펙을 묻는다. 기본 빈은 클래스패스의 `specs/*.yml`을 기동할 때 한 번 읽는다. 다른 `ScreenSpecs` 빈을 등록하면 그것을 쓴다(워크벤치는 소스 폴더에서 읽는다, [workbench.md](workbench.md)).
- **`ScreenSpec.verify()`**: 모든 상태의 fixture를 모델 타입으로 바꿔 본다. 렌더링할 때와 같은 변환이고, 맞지 않으면 상태와 모델 이름을 담아 실패한다.
| `cozy.ui.view-module-check` | `warn` (cozy-test가 있는 테스트에서는 `fail`) | 컨트롤러의 모듈과 뷰 이름의 첫 경로가 다르면 경고하거나(`warn`) 요청을 실패시킨다(`fail`). 검사 C7 |

- **자산 경로**: 모든 자산은 `/cozy/vendor/{이름}/{버전}/`에서 서빙된다. Tabler 1.7.4, Tabler Icons 3.51.0(스프라이트 2개, 약 2.9MB), htmx 2.0.11, Pretendard 1.3.9이고, 폴더마다 LICENSE가 있다.
- **아이콘 방식**: `<svg><use href="/cozy/vendor/tabler-icons/3.51.0/tabler-sprite.svg#tabler-plus">`. 같은 출처의 스프라이트를 한 번 받아 캐시하므로 CSP를 바꾸지 않는다. Java에서 이름을 확인할 때는 `CozyIcons.outline()`, `CozyIcons.filled()`, `CozyIcons.requireOutline(name)`을 쓴다.
- **아이콘 목록**: `/cozy/dev/catalog/icons`(카탈로그가 켜진 앱)에서 이름으로 찾고, 칸을 누르면 `icon('이름')`이 복사된다.
- **자산 버전 바꾸기**: `distribution/vendor-assets/assets.conf`를 고치고 `fetch.sh`를 실행한다. 그다음 `base.html`의 경로를 맞춘다. 빌드가 `cozy-ui/vendor.lock`과 대조한다.
- **app-shell의 앱 메뉴**: 사이드 내비 아래에 "진단 내보내기"와 "종료"가 내비 항목 모양으로 있다. 런타임의 `POST /cozy/diagnostics`, `POST /cozy/shutdown`을 부른다([runtime.md](runtime.md)).
- **업데이트 배너**: 새 버전이 있으면 layout 맨 위에 배너가 뜬다. 필수 업데이트면 빨간색이다. 설치할 수 있으면 "지금 업데이트" 버튼이 있고, 없으면 이유가 보인다. 직전 업데이트의 결과(성공, 롤백)도 같은 자리에 한 번 보인다([distribution.md](distribution.md)).
- **cozy.js**:
  - 변경 요청에 CSRF 헤더를 넣는다.
  - `data-cozy-confirm="문장"`이 있는 폼은 제출 전에 확인을 받는다.
  - 4xx·5xx 응답과 연결 실패는 토스트로 알리고, 요청 중에는 맨 위 진행 막대를 보인다.
  - 검증 오류(`.is-invalid`)가 있으면 첫 칸으로 포커스를 옮긴다.
  - `form-page` 안의 POST 폼에 저장하지 않은 입력이 있으면 떠날 때 브라우저가 확인한다. 서버는 `HX-Trigger: {"cozy:toast": {"message": "…", "kind": "success"}}`로 토스트를 띄울 수 있다.
