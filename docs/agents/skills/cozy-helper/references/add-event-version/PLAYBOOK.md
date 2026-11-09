# cozy add event-version

Adds fields to an existing event, bumps its schema version, and writes the next sample without breaking publishers.

## When to use

- Use it whenever an existing event must carry more data.

## When NOT to use

- Never remove, rename, or retype an event field; create a new event with [add-event](../add-event/PLAYBOOK.md) instead.

## Syntax

```text
cozy add event-version <모듈> <이벤트> --field 이름:종류 …
```

## Creates

- New record components with boxed types (`String`, `Integer`, `Long`, `Boolean`, `LocalDate`, `Instant`), which read as `null` from older events.
- A new `of(…)` that takes the new fields and writes the next `schemaVersion`; the previous `of(…)` now delegates with `null`, so existing publishers still compile.
- `src/test/resources/events/{Event}/v{n+1}.json`, built from the highest existing sample; older samples stay untouched.

## Slots

- This command leaves no slot; publishers that should send the new values switch to the new `of(…)`.

## Example

```bash
cozy add event-version memo MemoCreated --field pinned:bool
```

## Failures

| Message contains | Action |
| --- | --- |
| `가 이미`, `로 있습니다` | Use another field name; field types never change. |
| `일부 필드만 있습니다` | Drop the fields that already exist and rerun. |
| `한 줄인 of()를 하나로 찾지 못했습니다` | Add the field by hand and verify with `mvn -o verify` (C6). |
| `v1.json이 없습니다` or `schemaVersion이` | Fix the samples so the highest `v{n}.json` matches the current `schemaVersion`. |

## Next

- Make every listener the command lists handle older events whose new fields are `null`, then run `mvn -o verify`.
