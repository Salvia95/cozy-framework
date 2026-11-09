# cozy check

Runs the fast static check: template rules (R1–R15), module structure (C8), and unfilled slots (C9).

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

## Example

```text
[R3] templates/voc/api/list.html:1 → wiki/spi/card :: card(1개 인자) — voc의 allowedDependencies에 wiki가 없습니다. … 고치는 명령: cozy link voc --uses wiki
[cozy check] 오류 1건 (R1~R15, C8, C9). 컴파일과 렌더링 검사(C1, C3~C7)는 mvn verify가 합니다.
```

- Each violation reads `[ID] location → target — how to fix`.
- When a violation ends with `고치는 명령: cozy …`, run that command exactly as printed; `mvn -o verify` prints the same command for C1.
- When a violation has no command, follow the text after `—`.
- A violation for using another module's `internal` or `api` is fixed by switching to that module's `spi`, not by declaring a dependency.
- Template and design rules (R-ids) are explained in the [screens](../screens/PLAYBOOK.md) playbook.

## Failures

| Result | Action |
| --- | --- |
| Exit code 1 | Fix the listed violations and rerun. |
| Exit code 2 with `cozy-ui … jar가 없습니다` | Run `mvn -o dependency:resolve` once in the app folder and rerun. |

## Next

- Run `mvn -o verify` when `cozy check` reports no errors.
