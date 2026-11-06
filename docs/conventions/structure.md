# 모듈 구조: api · spi · internal

앱의 모듈은 Java 패키지와 템플릿 폴더 모두 api · spi · internal로 나눈다(ADR-0029). 배치와 방향은 검사 C8(Java)과 R1·R3·R9·R10·R11(템플릿)이 빌드 때 검사한다. 예시는 `samples/reference-app`의 memo · activity · dashboard 모듈이다.

| 이름 | 뜻 | Java | 템플릿 |
| --- | --- | --- | --- |
| api | HTTP 진입점 | 컨트롤러, `api/dto`(폼·필터·화면 DTO) | 컨트롤러가 렌더링하는 화면 |
| spi | 모듈 사이 계약 | `*Facade`·`*Finder` 인터페이스, `spi/dto`, `spi/event` | 다른 모듈이 부르는 fragment |
| internal | 밀폐된 구현 | `app/command`, `app/query`, `domain`, `infra`, `config` | api·spi가 조합하는 조각 |

## Java 패키지

```text
memo/
├── package-info.java                 @ApplicationModule(allowedDependencies = {})
├── api/
│   ├── MemoController.java           MemoFacade(쓰기)·MemoFinder(읽기)를 부르고 spi DTO를 화면 DTO로 바꾼다
│   └── dto/                          MemoForm, MemoFilter, MemoViews (화면 DTO)
├── spi/                              package-info: @NamedInterface("spi")
│   ├── MemoFacade.java               create, update, delete
│   ├── MemoFinder.java               find, list, search, summary
│   ├── dto/                          MemoItem, MemoPage, MemoSummary  (package-info: @NamedInterface("spi"))
│   └── event/                        MemoCreated (CozyEvent)          (package-info: @NamedInterface("spi"))
└── internal/
    ├── app/command/MemoFacadeImpl    @Service @Transactional, 이벤트 발행
    ├── app/query/MemoFinderImpl      @Transactional(readOnly = true), 도메인 → spi DTO
    ├── app/query/MemoSummaryProjection + MemoSummaryProjectionImpl
    ├── domain/Memo                   도메인 모델
    ├── infra/MemoMapper, MemoRow     MyBatis (SQL은 resources/mapper/memo/MemoMapper.xml)
    └── config/MemoConfiguration      모듈 전용 빈 (내비게이션 항목 등)
```

### 놓는 자리

| 무엇 | 어디 | 이름 |
| --- | --- | --- |
| 컨트롤러 | `api/` | `@Controller`. `api/` 바로 아래에는 컨트롤러만 |
| 폼·필터·화면 DTO | `api/dto/` | 자유 |
| 쓰기 계약 | `spi/` | `*Facade` 인터페이스 |
| 읽기 계약 | `spi/` | `*Finder` 인터페이스 |
| 모듈 사이 DTO | `spi/dto/` | record. 스프링 빈이 아니다 |
| 이벤트 | `spi/event/` | `CozyEvent` 구현 record. 다른 곳에 두면 C8 |
| 쓰기 구현 | `internal/app/command/` | `*FacadeImpl`, `*Service` + `*ServiceImpl` |
| 이벤트 리스너 | `internal/app/command/` | `*Listener` (`@ApplicationModuleListener` 등). 인터페이스 없이 둔다 |
| 읽기 구현 | `internal/app/query/` | `*FinderImpl`, `*Query` + `*QueryImpl`, `*Projection` + `*ProjectionImpl` |
| 도메인 모델·규칙 | `internal/domain/` | `*Repository`, `*Service`, `*Mapper`, `*Row`를 두지 않는다 |
| Mapper·행·저장소·외부 연동 | `internal/infra/` | `*Query`, `*Projection`, `*Facade`, `*Finder`, `*Service`를 두지 않는다 |
| 모듈 전용 설정 | `internal/config/` | `@Configuration` |

- **인터페이스 + Impl**: app의 구현 클래스는 이름이 `*Impl`이고, `Impl`을 뺀 이름의 인터페이스를 구현한다. Facade·Finder의 인터페이스는 같은 모듈의 `spi`에 있어야 한다. 구현 클래스는 package-private으로 둔다.
- **spi 하위 패키지**: `spi/dto`, `spi/event`에도 `package-info.java`에 `@NamedInterface("spi")`를 둔다. 빠지면 C8이 알려 준다.
- **다른 모듈 쓰기**: `package-info.java`에 `allowedDependencies = {"memo :: spi"}`로 적는다. 그 모듈의 `*Finder`로 읽고, 쓰기는 이벤트(`spi/event`)를 받아서 한다. 다른 모듈의 `*Facade`는 부르지 않는다.

### 방향 (C8)

```text
api ──▶ spi ◀── internal.app ──▶ internal.infra
                    │                 │
                    └──▶ internal.domain ◀┘
```

- api ↛ internal. spi ↛ internal, api. internal ↛ api.
- domain ↛ app, infra, config, spi, api.
- infra ↛ app, 다른 모듈의 spi.
- app.command → app.query는 `*Query` 인터페이스로만. app.query ↛ app.command.
- app.command는 `JdbcTemplate`·`JdbcClient`·`SqlSession`·`java.sql`을 직접 쓰지 않는다.
- 쓰기 `*Facade`는 api와 소유 모듈의 app.command만 부른다.

### 실패 메시지 (C8)

```text
[C8] it.cozy.app.memo.MemoService — 모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다. 컨트롤러는 …api, 모듈 사이 계약은 …spi, 구현은 …internal.app·domain·infra·config로 옮기세요.
[C8] it.cozy.app.memo.api.MemoController → it.cozy.app.memo.internal.infra.MemoStore — api는 internal을 쓰지 않습니다. 컨트롤러는 …spi의 Facade·Finder를 부릅니다.
[C8] it.cozy.app.memo.internal.app.query.MemoFinderImpl → it.cozy.app.memo.internal.app.command.MemoWriter — app.query는 app.command에 의존하지 않습니다(읽기가 쓰기를 부르지 않습니다).
```

## 템플릿

```text
templates/memo/
├── api/        list.html, form.html, detail.html     화면 (컨트롤러 뷰 "memo/api/list")
├── spi/        summary.html                          다른 모듈용 fragment (cozy/fragments.yml에 등록)
└── internal/   fields.html                           form·detail이 같이 쓰는 입력 칸과 버튼
```

### 참조와 모델 (R1, R3, R9, R10)

| 부르는 쪽 | 자기 internal | 자기 spi | 자기 api | 선언한 모듈의 spi | `cozy/`, `fragments/` | 모델 읽기 |
| --- | --- | --- | --- | --- | --- | --- |
| api | 된다 | 된다 | 된다 | 된다 | 된다 | 된다 |
| spi | 된다 | 된다 | 안 된다(R9) | 된다 | 된다 | 안 된다(R10) |
| internal | 된다 | 안 된다(R9) | 안 된다(R9) | 안 된다(R9) | 된다 | 안 된다(R10) |

- **화면(api)이 값을 모은다.** 모델을 읽는 곳은 api뿐이다. spi와 internal의 fragment는 파라미터로 받은 값과 그 안의 지역 변수(`th:each`, `th:with`)만 쓴다.
- **spi fragment**는 같은 모듈의 internal 조각을 조합하거나 혼자 그린다. 다른 모듈의 spi도 부를 수 있다(의존을 선언했을 때).
- **internal 조각**은 같은 모듈 안에서만 쓰인다. 다른 모듈의 템플릿은 부르지 않는다.

### 예: 화면이 조각을 조합한다

```html
<!-- memo/internal/fields.html: 값은 감싸는 form의 th:object와 파라미터로만 받는다 -->
<th:block th:fragment="memoFields(backLabel)">
  <div th:replace="~{cozy/component/field :: field('title', '제목', 'text')}"></div>
  ...
  <a class="btn" th:href="@{/memo}" th:text="${backLabel}">목록</a>
</th:block>

<!-- memo/api/form.html: 화면이 모델(form)을 th:object로 묶고 조각을 부른다 -->
<form th:object="${form}" ...>
  <th:block th:replace="~{memo/internal/fields :: memoFields('취소')}"></th:block>
</form>
```

- `field`가 쓰는 `*{title}`은 화면의 `th:object`에 바인딩된다. 조각 안에서 `*{...}`를 직접 쓰려면 조각 안에 `th:object`를 둔다(R10).

### 예: 다른 모듈의 spi fragment를 부른다

```html
<!-- dashboard/api/index.html. dashboard/package-info: allowedDependencies = {"memo :: spi", "activity :: spi"} -->
<div th:replace="~{memo/spi/summary :: memoSummary(${view.memo})}"></div>
<div th:replace="~{activity/spi/recent-activity :: recentActivity(${view.activities})}"></div>
```

### 반복과 조각 (R11)

```html
<!-- 틀림: th:replace가 th:each보다 먼저 실행되어 m이 없다 -->
<tr th:each="m : ${memos}" th:replace="~{memo/internal/row :: row(${m})}"></tr>
<!-- 맞음 -->
<th:block th:each="m : ${memos}"><tr th:replace="~{memo/internal/row :: row(${m})}"></tr></th:block>
```

### 검사

| 폴더 | 검사 |
| --- | --- |
| `api/` | 화면 스펙 `specs/{모듈}-{화면}.yml`이 있어야 한다(C5). 모든 상태가 렌더링되고 HTML이 유효해야 한다(C3, C4) |
| `spi/` | `cozy/fragments.yml`에 등록하고 샘플을 둔다(C5). 파라미터 타입에 api·internal 타입을 쓰지 않는다(C5) |
| `internal/` | 따로 검사하지 않는다. 그것을 쓰는 화면과 spi fragment를 렌더링할 때 같이 검사된다 |

- 컨트롤러가 반환하는 뷰는 `{모듈}/api/…`나 `{모듈}/spi/…`다(C7).

## 관련 문서

- 템플릿 규칙 전체: [rules.md](rules.md)
- 검사 C1~C8: [testing.md](testing.md)
- 화면 구성: [ui.md](ui.md)
- 이벤트: [events.md](events.md)
