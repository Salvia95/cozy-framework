# 테스트 기반 (cozy-test)

앱은 테스트 두 개를 각각 한 줄로 쓴다(PRD §6.5). 나머지 검사는 베이스 클래스가 한다.

```java
class ArchitectureTest extends CozyArchitectureTest {
}

class RenderingTest extends CozyRenderingTest {
}
```

```xml
<dependency>
  <groupId>io.salvia95.cozy</groupId>
  <artifactId>cozy-test</artifactId>
  <scope>test</scope>
</dependency>
```

- **같이 들어오는 테스트 의존성**: cozy-test 하나로 JUnit, AssertJ, Spring Test, MockMvc(`spring-boot-starter-webmvc-test`), Security Test가 함께 들어온다. 앱이 따로 추가하지 않는다.
- **테스트 위치**: 앱의 메인 패키지 아래에 둔다. 베이스 클래스는 테스트 패키지에서 위로 올라가며 `@SpringBootApplication`을 찾는다.
- **테스트 메서드**: 베이스 클래스의 테스트 메서드는 `final`이라 앱이 덮어써서 끌 수 없다.

## 검사

| ID | 검사 | 어디서 | 실패 조건 |
| --- | --- | --- | --- |
| C1 | 모듈 구조 | `CozyArchitectureTest.modulesAreVerified` | `ApplicationModules.of(앱).verify()` 위반 |
| C2 | 템플릿 경계 | `CozyArchitectureTest.templatesFollowBoundaryRules` | R1~R8 오류([rules.md](rules.md)). 경고는 출력만 한다 |
| C3 | 렌더링 | `CozyRenderingTest.everyScreenStateRendersAsValidHtml`, `everyManifestFragmentRendersAsValidHtml` | 스펙 형식 오류, fixture가 DTO 타입과 맞지 않음, 렌더링 예외, 200이 아닌 응답 |
| C4 | HTML | 위의 두 테스트 | 렌더링 결과의 vnu 오류. 경고는 출력만 한다 |
| C5 | 스펙 누락 | `CozyRenderingTest.everyScreenTemplateHasSpec` | 스펙이 없는 화면 템플릿, 매니페스트에 없는 `{모듈}/api/` fragment |
| C6 | 이벤트 호환 | (M5) | — |
| C7 | 컨트롤러·뷰 모듈 | 앱의 모든 MockMvc 테스트 | 컨트롤러의 모듈과 반환한 뷰 이름의 첫 경로가 다름 |

### 검사별 세부

- **실패 보고**: 실패는 모두 모아 한 번에 낸다. 메시지 앞에 검사 ID가 붙는다(`[C3] specs/memo-list.yml 상태 default — ...`).
- **C3 렌더링 경로**: 렌더링 테스트는 `cozy.dev.catalog.enabled=true`로 앱을 띄우고, 패턴 카탈로그 경로(`/cozy/dev/catalog/render`, `/cozy/dev/catalog/screen`)를 MockMvc로 부른다. 카탈로그 화면에 보이는 것과 같은 결과를 검사한다. 데이터 경로는 `target/cozy-test/rendering`이다.
- **C3 fixture 변환**: fixture는 엄격한 JSON 매핑으로 바꾼다. 다음은 모두 실패다.
  - 모르는 필드
  - 빠진 record 필드
  - primitive 필드의 null
- **C4 vnu 검사**: 검사는 jar 안의 스키마만 쓰고 네트워크를 쓰지 않는다.
  - htmx 속성은 HTML 표준 밖이라 vnu가 오류로 본다. Cozy가 쓰는 이름만 허용 목록(`CozyHtmlValidator.HTMX_ATTRIBUTES`)으로 걸러 낸다.
  - 목록에 없는 `hx-*`는 오류로 남는다. 오타와 인라인 스크립트인 `hx-on`이 여기에 해당한다.
- **C4 fragment 감싸기**: htmx 부분 갱신 fragment(스펙의 `fragments`)는 최소 문서로 감싼 뒤 검사한다. `<tr>`, `<li>`처럼 부모 요소가 필요한 fragment는 알맞은 부모로 감싼다.
- **C5 화면 템플릿**: `templates/{모듈}/` 아래의 템플릿 중 `api/` 밖에 있는 것이 화면 템플릿이다. `cozy/`, `fragments/`, `error/`는 대상이 아니다. `fragments/`의 fragment는 매니페스트(R8)가 대신한다.
- **C7 판정 방식**: 컨트롤러의 모듈은 메인 패키지 바로 아래 패키지의 이름이다. `@ApplicationModule(id)`가 있으면 그 이름을 쓴다. 다음 뷰 이름은 검사하지 않는다.
  - `redirect:`와 `forward:`로 시작하는 뷰 이름
  - htmx 리다이렉트의 빈 뷰
  - 프레임워크 컨트롤러가 반환한 뷰
- **C7 실패와 경고**: cozy-test가 클래스패스에 있으면 앱의 모든 Spring 테스트에서 `cozy.ui.view-module-check=fail`이 된다. 그래서 MockMvc 요청이 `[C7] ...` 예외로 실패한다. 운영에서는 기본값 `warn`이라 경고 로그만 남긴다.

### 바꿀 수 있는 것

검사를 끄는 방법은 없다. 위치만 바꿀 수 있다.

| 메서드 | 기본값 |
| --- | --- |
| `CozyArchitectureTest.applicationClass()` | 테스트 패키지에서 찾은 `@SpringBootApplication` |
| `CozyArchitectureTest.templateRoot()`, `CozyRenderingTest.templateRoot()` | `src/main/resources/templates` |
| `CozyArchitectureTest.manifestFile()` | `src/main/resources/cozy/fragments.yml` |
| `CozyArchitectureTest.allowSameLayer()` | 앱 설정 `cozy.rules.allow-same-layer` |

## 화면 스펙 `specs/{모듈}-{화면}.yml`

화면 템플릿마다 스펙 하나를 둔다. 패턴 카탈로그의 화면 미리보기와 렌더링 검사(C3~C5)가 같이 쓴다.

```yaml
screen: memo-list                     # 템플릿 경로의 '/'를 '-'로. 파일 이름도 memo-list.yml
template: memo/list                   # 뷰 이름
fragments: [result]                   # htmx로 따로 렌더링하는 fragment (pageOrFragment의 두 번째 인자)
model:                                # 모델 이름 → 화면 DTO 타입. 중첩 클래스는 Outer.Inner
  filter: io.salvia95.cozy.reference.memo.view.MemoFilter
  result: io.salvia95.cozy.reference.memo.view.MemoViews.ListResult
states:                               # default와 empty는 필수
  default:
    filter: { q: "", pinned: false }
    result: { rows: [ { id: 1, title: 회의록, pinned: true, updatedAt: "2026-11-06 10:00" } ],
              page: { number: 1, size: 10, totalElements: 1 } }
  empty:
    filter: { q: 없는 단어, pinned: false }
    result: { rows: [], page: { number: 1, size: 10, totalElements: 0 } }
```

- **상태와 모델 값**: 모든 상태는 `model`의 모든 이름에 값을 준다. 값이 없으면 `{}`를 적는다. `model`에 없는 이름을 상태에 쓰면 실패한다.
- **검증 오류 상태**: `_errors`로 검증 오류 상태를 만든다. 형식은 `모델 이름 → 필드 → 메시지`이다. 그 모델에 `BindingResult`가 붙어 `th:field`의 오류 표시까지 그려진다.

  ```yaml
  invalid:
    form: { title: "" }
    _errors:
      form: { title: 제목을 입력하세요 }
  ```

- **형식 오류가 드러나는 시점**: 스펙 형식이 틀리면 카탈로그가 켜진 앱이 기동할 때 실패한다(dev 프로필, 렌더링 테스트). 메시지는 `[cozy] 화면 스펙 오류 (specs/memo-list.yml): ...`이다.
- **카탈로그 미리보기**: 카탈로그 `/cozy/dev/catalog`의 screen 구역에 스펙마다 상태별 미리보기가 보인다.
- **직접 미리보기 주소**: `/cozy/dev/catalog/screen?spec=memo-list&state=default[&fragment=result]`

## 위반 픽스처

`samples/violations/`에 규칙(R1~R8)과 검사(C1~C5, C7)마다 정확히 그 이유로 실패하는 픽스처가 있다. 자세한 내용은 그 폴더의 README에 있다. 베이스 클래스를 고치면 이 픽스처가 회귀를 잡는다.

## 테스트 JVM

cozy-parent는 테스트 JVM에 Mockito를 `-javaagent`로 올린다. JDK가 실행 중 에이전트 부착을 막을 예정이기 때문이다. 속성은 `cozy.test.argLine`이다. Mockito가 없는 모듈은 이 속성을 비운다(`<cozy.test.argLine/>`).
