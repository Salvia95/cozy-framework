# cozy add fragment

Publishes a fragment that other modules' screens can include, and registers it in `cozy/fragments.yml`.

## When to use

- Use it when another module's screen (such as a dashboard) shows values owned by this module.

## When NOT to use

- Do not use it for a piece used only inside this module; put that in `templates/{module}/internal/` by hand.

## Syntax

```text
cozy add fragment <모듈> <이름> --param 이름:타입 … --purpose "용도"
```

- The name is lowercase with hyphens (`recent-activity`), and the fragment itself is named in camelCase (`recentActivity`).
- A parameter type is a field kind, a record in this module's `spi/dto` (`item:ActivityItem`), or a list of one (`items:list:ActivityItem`).

## Creates

- `templates/{module}/spi/{name}.html` with the `th:fragment` signature and one card.
- A `cozy/fragments.yml` entry with the signature, purpose, and `default` and `empty` samples.

## Slots

- `fragment-body` (template): render the card from the parameters only, never from the caller's model.
- `fragment-samples` (manifest) appears only when a sample value could not be generated; replace each `null` with an example value.

## Example

```bash
cozy add fragment activity recent-activity --param items:list:ActivityItem --purpose "최근 활동 목록"
```

- Another module includes it with `<div th:replace="~{activity/spi/recent-activity :: recentActivity(${view.items})}"></div>` after `cozy link <module> --uses activity`.

## Failures

| Message contains | Action |
| --- | --- |
| `.spi.dto.`, `가 없습니다` | Create the record first with [add-dto](../add-dto/PLAYBOOK.md). |
| `HTML 요소 이름과 같아 쓸 수 없습니다` | Prefix the name with the module (`memo-form`). |

## Next

- Run [link](../link/PLAYBOOK.md) in each module that includes the fragment.
- Follow the [screens](../screens/PLAYBOOK.md) rules for the markup.
