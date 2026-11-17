# cozy add behavior

Declares one Alpine.js component of a module in the module's behavior spec, or changes it, and makes the module's script from that spec.

## When to use

- Use it when a screen needs state that may be lost on reload and needs no server, such as an open panel, a selection count, or a live preview of an input.
- Use it again with the same name to add an entry to a component or to change one.

## When NOT to use

- Do not use it for anything that reads or saves data; use htmx (`hx-get`, `hx-post`) and a controller instead.
- Do not use it for copying, folding, or confirming a plain POST form; use `cozyCopy`, `cozyDisclosure`, or `cozyConfirm`.
- Do not edit `static/{module}/{module}.js` or `behaviors/{module}.yml` by hand; this command and `cozy sync behaviors` own both files.

## Syntax

```text
cozy add behavior <모듈> <동작> [--purpose 목적] [--state 이름:종류[=처음 값][@data-이름] …] [--computed 이름:식 …] [--action 이름:단계[; 단계] …] [--listen 동작:모듈:이벤트 …]
```

- The behavior name is lowercase with hyphens (`body-count`), and the component is the module name plus the name in camelCase (`memoBodyCount`).
- A new behavior needs `--purpose`, one sentence that the workbench and `cozy describe` show.
- State kinds are `bool`, `number`, `text`, and `list`; `@data-limit` reads the start value from the `x-data` element's `data-limit` attribute.
- Values are names of state and computed entries, numbers, `true`, `false`, quoted text (`'list'`), and event values (`event.value`, `event.checked`, `event.data.id`, `event.detail.message`); event values work only in actions.
- Computed functions are `not`, `and`, `or`, `eq`, `ne`, `gt`, `lt`, `ge`, `le`, `empty`, `count`, `length`, `includes`, `number`, `if`, and `format('{name}자', 'ko-KR')`.
- Action steps are `set`, `toggle`, `increment`, `decrement`, `addItem`, `removeItem`, `toggleItem`, `reset`, `emit('{module}:name', state …)`, `when(condition, step …)`, and `within('selector', step …)`, joined with `;`.
- `--listen clear:memo:archived` runs the action `clear` when the window receives the screen event `memo:archived`, from another component's `emit` or from the server's `HX-Trigger` header.

## Creates

- `src/main/resources/behaviors/{module}.yml`, the spec that is the source of every component of the module.
- `src/main/resources/static/{module}/{module}.js`, made from the spec with a stamp that the `[C12]` check compares on every build.
- The layout loads that one file on every screen.

## Slots

- This command leaves no slot.

## Example

```bash
cozy add behavior memo body-count --purpose "본문을 쓰는 동안 글자 수를 보인다" \
  --state length:number@data-length --state limit:number=5000@data-limit \
  --computed "over:gt(length, limit)" --computed "label:format('{length} / {limit}자', 'ko-KR')" \
  --action "count:within('textarea', set(length, length(event.value)))"
```

- Attach it in the module's template as `<div x-data="memoBodyCount" th:data-length="${length}" data-limit="5000" x-on:input="count">` and show `<div x-text="label">`.
- Write only names in `x-*` attributes, such as `x-on:click="pick"`, `x-show="over"`, and `x-bind:disabled="none"`.

## Failures

| Message contains | Action |
| --- | --- |
| `--purpose로 목적 한 문장` | Add `--purpose` with one sentence for the new behavior. |
| `를 모릅니다` | Use only the functions, steps, and event values listed in Syntax; if the behavior cannot be written with them, tell the user that it needs a new `cozy` component. |
| `이름 '` and `이 없습니다` | Declare the state or computed entry first, in the same command or an earlier one. |
| `손으로 쓴 컴포넌트 파일입니다` | Tell the user that the module has a hand-written script, and do not delete it yourself. |
| `[C12]` | Run `cozy sync behaviors` exactly as printed. |
| `R16` and `모듈의 컴포넌트입니다` | Use only `cozy` components and components of the template's own module. |

## Next

- Run `cozy check`, then `mvn -o verify`.
- Follow the [screens](../screens/PLAYBOOK.md) rules for the markup around the component.
