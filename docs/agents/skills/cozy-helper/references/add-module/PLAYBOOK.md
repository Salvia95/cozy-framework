# cozy add module

Creates an empty feature module that uses no other module.

## When to use

- Use it when a new feature needs its own module before its first screen, event, or DTO.

## When NOT to use

- Skip it before `cozy add crud` or `cozy add screen`; both create the module when it is missing.

## Syntax

```text
cozy add module <모듈> [--label 라벨]
```

- The module name is lowercase letters and digits starting with a letter (`memo`, `voc2`).
- The label is the on-screen name; without it the module name is used.

## Creates

- `{module}/package-info.java` with `@ApplicationModule(allowedDependencies = {})`.
- The `spi/` packages are created later by the first command that puts something in them.

## Slots

- This command leaves no slot.

## Example

```bash
cozy add module activity --label 활동
```

## Failures

| Message contains | Action |
| --- | --- |
| `영문 소문자로 시작하는 소문자·숫자여야 합니다` | Use a lowercase name such as `voc2`. |
| `예약어라 모듈 이름으로 쓸 수 없습니다` | Pick a name that is not `cozy`, `fragments`, or a Java keyword. |

- Running it for an existing module prints `바뀐 것 없음` and changes nothing.

## Next

- Add screens with [add-crud](../add-crud/PLAYBOOK.md) or [add-screen](../add-screen/PLAYBOOK.md).
- Use another module with [link](../link/PLAYBOOK.md).
