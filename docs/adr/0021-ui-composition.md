# ADR-0021 UI 구성 규칙: 레이아웃 조합, 예약 이름, 카탈로그, htmx 리다이렉트

- 상태: 승인 (2026-11-06)
- 출처: M3 구현 (PRD §6.4, §7). M4 규칙 검사(R4, R7, R8)가 이 결정을 기준으로 한다.

## 결정

### 1. 레이아웃은 한 파일에서 조합한다

`templates/cozy/layout/base.html` 한 파일에 fragment 세 개를 둔다.

| fragment | 용도 |
| --- | --- |
| `base(cozyTitle, cozyContent)` | 완전한 HTML 문서 |
| `appShell(cozyTitle, cozyContent)` | base + 사이드 내비 |
| `shellBody(cozyShellContent)` | appShell 내부 구성 |

- R4는 같은 계층끼리 참조하는 것을 기본으로 금지한다. 그런데 appShell은 base를 감싸야 한다. 같은 파일 안의 참조는 같은 계층 참조로 보지 않으므로, 세 fragment를 한 파일에 두면 R4를 지키면서 조합할 수 있다.
- PRD의 "layout: base, app-shell"은 매니페스트 id(`layout/base`, `layout/app-shell`)로 그대로 남긴다.
- 화면 쪽 사용법은 다음 한 줄이다.

  ```html
  <html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
  ```

### 2. `cozy`로 시작하는 이름은 예약한다

Thymeleaf에서는 fragment 파라미터가 같은 이름의 모델 속성을 가린다. 다른 fragment를 받는 fragment(레이아웃, 패턴, `dataTable`, `modal`) 안에서는 화면이 넘긴 본문이 파라미터 이름 공간에서 그려진다. 그래서 다음과 같은 문제가 생긴다.

- 패턴 파라미터가 `result`이면, 페이지의 `${result}`(목록 데이터)가 fragment 객체로 바뀌어 보인다. M3에서 실제로 발생했다.
- 이렇게 되면 화면 작성자가 피할 방법이 없다.

결정은 두 가지다.

- **fragment를 받는 fragment**: 모든 파라미터 이름을 `cozy`로 시작한다(`cozyHeading`, `cozyResult`). CatalogTest가 매니페스트로 이 규칙을 검사한다.
- **모델 속성과 요청 속성**: `cozy`로 시작하는 이름은 프레임워크 전용이다(`cozyApp`, `cozyNav`, `cozyFlash`, `cozyUpdate`, `cozySample`). 화면은 이 이름을 쓰지 않는다.
- 호출하는 쪽은 위치로 인자를 넘기므로 화면 코드에는 이 이름이 나타나지 않는다.

### 3. 카탈로그 미리보기는 Java가 템플릿을 만든다

- 템플릿 파일에서 fragment를 이름으로 골라 호출하면 동적 참조가 된다. R7은 동적 참조를 금지하므로 이 방법은 쓸 수 없다.
- 그래서 `CatalogTemplateResolver`가 템플릿 이름 `cozy-catalog:{id}|{state}`를 받아, fragment 호출 한 줄짜리 템플릿을 매니페스트로 만든다.
  - **값 파라미터**: 모델의 `cozySample`에서 채운다.
  - **fragment 파라미터**: 샘플 값이 있으면 그 fragment를 넣는다. 템플릿 경로가 없는 이름은 `cozy/dev/catalog`에서 찾는다. 샘플 값이 없으면 자리 표시를 넣는다.
- **템플릿 이름**: `::`를 쓰지 않는다. ThymeleafView가 `::`를 fragment 선택자로 해석하기 때문이다.
- **폼 샘플**: `_form` 값과 `_errors` 값을 `MapBindingResult`로 만들어 `th:field`와 오류 표시까지 그린다.
- **iframe**: 미리보기는 fragment마다 iframe으로 분리한다. 이 경로(`/cozy/dev/catalog/render`)에만 적용되는 보안 필터 체인이 같은 출처 프레임을 허용한다. 나머지 화면은 계속 `frame-ancestors 'none'`이다.

### 4. htmx 리다이렉트는 인터셉터가 바꾼다

- 컨트롤러는 항상 `redirect:`를 반환한다. `CozyViews.redirect(path)`도 결국 `"redirect:" + path`다.
- htmx 요청이면 `HtmxRedirectInterceptor`가 다음과 같이 바꾼다.
  - 200 빈 응답에 `HX-Redirect`를 싣는다.
  - 출력 플래시 맵을 저장한다.
- 이렇게 한 이유: Spring은 뷰 이름이 `redirect:`일 때만 `RedirectAttributes`의 플래시를 출력 플래시 맵으로 옮긴다. 컨트롤러가 다른 뷰 이름을 반환하면 플래시가 사라진다. M3 테스트에서 발견했다.

### 5. 벤더 자산은 바이트 그대로 고정한다

- `distribution/vendor-assets/fetch.sh`가 자산을 받는다.
  - npm tarball은 레지스트리의 sha512로 검증한다.
  - npm 패키지에 없는 Tabler LICENSE는 GitHub 릴리스 태그(`@tabler/core@1.7.3`)에서 받는다.
  - 결과는 `cozy-ui/vendor.lock`의 SHA-256으로 기록한다.
- **줄바꿈**: `.gitattributes`로 벤더 폴더를 `-text`로 지정한다. Windows에서 줄바꿈이 바뀌면 해시가 달라지기 때문이다.
- **검증 (VendorAssetsTest)**:
  - 파일이 lock과 일치하는지
  - 각 폴더에 LICENSE가 있는지
  - 템플릿이 참조하는 자산이 실제로 있는지
  - 외부 주소 참조가 0건인지

### 6. CSP 때문에 인라인 style과 htmx 기본 스타일을 쓰지 않는다

- htmx의 `includeIndicatorStyles`는 `<style>` 요소를 넣는다. CSP가 이를 막으므로 meta 설정으로 끄고, indicator CSS는 `cozy.css`에 둔다.
- `allowEval`과 `allowScriptTags`도 끈다.
- 화면 템플릿에는 `style=` 속성을 쓰지 않는다. MemoScreenTest가 검사한다.

## 결과

- **UI 의존성**: cozy-ui가 `spring-boot-starter-validation`에 의존한다(Hibernate Validator, Apache-2.0). Boot BOM이 관리하고, §10의 `@Valid` 규약에 필요하다. 금지 대상인 `org.hibernate.orm`이 아니다.
- **M4 규칙 검사의 범위**:
  - R4: 같은 파일 참조는 같은 계층 참조로 보지 않는다.
  - R8: 매니페스트 대상은 `layout`, `pattern`, `component` 폴더다.
  - `cozy/dev/`의 카탈로그 전용 조각은 R8 대상이 아니다.
