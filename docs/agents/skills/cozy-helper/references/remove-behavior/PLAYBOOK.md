# cozy remove behavior

Removes a module component, or some of its entries, from the behavior spec, and detaches the component from the module's templates.

## When to use

- Use it when a screen no longer needs a component that `cozy add behavior` declared.
- Use it to remove a state, computed, action, or listen entry that nothing uses.

## When NOT to use

- Do not delete `behaviors/{module}.yml` or `static/{module}/{module}.js` by hand; this command keeps the spec, the script, and the templates together.

## Syntax

```text
cozy remove behavior <모듈> <동작> [--state 이름 …] [--computed 이름 …] [--action 이름 …] [--listen 모듈:이벤트 …]
```

- Without entry options, the whole component goes, together with its `x-data`, its `data-*`, and the bindings inside it in every template of the module.
- With entry options, only those entries go; an entry that a template or another entry still uses is not removed.

## Creates

- The changed spec and the script made from it, or neither when the last component goes.
- The changed templates where the component was attached.

## Slots

- This command leaves no slot.

## Example

```bash
cozy remove behavior memo body-count --computed over
cozy remove behavior memo body-count
```

## Failures

| Message contains | Action |
| --- | --- |
| `를 템플릿이 부릅니다` | Detach the printed lines with `cozy attach … --detach` first. |
| `이름 '` and `이 없습니다` | Remove the entries that use that name first, in the same command. |
| `가 없습니다. 있는 것` | Use a name from the printed list. |

## Next

- Run `cozy check`, then `mvn -o verify`.
