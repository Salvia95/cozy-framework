# Screen states

Rules for drawing every state of a screen and for choosing how to report results and errors.

## When to use

- Use it when a screen shows empty data, filter results, validation errors, save results, or request failures, or when you must choose between a flash, an alert, a toast, and a dialog.

## When NOT to use

- Do not use it for the button layout of a screen; use [screen-layout](../screen-layout/PLAYBOOK.md).

## Syntax

- This playbook has no command; every state you design becomes a state in `specs/{module}-{screen}.yml`, and `mvn -o verify` renders each one.

## Creates

- Design every state, because a screen that only works with data is unfinished.

| State | When | Show |
| --- | --- | --- |
| Default | Normal data exists | The designed screen. |
| Nothing yet | No item exists and no filter is set | `emptyStateAction` with a create button. |
| No results | A filter or search matched nothing | `noResults` with a clear-filter button. |
| Invalid | The submitted form failed validation | The error under each field and focus on the first one. |
| Result | Create, edit, or delete finished and redirected | A flash at the top of the next page. |
| Empty value | An optional property is empty | A `text-secondary` sentence or a dash instead of a blank. |

- Report create, edit, and delete results with `CozyFlash` before a redirect, and never with a toast.
- Use `alert(tone, message)` for a notice that stays on the screen or a server error that belongs to no field.
- Use a toast only for minor confirmations such as a copy, because toasts disappear.
- Use `deleteConfirm` before every delete, and `modal` or `data-cozy-confirm` for other confirmations.
- Validate on submit only, never while the user types, and never disable the primary button.
- Choose the tone by what it asks of the user: `neutral` asks nothing, `info` informs, `success` confirms, `warning` asks for attention, and `critical` blocks or cannot be undone.

## Slots

- Add a state to the screen spec for each case above that the screen can reach, with `_errors` for the invalid state.

## Example

```yaml
states:
  default: { result: { rows: [ { id: 1, title: 회의록 } ], page: { number: 1, size: 10, totalElements: 1 } } }
  empty: { result: { rows: [], page: { number: 1, size: 10, totalElements: 0 } } }
  invalid:
    form: { title: "" }
    _errors: { form: { title: 제목을 입력하세요 } }
```

## Failures

| Message contains | Action |
| --- | --- |
| `[C3]` and a state name | Give the state a value for every model name, and use `{}` when it has none. |
| `[R14]` | Replace the color class with a component that takes a tone. |

## Next

- Read [wording](../wording/PLAYBOOK.md) to write the messages of each state.
