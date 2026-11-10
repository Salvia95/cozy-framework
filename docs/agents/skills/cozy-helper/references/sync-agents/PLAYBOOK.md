# cozy sync agents

Makes the app's `AGENTS.md` and `cozy-helper` skill match the installed `cozy` command.

## When to use

- Use it after the Cozy framework or the `cozy` command is upgraded, so that the playbooks describe the commands that are installed.
- Use it in an app that has no `AGENTS.md` or no `.agents/skills/cozy-helper/` folder.

## When NOT to use

- Do not keep your own notes inside `.agents/skills/cozy-helper/`; this command replaces changed files there and deletes files it does not know.

## Syntax

```text
cozy sync agents [--force]
```

## Creates

- `AGENTS.md` at the app root when the file is missing.
- `.agents/skills/cozy-helper/SKILL.md` and one `references/<command>/PLAYBOOK.md` per command, identical to the ones in the installed `cozy` command.
- An `AGENTS.md` with different content stays as it is (`그대로  AGENTS.md    내용이 달라 바꾸지 않음`) unless you pass `--force`.
- An app that already matches prints `바뀐 것 없음`.

## Slots

- This command leaves no slot.

## Example

```bash
cozy sync agents --dry-run
cozy sync agents
```

## Failures

| Message contains | Action |
| --- | --- |
| `cozy sync 뒤에는 agents만 적습니다` | Run `cozy sync agents` with no other argument. |
| `--force는 cozy sync agents에만 씁니다` | Remove `--force` from the other command. |
| `달라 그대로 두었습니다` | Copy any app-specific lines out of `AGENTS.md`, run `cozy sync agents --force`, and add those lines back. |

## Next

- Read `.agents/skills/cozy-helper/SKILL.md` again, because its playbook list can change with the `cozy` command.
