# AGENTS.md — Cozy 앱 작업 규칙

이 앱은 Cozy 프레임워크를 쓴다. 스택은 Spring MVC, Thymeleaf, htmx, Tabler, MyBatis, SQLite다. 아래 규칙을 지키고, 끝나면 `mvn -o verify`를 실행한다.

## 파일 위치

| 무엇 | 어디 |
| --- | --- |
| 모듈 | `src/main/java/{패키지}/{모듈}/`. 모듈은 기능 단위다(예: `home`, `order`) |
| 모듈 선언 | `{모듈}/package-info.java`의 `@ApplicationModule(allowedDependencies = {...})` |
| 컨트롤러·서비스 | `{모듈}/` 바로 아래 |
| 화면 DTO(record)·폼(class) | `{모듈}/view/` |
| Mapper, 내부 구현 | `{모듈}/internal/`. 다른 모듈은 볼 수 없다 |
| 다른 모듈에 공개할 것(이벤트, 조회 인터페이스) | `{모듈}/api/` (`@NamedInterface("api")`) |
| 화면 템플릿 | `templates/{모듈}/{화면}.html` |
| 다른 모듈이 쓰는 fragment | `templates/{모듈}/api/` + `cozy/fragments.yml`에 등록 |
| 앱 공용 fragment | `templates/fragments/{layout,pattern,component}/` + `cozy/fragments.yml`에 등록 |
| 화면 스펙 | `specs/{모듈}-{화면}.yml` (상태 `default`, `empty` 필수) |
| SQL | `mapper/{모듈}/{이름}Mapper.xml` |
| 마이그레이션 | `db/migration/V{yyyyMMddHHmm}__{모듈}_{설명}.sql` |
| 이벤트 샘플 | `src/test/resources/events/{이벤트}/v{n}.json` |

## 새 모듈 만드는 순서

`home`을 복제해서 시작한다.

1. `package-info.java`: 쓰는 모듈만 `allowedDependencies`에 `"모듈::api"`로 적는다.
2. 컨트롤러: 뷰 이름은 `"{모듈}/{화면}"`이다. htmx 부분 갱신은 `CozyViews.pageOrFragment`, 등록·수정 성공은 `CozyViews.redirect`로 처리한다.
3. view DTO: 템플릿에는 record만 넘긴다. 입력은 getter/setter가 있는 폼 클래스로 받고 `@Valid` + `BindingResult`로 검증한다.
4. 템플릿: `cozy/layout/base :: appShell(...)`로 감싼다.
5. 스펙: `specs/{모듈}-{화면}.yml`을 만든다.

## 화면

- 먼저 패턴 카탈로그(`/cozy/dev/catalog`, IntelliJ "dev 실행")와 `cozy/fragments.yml`에서 맞는 패턴·컴포넌트를 찾아 쓴다. 없을 때만 새로 만든다.
- 템플릿은 자기 모듈, `cozy/`(프레임워크), `fragments/`(앱 공용), 허용된 모듈의 `api/`만 참조한다.

## 금지

- 다른 모듈의 `internal`이나 `api` 밖 클래스를 쓰지 않는다. 다른 모듈의 템플릿을 직접 참조하지 않는다.
- 동적 템플릿 참조(`~{${...}}`, `th:replace="${...}"`)를 쓰지 않는다.
- 인라인 스크립트(`<script>` 본문, `onclick=`, `hx-on`)와 인라인 style을 쓰지 않는다. 보안 정책(CSP)이 막는다.
- 외부 주소(CDN, `http://`)를 참조하지 않는다.
- 이미 적용된 마이그레이션 파일을 고치지 않는다. 새 파일을 추가한다.
- 이벤트(`CozyEvent`) 필드를 지우거나 이름·타입을 바꾸지 않는다. 필드 추가만 하고, `schemaVersion`을 올리고, 새 샘플을 남긴다.
- 검사를 끄거나 테스트를 건너뛰지 않는다.

## 검증

- 작업을 마치면 `mvn -o verify`를 실행한다.
- 실패 메시지 앞의 ID대로 고친다.
  - `[R1]`~`[R8]`: 템플릿 규칙
  - `[C1]`~`[C7]`: 검사
- 메시지 형식은 `[ID] 위치 → 대상 — 고치는 법`이다. `—` 뒤의 고치는 법을 따른다.
- 규칙 설명은 Cozy 문서 `docs/conventions/rules.md`와 `testing.md`에 있다.
