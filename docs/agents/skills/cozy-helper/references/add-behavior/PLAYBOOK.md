# cozy add behavior

Adds one Alpine.js component to a module for state that stays inside the screen, such as an open panel, a selection count, or a live preview of an input.

## When to use

- Use it when a screen needs state that may be lost on reload and needs no server, and no `cozy` component in `docs` or the catalog already does it.

## When NOT to use

- Do not use it for anything that reads or saves data; use htmx (`hx-get`, `hx-post`) and a controller instead.
- Do not use it for copying, folding, or confirming a plain POST form; use `cozyCopy`, `cozyDisclosure`, or `cozyConfirm`.
- Do not use it for modals, dropdowns, or tooltips; use the Tabler markup and the `modal` component.

## Syntax

```text
cozy add behavior <모듈> <동작>
```

- The name is lowercase with hyphens (`char-count`), and the component is the module name plus the name in camelCase (`memoCharCount`).

## Creates

- `static/{module}/{module}.js` with one `window.Alpine.data('{component}', …)` registration inside `alpine:init`, or the registration appended to the end of that file.
- The layout loads this one file on every screen, so components registered anywhere else do not exist at runtime.

## Slots

- `behavior-{component}` (script): write the state as properties and the logic as methods of the returned object, then delete the marker.
- Read server values from `root.dataset`, which `init` already captures, and never call `fetch` or `htmx.ajax` from the component.

## Example

```bash
cozy add behavior memo char-count
```

- Attach it in the module's template as `<div x-data="memoCharCount" th:data-limit="${limit}">` and bind with `x-on:input="count"` and `x-text="label"`.
- Write only names, method calls, comparisons, and plain variable assignments in `x-*` expressions, because the CSP build reads nothing else.
- Write long attribute names (`x-on:click`, `x-bind:class`) and never put Alpine expressions on an `<iframe>` or `<script>` element.

## Failures

| Message contains | Action |
| --- | --- |
| `모양이 아닙니다` | Add the `window.Alpine.data(…)` call by hand inside the file's `alpine:init` listener. |
| `소문자와 하이픈` | Rename the behavior in lowercase with hyphens (`pin-toggle`). |
| `R16` and `모듈의 컴포넌트입니다` | Use only `cozy` components and components of the template's own module. |
| `[C10]` | Fix the syntax error at the printed line, because one broken file stops the components of every module. |

## Next

- Run `cozy check`, then `mvn -o verify`.
- Follow the [screens](../screens/PLAYBOOK.md) rules for the markup around the component.
