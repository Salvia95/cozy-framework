# 개발 도구 설치와 업데이트

개발 PC에 Cozy 개발 도구(cozy 명령, Maven, 번들 저장소, Windows는 JDK)를 설치하고, 새 판이 나오면 올리고, 앱을 새 판으로 옮기는 절차다. 관리자가 사내 업데이트 채널을 만드는 절차도 여기에 있다. 명령과 색인 형식의 자세한 내용은 [channel.md](channel.md)에, 결정과 이유는 ADR-0042에 있다.

- **PC마다 한 번**: 설치하고, 채널을 정한다.
- **새 판이 나오면 관리자가 한 번**: 릴리스를 받아 채널에 더한다(`cozy channel add`).
- **새 판이 나오면 PC마다**: `cozy upgrade` 한 번으로 올린다. 앱은 각자 준비가 되면 옮긴다.

## 설치한 것이 놓이는 곳

| 무엇 | Windows | Linux·WSL | 지우거나 올리면 |
| --- | --- | --- | --- |
| 설치 폴더(MSI) | `%LOCALAPPDATA%\Programs\Cozy` | (없음) | 그 판의 것으로 바뀐다 |
| 설치 폴더(zip, 기본) | `%USERPROFILE%\cozy` | `~/cozy` | 그 판의 것으로 바뀐다 |
| 사용자 저장소 | `%LOCALAPPDATA%\Cozy\repository` | `~/.local/share/cozy/repository` | 남는다. 이전 판의 파일이 쌓인다 |
| 받은 판 | `%LOCALAPPDATA%\Cozy\upgrade\{판}` | `~/.local/share/cozy/upgrade/{판}` | 설치에 성공하면 지운다 |
| 설정 | `%APPDATA%\Cozy\cozy.properties` | `~/.config/cozy/cozy.properties` | 남는다 |

- 설치 폴더의 `repository`는 그 판의 번들 저장소이고 Maven이 읽기만 한다. Maven이 쓰는 것(`mvn install`한 앱, 잠금 파일)은 사용자 저장소에 간다.
- 판을 올리면 이전 판에만 있던 저장소 파일을 사용자 저장소로 옮긴다. 그래서 아직 옮기지 않은 앱도 계속 오프라인으로 빌드된다.

## 설치

| 설치 파일 | 쓰는 곳 | 설치 |
| --- | --- | --- |
| `cozy-{판}-windows-x64.msi` | Windows PC(권장) | 더블클릭. 관리자 권한이 필요 없다. 사용자 PATH에 `bin`을 더한다 |
| `cozy-offline-bundle-{판}-windows-x64.zip` | MSI를 쓸 수 없는 Windows PC | 풀고 `.\cozy.cmd install [설치 폴더]` |
| `cozy-offline-bundle-{판}.zip` | Linux·WSL, JDK 25를 따로 둔 PC | 풀고 `./cozy install [설치 폴더]` |

- Windows의 두 설치 파일에는 JDK 25가 들어 있다. 시스템의 `JAVA_HOME`과 PATH는 바꾸지 않는다.
- zip으로 설치했으면 PATH에 `<설치 폴더>\bin`을 직접 더한다. 설치 명령이 마지막에 더할 줄을 보인다.
- 설치를 확인한다: 새 터미널에서 `cozy version`.
- 번들의 구성과 IDE 설정은 [번들 README](../../distribution/offline-bundle/README.md)에 있다.

## 채널 정하기 (PC마다 한 번)

관리자에게 채널 주소(`…/index/cozy-channel.json`)를 받아 정한다. 채널은 업데이트를 받아 오는 곳이다.

```powershell
cozy channel set '\\fileserver\share\cozy\index\cozy-channel.json'
cozy channel show
cozy upgrade --check
```

- 인증이 필요한 웹 서버나 GitLab이면 머리 이름을 함께 정하고, 토큰은 사용자 환경 변수 `COZY_CHANNEL_TOKEN`에 둔다(새로 연 터미널부터 보인다).

  ```powershell
  cozy channel set https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json --header DEPLOY-TOKEN
  [Environment]::SetEnvironmentVariable('COZY_CHANNEL_TOKEN', '<토큰>', 'User')
  ```

- **여러 PC에 같은 채널**: 사용자 환경 변수 `COZY_CHANNEL`에 주소를 둔다(로그온 스크립트, 그룹 정책). 설정 파일보다 앞선다.
- **인터넷이 되는 PC**: GitHub Releases를 바로 채널로 쓸 수 있다: `cozy channel set https://github.com/Salvia95/cozy-framework/releases/latest/download/cozy-channel.json`. 2.4.0부터 색인에 게시한 이전 릴리스(2.1.0부터)도 들어 있어, 판을 건너뛰어도 사이 판들의 요약과 앱에서 돌릴 명령이 보인다.
- 채널을 정하면 cozy 명령이 하루에 한 번 새 판을 확인해 마지막에 한 줄로 알린다. 자동으로 설치하지 않는다.

## 판 올리기

```powershell
cozy upgrade --check               # 새 판, 요약, 호환이 깨지는 판, 앱에서 돌릴 명령. 아무것도 바꾸지 않는다
cozy upgrade --check --to 3.0.0    # 그 판까지 바뀌는 것과 앱에서 돌릴 명령
cozy upgrade                       # 주 버전이 같은 가장 새 판으로 올린다
cozy upgrade --to 3.0.0            # 호환이 깨지는 판은 판을 적는다
```

1. `cozy upgrade`가 받은 파일의 크기와 SHA-256을 확인하고 설치를 맡긴 뒤 끝난다.
2. 1~2분 동안 창 없이 설치한다. 설치하는 동안 cozy 명령과 그 Maven(`mvn`)을 돌리지 않는다. 창을 닫아도 설치는 이어진다.
3. 다음 cozy 명령의 첫 줄에 결과가 보인다: `[cozy] 판을 올렸습니다: 2.1.0 → 2.1.1.` 지금 판은 `cozy version`으로 본다.

- 설치한 모양대로 올린다. MSI로 설치했으면 받은 MSI를, zip으로 설치했으면 받은 번들의 `cozy install`을 같은 설치 폴더에 돌린다.
- 앱은 바뀌지 않는다. 앱은 자기 `pom.xml`의 cozy-parent 판으로 계속 빌드된다.
- **MSI를 더블클릭해 올리지 않는다.** MSI는 이전 판의 파일을 지운다. 이전 판을 쓰는 앱이 있으면 `cozy upgrade`로 올린다. `cozy upgrade`는 설치하기 전의 저장소를 떠 두었다가 이전 판에만 있던 파일을 사용자 저장소로 옮긴다.
- **`cozy update`**: 2.3.0까지의 새 판 보기 명령이다. 2.x 동안 `cozy upgrade --check`의 별칭으로 남고 3.0.0에서 없어진다.
- **2.0.0에서 처음 올릴 때**: 2.0.0에는 `cozy channel`, `cozy update`, `cozy upgrade`가 없다. 2.1.0 이후의 첫 판은 MSI나 `cozy install`로 설치하고, 그다음부터 `cozy upgrade`를 쓴다. 2.1.0을 MSI로 설치하면 2.0.0 판의 cozy 모듈이 지워지므로, 2.0.0을 쓰던 앱은 cozy-parent 판을 올린다(zip의 `cozy install`은 옮긴다).

### 올리지 못했을 때

| 보이는 것 | 할 일 |
| --- | --- |
| `채널을 정하지 않았습니다` | 위의 "채널 정하기" |
| `[cozy] 최신 판입니다.` | 할 일 없음. 관리자가 새 판을 채널에 더했는지 확인한다 |
| `새 판은 모두 호환이 깨지는 판입니다` | 앱에서 돌릴 명령을 보고 준비가 되면 `cozy upgrade --to <판>` |
| `지금 판을 알 수 없습니다`, `릴리스 판이 아니라` | 설치 폴더의 `cozy`(MSI면 PATH의 `cozy`)로 실행한다. 개발 판(SNAPSHOT)은 채널과 비교하지 않는다 |
| HTTP 401, 403 | 머리 이름(`cozy channel show`)과 `COZY_CHANNEL_TOKEN`의 토큰을 확인한다 |
| `SHA-256이 채널 색인과 다릅니다`, 크기가 다르다는 메시지 | 받은 것은 지웠다. 관리자에게 알린다(채널의 파일이 깨졌거나 바뀌었다) |
| `판(…)을 설치하는 중입니다` | 1~2분 뒤 cozy 명령을 다시 돌린다 |
| `판(…)을 설치하지 못했습니다` | 보인 기록(`install.log`, MSI는 `msiexec.log`)을 보고, 받은 폴더의 파일로 손으로 설치한다. 회사 PC의 정책이 `msiexec`나 PowerShell 스크립트를 막았을 수 있다 |

## 앱을 새 판으로 옮기기

개발 도구를 올린 뒤 앱마다 준비가 되면 한다. 앱 폴더에서 실행한다.

```powershell
cozy version                 # 설치한 판. pom.xml의 cozy-parent 판을 이 판으로 바꾼다
# 그 사이 판들의 "앱에서 돌릴 명령"을 판 순서대로 돌린다(cozy upgrade --check, cozy upgrade 출력, 릴리스 노트)
cozy sync agents             # AGENTS.md와 cozy-helper 스킬을 설치한 판의 것으로
cozy check
mvn -o clean verify
```

- 설치한 판보다 높은 판은 적지 않는다. 그 판의 cozy 모듈이 저장소에 없어 오프라인 빌드가 실패한다.
- 앱에서 돌릴 명령은 `cozy upgrade --check`와 `cozy upgrade`가 판마다 보인다. 올린 뒤에는 채널 색인의 `appSteps`나 릴리스 노트에서 본다. 1.x에서 2.0.0으로 옮기는 절차는 [release.md](release.md)의 "앱을 2.0.0으로 올리기"에 있다.
- 옮기지 않은 앱은 그대로 빌드된다. 이전 판의 파일이 사용자 저장소에 있다.

## 지우기

- MSI: Windows 설정의 "설치된 앱"에서 Cozy를 제거한다. zip: 설치 폴더를 지우고 PATH에서 뺀다.
- 사용자 저장소, 설정, 받은 판은 남는다. 다시 설치하지 않을 것이면 `%LOCALAPPDATA%\Cozy`와 `%APPDATA%\Cozy`(Linux `~/.local/share/cozy`, `~/.config/cozy`)를 지운다.
- 사용자 저장소가 커지면 지워도 된다. 지우면 `mvn install`한 앱과 이전 판의 파일이 없어지므로, 지금 판으로 옮기지 않은 앱은 빌드되지 않는다.

## 채널 만들기 (관리자)

채널은 폴더 하나다. `cozy channel add`가 받은 릴리스를 확인하고 이 모양으로 만든다. 웹 서버와 GitLab도 같은 모양으로 올린다.

```text
<채널 폴더>/
├── index/cozy-channel.json        채널 색인. 채널 주소는 이 파일이다
├── 2.1.0/                         cozy-2.1.0-windows-x64.msi, cozy-offline-bundle-2.1.0-windows-x64.zip, cozy-offline-bundle-2.1.0.zip
└── 2.1.1/
```

### 릴리스 들여오기

1. 인터넷이 되는 PC에서 릴리스 자산을 폴더 하나에 받는다. 필요한 것은 MSI, 두 번들, `SHA256SUMS`, `cozy-channel.json`이다.

   ```bash
   gh release download v2.1.0 -R Salvia95/cozy-framework -D cozy-2.1.0 \
     -p '*.msi' -p 'cozy-offline-bundle-*.zip' -p SHA256SUMS -p cozy-channel.json
   ```

2. 그 폴더를 사내 반입 절차대로 망 안으로 옮긴다.
3. 망 안의 PC에서 채널에 더한다. 파일마다 SHA-256을 확인하고, 하나라도 다르면 아무것도 복사하지 않는다.

   ```powershell
   cozy channel add '\\fileserver\share\cozy' .\cozy-2.1.0 --dry-run   # 복사할 파일
   cozy channel add '\\fileserver\share\cozy' .\cozy-2.1.0
   ```

4. PC 하나에서 `cozy upgrade --check`로 새 판이 보이는지 확인한 뒤 사용자에게 알린다.

- 판 폴더의 파일을 먼저 두고 색인을 마지막에 바꾼다. 읽는 PC가 없는 파일을 가리키는 색인을 보지 않게 하기 위해서다. `cozy channel add`는 이 순서를 지킨다. 웹 서버와 GitLab에 손으로 올릴 때도 이 순서를 지킨다.
- 게시한 판의 파일은 바꾸지 않는다. 같은 판을 다른 파일로 다시 더하면 거부한다. 고칠 것이 있으면 새 판으로 낸다.
- 채널 폴더에 쓸 수 있는 사람은 모든 PC에 배포할 수 있는 사람이다. 쓰기 권한을 관리자로 좁힌다. SHA-256은 깨진 파일을 막고, 바꿔치기는 막지 않는다.
- 판마다 약 0.9GB(MSI와 두 번들)가 늘어난다. 아무 PC도 쓰지 않는 오래된 판은 판 폴더를 지우고, 그 판을 색인에서 빼려면 색인을 다시 만든다(빈 채널 폴더에 남길 판을 차례로 `cozy channel add`).

### 파일 서버

- 공유 폴더 하나를 채널 폴더로 쓴다(예: `\\fileserver\share\cozy`). 개발자 계정은 읽기만, 관리자 계정은 쓰기를 준다.
- 관리자 PC에서 `cozy channel add`를 그 공유 폴더에 바로 돌린다.
- PC의 채널 주소는 UNC 경로를 쓴다: `\\fileserver\share\cozy\index\cozy-channel.json`. 드라이브 문자(`Z:`)는 사용자마다 다를 수 있다.
- Linux·WSL은 공유 폴더를 마운트하고 그 경로를 쓴다(`/mnt/share/cozy/index/cozy-channel.json`).
- 판을 올리는 PC마다 300MB 안팎을 받는다. 많은 PC가 한꺼번에 올리면 공유 폴더가 느려질 수 있다.

### 사내 웹 서버

정적 파일만 내주면 된다. 서버의 API나 디렉터리 목록은 쓰지 않는다.

1. 로컬 폴더나 파일 서버에서 `cozy channel add`로 채널 폴더를 만든다.
2. 판 폴더를 먼저, `index/`를 마지막에 서버로 복사한다.

   ```bash
   rsync -a --exclude index/ ./cozy-channel/ web:/srv/cozy/
   rsync -a ./cozy-channel/index/ web:/srv/cozy/index/
   ```

   ```powershell
   robocopy .\cozy-channel \\web\wwwroot\cozy /E /XD index
   robocopy .\cozy-channel\index \\web\wwwroot\cozy\index
   ```

3. PC의 채널 주소: `https://intra.example/cozy/index/cozy-channel.json`.

- **캐시**: 색인은 바뀌므로 캐시하지 않게 한다(`Cache-Control: no-cache`). 판 폴더의 파일은 바뀌지 않으므로 캐시해도 된다.
- **확장자**: `.json`, `.msi`, `.zip`을 내주는지 확인한다. IIS는 MIME 형식에 등록된 확장자만 내준다. 없으면 `application/json`, `application/octet-stream`, `application/zip`으로 더한다.
- **HTTPS 인증서**: Windows는 Windows 인증서 저장소를 믿는다. 사내 CA가 PC에 들어 있으면 따로 설정하지 않는다. Linux는 JDK의 `cacerts`만 믿는다.
- **인증**: 읽기를 막아야 하면 머리 하나로 확인하는 방식을 쓴다.
  - 토큰 머리: 서버가 `Authorization: Bearer <토큰>`을 확인하면 PC는 `cozy channel set <주소>`만 한다(기본 머리). 다른 머리 이름이면 `--header <이름>`.
  - Basic 인증: `cozy channel set <주소> --header "Authorization: Basic"`으로 정하고, `COZY_CHANNEL_TOKEN`에 `사용자:비밀번호`를 Base64로 바꾼 값을 둔다.
  - Windows 통합 인증(NTLM, Kerberos)과 클라이언트 인증서는 쓸 수 없다.
- 토큰은 채널 주소와 같은 서버에만 보낸다. 파일을 다른 서버로 넘겨주면(리디렉션) 토큰 없이 받으므로, 그 서버는 인증 없이 내줘야 한다.

nginx 예:

```nginx
location /cozy/ {
    alias /srv/cozy/;
}
location = /cozy/index/cozy-channel.json {
    alias /srv/cozy/index/cozy-channel.json;
    add_header Cache-Control "no-cache";
}
```

IIS 예(`index\web.config`):

```xml
<configuration>
  <system.webServer>
    <staticContent>
      <clientCache cacheControlMode="DisableCache" />
    </staticContent>
  </system.webServer>
</configuration>
```

### GitLab

Generic Package Registry에 올린다. 패키지 이름은 `cozy` 하나이고, 채널 폴더의 폴더 이름을 패키지 판으로 쓴다. 판 폴더 `2.1.0/`의 파일은 패키지 판 `2.1.0`에, 색인은 패키지 판 `index`에 둔다. 그러면 색인의 상대 경로(`../2.1.0/…`)가 그대로 맞는다.

1. 로컬에서 `cozy channel add`로 채널 폴더를 만든다.
2. 판의 파일을 먼저, 색인을 마지막에 올린다.

   ```bash
   API=https://gitlab.example/api/v4/projects/42/packages/generic/cozy
   for f in 2.1.0/*; do
     curl --fail --header "PRIVATE-TOKEN: $TOKEN" --upload-file "$f" "$API/$f"
   done
   curl --fail --header "PRIVATE-TOKEN: $TOKEN" --upload-file index/cozy-channel.json "$API/index/cozy-channel.json"
   ```

3. PC의 채널 주소: `https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json`.
4. PC 하나에서 `cozy upgrade --check`로 새 판이 보이는지 확인한다.

- **올리는 토큰**: `api` 범위의 개인 토큰이나 프로젝트 토큰(Developer 이상), 또는 `write_package_registry` 범위의 배포 토큰. CI 작업이면 `JOB-TOKEN: $CI_JOB_TOKEN`.
- **읽는 토큰**: PC마다 개인 토큰을 만들지 않고, `read_package_registry` 범위의 배포 토큰 하나를 나눠 쓰는 것을 권한다. 읽기만 할 수 있다.

  ```powershell
  cozy channel set https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json --header DEPLOY-TOKEN
  [Environment]::SetEnvironmentVariable('COZY_CHANNEL_TOKEN', '<배포 토큰>', 'User')
  ```

  개인 토큰이면 `--header PRIVATE-TOKEN`이다.
- **색인 다시 올리기**: 판을 더할 때마다 `index/cozy-channel.json`을 같은 이름으로 다시 올린다. 프로젝트 설정(Settings > Packages and registries > Duplicate packages)에서 Generic 패키지의 중복을 막았다면 예외에 `index`를 더한다.
- **올린 뒤 확인**: GitLab의 판과 설정에 따라 패키지 판 이름이나 같은 이름의 파일을 다루는 방식이 다를 수 있다. 4번의 `cozy upgrade --check`가 새 판을 보여야 끝난 것이다. 보이지 않으면 `curl --header "DEPLOY-TOKEN: …" <채널 주소>`로 받은 색인이 올린 파일과 같은지 본다.
- GitLab이 파일을 객체 저장소로 넘겨주면 토큰 없이 받는다(토큰은 GitLab 서버에만 보낸다). 넘겨받는 주소는 GitLab이 서명한 주소라 따로 인증하지 않는다.
- GitLab Releases(릴리스 페이지의 자산 링크)는 채널로 쓰지 않는다. 판마다 주소가 달라 색인의 상대 경로가 맞지 않는다.
