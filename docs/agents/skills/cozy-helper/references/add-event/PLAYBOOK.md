# cozy add event

Creates a cross-module event record (`CozyEvent`) and its first serialization sample.

## When to use

- Use it when another module must react to something that happened in this module.

## When NOT to use

- Use `--publish` of [add-crud](../add-crud/PLAYBOOK.md) instead for create, update, and delete events of a CRUD entity; it also publishes them.
- Use [add-event-version](../add-event-version/PLAYBOOK.md) instead to add a field to an existing event.

## Syntax

```text
cozy add event <모듈> <이벤트> --field 이름:종류 …
```

- The event name is PascalCase in past tense (`MemoCreated`, `ReportSent`).
- A field is `name:kind` with kind `text`, `longtext`, `int`, `long`, `bool`, `date`, or `instant`; the command adds `eventId` and `schemaVersion`.
- The app needs the events starter ([enable-events](../enable-events/PLAYBOOK.md)).

## Creates

- `events/spi/{module}/{Event}.java` in the event contracts module, a record with a static `of(…)` factory, plus the `package-info.java` files of `events` and of its owner folder when they are missing.
- `"events :: spi"` in `allowedDependencies` of the owner module.
- `src/test/resources/events/{Event}/v1.json`, the version 1 sample.

## Slots

- The command leaves no slot, but you add the publish line in the sending module's `*FacadeImpl` write method, inside its transaction: `this.events.publishEvent(ReportSent.of(reportId, sentOn));`.
- Create the event only in its owner module's `internal/app/command`; when another module has news of its own, give that module its own event.

## Example

```bash
cozy add event report ReportSent --field reportId:long --field sentOn:date
```

## Failures

| Message contains | Action |
| --- | --- |
| `cozy-starter-events가 필요합니다` | Run [enable-events](../enable-events/PLAYBOOK.md) first. |
| `이벤트의 주인은 그 이벤트를 발행하는 기능 모듈입니다` | Pass the publishing module, not `events`. |
| `이미 있고 내용이 다릅니다` | Use a new name, or add fields with [add-event-version](../add-event-version/PLAYBOOK.md). |

## Next

- Handle the event with [add-listener](../add-listener/PLAYBOOK.md) in the receiving module.
