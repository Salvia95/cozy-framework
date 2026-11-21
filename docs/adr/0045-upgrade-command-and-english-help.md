# ADR-0045 판 올리기는 `cozy upgrade` 하나로, 명령 설명은 영어로

- 상태: 승인 (2026-11-21, 사용자 결정 "응 개정안 쓰고 진행해줘", 영어 범위는 "help + recipes")
- 출처: 사용자 지적 "업데이트 명령인 cozy update가 [--to <판>]이라 안내되는데 cozy upgrade --check는 인자를 받지 않는다고 하는 등 명령어간 일관성이 없는데", "명령어들의 설명이 너무 ai 스멜이 짙은 한글이 포함되어 있어 오히려 방해가 돼, 영어로 통일해줘"
- 바꾸는 결정:
  - ADR-0042 §4 "명령": 새 판 보기(`cozy update`)를 `cozy upgrade --check`로 옮긴다. `cozy update`는 2.x 동안 별칭이다(§1).
  - ADR-0042 §2 "릴리스 워크플로가 그 릴리스 하나만 담은 `cozy-channel.json`": 그 릴리스와 이전에 게시한 릴리스들을 담는다(§2).
  - 명령 설명의 언어: `cozy help`(cozy-cli와 번들 스크립트)와 `cozy recipes`의 명령 형식·설명은 영어다(§3).
- 바꾸지 않는 것: 채널 주소와 설정(ADR-0042 §1, §3), 색인 형식(`schema: 1`), 호환이 깨지는 판은 `--to`로 적어야 올리는 것, 낮은 판으로 가지 않는 것, 설치 방식과 이전 판 보존(ADR-0042 §4, §5). 오류와 결과 메시지는 한국어다(에이전트 문서와 워크벤치가 글자 그대로 쓴다, ADR-0034). 워크벤치 화면의 문구(ADR-0038)

## 배경

- 새 판을 보는 `cozy update`와 올리는 `cozy upgrade`는 뜻이 거의 같은 말이다. 안내 문구는 "업데이트 채널", "업데이트를 받을 곳"이라고 하므로, 업데이트하려는 사람은 `cozy update`를 친다. 그런데 `cozy update`는 아무것도 바꾸지 않는다.
- 다른 cozy 명령은 같은 명령에 `--dry-run`을 붙여 미리 본다. 판 올리기만 미리 보기가 다른 명령이다. `cozy upgrade --dry-run`은 거절한다.
- 옵션이 둘로 나뉘었다. `--json`은 `update`에만, `--to`는 `upgrade`에만 있다. "3.0.0으로 올리면 무엇이 바뀌나"를 한 명령으로 볼 수 없다.
- 거절 문구가 셋이다("인자를 받지 않습니다", "[--to <판>]로 씁니다", "--json이 없습니다"). 어느 것도 두 명령의 사용법을 함께 보이지 않는다. 사용자는 `cozy update --to`와 `cozy upgrade --check`를 섞어 쳤다.
- GitHub 채널(`releases/latest/download/cozy-channel.json`)은 최신 릴리스 하나만 담는다. 판을 건너뛰면(2.1.0 → 2.3.0) 건너뛴 판의 앱에서 돌릴 명령(2.2.0의 `cozy migrate behaviors`)이 보이지 않는다.
- `cozy help`와 `cozy recipes`의 설명은 한국어 문장이 길고 명사를 늘어놓아("앱의 지도: 모듈, 의존, spi 계약, …") 명령을 찾는 데 방해가 된다. 명령 이름과 옵션은 영어인데 설명과 자리표시자(`<모듈>`, `<판>`)만 한국어라 한 줄 안에서 섞인다. 번들 스크립트의 도움말은 cozy-cli의 명령을 한 줄에 이어 적었고 일부가 낡았다(`add`의 `behavior`, `field`가 빠졌다).

## 결정

### 1. 판 올리기는 `cozy upgrade` 하나다

```text
cozy upgrade [--check] [--to <version>] [--json]
```

| 실행 | 하는 일 |
| --- | --- |
| `cozy upgrade` | 주 버전이 같은 가장 새 판을 받아 확인하고, 이 명령이 끝나면 설치한다(지금과 같다) |
| `cozy upgrade --to 3.0.0` | 적은 판으로 올린다. 호환이 깨지는 판은 이렇게 적어야 올린다(지금과 같다) |
| `cozy upgrade --check` | 아무것도 받거나 바꾸지 않고 지금 판, 최신 판, 새 판마다 날짜·요약·호환 깨짐·앱에서 돌릴 명령, 올릴 판을 보인다(지금의 `cozy update`) |
| `cozy upgrade --check --to 3.0.0` | 그 판까지의 새 판과 앱에서 돌릴 명령, 그 판으로 올릴 수 있는지를 보인다 |
| `cozy upgrade --check --json` | 지금의 `cozy update --json`과 같은 필드에 `target`(올릴 판)을 더한다 |

- `--dry-run`은 `--check`와 같다. 다른 cozy 명령과 같은 말로 미리 본다.
- `--json`은 `--check`(또는 `--dry-run`)와 함께 쓴다. 설치하는 명령은 사람이 읽는 출력만 낸다.
- **`cozy update`는 별칭이다.** `cozy upgrade --check`와 같이 동작하고, 같은 인자(`--to`, `--json`)를 받는다. 오류 출력에 한 줄을 보인다: `[cozy] cozy update는 cozy upgrade --check로 바뀌었습니다. 3.0.0에서 없어집니다.` 2.x로 만든 스크립트와 플레이북이 그대로 돈다. 3.0.0에서 없앤다.
- **거절 문구는 하나다.** 모르는 인자, 빠진 판, `--check` 없는 `--json`은 모두 사용법 전체를 보인다: `cozy upgrade [--check] [--to <version>] [--json]로 씁니다(받은 인자: …). 새 판은 cozy upgrade --check로 봅니다.`
- **새 판 알림**은 `cozy upgrade --check`를 가리킨다: `[cozy] 새 버전이 있습니다: 2.4.0. cozy upgrade로 올립니다 (바뀌는 것: cozy upgrade --check)`.
- 설치를 맡긴 뒤의 출력, 종료 코드, 실패로 닫는 경우는 지금과 같다.

### 2. GitHub 채널은 이전 릴리스도 담는다

```bash
cozy channel release <릴리스 폴더> distribution/release/channel.yml --previous <이전 색인 폴더>
```

- 릴리스 워크플로가 게시한 릴리스들의 `cozy-channel.json`을 `--previous` 폴더에 받는다(없는 릴리스는 건너뛴다. 2.1.0부터 있다).
- `cozy channel release`는 이 릴리스를 맨 앞에 두고, 이전 색인들의 릴리스를 판마다 하나씩 잇는다. 이전 릴리스의 파일 경로는 그 릴리스의 자산을 가리키게 바꾼다(`../../download/v2.3.0/cozy-2.3.0-windows-x64.msi`). `releases/latest/download/`에서 읽든, 넘겨받은 `releases/download/v{판}/`에서 읽든 같은 파일이 된다.
- 그래서 판을 건너뛰어도 `cozy upgrade --check`와 `cozy upgrade`가 사이 판들의 앱에서 돌릴 명령을 보인다.
- `cozy channel add`는 받은 색인의 맨 앞 릴리스(그 폴더에 파일이 있는 판)만 채널에 더한다. 이전 릴리스는 각자의 릴리스 폴더로 더한다. 맨 앞 릴리스의 파일이 폴더 안의 이름이 아니면 거부한다.
- 색인 형식은 그대로다(`schema: 1`). 2.3.0 이하의 cozy도 새 색인을 읽는다.

### 3. 명령 설명은 영어다

- **`cozy help`**: cozy-cli의 도움말을 영어로 쓴다. 명령마다 한 줄, 동사로 시작하는 짧은 설명이다. 자리표시자도 영어다(`<module>`, `<version>`, `<template>:<line>`).
- **번들 스크립트**(`cozy`, `cozy.ps1`): `cozy help`는 번들 명령(`install`, `new`, `version`, `help`)을 영어로 보인 뒤, cozy-cli를 실행할 수 있으면 그 도움말을 잇는다. cozy-cli의 명령을 스크립트에 다시 적지 않는다(낡지 않게).
- **`cozy recipes`**: 레시피의 명령 형식(`usage`)과 하는 일(`does`), 빈칸 설명(`slots`), 목록의 머리말을 영어로 쓴다. 워크벤치의 코드 생성 화면도 레시피 설명을 영어로 보인다. 레시피의 제목·분류·폼 칸 이름(`title`, `group`, `params[].label`)은 워크벤치 화면의 말이라 한국어 그대로다.
- **그대로 두는 것**: 오류·결과·남은 일 메시지(한국어), 워크벤치 화면 문구, `docs/conventions/`(한국어 문서). 문서가 명령 형식을 옮겨 적은 곳은 영어 형식으로 맞춘다.
- 에이전트 문서의 플레이북은 레시피의 명령 형식을 글자 그대로 담는다(`AgentDocsTest`). 플레이북의 Syntax를 영어 형식으로 바꾸므로 한국어는 메시지만 백틱 안에 남는다(ADR-0034의 원칙에 더 맞다).

## 정할 것

| | 선택지 | 권장 |
| --- | --- | --- |
| 1 | 하나로 합칠 이름: `cozy upgrade --check` / `cozy update`(올리기는 `--apply`) / 새 이름 | `cozy upgrade --check`. 다른 명령의 `--dry-run`과 같은 꼴 |
| 2 | `cozy update`: 2.x 동안 별칭, 3.0.0에서 없앰 / 바로 없앰 | 별칭 |
| 3 | GitHub 색인의 이전 릴리스: 게시한 릴리스를 모두 / 바로 앞 릴리스의 색인만 이어 붙임 | 모두(빠진 판이 생기지 않는다) |
| 4 | 영어 범위: help + recipes / help만 / 메시지까지 | help + recipes (사용자 결정) |

## 단계

| 마일스톤 | 완료 기준 |
| --- | --- |
| M56 `cozy upgrade --check` | §1의 표가 그대로 동작한다(`file:`·로컬 HTTP 채널). `cozy update`는 같은 출력에 바뀐 이름 한 줄. 거절 문구 하나. 알림 문구. `cozy channel release --previous`의 누적 색인과 경로, `cozy channel add`의 맨 앞 릴리스. 워크플로, MSI 검증 스크립트, 문서(channel.md, install-and-update.md, cli.md), 업그레이드 플레이북 |
| M57 영어 설명 | `cozy help`와 번들 스크립트의 `cozy help`, `cozy recipes`(글과 `--json`의 `usage`·`does`·`slots`)가 영어다. 도움말이 모든 명령을 담는지와 레시피 형식이 플레이북·문서와 같은지 테스트한다. 워크벤치 코드 생성 화면 확인 |

## 위험

- **바뀐 이름**: 2.x에서 `cozy update`를 쓰는 스크립트와 습관은 별칭으로 계속 돈다. 3.0.0의 올리는 절차에 없어진다고 적는다.
- **이전 릴리스의 경로**: GitHub의 자산 주소 모양(`releases/download/v{판}/{파일}`)에 기댄다. 모양이 바뀌면 이전 판으로 `--to`를 적을 때만 실패하고(SHA-256과 404는 실패로 닫는다), 최신 판으로 올리는 것은 영향이 없다.
- **워크벤치의 영어 설명**: 한국어 화면에 레시피 설명만 영어로 보인다. 명령 형식과 같은 줄에 있어 읽기는 오히려 낫다고 본다. 화면 확인에서 다시 본다.

## M56 (2026-11-21, 사용자 지시 "응 개정안 쓰고 진행해줘")

- 시작할 때 최신 GA를 확인해 Spring Boot 4.2.0과 Modulith 2.2.0으로 올렸다(ADR-0017). 이벤트 계약 테스트가 Modulith 2.2의 포기한 발행(`ABANDONED`) 메서드를 잡아 SQLite 저장소에 구현했다.
- `cozy upgrade --check [--to <version>] [--json]`과 `--dry-run`. `--to`가 있으면 그 판까지의 새 판과 올리는 명령(`cozy upgrade --to <판>`)을 보이고, 낮은 판과 채널에 없는 판은 `cozy upgrade`와 같이 거절한다. `--json`에 `target`을 더했다.
- `cozy update`는 같은 인자로 `cozy upgrade --check`를 하고 오류 출력에 바뀐 이름 한 줄을 보인다. 번들 스크립트는 그대로 cozy-cli에 넘긴다(사용법에 없는 옛 이름은 `CozyCli.ALIASES`).
- 거절은 `cozy upgrade [--check] [--to <version>] [--json]로 씁니다(받은 인자: …). 새 판은 cozy upgrade --check로 봅니다.` 하나다. 판 형식이 틀린 `--to`도 같다.
- `cozy channel release --previous <폴더>`: 이전 색인들의 릴리스를 판마다 하나씩 잇고 경로를 `../../download/v{판}/{파일}`로 바꾼다. 같은 판을 다르게 적은 색인, 없는 폴더는 실패로 닫는다. `cozy channel add`는 맨 앞 릴리스만 더하고 이전 릴리스는 알리기만 한다.
- 릴리스 워크플로가 `cozy-channel.json`이 있는 게시 릴리스마다 색인을 받아 `--previous`로 준다(자산 목록에 있는데 받지 못하면 실패한다).
- 문서: channel.md, cli.md, install-and-update.md, windows-verification.md, 번들 README, 업그레이드 플레이북, MSI 검증 스크립트.

## M57 (2026-11-21, 사용자 결정 "help + recipes")

- `cozy help`: 영어로 다시 썼다. 묶음(App, Screen behavior, Upgrading apps, Development tool)마다 명령 한 줄과 동사로 시작하는 설명이다. 자리표시자는 영어다(`<module>`, `<template>:<line>`, `<version>`).
- 번들 스크립트(`cozy`, `cozy.ps1`): 머리의 도움말을 영어로 쓰고 번들 명령(`install`, `new`, `version`, `help`)만 적었다. `cozy help`는 그 뒤에 cozy-cli의 도움말을 잇는다(jar와 java가 있을 때). 큰따옴표 안의 예시 값(`-DappName="VOC 도우미"`)은 한글 그대로다.
- `cozy recipes`: 명령 형식, 하는 일, 빈칸, 머리말을 영어로 썼다. `attach`, `remove behavior`, `rename behavior`, `cozy channel`의 형식도 같다. 이 형식을 옮겨 적은 플레이북의 Syntax와 문서(cli.md의 형식, workbench.md, events.md)를 맞췄다.
- 워크벤치 코드 생성 화면은 레시피 설명을 영어로 보인다. 빈칸이 없는 레시피(`none …`)는 "미구현 항목: 없음"으로 보인다.
- 테스트: 도움말과 `cozy recipes`에 한글이 없다(`\p{IsHangul}`), 도움말이 모든 동작을 담는다, 번들 도움말이 영어이고 cozy-cli의 도움말을 잇는다.
- 그대로 둔 것: 오류·결과·"다음:" 안내 메시지의 한국어 자리표시자(`cozy add listener <모듈> --on …`). 메시지는 이번 범위 밖이다.
