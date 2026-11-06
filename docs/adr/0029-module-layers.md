# ADR-0029 모듈 안의 구조: api · spi · internal, 템플릿도 같은 3분할

- 상태: 승인 (2026-11-06, 사용자 지시 "백엔드 구조를 nklcbdt의 모듈 구조를 참고해 같은 원칙을 적용하고, Thymeleaf 템플릿도 같게 적용", 템플릿 규칙 수정안 확정)
- 출처: 사용자 지시. ADR-0008(기능형 패키지)과 ADR-0009(템플릿 디렉터리 = 모듈 소속)를 개정한다. 참고: nklcbdt ADR 0083~0087(모듈 레이아웃, 레이어 규칙)

## 배경

- **ADR-0008**: 모듈 단위로만 나누고 모듈 안은 정하지 않았다. 컨트롤러·서비스는 모듈 루트, Mapper는 `internal/`, 공개 타입은 `api/`(`@NamedInterface("api")`)였다.
- **ADR-0009**: `templates/{모듈}/api/`만 공개였다. Java의 `api`(다른 모듈에 공개)와 HTTP 진입점이 이름이 같아 헷갈렸다.
- **같은 원칙 적용**: nklcbdt는 모듈마다 HTTP 진입점(`api`), 모듈 사이 계약(`spi`), 밀폐된 구현(`internal`)을 나누고, 구현 안을 command/query/domain/infra로 다시 나눈다. Cozy 앱도 작은 LLM이 같은 자리에 같은 것을 두도록 이 배치를 강제한다.

## 결정

### 1. Java 패키지

```text
{모듈}/
├── package-info.java    @ApplicationModule(allowedDependencies = {"다른모듈 :: spi"})
├── api/                 컨트롤러(@Controller)만. api/dto: 폼·필터·화면 DTO
├── spi/                 @NamedInterface("spi"). *Facade(쓰기)·*Finder(읽기) 인터페이스만
│   ├── dto/             모듈 사이 DTO (record)
│   └── event/           CozyEvent
└── internal/
    ├── app/command/     *FacadeImpl, *Service + *ServiceImpl, *Listener
    ├── app/query/       *FinderImpl, *Query + *QueryImpl, *Projection + *ProjectionImpl
    ├── domain/          엔티티·VO·도메인 규칙
    ├── infra/           MyBatis Mapper, 행, 저장소 구현, 외부 연동
    └── config/          모듈 전용 설정·빈
```

- **모듈 루트**에는 `package-info.java`만 둔다.
- **spi의 하위 패키지**(dto, event)도 각각 `@NamedInterface("spi")`를 선언한다. Spring Modulith는 Named Interface를 하위 패키지로 넓혀 주지 않는다(확인: 선언이 없으면 C1이 "non-exposed type"으로 실패한다).
- **app의 클래스는 항상 인터페이스 + Impl**이다. 리스너(`*Listener`)만 예외다. 사용자 결정("항상 인터페이스 + Impl")이다.
- **의존은 `"모듈 :: spi"`로 선언한다.** `"모듈"`만 적으면 모듈 전체 의존이 되어 Modulith가 모든 공개 타입을 열어 주지만, 템플릿 경계(R3)는 `spi`만 연다.

### 2. 레이어 방향 (검사 C8)

| 규칙 | 이유 |
| --- | --- |
| api ↛ internal | 컨트롤러는 spi(Facade·Finder)로만 일을 시킨다. spi DTO를 화면 DTO로 바꾸는 일은 컨트롤러가 한다 |
| spi ↛ internal, api | 계약에 구현 타입이 새지 않게 한다. 시그니처에는 원시 값과 spi.dto만 |
| internal ↛ api | 구현이 화면을 모르게 한다 |
| domain ↛ app, infra, config, spi, api. domain에 Repository·Service·Mapper 없음 | 도메인 규칙은 다른 레이어를 모른다 |
| infra ↛ app. infra ↛ 다른 모듈의 spi | app → infra 한 방향. 모듈 사이 조합은 app이 한다 |
| command → query는 `*Query` 인터페이스로만. query ↛ command | 쓰기는 확인용 단순 조회만 쓰고, 읽기는 쓰기를 부르지 않는다 |
| command는 JDBC·SqlSession을 직접 쓰지 않는다 | SQL은 infra의 Mapper에 모은다 |
| 쓰기 `*Facade`는 api와 소유 모듈의 app.command만 부른다 | 다른 모듈의 쓰기는 이벤트로 일으킨다 |

- **C1과의 관계**: Modulith(C1)는 모듈 사이 경계만 본다. C8은 모듈 안의 배치와 방향만 본다. 다른 모듈의 internal을 쓰면 C1이 먼저 잡는다.
- **실패로 닫는다**: 예외 목록을 두지 않는다.

### 3. 템플릿 (ADR-0009 개정)

`templates/{모듈}/` 아래는 `api/`, `spi/`, `internal/` 셋 중 하나에 둔다(R1).

| 폴더 | 역할 | 참조할 수 있는 것 | 모델 읽기 | 검사 |
| --- | --- | --- | --- | --- |
| `api/` | 컨트롤러가 렌더링하는 화면 | 자기 internal·spi·api, 선언한 모듈의 spi, `cozy/`, `fragments/` | 된다 | 화면 스펙(C3~C5) |
| `spi/` | 다른 모듈이 부르는 fragment | 자기 internal·spi, 선언한 모듈의 spi, `cozy/`, `fragments/` | 안 된다(파라미터로만) | 매니페스트 + 샘플(C5) |
| `internal/` | 같은 모듈의 api·spi가 조합하는 조각 | 자기 internal, `cozy/`, `fragments/` | 안 된다(파라미터로만) | 그것을 쓰는 화면·fragment로 |

- **컨트롤러**는 `{모듈}/api/…`나 `{모듈}/spi/…`만 반환한다(C7).
- **모델 읽기 금지(R10)**: Thymeleaf fragment는 부르는 화면의 모델 전체를 볼 수 있다. 선언하지 않은 이름을 읽으면 조용히 빈 값이 되거나, 그 위에서 메서드를 부르면 예외가 난다. spi·internal은 파라미터와 지역 변수(`th:each`, `th:with`)만 읽게 해서, 부르는 쪽이 넘긴 값만으로 그려지게 한다.
  - 허용: `#strings` 같은 유틸리티(단 `#vars`, `#ctx`, `#root`, `#request`, `#session`, `#response`, `#servletContext`, `#application` 제외), `cozy*` 전역, `_csrf`.
  - 금지: `@빈` 참조, `param`.
  - `*{...}`는 fragment 안의 `th:object` 아래에서만 쓴다.
- **같은 요소의 th:each + th:replace 금지(R11)**: 속성 우선순위상 th:replace가 th:each보다 먼저 실행되어 반복 변수가 없다(실험으로 확인). `<th:block th:each>` 안쪽에 th:replace를 둔다.
- **화면 스펙 이름**: `memo/api/list` → `memo-list` (`api/`를 뺀다).

## 근거

- **역할 이름이 겹치지 않는다**: Java와 템플릿 모두 api = 진입점(화면), spi = 모듈 사이 계약, internal = 구현. 작은 LLM이 "다른 모듈이 쓰는 것은 spi"라는 규칙 하나만 기억하면 된다.
- **fragment 조합이 사용자의 목적에 맞는다**: 조각을 internal에 정의하고, api(화면)와 spi(다른 모듈용)가 이를 조합한다. 값을 파라미터로만 받게 하면 같은 조각을 어느 화면에서 불러도 같은 결과가 난다.
- **검사로 강제한다**: 배치와 방향은 C8, 템플릿 경계는 R1·R3·R9·R10·R11로 빌드 때 실패한다.

## 결과

- **ADR-0008 개정**: 기능형 패키지는 그대로이고, 모듈 안은 위 1·2로 나눈다. `controller/`·`service/` 같은 계층형 최상위 패키지 금지도 그대로다.
- **ADR-0009 개정**: 공개 영역은 `api/`가 아니라 `spi/`다. 경계 정보는 여전히 Java의 `@ApplicationModule`에서만 읽는다.
- **바뀐 검사**:
  - 규칙: R1(모듈 폴더 3분할), R3(`spi`만 공개), R8(모듈 `spi/` fragment), 새 규칙 R9(같은 모듈 안 방향), R10(모델 읽기), R11(th:each + th:replace)
  - 검사: C5(spi fragment는 매니페스트, api 화면은 스펙. spi fragment 파라미터 타입에 api·internal 금지), C7(뷰는 `{모듈}/api/`·`{모듈}/spi/`), 새 검사 C8
- **바뀐 산출물**: reference-app, 아키타입의 `home` 모듈, 위반 픽스처(R1~R11, C1~C8).
- **기존 앱**: 이 결정 전의 배치(`{모듈}/MemoService`, `view/`, `templates/{모듈}/list.html`)는 C8·R1에서 실패한다. 아직 배포된 앱이 없어 이행 도구는 두지 않는다.
- 문서: [structure.md](../conventions/structure.md)
