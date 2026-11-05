# ADR-0022 규칙 검사와 테스트 기반: 실패로 닫는 기준, vnu 구성, 검사 픽스처

- 상태: 승인 (2026-11-06)
- 출처: M4 구현 (PRD §6.2, §6.5, §7, §10). PRD §14 "판단할 수 없는 것은 실패로 닫는다"를 구체화한다.

## 결정

### 1. 정적으로 확인할 수 없는 템플릿 구문은 R7로 실패시킨다

PRD §7의 R7은 동적 참조를 금지한다. 다음 구문도 같은 이유, 즉 검사기가 무엇을 참조하는지 확정할 수 없다는 이유로 R7 오류로 처리한다.

- **동적 경로와 이름**: 템플릿 경로나 fragment 이름에 `${}`, `*{}`, `|...|`, `__...__`가 들어간 참조
- **복잡한 마크업 선택자**: `span[@id='x']`, `//div` 같은 선택자. 검사할 수 있는 선택자는 fragment 이름, `#id`, `.class`, 요소 이름뿐이다.
- **본문 텍스트 안의 `~{}`**: `[[~{...}]]` 같은 인라인 표현식
- **프로토타입 전용 주석**: `<!--/*/ ... /*/-->`. Thymeleaf는 이 주석 안의 마크업을 처리하지만, HTML 파서에게는 주석일 뿐이다.
- **읽을 수 없는 파일**: HTML로 파싱할 수 없는 템플릿

예외가 하나 있다. `th:replace="${p}"`는 `p`가 그 요소를 감싸는 `th:fragment`의 파라미터일 때 허용한다. 패턴이 화면의 조각을 받아 끼우는 슬롯이고, 끼워지는 조각은 호출하는 쪽에서 정적으로 검사된다.

`th:include`는 PRD대로 경고만 낸다.

### 2. allowedDependencies를 선언하지 않은 모듈은 다른 모듈 템플릿을 쓸 수 없다

Modulith는 `allowedDependencies`가 없는 모듈(open)에 모든 의존을 허용한다. 템플릿 경계에서는 이것을 "허용 목록 없음"으로 보고 실패로 닫는다. 위반 메시지는 선언할 값(`"wiki::api"`)을 알려 준다.

### 3. 매니페스트에는 모듈 공개 fragment도 적는다

PRD §6.5의 "fragment 전용 파일은 매니페스트로 대체"를 다음과 같이 해석한다.

- `templates/{모듈}/api/`의 fragment는 앱 매니페스트에 적는다.
- R8은 매니페스트에 적힌 `api/` 항목이 실제 템플릿과 맞는지만 확인한다. `api/` fragment가 매니페스트에 빠졌는지는 C5가 확인한다.
- 이렇게 하면 M5의 `memo::api`, `activity::api` fragment가 카탈로그에 나오고, 렌더링 검사(C3, C4)도 받는다.

### 4. 렌더링 검사는 패턴 카탈로그 경로를 MockMvc로 부른다

- `CozyRenderingTest`는 `cozy.dev.catalog.enabled=true`로 앱을 띄운다. 그다음 카탈로그의 `/render`(매니페스트 fragment)와 `/screen`(화면 스펙) 경로를 MockMvc로 부른다.
- 카탈로그 미리보기와 빌드 검사가 같은 코드로 그리므로, 사람이 보는 화면과 검사하는 결과가 같다. PRD §10의 "스펙은 패턴 카탈로그 미리보기와 빌드 렌더링 테스트가 같이 쓴다"를 이렇게 구현했다.
- 레이아웃 모델 속성(`cozyApp`, `cozyNav`)도 실제 요청처럼 채워진다.
- fixture는 앱의 JsonMapper와 분리한 엄격한 매퍼로 바꾼다. 모르는 필드, 빠진 record 필드, primitive의 null은 모두 실패한다.

### 5. 스펙 형식

- **필수 항목**: `screen`, `template`, `states`(`default`와 `empty` 포함)
- **이름 규칙**: `screen`은 템플릿 경로의 `/`를 `-`로 바꾼 값이고, 파일 이름은 `{screen}.yml`이다.
- **모델 값**: 모든 상태가 `model`의 모든 이름에 값을 준다.
- **검증 오류 상태**: `_errors`로 만든다.
- **형식 오류**: 카탈로그 빈을 만들 때 실패한다. 즉 dev 기동과 렌더링 테스트가 시작하는 시점에 바로 실패한다.

### 6. vnu는 내장 검사에 필요한 부분만 쓴다

`nu.validator:validator`의 의존성에서 다음을 뺐다. 실제로 `EmbeddedValidator`가 이것들 없이 동작하는 것을 확인했다.

- Jetty의 서버, 보안, 세션, ee10, alpn, 압축
- servlet API, commons-fileupload2
- log4j 1.2.17

`SimpleDocumentValidator`가 클래스 초기화 때 jetty-client와 log4j 1.x API를 참조하므로 다음 둘은 남긴다.

- **jetty-client/http/io/util**: 그대로 둔다.
- **log4j 1.x API**: `org.slf4j:log4j-over-slf4j`(Boot BOM 관리, MIT)로 대신한다. log4j 1.x는 지원이 끝났고 알려진 취약점이 있어 cozy-parent의 금지 의존성에 `log4j:log4j`를 추가했다.

### 7. htmx 속성은 이름 허용 목록으로 vnu 오류에서 뺀다

`hx-get` 등은 HTML 표준 속성이 아니라서 vnu가 오류로 보고한다. 두 가지 대안을 검토했다.

- **`data-hx-*` 사용**: 표준을 지키지만 htmx 예제와 다르다. 작은 LLM이 계속 틀리게 된다.
- **필터 끄기**: 오타(`hx-gett`)를 잡지 못한다.

결정은 htmx 2.x 속성 중 스크립트를 실행하지 않는 이름만 허용 목록(`CozyHtmlValidator.HTMX_ATTRIBUTES`)에 넣는 것이다. 목록 밖의 `hx-*`는 오류로 남는다. 오타와 `hx-on`(인라인 스크립트, CSP와 `allowEval=false`에 맞지 않음)이 여기에 해당한다. 규칙을 완화한 것이 아니라 더 좁게 검사하는 것이다.

### 8. 컨트롤러·뷰 모듈 검사(C7)는 cozy-ui 인터셉터가 하고, cozy-test가 실패 모드로 켠다

- `ViewModuleInterceptor`가 `postHandle`에서 핸들러 패키지의 모듈과 뷰 이름의 첫 경로를 비교한다.
- 모드는 `cozy.ui.view-module-check` 속성으로 정한다.
  - **운영**: 기본값 `warn`이다. 경고 로그만 남긴다.
  - **테스트**: cozy-test의 `ContextCustomizerFactory`가 모든 Spring 테스트 컨텍스트에 `fail`을 넣는다. 앱이 쓴 평범한 MockMvc 테스트가 위반 요청에서 실패한다. PRD의 "앱의 MockMvc 테스트 실행 중"이 이 동작이다.

### 9. 검사 픽스처는 invoker로 실제 앱을 빌드한다

규칙 픽스처와 검사 픽스처를 다르게 실행한다.

- **규칙 픽스처(`samples/violations/rules/R*`)**: 템플릿만 있으면 되므로 JVM 안에서 cozy-rules로 검사한다.
- **검사 픽스처(`samples/violations/checks/C*`)**: 앱 컨텍스트, 클래스패스, `src/main/resources`가 픽스처마다 달라야 해서 각각 작은 Maven 프로젝트로 두었다. maven-invoker-plugin이 빌드하고, 빌드가 실패해야 성공이다. `verify.groovy`가 확인하는 것은 두 가지다.
  - 실패 메시지가 기대한 검사 ID인지
  - 실패한 테스트가 하나뿐인지. 다른 검사까지 실패하면 픽스처가 잘못된 것이다.
- **비용**: 빌드 시간이 약 50초 늘어난다(2스레드). 기대한 이유로 실패하는지는 실제 빌드에서만 확인할 수 있으므로 받아들인다.

### 10. 베이스 클래스의 테스트 메서드는 final이다

앱이 덮어써서 검사를 끄지 못하게 한다. 바꿀 수 있는 것은 위치(템플릿 루트, 매니페스트 경로, 앱 클래스)뿐이다.

## 결과

- 새 의존성은 모두 ADR-0017 표의 범위 안에 있다.
  - spring-modulith-core: Modulith BOM. ArchUnit 1.4.2를 함께 가져온다(Apache-2.0).
  - nu.validator: 표에 있다.
  - log4j-over-slf4j: Boot BOM.
  - spring-boot-starter-test, webmvc-test, security-test: Boot BOM.
- 이벤트 호환 검사(C6)와 `@ApplicationModuleListener` 파라미터 검사는 M5에서 cozy-starter-events와 함께 추가한다.
- reference-app의 테스트 의존성은 cozy-test 하나로 정리했다.
