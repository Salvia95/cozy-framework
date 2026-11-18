# cozy describe

Prints the app map so you can find modules and contracts without opening files.

## When to use

- Run it at the start of a task to see modules, declared dependencies, Facade and Finder signatures, DTOs, events, screens, and open slots.
- Run it after a command to confirm the new structure.

## Syntax

```text
cozy describe [--json]
```

- `--json` prints the same map as `modules[]`, `screens[]`, `events[]`, `uiEvents[]`, `slots[]`, and `problems[]`.
- `modules[].behaviors[]` lists each component of the behavior spec, and `screens[].bindings[]` lists each element with the Alpine and htmx attributes bound to it and the `template` and `line` for `cozy attach --at`.

## Creates

- `describe` creates nothing; it only reads sources and resources.

## Slots

- `describe` lists every open `cozy:slot` as `file:line` with its task.
- `slots[]` also lists each `[C13]` item with `check` set to `C13`, such as a declared component that no template uses.

## Example

```text
모듈 memo (it.cozy.app.memo)
  의존: 없음
  쓰기 MemoFacade
    long create(String title, String body, boolean pinned)
  읽기 MemoFinder
    Optional<MemoItem> find(long id)
  컴포넌트 memoBodyCount
  화면 안의 동작 memoBodyCount — 본문을 쓰는 동안 글자 수와 한도를 보인다 · 붙인 곳 memo/internal/fields:7, memo/internal/fields:9
  화면 memo/api/form — src/main/resources/specs/memo-form.yml: default, empty, invalid · 컴포넌트 memoBodyCount(memo/internal/fields:6)

이벤트 memo.MemoCreated(long memoId, String title) 발행: src/main/java/.../MemoFacadeImpl.java → activity(MemoCreatedListener)
```

- `의존: 없음` means the module uses no other module.
- Events are listed once for the whole app as `{owner}.{Event}`, after the modules, with the files that publish them and the modules that receive them after `→`.
- An event without a `발행:` file is not published anywhere yet, and `받는 곳 없음` means no listener receives it.
- `컴포넌트` under a module lists the Alpine components that `static/{module}/{module}.js` registers.
- `화면 안의 동작` lists each component of the behavior spec with its purpose and the template lines it is attached to.
- `화면 이벤트` lines, after the events, list each screen event with the places that send it before `→` and the places that receive it after `→`.
- `컴포넌트` on a screen lists the components the screen uses, including those inside the app fragments it includes, with the template and line to edit.

## Failures

| Message contains | Action |
| --- | --- |
| `앱 프로젝트(pom.xml과 src/main/java가 있는 폴더)를 찾지 못했습니다` | Run the command inside the app folder or pass `--project <folder>`. |
| A file listed under `problems` | Fix the Java syntax error in that file and rerun. |

## Next

- Pick the playbook for the task from the table in `SKILL.md`.
