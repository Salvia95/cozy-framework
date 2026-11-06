# Cozy 오프라인 번들

인터넷이 없는 PC에서 Cozy 앱을 만들고, 빌드하고, 패키징하기 위한 묶음이다(PRD §9.7).

## 번들에 들어 있는 것

| 항목 | 내용 |
| --- | --- |
| `repository/` | Maven 로컬 저장소. 프레임워크와 앱 빌드·검사·패키징에 필요한 모든 라이브러리와 플러그인 |
| `apache-maven-3.10.0-bin.zip` | Maven 배포판 |
| `install-offline.ps1`, `install-offline.sh` | 오프라인 PC에 설치 |
| `new-app.ps1`, `new-app.sh` | 새 앱 만들기 (아키타입) |
| `docs/` | AGENTS.md, 규약 문서(`conventions/`), 결정 기록(`adr/`) |
| `COZY_VERSION` | 프레임워크 버전 |
| `LICENSE`, `NOTICE` | Cozy의 라이선스(Apache-2.0) |
| `licenses/THIRD-PARTY.txt` | `repository/`에 든 아티팩트 전부의 라이선스 목록(ADR-0028) |
| `licenses/license-overrides.txt` | POM으로 라이선스를 정할 수 없던 아티팩트와 확인한 근거 |

약한 카피레프트 라이선스(EPL, MPL, LGPL 등)만 있는 jar는 소스 jar(`*-sources.jar`)가 같은 폴더에 함께 들어 있다.

JDK 25는 들어 있지 않다. 사내 표준 배포본(Temurin 등)을 따로 설치한다. 앱 사용자 PC에는 JDK가 필요 없다(app-image에 런타임이 들어 있다).

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

## 설치와 사용 (오프라인 PC)

```powershell
Expand-Archive cozy-offline-bundle-1.0.0-SNAPSHOT.zip C:\cozy-bundle
cd C:\cozy-bundle\cozy-offline-bundle-1.0.0-SNAPSHOT
powershell -ExecutionPolicy Bypass -File .\install-offline.ps1           # %USERPROFILE%\cozy에 설치
$env:Path = "$env:USERPROFILE\cozy\apache-maven-3.10.0\bin;" + $env:Path

cd C:\work
powershell -ExecutionPolicy Bypass -File $env:USERPROFILE\cozy\new-app.ps1   # 값을 물어본다
cd voc-assistant
mvn -o verify
mvn -o -Pdist package
```

- **설치 스크립트가 하는 일**: Maven을 풀고, 그 Maven의 `conf\settings.xml`을 다음처럼 설정한다.
  - 로컬 저장소를 번들 저장소로 둔다.
  - `offline=true`로 둔다.
  - 이 Maven으로는 네트워크에 나가지 않는다.
- **PowerShell에서 `-D` 인수**: 따옴표로 감싼다(`"-Dpackage=com.example.voc"`). 감싸지 않으면 점에서 인수가 나뉜다.
- **새 앱의 옵션**:
  - `appId`: 영문 소문자·하이픈. 기본값은 artifactId다.
  - `appName`: 한글을 쓸 수 있다.
  - `persistence`: 기본 `true`.
  - `events`: 기본 `false`. `persistence=true`가 필요하다.
  - 자세한 내용은 `docs/conventions/getting-started.md`에 있다.
