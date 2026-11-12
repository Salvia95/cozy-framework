# Screen layout

Rules for choosing a screen's pattern and width and for placing its buttons.

## When to use

- Use it before writing or restructuring a screen, when you must pick a pattern, split the body into columns, or place a button.

## When NOT to use

- Do not use it to create a screen's files; use [add-crud](../add-crud/PLAYBOOK.md) or [add-screen](../add-screen/PLAYBOOK.md) first.

## Syntax

- This playbook has no command; `cozy check` enforces button order (R15) and the runtime check measures widths.

## Creates

- Write the screen's purpose in one sentence first, because the purpose picks the type and the type fixes the pattern, width, and button places.

| Purpose | Type | Pattern | Width |
| --- | --- | --- | --- |
| Find and pick one of many | List | `list-with-filter` | `container-xl` |
| Make one new item | Create | `form-page` | centered 48rem |
| Read one item | Details | `details` | `container-xl` |
| Change one item | Edit | `form-page` | centered 48rem |
| Scan the state of many items | Overview | `overview` | `container-xl` |
| Read items side by side while browsing | List and detail | `master-detail` | `container-xl` |

- Put exactly one type on a screen, and never put the detail view and the edit form side by side.
- Put the title at the top left, screen-wide actions at the top right, and form actions at the bottom right as [Cancel] [Primary].
- Put one primary button (`btn-primary`) per button group, as the last button, and never inside a table.
- Put delete on the details header as a secondary button to the left of edit, and use `deleteConfirm` so it opens a dialog.
- Show the path above the title on details, create, and edit screens instead of a back-to-list button.
- Split a details body into a wide column (`col-lg-8`) for content and a side column (`col-lg-4`) with a `datagrid` for properties.
- Put a stat row (`row row-deck row-cards` with `stat`) above the cards on an overview, and keep stat cards colorless.
- Ask the user before inventing a layout that no type covers, such as a wizard.

## Slots

- This playbook fills no slot; the pattern draws the header, body, and flash area around your content.

## Example

```html
<div id="memo-actions" class="btn-list">
  <th:block th:replace="~{cozy/component/delete-confirm :: deleteConfirm('memo-delete', '메모', ${memo.title}, '/memo/' + ${memo.id} + '/delete')}"></th:block>
  <a class="btn btn-primary" th:href="@{/memo/{id}/edit(id=${memo.id})}">수정</a>
</div>
```

## Failures

| Violation | Action |
| --- | --- |
| `[R15]` on a `btn-list` | Keep one primary button and move it to the end of the group. |
| A page frame such as `container-xl` written by hand | Remove it and pass the content to the pattern. |

## Next

- Read [screen-states](../screen-states/PLAYBOOK.md) to design every state of the screen.
