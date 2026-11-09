# cozy link

Declares that a module uses other modules' `spi` (Finder, DTO, event, public fragment).

## When to use

- Use it before code or templates in one module use another module's `spi`.
- Use it when `cozy check` or `mvn -o verify` prints `고치는 명령: cozy link …`.

## When NOT to use

- Do not use it to call another module's `*Facade`; cross-module writes go through events.

## Syntax

```text
cozy link <모듈> --uses <다른 모듈>[,…]
```

## Creates

- It adds `"{other} :: spi"` to `allowedDependencies` in `{module}/package-info.java`, always written as an array.
- An existing declaration prints `바뀐 것 없음`.

## Slots

- This command leaves no slot.

## Example

```bash
cozy link dashboard --uses memo,activity
```

## Failures

| Message contains | Action |
| --- | --- |
| `공개된 것(spi)이 없습니다` | Give the other module an `spi` first with add-crud, add-dto, add-event, or add-fragment. |
| `의존이 순환합니다` | Break the cycle by having one side receive an event ([add-listener](../add-listener/PLAYBOOK.md)). |
| `@ApplicationModule이 없습니다` | Add `@ApplicationModule(allowedDependencies = {})` to that module's `package-info.java` first. |
| `allowedDependencies를 읽을 수 없습니다` | Rewrite the value as a literal string array and rerun. |

## Next

- Use the other module's `spi` types and fragments in code and templates.
