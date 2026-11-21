# cozy rename behavior

Renames a module component in the behavior spec, the script, and every `x-data` of the module's templates.

## When to use

- Use it when a component's name no longer says what it does.

## When NOT to use

- Do not rename `x-data` values or spec keys by hand; the spec, the script, and the templates must change together.

## Syntax

```text
cozy rename behavior <module> <behavior> <new-behavior>
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
| `이미 있는 동작입니다` | Pick another new name. |
| `없는 동작입니다` | Use a name from the printed list; a trailing `인가요?` names the closest one. |

## Next

- Run `cozy check`, then `mvn -o verify`.
