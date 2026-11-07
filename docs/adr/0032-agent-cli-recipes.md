# ADR-0032 부록: 레시피 목록

- 상태: 승인 (2026-11-07). [ADR-0032](0032-agent-cli.md)의 §9 범위를 구체화한다.
- 기준 예시: reference-app의 memo(`crud`), activity(`listener`, `fragment`), dashboard(`screen`). 아래 1단계 레시피를 이어 붙이면 reference-app과 같은 구조가 나와야 한다(M10 완료 기준).

## 1. 공통 규칙

### 표기

| 기호 | 뜻 | 예 |
| --- | --- | --- |
| `{base}` | 앱 기본 패키지의 경로 | `io/salvia95/cozy/reference` |
| `{m}` | 모듈 이름 | `memo` |
| `{M}` | 모듈 이름의 PascalCase | `Memo` |
| `{E}` | 엔티티 이름 | `Memo` |
| `{t}` | 테이블 이름, `{E}`의 snake_case | `memo` |
| `{Ev}` | 이벤트 이름 | `MemoCreated` |
| `{s}` | 화면 이름 | `index`, `list` |

### 이름

| 대상 | 형식 | 거절하는 것 |
| --- | --- | --- |
| 모듈 | `[a-z][a-z0-9]*` | 예약어 `cozy`, `fragments`, `error`, Java 예약어, 이미 있는 모듈 |
| 엔티티, 이벤트 | PascalCase | 같은 모듈에 이미 있는 이름 |
| 화면 | `[a-z][a-z0-9-]*` | 같은 모듈에 이미 있는 화면 |
| fragment | 파일은 kebab-case, fragment 이름은 camelCase | HTML 요소 이름과 같은 것(R12): `form`, `table`, `main` 등 |
| 마이그레이션 설명 | `[a-z0-9_]+` | — |

### 필드 형식

필드는 `이름:종류[!][:라벨]` 형식으로 적는다. `!`는 필수 입력, 라벨은 화면에 보이는 이름이다(빼면 이름을 그대로 쓴다). 예: `--field title:text!:제목 --field body:longtext:본문 --field pinned:bool:고정`.

| 종류 | Java | SQLite | 입력 칸(`field`의 type) | `!`일 때 | 샘플 값 |
| --- | --- | --- | --- | --- | --- |
| `text` | `String` | `TEXT` | `text` | `@NotBlank`, `NOT NULL CHECK (length(…) > 0)` | `예시 {라벨}` |
| `longtext` | `String` | `TEXT NOT NULL DEFAULT ''` | `textarea` | `@NotBlank` | `첫 줄\n둘째 줄` |
| `int` | `Integer` | `INTEGER` | `number` | `@NotNull`, `NOT NULL` | `3` |
| `long` | `Long` | `INTEGER` | `number` | `@NotNull`, `NOT NULL` | `1` |
| `bool` | `boolean` | `INTEGER NOT NULL DEFAULT 0 CHECK (… IN (0, 1))` | `checkbox` | (없음) | `true` |
| `date` | `LocalDate` | `TEXT` | `date` | `@NotNull`, `NOT NULL` | `2026-11-06` |
| `instant` | `Instant` | `TEXT` | (폼에 두지 않는다) | `NOT NULL` | `2026-11-06T01:00:00Z` |

- 시간 값의 저장 형식은 [sqlite.md](../conventions/sqlite.md)를 따른다.
- 라벨은 한글을 쓸 수 있다. 문구의 조사(을/를, 이/가)는 라벨 마지막 글자의 받침을 보고 명령이 고른다. 예: "메모를 만들었습니다", "일정을 만들었습니다".

### 동작

- **원자성**: 계획을 먼저 세우고, 하나라도 실패하면 아무 파일도 쓰지 않는다. `--dry-run`은 계획만 출력한다.
- **같은 명령 두 번**: 이미 같은 내용이 있으면 아무것도 하지 않고 "바뀐 것 없음"을 알린다. 다른 내용이 있으면 덮어쓰지 않고 실패한다.
- **알아볼 수 없는 파일**: 덧붙일 파일이 명령이 만든 형식이 아니거나 `cozy:append` 표시가 없으면 실패한다. 이때 무엇을 손으로 해야 하는지 알린다.
- **사전 조건**: 레시피가 필요로 하는 스타터(`cozy-starter-sqlite`, `cozy-starter-events`)가 pom에 없으면 실패한다. 아키타입 옵션(`persistence`, `events`)을 알린다.

### 출력

```text
$ cozy add listener activity --on memo.MemoCreated
[cozy] 계획
  바꿈  src/main/java/{base}/activity/package-info.java    allowedDependencies += "memo :: spi"
  만듦  src/main/java/{base}/activity/internal/app/command/MemoCreatedListener.java
[cozy] 적용했습니다. 빈칸 1곳
  src/main/java/{base}/activity/internal/app/command/MemoCreatedListener.java:24
    listener-body — MemoCreated를 받았을 때 할 일을 쓰세요. 다 쓰면 cozy:slot 표시를 지웁니다
[cozy] 다음: 빈칸을 채우고 cozy check, 그다음 mvn verify
```

`--json`을 붙이면 같은 내용을 `{plan: [{action, file, detail}], slots: [{id, file, line, what}], next}`로 낸다.

## 2. 1단계 레시피

### 2.1 `module`: 빈 모듈

```text
cozy add module <m> [--label 라벨]
```

| 구분 | 내용 |
| --- | --- |
| 만드는 파일 | `{m}/package-info.java`: `@ApplicationModule(allowedDependencies = {})`. Javadoc 첫 줄은 라벨 |
| 덧붙이는 곳 | 없음 |
| 빈칸 | 없음 |
| 생성 직후 | 통과 |

- `spi/`, `spi/dto/`, `spi/event/`의 `package-info.java`는 그 안에 처음 무언가를 만드는 레시피가 함께 만든다. 빈 패키지는 두지 않는다.

### 2.2 `link`: 모듈 의존 선언

```text
cozy link <m> --uses <다른 모듈>[,<다른 모듈>…]
```

| 구분 | 내용 |
| --- | --- |
| 바꾸는 파일 | `{m}/package-info.java`의 `allowedDependencies`에 `"{다른 모듈} :: spi"`를 더한다. 결과는 항상 배열 형식 `{ "a :: spi", "b :: spi" }`이다 |
| 알아보는 형식 | `{}`, `"x"`, `"x :: spi"`, `{ … }` 배열. 문자열 상수가 아닌 값(상수 참조, 식)이 있으면 실패한다 |
| 빈칸 | 없음 |
| 생성 직후 | 통과 |
| 실패 | 대상 모듈이 없을 때. 대상에 `spi/`가 없을 때("공개된 것이 없습니다. 먼저 `cozy add fragment`나 `cozy add event`로 공개하세요"). 의존이 순환할 때(경로를 보여 준다) |

- 다른 레시피(`listener`, `fragment`를 다른 모듈 화면에 쓸 때)가 필요하면 같은 계획 안에서 `link`를 함께 실행한다.

### 2.3 `crud`: 목록·만들기·상세·수정·삭제 한 벌

```text
cozy add crud <m> --entity <E> --label 라벨 --icon 아이콘 --field … [--field …] [--publish created,updated,deleted]
```

reference-app의 memo와 같은 한 벌을 만든다. 흐름과 문구는 [design.md](../conventions/design.md) §2·§3을 따른다. 모듈이 없으면 함께 만든다. 모듈이 있으면 만들 파일과 겹치는 것이 없을 때만 진행한다.

| 구분 | 파일 |
| --- | --- |
| spi | `spi/package-info.java`, `spi/{E}Facade.java`(create, update, delete), `spi/{E}Finder.java`(find, search), `spi/dto/package-info.java`, `spi/dto/{E}Item.java`, `spi/dto/{E}Page.java` |
| internal | `internal/app/command/{E}FacadeImpl.java`, `internal/app/query/{E}FinderImpl.java`, `internal/domain/{E}.java`, `internal/infra/{E}Mapper.java`, `internal/infra/{E}Row.java`, `internal/config/{M}Configuration.java`(내비게이션 항목, `cozy:append nav`) |
| api | `api/{E}Controller.java`(`/{m}`, `/{m}/new`, `/{m}/{id}`, `/{m}/{id}/edit`, `/{m}/{id}/delete`), `api/dto/{E}Form.java`, `api/dto/{E}Filter.java`, `api/dto/{E}Views.java` |
| SQL | `db/migration/V{yyyyMMddHHmm}__{m}_create_{t}.sql`(필드 + `id`, `created_at`, `updated_at`), `mapper/{m}/{E}Mapper.xml` |
| 템플릿 | `templates/{m}/api/list.html`, `form.html`, `detail.html`, `edit.html`, `templates/{m}/internal/fields.html` |
| 스펙 | `specs/{m}-list.yml`(default, empty, noResults, paged), `{m}-form.yml`(default, empty, invalid), `{m}-detail.yml`, `{m}-edit.yml` |
| 테스트 | `{m}/{E}CrudTest.java`(Facade·Finder로 만들기·읽기·수정·삭제), `{m}/{E}ScreenTest.java`(MockMvc로 화면 흐름, C7) |

- **검색**: 목록 필터 `q`는 `text`·`longtext` 필드를 LIKE로 찾는다. 다른 조건은 2단계 `query`로 더한다.
- **`--publish`**: `{E}Created`, `{E}Updated`, `{E}Deleted` 이벤트를 `event` 레시피로 함께 만든다. 그리고 `{E}FacadeImpl`의 해당 메서드 안, 같은 트랜잭션에서 발행한다. 이벤트 필드는 `{e}Id`와 `text!` 필드다.
- **이벤트 스타터가 있을 때**: `{E}FacadeImpl`은 `--publish`가 없어도 `ApplicationEventPublisher`를 받아 둔다. 나중에 에이전트가 발행 한 줄만 쓰면 되게 하기 위해서다.
- **빈칸**: 없음. 업무 규칙(검증 추가, 기본값, 정렬)은 생성된 코드를 에이전트가 고친다.
- **생성 직후**: 통과한다. 생성된 테스트까지 포함한다.
- **프레임워크 레포의 확인**: 이 레시피가 만든 화면은 runtime-verifier로 확인한다(ADR-0030). 레시피 템플릿이 바뀔 때마다 다시 한다.

### 2.4 `screen`: 화면 하나

```text
cozy add screen <m> <s> --pattern overview|details|list|master-detail --title 제목 [--field …] [--nav 라벨:아이콘]
```

CRUD가 아닌 읽기 화면이다. 예: dashboard의 index.

| 구분 | 내용 |
| --- | --- |
| 만드는 파일 | `api/{M}{S}Controller.java`(`s`가 `index`면 `{M}Controller`, 경로 `/{m}`. 아니면 `/{m}/{s}`), `api/dto/{S}View.java`(필드로 만든 record), `templates/{m}/api/{s}.html`(패턴을 쓰고, 필드를 패턴의 기본 배치로 보인다), `specs/{m}-{s}.yml`(default, empty) |
| 덧붙이는 곳 | `--nav`가 있으면 `internal/config/{M}Configuration.java`의 `cozy:append nav`. 파일이 없으면 만든다 |
| 빈칸 | 컨트롤러 `view-model`: Finder로 값을 모아 `{S}View`를 만든다 |
| 생성 직후 | C9 1곳 |

- 다른 모듈의 Finder나 spi fragment를 쓰려면 먼저 `cozy link`를 실행한다. 빈칸 설명에 지금 쓸 수 있는 Finder 목록을 함께 적는다.
- 기존 컨트롤러에 메서드를 더하지 않고 화면마다 컨트롤러를 따로 만든다. 1단계에서는 Java 파일의 본문을 고치지 않기 위해서다.

### 2.5 `fragment`: 다른 모듈에 공개하는 fragment

```text
cozy add fragment <m> <이름> --param 이름:타입 [--param …] --purpose "용도"
```

| 구분 | 내용 |
| --- | --- |
| 타입 | 필드 종류(§1), `{m}.spi.dto`의 record 이름, `list:{record 이름}` |
| 만드는 파일 | `templates/{m}/spi/{이름}.html`: `th:fragment="{camelCase}(파라미터…)"`, 본문은 `card` 하나와 빈칸 |
| 덧붙이는 곳 | `cozy/fragments.yml`에 항목을 더한다(id, template, signature, purpose, params, samples default·empty). 샘플 값은 타입에서 만든다. record는 소스의 구성 요소를 읽는다 |
| 빈칸 | 템플릿 `fragment-body`: 파라미터로 받은 값만으로 그린다(R10) |
| 생성 직후 | C9 1곳 |
| 실패 | 타입이 api·internal 타입일 때(C5와 같은 기준). record를 알아볼 수 없을 때 |

### 2.6 `event`: 모듈 사이 이벤트

```text
cozy add event <m> <Ev> --field … [--field …]
```

| 구분 | 내용 |
| --- | --- |
| 만드는 파일 | `spi/event/{Ev}.java`(record, `CozyEvent`, `eventId`, `schemaVersion` = 1, 필드, `of(…)`), `spi/event/package-info.java`(없으면), `src/test/resources/events/{Ev}/v1.json`(샘플 값) |
| 빈칸 | 없음 |
| 생성 직후 | 통과(C6 호환 검사 포함) |
| 출력에 더하는 것 | 발행할 한 줄 `this.events.publishEvent({Ev}.of(…));`과 넣을 곳(`{M}FacadeImpl`의 쓰기 메서드, 트랜잭션 안) |

- 발행할 위치는 업무 로직이므로 에이전트가 정한다. `crud --publish`로 만든 이벤트는 명령이 발행까지 넣는다.
- 발행하는 곳이 없는 이벤트는 `cozy describe`와 `cozy check`가 경고한다(소스에서 `{Ev}.of(`나 `new {Ev}(`를 찾는다). 빌드는 실패하지 않는다.

### 2.7 `listener`: 이벤트 받기

```text
cozy add listener <m> --on <보내는 모듈>.<Ev> [--name 이름]
```

| 구분 | 내용 |
| --- | --- |
| 함께 하는 것 | 보내는 모듈이 다르면 `cozy link <m> --uses <보내는 모듈>` |
| 만드는 파일 | `internal/app/command/{Ev}Listener.java`(이름을 주면 `{이름}Listener`): `@Component`, `@ApplicationModuleListener void on({Ev} event)`. 같은 모듈에 `{M}Facade`가 있으면 생성자로 받는다 |
| 빈칸 | `listener-body`: 받았을 때 할 일. 자기 모듈의 Facade를 부른다. 다른 모듈의 Facade는 부르지 않는다(C8) |
| 생성 직후 | C9 1곳 |
| 실패 | 이벤트가 없을 때. 같은 모듈에 같은 이름의 리스너가 있을 때(`--name`을 알린다) |

- 빈칸 설명에 실행 방식을 함께 적는다. 커밋 뒤 별도 트랜잭션에서 비동기로 실행되고, 같은 이벤트는 인박스 덕분에 한 번만 처리된다([events.md](../conventions/events.md)).
- 리스너 메서드의 이름과 위치는 리스너 id가 된다. 그래서 명령이 정한 이름(`on`)을 바꾸지 말라고 빈칸 설명에 적는다.

### 2.8 `migration`: 스키마 변경

```text
cozy add migration <m> <설명>
```

| 구분 | 내용 |
| --- | --- |
| 만드는 파일 | `db/migration/V{yyyyMMddHHmm}__{m}_{설명}.sql`. 시각은 지금 시각과 마지막 마이그레이션 + 1분 중 늦은 쪽이다. 순서가 뒤집히거나 이름이 겹치지 않게 하기 위해서다 |
| 빈칸 | `migration-sql`: DDL. 그 모듈의 테이블만 다룬다 |
| 생성 직후 | C9 1곳 |

## 3. 2단계 레시피 (기존 Java 본문을 고친다)

모두 명령이 만든 파일의 `cozy:append` 표시에만 덧붙인다. 표시가 없으면 실패한다.

| 레시피 | 하는 일 | 빈칸 |
| --- | --- | --- |
| `cozy add query <m> <이름> --param … --returns …` | `{E}Finder`에 메서드, `{E}FinderImpl`에 구현, `{E}Mapper`에 메서드, Mapper XML에 `<select>` | SQL, 행 → spi DTO 변환 |
| `cozy add command <m> <이름> --param … --returns …` | `{E}Facade`에 메서드, `{E}FacadeImpl`에 구현 | 메서드 본문 |
| `cozy add field <m> <필드>` | crud 한 벌에 필드 하나: `ALTER TABLE` 마이그레이션, Row, domain, Item, Form, Views, Mapper XML 열, `fields.html`, 스펙 fixture | 없음(기본값이 필요한 필수 필드는 마이그레이션 빈칸) |
| `cozy add event-version <m> <Ev> --field …` | 이벤트에 필드를 더하고 `schemaVersion`을 올리고, 샘플 `v{n}.json`을 더한다 | 없음 |
| `cozy enable events` | pom에 `cozy-starter-events`를 더한다 | 없음 |

## 4. 조회·검사 명령

| 명령 | 하는 일 |
| --- | --- |
| `cozy recipes` | 레시피 목록과 인자. `AGENTS.md`에 넣는 내용과 같다 |
| `cozy describe [--json]` | 모듈, 의존, spi 시그니처, 이벤트·리스너, 화면·스펙 상태, 공개 fragment, 남은 빈칸 |
| `cozy check [--json]` | R1~R15, 소스로 판단하는 C8, C9. 컴파일하지 않는다 |

### 위반 → 명령

`cozy check`와 `mvn verify`의 메시지에 고치는 명령이 있으면 함께 적는다.

| 위반 | 명령 |
| --- | --- |
| R3: 선언하지 않은 모듈의 spi fragment를 참조 | `cozy link <m> --uses <대상>` |
| C1: 선언하지 않은 모듈의 spi 타입을 사용 | `cozy link <m> --uses <대상>` |
| R1: 템플릿 최상위 폴더가 모듈이 아님 | `cozy add module <이름>` (오타가 아니라면) |
| C5: 스펙 없는 화면 템플릿 | `cozy add screen …`으로 만든 화면이 아니면 없음. 스펙 형식을 알린다 |
| C6: 이벤트 샘플이 없음 | `cozy add event-version …` (2단계) |
| C9: 빈칸이 남음 | 없음. 에이전트가 채운다 |

## 5. reference-app을 레시피로 다시 만들기 (M10 완료 기준)

```bash
cozy new -DartifactId=reference-app -Dpersistence=true -Devents=true …
cozy add crud memo --entity Memo --label 메모 --icon notes \
  --field title:text!:제목 --field body:longtext:본문 --field pinned:bool:고정 --publish created
cozy add module activity --label 활동
cozy add migration activity create_activity          # 빈칸: CREATE TABLE activity …
cozy add listener activity --on memo.MemoCreated     # 빈칸: 활동 기록 (link memo 포함)
cozy add fragment activity recent-activity --param items:list:ActivityItem --purpose "최근 활동 목록"
cozy add screen dashboard index --pattern overview --title 대시보드 --nav 대시보드:dashboard
cozy link dashboard --uses memo,activity              # screen 빈칸에서 Finder를 쓰기 전에
```

- 빈칸을 채우기 전에는 C9만 실패해야 한다. reference-app의 해당 코드로 빈칸을 채우면 `mvn verify`가 통과해야 한다.
- activity의 `ActivityItem`, `ActivityFinder`, `ActivityService`는 1단계로 만들 수 없다. 이 부분은 2단계 `query`·`command`를 기다리거나 손으로 쓴다. 1단계에서 손으로 써야 하는 부분이 얼마나 되는지는 평가(ADR-0032 §8)로 잰다.
