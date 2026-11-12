# 프레임워크 UI 바꾸기

cozy-ui의 레이아웃·패턴·컴포넌트를 새로 만들거나 시그니처를 바꿀 때, `cozy.css`·`cozy.js`·벤더 자산을 바꿀 때의 절차다. 원문은 `docs/conventions/ui.md`의 "매니페스트"와 "설정과 자산", `docs/conventions/rules.md`의 R4·R8, `docs/adr/0021-ui-composition.md`다.

## 먼저 따질 것

프레임워크 컴포넌트는 모든 앱이 따르는 원칙을 담는 그릇이다. 하나를 더하면 모든 앱의 화면 어휘가 늘어난다.

- **기존 조합으로 안 되는가.** 패턴과 컴포넌트를 조합해서 되면 새로 만들지 않는다.
- **한 앱의 일인가, 모든 앱의 일인가.** 한 앱에서만 쓰면 그 앱의 `templates/fragments/`나 모듈 `spi/`에 둔다. 프레임워크로 올리는 것은 여러 앱이 같은 모양을 반복해서 만들 때다.
- **원칙을 담는가.** 새 컴포넌트는 원칙을 앱 대신 지켜 주는 것이어야 한다(버튼 순서를 품은 `formActions`, tone만 받는 `badge`, 문구를 품은 `deleteConfirm`처럼). 앱이 색이나 순서를 고를 여지를 남기는 컴포넌트는 만들지 않는다.
- **원칙을 바꾸는가.** 새 화면 유형, 새 tone, 새 밀도, 폭이나 버튼 자리의 변경은 원칙의 변경이다. ADR 개정안을 먼저 낸다.

## 계층과 위치

- 계층은 layout → pattern → component → icon이다. 위 계층만 아래 계층을 참조한다(R4). 같은 계층끼리는 참조하지 않는다. 같은 파일 안의 참조는 같은 계층 참조로 보지 않는다(그래서 `base`, `appShell`, `shellBody`가 한 파일에 있다).
- 템플릿은 `cozy-ui/src/main/resources/templates/cozy/{layout,pattern,component,icon}/`에 둔다.
- `cozy/`는 `cozy/` 밖을 참조하지 않는다(R5).
- 패턴의 루트는 `th:block`이고 `page-header` → `page-body`(플래시 + 본문)를 그린다. 새 패턴도 같다.

## fragment 시그니처

- **fragment를 받는 fragment는 모든 파라미터 이름이 `cozy`로 시작한다**(`cozyHeading`, `cozyResult`). Thymeleaf에서 파라미터가 같은 이름의 모델 속성을 가리기 때문이다. 화면이 넘긴 본문 안의 `${result}`가 패턴 파라미터 `result`로 바뀌어 보이는 결함이 실제로 있었다. 카탈로그 테스트가 매니페스트로 이 규칙을 검사한다.
- 값만 받는 컴포넌트는 짧은 이름을 쓴다(`badge(text, tone)`).
- 색은 tone 이름으로 받는다. 색 클래스를 인자로 받지 않는다.
- 문구가 원칙으로 정해진 곳(삭제 확인, 조건 결과 없음)은 컴포넌트가 문구를 품고 앱은 대상 이름만 넘긴다.
- fragment 이름은 같은 파일의 HTML 요소 이름과 겹치지 않는다(R12).
- 파일 맨 위 파서 주석에 용도, 인자, 어디에 두는지를 적는다. 기존 컴포넌트의 주석 형식을 따른다.

## 매니페스트 항목

모든 레이아웃·패턴·컴포넌트·아이콘 fragment는 `cozy-ui/src/main/resources/cozy/fragments.yml`에 있어야 하고, 매니페스트의 모든 항목은 실제 fragment와 맞아야 한다(R8).

```yaml
  - id: component/badge                 # 고유 이름 ({계층}/{이름})
    label: 배지                          # 표시 이름. KRDS에 같은 역할이 있으면 그 명칭
    template: cozy/component/badge      # 템플릿 경로
    signature: badge(text, tone)        # th:fragment와 같게
    purpose: 상태나 분류를 짧게 표시한다. 분류는 neutral, 상태만 색 tone을 쓴다.
    document: false                     # 완전한 HTML 문서를 그리면 true
    params:                             # signature와 같은 순서
      - { name: text, type: String, required: true, description: 표시할 글자 }
      - { name: tone, type: String, required: true, description: "neutral, info, success, warning, critical",
          choices: { neutral: 중립, info: 정보, success: 성공, warning: 경고, critical: 위험 } }
    samples:                            # 상태 이름 → 값. 카탈로그와 렌더링 테스트가 쓴다
      default: { text: 고정, tone: neutral }
```

- **purpose**는 언제 쓰고 언제 쓰지 않는지를 쓴다. 비슷한 컴포넌트가 있으면 그것과의 차이를 적는다(`toast`의 "만들기·수정·삭제 결과는 플래시로").
- **label**은 워크벤치의 구조 트리와 속성 패널에 보인다. fragment를 받는 파라미터의 `label`은 "{label} 영역"으로 보인다.
- **choices**는 정해진 값만 받는 `String` 파라미터에 적는다. 워크벤치 속성 패널이 선택 칸으로 보이고 `cozy set arg`가 그 밖의 값을 거절한다. 맵이면 값 → 한글 이름이다.
- **samples**에는 이 컴포넌트가 보일 수 있는 상태를 모두 둔다. fragment 파라미터의 샘플 값은 `template :: selector`이고, 템플릿을 생략하면 `cozy/dev/catalog`에서 찾는다. 폼 샘플은 `_form`, `_errors`다.
- YAML의 `{ }` 안 값에 `,`, `?`, `{}`, `: `가 들어가면 따옴표로 감싼다.

## 바꾸면 따라 바꿀 곳

시그니처, 문구, 마크업 구조를 바꾸면 아래를 모두 찾는다. 하나라도 빠지면 다음 생성 앱이나 픽스처에서 깨진다.

| 어디 | 무엇 |
| --- | --- |
| `cozy-ui/src/main/resources/cozy/fragments.yml` | 시그니처, params, samples |
| `samples/reference-app/src/main/resources/templates/` | 기준 예시 화면 |
| `cozy-archetype/src/main/resources/archetype-resources/…/templates/home/` | 생성 앱의 첫 화면 |
| `cozy-cli/src/main/resources/recipes/crud/*.html`, `recipes/screen/*.html`, `recipes/fragment.html` | cozy 명령이 만드는 화면 |
| `cozy-cli/src/test/resources/expected/` | 명령 출력의 기대값(`describe` 결과 등) |
| `samples/violations/rules/` | 규칙별 위반 픽스처 |
| `cozy-ui/src/main/resources/templates/cozy/dev/catalog.html` | 카탈로그 전용 샘플 조각 |
| `docs/conventions/ui.md`의 fragment 목록 | 공개 API 문서 |
| `docs/agents/skills/cozy-helper/references/screens/PLAYBOOK.md` | 생성 앱 에이전트의 화면 규칙(영어, lean) |
| 이 스킬의 `components.md` | 컴포넌트 목록 |

- 찾을 때는 fragment 이름과 템플릿 경로로 저장소 전체를 검색한다(`target/` 제외).
- 공개 API(시그니처, 매니페스트 형식)를 바꾸면 `docs/conventions/`에 바로 문서화한다.

## `cozy.css`

- `cozy.css`는 Tabler 위의 최소 보정이다. Tabler가 하는 일을 다시 하지 않는다. 고치는 이유(대비 미달, CSP로 막힌 인라인 스타일 대신, Tabler가 다른 곳에서 덮어쓰는 값)를 주석에 적는다.
- 색은 토큰(`--cozy-{tone}-fg`, `--cozy-{tone}-bg`)과 Tabler 변수(`--tblr-*`)로 쓴다. 새 색 값을 쓰면 대비 수치를 주석에 남긴다.
- 클래스 이름은 `cozy-`로 시작하고 영역을 붙인다: 앱 화면 공용 `cozy-btn-critical`, `cozy-width-form`; 카탈로그 `cozy-catalog-*`; 워크벤치 `cozy-workbench-*`.
- 파일은 구역으로 나뉜다(토큰 → 화면 폭 → htmx 표시 → 앱 틀 → 카탈로그 → 워크벤치 → 아이콘 목록). 새 규칙은 맞는 구역에 둔다.
- 대비 같은 원칙을 지키는 보정은 공통 규칙으로 모든 화면에 건다. 한 영역에서만 고치면 같은 컴포넌트가 화면마다 다르게 보인다. 워크벤치 전용 구역에는 배치와 밀도처럼 워크벤치 유형에만 속하는 것만 둔다.

## `cozy.js`

- 모든 앱 화면에 공통인 동작만 둔다: CSRF 헤더, `data-cozy-confirm`, 4xx·5xx·연결 실패 토스트, 진행 막대, 첫 오류 칸 포커스, 떠나기 전 확인, `cozy:toast` 이벤트.
- 화면은 `data-cozy-*` 속성으로 동작을 요청한다. 화면에 스크립트를 쓰지 않는다.
- 워크벤치 동작은 `/cozy/workbench.js`에 두고 워크벤치 화면에서만 읽는다.

## 벤더 자산

- 자산은 `/cozy/vendor/{이름}/{버전}/`에서 서빙된다. 지금은 Tabler 1.7.4, Tabler Icons 3.51.0, htmx 2.0.11, Pretendard 1.3.9이고 폴더마다 LICENSE가 있다.
- 버전을 바꾸려면 `distribution/vendor-assets/assets.conf`를 고치고 `fetch.sh`를 실행한 뒤 `base.html`의 경로를 맞춘다. 빌드가 `cozy-ui/vendor.lock`과 대조한다.
- 새 자산(라이브러리, 글꼴, 아이콘 세트)은 `docs/adr/0017-version-policy.md` 표에 없으면 더하지 않는다. 필요하면 이유, 라이선스, 폐쇄망 반입 영향을 적어 제안한다.
- Polaris, Cloudscape, KRDS에서는 원칙과 명칭만 가져온다. 코드, 토큰 값, 아이콘, 문서 문장을 가져오지 않는다.

## 마무리

- 카탈로그(`/cozy/dev/catalog`)에서 새 항목이 상태별로 보이는지 확인한다.
- `mvn -o verify`로 렌더링(C3, C4)과 규칙(R1~R15)을 통과시킨다. 아키타입이나 레시피를 바꿨으면 해당 검증(`mvn -o verify -pl cozy-archetype -am`, `mvn -o verify -pl samples/recipes -am`)도 돌린다.
- runtime-verifier로 그 컴포넌트를 쓰는 화면을 실제로 확인하고 보고에 스크린샷을 붙인다(`verification.md`).
