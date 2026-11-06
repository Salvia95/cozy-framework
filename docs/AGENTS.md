# AGENTS.md — Cozy 앱 작업 규칙

이 앱은 Cozy 프레임워크를 쓴다. 스택은 Spring MVC, Thymeleaf, htmx, Tabler, MyBatis, SQLite다. 아래 규칙을 지키고, 끝나면 `mvn -o verify`를 실행한다.

## 파일 위치

| 무엇 | 어디 |
| --- | --- |
| 모듈 | `src/main/java/{패키지}/{모듈}/`. 모듈은 기능 단위다(예: `home`, `order`) |
| 모듈 선언 | `{모듈}/package-info.java`의 `@ApplicationModule(allowedDependencies = {...})` |
| 모듈 루트 | `package-info.java`만 |
| 컨트롤러 | `{모듈}/api/` (컨트롤러만) |
| 화면 DTO(record)·폼(class) | `{모듈}/api/dto/` |
| 다른 모듈이 쓰는 계약 | `{모듈}/spi/`: `*Facade`(쓰기)·`*Finder`(읽기) 인터페이스. `spi/dto/`(record), `spi/event/`(CozyEvent). 패키지마다 `@NamedInterface("spi")` |
| 쓰기 구현 | `{모듈}/internal/app/command/`: `*FacadeImpl`, `*Service`+`*ServiceImpl`, 이벤트 리스너 `*Listener` |
| 읽기 구현 | `{모듈}/internal/app/query/`: `*FinderImpl`, `*Query`+`*QueryImpl`, `*Projection`+`*ProjectionImpl` |
| 도메인 모델·규칙 | `{모듈}/internal/domain/` (Repository·Service·Mapper 없음) |
| Mapper, 행, 저장소 | `{모듈}/internal/infra/` |
| 모듈 전용 설정·빈 | `{모듈}/internal/config/` |
| 화면 템플릿 | `templates/{모듈}/api/{화면}.html` (모델을 읽는 곳은 여기뿐) |
| 화면이 조합하는 조각 | `templates/{모듈}/internal/` (값은 파라미터로만) |
| 다른 모듈이 쓰는 fragment | `templates/{모듈}/spi/` + `cozy/fragments.yml`에 등록 (값은 파라미터로만) |
| 앱 공용 fragment | `templates/fragments/{layout,pattern,component}/` + `cozy/fragments.yml`에 등록 |
| 화면 스펙 | `specs/{모듈}-{화면}.yml` (상태 `default`, `empty` 필수) |
| SQL | `mapper/{모듈}/{이름}Mapper.xml` |
| 마이그레이션 | `db/migration/V{yyyyMMddHHmm}__{모듈}_{설명}.sql` |
| 이벤트 샘플 | `src/test/resources/events/{이벤트}/v{n}.json` |

## 새 모듈 만드는 순서

`home`을 복제해서 시작한다.

1. `package-info.java`: 쓰는 모듈만 `allowedDependencies`에 `"모듈 :: spi"`로 적는다.
2. spi: `*Facade`(쓰기)·`*Finder`(읽기) 인터페이스와 `spi/dto`의 record. 시그니처에는 원시 값과 spi DTO만 쓴다.
3. internal: `app/command`의 `*FacadeImpl`(`@Transactional`), `app/query`의 `*FinderImpl`(`@Transactional(readOnly = true)`), `domain`, `infra`(Mapper). app의 클래스는 항상 인터페이스 + `*Impl`이다.
4. 컨트롤러(`api/`): spi만 부르고 spi DTO를 `api/dto`의 화면 DTO로 바꾼다. 뷰 이름은 `"{모듈}/api/{화면}"`이다. htmx 부분 갱신은 `CozyViews.pageOrFragment`, 등록·수정 성공은 `CozyViews.redirect`로 처리한다. 입력은 getter/setter가 있는 폼 클래스로 받고 `@Valid` + `BindingResult`로 검증한다.
5. 템플릿: 화면은 `cozy/layout/base :: appShell(...)`로 감싼다. 같이 쓰는 조각은 `internal/`에 두고 파라미터로 값을 받는다.
6. 스펙: `specs/{모듈}-{화면}.yml`을 만든다(`api/`는 이름에서 뺀다).

## 화면

- 먼저 패턴 카탈로그(`/cozy/dev/catalog`, IntelliJ "dev 실행")와 `cozy/fragments.yml`에서 맞는 패턴·컴포넌트를 찾아 쓴다. 없을 때만 새로 만든다.
- 화면은 패턴으로 감싼다. 패턴이 제목줄(`page-header`), 본문(`page-body`), 플래시를 그린다. `container-xl` 같은 틀을 직접 만들지 않는다.
  - 목록: `list-with-filter`
  - 만들기·수정: `form-page`. `<form class="card">` + `formActions`
  - 상세: `details`. 읽기 전용, 머리에 [deleteConfirm] [수정]
  - 개요: `overview`
  - 목록 + 상세: `master-detail`
- 디자인 원칙은 `docs/conventions/design.md`를 따른다.
  - 주 버튼은 묶음마다 하나, 맨 오른쪽에 둔다(R15). 폼 버튼은 오른쪽 아래 [취소] [주 버튼]이다.
  - 색 클래스(`bg-*`, `text-red`, `btn-outline-danger`)를 직접 쓰지 않는다(R14). `badge('고정', 'neutral')`처럼 tone을 넘긴다. 분류는 neutral이다.
  - 버튼은 "대상 + 동사"(메모 만들기), 결과 알림은 `CozyFlash.success("메모를 만들었습니다")`로 쓴다.
  - 비어 있을 때는 `emptyStateAction`(만들기)과 `noResults`(조건 지우기)를 구분한다.
- 아이콘은 `<svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>`로 쓴다. 이름은 `/cozy/dev/catalog/icons`에서 찾는다. 버튼 글자 앞, 빈 상태, 내비(`CozyNavItem`의 네 번째 인자)에 둔다.
- 마크업은 Tabler 클래스를 따른다: 카드 안의 목록은 `list-group list-group-flush`, 상세는 `datagrid`, 숫자는 `stat`, 결과 없음은 `emptyState`.
- fragment 이름은 HTML 요소 이름(`form`, `table`)과 겹치지 않게 짓는다(예: `memoForm`).
- 템플릿은 자기 모듈, `cozy/`(프레임워크), `fragments/`(앱 공용), 허용된 모듈의 `spi/`만 참조한다.
- 같은 모듈 안에서는 api → spi → internal 방향으로만 참조한다. internal은 다른 모듈을 참조하지 않는다.
- spi·internal fragment는 `${...}`로 파라미터와 `th:each`·`th:with` 지역 변수만 읽는다. 부르는 화면의 모델을 읽지 않는다.
- 한 요소에 `th:each`와 `th:replace`를 같이 쓰지 않는다. `<th:block th:each="...">` 안에 둔다.

## 금지

- 다른 모듈의 `spi` 밖 클래스를 쓰지 않는다. 다른 모듈의 `*Facade`(쓰기)를 부르지 않는다. 다른 모듈의 쓰기는 이벤트로 일으킨다.
- 컨트롤러(api)에서 internal을 쓰지 않는다. 읽기 구현(query)에서 쓰기 구현(command)을 부르지 않는다. command에서 JDBC를 직접 쓰지 않는다.
- 동적 템플릿 참조(`~{${...}}`, `th:replace="${...}"`)를 쓰지 않는다.
- 인라인 스크립트(`<script>` 본문, `onclick=`, `hx-on`)와 인라인 style을 쓰지 않는다. 보안 정책(CSP)이 막는다.
- 외부 주소(CDN, `http://`)를 참조하지 않는다.
- 이미 적용된 마이그레이션 파일을 고치지 않는다. 새 파일을 추가한다.
- 이벤트(`CozyEvent`) 필드를 지우거나 이름·타입을 바꾸지 않는다. 필드 추가만 하고, `schemaVersion`을 올리고, 새 샘플을 남긴다.
- 검사를 끄거나 테스트를 건너뛰지 않는다.

## 검증

- 작업을 마치면 `mvn -o verify`를 실행한다.
- 실패 메시지 앞의 ID대로 고친다.
  - `[R1]`~`[R15]`: 템플릿·디자인 규칙
  - `[C1]`~`[C8]`: 검사 (C8은 모듈 안 구조)
- 메시지 형식은 `[ID] 위치 → 대상 — 고치는 법`이다. `—` 뒤의 고치는 법을 따른다.
- 규칙 설명은 Cozy 문서 `docs/conventions/structure.md`, `rules.md`, `testing.md`에 있다.
