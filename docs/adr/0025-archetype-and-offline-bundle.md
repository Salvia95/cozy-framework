# ADR-0025 아키타입과 오프라인 번들

- 상태: 제안 (2026-11-06, M7 보고에서 승인 요청)
- 출처: M7 구현 (PRD §6.6, §9.7, §10). ADR-0013(프레임워크를 의존성으로), ADR-0017(버전 정책)을 따른다.

## 결정

### 1. 옵션 차이는 Velocity `#if`와 생성 후처리 스크립트로 나눈다

- **파일 안의 차이**: Velocity `#if`로 처리한다. 대상은 POM 의존성과 `HomeService`(트랜잭션, 이벤트 발행)다.
- **파일 단위의 차이**: `archetype-post-generate.groovy`가 처리한다. 옵션에 맞지 않는 파일을 지운다(PRD §6.6).
  - 없음: `MemoryNoteStore`만 남는다.
  - persistence: `SqliteNoteStore`, `NoteMapper`(Java·XML), 마이그레이션을 쓴다.
  - events: `home/api/NoteAdded`, `NoteAddedListener`, 이벤트 샘플이 더해진다.
- **옵션 검사**: 같은 스크립트가 한다. 검사 대상은 다음과 같다.
  - `events=true`면서 `persistence=false`
  - `appId` 형식
  - `appName`의 특수 문자(`" \ < > & $ #`). POM `<name>`과 `application.yml`의 따옴표 문자열에 그대로 들어가기 때문이다
  - true/false가 아닌 값
- **검사 실패**: 만들던 폴더를 지우고 실패한다. 실패로 닫는다.
  - 아키타입 플러그인의 `validationRegex`는 대화형 입력에서만 검사한다. `-D`로 준 값은 검사하지 않는다. 그래서 같은 검사를 스크립트에 다시 둔다.
- **Velocity를 거치지 않는 파일**: Thymeleaf 템플릿, SQL, JSON은 필터링하지 않는다. `${...}`, `#`를 그대로 둔다.
  - MyBatis XML은 패키지 이름 때문에 필터링한다. `#{...}`는 `${h}{...}`로 쓴다(`#set( $h = '#' )`).

### 2. home 샘플은 옵션마다 같은 화면, 다른 저장 방식

- 화면(list-with-filter, 등록 폼, 최근 활동), 스펙, 흐름 테스트는 세 조합이 같다.
- 차이는 저장소 구현과 활동을 기록하는 쪽뿐이다. 그래서 생성 직후의 앱이 옵션과 상관없이 같은 검사(C1~C7)를 받는다.
- 최근 활동은 화면 DTO record(`HomeViews.Activity`)로 감싼다. 이렇게 해야 렌더링 검사(C3)가 스펙 fixture의 모양까지 확인한다. 모델 타입이 `java.util.List`면 원소 모양을 검사할 수 없다.

### 3. 생성 앱의 부모 버전과 AGENTS.md는 프레임워크 빌드에서 정한다

- **부모 버전**: 아키타입의 `pom.xml` 템플릿만 Maven 리소스 필터링(`@` 구분자)을 받는다. 그래서 `cozy-parent` 버전이 아키타입 버전과 같아진다. Velocity의 `${...}`는 건드리지 않는다.
- **AGENTS.md**: 원본은 `docs/AGENTS.md` 하나다. 빌드가 아키타입에 복사한다.
- **`.gitignore`**: 아키타입 jar는 `.gitignore`를 담지 않는다(패키징 기본 제외). 그래서 `__gitignore__`로 두고 `gitignore` 속성(값 `.gitignore`)으로 이름을 바꾼다.

### 4. 아키타입 검증은 생성 → verify -Pdist까지

- **정상 조합**: `archetype:integration-test`가 옵션 3조합을 생성하고 `verify -Pdist`를 돌린다. `verify.groovy`가 옵션별 파일, 의존성, appName(한글), app-image를 확인한다.
- **잘못된 조합**: invoker(`src/it`)가 생성이 실패하고 폴더가 남지 않는지 본다.
- **설치**: 생성 앱이 쓰는 cozy 모듈은 아키타입 POM의 test 범위 의존성이다. 그래서 리액터 순서가 맞고, `invoker:install`이 통합 테스트 전에 로컬 저장소에 넣는다.
- **추가 플러그인 없음**: maven-archetype-plugin, maven-invoker-plugin은 ADR-0017 표에 있다.

### 5. 아키텍처 검사는 마지막 클래스 폴더로 테스트 클래스를 가린다

- **문제**: Spring Modulith의 기본 ArchUnit 옵션(`DoNotIncludeTests`)은 경로 어디에든 `/target/test-classes/`가 있으면 뺀다. 아키타입 통합 테스트의 앱은 `cozy-archetype/target/test-classes/projects/...` 아래에 생긴다. 그래서 메인 클래스까지 빠져 C1이 "No classes found"로 실패했다.
- **결정**: cozy-test의 `MainClassesOnly`가 경로의 마지막 클래스 폴더(`target/classes` 또는 `target/test-classes`)로 판단한다. 이벤트 검사(C6)의 메인 클래스 판별도 같은 기준을 쓴다.
- **영향**: 검사 범위는 줄지 않는다. 일반 프로젝트에서는 결과가 같다.

### 6. 오프라인 번들은 빈 로컬 저장소에서 실제 빌드로 채운다

- **채우는 방법**: 의존성 목록을 따로 관리하지 않는다. 빈 `maven.repo.local`로 다음을 실제로 돌리면 필요한 것이 모두 받아진다.
  - 프레임워크 전체 빌드(테스트, invoker 픽스처 포함)
  - 옵션 3조합의 `verify`와 `-Pdist package`
- **원격 표시 제거**: `_remote.repositories`, `*.lastUpdated`를 지운다. 그래야 오프라인 PC의 Maven 설정(사내 미러 id 등)과 상관없이 저장소를 그대로 쓴다.
- **Maven 설정**: 설치 스크립트는 번들의 Maven 배포판을 풀고, 그 Maven의 `conf/settings.xml`에 `localRepository`와 `offline=true`를 쓴다. 사용자의 `~/.m2/settings.xml`은 건드리지 않는다.
- **JDK**: 번들에 넣지 않는다. 사내 표준 배포본을 따른다. 앱 사용자 PC는 app-image에 런타임이 있어 JDK가 필요 없다.
- **스크립트**: bash와 PowerShell을 같이 둔다(PRD §9.7). PowerShell 파일은 UTF-8 BOM으로 저장한다(Windows PowerShell 5.1).
- **검증**: Linux에서는 `verify-offline.sh`가 `unshare --net`으로 네트워크 장치가 없는 상태를 만든다. 그 상태에서 번들만으로 설치, 3조합 생성, `mvn -o verify`, `mvn -o -Pdist package`까지 한다. 이때 `HOME`과 `user.home`도 빈 폴더로 바꿔 기존 `~/.m2`를 쓰지 않게 한다. Windows는 사용자가 네트워크를 끊은 PC에서 확인한다(windows-verification.md M7).

### 7. SQLite 네이티브 라이브러리는 app-image 안에서 읽는다

- **문제**: sqlite-jdbc는 기본으로 실행할 때마다 `%TEMP%`에 DLL을 풀어 읽는다. AppLocker 같은 정책이 임시 폴더의 DLL 실행을 막는 PC가 있다(M6 남은 위험).
- **결정**: 패키징이 플랫폼에 맞는 라이브러리를 실행 폴더의 앱 폴더(Windows `app\native\`, Linux `lib/app/native/`)에 꺼내 둔다. `-Dorg.sqlite.lib.path=$APPDIR/native`와 `-Dorg.sqlite.lib.name`을 실행 옵션에 넣는다.
- **검증**: 종단 검증이 파일과 옵션을 확인한다.
  - 옵션이 있으면 임시 폴더에 sqlite 파일이 생기지 않는다.
  - 옵션을 빼면 임시 폴더에 풀린다.
  - 위 두 가지는 실제 app-image로 확인했다.

## 결과

- 새 의존성과 플러그인은 없다.
- 번들 크기는 프레임워크 검사 도구(vnu 등)와 jpackage용 플러그인을 포함해 수백 MB다. 실제 크기는 docs/conventions/performance.md에 적는다.
- 번들의 Maven 저장소는 만든 시점의 버전으로 고정된다. 프레임워크 버전을 올리면 번들을 다시 만든다.

## 보완 (2026-11-12, 사용자 결정 "권장안으로 진행해줘": Windows x64 번들에 JDK를 담는다)

### 배경

- 6의 결정은 "JDK는 번들에 넣지 않는다. 사내 표준 배포본을 따른다"였다.
- 그래서 오프라인 PC에는 번들과 별도로 JDK 25를 반입하고 설치해야 했다. 사내 표준 배포본이 25가 아니면 이것이 설치의 첫 걸림돌이 된다.
- 사용자가 번들에 컴팩트한 JDK를 함께 넣을 수 있는지 물었고, 권장안(아래)을 골랐다.

### 결정

1. **번들은 두 가지다.**
   - `cozy-offline-bundle-{버전}.zip`: 지금과 같다. 운영체제와 상관없고 JDK가 없다. 6의 JDK 결정은 이 번들에 그대로 적용된다.
   - `cozy-offline-bundle-{버전}-windows-x64.zip`: 같은 내용에 `jdk/`를 더한다. 안의 폴더 이름에도 플랫폼을 붙인다.
   - 대상이 폐쇄망 Windows PC라 Windows x64만 낸다.
2. **JDK는 Eclipse Temurin 25다.** 배포판 이름과 SHA-256을 `distribution/offline-bundle/jdk.env`에 고정한다. Adoptium API의 checksum과 릴리스 자산의 `.sha256.txt`가 같은 것을 확인했다. 판은 ADR-0017 표를 따른다.
3. **줄이는 것은 `lib/src.zip`(약 50MB)뿐이다.**
   - Temurin 25에는 `jmods/`가 없다. `jlink`는 JDK 자기 런타임에서 링크한다(JEP 493). 이때 `jlink`는 모듈에 속한 파일의 해시를 확인한다.
   - 직접 확인했다. `man/`을 지우면 `SHA-512 sum check failed`, `conf/net.properties`를 고치면 `... has been modified`로 `jlink`가 실패한다. `lib/src.zip`은 모듈 파일이 아니어서 지워도 된다.
   - `mvn -Pdist package`가 `jpackage`(안에서 `jlink`)를 쓰므로, 모듈 파일을 건드리면 앱 패키징이 깨진다.
   - `jlink`로 만든 작은 런타임은 쓰지 않는다. 개발 PC에는 `javac`(앱 컴파일, cozy-cli의 파서), `jpackage`, `jlink`와 그 원료가 모두 필요하다.
4. **번들 JDK는 cozy 명령과 설치한 Maven만 쓴다.** 시스템의 `JAVA_HOME`과 PATH 설정은 바꾸지 않는다.
   - `cozy`·`cozy.ps1`은 번들(또는 설치) 폴더에 `jdk/`가 있으면, 자기 프로세스 안에서만 `JAVA_HOME`을 그 JDK로 두고 PATH 앞에 `jdk/bin`을 더한다.
   - `cozy install`은 `jdk/`를 설치 폴더에 복사한다. 이전 설치의 `jdk/`는 먼저 지운다. 판이 다른 JDK가 남지 않게 하기 위해서다.
   - `cozy install`은 `bin/mvn`(Windows `bin\mvn.cmd`)을 만든다. 설치 폴더의 Maven을 부르고, 설치 폴더에 `jdk/`가 있으면 그 JDK로 돌린다. PATH 안내는 `<설치 폴더>/bin` 하나가 된다.
   - Maven이 번들 JDK로 돌면 나머지가 따라온다. 테스트 포크, `CozyPackager`(`java.home`의 java), `jpackage`가 모두 Maven의 `java.home`을 쓴다.
   - IDE는 자기 설정을 쓴다. 번들 README에 Project SDK와 Maven JRE를 `<설치 폴더>\jdk`로 지정하라고 적는다.
   - 번들 JDK의 파일을 고치지 않는다(사내 인증서를 `cacerts`에 더하는 것 등). 3의 이유로 `-Pdist`가 실패한다. 인증서가 필요하면 별도 truststore를 JVM 옵션으로 준다.
5. **라이선스와 소스**
   - JDK는 `GPL-2.0-only WITH Classpath-exception-2.0`이다. ADR-0028의 약한 카피레프트 묶음에 들고, 바이너리를 재배포할 때 소스를 얻는 방법을 함께 줘야 한다.
   - 소스 묶음(`OpenJDK25U-jdk-sources_*.tar.gz`, 약 120MB)은 번들 안이 아니라 **같은 GitHub Release의 자산**으로 올린다.
     - 번들 안에 넣으면 Windows 번들이 120MB 더 커진다. 번들을 쓰는 데는 필요 없다.
     - GPLv2 §3은 바이너리를 받는 곳에서 소스도 받을 수 있게 하면 소스를 배포한 것으로 본다.
     - 상위 링크에 기대지 않는다. ADR-0028이 소스 jar를 번들에 담는 이유(상위 링크는 사라질 수 있다)와 같다. 우리 릴리스에 같이 둔다.
   - `jdk/legal/`과 `jdk/NOTICE`는 그대로 둔다.
   - `licenses/THIRD-PARTY.txt`에 JDK 줄을 적는다. 배포판 이름, 라이선스, `jdk/legal/`, 소스 묶음 이름이 들어간다. `BundleLicenses`의 `--jdk-distribution`과 `--jdk-sources`는 함께 줘야 하고, 하나만 주면 실패로 닫는다.
6. **만들기와 검증**
   - `add-jdk.sh`가 JDK 없는 번들에서 플랫폼 번들을 만든다. 순서는 받기, SHA-256 확인, `jdk/`에 풀기, `lib/src.zip` 지우기, 도구와 판 확인, 라이선스 목록 다시 만들기다.
   - JDK 번들은 릴리스 워크플로(Linux)가 만든다. 그래서 PowerShell 판은 두지 않는다. Windows에서 만들려면 WSL에서 돌린다. 6의 "bash와 PowerShell을 같이 둔다"는 사용자가 실행하는 스크립트(`cozy`, `cozy.ps1`)와 `build-bundle`에 그대로 적용된다.
   - 오프라인 검증은 두 번 한다. 한 번은 JDK 없는 번들이다. 다른 한 번은 같은 JDK의 Linux x64 판을 담은 번들이다(검증용, 릴리스로 내지 않는다).
   - 두 번째 검증에서는 `verify-offline.sh`가 시스템 JDK 자리에 부르면 실패하는 `java`를 둔다(`JAVA_HOME`도 그 자리). 설치, 생성, `cozy add`, `mvn -o verify`, `mvn -o -Pdist package`가 모두 번들 JDK로 돌아야 통과한다.
   - Windows x64 번들은 Linux에서 실행할 수 없다. 여기서는 `add-jdk.sh`가 파일, 도구, 판을 확인하는 것까지다. 실행은 Windows에서 확인한다(windows-verification.md).
   - bash `cozy`의 JDK 선택과 설치는 `BundleCommandsTest`가 가짜 JDK와 가짜 Maven으로 확인한다.
7. **JDK 판올림**: JDK는 분기마다(1·4·7·10월) 보안 패치가 나온다. ADR-0017 확인 때 같이 보고 `jdk.env`를 올린다. JDK 보안 패치는 패치 릴리스로 낼 수 있다(release.md의 "보안 수정").

### 결과

- 릴리스 자산은 네 개다. 두 번들, JDK 소스 묶음, `SHA256SUMS`.
- 새 Maven 의존성과 플러그인은 없다. 반입하는 것이 하나 늘었다(Temurin JDK, ADR-0017 표).
- 크기(1.3.0-SNAPSHOT 실측): JDK 없는 번들 233MB, Windows x64 번들 318MB(+85MB). 풀면 JDK가 240MB다(원래 292MB에서 `lib/src.zip` 51MB를 뺐다). JDK 소스 묶음은 116MB다.

### 도입 검토 (CLAUDE.md: 이유, 라이선스, 폐쇄망 반입 영향)

| 항목 | 이유 | 라이선스 | 폐쇄망 반입 |
| --- | --- | --- | --- |
| Eclipse Temurin JDK 25.0.5+8 (Windows x64, 검증용 Linux x64) | 오프라인 PC에 JDK를 따로 반입·설치하지 않게 한다 | GPL-2.0 with Classpath Exception(구성 요소별 라이선스는 `jdk/legal/`). 소스 묶음을 같은 릴리스에 올린다 | Windows x64 번들 안에 들어 있다. 따로 반입할 것이 없다 |
