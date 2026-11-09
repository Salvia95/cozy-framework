# cozy add migration

Creates an empty Flyway migration with a correctly ordered file name.

## When to use

- Use it to create a table, add a column, or add an index.

## When NOT to use

- Skip it for a CRUD entity's table; [add-crud](../add-crud/PLAYBOOK.md) creates that migration.

## Syntax

```text
cozy add migration <모듈> <설명>
```

- The description is lowercase snake_case (`create_activity`, `add_due_on`).
- The command picks the timestamp so the new file sorts after every existing migration.

## Creates

- `src/main/resources/db/migration/V{yyyyMMddHHmm}__{module}_{description}.sql`.

## Slots

- `migration-sql`: write SQLite DDL for this module's tables only, and start every table and index name with the module name.
- Store an `Instant` as `TEXT`, and store a boolean as `INTEGER NOT NULL DEFAULT 0 CHECK (x IN (0, 1))`.

## Example

```bash
cozy add migration activity create_activity
```

```sql
CREATE TABLE activity (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    message    TEXT    NOT NULL,
    created_at TEXT    NOT NULL
);
```

## Failures

| Message contains | Action |
| --- | --- |
| `cozy-starter-sqlite가 필요합니다` | Stop; this app has no storage, and storage must be chosen when the app is generated. |

- Never edit an applied migration; add a new migration for every later change.

## Next

- Add reads and writes with [add-query](../add-query/PLAYBOOK.md) and [add-command](../add-command/PLAYBOOK.md); both print the module's tables and columns.
