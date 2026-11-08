# AGENTS.md — Cozy 앱 작업 규칙

이 앱은 Cozy 프레임워크를 쓴다. 스택은 Spring MVC, Thymeleaf, htmx, Tabler, MyBatis, SQLite다.

**구조는 `cozy` 명령이 만들고, 너는 명령이 남긴 빈칸만 채운다.** 파일을 직접 만들거나 옮기지 않는다. 어노테이션, `allowedDependencies`, `cozy/fragments.yml` 항목, 내비게이션 항목을 손으로 쓰지 않는다.

## 일하는 순서

1. `cozy describe`로 앱의 지도를 본다. 모듈, 모듈 사이 의존, spi 계약(Facade·Finder 메서드), 이벤트, 화면, 남은 빈칸이 나온다.
2. 구조가 필요하면 아래 명령을 실행한다. `--dry-run`을 붙이면 바꿀 파일만 보여 준다.
3. 명령이 출력한 빈칸(`cozy:slot`)을 채운다. 다 채우면 그 `cozy:slot` 표시를 지운다.
4. `cozy check`를 실행한다. 위반 끝에 `고치는 명령: cozy …`가 있으면 그 명령을 실행한다. 없으면 `—` 뒤의 고치는 법을 따른다.
5. `mvn -o verify`를 실행한다. 통과하면 끝이다.

## 명령

`cozy recipes`가 같은 목록을 보여 준다.

```text
  cozy add module <모듈> [--label 라벨]
      빈 모듈(package-info.java, allowedDependencies = {}). 빈칸: 없음
  cozy link <모듈> --uses <다른 모듈>[,…]
      package-info.java의 allowedDependencies에 "모듈 :: spi" 추가. 빈칸: 없음
  cozy add crud <모듈> --entity <E> --label 라벨 --icon 아이콘 --field 이름:종류[!][:라벨] … [--publish created,updated,deleted]
      목록·만들기·상세·수정·삭제 한 벌(spi, internal, api, SQL, 템플릿, 스펙, 테스트). 빈칸: 없음
  cozy add screen <모듈> <화면> --pattern overview|details --title 제목 [--field …] [--nav 라벨:아이콘]
      읽기 화면 하나(컨트롤러, 화면 DTO, 템플릿, 스펙, 내비게이션). 목록·폼은 crud가 만든다. 빈칸: 컨트롤러 view-model
  cozy add fragment <모듈> <이름> --param 이름:타입 … --purpose "용도"
      다른 모듈에 공개하는 fragment와 매니페스트 항목. 빈칸: 템플릿 fragment-body
  cozy add event <모듈> <이벤트> --field 이름:종류 …
      CozyEvent record와 직렬화 샘플 v1. 빈칸: 없음(발행할 한 줄은 에이전트가 넣는다)
  cozy add listener <모듈> --on <보내는 모듈>.<이벤트> [--name 이름]
      이벤트 리스너(필요하면 link 포함). 빈칸: listener-body
  cozy add migration <모듈> <설명>
      V{시각}__{모듈}_{설명}.sql. 빈칸: migration-sql
```

- **이름**: 모듈은 영문 소문자와 숫자(`memo`, `voc2`). 엔티티·이벤트는 대문자로 시작한다(`Memo`, `MemoCreated`). 필드는 소문자로 시작하는 camelCase다(`dueOn`).
- **필드** `이름:종류[!][:라벨]`: 종류는 `text`, `longtext`, `int`, `long`, `bool`, `date`, `instant`다. `!`는 필수다. 라벨은 화면에 보이는 한글 이름이다. 예: `--field title:text!:제목 --field dueOn:date:마감일`
  - `crud`에는 `text!` 필드가 하나 이상 있어야 한다. 첫 `text!` 필드가 목록의 이름 칸이 된다. `instant`는 쓰지 않는다(만든 시각·수정한 시각은 명령이 넣는다).
- **fragment 파라미터** `이름:종류` 또는 `이름:Record`(내 모듈 `spi/dto`의 record) 또는 `이름:list:Record`.
- **아이콘**: Tabler Icons 이름(`notes`, `checklist`, `dashboard`). 없는 이름이면 명령이 실패한다. 목록은 `/cozy/dev/catalog/icons`에 있다.
- **공백이 있는 값**은 따옴표로 감싼다: `--label "할 일"`, `--purpose "최근 활동 목록"`.

### 예: 메모를 만들면 활동 기록을 남긴다

```bash
cozy add crud memo --entity Memo --label 메모 --icon notes --field title:text!:제목 --field body:longtext:본문 --publish created
cozy add module activity --label 활동
cozy add migration activity create_activity       # 빈칸: CREATE TABLE
cozy add listener activity --on memo.MemoCreated  # 빈칸: 받았을 때 할 일 (memo :: spi 의존도 함께 선언된다)
```

- `--publish`가 없는 이벤트를 새로 만들었으면(`cozy add event`), 보내는 모듈의 `*FacadeImpl` 트랜잭션 안에 한 줄을 넣는다: `this.events.publishEvent(MemoCreated.of(...));`
- 명령이 없는 구조(Finder에 메서드 추가, `spi/dto`의 record, 서비스)는 아래 "파일 위치" 표대로 만든다. 이것은 다음 단계 명령(`cozy add query`, `cozy add command`)이 맡을 몫이다.

### 명령 결과 읽기

| 결과 | 뜻 | 할 일 |
| --- | --- | --- |
| `[cozy] 적용했습니다. 빈칸 N곳` | 파일을 만들었고 채울 곳이 N곳 남았다 | 나열된 `파일:줄`의 빈칸을 채운다 |
| `[cozy] 바뀐 것 없음` | 같은 내용이 이미 있다 | 없다 |
| 종료 코드 2 | 명령을 적용할 수 없었다. **아무 파일도 바뀌지 않았다** | 표준 오류의 이유를 읽고 인자를 고쳐 다시 실행한다 |

- `만듦`은 새 파일, `바꿈`은 기존 파일에 덧붙인 것, `그대로`는 이미 같은 내용인 파일이다.
- 기계가 읽을 출력이 필요하면 `--json`을 붙인다.
- 같은 명령을 다시 실행해도 안전하다. 내용이 다른 파일이 이미 있으면 덮어쓰지 않고 실패한다.

## 빈칸 (cozy:slot)

| 파일 | 표시 | 채우기 전 |
| --- | --- | --- |
| Java | `// cozy:slot {id} — {무엇을}` 다음 줄에 `throw new UnsupportedOperationException("cozy:slot {id}");` | 컴파일된다. 부르면 실패한다 |
| HTML | `<!--/* cozy:slot … */-->` | 렌더링된다 |
| SQL, XML, YAML | `-- cozy:slot …`, `<!-- cozy:slot … -->`, `# cozy:slot …` | 그대로 둔다 |

- 채운 뒤에는 표시와 `throw` 줄을 지운다. 표시가 남아 있으면 `mvn verify`가 `[C9]`로 실패한다.
- 표시만 지우고 채우지 않으면 검사가 잡지 못한다. 테스트를 함께 쓴다.

## 파일 위치

| 무엇 | 어디 |
| --- | --- |
| 모듈 | `src/main/java/{패키지}/{모듈}/`. 모듈은 기능 단위다(예: `home`, `order`) |
| 모듈 선언 | `{모듈}/package-info.java`의 `@ApplicationModule(allowedDependencies = {...})` (`cozy link`가 고친다) |
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
| 다른 모듈이 쓰는 fragment | `templates/{모듈}/spi/` + `cozy/fragments.yml`에 등록 (`cozy add fragment`가 함께 만든다) |
| 앱 공용 fragment | `templates/fragments/{layout,pattern,component}/` + `cozy/fragments.yml`에 등록 |
| 화면 스펙 | `specs/{모듈}-{화면}.yml` (상태 `default`, `empty` 필수) |
| SQL | `mapper/{모듈}/{이름}Mapper.xml` |
| 마이그레이션 | `db/migration/V{yyyyMMddHHmm}__{모듈}_{설명}.sql` (`cozy add migration`이 이름을 정한다) |
| 이벤트 샘플 | `src/test/resources/events/{이벤트}/v{n}.json` |

- app의 클래스는 항상 인터페이스 + `*Impl`이다. 쓰기 구현은 `@Transactional`, 읽기 구현은 `@Transactional(readOnly = true)`다.
- spi 시그니처에는 원시 값과 spi DTO만 쓴다.
- 컨트롤러는 spi만 부르고 spi DTO를 `api/dto`의 화면 DTO로 바꾼다. 뷰 이름은 `"{모듈}/api/{화면}"`이다.
  - htmx 부분 갱신은 `CozyViews.pageOrFragment`, 등록·수정 성공은 `CozyViews.redirect`로 처리한다.
  - 입력은 getter/setter가 있는 폼 클래스로 받고 `@Valid` + `BindingResult`로 검증한다.

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

- 구조를 손으로 바꾸지 않는다. 모듈·화면·이벤트·리스너·마이그레이션·공개 fragment는 명령으로 만들고, 의존 선언은 `cozy link`로 더한다.
- 다른 모듈의 `spi` 밖 클래스를 쓰지 않는다. 다른 모듈의 `*Facade`(쓰기)를 부르지 않는다. 다른 모듈의 쓰기는 이벤트로 일으킨다.
- 컨트롤러(api)에서 internal을 쓰지 않는다. 읽기 구현(query)에서 쓰기 구현(command)을 부르지 않는다. command에서 JDBC를 직접 쓰지 않는다.
- 동적 템플릿 참조(`~{${...}}`, `th:replace="${...}"`)를 쓰지 않는다.
- 인라인 스크립트(`<script>` 본문, `onclick=`, `hx-on`)와 인라인 style을 쓰지 않는다. 보안 정책(CSP)이 막는다.
- 외부 주소(CDN, `http://`)를 참조하지 않는다.
- 이미 적용된 마이그레이션 파일을 고치지 않는다. `cozy add migration`으로 새 파일을 더한다.
- 이벤트(`CozyEvent`) 필드를 지우거나 이름·타입을 바꾸지 않는다. 필드 추가만 하고, `schemaVersion`을 올리고, 새 샘플을 남긴다.
- 검사를 끄거나 테스트를 건너뛰지 않는다.

## 검증

- 빠른 검사는 `cozy check`(몇 초, 컴파일하지 않는다), 최종 판단은 `mvn -o verify`다. `cozy check`가 통과해도 `mvn -o verify`가 실패할 수 있다.
- 메시지 형식은 `[ID] 위치 → 대상 — 고치는 법`이다. 고치는 명령이 있으면 끝에 `고치는 명령: cozy …`가 붙는다. 그 명령을 그대로 실행한다.
  - 예: `[R3] templates/voc/api/list.html:1 → wiki/spi/card :: card(1개 인자) — voc의 allowedDependencies에 wiki가 없습니다. … 고치는 명령: cozy link voc --uses wiki`
- ID별 뜻:
  - `[R1]`~`[R15]`: 템플릿·디자인 규칙
  - `[C1]`~`[C8]`: 검사 (C1은 모듈 의존, C8은 모듈 안 구조)
  - `[C9]`: 채우지 않은 빈칸. 위치의 `cozy:slot`을 채우고 표시를 지운다
- 규칙 설명은 Cozy 문서 `docs/conventions/structure.md`, `rules.md`, `testing.md`, `cli.md`에 있다.
