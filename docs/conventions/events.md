# 이벤트 (cozy-starter-events)

모듈 사이의 연동은 이벤트로 한다(PRD §8). 업무 데이터 커밋과 이벤트 발행 기록은 같은 SQLite 트랜잭션에서 함께 성공하거나 함께 실패한다. 받는 쪽은 인박스가 있어서 같은 이벤트를 한 번만 처리한다.

```xml
<dependency>
  <groupId>io.salvia95.cozy</groupId>
  <artifactId>cozy-starter-events</artifactId>
</dependency>
```

`events=false`로 만든 앱(`cozy-starter-sqlite`만 있는 앱)에는 `cozy enable events`가 이 의존을 더한다([cli.md](cli.md#add-link-enable)).

## 이벤트 만들기

```java
// memo/spi/event/MemoCreated.java — 받는 모듈이 쓰므로 모듈 사이 계약(Named Interface spi)에 둔다
public record MemoCreated(UUID eventId, int schemaVersion, long memoId, String title) implements CozyEvent {
    public static MemoCreated of(long memoId, String title) {
        return new MemoCreated(UUID.randomUUID(), 1, memoId, title);
    }
}
```

```java
// 보내는 쪽(memo/internal/app/command/MemoFacadeImpl): 업무 트랜잭션 안에서 발행한다
@Transactional
public long create(String title, String body, boolean pinned) {
    ...
    this.mapper.insert(row);
    this.events.publishEvent(MemoCreated.of(row.getId(), title));   // ApplicationEventPublisher
    return row.getId();
}
```

```java
// 받는 쪽(activity/internal/app/command/): package-info.java에 @ApplicationModule(allowedDependencies = "memo :: spi")
@Component
class MemoCreatedListener {
    @ApplicationModuleListener
    void on(MemoCreated event) { ... }
}
```

- **CozyEvent 구현**: 모듈 사이의 이벤트는 반드시 `CozyEvent`를 구현하고 `{모듈}/spi/event/`에 둔다(C8). `spi/event/package-info.java`에 `@NamedInterface("spi")`를 둔다. 필드는 `eventId`(멱등성 키, 발행할 때 만든다)와 `schemaVersion`(1부터)이다.
- **리스너 파라미터**: `@ApplicationModuleListener`의 파라미터는 CozyEvent 하나다. 어기면 검사 C6이 빌드를 실패시킨다.
- **리스너 실행 방식**: 리스너는 발행한 트랜잭션이 커밋된 뒤, 별도 트랜잭션에서 비동기로 실행된다.
  - 업무 트랜잭션이 롤백되면 이벤트도 발행 기록도 남지 않는다.
  - 리스너가 실패해도 업무 데이터는 그대로다.

## 전달 보장

| 상황 | 동작 |
| --- | --- |
| 업무 트랜잭션이 롤백됨 | 발행 기록이 남지 않는다 |
| 리스너가 예외를 던짐 | 리스너 트랜잭션이 롤백되어 본문의 쓰기와 인박스 행이 함께 사라진다. 발행 기록은 `FAILED`로 남는다 |
| 앱을 다시 시작함 | 미완료 발행 기록을 다시 발행한다(`spring.modulith.events.republish-outstanding-events-on-restart=true`가 기본값) |
| 프로세스가 리스너 실행 중에 강제 종료됨 | 리스너 트랜잭션은 커밋되지 않았으므로, 재시작 뒤 처음부터 다시 처리한다 |
| 같은 이벤트가 두 번 전달됨 | 인박스(`cozy_inbox`, 기본키 (이벤트 id, 리스너 id))에 이미 행이 있으므로 본문을 건너뛰고 정상 완료로 처리한다 |
| 리스너는 커밋했지만 완료 기록 전에 종료됨 | 재시작 뒤 다시 발행되지만 인박스가 본문을 건너뛴다 |

- **리스너 id**: `클래스 FQN#메서드명(파라미터 타입 FQN)`이다. 리스너 메서드의 이름이나 위치를 바꾸면 다른 리스너로 본다.
- **실패한 발행 다시 보내기**: 운영 중에 실패한 발행을 다시 보내려면 Modulith의 `IncompleteEventPublications.resubmitIncompletePublications(...)`를 쓴다.

### 외부 호출 리스너

외부 API 호출은 SQLite 트랜잭션으로 묶이지 않는다. 리스너가 외부를 호출한 뒤 실패하면 다시 실행될 때 외부 호출도 다시 나간다. 이런 리스너는 다음 중 하나를 지킨다(v1은 규칙만 제공한다).

1. **상태 테이블**: 자기 모듈 테이블에 `처리 중 → 완료`를 기록한다. 다시 실행되면 상태를 보고 이어서 하거나 건너뛴다.
2. **멱등 키**: 외부 쪽의 중복 방지 키(멱등 키)로 `eventId`를 보낸다. 같은 키로 다시 호출해도 외부가 한 번만 처리한다.

외부 호출 앞뒤의 DB 쓰기는 각각 짧은 트랜잭션으로 나눈다. 외부 응답을 기다리는 동안 SQLite 쓰기 잠금을 잡고 있지 않기 위해서다.

## 스키마 진화 규칙 (PRD §8.4)

처리되지 않은 이벤트는 업데이트 뒤 새 버전 코드가 읽는다. 그래서 다음 규칙을 강제한다.

- **필드 추가만 허용한다.** 이름 변경, 삭제, 타입 변경은 금지한다.
- **추가한 필드는 없을 때의 기본값이 있어야 한다.** 옛 기록에는 그 필드가 없다. 이벤트 전용 매퍼는 없는 primitive를 0이나 false로, 객체를 null로 채운다.
- **필드를 추가하면 schemaVersion을 올린다.** 리스너는 버전에 따라 처리할 수 있어야 한다.
- **이벤트 클래스의 FQN을 바꾸지 않는다.** 바꿔야 하면 새 이벤트를 만든다.
- **버전마다 직렬화 샘플을 남긴다.** 위치는 `src/test/resources/events/{클래스 단순 이름}/v{n}.json`이고, 지우지 않는다.

```json
// src/test/resources/events/MemoCreated/v1.json
{"eventId":"7d4b3c2a-1e0f-4a5b-9c8d-6e7f8a9b0c1d","memoId":1,"schemaVersion":1,"title":"회의록 정리"}
```

`CozyArchitectureTest`(검사 C6)가 다음을 확인한다.

1. 모든 CozyEvent 구현에 `v1.json`부터 빠진 번호 없이 샘플이 있다.
2. 모든 샘플이 현재 클래스로 역직렬화되고, 샘플의 schemaVersion이 파일 번호와 같다.
3. 샘플의 모든 필드가 현재 클래스에 같은 값으로 남아 있다. 이로써 삭제, 이름 변경, 타입 변경을 잡는다.
4. 가장 높은 버전의 샘플이 현재 클래스의 모든 필드를 가진다. 필드를 추가했는데 새 버전 샘플이 없으면 실패한다.
5. 샘플 폴더마다 같은 이름의 CozyEvent 구현이 있다. 이로써 클래스 이름 변경을 잡는다.

필드를 추가하는 순서는 다음과 같다.

1. record에 필드를 추가한다.
2. `of()`의 schemaVersion을 올린다.
3. 새 버전 샘플 `v{n+1}.json`을 추가한다.
4. 리스너가 옛 버전(새 필드가 기본값)을 처리하는지 확인한다.

## 설정

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `cozy.events.retention` | `30d` | 완료된 발행 기록과 인박스 행의 보관 기간 |
| `cozy.events.cleanup-interval` | `6h` | 보관 기간이 지난 행을 지우는 주기. 기동 1분 뒤 처음 실행한다 |
| `spring.modulith.events.republish-outstanding-events-on-restart` | `true` | 재시작할 때 미완료 이벤트를 다시 발행한다 |
| `spring.task.execution.pool.core-size`, `max-size` | `2` | 비동기 리스너 실행기의 동시 실행 수. SQLite 쓰기 경합을 줄인다 |
| `spring.modulith.events.completion-mode` | `update` | `update`만 지원한다. 다른 값이면 기동이 실패한다 |

- **보관 정리**: 완료된 기록만 지운다. 미완료 발행 기록과, 그 이벤트의 인박스 행은 보관 기간이 지나도 남긴다.

## 테이블 (`cozy_schema_history`, `db/cozy/events/V2_1__events_create.sql`)

| 테이블 | 내용 |
| --- | --- |
| `cozy_event_publication` | Modulith 2.1 공식 JDBC 스키마 v2의 컬럼. UUID와 시각은 TEXT(UTC, 소수점 9자리 고정 폭) |
| `cozy_inbox` | `event_id`, `listener_id`, `processed_at`, 기본키 `(event_id, listener_id)` |

## 주의

- **트랜잭션 설정**: 앱에서 `@EnableTransactionManagement`를 직접 선언하지 않는다. cozy-starter-events가 트랜잭션 어드바이저의 순서를 정해 인박스가 리스너 트랜잭션 안에서 돌게 한다(ADR-0023). 순서가 어긋나면 리스너가 `[cozy] 인박스는 리스너 트랜잭션 안에서 실행되어야 합니다`로 실패한다.
- **직렬화 매퍼**: 이벤트 직렬화는 전용 매퍼 `CozyEventMapper`를 쓴다. 앱의 JsonMapper 설정은 이벤트에 영향을 주지 않는다.
