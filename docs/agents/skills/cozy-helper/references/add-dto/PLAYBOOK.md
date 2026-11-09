# cozy add dto

Creates a record in `spi/dto` that other modules can receive.

## When to use

- Use it for a value returned by a Finder or passed to a public fragment.

## When NOT to use

- Do not use it for a screen-only shape; screen DTOs live in `api/dto` and are created by the screen commands.

## Syntax

```text
cozy add dto <모듈> <Record> --field 이름:종류[:설명] …
```

- A kind maps to a Java type: `text`→`String`, `int`→`int`, `long`→`long`, `bool`→`boolean`, `date`→`LocalDate`, `instant`→`Instant`.
- A component can be another record of the same module (`latest:ActivityItem`) or a list of one (`items:list:ActivityItem`).
- Never mark a component with `!`; a record has no required flag.
- The description becomes the component's `@param` text.

## Creates

- `{module}/spi/dto/{Record}.java`, and `spi/dto/package-info.java` when it is missing.

## Slots

- This command leaves no slot.

## Example

```bash
cozy add dto activity ActivityItem --field "message:text:무엇이 있었는지" --field "at:text:언제 (yyyy-MM-dd HH:mm)"
```

## Failures

| Message contains | Action |
| --- | --- |
| `필수 여부가 없어 !를 쓰지 않습니다` | Remove the `!`. |
| `.spi.dto.`, `가 없습니다` | Create the nested record first. |
| `이미 있고 내용이 다릅니다` | Use another name; CRUD already owns `{E}Item` and `{E}Page`. |

## Next

- Return the record with [add-query](../add-query/PLAYBOOK.md) or pass it to [add-fragment](../add-fragment/PLAYBOOK.md).
