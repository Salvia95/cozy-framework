# cozy rename behavior

Renames a module component in the behavior spec, the script, and every `x-data` of the module's templates.

## When to use

- Use it when a component's name no longer says what it does.

## When NOT to use

- Do not rename `x-data` values or spec keys by hand; the spec, the script, and the templates must change together.

## Syntax

```text
cozy rename behavior <모듈> <동작> <새 동작>
```

- Both names are lowercase with hyphens (`body-count`), and the component becomes the module name plus the new name in camelCase (`memoBodyLength`).

## Creates

- The changed spec and the script made from it.
- The changed templates whose `x-data` named the old component.

## Slots

- This command leaves no slot.

## Example

```bash
cozy rename behavior memo body-count body-length
```

## Failures

| Message contains | Action |
| --- | --- |
| `가 이미 있습니다` | Pick another new name. |
| `가 없습니다. 있는 것` | Use a name from the printed list. |

## Next

- Run `cozy check`, then `mvn -o verify`.
