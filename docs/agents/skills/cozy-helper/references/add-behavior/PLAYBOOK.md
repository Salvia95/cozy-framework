# cozy add behavior

Declares one Alpine.js component of a module in the module's behavior spec, or changes it, and makes the module's script from that spec.

## When to use

- Use it when a screen needs state that may be lost on reload and needs no server, such as an open panel, a selection count, or a live preview of an input.
- Use it again with the same name to add an entry to a component or to change one.

## When NOT to use

- Do not use it for anything that reads or saves data; use htmx (`hx-get`, `hx-post`) and a controller instead.
- Do not use it for copying, folding, or confirming a plain POST form; use `cozyCopy`, `cozyDisclosure`, or `cozyConfirm`.
- Do not use it for modals, dropdowns, or tooltips; use the Tabler markup and the `modal` component.
- Do not edit `static/{module}/{module}.js` or `behaviors/{module}.yml` by hand; this command and `cozy sync behaviors` own both files.
- Do not use it to remove or rename a component; use [remove-behavior](../remove-behavior/PLAYBOOK.md) or [rename-behavior](../rename-behavior/PLAYBOOK.md).

## Syntax

```text
cozy add behavior <module> <behavior> [--purpose <purpose>] [--state <name>:<type>[=<initial>][@data-<name>] …] [--computed <name>:<expression> …] [--action <name>:<step>[; <step>] …] [--listen <action>:<module>:<event> …]
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

```bash
cozy attach memoBodyCount --at memo/internal/fields:7 --data 'length=${bodyLength}' --data limit=5000 --on input:count
cozy attach memoBodyCount --at memo/internal/fields:9 --text label --cloak
```

- Attach it in the same task with [attach](../attach/PLAYBOOK.md), using the `template:line` from `cozy describe --json`.
- To tell an htmx request about a change, add a step `emit('memo:selection-changed', ids)` and receive it with `hx-trigger="memo:selection-changed from:window"` on the requesting element.
- To react to a server change, add `--listen clear:memo:archived` and have the controller set the header `HX-Trigger` to the literal `memo:archived`.

## Failures

| Message contains | Action |
| --- | --- |
| `--purpose로 목적 한 문장` | Add `--purpose` with one sentence for the new behavior. |
| `모르는 함수` or `모르는 이벤트 값` or `모르는 종류` | Use only the functions, steps, and event values listed in Syntax; if the behavior cannot be written with them, tell the user that it needs a new `cozy` component. |
| `없는 이름입니다` or `없는 상태입니다` | Declare the state or computed entry first, in the same command or an earlier one. |
| `인가요?` | The command names the closest existing name; use it when it is the name you meant, otherwise pick one from the printed list. |
| `손으로 쓴 컴포넌트 파일입니다` | Tell the user that the module has a hand-written script, and do not delete it yourself. |
| `[C12]` | Run `cozy sync behaviors` exactly as printed. |
| `[C13]` | Attach the component and connect each action in the same task, as the message prints. |
| `R16` and `모듈의 컴포넌트입니다` | Use only `cozy` components and components of the template's own module. |

## Next

- Attach the component with [attach](../attach/PLAYBOOK.md) in the same task, because `cozy check` fails with `[C13]` until a template uses the component and each action has a caller.
- Run `cozy check` and `mvn -o verify`.
- Follow the [screens](../screens/PLAYBOOK.md) rules for the markup around the component.
