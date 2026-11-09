# cozy add query

Adds a read method to a Finder together with its implementation, mapper method, row record, and `<select>`.

## When to use

- Use it for a list, count, summary, or filtered read that this module or other modules call.

## Syntax

```text
cozy add query <모듈> <이름> [--param 이름:종류 …] --returns long|int|bool|text|<Record>|list:<Record> [--in <Finder>]
```

- The name is a camelCase method name (`recent`, `countPinned`), and a parameter is `name:kind` with a field kind.
- `<Record>` returns `Optional<Record>` and `list:<Record>` returns `List<Record>`; create the record first with [add-dto](../add-dto/PLAYBOOK.md).
- Without a Finder the command creates `{Module}Finder`; with two or more, pass `--in <Finder>`.

## Creates

| File | Addition for `recent --param limit:int --returns list:ActivityItem` |
| --- | --- |
| `spi/…Finder` | `List<ActivityItem> recent(int limit);` |
| `internal/app/query/…FinderImpl` | A method that calls the mapper and copies each row into the record |
| `internal/infra/…Mapper` | `List<ActivityItemRow> recent(@Param("limit") int limit);` |
| `internal/infra/ActivityItemRow` | A row record with the record's components, only when missing |
| `mapper/{module}/…Mapper.xml` | `<select id="recent" resultType="…ActivityItemRow">` |

## Slots

- `query-sql` (XML): write one SELECT whose column names match the row component names (`created_at` fills `createdAt`), and bind parameters as `#{limit}`.
- The command output lists this module's tables and columns from the migrations.
- When the stored format differs from the shown format, convert it in the SELECT or change the row type and the copy code in the FinderImpl.

## Example

```bash
cozy add query activity recent --param limit:int --returns list:ActivityItem
```

```xml
SELECT message, strftime('%Y-%m-%d %H:%M', created_at, 'localtime') AS at
FROM activity ORDER BY created_at DESC, id DESC LIMIT #{limit}
```

## Failures

| Message contains | Action |
| --- | --- |
| `먼저 cozy add dto` | Create the record with [add-dto](../add-dto/PLAYBOOK.md). |
| `Finder가 둘 이상입니다` | Pass `--in` with one of the listed Finders. |
| `같은 이름이 다른 시그니처로 있습니다` | Use another method name; overloads are never generated. |
| `일부만 있습니다` | Complete the missing places or delete the existing ones, then rerun. |
| `Mapper가 없습니다` | Make the implementation's constructor take this module's `*Mapper`, then rerun. |
| `행으로 옮길 수 없습니다` | Write this query by hand; the record holds a record or list. |

- Rerunning the same command prints `바뀐 것 없음` even after the SQL and method bodies are filled.

## Next

- Fill `query-sql`, then run `cozy check` and `mvn -o verify`.
