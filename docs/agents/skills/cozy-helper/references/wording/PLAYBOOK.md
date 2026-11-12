# Wording

Rules for button labels, titles, messages, and terms shown on screens.

## When to use

- Use it when you write or change any text a user sees: a button, a title, a flash, an empty state, an error, or a column name.

## When NOT to use

- Do not use it for the messages that cozy commands print; those come from the command.

## Syntax

- This playbook has no command; write screen text in Korean polite declarative form (`-습니다`, `-세요`).

## Creates

| Text | Rule | Example |
| --- | --- | --- |
| Button | Object and verb; the edit form's primary button is save | `메모 만들기`, `저장`, `삭제`, `조건 지우기` |
| Title | Object for a list, object and verb for create and edit, the item name for details | `메모`, `메모 만들기`, `메모 수정`, `회의록 정리` |
| Result | Object and past tense | `메모를 만들었습니다` |
| Nothing changed | Fixed text | `바뀐 내용이 없습니다` |
| Empty state | What is missing and what to do next | `메모가 없습니다. 첫 메모를 만들어 보세요.` |
| Field error | What the user should do | `제목을 입력하세요` |
| Delete dialog | Object and delete, the item name, and that it cannot be undone | `메모 삭제` |

- Use one term for one concept across the whole app.
- Use common UI words and common development terms, and never coin a new word.
- Use the same label everywhere for the same action, such as the list button, the empty-state button, and the create form's primary button.

## Slots

- Put user-facing text in templates and flash calls, and keep identifiers out of it.

## Example

```java
flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 만들었습니다"));
```

## Failures

| Problem | Action |
| --- | --- |
| A message that names the system instead of the user's action | Rewrite it as the object and the verb the user performed. |
| Two words for one concept | Pick the one already used in the app and replace the other. |

## Next

- Run `mvn -o verify` to render every state with the new text.
