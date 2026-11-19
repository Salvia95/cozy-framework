# Cozy 오프라인 번들

인터넷이 없는 PC에서 Cozy 앱을 만들고, 빌드하고, 패키징하기 위한 묶음이다(PRD §9.7).

## 번들 두 가지

Windows PC는 보통 번들 대신 같은 릴리스의 설치 파일 `cozy-{버전}-windows-x64.msi`를 쓴다. 더블클릭하면 아래 Windows x64 번들의 내용을 `%LOCALAPPDATA%\Programs\Cozy`에 설치하고(관리자 권한 불필요) 사용자 PATH에 `bin`을 등록한다. 제거는 "설치된 앱"에서 한다(ADR-0039). zip 번들은 MSI를 쓸 수 없는 PC와 Linux·WSL용이다.

| 번들 | 쓰는 곳 |
| --- | --- |
| `cozy-offline-bundle-{버전}-windows-x64.zip` | Windows x64 PC. JDK 25(Eclipse Temurin)가 `jdk/`에 들어 있어 따로 설치하지 않는다 |
| `cozy-offline-bundle-{버전}.zip` | 운영체제와 상관없다. JDK 25(사내 표준 배포본, Temurin 등)를 따로 설치한다 |

두 번들은 `jdk/`만 다르다. 앱 사용자 PC에는 어느 쪽이든 JDK가 필요 없다(app-image에 런타임이 들어 있다).

## 번들에 들어 있는 것

| 항목 | 내용 |
| --- | --- |
| `jdk/` | (Windows x64 번들만) Eclipse Temurin JDK 25. `lib/src.zip`만 뺐다. 라이선스는 `jdk/legal/`과 `jdk/NOTICE`, 소스 묶음은 같은 릴리스의 `OpenJDK25U-jdk-sources_*.tar.gz`(ADR-0025 보완) |
| `repository/` | 번들 저장소. 프레임워크와 앱 빌드·검사·패키징에 필요한 모든 라이브러리와 플러그인. 설치하면 Maven이 읽기만 한다 |
| `apache-maven-3.10.1-bin.zip` | Maven 배포판 |
| `cozy.cmd`(+`cozy.ps1`), `cozy` | Cozy 명령. `cozy install`(오프라인 PC에 설치), `cozy new`(새 앱), `cozy add`·`cozy link`(앱에 구조 더하기), `cozy describe`·`cozy check`·`cozy recipes`, `cozy enable`·`cozy sync`·`cozy set`·`cozy migrate`, `cozy channel`·`cozy update`·`cozy upgrade`(개발 도구 업데이트), `cozy version`, `cozy help` |
| `lib/cli/` | cozy-cli(`cozy-cli.jar`와 `lib/`의 cozy-rules, AttoParser, SnakeYAML). `add`, `link`, `describe`, `check`, `recipes`, `enable`, `sync`, `set`, `migrate`, `channel`, `update`, `upgrade`를 맡는다(ADR-0032, ADR-0042) |
| `docs/` | 앱의 에이전트 문서 원본(`agents/`: AGENTS.md, cozy-helper 스킬), 규약 문서(`conventions/`), 결정 기록(`adr/`) |
| `COZY_VERSION` | 프레임워크 버전 |
| `LICENSE`, `NOTICE` | Cozy의 라이선스(Apache-2.0) |
| `licenses/THIRD-PARTY.txt` | `repository/`에 든 아티팩트 전부의 라이선스 목록(ADR-0028). Windows x64 번들은 JDK와 그 소스 묶음도 적는다 |
| `licenses/license-overrides.txt` | POM으로 라이선스를 정할 수 없던 아티팩트와 확인한 근거 |

약한 카피레프트 라이선스(EPL, MPL, LGPL 등)만 있는 jar는 소스 jar(`*-sources.jar`)가 같은 폴더에 함께 들어 있다.


## 만들기 (인터넷 PC, 프레임워크 레포 루트에서)

```powershell
powershell -ExecutionPolicy Bypass -File distribution\offline-bundle\build-bundle.ps1
```

```bash
distribution/offline-bundle/build-bundle.sh
```

1. 비어 있는 로컬 저장소로 프레임워크 전체를 빌드한다(테스트 포함).
2. 아키타입으로 옵션 3조합 앱(없음 / persistence / persistence+events)을 만들어 `verify`와 `-Pdist package`를 돌린다.
3. 저장소에서 원격 저장소 표시(`_remote.repositories`)를 지운다. 오프라인 PC의 Maven 설정(사내 미러 등)과 상관없이 쓸 수 있게 하기 위해서다.
4. 저장소의 라이선스 목록을 만든다. 허용 라이선스를 확인할 수 없는 아티팩트가 있으면 실패한다. 약한 카피레프트만 있는 jar는 소스 jar를 받아 담는다.
5. Maven 배포판의 SHA-512를 확인하고 `build/offline-bundle/cozy-offline-bundle-{버전}.zip`을 만든다.

- **Maven 배포판**: 기본은 Maven Central(repo.maven.apache.org)에서 받는다. 이미 받아 둔 파일이 있으면 두 번째 인자(`-MavenZip`)로 경로를 준다.
- **소스 jar**: 기본은 Maven Central에서 받는다. 사내 미러를 쓰려면 `SOURCES_REPO`(bash) 또는 `-SourcesRepo`(PowerShell)에 주소를 준다.
- **라이선스 검사가 실패하면**: 메시지의 아티팩트를 상위 저장소에서 확인한다. 허용 라이선스면 `distribution/offline-bundle/license-overrides.txt`에 근거와 함께 적는다. 아니면 의존성을 바꾼다.
- **빌드 PC 운영체제**: Windows와 Linux 어느 쪽에서 만들어도 된다. 번들의 라이브러리와 플러그인은 운영체제와 상관없다(sqlite-jdbc는 모든 OS의 네이티브 라이브러리를 담는다).

### JDK를 담은 번들 (bash: Linux, WSL, CI)

```bash
distribution/offline-bundle/add-jdk.sh build/offline-bundle/cozy-offline-bundle-2.4.0-SNAPSHOT.zip windows-x64
```

- `distribution/offline-bundle/jdk.env`에 고정한 Temurin 배포판을 받아 SHA-256을 확인하고, 번들의 `jdk/`에 푼다. 이미 받아 둔 파일이 있으면 세 번째 인자로 경로를 준다.
- `lib/src.zip`만 지운다. 이 JDK는 jmods 없이 자기 런타임에서 jlink하므로, 다른 파일(`man/`, `legal/`, `conf/` 등)을 지우거나 고치면 `jlink`와 `mvn -Pdist package`가 거부한다.
- 라이선스 목록에 JDK와 소스 묶음(`jdk.env`의 `JDK_SOURCES`)을 넣어 다시 만든다. 소스 묶음은 `distribution/release/jdk-sources.sh`로 받아 같은 릴리스에 올린다.
- `linux-x64`는 오프라인 검증용이다. `verify-offline.sh`가 시스템 JDK를 막은 상태에서 번들 JDK만으로 끝까지 돌린다.

## 설치와 사용 (오프라인 PC)

설치, 채널 정하기, 새 판으로 올리기, 앱 옮기기, 지우기의 전체 절차와 관리자가 사내 채널(파일 서버, 웹 서버, GitLab)을 만드는 절차는 `docs/conventions/install-and-update.md`에 있다.

```powershell
Expand-Archive cozy-offline-bundle-2.4.0-SNAPSHOT-windows-x64.zip C:\cozy-bundle
cd C:\cozy-bundle\cozy-offline-bundle-2.4.0-SNAPSHOT-windows-x64
.\cozy.cmd install                      # %USERPROFILE%\cozy에 설치 (다른 곳: .\cozy.cmd install D:\cozy)
$env:Path = "$env:USERPROFILE\cozy\bin;" + $env:Path

cd C:\work
cozy new                                 # 값을 물어본다
cd voc-assistant
cozy add crud task --entity Task --label "할 일" --icon checklist --field "title:text!:제목"   # 구조 더하기 (cozy-helper 스킬)
mvn -o verify
mvn -o -Pdist package
```

- **이미 있는 앱**: 새 번들을 설치한 뒤 앱 폴더에서 `cozy sync agents`를 실행하면 `AGENTS.md`(없을 때)와 `cozy-helper` 스킬이 설치한 cozy 명령의 것으로 맞춰진다. 앱에서 고친 `AGENTS.md`는 `--force`를 줘야 바뀐다.
- **명령 형식**: `cozy <동작> [인자...]`. Windows는 `cozy.cmd`가 `cozy.ps1`을 실행 정책과 상관없이 부른다. Linux·WSL은 `cozy`(bash)를 쓴다(`./cozy install` → `~/cozy/bin/cozy new`). Linux에 설치해도 `bin/`에 `cozy`와 `mvn`이 생긴다.
- **`cozy install`이 하는 일**: Maven을 풀고 `bin\cozy.cmd`와 `bin\mvn.cmd`를 설치 폴더에 둔다. 그 Maven의 `conf\settings.xml`을 다음처럼 설정한다.
  - 로컬 저장소는 사용자 저장소다. Windows는 `%LOCALAPPDATA%\Cozy\repository`(`${env.LOCALAPPDATA}/Cozy/repository`), Linux는 `~/.local/share/cozy/repository`(`${env.HOME}/.local/share/cozy/repository`)다. Maven은 이곳에만 쓰고(`mvn install`한 앱, 잠금 파일), 다시 설치하거나 지워도 남는다.
  - 설치 폴더의 `repository`는 읽기만 하는 꼬리 저장소다(늘 켜진 프로필 `cozy-bundle`의 `maven.repo.local.tail`, `${maven.home}/../repository`). 절대 경로가 없어 설치 폴더를 옮겨도 맞는다.
  - `offline=true`로 둔다. 이 Maven으로는 네트워크에 나가지 않는다.
- **다시 설치할 때**: 설치 폴더의 `repository`를 새 번들의 것으로 바꾸기 전에, 이전 저장소에만 있던 파일(이전 판의 cozy 모듈, 그 판만 쓰는 의존성, 예전 설치가 저장소에 쓴 앱)을 사용자 저장소로 옮긴다. 그래서 아직 판을 올리지 않은 앱도 오프라인으로 빌드된다. 사용자 저장소에 이미 있는 파일은 덮어쓰지 않는다.
- **MSI로 올릴 때**: `cozy upgrade`로 올린다. 설치하기 전의 저장소를 떠 두었다가 이전 판에만 있던 파일을 사용자 저장소로 옮긴다. MSI를 더블클릭해 올리면 MSI가 이전 판의 파일을 지우므로, 그 판을 쓰던 앱은 `pom.xml`의 cozy-parent 판을 올린다.
- **JDK**: 번들에 `jdk\`가 있으면 설치 폴더에 복사한다. 이전 설치의 `jdk\`는 지운다.
  - `cozy`와 `bin\mvn.cmd`는 그 JDK로 돈다. Maven이 그 JDK로 돌면 테스트, 패키징(`jpackage`)도 같은 JDK를 쓴다.
  - 시스템의 `JAVA_HOME`과 PATH 설정은 바꾸지 않는다. 다른 Java 프로그램에는 영향이 없다.
  - 번들 JDK의 파일을 고치지 않는다(사내 인증서를 `cacerts`에 더하는 것 등). 고치면 `mvn -Pdist package`의 jlink가 거부한다. 인증서가 필요하면 별도 truststore를 JVM 옵션으로 준다.
  - JDK 없는 번들이면 PATH의 JDK 25를 쓴다. `bin\mvn.cmd`는 시스템의 `JAVA_HOME`을 따른다.
- **IDE**: IntelliJ는 자기 설정의 JDK와 Maven을 쓴다. Project SDK와 Maven의 JRE를 `<설치 폴더>\jdk`로, Maven home을 `<설치 폴더>\apache-maven-3.10.1`으로 지정한다. 그 Maven의 `conf\settings.xml`을 읽어야 꼬리 저장소를 안다(User settings file을 바꾸지 않는다).
- **`cozy add`, `cozy link`, `cozy describe`, `cozy check`, `cozy recipes`, `cozy enable`, `cozy sync`, `cozy set`**: 앱 폴더(또는 그 아래)에서 실행한다. 설치 폴더의 `lib/cli/cozy-cli.jar`를 JDK로 실행하고, 앱이 쓰는 판의 cozy-ui jar를 사용자 저장소에서 먼저, 없으면 설치 폴더의 `repository/`에서 찾는다(`COZY_REPOSITORY`로 바꿀 수 있다). 네트워크를 쓰지 않는다. 작업 순서와 명령별 사용법은 앱의 cozy-helper 스킬(`.agents/skills/cozy-helper/`)에, 형식은 `docs/conventions/cli.md`에 있다.
- **`cozy channel`**: 개발 도구의 업데이트 채널(파일 서버, 웹 서버, GitLab, GitHub의 `cozy-channel.json`)을 정하고(`set`, `show`), 관리자가 받은 릴리스로 채널 폴더를 만든다(`add`). 어디서나 실행하고 네트워크를 쓰지 않는다. 형식은 `docs/conventions/channel.md`에 있다.
- **`cozy update`**: 채널의 새 판, 요약, 호환이 깨지는 판, 앱에서 돌릴 명령을 보인다. 아무것도 바꾸지 않는다. 채널을 정했으면 다른 cozy 명령 뒤에 하루에 한 번 새 판을 확인해 한 줄로 알린다.
- **`cozy upgrade [--to <판>]`**: 채널에서 새 판을 받아 SHA-256을 확인하고, 이 명령이 끝나면 설치한 모양대로(MSI는 `msiexec`, zip은 받은 번들의 `cozy install`) 다시 설치한다. 이전 판에만 있던 저장소 파일은 사용자 저장소로 옮겨 올리기 전의 앱도 빌드된다. 결과는 다음 cozy 명령이 처음에 한 줄로 보인다. 호환이 깨지는 판은 `--to`로 적는다. MSI를 더블클릭해 올리면 이전 판의 파일을 옮기지 않는다.
- **PowerShell에서 `-D` 인수**: 따옴표로 감싼다(`"-Dpackage=com.example.voc"`). 감싸지 않으면 점에서 인수가 나뉜다.
- **새 앱의 옵션**:
  - `appId`: 영문 소문자·하이픈. 기본값은 artifactId다.
  - `appName`: 한글을 쓸 수 있다.
  - `persistence`: 기본 `true`.
  - `events`: 기본 `false`. `persistence=true`가 필요하다.
  - 자세한 내용은 `docs/conventions/getting-started.md`에 있다.
