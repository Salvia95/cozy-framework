# ADR-0033 2단계 레시피: 이미 있는 코드에 메서드와 SQL 더하기

- 상태: 승인 (2026-11-08, 사용자 결정 "제안대로 모두 승인"). 아래 "정한 것" 여섯 가지
- 출처: 사용자 지시 "2단계 레시피 ADR 초안 정리해줘"
- 이어받는 결정: [ADR-0032](0032-agent-cli.md) §9 범위, [부록](0032-agent-cli-recipes.md) §3 2단계 레시피
- 바꾸는 결정:
  - ADR-0032 §2 "기존 파일은 표시된 자리에만 덧붙인다": Java와 Mapper XML은 표시 대신 파서가 찾은 위치에 덧붙인다(§2).
  - 부록 §3 목록: `dto`를 더하고, `field`는 뒤로 미룬다(§1).
  - 부록 §2.7 `listener`: Facade를 주입할지 앱 상태로 정하지 않는다(§5.6).

## 배경

- **1단계는 새 파일만 만든다.** M10에서 레시피로 reference-app 구조를 다시 만들었을 때, 빈칸이 아닌데도 손으로 써야 했던 구조가 있었다.

  | 손으로 쓴 것 (samples/recipes/hand) | 무엇 | 2단계에서 맡을 레시피 |
  | --- | --- | --- |
  | `activity/spi/dto/ActivityItem.java` + `package-info.java` | 다른 모듈에 내보내는 record | `dto` |
  | `ActivityFinder`, `ActivityFinderImpl`, `ActivityMapper.findRecent`, `ActivityRow`, `<select id="findRecent">` | 읽기 하나 | `query` |
  | `ActivityService`, `ActivityServiceImpl`, `ActivityMapper.insert`, `<insert id="insert">` | 쓰기 하나 | `command` |
  | (reference-app에만 있음) `MemoSummary`, `MemoFinder.summary()`, `memo/summary` fragment | 요약 값 | `dto` + `query` + 1단계 `fragment` |

  - 작은 모델에게 이 부분은 이벤트 추가와 같은 종류의 일이다. 인터페이스, 구현, Mapper, XML 네 곳의 이름·타입·`@Param`·`namespace`·`resultType`을 서로 맞춰야 한다.
- **부록 §3의 전제가 맞지 않는다.** 부록은 "명령이 만든 파일의 `cozy:append` 표시에만 덧붙인다"고 적었다.
  - M10의 `crud` 템플릿에는 그런 표시가 없다.
  - 손으로 쓴 모듈(reference-app의 activity)과 아키타입 `home`에도 표시가 없다.
  - 에이전트가 표시를 지우거나 옮기면 그 뒤로는 덧붙일 수 없다.
- **2단계는 에이전트가 이미 고친 파일을 다시 연다.** 1단계는 파일 전체가 같으면 "바뀐 것 없음"이었다. 2단계 대상 파일은 에이전트가 메서드 본문을 채운 뒤이므로, 파일 전체를 비교하면 다시 실행할 때마다 충돌한다.
- **이미 javac 파서가 있다.** cozy-cli는 M9부터 `com.sun.source`로 Java를 읽고, M10에서는 `package-info.java`의 의존 선언을 파서가 찾은 위치에서 고친다. 새 의존성 없이 타입 본문의 끝, import 목록, 메서드 시그니처를 정확히 찾을 수 있다.

## 결정

### 1. 범위와 순서

| 레시피 | 하는 일 | 빈칸 | 마일스톤 |
| --- | --- | --- | --- |
| `cozy add dto <m> <Record> --field …` | `spi/dto`에 record 하나 (+ `package-info.java`) | 없음 | M12 |
| `cozy enable events` | pom에 `cozy-starter-events`를 더한다 | 없음 | M12 |
| `cozy add query <m> <이름> [--param …] --returns <반환> [--in <Finder>]` | Finder 메서드, 구현, Mapper 메서드, 행(record), `<select>` | `query-sql` | M13 |
| `cozy add command <m> <이름> [--param …] [--returns <반환>] [--sql insert\|update\|delete]` | Facade 메서드와 구현. `--sql`이면 Mapper 메서드와 SQL 문도 | `command-body`, (`--sql`이면) `command-sql` | M13 |
| `cozy add event-version <m> <Ev> --field …` | 이벤트에 필드를 더하고 `schemaVersion`을 올리고 샘플 `v{n+1}.json`을 더한다 | 없음 | M14 |
| `cozy add field <m> <필드>` | crud 한 벌에 필드 하나 | — | **보류** (정한 것 4) |

- **범위 밖은 그대로다**: 이름 바꾸기, 지우기, 옮기기, 메서드 시그니처 바꾸기. 손으로 하고 검사로 확인한다.
- **`dto`를 따로 둔다**: record는 `query`의 반환 타입이면서 1단계 `fragment`의 파라미터 타입이기도 하다(M10에서 손으로 쓴 `ActivityItem`이 그 경우다). 그래서 `query`가 record를 만들게 하지 않고 따로 만든다. `query`는 없는 record를 받으면 `먼저 cozy add dto …`를 알리고 실패한다.

### 2. 덧붙이는 위치

| 파일 | 위치 | 확인하는 것 (아니면 실패) |
| --- | --- | --- |
| Java 인터페이스·클래스 | javac가 찾은 그 타입 본문의 마지막 `}` 바로 앞. 빈 줄 하나를 두고 탭 들여쓰기로 넣는다 | 파일에 그 이름의 최상위 타입이 하나. 문법 오류 없음 |
| Java import | 마지막 import 뒤. 이미 있으면(같은 이름, 같은 패키지의 `*`) 더하지 않는다. 정렬하지 않는다 | 같은 단순 이름을 다른 패키지에서 import하고 있지 않다 |
| Java 생성자 주입 (새로 만든 구현만) | — | 이미 있는 구현의 생성자는 고치지 않는다(§4) |
| Mapper XML | `</mapper>` 바로 앞 | 루트가 `mapper`이고 `namespace`가 그 Mapper 인터페이스의 이름과 같다. JDK의 XML 파서로 읽는다 |
| pom | `cozy-starter-sqlite` 의존 블록 바로 뒤 | 그 블록이 한 번 있다. `cozy-starter-events`가 이미 있으면 바뀐 것 없음 |

- 1단계에서 정한 `// cozy:append nav`(내비게이션)는 그대로 둔다. 메서드 본문 안이라 파서로 위치를 정할 기준이 없기 때문이다.
- 고친 파일을 다시 javac로 읽어 문법 오류가 없는지 확인한 뒤 적용한다. XML도 다시 읽어 본다.

### 3. 다시 실행과 충돌: 시그니처로 비교한다

- **본문은 비교하지 않는다.** 에이전트가 채운 뒤이기 때문이다. 비교하는 것은 메서드 이름, 파라미터 타입, 반환 타입, Mapper XML 문의 `id`와 종류(`select`·`insert`…)다.
- 한 레시피가 손대는 곳(예: `query`는 Finder, 구현, Mapper, XML 네 곳)마다 판단한다.

  | 상태 | 결과 |
  | --- | --- |
  | 네 곳 모두 같은 시그니처로 있다 | 바뀐 것 없음 |
  | 네 곳 모두 없다 | 더한다 |
  | 일부만 있다 | 실패. 어디에 있고 어디에 없는지 알린다. 손으로 맞추거나 있는 것을 지운 뒤 다시 실행한다 |
  | 같은 이름이 다른 시그니처로 있다 | 실패. 오버로드를 만들지 않는다 |

- 새로 만드는 파일(없던 Finder, 행 record 등)은 1단계처럼 파일 전체로 비교한다.

### 4. 대상 고르기

추측하지 않는다. 하나로 정해지지 않으면 실패하고 고르는 옵션을 알린다.

| 대상 | 고르는 법 | 없을 때 |
| --- | --- | --- |
| Finder (`query`) | 모듈 `spi`의 `*Finder`가 하나면 그것. 둘 이상이면 `--in <이름>`이 필요하다 | `{M}Finder`와 `internal/app/query/{M}FinderImpl`을 만든다 |
| Facade (`command`) | 모듈 `spi`의 `*Facade`가 하나면 그것. 둘 이상이면 `--in` | `{M}Facade`와 `internal/app/command/{M}FacadeImpl`을 만든다 |
| 구현 | `app/query`(또는 `app/command`)에서 그 인터페이스를 `implements`하는 클래스. 하나여야 한다 | 위에서 함께 만든다 |
| Mapper | 구현의 생성자 파라미터 중 `internal/infra`의 `*Mapper`. 하나여야 한다 | 구현을 새로 만들 때만 `{M}Mapper`와 `mapper/{m}/{M}Mapper.xml`을 함께 만든다. 이미 있는 구현에 Mapper가 없으면 실패한다 |

- `crud`로 만든 모듈은 `{E}Finder`·`{E}Facade`·`{E}Mapper`가 하나씩이라 옵션 없이 정해진다. 1단계 `module`로 만든 빈 모듈은 전부 새로 만든다.
- reference-app처럼 구현이 Mapper 대신 다른 구성 요소(`MemoSummaryProjection`)를 쓰는 경우는 실패한다. 이때는 손으로 쓴다.

### 5. 레시피별

#### 5.1 `dto`

```text
cozy add dto <m> <Record> --field 이름:종류[:설명] … 
```

- 만드는 것: `{m}/spi/dto/{Record}.java`(record), 없으면 `spi/dto/package-info.java`(`@NamedInterface("spi")`).
- 필드 종류는 1단계와 같다(`text`, `longtext`, `int`, `long`, `bool`, `date`, `instant`). 그리고 같은 모듈의 다른 spi DTO(`이름:Record`, `이름:list:Record`)를 쓸 수 있다. `!`는 받지 않는다(record에는 필수 여부가 없다).
- 이름이 `*Item`, `*Page`처럼 `crud`가 쓰는 이름과 겹치면 1단계와 같이 "이미 있고 내용이 다릅니다"로 실패한다.

#### 5.2 `query`

```text
cozy add query <m> <이름> [--param 이름:종류 …] --returns <반환> [--in <Finder>]
  반환: long | int | bool | text | <Record> | list:<Record>
```

| 곳 | 더하는 것 |
| --- | --- |
| `spi/{X}Finder` | `List<ActivityItem> recent(int limit);` (반환이 `<Record>`면 `Optional<Record>`) |
| `app/query/{X}FinderImpl` | `@Override` 메서드. Mapper를 부르고 행을 record로 옮긴다(같은 이름 구성 요소를 그대로 옮긴다) |
| `infra/{X}Mapper` | `List<ActivityItemRow> recent(@Param("limit") int limit);` |
| `infra/{Record}Row` | 없으면 만든다. 구성 요소는 record와 같다. 이미 있으면 구성 요소가 같아야 한다 |
| `mapper/{m}/{X}Mapper.xml` | `<select id="recent" resultType="…ActivityItemRow">`. 본문은 빈칸 `query-sql` |

- **빈칸은 SQL 하나다.** 행과 옮기는 코드는 같은 모양으로 만든다. 저장 형식과 보이는 형식이 다르면(예: `Instant` → `"2026-11-08 10:00"`) 에이전트가 행의 타입과 옮기는 코드를 고친다. 이것은 구조가 아니라 로직이다.
- 반환이 원시 값이면 행을 만들지 않고 Mapper가 그 값을 돌려준다.
- 출력의 "다음"에 쓸 수 있는 테이블과 열을 적는다(마이그레이션에서 읽는다). 파일 내용은 앱 상태에 기대지 않는다는 1단계 규칙을 지키기 위해 파일이 아니라 출력에 적는다.

#### 5.3 `command`

```text
cozy add command <m> <이름> [--param 이름:종류 …] [--returns long|int|bool|text] [--sql insert|update|delete] [--in <Facade>]
```

| 곳 | 더하는 것 |
| --- | --- |
| `spi/{X}Facade` | 메서드 선언. `--returns`가 없으면 `void` |
| `app/command/{X}FacadeImpl` | `@Override` 메서드. 본문은 빈칸 `command-body`. 구현 클래스는 이미 `@Transactional`이다 |
| `--sql`이면 `infra/{X}Mapper` | 같은 파라미터의 메서드(`@Param`). `insert`·`update`·`delete`는 영향받은 행 수 `int`를 돌려준다 |
| `--sql`이면 Mapper XML | `<insert id="…">` 등. 본문은 빈칸 `command-sql` |

- 쓰기는 Facade로 모은다. reference-app의 activity는 내부 `ActivityService`를 썼지만, 같은 모듈의 리스너와 컨트롤러가 Facade를 부르는 것은 이미 허용된다(C8). 그래서 `Service`를 만드는 레시피는 따로 두지 않는다. 다른 모듈은 Facade를 부르지 못한다는 C8 규칙은 그대로다.
- 이벤트 발행은 지금처럼 본문에 한 줄을 쓴다. `FacadeImpl`이 `ApplicationEventPublisher`를 갖고 있지 않으면 출력의 "다음"이 그것을 알린다. 생성자를 고치는 일은 하지 않는다(§4).

#### 5.4 `event-version`

```text
cozy add event-version <m> <Ev> --field 이름:종류 …
```

- record 끝에 구성 요소를 더한다. 종류는 박싱 타입(`Long`, `Integer`, `Boolean`, `String`, `LocalDate`, `Instant`)으로 만든다. 옛 샘플을 읽으면 `null`이 되도록 하기 위해서다.
- `of()`: 지금 것을 지우지 않고 새 파라미터가 붙은 `of()`를 더한다. 옛 `of()`는 새 필드를 `null`로 넘기도록 본문을 바꾼다. 그래서 발행하는 곳의 코드가 그대로 컴파일된다.
- `schemaVersion`: `of()` 안의 `new {Ev}(UUID.randomUUID(), N, …)`에서 `N`을 1 올린다. 이 모양이 아니면 실패한다.
- 샘플: 가장 높은 `v{n}.json`에 새 필드의 샘플 값을 더해 `v{n+1}.json`을 만든다.
- 출력의 "다음": 이 이벤트의 리스너 목록과, 새 필드가 `null`인 옛 이벤트를 처리하는지 확인하라는 안내.

#### 5.5 `enable events`

- pom에 `cozy-starter-events`를 더한다(§2). `cozy-starter-sqlite`가 없으면 "persistence가 필요합니다"로 실패한다.
- 아키타입 `events=true`가 더하는 예시 코드(`NoteAdded`, `NoteAddedListener`)는 만들지 않는다.

#### 5.6 `listener`의 Facade 주입 (1단계 고침)

- 지금(M10)은 리스너를 만들 때 자기 모듈에 Facade가 정확히 하나 있으면 주입하고, 없으면 주입하지 않는다. 그래서 나중에 `command`로 Facade가 생긴 뒤 같은 `listener` 명령을 다시 실행하면 "내용이 다릅니다"로 실패한다. "생성 파일은 앱 상태에 기대지 않는다"는 부록 §1 규칙에도 어긋난다.
- **바꾼다**: 리스너는 늘 `{M}Facade`를 주입한다. 없으면 `listener`가 빈 `{M}Facade`와 `{M}FacadeImpl`을 함께 만든다. 리스너 파일은 앱 상태와 상관없이 같다.
- 리스너 본문(빈칸)은 `command`로 Facade에 메서드를 더한 뒤 그것을 부르는 한 줄이 된다.

### 6. 완료 기준

- **reference 픽스처에서 손으로 쓴 구조가 사라진다.** samples/recipes의 `hand/activity-item`과 `hand/reference-filled`의 구조 파일(Finder, Mapper, Row, Service, XML)을 레시피로 바꾼다. `hand`에는 빈칸을 채운 결과만 남는다.

  ```text
  cozy add dto activity ActivityItem --field message:text --field at:text
  cozy add query activity recent --param limit:int --returns list:ActivityItem
  cozy add command activity record --param eventId:text --param message:text --sql insert
  ```

- 레시피마다 픽스처가 "C9만" 또는 "통과"다(1단계와 같은 기준).
- 다시 실행하면 바뀐 것이 없다. 빈칸을 채운 뒤에 다시 실행해도 그렇다(§3).
- 실패하는 명령이 아무 파일도 바꾸지 않는다: 일부만 있음, 다른 시그니처, 없는 record, Finder 둘인데 `--in` 없음, Mapper 없는 기존 구현, 알아볼 수 없는 `of()`.
- `cozy recipes`·`AGENTS.md`의 명령 목록에 쓸 수 있게 된 레시피가 들어간다(M11 테스트가 같은지 확인한다).

### 7. 마일스톤

| 마일스톤 | 내용 | 완료 기준 |
| --- | --- | --- |
| M12 | 덧붙이는 편집기(Java 메서드·import, Mapper XML, pom), 시그니처 비교, `dto`, `enable events`, `listener` 고침 | 편집기 단위 테스트(알아보는 형식만 바꾸고 나머지는 실패). `dto`·`enable events` 픽스처 통과. 리스너 다시 실행이 Facade 생성 전후로 같다 |
| M13 | `query`, `command` | §6의 activity 세 줄로 `hand/activity-item`이 사라진다. 채운 픽스처 통과 |
| M14 | `event-version` | 필드를 더한 뒤 C6(샘플 호환)이 통과하고, 옛 `of()`를 부르는 코드가 그대로 컴파일된다 |

### M12 구현 메모

- **편집기**: `cozy-cli`의 `JavaEdits`(멤버·import·시그니처 비교)와 `XmlEdits`(Mapper XML 문, pom 의존)다. M10의 `package-info.java` 편집도 같은 javac 파서를 쓴다. 단위 테스트(`EditsTest`)는 알아보는 형식(빈 타입, 중첩 타입이 있는 본문, import 없음·와일드카드, 한 줄과 여러 줄 pom 블록)과 실패하는 형식(타입 없음, 문법 오류, 덧붙인 결과의 문법 오류, 단순 이름 충돌, 다른 namespace, `</mapper>` 뒤의 내용, 주석이나 `dependencyManagement` 안의 sqlite 블록)을 확인한다.
- **`dto`**: 구성 요소 타입은 이벤트 필드와 같다(`int`, `long`, `boolean`은 원시 타입). reference-app의 `MemoSummary(long total, long pinned, String latestTitle)`과 같은 모양이다. 레시피 픽스처 `reference`, `reference-filled`에서 손으로 쓴 `ActivityItem`을 이 명령으로 바꿨다.
- **`listener`**: 리스너가 만든 빈 Facade·구현은 같은 모듈의 `crud`가 채운다(엔티티 이름이 모듈 이름과 같을 때). 내용이 리스너가 만든 빈 파일과 글자까지 같을 때만 바꾸고, 메서드를 더했으면 1단계처럼 실패한다. 그래서 "리스너를 먼저, crud를 나중에" 순서도 동작한다(픽스처 `stage-two`).
- **`cozy recipes`**: 글 출력은 쓸 수 있는 레시피를 먼저, 준비 중인 것을 뒤에 둔다. 보류한 `field`는 목록에서 뺐고, 실행하면 보류했다고 알린다.

### M13 구현 메모

- **빈 구현에 Mapper 생성자를 더한다**: §4는 "이미 있는 구현에 Mapper가 없으면 실패한다"였다. 그런데 `listener`(§5.6)와 Mapper 없는 `command`가 만든 빈 구현에도 그대로 적용하면 `listener` → `command --sql` 순서가 늘 실패한다. 그래서 **생성자도 필드도 없는 구현**에만 `{M}Mapper`를 받는 필드와 생성자를 본문 맨 앞에 더한다. 이미 있는 생성자는 여전히 고치지 않고, 생성자가 있는데 Mapper가 없으면 실패한다.
- **행은 구성 요소 이름으로 비교한다**: §5.2 표는 "이미 있으면 구성 요소가 같아야 한다", 본문은 "에이전트가 행의 타입을 고친다"였다. 둘을 함께 지키려고 이름(순서 포함)만 비교하고 타입은 비교하지 않는다.
- **import 그룹**: §2는 "마지막 import 뒤"였다. 생성된 앱 코드의 관례(java, javax, org, com, 그 밖, 그룹 사이 빈 줄)에 맞추려고 같은 그룹의 끝에 넣는다. 이미 있는 import는 정렬하지 않는다.
- **빈칸 목록**: 명령 출력은 바뀐 파일에 남은 빈칸을 모두 보인다(예: `command --sql`이 query가 남긴 `query-sql`도 보인다). 1단계와 같은 동작이다.
- **완료 기준**: 레시피 픽스처 `reference`·`reference-filled`가 §6의 세 줄로 activity를 만든다. `hand/reference-filled`에서 `ActivityFinder`, `ActivityFinderImpl`, `ActivityMapper`, `ActivityRow`, `ActivityService`, `ActivityServiceImpl`이 사라졌고, 남은 것은 빈칸을 채운 파일(SQL, Facade 본문, 리스너 본문, 마이그레이션, 화면)뿐이다. 이것을 `verify.groovy`가 확인한다. 채운 뒤 `query`·`command`를 다시 실행해도 바뀐 것이 없다.

## 정한 것

사용자가 2026-11-08에 여섯 가지 모두 제안대로 정했다.

| # | 질문 | 결정 |
| --- | --- | --- |
| 1 | Java·XML에 덧붙일 때 `cozy:append` 표시를 쓸지, 파서가 찾은 위치를 쓸지 | **파서 위치.** 표시가 없는 기존 코드(crud 결과, 손으로 쓴 모듈, 아키타입 `home`)에도 쓸 수 있고, 에이전트가 표시를 지워도 깨지지 않는다. ADR-0032 §2를 개정한다 |
| 2 | record를 `dto`로 따로 만들지, `query`가 만들지 | **따로.** `fragment` 파라미터에도 쓰이고, 레시피 하나가 하는 일이 하나로 남는다 |
| 3 | 쓰기 SQL을 `command --sql`로 만들지, 별도 레시피(`cozy add sql`)로 만들지 | **`--sql`.** 쓰기 메서드와 SQL은 늘 함께 다닌다. 명령 수를 늘리지 않는다 |
| 4 | `field`를 언제 할지 | **보류.** crud 한 벌의 열 곳 넘게(마이그레이션, 행, 도메인, DTO, 폼, SQL 문자열, 템플릿, 스펙)을 고쳐야 하고, 에이전트가 이미 고친 곳과 겹친다. 하려면 crud 템플릿에 표시를 먼저 넣어야 한다. 그러면 그 뒤에 만든 crud만 지원하게 된다. 사용자 평가에서 필요가 확인되면 따로 ADR을 쓴다 |
| 5 | 내부 `*Service` 레시피를 둘지 | **두지 않는다.** 쓰기는 Facade로 모은다(§5.3). 서비스로 나누는 것은 에이전트가 구현 안에서 한다 |
| 6 | `listener`의 Facade 주입을 바꿀지 (1단계 출력이 바뀐다) | **바꾼다**(§5.6). 1.1.0은 아직 릴리스 전이다. 1.x 동안 명령 이름과 인자 호환을 지키기로 했으므로 1.1.0 전에 바꾸는 편이 낫다 |

## 위험

- **에이전트가 고친 파일을 다시 연다.** 파서가 알아보는 범위 안에서만 고치고 고친 뒤 다시 읽어 보지만, 에이전트가 이상하게 고친 파일(같은 이름 타입 둘, 문법 오류)은 실패로 닫힌다. 그때 손으로 할 일을 메시지가 알려야 한다.
- **import 충돌.** 같은 단순 이름(`Item`)을 다른 패키지에서 이미 쓰면 실패한다. 이름을 모듈 접두어로 짓는 1단계 규칙 덕분에 드물 것으로 본다.
- **행 record의 중복.** `query`마다 `{Record}Row`가 생겨 spi DTO와 같은 모양의 record가 둘씩 생긴다. DB 모양과 계약을 나누려는 것이지만, 평가에서 혼란을 준다면 Mapper가 spi DTO를 바로 돌려주게 바꾼다(infra는 자기 모듈 spi를 쓸 수 있다, structure.md).
- **`event-version`의 `of()`가 버전마다 늘어난다.** 오래 쓰면 오버로드가 쌓인다. 정리는 손으로 하고 C6가 확인한다.
- **평가 전 설계다.** 작은 모델이 이 단위로 쓰기 쉬운지는 사용자의 내부 평가(ADR-0032 §8)로 확인한다. 레시피 이름과 인자는 1.1.0 릴리스 전에 확정한다.
