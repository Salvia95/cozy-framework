# cozy add screen

Creates one read-only screen with its controller, view record, template, spec, and optional navigation entry.

## When to use

- Use it for a dashboard, summary, or detail screen that only displays values.

## When NOT to use

- Use [add-crud](../add-crud/PLAYBOOK.md) instead when the screen needs a list with forms.

## Syntax

```text
cozy add screen <모듈> <화면> --pattern overview|details --title 제목 [--field …] [--nav 라벨:아이콘]
```

- `overview` shows `int` and `long` fields as stat cards and other fields as a summary card.
- `details` shows every field in one information card.
- A screen named `index` maps to `/{module}`; any other name maps to `/{module}/{screen}`.
- A field is `name:kind[!][:label]`; kind is `text`, `longtext`, `int`, `long`, `bool`, `date`, or `instant`, `!` marks it required, and the label is the on-screen name (quote it when it has spaces: `--field "dueOn:date:마감 날짜"`).
- `--nav label:icon` adds a navigation entry.

## Creates

- `api/{M}{S}Controller`, `api/dto/{S}View`, `templates/{module}/api/{screen}.html`, and `specs/{module}-{screen}.yml` with `default` and `empty` states.

## Slots

- `view-model` (controller): build the `{S}View` from Finder calls and add it to the model as `view`.
- Run [link](../link/PLAYBOOK.md) before calling another module's Finder, and use `cozy describe` to find Finder methods.

## Example

```bash
cozy add screen dashboard index --pattern overview --title 대시보드 --field total:long:전체 --nav 대시보드:dashboard
```

## Failures

| Message contains | Action |
| --- | --- |
| `Tabler Icons에 없습니다` | Pick an icon name from the icon catalog. |
| `이미 있고 내용이 다릅니다` | Use a different screen name. |

## Next

- Fill `view-model`, then run `cozy check`.
- Edit the template with the [screens](../screens/PLAYBOOK.md) rules.
