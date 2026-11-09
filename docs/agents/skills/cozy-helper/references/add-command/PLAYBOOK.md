# cozy add command

Adds a write method to a Facade and its implementation, and with `--sql` also a mapper method and write statement.

## When to use

- Use it for a save, change, or delete that this module's controllers or listeners call.

## When NOT to use

- Do not use it to let another module write here; that module publishes an event and this module handles it with [add-listener](../add-listener/PLAYBOOK.md).

## Syntax

```text
cozy add command <모듈> <이름> [--param 이름:종류 …] [--returns long|int|bool|text] [--sql insert|update|delete] [--in <Facade>]
```

- Without `--returns` the method returns `void`.
- `--sql` adds a mapper method that returns the affected row count and an `<insert>`, `<update>`, or `<delete>` statement.
- Without a Facade the command creates `{Module}Facade`; with two or more, pass `--in <Facade>`.

## Creates

| File | Addition for `record --param eventId:text --param message:text --sql insert` |
| --- | --- |
| `spi/…Facade` | `void record(String eventId, String message);` |
| `internal/app/command/…FacadeImpl` | An `@Override` method with a slot body, in an already transactional class |
| `internal/infra/…Mapper` | `int record(@Param("eventId") String eventId, @Param("message") String message);` |
| `mapper/{module}/…Mapper.xml` | `<insert id="record">` |

- An implementation with no constructor and no fields gets a constructor that takes the module's mapper.

## Slots

- `command-body` (Java): write the rule, and with `--sql` call `this.mapper.record(eventId, message)`.
- `command-sql` (XML, only with `--sql`): write one statement and bind parameters as `#{eventId}`.
- To publish an event from the body, the implementation's constructor must take `ApplicationEventPublisher`; add it by hand when the output says it is missing.

## Example

```bash
cozy add command activity record --param eventId:text --param message:text --sql insert
```

```xml
INSERT INTO activity (event_id, message, created_at)
VALUES (#{eventId}, #{message}, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
```

## Failures

| Message contains | Action |
| --- | --- |
| `Facade가 둘 이상입니다` | Pass `--in` with one of the listed Facades. |
| `같은 이름이 다른 시그니처로 있습니다` | Use another method name; overloads are never generated. |
| `일부만 있습니다` | Complete the missing places or delete the existing ones, then rerun. |
| `Mapper가 없습니다` | Make the implementation's constructor take this module's `*Mapper`, then rerun. |

- Rerunning the same command prints `바뀐 것 없음` even after the body and SQL are filled.

## Next

- Fill the slots, then call the method from a controller or from [add-listener](../add-listener/PLAYBOOK.md).
