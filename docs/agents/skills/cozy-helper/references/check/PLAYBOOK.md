# cozy check

Runs the fast static check: template rules (R1–R16), module structure (C8), unfilled slots (C9), behavior specs (C12), and unwired behaviors (C13).

## When to use

- Run it after any command or slot edit, before `mvn -o verify`.
- Run it to see a `mvn -o verify` violation again in seconds.

## When NOT to use

- Do not treat a passing `cozy check` as done; module dependencies (C1), rendering and specs (C3–C5), event samples (C6), and view names (C7) are checked only by `mvn -o verify`.

## Syntax

```text
cozy check [--json]
```

- `--json` prints each violation with `id`, `file`, `line`, `message`, and `command`.

## Creates

- `check` creates nothing.

## Slots

- A `[C9]` violation is an unfilled slot; fill the `cozy:slot` at that location and delete the marker.
- A `[C13]` violation is a declared behavior that no template uses yet, an action that nothing calls, or a screen event that nothing sends or receives.

## Example

```text
[R3] templates/voc/api/list.html:1 → wiki/spi/card :: card(1개 인자) — voc의 allowedDependencies에 wiki가 없습니다. … 고치는 명령: cozy link voc --uses wiki
[cozy check] 오류 1건 (R1~R16, C8, C9, C12, C13). 컴파일, 렌더링, 스크립트 검사(C1, C3~C7, C10)는 mvn verify가 합니다.
```

- Each violation reads `[ID] location → target — how to fix`.
- When a violation ends with `고치는 명령: cozy …`, run that command exactly as printed; `mvn -o verify` prints the same command for C1.
- After `cozy migrate events`, run `mvn -o clean verify`, because the classes compiled from the old event place stay in `target/` until a clean build.
- When a violation has no command, follow the text after `—`.
- A violation for using another module's `internal` or `api` is fixed by switching to that module's `spi`, not by declaring a dependency.
- Template and design rules (R-ids) are explained in the [screens](../screens/PLAYBOOK.md) playbook.

## Failures

| Result | Action |
| --- | --- |
| Exit code 1 | Fix the listed violations and rerun. |
| `[C13]` and `붙이지 않았습니다` | Attach the component with [attach](../attach/PLAYBOOK.md) in the same task, or remove it with [remove-behavior](../remove-behavior/PLAYBOOK.md) when the screen does not need it. |
| `[C13]` and `부르는 요소가 없습니다` | Connect the action with `cozy attach … --on 이벤트:동작` or `--listen`, or remove the action as printed. |
| `[C13]` and `받는 곳이 없습니다` or `내는 곳이 없습니다` | Add the printed receiver or sender; for a server event, write the event name as one literal string in the `HX-Trigger` header value. |
| `[C12 경고]` and `손으로 쓴 모듈 JS` | Run `cozy migrate behaviors`, and tell the user about each `남은 일` that it prints. |
| Exit code 2 with `cozy-ui … jar가 없습니다` | Run `mvn -o dependency:resolve` once in the app folder and rerun. |

## Next

- Run `mvn -o verify` when `cozy check` reports no errors.
