# cozy add listener

Creates a listener that handles an event in the receiving module.

## When to use

- Use it when this module must act on an event from another module or from itself.

## Syntax

```text
cozy add listener <모듈> --on <보내는 모듈>.<이벤트> [--name 이름]
```

- The command also declares the dependency on the sending module when it differs.
- `--name` sets the class name to `{name}Listener`; use it when a second listener handles the same event.

## Creates

- `internal/app/command/{Event}Listener.java`, which receives this module's `{Module}Facade` (for example `ActivityFacade`) in its constructor.
- An empty `{Module}Facade` and `{Module}FacadeImpl` when the module has no such Facade yet.

## Slots

- `listener-body`: call one method of this module's Facade; add that method first with [add-command](../add-command/PLAYBOOK.md) when it does not exist.
- The listener runs asynchronously in its own transaction after the publisher commits, and each event is processed once.
- Never rename the method `on` or move it, because its name and location are the listener id.
- Never call another module's Facade from a listener.

## Example

```bash
cozy add listener activity --on memo.MemoCreated
```

```java
this.facade.record(event.eventId().toString(), "메모를 만들었습니다: " + event.title());
```

## Failures

| Message contains | Action |
| --- | --- |
| `에 이벤트`, `가 없습니다` | Create the event first with [add-event](../add-event/PLAYBOOK.md). |
| `--on은 <보내는 모듈>.<이벤트> 형식입니다` | Write the source as `memo.MemoCreated`. |
| `Listener.java가 이미 있고 내용이 다릅니다` | Pass `--name` with a different name. |

## Next

- Fill `listener-body` and run `cozy check`.
- Make the body handle old events whose newer fields are `null`.
