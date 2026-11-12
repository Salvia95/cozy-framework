# 만들기·상세·수정·삭제 흐름

만들기·상세·수정·삭제 화면이나 그 컨트롤러를 만들거나 고칠 때 따르는 흐름이다. 원문은 `docs/conventions/design.md` §3, `docs/conventions/ui.md`의 "컨트롤러 규약"이다. 기준 구현은 `samples/reference-app/.../memo/api/MemoController.java`다.

## 왜 이렇게 나누는가

서버가 HTML을 그리고 htmx가 일부를 바꾸는 구조에서는, 한 화면에서 읽기와 고치기를 섞으면 바뀐 내용을 추적하기 어렵고 부분 갱신이 엉킨다. 그래서 읽는 화면(상세)과 고치는 화면(수정)을 나누고, 쓰기 요청이 끝나면 늘 다른 주소로 보내서(PRG) 새로고침이 요청을 다시 보내지 않게 한다. 결과는 이동한 화면 맨 위의 플래시로 알린다.

## 화면과 주소

`메모`를 대상의 예로 쓴다. 주소의 첫 경로는 모듈 이름이다.

| 동작 | 요청 | 화면 제목 | 주 버튼 | 끝나면 |
| --- | --- | --- | --- | --- |
| 목록 | `GET /memo` | 메모 | 메모 만들기 (머리) | — |
| 만들기 | `GET /memo/new` → `POST /memo` | 메모 만들기 | 메모 만들기 (폼) | 상세로 이동, 플래시 "메모를 만들었습니다" |
| 상세 | `GET /memo/{id}` | 대상 이름 (회의록 정리) | 수정 (머리) | — |
| 수정 | `GET /memo/{id}/edit` → `POST /memo/{id}` | 메모 수정 | 저장 (폼) | 상세로 이동, 플래시 "메모를 수정했습니다" |
| 삭제 | 상세 머리 [삭제] → 확인 창 → `POST /memo/{id}/delete` | — | 삭제 (확인 창) | 목록으로 이동, 플래시 "메모를 삭제했습니다" |

- **바뀐 것이 없는 저장**은 저장하지 않고 상세로 돌아가 info 플래시 "바뀐 내용이 없습니다"를 보인다.
- 삭제 확인 창의 제목은 "메모 삭제", 본문은 "지울 메모: **회의록 정리**"와 "지우면 되돌릴 수 없습니다."다. `deleteConfirm`이 이 문구를 만든다.

## 컨트롤러

```java
@PostMapping
String create(@Valid @ModelAttribute("form") MemoForm form, BindingResult errors, RedirectAttributes flash) {
    if (errors.hasErrors()) {
        return CozyViews.pageOrFragment("memo/api/form", "memoForm");   // htmx 요청이면 폼만 다시 그린다
    }
    flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 만들었습니다"));
    return CozyViews.redirect("/memo/" + id);                          // htmx 요청이면 HX-Redirect가 된다
}
```

- 쓰기가 성공하면 항상 `CozyViews.redirect`를 반환한다. htmx 요청이면 인터셉터가 200 빈 응답과 `HX-Redirect`로 바꾸고 플래시도 넘긴다. 다른 뷰 이름을 반환하면 플래시가 사라진다.
- 검증 오류면 같은 화면을 다시 그린다. `pageOrFragment`가 htmx 요청에는 폼 fragment만, 보통 요청에는 화면 전체를 돌려준다.
- 플래시 종류는 tone이다: `CozyFlash.success`, `info`, `warning`, `critical`.
- 뷰 이름은 자기 모듈의 `{모듈}/api/{화면}`이다. 다른 모듈의 뷰를 반환하면 테스트에서 C7로 실패한다.
- 입력은 getter와 setter가 있는 폼 클래스(`api/dto`)로 받고, 출력은 `api/dto`의 record로 넘긴다. 도메인 객체를 템플릿에 넘기지 않는다.

## 검증

- **입력하는 중에는 오류를 띄우지 않는다.** 사용자가 아직 쓰고 있는 칸을 붉게 칠하지 않는다.
- **제출하면 서버가 검증한다.** 오류는 해당 칸 바로 아래에 보이고(`field`가 그린다), 첫 오류 칸으로 포커스가 간다(`cozy.js`).
- **칸과 상관없는 서버 오류**는 폼 버튼 위에 `alert('critical', 문장)`으로 보인다.
- **주 버튼은 비활성화하지 않는다.** 언제든 눌러서 무엇이 모자란지 알 수 있어야 한다.
- 오류 문장은 무엇을 하면 되는지를 쓴다("제목을 입력하세요").

## 떠나기 전 확인

- 만들기·수정 화면(`form-page` 안의 POST 폼)에서 입력을 바꾼 뒤 떠나려 하면 브라우저가 확인한다. `cozy.js`가 하므로 화면이 따로 할 일은 없다. 이 동작을 막는 구조(폼을 `form-page` 밖에 두기)를 만들지 않는다.

## 목록의 필터와 페이지

- 필터 폼은 GET이고 `hx-get`으로 결과 영역만 바꾸며 `hx-push-url="true"`로 주소에 조건을 남긴다. 주소를 그대로 주고받으면 같은 목록이 보인다.
- 검색 칸은 입력 뒤 잠시 기다렸다가 다시 찾는다(`input delay:300ms`). 이것은 검색이지 검증이 아니다.
- 페이지 이동은 `pagination(page, 목록 주소, 결과 영역, 필터 폼)`이 필터 값을 함께 보낸다.

## 화면 명세에 남길 상태

| 화면 | 꼭 둘 상태 |
| --- | --- |
| 목록 | `default`(행 있음), `empty`(아직 없음 또는 조건 결과 없음), 여러 페이지가 의미 있으면 `paged`, 조건 결과 없음을 따로 보이려면 `noResults` |
| 만들기 | `default`(빈 폼), `invalid`(`_errors`로 칸 오류) |
| 수정 | `default`(값이 채워진 폼), `invalid` |
| 상세 | `default`, 값이 빌 수 있는 칸이 있으면 그 상태 |

- `default`와 `empty`는 모든 화면 명세에 필수다. 형식은 `docs/conventions/testing.md`의 "화면 스펙"에 있다.
