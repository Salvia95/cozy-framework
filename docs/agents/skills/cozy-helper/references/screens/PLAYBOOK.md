# Screens

Rules for editing generated templates, filling `fragment-body`, and fixing R-rule violations.

## When to use

- Use it when editing a template under `templates/{module}/` or when `cozy check` reports an `[R…]` violation.

## When NOT to use

- Do not create a screen by hand; use [add-crud](../add-crud/PLAYBOOK.md) or [add-screen](../add-screen/PLAYBOOK.md).

## Syntax

- This playbook has no command; it holds the markup rules that `cozy check` and `mvn -o verify` enforce.

## Creates

- Look for a matching pattern or component in the dev catalog (`/cozy/dev/catalog`) and `cozy/fragments.yml` before writing new markup.
- Wrap every screen in a pattern: `list-with-filter` for lists, `form-page` for create and edit, `details` for detail, `overview` for summaries, and `master-detail` for list plus detail.
- Never build page frames such as `container-xl` by hand; the pattern draws the header, body, and flash messages.
- Use Tabler markup: `list-group list-group-flush` inside cards, `datagrid` for details, `stat` for numbers, and `emptyState` for no results.
- Insert icons with `<svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>`.

## Slots

- A fragment under `spi/` or `internal/` reads only its parameters and its own `th:each` or `th:with` variables, never the caller's model.

## Example

- Put one primary button per group at the far right, and order form buttons as [Cancel] [Primary] at the bottom right.
- Pass a tone instead of a color class (`badge('고정', 'neutral')`), and never write `bg-*`, `text-red`, or `btn-outline-danger`.
- Label buttons as object plus verb (`메모 만들기`) and report results with `CozyFlash.success("메모를 만들었습니다")`.
- Show `emptyStateAction` when nothing exists yet and `noResults` when a filter matches nothing.

## Failures

| Violation | Action |
| --- | --- |
| Reference to another module's template | Reference only its `spi/` fragments and declare the module with [link](../link/PLAYBOOK.md). |
| Reference against the layer direction | Reference only in the direction api → spi → internal within a module. |
| Dynamic reference such as `~{${...}}` | Replace it with a literal template reference. |
| `th:each` and `th:replace` on one element | Move `th:each` to a surrounding `<th:block>`. |
| Fragment named like an HTML element (`form`) | Prefix the name with the module (`memoForm`). |
| Inline `<script>`, `onclick=`, or `style=` | Remove it; the security policy blocks inline code. |

## Next

- Run `cozy check`, then `mvn -o verify`, which also renders every spec state.
