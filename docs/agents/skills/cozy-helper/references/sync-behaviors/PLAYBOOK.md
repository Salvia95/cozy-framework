# cozy sync behaviors

Makes every module's script again from its behavior spec, and tidies each spec into the form that cozy writes.

## When to use

- Use it when `cozy check` or `mvn -o verify` prints `[C12]` with `고치는 명령: cozy sync behaviors`.
- Use it after the user edited a `behaviors/{module}.yml` file by hand.
- Use it after upgrading Cozy when `[C12]` says that an earlier cozy made the script, because the script lacks the `cozy:component` lines that the checks read.

## When NOT to use

- Do not use it to change a behavior; use `cozy add behavior` with the same behavior name.

## Syntax

```text
cozy sync behaviors
```

## Creates

- `src/main/resources/static/{module}/{module}.js` for every `src/main/resources/behaviors/{module}.yml`, replacing any hand edit.
- The spec file itself rewritten in the tidy form when it was edited by hand.
- A made script whose spec was deleted is removed (`지움`).

## Slots

- This command leaves no slot.

## Example

```bash
cozy sync behaviors --dry-run
cozy sync behaviors
```

## Failures

| Message contains | Action |
| --- | --- |
| `모르는 키` or `를 모릅니다` | Fix the printed entry of the spec with `cozy add behavior`, or show the user the line they edited by hand. |
| `손으로 쓴 컴포넌트 파일입니다` | Tell the user that the module has a hand-written script next to a spec, and do not delete it yourself. |
| `모듈 '` and `가 없습니다` | Ask the user whether to delete the spec of the removed module. |
| `[C12 경고]` and `손으로 쓴 모듈 JS` | Run `cozy migrate behaviors` instead; this command never overwrites a hand-written script. |

## Next

- Run `cozy check`, then `mvn -o verify`.
