# cozy enable events

Adds the Cozy events starter to an app that has storage but no events.

## When to use

- Use it before the first `add event`, `add listener`, or `add crud --publish` in an app generated without events.

## Syntax

```text
cozy enable events
```

## Creates

- A `cozy-starter-events` dependency right after `cozy-starter-sqlite` in `pom.xml`.
- An app that already has the starter prints `바뀐 것 없음`.

## Slots

- This command leaves no slot.

## Example

```bash
cozy enable events
mvn -o verify
```

## Failures

| Message contains | Action |
| --- | --- |
| `저장(persistence)이 필요합니다` | Stop; events need storage, which must be chosen when the app is generated. |
| `의존 블록을 하나로 찾지 못했습니다` | Add the `io.salvia95.cozy:cozy-starter-events` dependency to `pom.xml` by hand. |

## Next

- Create events with [add-event](../add-event/PLAYBOOK.md) or `add crud --publish`, and handle them with [add-listener](../add-listener/PLAYBOOK.md).
