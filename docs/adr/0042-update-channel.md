# ADR-0042 개발 도구 업데이트 채널: `cozy update`·`cozy upgrade`, 직접 정하는 업데이트 서버

- 상태: 승인 (2026-11-17, 사용자 결정 "승인할게"). "정할 것" 1~6은 권장안
- 출처: 사용자 요청 "이미 설치된 상태에서 매번 다운로드 받아서 업데이트 하는게 힘든데 ota를 지원할 방법은 없을까? 아니면 apt와 같이 update를 통해 변경사항을 끌어와 upgrade한다던지 말이야", "내부 파일서버나 gitlab 등이 지원되니 업데이트 서버를 직접 설정할 수 있는 구조였으면 좋겠어"
- 바꾸는 결정:
  - ADR-0025 §6과 ADR-0039 §3 "Maven 설정": `localRepository`를 설치 폴더의 `repository/`에서 사용자 저장소로 옮기고, 설치 폴더의 저장소는 읽기 전용 꼬리(`maven.repo.local.tail`)로 둔다(§5).
  - ADR-0028 릴리스 자산: 채널 색인 `cozy-channel.json`을 더한다(§2).
- 바꾸지 않는 것: 앱의 자체 업데이트(ADR-0012, ADR-0024, `cozy.update.*`), 오프라인 번들의 구성과 라이선스 목록(ADR-0025, ADR-0028), MSI의 사용자별 설치와 UpgradeCode(ADR-0039), cozy-cli의 의존(rules와 SnakeYAML, ADR-0016)

## 배경

- 개발 도구(MSI나 zip 번들)를 올리려면 PC마다 릴리스를 받아 다시 설치해야 한다. 파일이 250~330MB이고, 폐쇄망 PC는 누군가 파일을 들고 와야 한다.
- 폐쇄망 PC는 인터넷에서 직접 받을 수 없다. 새 판은 누군가 한 번은 망 안으로 들여와야 한다. 줄일 수 있는 수고는 PC마다 받고 설치하는 일이다. 한 사람이 내부 서버에 올리면 나머지 PC는 명령 하나로 올리게 한다. apt가 사내 미러를 보는 것과 같은 모양이다.
- 조직마다 쓸 수 있는 서버가 다르다. 파일 서버(공유 폴더), 사내 웹 서버, GitLab(패키지 레지스트리, 릴리스)이 있고, 인터넷이 되는 PC는 GitHub Releases를 바로 볼 수 있다. 특정 서버의 API에 묶이면 다른 곳에서 쓸 수 없다.
- 지금 설치 방식은 이전 판을 지운다. `cozy install`은 설치 폴더의 `repository/`를 지우고 새로 복사하고, MSI 업그레이드도 이전 판의 파일을 지운다. 2.0.0을 설치하면 1.x cozy 모듈이 사라져서, 아직 올리지 않은 1.x 앱을 오프라인으로 빌드할 수 없다. 업그레이드를 쉽게 하려면 이것부터 풀어야 한다.
- Maven은 빌드할 때 `localRepository`에 쓴다(잠금 파일, `mvn install` 결과). 지금은 그곳이 설치 폴더라서 MSI를 지워도 파일이 남는다(ADR-0039 위험).

## 결정

### 1. 채널은 주소 하나다

채널은 색인 파일 `cozy-channel.json`의 주소다. 색인과 파일은 정적 파일이라 서버의 API를 쓰지 않는다.

| 서버 | 채널 주소 예 |
| --- | --- |
| 파일 서버 | `\\fileserver\share\cozy\cozy-channel.json`, `file:///mnt/share/cozy/cozy-channel.json` |
| 사내 웹 서버 | `https://intra.example/cozy/cozy-channel.json` |
| GitLab 패키지 레지스트리 | `https://gitlab.example/api/v4/projects/42/packages/generic/cozy/channel/cozy-channel.json` |
| GitHub Releases | `https://github.com/Salvia95/cozy-framework/releases/latest/download/cozy-channel.json` |

- 색인 안의 파일 경로는 색인 주소를 기준으로 한 상대 경로다(`2.1.0/cozy-2.1.0-windows-x64.msi`, `../2.1.0/…`). 그래서 채널 폴더를 통째로 다른 서버로 복사해도 그대로 동작한다.
- 주소의 형식은 `file:`, UNC 경로, `http:`, `https:`다. 그 밖의 형식은 거부한다.

### 2. 색인 형식 `cozy-channel.json`

```json
{
  "schema": 1,
  "releases": [
    {
      "version": "2.1.0",
      "date": "2026-12-01",
      "breaking": false,
      "summary": "한 줄 요약",
      "appSteps": ["cozy sync agents"],
      "files": [
        { "kind": "msi", "path": "2.1.0/cozy-2.1.0-windows-x64.msi", "sha256": "…", "size": 320451561 },
        { "kind": "windows-x64", "path": "2.1.0/cozy-offline-bundle-2.1.0-windows-x64.zip", "sha256": "…", "size": 346199784 },
        { "kind": "portable", "path": "2.1.0/cozy-offline-bundle-2.1.0.zip", "sha256": "…", "size": 257272889 }
      ]
    }
  ]
}
```

- `breaking`은 앱을 올릴 때 손이 가는 판(주 버전이 바뀐 판)이다. `appSteps`는 앱 폴더에서 돌릴 명령이다(2.0.0이면 `cozy migrate alpine`, `cozy migrate events`).
- 릴리스 워크플로가 그 릴리스 하나만 담은 `cozy-channel.json`을 릴리스 자산에 더한다. 그래서 GitHub의 `releases/latest/download/cozy-channel.json`이 그대로 채널이 된다.
- 모르는 `schema`, 빠진 필드, 맞지 않는 SHA-256은 실패로 닫는다.

### 3. 설정

```bash
cozy channel set '\\fileserver\share\cozy\cozy-channel.json'
cozy channel set https://gitlab.example/api/v4/projects/42/packages/generic/cozy/channel/cozy-channel.json --header PRIVATE-TOKEN
cozy channel show
```

- 설정은 설치 폴더 밖의 사용자 설정 파일에 둔다(Windows `%APPDATA%\Cozy\cozy.properties`, Linux `~/.config/cozy/cozy.properties`). 업그레이드하거나 지워도 남는다.
- 환경 변수 `COZY_CHANNEL`이 있으면 설정 파일보다 앞선다. 여러 PC에 같은 채널을 둘 때 쓴다.
- 인증이 필요한 서버는 머리 이름(`--header`, 기본 `Authorization: Bearer`)과 토큰을 쓴다. 토큰은 환경 변수 `COZY_CHANNEL_TOKEN`이나 설정 파일의 `channel.token`에서 읽고, 출력과 로그에 보이지 않는다.
- Windows에서는 Windows 인증서 저장소(`Windows-ROOT`)를 믿는다. 사내 CA로 서명한 HTTPS 서버도 따로 설정하지 않고 쓴다. 프록시는 시스템 설정을 따른다.
- 채널을 정하지 않으면 업데이트를 확인하지 않는다. 기본값으로 인터넷 주소를 두지 않는다(폐쇄망에서 시간 초과를 기다리지 않게).

### 4. 명령

| 명령 | 하는 일 |
| --- | --- |
| `cozy update` | 채널 색인을 읽어 지금 판, 최신 판, 그 사이 판의 요약, 호환 깨짐 표시, 앱에서 돌릴 명령을 보인다. 아무것도 바꾸지 않는다. `--json` |
| `cozy upgrade [--to 버전]` | 판을 받아 SHA-256을 확인하고(§5의 보존을 먼저 한 뒤) 설치한다 |
| `cozy channel set·show` | 채널 설정(§3) |
| `cozy channel add <채널 폴더> <릴리스 폴더>` | 관리자가 채널을 만든다. 받은 릴리스 파일을 `SHA256SUMS`로 확인하고 `<채널 폴더>/{버전}/`에 복사한 뒤 색인에 더한다. 웹 서버나 GitLab이면 이 폴더를 올린다 |

- **설치 방식은 설치한 모양을 따른다.** MSI로 설치했으면 받은 MSI를 사용자별로 조용히 설치하고(`msiexec /i … /qn`, 관리자 권한 없음), zip으로 설치했으면 받은 번들의 `cozy install`을 돌린다.
- **설치는 cozy가 끝난 뒤에 한다.** 실행 중인 `cozy.cmd`와 `cozy-cli.jar`를 바꾸면 Windows에서 실패하거나 cmd가 바뀐 파일을 이어 읽는다. 앱의 교체 스크립트처럼 임시 폴더에 둔 설치 스크립트를 띄우고 cozy는 바로 끝난다. 설치 결과는 사용자 설정 폴더의 기록 파일에 남고, 다음 `cozy` 명령이 보인다.
- **호환이 깨지는 판은 직접 고른다.** `breaking` 판으로 넘어가는 `cozy upgrade`는 그 판의 `appSteps`를 보이고 멈춘다. `cozy upgrade --to 3.0.0`처럼 판을 적어야 올린다.
- **낮은 판으로는 가지 않는다.** 채널의 최신 판이 지금 판보다 낮으면 알리고 멈춘다.
- **새 판 알림**: 채널이 있으면 `cozy` 명령이 하루에 한 번 색인을 확인하고, 새 판이 있으면 마지막에 한 줄을 보인다(`새 버전 2.1.0이 있습니다. cozy upgrade로 올립니다`). 확인이 실패해도 명령의 결과는 바뀌지 않는다. 자동으로 설치하지 않는다.

### 5. 저장소를 설치 폴더와 나눈다

```text
%LOCALAPPDATA%\Programs\Cozy\repository\   설치 폴더. 그 판의 번들 저장소. 읽기 전용 꼬리
%LOCALAPPDATA%\Cozy\repository\            사용자 저장소. Maven이 쓰는 곳. 업그레이드하고 지워도 남는다
```

- `cozy install`이 쓰는 `settings.xml`은 `localRepository`를 사용자 저장소로 하고, 늘 켜진 프로필의 속성 `maven.repo.local.tail`을 설치 폴더의 저장소로 한다. 명령줄 인자가 아니라 `settings.xml`에 두므로 번들의 Maven을 지정한 IDE도 같은 설정을 읽는다.
- Maven 3.10.1에서 확인했다(2026-11-17). 빈 사용자 저장소와 꼬리로 오프라인 빌드가 되고, 사용자 저장소에는 `mvn install`한 것만 생긴다. 같은 `settings.xml`에서 프로필만 빼면 오프라인 빌드가 의존성을 찾지 못해 실패한다.
- **이전 판을 남긴다.** `cozy upgrade`는 설치하기 전에, 지금 번들 저장소에 있고 새 번들 저장소에 없는 파일(이전 판의 cozy 모듈, 그 판만 쓰는 의존성)을 사용자 저장소로 옮긴다. 새 판의 파일 목록은 받은 패키지에서 읽는다. 그래서 올리기 전의 앱도 계속 오프라인으로 빌드된다. 늘어나는 크기는 판 사이의 차이만큼이다.
- **2.0.0에서 처음 올릴 때**: 2.0.0에는 `cozy upgrade`가 없으므로 첫 판(2.1.0)은 MSI나 `cozy install`로 설치한다. 그 판의 설치가 설치 폴더 저장소의 사용자 파일(`mvn install` 결과)과 2.0.0 판의 cozy 모듈을 사용자 저장소로 옮긴다. 그다음부터는 `cozy upgrade`로 올린다.
- 사용자 저장소를 지우는 명령은 이번에 만들지 않는다. 쌓인 판은 폴더를 지워 정리한다(남은 일).

### 6. 테스트는 외부 주소를 쓰지 않는다

- 채널 읽기와 받기는 `file:` 채널과 JDK의 `HttpServer`로 띄운 로컬 서버로 확인한다. 머리 인증, 상대 경로(`../`), SHA-256 불일치, 모르는 `schema`, 낮은 판, `breaking` 판을 다룬다.
- 종단 검증(`-Pe2e`): 같은 빌드로 판만 다른 두 번들(현재 판, 현재 판 + `.1`)을 만들어 로컬 채널에 두고, 앞 판을 설치 → 앱 생성 → `cozy upgrade` → 앞 판 앱과 새 앱이 모두 오프라인으로 빌드되는지 본다.
- Windows 러너: MSI 두 판으로 같은 흐름을 `verify-msi.ps1`에 더한다.

## 정할 것

| | 선택지 | 권장 |
| --- | --- | --- |
| 1 | 채널 형식: 정적 색인 파일(모든 서버) / 서버마다 API(GitLab Releases API 등) | 정적 색인 파일 |
| 2 | 설정 위치: 사용자 설정 파일 + `COZY_CHANNEL` / 설치 폴더의 파일 | 사용자 설정 파일 + 환경 변수 |
| 3 | 호환 깨지는 판: `--to`로 적어야 올림 / 확인 질문 / 구분 없이 최신 | `--to`로 적어야 올림 |
| 4 | 새 판 알림: 하루 한 번 한 줄 / 끔(`cozy update`로만) | 하루 한 번 한 줄 |
| 5 | 무결성: 색인의 SHA-256, 채널에 쓸 수 있는 사람을 믿음 / 지금 서명(키 관리 필요) | SHA-256. 서명은 따로 ADR |
| 6 | 저장소: 사용자 저장소 + 읽기 전용 꼬리, 이전 판 보존 / 지금처럼 설치 폴더 하나 | 사용자 저장소 + 꼬리 |

## 이번에 하지 않는 것

- **차이만 담은 패키지**: 판 사이에 바뀌는 것은 대부분 cozy 모듈 jar와 판이 바뀐 몇 개라서 크기를 크게 줄일 수 있다. MSI가 가진 파일과 섞는 방법을 따로 정해야 하므로 다음 ADR로 둔다. §5의 차이 계산을 그대로 쓴다.
- **`cozy upgrade app`**: 앱의 cozy-parent 판을 올리고 `appSteps`를 차례로 돌리는 명령. 다음 ADR로 둔다. 이번에는 `cozy update`가 돌릴 명령을 보인다.
- **서명**: 정할 것 5.

## 단계

| 마일스톤 | 완료 기준 |
| --- | --- |
| M41 저장소 나누기 | `cozy install`과 MSI가 사용자 저장소 + 꼬리로 설치한다. 2.0.0 설치 위에 설치하면 사용자 파일과 2.0.0 cozy 모듈이 사용자 저장소로 옮겨지고 두 판의 앱이 오프라인으로 빌드된다. 지우면 설치 폴더가 남지 않는다 |
| M42 채널 색인 | 색인 형식, `cozy channel set·show·add`, 릴리스 워크플로의 `cozy-channel.json`. 실패로 닫는 경우의 테스트 |
| M43 `cozy update`와 알림 | `file:`·로컬 HTTP 채널, 인증 머리, 하루 한 번 알림, `--json` |
| M44 `cozy upgrade` | zip 설치(Linux, e2e)와 MSI 설치(Windows 러너), 이전 판 보존, `breaking`과 낮은 판 거부 |
| M45 문서 | `docs/conventions/`(설치와 업데이트, 채널 만들기: 파일 서버, 웹 서버, GitLab), 번들 README, 에이전트 문서 |

## 위험

- **채널에 쓸 수 있는 사람이 배포할 수 있는 사람이다.** 색인과 파일이 같은 곳에 있으므로 SHA-256은 전송 오류와 깨진 파일만 막는다. 앱의 자체 업데이트도 같은 전제다. 채널 폴더의 쓰기 권한을 관리자로 좁히라고 문서에 적는다.
- **토큰을 파일에 두면 평문이다.** 사용자 설정 폴더에만 두고, 환경 변수를 먼저 권한다.
- **회사 PC의 정책**: `msiexec`의 조용한 설치나 임시 폴더의 스크립트 실행을 막을 수 있다. 막히면 받은 파일 위치를 알리고 손으로 설치하게 한다. Windows에서 직접 확인해야 한다(windows-verification.md에 항목을 더한다).
- **사용자 저장소가 쌓인다.** 판마다 차이만큼 늘어난다. 지우는 명령은 남은 일이다.
- **꼬리 저장소는 Maven 기능에 기댄다.** `maven.repo.local.tail`은 Maven 3.9부터 있다. IDE가 번들의 `settings.xml`을 쓰지 않으면 꼬리를 모른다(IntelliJ는 Maven home과 settings를 번들로 지정하라고 README에 이미 적혀 있다). Maven 4로 올릴 때 다시 확인한다.
