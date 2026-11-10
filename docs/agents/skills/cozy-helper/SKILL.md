---
name: cozy-helper
description: Add Cozy app structure with cozy commands (module, CRUD, screen, fragment, event, listener, migration, DTO, query, command) and fix cozy check or mvn verify violations.
---

# cozy-helper

Cozy commands generate structure, and you fill only the slots they leave. A command writes the same files for the same input, and a failed command changes no file.

## When to use

- Use this skill to add a module, CRUD set, read-only screen, public fragment, event, listener, migration, spi DTO, Finder read, or Facade write.
- Use this skill to declare that one module uses another module.
- Use this skill to fix a `cozy check` or `mvn -o verify` violation, including an unfilled `cozy:slot` marker (`[C9]`).

## When NOT to use

- Do not look for a command to rename, move, or delete structure; edit those by hand and verify with `cozy check` and `mvn -o verify`.
- Do not look for a command to change business logic inside an existing method; edit the method directly.

## Steps

1. Run `cozy describe` to see modules, dependencies, spi contracts, events, screens, and open slots.
2. Pick the playbook for the task from the Playbooks table and read it.
3. Run the playbook's command with `--dry-run` to see the planned files, then run it without `--dry-run`.
4. Fill every slot the command lists as `file:line`, and delete each `cozy:slot` marker you fill.
5. Run `cozy check`, and run any `고치는 명령: cozy …` it prints exactly as printed.
6. Run `mvn -o verify`; the task is done when it passes.

## Playbooks

| Task | Playbook |
| --- | --- |
| See the app map | [describe](references/describe/PLAYBOOK.md) |
| Run the fast check and read violations | [check](references/check/PLAYBOOK.md) |
| Add a feature module | [add-module](references/add-module/PLAYBOOK.md) |
| Add list, create, detail, edit, and delete screens with storage | [add-crud](references/add-crud/PLAYBOOK.md) |
| Add a read-only screen such as a dashboard | [add-screen](references/add-screen/PLAYBOOK.md) |
| Publish a fragment for other modules' screens | [add-fragment](references/add-fragment/PLAYBOOK.md) |
| Declare that a module uses another module | [link](references/link/PLAYBOOK.md) |
| Add a value record that other modules see | [add-dto](references/add-dto/PLAYBOOK.md) |
| Add a read method with its SELECT | [add-query](references/add-query/PLAYBOOK.md) |
| Add a write method, optionally with INSERT, UPDATE, or DELETE | [add-command](references/add-command/PLAYBOOK.md) |
| Announce that something happened (event) | [add-event](references/add-event/PLAYBOOK.md) |
| Handle another module's event | [add-listener](references/add-listener/PLAYBOOK.md) |
| Add a field to an existing event | [add-event-version](references/add-event-version/PLAYBOOK.md) |
| Create or alter a table | [add-migration](references/add-migration/PLAYBOOK.md) |
| Turn on events in an app without them | [enable-events](references/enable-events/PLAYBOOK.md) |
| Edit screen markup and wording | [screens](references/screens/PLAYBOOK.md) |
| Update `AGENTS.md` and this skill after a Cozy upgrade | [sync-agents](references/sync-agents/PLAYBOOK.md) |

## Reading results

| Output | Meaning | Action |
| --- | --- | --- |
| `[cozy] 적용했습니다. 빈칸 N곳` | The command wrote files and N slots remain. | Fill each listed `file:line`. |
| `[cozy] 바뀐 것 없음` | The same structure already exists. | Do nothing; rerunning a command is safe. |
| Exit code 2 | The command was refused and no file changed. | Read stderr, fix the arguments, and rerun. |

- A plan line starting with `만듦` is a new file, `바꿈` is an edited file, and `그대로` is an unchanged file.
- Add `--json` to any command for machine-readable output.

## Filling slots

- In Java, replace the `// cozy:slot {id} — {task}` line and the `throw new UnsupportedOperationException(...)` line under it with your code.
- In HTML, SQL, XML, and YAML, replace the `cozy:slot` comment with your content.
- A remaining marker fails `mvn -o verify` with `[C9]`, and a deleted marker without real content passes it, so cover the filled logic with a test.

## Constraints

- Never create or move a structural file by hand when a playbook covers the task.
- Never edit `allowedDependencies`, `cozy/fragments.yml` entries, or navigation entries by hand.
