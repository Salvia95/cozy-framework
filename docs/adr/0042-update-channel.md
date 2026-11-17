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
| 파일 서버 | `\\fileserver\share\cozy\index\cozy-channel.json`, `Z:\cozy\index\cozy-channel.json`, `file:///mnt/share/cozy/index/cozy-channel.json` |
| 사내 웹 서버 | `https://intra.example/cozy/index/cozy-channel.json` |
| GitLab 패키지 레지스트리 | `https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json` |
| GitHub Releases | `https://github.com/Salvia95/cozy-framework/releases/latest/download/cozy-channel.json` |

- 색인 안의 파일 경로는 색인 주소를 기준으로 한 상대 경로다(`../2.1.0/cozy-2.1.0-windows-x64.msi`). 채널 폴더는 `index/cozy-channel.json`과 판 폴더 `{판}/`를 나란히 둔다(M42). 그래서 채널 폴더를 통째로 다른 서버로 복사해도 그대로 동작한다.
- 주소의 형식은 `file:`, UNC 경로, 절대 경로, `http:`, `https:`다. 그 밖의 형식은 거부한다.

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
        { "kind": "msi", "path": "../2.1.0/cozy-2.1.0-windows-x64.msi", "sha256": "…", "size": 320451561 },
        { "kind": "windows-x64", "path": "../2.1.0/cozy-offline-bundle-2.1.0-windows-x64.zip", "sha256": "…", "size": 346199784 },
        { "kind": "portable", "path": "../2.1.0/cozy-offline-bundle-2.1.0.zip", "sha256": "…", "size": 257272889 }
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
cozy channel set '\\fileserver\share\cozy\index\cozy-channel.json'
cozy channel set https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json --header PRIVATE-TOKEN
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
| `cozy channel add <채널 폴더> <릴리스 폴더>` | 관리자가 채널을 만든다. 받은 릴리스 파일을 릴리스 색인의 SHA-256으로 확인하고 `<채널 폴더>/{버전}/`에 복사한 뒤 `index/cozy-channel.json`에 더한다. 웹 서버나 GitLab이면 이 폴더를 같은 모양으로 올린다 |

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
- **이전 판을 남긴다.** `cozy upgrade`는 지금 번들 저장소에 있고 새 번들 저장소에 없는 파일(이전 판의 cozy 모듈, 그 판만 쓰는 의존성)을 사용자 저장소로 옮긴다. zip은 받은 번들의 `cozy install`이 옮기고, MSI는 설치하기 전의 저장소를 하드 링크로 떠 두었다가 설치한 뒤 옮긴다(M44). 그래서 올리기 전의 앱도 계속 오프라인으로 빌드된다. 늘어나는 크기는 판 사이의 차이만큼이다.
- **2.0.0에서 처음 올릴 때**: 2.0.0에는 `cozy upgrade`가 없으므로 첫 판(2.1.0)은 MSI나 `cozy install`로 설치한다.
  - `cozy install`(zip)은 설치 폴더 저장소에만 있던 파일(2.0.0 판의 cozy 모듈, `mvn install` 결과)을 사용자 저장소로 옮긴다.
  - MSI는 설치하기 전에 코드를 돌리지 않으므로(`cozy.wxs`에 사용자 작업이 없다) 2.0.0 판의 cozy 모듈을 지운다. Maven이 설치 폴더에 쓴 파일은 MSI의 것이 아니라 남고, 꼬리로 계속 읽힌다. 2.0.0을 쓰던 앱은 cozy-parent 판을 올린다.
  - 그다음부터는 `cozy upgrade`가 MSI를 실행하기 전에 옮긴다(§4). MSI를 더블클릭해 올리면 이전 판이 남지 않는다고 문서에 적는다.
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
| M41 저장소 나누기 | `cozy install`과 MSI가 사용자 저장소 + 꼬리로 설치한다. 이전 설치 위에 `cozy install`하면 이전 저장소에만 있던 파일이 사용자 저장소로 옮겨지고 이전 판의 앱이 오프라인으로 빌드된다. 빌드가 설치 폴더에 쓰지 않고, 지우면 설치 폴더가 남지 않는다 |
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

## M41 (2026-11-17, 사용자 결정 "승인할게")

- 시작할 때 최신 GA: 같은 날 릴리스 2.0.0 준비 때 확인한 그대로다(ADR-0017 2026-11-17).
- `cozy install`(bash, PowerShell)이 쓰는 `settings.xml`: `localRepository`는 사용자 저장소(Windows `${env.LOCALAPPDATA}/Cozy/repository`, Linux `${env.HOME}/.local/share/cozy/repository`), 늘 켜진 프로필 `cozy-bundle`의 `maven.repo.local.tail`은 `${maven.home}/../repository`다. Linux에서 `${user.home}`이 아니라 `${env.HOME}`을 쓴다. JDK의 `user.home`은 `HOME`을 따르지 않아 설치 스크립트(`$HOME`)와 어긋난다.
- 다시 설치하면 이전 저장소에만 있는 파일을 사용자 저장소로 옮긴다. 새 번들에도 있는 파일, 사용자 저장소에 이미 있는 파일, `.locks`는 그대로 둔다.
- cozy 명령의 `COZY_REPOSITORY` 기본값이 사용자 저장소와 설치 폴더 저장소 두 개다. cozy-cli는 `COZY_REPOSITORY`를 경로 구분자로 나눈 목록으로 읽고 앞에서부터 cozy-ui jar를 찾는다. 올리기 전의 앱이 쓰는 판은 사용자 저장소에만 있기 때문이다.
- 확인:
  - `BundleCommandsTest`: 설정 내용, 다시 설치할 때 옮기는 파일(이전 판 cozy-ui, 예전 설치가 쓴 앱)과 옮기지 않는 파일(사용자 저장소에 있는 것, 잠금 파일), 첫 설치, cozy 명령의 저장소 목록, PowerShell 스크립트의 같은 설정. `CozyCliTest.everyRepositoryInTheListIsSearchedInOrder`.
  - 실제 Maven 3.10.1과 실제 저장소로 손으로 확인했다(Linux). 2.0.0 번들 설치 → `cozy new` → 2.0.0 cozy 모듈이 없는 번들로 다시 설치(35개 옮김) → 2.0.0 앱의 `mvn -o verify`(테스트 14개)와 `cozy check` 통과. 꼬리 저장소에는 새 파일이 생기지 않았다.
  - `verify-msi.ps1`: 설정 내용, 빌드한 뒤 설치 폴더 저장소의 파일 수가 그대로인지, 사용자 저장소가 생겼는지, 지운 뒤 설치 폴더가 남지 않는지(전에는 남은 파일을 참고로만 알렸다)를 본다. Windows 러너에서는 다음 릴리스 워크플로 실행 때 돈다.
- 계획과 달라진 것: MSI 위에 MSI를 설치할 때 이전 판을 남기는 것은 이번에 하지 않는다. MSI에 사용자 작업(CustomAction)을 더해야 하는데, 지금 MSI에는 사용자 작업이 없고 회사 PC 정책에 걸릴 곳이 늘어난다. §5에 고쳐 적었고, `cozy upgrade`(M44)가 MSI를 실행하기 전에 옮긴다.

## M42 (2026-11-17, 사용자 지시 "러너 검증은 이번 마일스톤이 끝나고 돌리자 다음 진행해줘")

- 러너 검증(`gh workflow run release.yml`)은 사용자 결정대로 M41~M45를 마친 뒤 한 번에 돌린다.
- 색인 형식, `cozy channel set·show·add·release`, 사용자 설정 파일, 릴리스 워크플로의 `cozy-channel.json`을 만들었다. 형식과 사용법은 `docs/conventions/channel.md`에 있다.
- 계획과 달라진 것:
  - **채널 폴더의 모양**: 색인을 `<채널 폴더>/index/cozy-channel.json`에 두고 파일 경로를 `../{판}/{파일}`로 적는다(처음 쓴 §1·§2의 예는 색인이 판 폴더 옆이었다. 고쳐 적었다). GitLab Generic Package Registry는 파일 이름에 `/`를 넣을 수 없어 패키지 판이 폴더 하나다. 색인을 판 폴더와 나란한 `index`에 두면 패키지 판 `index`와 `2.1.0`으로 같은 모양을 올린다. 파일 서버와 웹 서버도 같은 모양을 쓴다.
  - **주소 형식**: UNC, `file:`, `http:`, `https:`에 더해 절대 경로(연결한 드라이브 `Z:\…`, 마운트 `/mnt/…`)를 받는다. 회사 PC에서 공유 폴더를 드라이브로 연결해 쓰는 일이 많다. `file:`과 같은 뜻이다.
  - **릴리스 색인의 내용**: `summary`와 `appSteps`는 레포의 `distribution/release/channel.yml`에 둔다. 릴리스 절차의 버전 바꾸기 단계에서 함께 고치고, 태그 빌드의 `check-version.sh`가 그 `version`이 릴리스 판과 같은지 먼저 본다. `breaking`은 적지 않고 판에서 정한다(`x.0.0`).
  - `cozy channel release`를 더했다. 워크플로의 `release` 작업이 JDK 없는 번들의 `lib/cli`로 실행한다(Java 25를 설치하는 단계를 더했다).
- 확인: `ChannelCommandTest` 18개. 설정 파일과 환경 변수의 순서, 토큰을 보이지 않음, 주소 형식 거부, 릴리스 색인(SHA256SUMS 대조, 다른 판의 노트, SNAPSHOT, 빠진 파일), 채널에 더하기(판 폴더와 색인, 새 판부터, 같은 판 다시 더하기, 같은 판의 다른 파일 거부, 깨진 파일은 복사 전에 거부, 읽을 수 없는 색인은 덮어쓰지 않음, `--dry-run`). `check-version.sh`의 `channel.yml` 판 대조는 손으로 확인했다.
- 워크플로의 새 단계(`cozy channel release`)는 태그 빌드에서만 돈다. 다음 릴리스(2.1.0)에서 처음 돈다.


## M43 (2026-11-17, 사용자 지시 "진행해줘")

- 시작할 때 최신 GA: 같은 날 확인한 그대로다(ADR-0017 2026-11-17). 새 의존성은 없다(JDK의 `HttpClient`와 `KeyStore`만 쓴다).
- `cozy update [--json]`과 하루 한 번의 새 판 알림을 만들었다. 사용법은 `docs/conventions/channel.md`의 "`cozy update`와 새 판 알림"에 있다.
- 정한 것:
  - **지금 판**: 번들의 cozy 명령(bash, PowerShell)이 설치 폴더를 환경 변수 `COZY_HOME`으로 cozy-cli에 넘기고, cozy-cli는 그 폴더의 `COZY_VERSION`을 읽는다. 설치 폴더를 모르거나 개발 판(SNAPSHOT)이면 `cozy update`는 종료 코드 2, 알림은 확인하지 않는다. M44의 `cozy upgrade`도 이 값으로 설치 폴더와 설치 모양을 안다.
  - **올릴 판**: 지금 판과 주 버전이 같은 가장 새 판. 주 버전이 바뀐 판은 `--to`로 적어야 한다는 §4를 `cozy update`가 그대로 보인다.
  - **토큰은 채널 주소와 같은 서버에만 보낸다.** 넘겨주기를 직접 따르고(5번까지), 다른 서버(scheme, 호스트, 포트가 다른 곳)로 넘어가면 토큰을 빼고 요청한다. GitHub의 `releases/latest/download/`는 자산 저장소로 넘겨주기 때문이다. `https`에서 `http`로 넘겨주면 따르지 않는다.
  - **인증서**: JDK의 `cacerts`와 Windows의 `Windows-ROOT`를 함께 믿는다(둘 가운데 하나가 믿으면 믿는다. 이름 확인은 각 관리자가 한다). §3은 `Windows-ROOT`만 적었지만, Windows 인증서 저장소의 공용 루트는 처음 쓸 때 받아 오는 경우가 있어 폐쇄망 PC에서 비어 있을 수 있다.
  - **프록시**: `main`이 `java.net.useSystemProxies`를 켜고 시작한다.
- 계획과 달라진 것:
  - **알림의 자리와 범위**: 오류 출력(stderr)의 마지막 줄에 보이고, 앱에 쓰는 명령(`describe`, `check`, `recipes`, `add`, `link`, `enable`, `sync`, `set`, `migrate`)의 사람이 읽는 출력 뒤에만 확인한다. `--json`(에이전트와 워크벤치가 읽는다), `cozy update`, `cozy channel`, 도움말 뒤에는 하지 않는다. 확인은 3초까지만 기다리고(파일 서버의 응답 없는 UNC 경로도 같다), 실패하면 그날은 다시 확인하지 않는다. 확인한 날은 설정 파일 옆의 `update-check.properties`에 적는다. 문구는 판 뒤에 조사가 붙지 않게 `새 버전이 있습니다: 2.1.0. cozy upgrade로 올립니다 (바뀌는 것: cozy update)`로 바꿨고(판의 끝 숫자에 따라 조사가 달라진다), 호환이 깨지는 판만 있으면 `cozy upgrade` 대신 `cozy update`를 권한다.
  - 번들의 `cozy version`, `cozy new`, `cozy install`은 셸 스크립트가 하므로 알림이 없다.
- 확인:
  - `UpdateCommandTest` 27개. 파일 채널(경로, `file:`)과 JDK `HttpServer`로 띄운 로컬 서버. 새 판 목록과 요약, `--json`, 최신 판, 호환 깨짐 판과 `--to`, 낮은 채널, 모르는 `schema`, 없는 색인, 채널·설치 폴더·릴리스 판이 없을 때, 기본 머리(`Authorization: Bearer`)와 이름을 정한 머리(`PRIVATE-TOKEN`), 401과 404(토큰을 보이지 않음), 같은 서버와 다른 서버로의 넘겨주기, 끝없는 넘겨주기, 상대 경로(`../`), 알림(하루 한 번, 다음 날, 호환 깨짐, 실패, `--json`·update·channel·도움말 제외, `cozy update`가 적은 날).
  - 인증서: `keytool`로 만든 자체 서명 인증서의 로컬 HTTPS 서버를 JDK 인증서만으로는 거부하고, 그 인증서를 담은 저장소를 더하면 읽는다. `Windows-ROOT` 자체는 Linux에서 확인할 수 없다(windows-verification.md에 항목을 더했다).
  - `BundleCommandsTest`: 설치한 `bin/cozy`가 `update`를 cozy-cli에 넘기고 `COZY_HOME`을 알려 준다. PowerShell 스크립트도 같다.
  - `verify-msi.ps1`: 설치한 MSI의 `cozy update --json`이 설치 폴더의 판을 파일 채널과 비교한다(개발 판이면 종료 코드 2). Windows 러너에서는 M45 뒤의 러너 검증 때 돈다.

## M44 (2026-11-17, 사용자 지시 "진행해줘")

- 시작할 때 최신 GA: 같은 날 확인한 그대로다(ADR-0017 2026-11-17). 새 의존성은 없다(JDK의 `HttpClient`, `MessageDigest`, `ZipFile`, 하드 링크만 쓴다).
- `cozy upgrade [--to 판]`을 만들었다. 사용법은 `docs/conventions/channel.md`의 "`cozy upgrade`"에 있다.
- 정한 것:
  - **설치한 모양을 아는 방법**: `build-msi.ps1`이 설치 폴더 모양에 표시 파일 `COZY_INSTALLER`(`msi`)를 넣는다. 없으면 zip 설치이고, `jdk/`가 있으면 Windows는 `windows-x64`, 없으면 `portable`을 받는다. Linux에서 JDK를 담은 번들(linux-x64, 검증용)은 채널에 맞는 파일이 없어 올리지 않는다(JDK 없는 번들로 바꾸면 번들 JDK가 지워진다).
  - **받기**: 사용자 데이터 폴더(`%LOCALAPPDATA%\Cozy\upgrade\{판}`, Linux `~/.local/share/cozy/upgrade/{판}`)의 `.part`에 받으면서 SHA-256을 재고, 크기와 SHA-256이 색인과 같을 때만 남긴다. 전에 받은 파일이 맞으면 다시 받지 않는다(실패한 설치를 다시 할 때). zip은 cozy가 끝나기 전에 풀어 그 판의 번들인지, 폴더 밖 항목이 없는지 본다. 설치하기 전에 알 수 있는 실패는 모두 cozy가 끝나기 전에 종료 코드 2로 알린다.
  - **설치 스크립트**: 받은 폴더에 쓰고 따로 띄운다. Linux는 `setsid bash install.sh`, Windows는 `cmd /c start`로 새 콘솔에 숨긴 창의 PowerShell(BOM 있는 UTF-8)을 띄운다. 그래서 cozy를 실행한 창을 닫아도 설치가 이어진다. 스크립트는 cozy-cli와 (Windows는) 그것을 띄운 PowerShell이 끝나기를 기다리고, Windows는 cmd가 `cozy.cmd`의 마지막 줄을 읽도록 2초 더 기다린다. 자기 PID를 `pid`에, 종료 코드를 `result`에 남긴다.
  - **결과 보이기**: 맡긴 설치는 설정 폴더의 `upgrade.properties`에 적고, 다음 cozy-cli 명령이 시작할 때 오류 출력에 한 번 보이고 지운다. 스크립트가 돌고 있으면(PID가 살아 있거나 2분이 지나지 않음) "설치하는 중"이라 보이고 `cozy upgrade`를 다시 받지 않는다. 성공은 종료 코드 0과 설치 폴더의 `COZY_VERSION`으로 판단하고, 받은 폴더를 지운다. 실패하면 받은 파일을 남기고 위치를 알린다.
  - **`--to`**: 적은 판이 지금 판이면 아무것도 하지 않고(종료 코드 0), 낮으면 멈춘다. `--json`과 `--dry-run`은 받지 않는다(올릴 판은 `cozy update`가 보인다).
- 계획과 달라진 것:
  - **새 판의 파일 목록**: §5는 "받은 패키지에서 읽는다"였다. zip은 받은 번들의 `cozy install`이 이전 저장소와 새 저장소를 비교해 옮기므로 같다. MSI는 받은 파일에서 저장소 목록을 읽으려면 MSI 표를 읽는 도구(msiexec의 관리 설치나 WiX)가 필요해, 대신 설치하기 전의 저장소를 하드 링크로 떠 두었다가 설치한 뒤 새 저장소와 비교해 옮긴다. 하드 링크라 디스크가 늘지 않고, 하드 링크를 만들 수 없는 곳이면 복사한다. §5를 고쳐 적었다.
  - **종단 검증의 자리**: §6의 `-Pe2e`가 아니라 cozy-cli의 테스트(`UpgradeBundleTest`)다. 번들의 진짜 cozy 스크립트와 이 빌드의 cozy-cli로 설치 → 채널 → `cozy upgrade` → 설치 스크립트 → 다음 명령까지 돌린다. 몇 초면 끝나 늘 도는 빌드에 둔다. 번들의 Maven과 저장소는 작은 가짜다. 이전 판의 앱이 진짜 Maven으로 빌드되는 것은 M41에서 같은 `cozy install`로 확인했다.
  - **Windows 러너**: `verify-msi.ps1`에 더하지 않고 `verify-msi-upgrade.ps1`로 나눴다. 번들에서 판만 다른 MSI 두 개(개발 판이면 `-SNAPSHOT`을 뗀 판과 끝자리 + 1)를 만들고, 앞 판에만 표시 파일을 둔다. 워크플로의 Windows 작업 시간 제한을 120분으로 늘렸다.
- 확인:
  - `UpgradeCommandTest` 22개. 받기·풀기·스크립트 맡기와 스크립트를 직접 돌린 설치, 성공·실패·설치하는 중·결과 없이 끝남의 결과 줄, 실패한 뒤 받은 파일 다시 쓰기, cozy가 끝나기를 기다림, 다른 판의 번들, 주 버전이 같은 최신 판과 호환 깨짐 판의 알림, 호환 깨짐 판만 있을 때 멈춤과 `--to`, 낮은 판(채널, `--to`), 지금 판, 채널에 없는 판, 인자, `--dry-run`, SHA-256과 크기가 다른 파일(남기지 않음), 웹 채널에서 파일 받기에도 토큰, 맞는 파일 종류가 없음, Linux의 JDK 번들 설치, 채널과 릴리스 판이 없을 때. Windows 모양(MSI의 PowerShell 스크립트 내용, BOM과 CRLF, 하드 링크로 뜬 저장소와 잠금 파일 제외, `windows-x64` 번들을 JDK로 풀기, 폴더 밖 항목 거부, Linux의 MSI 설치 거부).
  - `UpgradeBundleTest`: 위의 종단 흐름. 설치 폴더의 판, 새 번들 저장소, 사용자 저장소로 옮긴 이전 판의 cozy-ui, 옮기지 않은 공통 파일, 다음 명령의 "판을 올렸습니다"와 지운 받은 폴더.
  - `BundleCommandsTest`: bash와 PowerShell의 cozy 명령이 `upgrade`를 넘기고, MSI만 `COZY_INSTALLER`를 쓰고, 워크플로가 `verify-msi-upgrade.ps1`을 돈다.
  - cozy-cli 테스트 238개 통과.
  - `verify-msi-upgrade.ps1`은 Linux에서 돌릴 수 없다. M45 뒤의 러너 검증 때 처음 돈다.
