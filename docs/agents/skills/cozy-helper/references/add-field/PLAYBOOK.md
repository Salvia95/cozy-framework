# cozy add field

Adds one field to a CRUD set that `cozy add crud` created, across the table, the Java types, the SQL, the form, and the specs.

## When to use

- Use it whenever an existing CRUD set must store and edit one more value.

## When NOT to use

- Do not use it on a module without a CRUD set; create the set with [add-crud](../add-crud/PLAYBOOK.md) first.
- Never use it to remove, rename, or retype a field; edit those by hand and verify with `mvn -o verify`.
- Do not use it to add data to an event; use [add-event-version](../add-event-version/PLAYBOOK.md) for that.

## Syntax

```text
cozy add field <모듈> --entity <E> --field 이름:종류[!][:라벨] [--default 값]
```

- The field format is the same as in [add-crud](../add-crud/PLAYBOOK.md), and the kinds are `text`, `longtext`, `int`, `long`, `bool`, and `date`.
- A required field (`!`) needs `--default`, because existing rows take that value; `bool` never takes `--default`.
- Run the command once per field.

## Creates

- A new migration `V{time}__{module}_add_{table}_{column}.sql` with one `ALTER TABLE … ADD COLUMN`.
- The field in the domain record, the spi DTO `{E}Item`, `{E}Row`, `{E}Form`, and the `Row` and `Detail` records of `{E}Views`.
- One more parameter on `create` and `update` of `{E}Facade` and its implementation, and on `update` of `{E}Mapper`.
- The column in `columns`, `insert`, and `update` of `{E}Mapper.xml`.
- The input in `templates/{module}/internal/fields.html`, and a sample value in every spec that uses `{E}Views` or `{E}Form`.
- One more argument in every call of `create`, `update`, and the record constructors, in main code and in tests.

## Slots

- This command leaves no slot.

## Example

```bash
cozy add field memo --entity Memo --field dueOn:date:마감일
cozy add field memo --entity Memo --field code:text!:코드 --default 없음
```

## Failures

| Message contains | Action |
| --- | --- |
| `crud 한 벌이 아닙니다` | Check `--entity`, or create the set with [add-crud](../add-crud/PLAYBOOK.md). |
| `--default` | Add `--default` for a required field, and remove it for an optional or `bool` field. |
| `일부에만 있습니다` | Remove the half-added field from the listed files, then rerun. |
| `넘길 값을 정할 수 없습니다` | Change the call at the printed `file:line` to pass values read from one object such as `form.getTitle()`, then rerun. |
| `인자가` | Fix the call at the printed `file:line` so it compiles against the current signature, then rerun. |
| `를 찾지 못했습니다` | Restore the named place to the shape `cozy add crud` generated, or add the field by hand in every file listed under Creates. |

## Next

- Add the list column in `templates/{module}/api/list.html` and the item in `templates/{module}/api/detail.html` by hand; the command does not edit those two screens.
- Run `mvn -o verify`.
