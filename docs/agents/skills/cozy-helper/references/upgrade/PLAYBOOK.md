# Upgrading Cozy

Steps for a new Cozy release: the user upgrades the installed development tool, and the app moves to that release when it is ready.

## When to use

- Use it when the user asks to update Cozy or to move the app to a newer Cozy release.
- Use it when a command prints `[cozy] 새 버전이 있습니다` or `[cozy] 판을 올렸습니다`, to tell the user what comes next.

## When NOT to use

- Do not run `cozy upgrade` unless the user asks for it, because it reinstalls the development tool that every app on the PC uses.
- Do not change the cozy-parent version as part of another task.

## Syntax

```text
cozy upgrade --check [--to <version>] [--json]
cozy upgrade [--to <version>]
cozy version
```

- `cozy upgrade --check` reads the update channel, shows newer releases with the commands each one needs in the app, and changes nothing; `--dry-run` does the same, and `cozy update` is its old name.
- `cozy upgrade` downloads a release, ends, and installs it in the background for one or two minutes; the next `cozy` command prints the result on its first line.
- `cozy version` prints the installed release.
- Neither command changes the app, so the app keeps building with the cozy-parent version in its `pom.xml`.

## Creates

| Step | What to do |
| --- | --- |
| 1 | Run `cozy version` and read the cozy-parent `<version>` in `pom.xml`. |
| 2 | Set the cozy-parent `<version>` to the installed release, and never to a higher one. |
| 3 | Run each command listed after `앱에서 돌릴 명령` for the releases in between, oldest release first. |
| 4 | Run `cozy sync agents`, then read `.agents/skills/cozy-helper/SKILL.md` again. |
| 5 | Run `cozy check`, then `mvn -o clean verify`. |

- `cozy upgrade --check` lists the commands before the upgrade, and `cozy upgrade` prints them after `[cozy] 앱을 이 판으로 올릴 때 앱 폴더에서 돌릴 명령:`.
- After the upgrade, ask the user for the release notes when you need the commands of releases that the output no longer shows.

## Slots

- This playbook leaves no slot.
- A command in step 3 can print remaining work (`남은 일`) as `file:line`; fix each one by hand and rerun `cozy check`.
- When `cozy check` prints `[C12 경고]` for a hand-written module script, run `cozy migrate behaviors`; a component that it cannot move stays hand-written, so tell the user what each `남은 일` says.

## Example

```bash
cozy version
# pom.xml: set the cozy-parent <version> to the printed release
cozy sync agents
cozy check
mvn -o clean verify
```

## Failures

| Message contains | Action |
| --- | --- |
| `채널을 정하지 않았습니다` | Ask the user for the channel address from their administrator, then run `cozy channel set <주소>`. |
| `새 판은 모두 호환이 깨지는 판입니다` | Show the user the listed commands, and run `cozy upgrade --to <version>` only after the user agrees. |
| `설치하는 중입니다` | Wait one or two minutes and run any `cozy` command again. |
| `설치하지 못했습니다` | Show the user the printed log path and downloaded file, and do not retry on your own. |
| `지금 판을 알 수 없습니다` | Ask the user to run the installed `cozy` command instead of a copied jar. |
| Maven cannot resolve an `io.salvia95.cozy` artifact | Set the cozy-parent `<version>` back to the release that `cozy version` prints. |

## Next

- Tell the user that apps still on the old release keep building, and that each app moves when it is ready.
