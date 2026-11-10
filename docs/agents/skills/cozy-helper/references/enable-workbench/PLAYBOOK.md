# cozy enable workbench

Adds the dev-only workbench to an app, so that a person can browse screens, states, and structure at `/cozy/dev/workbench`.

## When to use

- Use it in an app that has screens (`cozy-ui`) but no `cozy-workbench` dependency in `pom.xml`.

## When NOT to use

- Do not use it to inspect the app yourself; `cozy describe --json` returns the same screen structure as text.

## Syntax

```text
cozy enable workbench
```

## Creates

- A `cozy-workbench` dependency right after `cozy-ui` in `pom.xml`.
- The workbench runs only with the `dev` profile, and the distribution package leaves it out.
- An app that already has the dependency prints `바뀐 것 없음`.

## Slots

- This command leaves no slot.

## Example

```bash
cozy enable workbench
mvn -o verify
```

## Failures

| Message contains | Action |
| --- | --- |
| `워크벤치는 cozy 1.2.0부터 있습니다` | Raise the `cozy-parent` version in `pom.xml` first, then rerun. |
| `cozy-ui가 없습니다` | Stop; the workbench needs an app with screens. |
| `의존 블록을 하나로 찾지 못했습니다` | Add the `io.salvia95.cozy:cozy-workbench` dependency to `pom.xml` by hand. |

## Next

- Tell the user to run the app with the `dev` profile and open `/cozy/dev/workbench`.
