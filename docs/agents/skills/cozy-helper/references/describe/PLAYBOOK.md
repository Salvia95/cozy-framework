# cozy describe

Prints the app map so you can find modules and contracts without opening files.

## When to use

- Run it at the start of a task to see modules, declared dependencies, Facade and Finder signatures, DTOs, events, screens, and open slots.
- Run it after a command to confirm the new structure.

## Syntax

```text
cozy describe [--json]
```

- `--json` prints the same map as `modules[]`, `slots[]`, and `problems[]`.

## Creates

- `describe` creates nothing; it only reads sources and resources.

## Slots

- `describe` lists every open `cozy:slot` as `file:line` with its task.

## Example

```text
모듈 memo (it.cozy.app.memo)
  의존: 없음
  쓰기 MemoFacade
    long create(String title, String body, boolean pinned)
  읽기 MemoFinder
    Optional<MemoItem> find(long id)
  이벤트 MemoCreated(long memoId, String title) 발행: src/main/java/.../MemoFacadeImpl.java
```

- `의존: 없음` means the module uses no other module.
- An event without a `발행:` file is not published anywhere yet.

## Failures

| Message contains | Action |
| --- | --- |
| `앱 프로젝트(pom.xml과 src/main/java가 있는 폴더)를 찾지 못했습니다` | Run the command inside the app folder or pass `--project <folder>`. |
| A file listed under `problems` | Fix the Java syntax error in that file and rerun. |

## Next

- Pick the playbook for the task from the table in `SKILL.md`.
