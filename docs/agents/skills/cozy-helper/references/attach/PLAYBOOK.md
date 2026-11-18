# cozy attach

Attaches a component to one element of a template, or detaches it, after checking that every name belongs to the component.

## When to use

- Use it to put a module component from `cozy add behavior` or a `cozy` component on an element of a screen.
- Use it to add, change, or remove one event or value binding (`x-on`, `x-bind`, `x-text`, `x-show`, `x-model`, `x-cloak`) on an element.
- Use it with `--detach` before removing an entry that a template still calls.

## When NOT to use

- Do not write `x-data`, `x-on`, `x-bind`, `x-text`, `x-show`, `x-model`, or `x-cloak` in a template by hand; this command owns those attributes.
- Do not use it to change what a component does; use `cozy add behavior` for module components.

## Syntax

```text
cozy attach <컴포넌트> --at <템플릿>:<줄> [--data 이름=값 …] [--on 이벤트:동작 …] [--text 이름] [--show 이름] [--bind 속성:이름 …] [--model 상태] [--cloak] [--detach]
```

- `--at` takes the template path under `templates/` without `.html` and the line where the element starts, such as `memo/internal/fields:7`.
- `cozy describe --json` lists what is already bound on each screen element in `screens[].bindings[]`, with the `template` and `line` to pass to `--at`.
- `cozy describe --json` lists each module component in `modules[].behaviors[]`, with its state, computed values, actions, screen events, and the elements in `attached[]`.
- The element without a surrounding element of the same component becomes the component's root and takes `x-data` and `--data`.
- An element inside the root takes only `--on`, `--text`, `--show`, `--bind`, `--model`, and `--cloak`.
- `--data` takes the names that the component reads; a value with `${…}` becomes `th:data-…`.
- Each value is one name, or `name('text')` for a `cozy` method that takes one text argument, such as `toggle('filter')`.
- `--detach` removes only the listed attributes, or every attribute of the component on that element when nothing else is listed.

## Creates

- The changed start tag of the one element in the template; the rest of the file stays as it is.

## Slots

- This command leaves no slot.

## Example

```bash
cozy attach memoBodyCount --at memo/internal/fields:7 --data 'length=${bodyLength}' --data limit=5000 --on input:count
cozy attach memoBodyCount --at memo/internal/fields:9 --text label --cloak
cozy attach cozyCopy --at memo/api/detail:21 --data 'copy-text=${memo.body}' --on click:copy --cloak
```

- Read the line numbers again after a `--detach`, because removing an attribute that sat on its own line joins two lines.

## Failures

| Message contains | Action |
| --- | --- |
| `에 상태나 계산` or `에 동작` and `가 없습니다` | Use a name from the printed list, or add the entry first with `cozy add behavior`. |
| `시작하는 요소가` | Pick the line where exactly one element starts. |
| `그려질 때 다른 것으로 바뀌어` | Attach to the surrounding element or to an element inside the fragment's own template. |
| `이미` and `를 붙였습니다` | Attach to another element, or detach the other component first as printed. |
| `를 읽지 않습니다` | Use only the printed `data-*` names. |
| `--data는 컴포넌트를 붙인 요소` | Put `--data` on the root element that has `x-data`. |
| `안쪽 요소에 붙인 것이 남아 있습니다` | Detach the printed inner lines first, then the root. |
| `R16` and `감싸는 컴포넌트` | Run `cozy attach` with a name from the printed list instead of the hand-written attribute. |
| `[C13]` | Attach the printed component, or connect the printed action with `--on`, as the message says. |

## Next

- Run `cozy check`, which fails with `[C13]` until every declared component is attached and every action has a caller, then `mvn -o verify`.
