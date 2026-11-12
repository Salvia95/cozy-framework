# 마크업 작성

화면 템플릿의 마크업을 직접 쓰거나 고칠 때의 규칙이다. 원문은 `docs/conventions/ui.md`, `docs/conventions/structure.md`의 "템플릿", `docs/conventions/rules.md`의 R1~R15다.

## 화면 한 장의 골격

```html
<!DOCTYPE html>
<!--/* 메모 목록 화면(api): list-with-filter 패턴. 필터와 페이지 이동은 result fragment만 바꾼다. */-->
<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
<body>
<main>
  <section th:replace="~{cozy/pattern/list-with-filter :: listWithFilter('메모', ~{::#memo-actions}, ~{::#filter}, ~{::#result})}">
    <a id="memo-actions" class="btn btn-primary" th:href="@{/memo/new}"><svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>메모 만들기</a>
    <form id="filter" th:hx-get="@{/memo}" hx-target="#result" hx-swap="outerHTML" hx-push-url="true"> … </form>
    <div id="result" th:fragment="result"> … </div>
  </section>
</main>
</body>
</html>
```

- `<html>`은 `appShell(제목, ~{::main})`으로 바꾼다. 본문은 `<main>` 하나다.
- `<main>` 안에서 패턴 하나를 부르고, 패턴에 넘길 영역을 같은 파일 안에 id나 class로 둔다.
- 파일 맨 위 파서 주석(`<!--/* … */-->`)에 이 화면이 무엇이고 어떤 패턴과 상태를 쓰는지 적는다. 렌더링 결과에 남지 않는다.

## Tabler 구조를 따른다

- 마크업은 Tabler의 구조와 클래스를 그대로 쓴다. 비슷한 모양을 직접 만들지 않는다.
- 카드 안에는 `card-body`, `list-group list-group-flush`, `card-table` 중 하나를 둔다. 내용이 카드 테두리에 바로 닿으면 결함이다.
- 상세의 이름·값 쌍은 `datagrid`(`datagrid-item`, `datagrid-title`, `datagrid-content`)다.
- 숫자 카드 줄은 `row row-deck row-cards` 안의 `col`과 `stat`이다.
- 검색 칸은 `input-icon`과 `input-icon-addon`(왼쪽 검색 아이콘, 오른쪽 `loading()`)이다.
- 표의 짧은 칸은 `w-1`, 줄바꿈하지 않을 칸은 `text-nowrap`이다.
- 화면 틀(`container-xl`, `page-header`, `page-body`)은 패턴이 그린다. 화면에서 만들지 않는다.

## 쓰지 않는 것

| 쓰지 않는 것 | 이유 | 대신 |
| --- | --- | --- |
| `style=`, `<style>` | CSP가 인라인 스타일을 막는다 | `cozy.css`의 클래스 |
| `<script>`, `onclick=`, `hx-on` | CSP가 인라인 스크립트를 막는다 | `cozy.js`, `data-cozy-*` 속성 |
| `<link>` | 자산은 레이아웃이 모두 넣는다 | — |
| 외부 URL | 폐쇄망이다 | `/cozy/vendor/…` |
| 색 클래스 | 색은 의미를 받는 컴포넌트가 고른다(R14) | tone을 받는 컴포넌트 |
| `th:include` | Thymeleaf 3에서 권장하지 않는다(R7 경고) | `th:replace`, `th:insert` |
| 동적 참조 `~{${view} :: body}` | 검사할 수 없다(R7) | 글자 그대로의 참조 |

## htmx로 바꿀 영역

- 바꿀 영역에는 `th:fragment`와 `id`를 함께 둔다: `<div id="result" th:fragment="result">`.
- 컨트롤러는 `CozyViews.pageOrFragment("memo/api/list", "result")`를 반환한다. htmx 요청이면 그 fragment만, 아니면 화면 전체다.
- 요청에는 `hx-target`, `hx-swap="outerHTML"`을 쓰고, 목록 조건처럼 주소에 남아야 하면 `hx-push-url="true"`를 쓴다.
- 폼 오류를 다시 그릴 때는 폼 자체를 fragment로 둔다(`<form id="memoForm" th:fragment="memoForm" … hx-target="this" hx-swap="outerHTML">`).
- 요청 중 표시는 `loading()`을 요청하는 요소 안에 두거나 `hx-indicator`로 가리킨다.
- 쓰는 htmx 속성은 `CozyHtmlValidator.HTMX_ATTRIBUTES`에 있는 것뿐이다. 없는 `hx-*`는 HTML 검사(C4)에서 실패한다.

## fragment 이름

- fragment 이름은 같은 파일에 있는 HTML 요소 이름과 겹치지 않는다(R12). Thymeleaf가 `~{t :: form}`으로 `th:fragment="form"`과 `<form>` 요소를 함께 고르기 때문이다. 이름은 `memoForm`, `memoTable`처럼 모듈 이름을 붙인다.
- 패턴 인자로 넘기는 영역은 같은 파일 안의 id(`~{::#id}`)나 class(`~{::.row}`)로 고른다.
- `cozy`로 시작하는 모델 속성 이름은 쓰지 않는다. 프레임워크 전용이다(`cozyApp`, `cozyNav`, `cozyFlash` 등).

## `th:replace`와 조건·반복

- `th:replace`는 `th:if`, `th:each`보다 먼저 실행된다. 조건부나 반복으로 넣으려면 감싼다.

  ```html
  <th:block th:if="${row.pinned}"><span th:replace="~{cozy/component/badge :: badge('고정', 'neutral')}"></span></th:block>
  <th:block th:each="m : ${memos}"><tr th:replace="~{memo/internal/row :: row(${m})}"></tr></th:block>
  ```

- 같은 요소에 `th:each`와 `th:replace`를 함께 쓰면 R11로 실패한다.

## 아이콘

- `<svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>`. 채운 아이콘은 `iconFilled('star')`.
- 이름은 `/cozy/dev/catalog/icons`에서 찾는다. 없는 이름을 글자로 쓰면 R13으로 빌드가 실패하고 비슷한 이름을 알려 준다.
- Java에서 넘기는 이름(`CozyNavItem`의 아이콘 등)은 `CozyIcons.requireOutline`이 기동할 때 확인한다.
- 아이콘은 글자를 거든다. 아이콘만 있는 버튼에는 `aria-label`을 단다.

## 템플릿 폴더와 참조 방향

| 폴더 | 무엇 | 모델을 읽는가 |
| --- | --- | --- |
| `templates/{모듈}/api/` | 컨트롤러가 그리는 화면. 화면마다 화면 명세가 있어야 한다 | 읽는다 |
| `templates/{모듈}/spi/` | 다른 모듈이 부르는 fragment. 매니페스트에 등록한다 | 파라미터만(R10) |
| `templates/{모듈}/internal/` | 같은 모듈의 api·spi가 같이 쓰는 조각 | 파라미터만(R10) |
| `templates/fragments/{layout,pattern,component,icon}/` | 앱 공용 fragment. 매니페스트에 등록한다 | 파라미터만 |
| `templates/cozy/` | 프레임워크(cozy-ui jar). 앱은 쓰지 않는다 | — |

- 같은 모듈 안에서는 api → spi → internal 방향으로만 참조한다(R9).
- 다른 모듈의 템플릿은 `spi/` 아래 것만, 의존을 선언했을 때만 참조한다(R3). 선언은 `cozy link {내 모듈} --uses {그 모듈}`로 한다.
- 만들기와 수정이 같이 쓰는 입력 칸은 `internal/` 조각으로 빼고, 칸은 감싸는 폼의 `th:object`에 바인딩되게 한다.

## 생성 앱에서

- 생성 앱의 화면은 `cozy add crud`, `cozy add screen`, `cozy add field`가 만들고 고친다. 구조 파일을 손으로 만들거나 옮기지 않는다. 남은 `cozy:slot` 칸만 채운다.
- 생성 앱의 에이전트 문서(`docs/agents/`)의 `screens` 플레이북이 같은 규칙을 앱 쪽 말로 담고 있다. 이 기준과 다르면 둘 다 고친다.
