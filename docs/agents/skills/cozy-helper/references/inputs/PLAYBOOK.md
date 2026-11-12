# Inputs

Rules for choosing the input component for each value in a form.

## When to use

- Use it when you add a form field, a value chosen from a fixed set, or several rows of the same shape.

## When NOT to use

- Do not use it to add a field to an existing CRUD set; use [add-field](../add-field/PLAYBOOK.md) first and then adjust the input here.

## Syntax

- This playbook has no command; the components are listed in `cozy/fragments.yml` and shown in the dev catalog (`/cozy/dev/catalog`).

## Creates

| Value | Component |
| --- | --- |
| A value the user types (text, long text, number, date, email) | `field(name, label, type)` |
| A single on or off value | `field(name, label, 'checkbox')` or `field(name, label, 'switch')` |
| One value from a list | `selectField(name, label, options)` |
| One value from a few options shown side by side | `radioGroup(name, label, options)` |
| Several values from a list | `checkboxGroup(name, label, options)` |
| Several rows of the same shape | `inputGrid(label, grid)` |

- Never add a selection type to `field`; use a selection component instead.
- Build options with `CozyOptions.of(Status.class, labelFunction)` or `CozyOptions.of(orderedMap)` in the controller.
- Build a grid with `CozyGrid.Column` for each column, choosing text, number, date, email, select, radio, or checkbox, and fill it with `CozyGrid.bind(property, columns, rows, errors)`.
- Handle the grid's add, duplicate, and delete buttons with `CozyGrid.RowAction.parse(row)` and redraw the same screen without validating.
- Ask the user before building a type-to-filter combobox, because no component exists for it yet.

## Slots

- Fill the options or grid in the `view-model` or controller slot, and keep the form property a single value for select and radio and a list for checkbox groups and grids.

## Example

```java
model.addAttribute("statusOptions", CozyOptions.of(Status.class, (s) -> s == Status.TODO ? "할 일" : "완료"));
model.addAttribute("fieldsGrid", CozyGrid.bind("items", List.of(
        CozyGrid.Column.text("title", "이름").asRequired(),
        CozyGrid.Column.select("kind", "종류", kindOptions),
        CozyGrid.Column.checkbox("required", "필수")), form.getItems(), errors));
```

## Failures

| Message contains | Action |
| --- | --- |
| `선택지는 select와 radio 열에만` | Give options to select and radio columns only. |
| `행 작업 값` | Submit only the `row` values that the grid buttons send. |

## Next

- Run `cozy check`, then `mvn -o verify`, which renders every spec state.
