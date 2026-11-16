# 규칙별 의도적 위반 픽스처

PRD §6.2, §6.5의 수용 기준이다. 픽스처마다 정확히 그 규칙이나 검사에서 실패해야 한다. `mvn verify`가 둘 다 실행한다.

## rules/ — 템플릿 규칙 R1~R16

| 폴더 | 위반 |
| --- | --- |
| `R1-unknown-module` | 모듈이 아닌 최상위 폴더 `templates/vocc/` |
| `R2-root-template` | `templates/index.html` |
| `R3-internal-template` | voc 화면이 wiki의 화면 템플릿 `wiki/api/detail :: body`를 참조 (다른 모듈에는 spi만 공개) |
| `R4-layer-direction` | component가 pattern을 참조 |
| `R5-shared-uses-module` | `fragments/pattern/x`가 `memo/spi/y`를 참조 |
| `R6-missing-fragment` | `~{cozy/component/badge :: badg(x)}` |
| `R7-dynamic-reference` | `th:replace="~{${view} :: body}"` |
| `R8-unlisted-fragment` | 매니페스트에 없는 `fragments/component/chip` |
| `R9-module-layer` | `memo/spi/board`가 같은 모듈의 화면 `memo/api/list`를 참조 |
| `R10-model-read` | spi fragment `card(summary)`가 모델 변수 `memos`를 읽음 |
| `R11-each-with-replace` | 한 요소에 `th:each`와 `th:replace` |
| `R12-fragment-element-name` | fragment 이름 `form`이 같은 파일의 `<form>` 요소 이름과 같다 |
| `R13-unknown-icon` | Tabler Icons에 없는 아이콘 이름 `icon('pluss')` |
| `R14-raw-color` | 앱 화면이 색 클래스 `bg-blue-lt`를 직접 쓴다 |
| `R15-action-order` | 주 버튼(저장)이 버튼 묶음의 맨 오른쪽이 아니다 |
| `R16-shorthand` | 줄임 표기 `@click` (긴 이름 `x-on:click`으로 써야 한다) |
| `R16-inline-data` | `x-data="{ open: false }"` (등록된 컴포넌트 이름만 쓴다) |
| `R16-other-module` | memo 화면이 wiki 모듈의 컴포넌트 `wikiPicker`를 쓴다 |
| `R16-directive` | `x-html` |
| `R16-thymeleaf-attribute` | `th:attr="x-data=${component}"` (Thymeleaf로 Alpine 속성을 만든다) |
| `R16-expression` | `x-on:click="() => copy()"` (CSP 빌드가 읽지 못하는 화살표 함수) |
| `R16-registration` | 모듈 폴더 밖 `static/app.js`에서 컴포넌트를 등록한다 |
| `R16-iframe` | `<iframe x-on:load="copy">` (CSP 빌드는 iframe에서 식을 실행하지 않는다) |
| `R16-removed` | 없앤 동작 속성 `data-cozy-confirm` (`cozyConfirm`으로 쓴다. `cozy migrate alpine`이 바꾼다) |

- 폴더 구성: `templates/`, `modules.properties`(모듈 구조: `모듈=허용 의존`), `fragments.yml`(앱 매니페스트, 선택), `static/`(앱 JS, 선택)
- 프레임워크 템플릿, 매니페스트, Alpine 컴포넌트(`cozy.js`)는 실제 cozy-ui jar의 것을 쓴다.
- `RuleFixturesTest`가 각 폴더의 위반이 하나 이상이고 모두 폴더 이름의 규칙 ID인지 검사한다.

## checks/ — cozy-test 검사 C1~C11

검사마다 그 검사에서 실패하는 작은 앱이다. maven-invoker-plugin이 각 앱을 실제로 빌드한다. 빌드가 실패해야 성공이고, `verify.groovy`가 실패 이유를 `build.log`에서 확인한다.

| 폴더 | 검사 | 위반 |
| --- | --- | --- |
| `C1-modulith` | C1 모듈 검증 | a 모듈의 쓰기 구현이 b 모듈의 internal 클래스와, 선언하지 않은 b의 spi DTO를 쓴다. spi 쪽에만 고치는 명령 `cozy link a --uses b`가 붙는다 |
| `C2-rules` | C2 템플릿 규칙 | voc 화면이 wiki의 화면(api) 템플릿을 참조한다 (R3) |
| `C3-spec-type` | C3 스펙 렌더링 | fixture 필드 이름이 화면 DTO와 다르다 |
| `C3-spec-states` | C3 스펙 형식 | 스펙에 empty 상태가 없다 |
| `C4-html` | C4 vnu | 렌더링 결과에 같은 id가 두 번 있다 |
| `C5-missing-spec` | C5 스펙 누락 | 화면 템플릿에 스펙이 없다 |
| `C6-listener-type` | C6 리스너 파라미터 | `@ApplicationModuleListener`의 파라미터가 CozyEvent가 아니다 |
| `C6-field-removed` | C6 이벤트 호환 | v1 샘플의 필드가 현재 이벤트 클래스에 없다 (필드 삭제) |
| `C6-missing-sample` | C6 이벤트 샘플 | 이벤트에 직렬화 샘플이 없다 |
| `C7-view-module` | C7 컨트롤러·뷰 모듈 | memo 컨트롤러가 `wiki/api/list`를 반환한다 |
| `C8-layers` | C8 모듈 안 구조 | 모듈 루트의 클래스, spi에 도메인 타입, dto의 Named Interface 누락, 컨트롤러가 internal 사용, Impl 없는 app 클래스, 읽기가 쓰기 호출, domain의 저장소 (7건) |
| `C9-slots` | C9 빈칸 | Java(주석 + throw), HTML, SQL에 cozy:slot 표시가 남아 있다 (3곳) |
| `C10-module-script` | C10 모듈 스크립트 | `static/memo/memo.js`의 메서드 사이에 쉼표가 빠졌다 |
| `C1-event-cycle` | C1 순환 | 이벤트를 각 모듈의 `spi/event`에 두고 두 모듈이 서로의 이벤트를 받는다(옛 자리). 이벤트 계약 모듈 `events`가 고치는 문제다 |
| `C8-events-module` | C8 이벤트 계약 모듈 | `events`에 이벤트가 아닌 클래스(`BookFormat`)와 주인 폴더가 없는 이벤트(`events/spi/Orphan`)가 있다 |
| `C11-foreign-publish` | C11 주인만 발행 | `rental`이 `book`의 이벤트 `BookAdded`를 만든다 |
