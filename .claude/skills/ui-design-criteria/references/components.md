# 패턴과 컴포넌트 목록

어떤 패턴과 컴포넌트가 있는지, 시그니처와 인자가 무엇인지, 비슷한 것 가운데 무엇을 골라야 하는지 확인할 때 읽는다. 원본은 `cozy-ui/src/main/resources/cozy/fragments.yml`(매니페스트)과 `templates/cozy/` 아래 템플릿이다. 이 목록과 매니페스트가 다르면 매니페스트가 맞다.

## 레이아웃

| 표시 이름 | 호출 | 언제 |
| --- | --- | --- |
| 기본 레이아웃 | `cozy/layout/base :: appShell(제목, ~{::main})` | 모든 앱 화면 |
| 빈 레이아웃 | `cozy/layout/base :: base(제목, ~{::main})` | 내비가 필요 없는 문서(오류 화면, 카탈로그) |

## 패턴

| 표시 이름 | 호출 | 언제 |
| --- | --- | --- |
| 목록 탐색 | `cozy/pattern/list-with-filter :: listWithFilter(제목, 버튼, 필터폼, 결과)` | 목록. 결과는 `th:fragment` + `id` |
| 입력폼 | `cozy/pattern/form-page :: formPage(제목, 상위이름, 상위주소, 폼)` | 만들기·수정. 폼은 `<form class="card">` + `formActions` |
| 상세 정보 확인 | `cozy/pattern/details :: details(제목, 상위이름, 상위주소, 버튼, 본문)` | 상세(읽기 전용). 버튼은 `<div class="btn-list">` [deleteConfirm] [수정] |
| 대시보드 | `cozy/pattern/overview :: overview(제목, 버튼, 숫자카드, 본문)` | 개요. 숫자 카드가 없으면 `~{}` |
| 목록·상세 분할 | `cozy/pattern/master-detail :: masterDetail(제목, 목록, 상세)` | 목록을 보며 옆에서 읽기. 상세 영역 id는 `detail` |
| 자동 갱신 패널 | `cozy/pattern/polling-panel :: pollingPanel(제목, 주소, 초, 본문)` | 주기적으로 다시 받는 카드. 본문은 `list-group-flush`나 `card-body` |

- 상위가 없으면 상위 이름과 상위 주소에 `null`, 버튼이 없으면 `~{}`를 넘긴다.

## 컴포넌트

| 표시 이름 | 호출 | 언제 |
| --- | --- | --- |
| 페이지 제목 | `pageHeader(제목, 상위이름, 상위주소, 버튼)` | 패턴이 쓴다. 화면이 직접 부르지 않는다 |
| 버튼 그룹 | `formActions('/memo', 'plus', '메모 만들기')` | 폼 카드의 마지막 자식. 오른쪽 아래 [취소] [주 버튼] |
| 삭제 확인 모달 | `deleteConfirm('memo-delete', '메모', ${memo.title}, '/memo/1/delete')` | 삭제 버튼과 확인 창 한 벌. 상세 머리 [삭제] [수정] |
| 입력 필드 | `field('title', '제목', 'text')` | `<form th:object>` 안. 직접 적는 입력(`text`, `textarea`, `number`, `date`, `email`)과 켜고 끄는 `checkbox`, `switch`. 정해진 값 중에서 고르는 칸에는 쓰지 않는다 |
| 셀렉트 | `selectField('status', '상태', ${statusOptions})` | 정해진 값 가운데 하나를 펼침 목록에서. 맨 앞 "선택하세요"는 아직 고르지 않음. 경로는 `cozy/component/select` |
| 체크박스 | `checkboxGroup('channels', '알림 받을 곳', ${channelOptions})` | 늘어놓고 여럿 고르기. 폼 속성은 목록. 경로는 `cozy/component/choice` |
| 라디오 버튼 | `radioGroup('priority', '중요도', ${priorityOptions})` | 늘어놓고 하나 고르기. 경로는 `cozy/component/choice` |
| 입력 그리드 | `inputGrid('필드', ${fieldsGrid})` | 같은 모양의 여러 행을 표에서 입력. 열마다 입력 방식(text, number, date, email, select, radio, checkbox)을 `CozyGrid.Column`으로 고른다. 폼 안에 둔다. 사용법(바인딩, 행 작업)은 `docs/conventions/ui.md`의 "입력 그리드" |
| 표 | `dataTable(~{::#head}, ~{::.row})` | `<tr>` 머리행과 행들 |
| 페이지네이션 | `pagination(${page}, '/memo', '#result', '#filter')` | `CozyPage`. 전체 건수, 이전·다음, 번호 5개 |
| 배지 | `badge('고정', 'neutral')` | 짧은 상태나 분류. tone 다섯 가지. 분류는 neutral |
| 알림 | `alert('critical', '문장')` | 화면에 머무는 안내, 칸과 무관한 오류. 종류별 아이콘 |
| 처리 결과 알림 | `flash()` | 패턴이 page-body 맨 위에 둔다. 패턴을 쓰지 않는 화면만 직접 둔다 |
| 통계 카드 | `stat('notes', '12개', '전체 메모')` | 개요의 숫자 하나. 색 없음 |
| 빈 상태 안내 | `emptyState('notes', '제목', '설명')` | 사용자가 만들 수 없는 목록이 비었을 때 |
| 빈 상태 안내 (버튼 포함) | `emptyStateAction('notes', '메모가 없습니다', '첫 메모를 만들어 보세요.', ~{::#empty-action})` | 아직 아무것도 없고 만들 수 있을 때 |
| 검색 결과 없음 | `noResults('/memo')` | 조건 때문에 0건일 때. 인자는 조건 없는 목록 주소 |
| 모달 | `modal('id', '제목', ~{::#body})` | 일반 확인 창. 여는 버튼에 `data-bs-toggle="modal" data-bs-target="#id"`. 확인 창은 모두 `modal modal-blur fade`, `role="dialog"`, 제목 `h2.modal-title`(id `{id}-title`), `aria-labelledby`의 같은 구조다. 새 확인 창을 직접 만들 때도 이 구조를 따른다 |
| 토스트 | `toast('success', '복사했습니다')` | 사소한 확인만. 스크립트는 `Cozy.toast(message, kind)` |
| 스피너 | `loading()` | htmx 요청 중 표시(`htmx-indicator`) |

- 선택 컴포넌트의 `options`는 `CozyOptions`(값과 이름의 목록)다. 컨트롤러가 `CozyOptions.of(Enum.class, 이름 함수)`나 `CozyOptions.of(순서 있는 Map)`으로 만든다. 화면 명세의 샘플 값은 `{ items: [ { value: TODO, label: 할 일 } ] }`다.
- 경로는 모두 `cozy/component/{파일} :: …`다. 파일 이름은 매니페스트의 `template`에 있다(`emptyStateAction`과 `noResults`는 `empty-state.html`, `flash()`는 `alert.html`).

## 아이콘

| 표시 이름 | 호출 | 언제 |
| --- | --- | --- |
| 아이콘 | `cozy/icon/tabler :: icon('plus')` | 외곽선 아이콘(5,233개) |
| 아이콘 (채움) | `cozy/icon/tabler :: iconFilled('star')` | 채운 아이콘(1,054개). 켜짐 상태 등 |

## 비슷한 것 가운데 고르기

| 고민 | 고르는 기준 |
| --- | --- |
| `alert` / `flash()` / `toast` | 만들기·수정·삭제 결과는 컨트롤러의 `CozyFlash` → `flash()`. 화면에 머물 안내는 `alert`. 사소한 확인과 요청 실패만 `toast` |
| `modal` / `deleteConfirm` | 삭제면 항상 `deleteConfirm`. 그 밖의 확인은 `modal`이나 폼의 `data-cozy-confirm` |
| `emptyState` / `emptyStateAction` / `noResults` | 조건 때문이면 `noResults`. 아니면 사용자가 만들 수 있을 때 `emptyStateAction`, 없을 때 `emptyState` |
| `badge` / 글자 | 상태나 분류를 짧게 표시하면 `badge`. 값이 없음을 알리는 것은 배지가 아니라 `text-secondary` 글자 |
| `stat` / 카드 | 숫자 하나는 `stat`. 숫자에 설명이나 목록이 붙으면 보통 카드 |
| `details` / `master-detail` | 대상 하나를 깊게 읽으면 `details`. 여러 대상을 오가며 짧게 읽으면 `master-detail` |
| `field` / 선택 컴포넌트 | 사용자가 값을 직접 적으면 `field`. 정해진 값 중에서 고르면 선택 컴포넌트이고 `field`에 선택용 type을 더하지 않는다. 켜고 끄는 칸 하나는 `field`의 `checkbox`·`switch`다 |
| `selectField` / `radioGroup` | 하나를 고른다. 선택지가 몇 개뿐이고 늘어놓고 비교하는 편이 나으면 `radioGroup`, 많거나 자리가 좁으면 `selectField` |
| `checkboxGroup` / 여러 `field('…', 'checkbox')` | 같은 질문의 여러 답이면 `checkboxGroup`(폼 속성 하나가 목록). 서로 다른 켜고 끄기면 `field` 여러 개 |
| 여러 행 입력 / 반복된 폼 칸 | 같은 모양의 행이 여럿이면 `inputGrid`. 칸 전체가 입력이므로 그리드 안에 `field`나 `selectField`를 넣지 않고 열의 입력 방식으로 고른다. 행마다 입력이 많거나 여러 줄 글이 있으면 그리드 대신 행마다 카드로 나눈다 |
| 찾아서 고르기 | 글자를 쳐서 선택지를 걸러 고르는 칸이다(셀렉트를 흔히 콤보박스라고도 부르지만 셀렉트는 `selectField`다). 아직 컴포넌트가 없다. 화면에서 `datalist`나 스크립트로 따로 만들지 않는다. 필요해지면 알리고 프레임워크 컴포넌트로 더할지 정한다(새 의존성이면 ADR-0017 표 검토) |

## 다른 모듈의 fragment

- 다른 모듈이 공개한 fragment는 `templates/{모듈}/spi/` 아래에 있고 앱의 `resources/cozy/fragments.yml`에 등록되어 있다. 카탈로그의 `spi` 묶음에 보인다.
- 부르려면 의존을 선언해야 한다(`cozy link {내 모듈} --uses {그 모듈}`).
- 예: `~{memo/spi/summary :: memoSummary(${dashboard.memo})}`, `~{activity/spi/recent-activity :: recentActivity(${dashboard.activities})}`.
