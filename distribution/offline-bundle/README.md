# Cozy 오프라인 번들

인터넷이 없는 PC에서 Cozy 앱을 만들고, 빌드하고, 패키징하기 위한 묶음이다(PRD §9.7).

## 번들 두 가지

| 번들 | 쓰는 곳 |
| --- | --- |
| `cozy-offline-bundle-{버전}-windows-x64.zip` | Windows x64 PC. JDK 25(Eclipse Temurin)가 `jdk/`에 들어 있어 따로 설치하지 않는다 |
| `cozy-offline-bundle-{버전}.zip` | 운영체제와 상관없다. JDK 25(사내 표준 배포본, Temurin 등)를 따로 설치한다 |

두 번들은 `jdk/`만 다르다. 앱 사용자 PC에는 어느 쪽이든 JDK가 필요 없다(app-image에 런타임이 들어 있다).

## 번들에 들어 있는 것

| 항목 | 내용 |
| --- | --- |
| `jdk/` | (Windows x64 번들만) Eclipse Temurin JDK 25. `lib/src.zip`만 뺐다. 라이선스는 `jdk/legal/`과 `jdk/NOTICE`, 소스 묶음은 같은 릴리스의 `OpenJDK25U-jdk-sources_*.tar.gz`(ADR-0025 보완) |
| `repository/` | Maven 로컬 저장소. 프레임워크와 앱 빌드·검사·패키징에 필요한 모든 라이브러리와 플러그인 |
| `apache-maven-3.10.0-bin.zip` | Maven 배포판 |
| `cozy.cmd`(+`cozy.ps1`), `cozy` | Cozy 명령. `cozy install`(오프라인 PC에 설치), `cozy new`(새 앱), `cozy add`·`cozy link`(앱에 구조 더하기), `cozy describe`·`cozy check`·`cozy recipes`, `cozy enable`·`cozy sync`·`cozy set`, `cozy version`, `cozy help` |
| `lib/cli/` | cozy-cli(`cozy-cli.jar`와 `lib/`의 cozy-rules, AttoParser, SnakeYAML). `add`, `link`, `describe`, `check`, `recipes`, `enable`, `sync`, `set`을 맡는다(ADR-0032) |
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
distribution/offline-bundle/add-jdk.sh build/offline-bundle/cozy-offline-bundle-1.3.0-SNAPSHOT.zip windows-x64
```

- `distribution/offline-bundle/jdk.env`에 고정한 Temurin 배포판을 받아 SHA-256을 확인하고, 번들의 `jdk/`에 푼다. 이미 받아 둔 파일이 있으면 세 번째 인자로 경로를 준다.
- `lib/src.zip`만 지운다. 이 JDK는 jmods 없이 자기 런타임에서 jlink하므로, 다른 파일(`man/`, `legal/`, `conf/` 등)을 지우거나 고치면 `jlink`와 `mvn -Pdist package`가 거부한다.
- 라이선스 목록에 JDK와 소스 묶음(`jdk.env`의 `JDK_SOURCES`)을 넣어 다시 만든다. 소스 묶음은 `distribution/release/jdk-sources.sh`로 받아 같은 릴리스에 올린다.
- `linux-x64`는 오프라인 검증용이다. `verify-offline.sh`가 시스템 JDK를 막은 상태에서 번들 JDK만으로 끝까지 돌린다.

## 설치와 사용 (오프라인 PC)

```powershell
Expand-Archive cozy-offline-bundle-1.3.0-SNAPSHOT-windows-x64.zip C:\cozy-bundle
cd C:\cozy-bundle\cozy-offline-bundle-1.3.0-SNAPSHOT-windows-x64
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
  - 로컬 저장소를 번들 저장소로 둔다.
  - `offline=true`로 둔다.
  - 이 Maven으로는 네트워크에 나가지 않는다.
- **JDK**: 번들에 `jdk\`가 있으면 설치 폴더에 복사한다. 이전 설치의 `jdk\`는 지운다.
  - `cozy`와 `bin\mvn.cmd`는 그 JDK로 돈다. Maven이 그 JDK로 돌면 테스트, 패키징(`jpackage`)도 같은 JDK를 쓴다.
  - 시스템의 `JAVA_HOME`과 PATH 설정은 바꾸지 않는다. 다른 Java 프로그램에는 영향이 없다.
  - 번들 JDK의 파일을 고치지 않는다(사내 인증서를 `cacerts`에 더하는 것 등). 고치면 `mvn -Pdist package`의 jlink가 거부한다. 인증서가 필요하면 별도 truststore를 JVM 옵션으로 준다.
  - JDK 없는 번들이면 PATH의 JDK 25를 쓴다. `bin\mvn.cmd`는 시스템의 `JAVA_HOME`을 따른다.
- **IDE**: IntelliJ는 자기 설정의 JDK와 Maven을 쓴다. Project SDK와 Maven의 JRE를 `<설치 폴더>\jdk`로, Maven home을 `<설치 폴더>\apache-maven-3.10.0`으로 지정한다.
- **`cozy add`, `cozy link`, `cozy describe`, `cozy check`, `cozy recipes`, `cozy enable`, `cozy sync`, `cozy set`**: 앱 폴더(또는 그 아래)에서 실행한다. 설치 폴더의 `lib/cli/cozy-cli.jar`를 JDK로 실행하고, cozy-ui jar는 설치 폴더의 `repository/`에서 읽는다(`COZY_REPOSITORY`로 바꿀 수 있다). 네트워크를 쓰지 않는다. 작업 순서와 명령별 사용법은 앱의 cozy-helper 스킬(`.agents/skills/cozy-helper/`)에, 형식은 `docs/conventions/cli.md`에 있다.
- **PowerShell에서 `-D` 인수**: 따옴표로 감싼다(`"-Dpackage=com.example.voc"`). 감싸지 않으면 점에서 인수가 나뉜다.
- **새 앱의 옵션**:
  - `appId`: 영문 소문자·하이픈. 기본값은 artifactId다.
  - `appName`: 한글을 쓸 수 있다.
  - `persistence`: 기본 `true`.
  - `events`: 기본 `false`. `persistence=true`가 필요하다.
  - 자세한 내용은 `docs/conventions/getting-started.md`에 있다.
