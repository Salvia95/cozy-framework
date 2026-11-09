# cozy add crud

Creates list, create, detail, edit, and delete screens with storage and tests for one entity.

## When to use

- Use it when a feature stores records and edits them through forms.

## When NOT to use

- Use [add-screen](../add-screen/PLAYBOOK.md) instead for a screen that only displays values.

## Syntax

```text
cozy add crud <모듈> --entity <E> --label 라벨 --icon 아이콘 --field 이름:종류[!][:라벨] … [--publish created,updated,deleted]
```

- A field is `name:kind[!][:label]`; kind is `text`, `longtext`, `int`, `long`, `bool`, `date`, or `instant`, `!` marks it required, and the label is the on-screen name (quote it when it has spaces: `--field "dueOn:date:마감 날짜"`).
- At least one `text!` field is required, and the first one becomes the list link and the detail title.
- Never pass an `instant` field; the command adds `createdAt` and `updatedAt`.
- The icon is a Tabler outline icon name (`notes`, `checklist`), listed at `/cozy/dev/catalog/icons` in a dev run.
- `--publish` adds `{E}Created`, `{E}Updated`, or `{E}Deleted` events and publishes them inside the write transaction; it needs the events starter ([enable-events](../enable-events/PLAYBOOK.md)).

## Creates

- spi: `{E}Facade` (create, update, delete), `{E}Finder` (find, search), and `spi/dto/{E}Item` and `{E}Page`.
- internal: `{E}FacadeImpl`, `{E}FinderImpl`, `domain/{E}`, `infra/{E}Mapper`, `infra/{E}Row`, and a navigation entry.
- api: `{E}Controller` with `/{module}`, `/new`, `/{id}`, `/{id}/edit`, and `/{id}/delete`, plus form, filter, and view DTOs.
- A migration, `mapper/{module}/{E}Mapper.xml`, the `list`, `form`, `detail`, and `edit` templates, four specs, `{E}CrudTest`, and `{E}ScreenTest`.

## Slots

- This command leaves no slot, and `mvn -o verify` passes right after it.
- Change validation, defaults, or ordering by editing the generated code.

## Example

```bash
cozy add crud memo --entity Memo --label 메모 --icon notes --field title:text!:제목 --field body:longtext:본문 --field pinned:bool:고정 --publish created
```

## Failures

| Message contains | Action |
| --- | --- |
| `필수 text 필드` | Add a required text field such as `--field title:text!:제목`. |
| `Tabler Icons에 없습니다` | Pick an icon name from the icon catalog. |
| `cozy-starter-events가 필요합니다` | Drop `--publish`, or run [enable-events](../enable-events/PLAYBOOK.md) first. |
| `이미 있고 내용이 다릅니다` | Use a different `--entity`; the command never overwrites an existing file. |

## Next

- Add filtered reads or summaries with [add-query](../add-query/PLAYBOOK.md).
- Add other writes with [add-command](../add-command/PLAYBOOK.md).
- Edit the generated templates with the [screens](../screens/PLAYBOOK.md) rules.
