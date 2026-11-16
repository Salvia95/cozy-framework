# ADR-0041 이벤트 계약 모듈 `events`: 이벤트 클래스를 한 모듈에 모아 순환 의존을 없앤다

- 상태: 승인 (2026-11-17, 사용자 결정 "권장안대로 승인, M36 진행해줘"). "정할 것" 1~5는 권장안
- 출처: 사용자 지적 "구조 상 순환참조는 빈번히 발생할 것 같아 … 시스템 차원에서 이벤트 관리를 위한 도메인을 먼저 선점하거나, event broker와 같은 모듈로 별도로 관리하고 이벤트 리스닝이 필요한 모듈에서는 이를 참조하는 방식이 낫지 않을까? 현재는 소규모 앱에서는 무조건 적으로 순환참조가 발생해", 지시 "이벤트 계약 모듈을 별도로 두는 방향으로 마일스톤을 써줘"
- 바꾸는 결정:
  - ADR-0029와 structure.md: 이벤트의 자리를 `{모듈}/spi/event/`에서 `events/spi/{주인 모듈}/`로 옮긴다.
  - events.md "이벤트 클래스의 FQN을 바꾸지 않는다": 이번 한 번은 `cozy migrate events`가 저장된 FQN까지 함께 옮긴다(§6). 규칙 자체는 그대로다.
  - ADR-0038 모듈 다이어그램: `events` 모듈은 상자로 그리지 않는다. 점선(발행 → 수신)은 지금처럼 주인 모듈과 받는 모듈 사이에 그린다.
- 바꾸지 않는 것: ADR-0005(Modulith 검사는 늘 켠다, C1), ADR-0006·0023의 전달 방식(발행 기록, 인박스, 재발행), `CozyEvent`, 스키마 진화 규칙과 샘플(C6), "다른 모듈의 쓰기는 이벤트로만"(ADR-0029), 읽기는 `*Finder`로

## 배경

모듈 사이의 의존은 모두 상대의 `spi`를 향한다.

- 읽기: A가 B의 `Finder`를 쓰면 A → B.
- 이벤트: B가 A의 이벤트를 받으면 리스너가 이벤트 클래스(`a/spi/event/…`)를 import하므로 B → A.

그래서 "대여가 도서를 읽고, 도서는 대여 이벤트를 받아 재고를 바꾼다" 같은 흔한 흐름이 바로 순환이 된다. C1(Modulith `verify()`)과 `cozy link`가 순환을 막으므로 앱을 만들 수 없다. 모듈끼리 서로 반응하는 일이 많은 소규모 앱일수록 피하기 어렵다.

순환을 피하려고 제3의 모듈로 흐름을 꺾으면 빌드는 되지만, 코드의 구조가 실제 업무 흐름(누가 내고 누가 받는가)을 보여 주지 못한다.

## 결정

### 1. 이벤트 계약 모듈 `events`

```text
{메인 패키지}/
├── events/                       이벤트 계약. 로직 없음. 다른 앱 모듈에 의존하지 않는다
│   ├── package-info.java         @ApplicationModule(allowedDependencies = {})
│   └── spi/
│       ├── memo/                 주인이 memo인 이벤트 (package-info: @NamedInterface("spi"))
│       │   └── MemoCreated.java
│       └── rental/
│           └── RentalCreated.java
├── memo/      package-info: allowedDependencies = {"events :: spi"}              발행
└── activity/  package-info: allowedDependencies = {"events :: spi", "memo :: spi"}  수신 (memo를 읽을 때만 memo :: spi)
```

- 이벤트를 내는 모듈도 받는 모듈도 `events :: spi`에만 의존한다. 이벤트 때문에 앱 모듈끼리 의존하는 일이 없어지고, 모듈 사이의 의존은 읽기(`*Finder`, `spi/dto`, `spi` fragment)만 남는다.
- `events`는 Modulith 모듈이다. 다른 앱 모듈에 의존하지 않으므로 순환의 한쪽이 될 수 없다.
- `events` 안에는 `spi/{주인 모듈}/`의 CozyEvent record와 `package-info.java`만 둔다. 다른 클래스, 템플릿, 마이그레이션, 화면은 두지 않는다(C8).
- 이벤트의 필드 타입은 지금처럼 기본 타입(`String`, `long`, `boolean`, `LocalDate`, `Instant`, `UUID` 등)만 쓴다. 주인 모듈의 `spi/dto` record를 필드로 쓰면 `events`가 그 모듈에 의존하게 되므로 금지한다(C8).
- 이름 `events`는 예약한다. `cozy add module events`와 아키타입의 모듈 이름으로 쓸 수 없다.

### 2. 주인은 그대로 남긴다: 주인 모듈만 발행한다 (C11)

- 이벤트의 주인은 하위 패키지 이름이다(`events/spi/memo/MemoCreated` → memo).
- **C11**: 이벤트를 만드는 코드(`MemoCreated.of(…)`, `new MemoCreated(…)`)는 주인 모듈의 `internal/app/command/`에만 있어야 한다. 다른 모듈이 남의 이벤트를 내면 실패한다. `CozyArchitectureTest`가 ArchUnit으로 검사하고, 테스트 코드는 제외한다.
- 받는 쪽은 제한하지 않는다. 주인 모듈이 자기 이벤트를 받는 것도 된다(아키타입 `home`의 `NoteAdded`).
- 이벤트의 단순 이름은 앱 전체에서 하나여야 한다. 샘플 폴더가 `src/test/resources/events/{단순 이름}/`이기 때문이다(C6이 확인한다). 지금도 같은 전제이고, 한 패키지에 모이면서 드러날 뿐이다.

### 3. 흐름은 의존과 따로 보인다

- `cozy describe`: 모듈 항목의 이벤트를 앱 수준의 `events[]`로 옮긴다. 항목은 `type`, `owner`, `fields[]`, `publishedIn[]`, `listeners[]`(`모듈`, `리스너 클래스`)다. 글 출력은 `이벤트 memo.MemoCreated(long memoId, String title) 발행: … → activity(MemoCreatedListener), rental(…)`이다.
- 워크벤치 모듈 다이어그램(ADR-0038): `events` 상자와 그쪽 실선은 그리지 않는다. 모든 모듈이 `events`에 의존하므로 그리면 한 점으로 모이는 선만 늘어난다. 점선(발행 → 수신)은 주인 모듈에서 받는 모듈로 그린다. 의존(실선)과 흐름(점선)이 다른 그림이 된다.
- 서로의 이벤트를 받는 두 모듈은 점선이 양쪽으로 그려지고, 순환 경고는 나오지 않는다.

### 4. 명령

| 명령 | 바뀌는 것 |
| --- | --- |
| `cozy add event <모듈> <이벤트> --field …` | `events/spi/{모듈}/{이벤트}.java`에 만든다. `events` 모듈이 없으면 `package-info.java`와 함께 만든다. 주인 모듈에 `events :: spi`를 선언한다 |
| `cozy add crud … --publish created` | 같은 자리에 만든다 |
| `cozy add listener <모듈> --on <주인>.<이벤트>` | 형식은 그대로다. 받는 모듈에 `<주인> :: spi` 대신 `events :: spi`를 선언한다 |
| `cozy add event-version <모듈> <이벤트> --field …` | 새 자리에서 찾는다 |
| `cozy add module events` | 예약한 이름이라 실패한다 |
| `cozy link a --uses events` | 된다. 보통은 위 명령이 알아서 선언한다 |
| `cozy migrate events` | §6 |

- 명령이 쓰는 자리와 메시지는 cli.md, 플레이북(add-event, add-listener, add-event-version, enable-events), AGENTS.md의 Architecture 절에 반영한다.

### 5. 순환이 이벤트로 생기지 않는다는 것을 픽스처로 보인다

- 레시피 픽스처 `events-cycle`: `book`과 `rental`을 `cozy add crud --publish`로 만들고, 서로의 이벤트를 `cozy add listener`로 받고, `rental`이 `book`을 `Finder`로 읽는다. `mvn verify`(C1 포함)가 통과해야 한다.
- 같은 흐름을 옛 자리(`{모듈}/spi/event/`)로 만든 위반 픽스처는 C1의 순환으로 실패한다. 이 ADR이 고치는 문제를 그대로 남겨 두는 기록이다.

### 6. 이미 있는 앱: `cozy migrate events` (2.0.0)

1. `{모듈}/spi/event/*.java`를 `events/spi/{모듈}/`로 옮기고 `package` 선언과 앱 전체(`src/main`, `src/test`)의 import를 고친다. `spi/event/package-info.java`는 지우고 새 하위 패키지에 만든다.
2. `events/package-info.java`를 만들고, 이벤트를 내거나 받는 모듈에 `events :: spi`를 선언한다.
3. 받는 모듈의 `"{주인} :: spi"`는 그 모듈이 주인의 다른 `spi`(Finder, dto, fragment)를 쓰지 않을 때만 지운다. 판단할 수 없으면 남기고 "남은 일"로 알린다.
4. **저장된 FQN을 옮긴다**: 앱의 `db/migration`에 `V{시각}__cozy_move_event_contracts.sql`을 만든다. `cozy_event_publication.event_type`과 `listener_id`, `cozy_inbox.listener_id`에서 옛 FQN을 새 FQN으로 바꾼다. 업데이트한 PC에 남아 있던 미완료 발행 기록이 새 클래스로 읽히고, 이미 처리한 이벤트는 인박스가 계속 건너뛴다.
5. 다른 명령처럼 계획을 보이고(`--dry-run`) 원자적으로 적용한다. 두 번 실행하면 바뀐 것 없음이다.

- 옛 자리는 M40부터 C8 오류다. 메시지 끝에 `고치는 명령: cozy migrate events`를 붙인다. M36~M39 동안은 두 자리를 모두 받는다(빌드를 깨지 않고 단계별로 옮기기 위해서).
- ADR-0040과 함께 2.0.0의 호환이 깨지는 변경이다. release.md의 "앱을 2.0.0으로 올리기"에 `cozy migrate events`를 더한다.

## 정할 것

권장안으로 썼다. 다르게 정하면 해당 절과 단계를 고친다.

1. **모듈 이름**: `events`(권장). 다른 안은 `contract`다. `events`가 하는 일을 그대로 말하고, 리스너 코드에서 `import …events.spi.memo.MemoCreated`로 읽힌다.
2. **담는 것**: 이벤트만(권장). 공유 DTO까지 담으면 "모든 것이 모이는 모듈"이 되어 모듈 경계가 흐려진다. 읽기 계약은 지금처럼 주인 모듈의 `spi`에 둔다.
3. **발행 제한 C11**: 주인 모듈만 발행(권장). 제한하지 않으면 계약 모듈이 아무나 쓰는 전역 버스가 된다.
4. **옛 자리**: M40부터 오류이고 `cozy migrate events`로 옮긴다(권장). 다른 안은 두 자리를 계속 받는 것이다. 그러면 검사, 명령, 워크벤치가 두 모양을 모두 알아야 하고, 옛 자리로 만든 앱은 계속 순환에 걸린다.
5. **자기 모듈 안에서만 쓰는 이벤트**: 이것도 `events`에 둔다(권장). 자리 규칙이 하나라서 명령과 검사가 단순하다. 나중에 다른 모듈이 받게 되어도 옮길 것이 없다.

## 단계

| 마일스톤 | 내용 | 완료 기준 |
| --- | --- | --- |
| M36 규칙과 검사 | cozy-rules의 `Layer`와 C8에 `events` 모듈(자리, 필드 타입, 예약 이름), C11(주인만 발행), C6의 단순 이름 중복 확인. 옛 자리도 받는다. structure.md, events.md, testing.md | 손으로 만든 픽스처 앱에서 두 모듈이 `events`를 거쳐 서로의 이벤트를 받고 한쪽이 다른 쪽을 읽어도 `mvn verify`가 통과한다. 같은 흐름의 옛 자리 픽스처는 C1 순환으로 실패한다. C8(`events` 안의 다른 클래스, dto 필드)과 C11(남의 이벤트 발행) 위반 픽스처가 그 이유로 실패한다 |
| M37 명령 | `add event`, `add crud --publish`, `add listener`, `add event-version`, 예약 이름, `describe`의 `events[]`. 레시피 픽스처(stage-two 등) 갱신과 `events-cycle`. 플레이북과 AGENTS.md | 레시피 픽스처 전체와 `events-cycle`이 `mvn verify`를 통과한다. 명령이 만든 리스너 모듈의 `allowedDependencies`에 주인 모듈이 없다. reference-app의 describe 기대 파일이 새 형식이다 |
| M38 reference-app과 아키타입 | reference-app(memo → activity), 아키타입 `home`의 `NoteAdded`를 `events`로. 생성 앱의 에이전트 문서 | 아키타입 3조합과 reference-app이 `mvn verify`를 통과한다. `-Pe2e`의 배포·업데이트 종단 검증이 통과한다 |
| M39 워크벤치 | 모듈 다이어그램에서 `events` 상자와 실선을 빼고 점선(발행 → 수신)을 주인과 받는 모듈 사이에. 코드 생성 화면의 이벤트 선택(주인.이벤트) | runtime-verifier: reference-app과 `events-cycle` 앱의 다이어그램에 순환 경고가 없고, 점선이 양방향으로 보인다. 1280·390 스크린샷 |
| M40 옮기기와 2.0.0 | `cozy migrate events`(§6), 저장된 FQN을 옮기는 앱 마이그레이션, 옛 자리를 C8 오류로, release.md, cli.md | 레시피 픽스처 `migrate-events`: 옛 자리로 만든 앱을 옮긴 뒤 `mvn verify` 통과, 두 번째 실행은 바뀐 것 없음. cozy-starter-events 통합 테스트: 옛 FQN의 미완료 발행 기록이 있는 DB에 마이그레이션을 적용하고 재시작하면 리스너가 한 번 처리하고, 이미 처리한 이벤트는 다시 처리하지 않는다 |

- 화면이 바뀌는 M39는 runtime-verifier로 확인한다(ADR-0030).
- 마일스톤을 시작할 때 Spring Modulith의 최신 GA를 확인한다(ADR-0017). Named Interface와 `verify()`의 동작이 이 결정의 전제다.

## 위험

- **`events`에 모든 이벤트가 모인다.** 이벤트를 하나 바꾸면 그 모듈을 쓰는 모든 모듈이 다시 컴파일된다. 소규모 앱에서는 문제가 되지 않을 것으로 보지만, 모듈이 수십 개가 되면 다시 본다.
- **의존이 사라져도 의미의 결합은 남는다.** 받는 모듈은 여전히 주인 모듈이 그 이벤트를 언제 내는지에 기대어 동작한다. 그래서 의존 그래프만으로는 보이지 않는 흐름을 describe와 다이어그램의 점선으로 보인다(§3).
- **저장된 FQN 옮기기를 빠뜨린 PC.** 2.0.0으로 업데이트한 앱이 마이그레이션 없이 배포되면 미완료 발행 기록을 역직렬화하지 못한다. `cozy migrate events`가 마이그레이션 파일을 늘 함께 만들고, `migrate-events` 픽스처와 통합 테스트로 확인한다. 직접 옮긴 앱은 events.md에 적은 SQL을 따른다.
- **읽기 의존만으로도 순환은 생길 수 있다.** A가 B를 읽고 B가 A를 읽으면 여전히 순환이다. 이것은 업무 경계가 잘못 나뉜 신호이므로 이 결정의 범위 밖이다. `cozy link`의 실패 메시지가 "읽기 순환은 모듈을 나누는 방법을 다시 본다"를 안내하도록 M37에서 문구만 고친다.
- **Modulith가 같은 이름(`spi`)의 Named Interface를 여러 하위 패키지에 두는 것을 계속 허용해야 한다.** 지금도 `spi`, `spi/dto`, `spi/event`가 같은 이름을 쓴다. Modulith를 올릴 때 계약 테스트가 확인한다.

## M36 (2026-11-17, 사용자 지시 "권장안대로 승인, M36 진행해줘")

- **C8은 `events` 모듈을 따로 본다** (cozy-rules `LayerChecker`, `cozy check`와 `mvn verify`가 같은 판단을 쓴다). 모듈 패키지의 마지막 이름이 `events`면 이벤트 계약 모듈이다. `events.spi.{주인}` 아래의 CozyEvent record만 두고, 주인 폴더는 있는 모듈의 이름이어야 하며, 이벤트 필드는 다른 앱 모듈의 타입을 쓰지 않는다.
  - 자리가 틀린 이벤트(`events.spi` 바로 아래, 더 깊은 폴더, 없는 주인)에는 `@NamedInterface` 빠짐을 따로 알리지 않는다. 고칠 것은 자리이고, 두 줄이 나오면 무엇을 먼저 할지 흐려진다.
  - 기능 모듈에서 자리가 틀린 이벤트는 이제 `events.spi.{그 모듈}`로 안내한다. 옛 자리(`spi.event`)는 M40까지 통과한다.
- **C11은 cozy-test에서만 한다** (`CozyArchitectureTest.eventsArePublishedByTheirOwner`). "이벤트를 만든다"를 생성자와 static 메서드 호출로 가려야 해서 바이트코드(ArchUnit)가 필요하다. 소스만 읽는 `cozy check`는 하지 않는다. 이벤트 자신이 부르는 생성자(`of` 안)는 뺀다.
- **C6에 이름 중복**: 샘플 폴더가 단순 이름이라 두 이벤트의 단순 이름이 같으면 실패한다.
- **아키텍처 테스트가 7개가 되어** 테스트 수를 확인하던 위반·레시피 픽스처의 숫자를 고쳤다.
- **완료 기준의 픽스처**:
  - 레시피 픽스처 `events-module`(통과): 손으로 만든 `book`·`rental`이 `events`를 거쳐 서로의 이벤트를 받고 `rental`이 `book`을 읽는다. `cozy check`와 `mvn verify`가 통과하고, 흐름 테스트가 두 방향의 리스너가 실제로 도는 것을 확인한다. 명령이 새 자리를 쓰는 것은 M37이라 지금은 손으로 만든다.
  - 위반 픽스처 `C1-event-cycle`: 같은 흐름을 옛 자리로 만들면 Modulith가 `Cycle detected: Slice book -> …`로 실패한다.
  - 위반 픽스처 `C8-events-module`(이벤트가 아닌 클래스, 주인 폴더가 없는 이벤트), `C11-foreign-publish`(rental이 book의 이벤트를 만든다). 이벤트 필드에 다른 모듈의 타입을 쓰는 경우는 Modulith의 순환(C1)도 함께 나므로 픽스처 대신 `LayerRulesTest`로 확인한다.


## M37 (2026-11-17, 사용자 지시 "다음 작업 진행해")

- **명령이 새 자리에 만든다**: `add event`와 `add crud --publish`는 `events/spi/{주인}/`에 이벤트를, 없으면 `events`의 `package-info.java`(`allowedDependencies = {}`)와 주인 폴더의 `@NamedInterface("spi")`를 만들고, 주인 모듈에 `"events :: spi"`를 더한다. `add listener`는 받는 모듈에 `"events :: spi"`만 더한다. `add listener --on`과 `event-version`은 새 자리, 옛 자리 순서로 찾는다. 옛 자리의 이벤트를 받는 리스너는 예전처럼 주인 모듈에 의존한다(M40 전까지 기존 앱이 그대로 돈다).
  - 의존을 선언하지 않은 open 모듈(`@ApplicationModule` 없음)에는 더하지 않는다. open은 모든 모듈을 쓸 수 있다는 뜻이라, 하나를 적으면 오히려 좁아진다.
- **예약 이름**: `add module events`, `add crud events …`, `add event events …`가 실패한다. `link events --uses …`도 실패한다(events 모듈은 다른 앱 모듈에 의존하지 않는다). `link a --uses events`는 된다.
- **`link`의 메시지**: 순환이면 이벤트를 이벤트 계약 모듈로 옮기라고, 읽기끼리의 순환이면 경계를 다시 나누라고 알린다. 공개된 것이 없는 모듈에는 `add event`를 권하지 않는다(이벤트는 그 모듈의 spi가 아니다). 이벤트만 받으면 link가 필요 없다고 알린다.
- **describe**: 이벤트를 모듈 항목에서 앱의 `events[]`(`type`, `owner`, `fields[]`, `publishedIn[]`, `listeners[]`의 `module`·`type`)로 옮겼다. 글 출력은 모듈 다음에 `이벤트 memo.MemoCreated(…) 발행: … → activity(MemoCreatedListener)`다. 주인 폴더에 맞는 모듈이 없는 이벤트는 싣지 않는다(C8이 알린다).
  - 워크벤치는 화면을 M39에서 바꾸므로, 지금은 describe를 읽을 때 앱의 `events[]`를 주인 모듈의 `events`에 다시 싣는다. 화면은 그대로다.
- **레시피 픽스처**:
  - `events-cycle`(새로 만듦): `book`·`rental`을 `crud --publish created`로, 서로의 이벤트를 `add listener`로, `rental`이 `book`을 `link`로 읽는다. `book`의 의존은 `events :: spi`뿐이고 `mvn verify`(C1)가 통과한다. 읽기끼리의 순환(`link book --uses rental`)은 여전히 거절한다. 리스너 본문은 기록만 남긴다. 생성된 CRUD 테스트가 도는 동안 리스너가 다른 모듈의 행을 바꾸면 테스트가 흔들리기 때문이다.
  - `reference`: `cozy! link memo --uses activity`가 순환으로 실패하던 것이 이제 성공한다(activity가 memo에 의존하지 않는다). 이 ADR이 노린 결과라서, 실패해야 하는 명령을 `link events --uses memo`로 바꿨다. 순환 거절은 `events-cycle`과 `RecipeCommandTest`가 확인한다.
  - `crud-kinds`: 이벤트를 내는 `report`의 의존이 `{ "task :: spi", "events :: spi" }`가 된다. `field`, `reference-filled`, `stage-two`의 손 파일은 새 자리를 import한다.
- **문서**: cli.md(이벤트의 자리, describe `events[]`), 플레이북(add-event, add-listener, add-event-version, enable-events, link, describe), AGENTS.md의 Architecture.
- **남은 것**: reference-app과 아키타입 `home`은 아직 옛 자리다(M38). 그래서 reference-app의 describe 기대 파일은 새 형식(앱의 `events[]`)이지만 이벤트의 자리는 옛 자리다.

## M38 (2026-11-17, 사용자 지시 "다음 진행해줘")

- **reference-app**: `MemoCreated`를 `events/spi/memo/`로 옮기고 `events/package-info.java`를 더했다. `memo`와 `activity`의 의존은 둘 다 `"events :: spi"`뿐이다. `activity`는 memo의 이벤트만 받고 다른 spi는 쓰지 않으므로 `"memo :: spi"`를 지웠다. `dashboard`는 memo와 activity를 읽으므로 그대로다. describe 기대 파일을 다시 만들었다(`events` 모듈이 보이고, 이벤트의 자리가 새 자리).
- **아키타입 `home`**: `events=true`면 `NoteAdded`가 `events/spi/home/`에, `home`의 의존이 `"events :: spi"`다. `events=false`면 생성 뒤 스크립트가 `events` 패키지를 통째로 지우고 `home`의 의존은 `{}`다. 세 조합의 verify가 이것을 확인한다.
- **저장된 FQN**: 이벤트 클래스의 FQN이 바뀌므로, 옛 FQN으로 남은 미완료 발행 기록은 새 클래스로 읽히지 않는다. 그 SQL은 M40의 `cozy migrate events`가 만든다. reference-app은 사용자 PC에 배포한 앱이 아니고, `-Pe2e`의 업데이트 검증은 모든 버전을 같은 소스로 만들기 때문에 이 차이를 보지 않는다. M40에서 옛 FQN의 미완료 기록을 남긴 업데이트를 종단 검증에 더한다.
- **생성 앱의 에이전트 문서**: M37에서 고친 `docs/agents`가 그대로 들어간다.

## M39 (2026-11-17, 사용자 지시 "이어서 진행해줘")

- **다이어그램**: `ModuleGraph`가 이벤트 계약 모듈(`events`)을 빼고 배치한다. 그쪽으로 가는 실선도 없다. 점선(발행 → 수신)은 그대로 주인 모듈에서 받는 모듈로 그린다. 서로의 이벤트를 받는 두 모듈은 점선이 양쪽으로 그려지고 순환 알림이 없다. 앱에 이 모듈이 있으면 범례에 "이벤트 계약 모듈(events)은 그리지 않습니다"를 더한다.
- **같은 마디를 쓰는 화면**: 표 보기, 개요의 모듈 목록과 숫자 카드, 검색의 모듈도 `events`를 보이지 않는다. 지우기만 하면 의존이 없는 것처럼 보이므로, 의존하는 모듈의 "의존 모듈"(지도 속성, 개요 표)에 `이벤트 계약 모듈`을 적는다. `?module=events`는 404다(이유를 메시지로).
- **코드 생성**: 모듈 선택에서 `events`를 뺐다(명령도 거절한다). 이벤트 선택은 M37의 describe 덕분에 `주인.이벤트`다. 워크벤치가 describe의 앱 `events[]`를 주인 모듈에 다시 싣는 방식(M37)은 화면이 모듈마다 이벤트를 보이므로 그대로 쓴다.
- **용어**: 워크벤치 용어표에 "이벤트 계약 모듈"을 더했다(`docs/conventions/workbench.md`).
- **확인**: runtime-verifier로 reference-app과 `events-cycle` 레시피 앱(워크벤치 의존을 더한 사본)의 지도, 표, 개요, 모듈 속성(memo, book)을 1280·390에서 확인했다. 두 앱 모두 `events` 마디와 순환 알림이 없고, events-cycle은 점선 두 개(BookCreated, RentalCreated)가 겹치지 않으며, 390에서 페이지 가로 스크롤이 없고, `?module=events`는 404였다. rental의 속성은 1280에서 DOM으로만 확인했다.

## M40 (2026-11-17, 사용자 지시 "다음 진행해줘")

- **옛 자리는 C8 오류다** (cozy-rules `LayerChecker`): 기능 모듈의 `spi.event` 안의 타입은 이벤트든 아니든 `…는 옛 이벤트 자리입니다. 이벤트는 이벤트 계약 모듈의 …에 둡니다` 한 줄로 실패하고 `고치는 명령: cozy migrate events`가 붙는다. 그 패키지의 `@NamedInterface` 빠짐은 따로 알리지 않는다. 자리 표 `Role`에서 `EVENT`를 뺐다(기능 모듈에 이벤트의 자리가 없다). `Layer.SPI_EVENT`는 옛 자리를 가리키려고 남겼다.
- **명령도 옛 자리를 받지 않는다**: `add listener --on`과 `add event-version`이 옛 자리의 이벤트를 만나면 `먼저 cozy migrate events로 … 옮기세요`로 실패한다. 옛 자리의 이벤트를 받는 리스너가 주인 모듈에 `link`하던 길(M37)은 지웠다. describe와 워크벤치의 변경 목록은 읽기만 하므로 옛 자리도 그대로 알아본다.
- **`cozy migrate events`** (`MigrateEvents`): §6대로 옮긴다. 정한 것:
  - 이름 고치기는 옛 패키지 뒤에 타입 이름(대문자)이나 `*`가 오는 곳만 바꾼다. 옛 자리에는 이벤트만 있어야 하므로(아니면 실패로 닫는다) 패키지 단위로 바꿔도 다른 타입이 딸려 가지 않는다.
  - 주인 의존을 지우는 판단(§6의 3)은 글자로 한다. 받는 모듈의 Java, `templates/{모듈}/`, `mapper/{모듈}/`에 `{주인}.spi`(템플릿은 `{주인}/spi/`)가 남아 있으면 둔다. `"{주인} :: spi"`가 아닌 형식(`"book"` 등)은 지우지 않고 남은 일로 알린다.
  - SQL은 이벤트마다 세 줄이다. 리스너 id는 Modulith(`클래스.메서드(이벤트 FQN)`)와 인박스(`클래스#메서드(이벤트 FQN)`) 모두 `(이벤트 FQN)`으로 끝나므로 괄호까지 넣어 바꾼다. 마이그레이션 이름의 모듈 자리는 `cozy`다(앱 마이그레이션 이름 규칙 `V{시각}__{모듈}_{설명}.sql`은 형식만 본다).
  - 다음 할 일은 `mvn clean verify`다. M38에서 본 것처럼 옮기기 전의 `.class`가 `target/`에 남으면 C6의 이름 중복으로 실패한다.
- **완료 기준**:
  - 레시피 픽스처 `migrate-events`: `hand/migrate-events`가 옛 자리로 book·rental을 만든다(서로의 이벤트를 받아 순환하는, 1.x에서는 빌드할 수 없던 모양). `cozy check`가 C8로 실패하고, `cozy migrate events` 뒤 두 번째 실행은 바뀐 것이 없고, `cozy check`와 `mvn verify`(C1, 흐름 테스트, 옮긴 import를 쓰는 테스트)가 통과한다. book의 `"rental :: spi"`는 지워지고 rental의 `"book :: spi"`는 Finder 때문에 남는다.
  - cozy-starter-events `EventContractTest`: 옛 클래스 이름으로 남은 미완료 발행 기록 둘(리스너가 실패한 것, 리스너가 처리해 인박스에 남았지만 완료 표시 전에 멈춘 것)에 이름을 옮기는 마이그레이션을 적용하고 기동하면, 리스너 본문은 실패했던 것에만 한 번 돈다. 마이그레이션 없이 기동하면 두 기록 모두 처리하지 않는다. 테스트가 적용하는 SQL이 명령이 만드는 SQL과 같은 모양인지는 `MigrateEventsTest`가 확인한다.
  - 위반 픽스처: `C1-event-cycle`은 C1과 함께 C8(옛 자리, 고치는 명령)로도 실패한다(실패 2건). `C6-*` 셋은 이벤트를 새 자리로 옮겼고, CozyEvent가 아닌 리스너 파라미터 `OrderPlainEvent`는 `order/spi/dto`로 옮겼다.
- **종단 검증은 늘리지 않았다**: M38에서 `-Pe2e`에 옛 이름의 미완료 기록을 남긴 업데이트를 더한다고 적었다. 하지만 패키징한 앱에서 미완료 기록을 남기려면 실행 중인 DB에 행을 직접 넣어야 해서, 위 통합 테스트와 같은 것을 더 느리게 확인하게 된다. 그래서 통합 테스트(실제 Flyway, 재발행, 인박스)로 갈음했다. 필요하면 따로 더한다.
- **문서**: cli.md(migrate events 절과 SQL), structure.md·events.md(옛 자리는 오류, FQN 규칙의 예외), release.md(앱을 2.0.0으로 올리기), 플레이북(add-listener, add-event-version, check), AGENTS.md(이벤트 클래스를 옮기지 않는다).
