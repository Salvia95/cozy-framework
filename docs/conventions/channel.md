# 개발 도구 업데이트 채널

설치한 개발 도구(MSI나 오프라인 번들)를 새 판으로 올릴 때 새 판을 받아 오는 곳이다. 결정과 이유는 ADR-0042에 있다. 앱의 자체 업데이트(`cozy.update.*`, [distribution.md](distribution.md))와는 다르다.

- **채널**: 색인 파일 `cozy-channel.json`의 주소 하나다. 색인과 파일은 정적 파일이라 서버의 API를 쓰지 않는다. 그래서 파일 서버, 사내 웹 서버, GitLab, GitHub 어디에나 둘 수 있다.
- **흐름**: 관리자가 릴리스를 받아 채널에 더한다(`cozy channel add`). 각 PC는 채널을 한 번 정한다(`cozy channel set`). 그 뒤 새 판을 보고(`cozy update`) 올린다(`cozy upgrade`. 다음 판에서 더한다).
- **네트워크**: `cozy channel`의 명령은 네트워크를 쓰지 않는다. `set`은 주소의 형식만 본다. 채널을 읽는 것은 `cozy update`와 하루 한 번의 새 판 알림이다.

## 채널 주소

| 서버 | 주소 예 |
| --- | --- |
| 파일 서버(UNC) | `\\fileserver\share\cozy\index\cozy-channel.json` |
| 파일 서버(연결한 드라이브, 마운트) | `Z:\cozy\index\cozy-channel.json`, `/mnt/share/cozy/index/cozy-channel.json`, `file:///mnt/share/cozy/index/cozy-channel.json` |
| 사내 웹 서버 | `https://intra.example/cozy/index/cozy-channel.json` |
| GitLab Generic Package Registry | `https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json` |
| GitHub Releases(인터넷이 되는 PC) | `https://github.com/Salvia95/cozy-framework/releases/latest/download/cozy-channel.json` |

- 주소는 `cozy-channel.json`으로 끝난다. `cozy channel add`로 만든 채널이면 `<채널 폴더>/index/cozy-channel.json`이다.
- 형식은 UNC 경로, 절대 경로, `file:`, `http:`, `https:`다. 그 밖의 형식(`ftp:`, 상대 경로)은 거부한다.

## `cozy channel set·show`

```bash
cozy channel set '\\fileserver\share\cozy\index\cozy-channel.json'
cozy channel set https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json --header PRIVATE-TOKEN
cozy channel show            # 채널, 출처(환경 변수나 설정 파일), 머리 이름, 토큰이 있는지
cozy channel show --json     # {"address", "source", "header", "token", "config"}
```

- **설정 파일**: 설치 폴더 밖에 둔다. 업그레이드하거나 지워도 남는다.
  - Windows: `%APPDATA%\Cozy\cozy.properties`
  - 그 밖: `$XDG_CONFIG_HOME/cozy/cozy.properties`, 없으면 `~/.config/cozy/cozy.properties`
- **키**: `channel`(주소), `channel.header`(머리 이름), `channel.token`(토큰). `set`은 `channel`과 `channel.header`만 쓴다. `--header` 없이 다시 `set`하면 머리 이름을 지운다.
- **환경 변수**: `COZY_CHANNEL`이 있으면 설정 파일의 `channel`보다 앞선다. 여러 PC에 같은 채널을 둘 때 쓴다.
- **인증**: 머리 이름을 정하지 않으면 `Authorization: Bearer <토큰>`이다. GitLab의 개인 토큰은 `--header PRIVATE-TOKEN`이다.
  - 토큰은 환경 변수 `COZY_CHANNEL_TOKEN`이 설정 파일의 `channel.token`보다 앞선다. 명령줄 인자로는 받지 않는다(셸 기록에 남지 않게).
  - 설정 파일의 토큰은 평문이다. 환경 변수를 권한다.
  - `show`와 그 밖의 출력은 토큰을 보이지 않는다. 있는지만 보인다.
- **채널을 정하지 않으면**: 업데이트를 확인하지 않는다. 기본 채널은 없다(폐쇄망에서 인터넷 주소의 시간 초과를 기다리지 않게).

## `cozy update`와 새 판 알림

```bash
cozy update          # 지금 판, 최신 판, 새 판마다 날짜·요약·호환 깨짐·앱에서 돌릴 명령, cozy upgrade가 올릴 판
cozy update --json   # {"current", "latest", "channel", "upToDate", "upgrade", "breaking", "releases": [{"version", "date", "breaking", "summary", "appSteps"}]}
```

- 채널 색인을 읽어 비교만 한다. 아무것도 받거나 바꾸지 않는다. 어디서나 실행한다.
- **지금 판**: 설치한 cozy 명령(설치 폴더의 `bin/cozy`, `bin\cozy.cmd`, 번들 폴더의 `cozy`)이 설치 폴더를 `COZY_HOME`으로 알려 주고, 그 폴더의 `COZY_VERSION`이 지금 판이다. 설치 폴더를 모르거나 개발 판(SNAPSHOT)이면 비교하지 않고 종료 코드 2로 끝낸다.
- **올릴 판**(`upgrade`): 지금 판과 주 버전이 같은 가장 새 판. 주 버전이 바뀐 판(`breaking`)은 `cozy upgrade --to <판>`으로 적어야 올린다. `--json`의 `breaking`은 그런 판 가운데 최신 판이다.
- **채널이 더 낮으면**: 채널의 최신 판이 지금 판보다 낮으면 알리기만 한다. 낮은 판으로는 올리지 않는다.
- **실패로 닫는 것**(종료 코드 2): 채널을 정하지 않음, 색인이 없음(파일이 없거나 HTTP 404), 거부(HTTP 401, 403), 연결하지 못함, 읽을 수 없는 색인(모르는 `schema`, 빠진 필드).
- **웹 서버**:
  - 토큰은 채널 주소와 같은 서버(scheme, 호스트, 포트)에만 보낸다. 다른 서버로 넘겨주면(GitHub의 자산 저장소 등) 토큰 없이 따른다. 넘겨주기는 5번까지, `https`에서 `http`로는 따르지 않는다.
  - 인증서: JDK의 인증서(`cacerts`)에 더해 Windows는 Windows 인증서 저장소(`Windows-ROOT`)를 믿는다. 사내 CA로 서명한 서버도 그 CA가 Windows에 들어 있으면 따로 설정하지 않는다.
  - 프록시: 시스템 설정(Windows는 인터넷 옵션의 프록시)을 따른다.
  - 연결은 10초, 응답은 60초까지 기다린다.
- **새 판 알림**: 채널을 정했으면 앱에 쓰는 명령(`describe`, `check`, `recipes`, `add`, `link`, `enable`, `sync`, `set`, `migrate`) 뒤에 하루에 한 번 채널을 확인한다. 새 판이 있으면 오류 출력의 마지막에 한 줄을 보인다.

  ```text
  [cozy] 새 버전이 있습니다: 2.1.0. cozy upgrade로 올립니다 (바뀌는 것: cozy update)
  [cozy] 호환이 깨지는 새 버전이 있습니다: 3.0.0. 바뀌는 것과 앱에서 돌릴 명령은 cozy update로 봅니다
  ```

  - `--json` 출력, `cozy update`, `cozy channel`, 도움말 뒤에는 확인하지 않는다. 명령의 출력과 종료 코드는 바뀌지 않는다.
  - 확인은 3초까지 기다린다. 실패하거나 늦으면 아무것도 보이지 않고 다음 날 다시 확인한다. 자동으로 설치하지 않는다.
  - 확인한 날은 설정 파일 옆의 `update-check.properties`(`checked`, `latest`)에 적는다. `cozy update`도 적는다. 지우면 다음 명령이 다시 확인한다.
  - 알림을 끄려면 채널을 정하지 않는다(설정 파일의 `channel`을 지운다).

## `cozy channel add` (관리자)

```bash
# 인터넷이 되는 PC에서 릴리스 자산(MSI, 두 번들, SHA256SUMS, cozy-channel.json)을 받아 폴더 하나에 둔 뒤
cozy channel add '\\fileserver\share\cozy' ./cozy-2.1.0
cozy channel add '\\fileserver\share\cozy' ./cozy-2.1.0 --dry-run   # 복사할 파일만 보인다
```

- 받은 폴더의 `cozy-channel.json`(그 릴리스 하나의 색인)을 읽고, 적힌 파일마다 SHA-256을 확인한다. 하나라도 다르면 아무것도 복사하지 않는다.
- 파일을 `<채널 폴더>/{판}/`에 복사하고 복사한 파일의 SHA-256을 다시 확인한 뒤, 채널 색인 `<채널 폴더>/index/cozy-channel.json`에 그 판을 더한다. 색인은 마지막에 바꾼다(읽는 PC가 없는 파일을 가리키는 색인을 보지 않게).

```text
<채널 폴더>/
├── index/cozy-channel.json        채널 색인. 파일 경로는 ../{판}/{파일}
├── 2.1.0/                         cozy-2.1.0-windows-x64.msi, cozy-offline-bundle-2.1.0-windows-x64.zip, cozy-offline-bundle-2.1.0.zip
└── 2.2.0/
```
- 같은 판이 이미 같은 파일로 있으면 바꾸지 않는다. 같은 판이 다른 파일로 있으면 거부한다. 게시한 릴리스의 파일은 바꾸지 않는다.
- 채널 색인을 읽을 수 없으면(모르는 `schema`, 빠진 필드) 덮어쓰지 않고 멈춘다.
- **웹 서버와 GitLab**: 로컬 폴더나 파일 서버에서 채널 폴더를 만들고, 같은 모양으로 올린다. 판 폴더의 파일을 먼저 올리고 `index/cozy-channel.json`을 마지막에 올린다.
  - **GitLab Generic Package Registry**: 패키지 이름 하나(`cozy`)에 폴더 이름을 패키지 판으로 올린다. 판 폴더 `2.1.0/`의 파일은 패키지 판 `2.1.0`에, 색인은 패키지 판 `index`에 둔다. 색인을 바꿀 때마다 같은 이름으로 다시 올리므로, 프로젝트 설정에서 Generic 패키지의 중복을 막았다면 `index`에는 허용한다.

    ```bash
    curl --fail --header "PRIVATE-TOKEN: $TOKEN" --upload-file 2.1.0/cozy-2.1.0-windows-x64.msi \
      "https://gitlab.example/api/v4/projects/42/packages/generic/cozy/2.1.0/cozy-2.1.0-windows-x64.msi"
    # 두 번들도 같게 올린 뒤 마지막에
    curl --fail --header "PRIVATE-TOKEN: $TOKEN" --upload-file index/cozy-channel.json \
      "https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json"
    ```
- **채널 폴더의 쓰기 권한**: 채널에 쓸 수 있는 사람은 배포할 수 있는 사람이다. 쓰기 권한을 관리자로 좁힌다. SHA-256은 깨진 파일과 전송 오류를 막고, 바꿔치기는 막지 않는다.

## 색인 형식 `cozy-channel.json`

```json
{
  "schema": 1,
  "releases": [
    {
      "version": "2.1.0",
      "date": "2026-12-01",
      "breaking": false,
      "summary": "한 줄 요약",
      "appSteps": [],
      "files": [
        { "kind": "msi", "path": "../2.1.0/cozy-2.1.0-windows-x64.msi", "sha256": "…", "size": 320451561 },
        { "kind": "windows-x64", "path": "../2.1.0/cozy-offline-bundle-2.1.0-windows-x64.zip", "sha256": "…", "size": 346199784 },
        { "kind": "portable", "path": "../2.1.0/cozy-offline-bundle-2.1.0.zip", "sha256": "…", "size": 257272889 }
      ]
    }
  ]
}
```

| 필드 | 뜻 |
| --- | --- |
| `schema` | 색인 형식. 지금은 `1`이다. 다른 값이면 읽지 않는다(cozy를 먼저 새 판으로 설치한다) |
| `releases` | 새 판부터. 같은 판은 한 번만 |
| `version` | 릴리스 판(숫자 세 개). SNAPSHOT은 채널에 두지 않는다 |
| `date` | 릴리스 날짜(`yyyy-MM-dd`) |
| `breaking` | 앱을 올릴 때 손이 가는 판. 주 버전이 바뀐 판(`x.0.0`)이 `true`다 |
| `summary` | 한 줄 요약 |
| `appSteps` | 앱 폴더에서 돌릴 명령(예: `cozy migrate events`). 없으면 `[]` |
| `files[].kind` | `msi`(MSI), `windows-x64`(JDK를 담은 Windows x64 번들), `portable`(JDK 없는 번들). 종류마다 하나 |
| `files[].path` | 색인 주소 기준의 상대 경로. `/`로 나눈다. `../`를 쓸 수 있고 절대 경로는 안 된다 |
| `files[].sha256`, `files[].size` | 소문자 16진수 64자, 바이트 |

- 빠지거나 형식이 틀린 필드는 읽지 않는다. 모르는 키는 건너뛴다(같은 `schema` 안에서는 키를 더하기만 한다).
- 파일 경로가 상대 경로라서 채널 폴더를 통째로 다른 서버로 복사해도 그대로 동작한다.

## 릴리스 자산의 색인

릴리스 워크플로가 릴리스마다 그 릴리스 하나만 담은 `cozy-channel.json`을 자산에 더한다([release.md](release.md)). 경로는 같은 자산의 파일 이름이다. 그래서 GitHub의 `releases/latest/download/cozy-channel.json`이 그대로 채널이 되고, 받은 폴더를 그대로 `cozy channel add`에 줄 수 있다.

```bash
cozy channel release <릴리스 폴더> distribution/release/channel.yml   # 워크플로가 실행한다
```

- 릴리스 폴더의 세 파일(MSI, 두 번들)을 `SHA256SUMS`와 대 보고, `distribution/release/channel.yml`의 `version`, `summary`, `appSteps`로 색인을 만든다. `breaking`은 판에서 정한다.
- `channel.yml`의 `version`이 릴리스 판과 다르면 실패한다. 태그 빌드의 버전 확인(`check-version.sh`)도 같은 것을 먼저 본다.
