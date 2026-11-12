# Windows 검증 절차

WSL에서 검증할 수 없는 수용 기준을 Windows 11 x64에서 확인하는 절차다. 마일스톤마다 항목을 추가한다. 결과는 아래 기록 표에 남긴다.

## 준비

- JDK 25 (`java -version`으로 확인)
- WSL에서 `mvn verify`로 만든 `samples/reference-app/target/cozy-reference-app-1.0.0-SNAPSHOT.jar`
  - Windows 쪽 폴더(예: `C:\cozy-check\`)로 복사한다.
  - WSL 경로 `\\wsl$\...`에서 바로 실행하면 파일 잠금 동작이 달라질 수 있다.
- `distribution/windows-checks/runtime-checks.ps1`도 같은 폴더로 복사한다.

## M1 런타임 기반

PowerShell에서 실행한다.

```powershell
cd C:\cozy-check
powershell -ExecutionPolicy Bypass -File .\runtime-checks.ps1 -Jar .\cozy-reference-app-1.0.0-SNAPSHOT.jar
```

스크립트가 자동으로 확인하는 것:

1. 데이터 루트 `%LOCALAPPDATA%\cozy-reference`와 하위 폴더 6개가 생긴다.
2. `app.port`가 생기고, 그 포트로 `Host: 127.0.0.1:{port}` 요청이 403이 아니다.
3. `Host: evil.example:{port}` 요청은 403이다.
4. 두 번째 실행이 종료 코드 0으로 끝나고, 출력에 "이미 실행 중인 인스턴스가 있어 그 화면을 엽니다"가 나온다.
5. 로그 파일 `%LOCALAPPDATA%\cozy-reference\logs\app.log`가 생긴다.

직접 눈으로 확인할 것:

- [ ] 첫 실행에서 기본 브라우저가 `http://127.0.0.1:{port}/`를 연다. 아직 화면이 없으므로 오류 페이지가 정상이다.
- [ ] 두 번째 실행에서 브라우저 탭이 하나 더 열린다(같은 주소).

스크립트는 끝날 때 첫 번째 프로세스를 종료한다. 데이터 폴더는 남긴다. 다시 처음부터 확인하려면 `%LOCALAPPDATA%\cozy-reference`를 지운다.

## M2 영속성

M1 스크립트를 다시 실행하면 된다. M2부터 reference-app이 기동할 때 SQLite를 연다.

- [ ] 스크립트가 모두 통과한다. sqlite-jdbc의 Windows 네이티브 DLL이 로드되지 않으면 첫 실행이 기동 중에 끝난다.
- [ ] `%LOCALAPPDATA%\cozy-reference\data\app.db`가 생긴다.
- [ ] `logs\app.log`에 `[cozy] SQLite … {journal_mode=wal, …}` 줄이 있다.
- [ ] 같은 jar를 한 번 더 실행했다가 닫았을 때 `backup\`이 비어 있다. 변경이 없으면 백업하지 않는다.

sqlite-jdbc는 네이티브 DLL을 `%TEMP%`에 풀어서 로드한다. 사내 PC 정책(AppLocker 등)이 `%TEMP%`의 DLL 로드를 막으면 첫 실행이 실패한다. 이때는 `first.out.log`의 오류를 기록해 둔다. M6 패키징에서 DLL을 앱 폴더에 미리 넣는 방식으로 대응한다.

## M3 화면

새로 빌드한 jar를 dev 프로필로 실행한다. dev 프로필이면 패턴 카탈로그가 켜진다.

```powershell
java -jar .\cozy-reference-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

- [ ] 메모 목록이 Tabler 모양으로 그려진다. 사이드 내비가 있고, 글꼴은 Pretendard다.
- [ ] 검색어를 치면 0.3초 뒤 목록만 바뀐다. 화면 전체가 깜박이지 않고 주소창에 `?q=`가 붙는다.
- [ ] "새 메모"에서 제목을 비우고 저장하면 폼 아래에 "제목을 입력하세요"가 보인다.
- [ ] 저장하면 상세 화면으로 이동하고 초록 알림 "메모를 저장했습니다"가 보인다.
- [ ] 삭제를 누르면 확인 창이 뜨고, 지운 뒤 목록에 "메모를 지웠습니다"가 보인다.
- [ ] `http://127.0.0.1:{port}/cozy/dev/catalog`에 fragment 17개가 상태별 미리보기(24개)로 보인다.
- [ ] 랜선과 Wi-Fi를 끊고 브라우저 캐시를 비운 뒤(Ctrl+F5) 다시 열어도 같은 모양이다.
- [ ] 개발자 도구(F12) 콘솔에 CSP 위반 오류가 없다.

## M4 규칙과 테스트 기반

Windows의 경로 구분자(`\`)와 `file:/C:/...` URL에서도 규칙 검사가 같은 결과를 내는지 확인한다. 레포를 Windows에 받아 오프라인으로 빌드한다(의존성은 한 번 받아 둔 상태).

```powershell
mvn -o verify
```

- [ ] `BUILD SUCCESS`. 실패하면 `[R`, `[C`로 시작하는 줄을 기록한다.
- [ ] `Cozy :: Samples :: Violations`의 invoker 결과가 `Passed: 7`이다.
- [ ] reference-app의 `ArchitectureTest`(2개)와 `RenderingTest`(3개)가 통과한다.

## M5 이벤트

새로 빌드한 jar를 실행한다(`java -jar .\cozy-reference-app-1.0.0-SNAPSHOT.jar`). 브라우저가 대시보드로 열린다.

- [ ] 사이드 내비에 대시보드, 메모 순으로 보인다.
- [ ] 메모를 하나 만든 뒤 대시보드로 가면, 10초 안에 "최근 활동"에 "메모를 만들었습니다: (제목)"이 나타난다. 새로고침하지 않아도 된다.
- [ ] 전체 메모 수와 고정한 메모 수가 맞다.
- [ ] 메모를 만든 직후 작업 관리자에서 java 프로세스를 끝내고 다시 실행해도, 그 메모의 활동이 한 번만 보인다.
- [ ] `mvn -o verify`에서 cozy-starter-events의 `EventContractTest` 9개가 통과한다. 강제 종료 테스트도 포함된다.

## M6 배포 장치

레포를 Windows 쪽에 둔다(예: `C:\cozy-framework`). PowerShell에서 JDK 25와 Maven 3.10.0으로 실행한다.

### 종단 검증 (M6 완료 기준)

```powershell
cd C:\cozy-framework
mvn -Pe2e install
```

- [ ] `UpdateEndToEndTest` 5개가 통과한다. 다음을 확인한다.
  - reference-app을 Windows app-image(`cozy-reference.exe`)로 세 버전 패키징한다.
  - 1.0.0 → 1.1.0 업데이트에서 메모가 보존된다.
  - 1.1.1이 롤백된다.
  - 화면 메뉴로 종료된다.
- [ ] 실패하면 다음 파일을 기록에 남긴다.
  - `samples\dist-e2e\target\e2e\data\update\update.log`
  - `samples\dist-e2e\target\e2e\build-*.log`
  - `samples\dist-e2e\target\surefire-reports`

### 직접 설치해서 확인

```powershell
cd C:\cozy-framework
mvn -o -Pdist package -pl samples/reference-app
Expand-Archive samples\reference-app\target\dist\cozy-reference-*-win-x64.zip $env:LOCALAPPDATA\Programs
& "$env:LOCALAPPDATA\Programs\cozy-reference\cozy-reference.exe"
```

- [ ] JDK가 없는 다른 PC(또는 JDK를 PATH에서 뺀 상태)에서도 `cozy-reference.exe`가 뜬다.
- [ ] 작업 표시줄 알림 영역에 "C" 아이콘이 보인다. 마우스를 올리면 앱 이름과 주소가 보인다.
- [ ] 트레이 메뉴 "열기"와 아이콘 더블클릭이 브라우저로 앱을 연다.
- [ ] 트레이 메뉴 "종료"로 프로세스가 끝난다. 작업 관리자에서 `cozy-reference.exe`가 사라진다.
- [ ] 화면 메뉴 "종료"는 확인 창을 띄운 뒤 "앱을 종료했습니다"를 보이고, 프로세스와 트레이 아이콘이 사라진다.
- [ ] 화면 메뉴 "진단 내보내기"는 탐색기로 `%LOCALAPPDATA%\cozy-reference\diagnostics`를 연다. zip 안에 `summary.txt`, `config.txt`, `logs\app.log`, `sqlite.txt`, `events.txt`가 있다.
- [ ] 두 번째 실행 시간이 첫 실행보다 짧다(AOT 캐시). 시작 메뉴 바로가기는 없는 것이 정상이다.

### 공유 폴더 업데이트

1. 버전을 올려(예: reference-app POM에 `<version>1.0.1</version>`) 다시 `-Pdist`로 만든다.
2. zip과 `cozy-reference-update.json`을 공유 폴더(예: `\\fileserver\share\cozy-reference\`)에 복사한다.
3. `%LOCALAPPDATA%\cozy-reference\config\application.yml`에 `cozy.update.manifest-url: \\fileserver\share\cozy-reference\cozy-reference-update.json`을 쓴다.

- [ ] 앱을 다시 실행하면 배너 "새 버전 1.0.1이 있습니다."가 뜬다.
- [ ] "지금 업데이트"를 누르면 앱이 닫히고 몇 초 뒤 새 버전 브라우저 창이 열린다. 푸터 버전이 1.0.1이고 "v1.0.1으로 업데이트했습니다."가 보인다.
- [ ] `%LOCALAPPDATA%\Programs\cozy-reference.old`가 남아 있다.
- [ ] 회사 PC의 PowerShell 실행 정책 때문에 실패하면 "업데이트 스크립트를 실행하지 못했습니다"가 보이고 앱은 그대로다. 이 경우 `Get-ExecutionPolicy -List` 결과를 기록한다.

## M7 아키타입과 오프라인 번들

### 번들 만들기 (인터넷이 되는 Windows PC)

```powershell
cd C:\cozy-framework
powershell -ExecutionPolicy Bypass -File distribution\offline-bundle\build-bundle.ps1
```

- [ ] `build\offline-bundle\cozy-offline-bundle-1.0.0-SNAPSHOT.zip`이 생긴다.
- [ ] 로그에 옵션 3조합(basic, persistence, events)의 `BUILD SUCCESS`가 각각 두 번 있다(verify, -Pdist package).
- [ ] 로그에 `[cozy] 번들 라이선스 목록: 아티팩트 ...개, 소스 jar ...개`가 있다. 번들에 `LICENSE`, `NOTICE`, `licenses\THIRD-PARTY.txt`가 있다(ADR-0028).

WSL이나 Linux에서 만든 번들(`distribution/offline-bundle/build-bundle.sh`)을 써도 된다. 번들 내용은 운영체제와 상관없다.
릴리스 전에는 CI가 만든 번들로 확인한다(`gh workflow run release.yml` → 실행 결과의 `cozy-offline-bundle-{버전}`). 받은 뒤 `Get-FileHash -Algorithm SHA256`이 `SHA256SUMS`와 같은지 본다.

### 오프라인 PC에서 확인 (M7 완료 기준)

네트워크를 끊는다(랜선을 뽑거나 Wi-Fi를 끈다). JDK 없는 번들은 JDK 25만 설치된 PC를 쓴다. Windows x64 번들(JDK 포함)은 아래 "번들 JDK"를 먼저 본다. 기존 `%USERPROFILE%\.m2`가 있으면 이름을 바꿔 둔다.

```powershell
Expand-Archive cozy-offline-bundle-1.0.0-SNAPSHOT.zip C:\cozy-bundle
cd C:\cozy-bundle\cozy-offline-bundle-1.0.0-SNAPSHOT
.\cozy.cmd install
$env:Path = "$env:USERPROFILE\cozy\bin;" + $env:Path

mkdir C:\work; cd C:\work
cozy version
cozy new "-DgroupId=com.example" "-DartifactId=voc-assistant" `
  "-Dpackage=com.example.voc" "-DappName=VOC 도우미" "-Dpersistence=true" "-Devents=true" "-DinteractiveMode=false"
cd voc-assistant
mvn -o verify
mvn -o -Pdist package
& .\target\dist\image\voc-assistant\voc-assistant.exe
```

- [ ] `cozy version`이 `1.0.0-SNAPSHOT`을 출력한다. `cozy help`가 동작 목록(install, new, version, help)을 한글로 보여 준다. `cozy nope`은 종료 코드 2로 끝난다.
- [ ] `cozy new`가 `[cozy] VOC 도우미 (voc-assistant) 생성: persistence=true, events=true`를 출력한다(한글 appName이 깨지지 않는다).
- [ ] 생성된 앱의 `home` 모듈이 `api\`, `spi\`, `internal\app\command`, `internal\app\query`, `internal\domain`, `internal\infra`로 나뉘고, 템플릿이 `templates\home\api\`와 `templates\home\internal\`에 있다(ADR-0029).
- [ ] `mvn -o verify`가 `BUILD SUCCESS`로 끝난다. 테스트 10개가 통과한다.
- [ ] `mvn -o -Pdist package`가 `target\dist\voc-assistant-1.0.0-SNAPSHOT-win-x64.zip`을 만든다.
- [ ] 실행하면 브라우저 제목과 사이드바에 "VOC 도우미"가 보인다. 메모를 추가하면 목록과 "최근 활동"에 나타난다.
- [ ] `target\dist\image\voc-assistant\licenses\THIRD-PARTY.txt`에 의존성 목록과 웹 자산 4개(Tabler, Tabler Icons, htmx, Pretendard)가 있다.
- [ ] `target\dist\image\voc-assistant\app\native\sqlitejdbc.dll`이 있다. 실행 중에 `%TEMP%`에 `sqlite-*-sqlitejdbc.dll`이 생기지 않는다.
- [ ] `"-Devents=true" "-Dpersistence=false"`로 만들면 `[cozy] events=true는 persistence=true가 필요합니다`로 실패하고 폴더가 남지 않는다.
- [ ] IntelliJ로 `voc-assistant`를 열면 실행 설정 "dev 실행", "아키텍처 테스트", "mvn -o verify"가 보인다. "dev 실행"이 `/cozy/dev/catalog`를 연다.
- [ ] 실패하면 `mvn -o verify`의 오류 앞부분과 `%USERPROFILE%\cozy\apache-maven-3.10.0\conf\settings.xml`을 기록에 남긴다.

## M11 번들의 cozy 명령 (ADR-0032)

M7의 오프라인 PC에서 이어서 한다. `voc-assistant` 폴더에서 PowerShell로 실행한다.

```powershell
cozy recipes
cozy add crud task --entity Task --label "할 일" --icon checklist --field "title:text!:제목" --field "dueOn:date:마감일" --field "done:bool:완료"
cozy add crud task --entity Task --label "할 일" --icon checklist --field "title:text!:제목" --field "dueOn:date:마감일" --field "done:bool:완료"
cozy link task --uses home
cozy add screen task stats --pattern details --title 통계 --nav "통계:no-such-icon"; $LASTEXITCODE
cozy check
cozy describe
mvn -o verify
```

- [ ] `cozy recipes`의 한글이 깨지지 않는다(`cozy help`도 `add`, `link` 줄을 보여 준다).
- [ ] 첫 `cozy add crud`가 `만듦  src/main/java/...` 목록과 `[cozy] 적용했습니다`를 출력한다. `--label "할 일"`처럼 공백이 있는 값이 한 인자로 넘어간다(라벨이 "할"만 남지 않는다).
- [ ] 같은 명령을 다시 실행하면 `[cozy] 바뀐 것 없음`이다.
- [ ] `cozy link task --uses home`이 `바꿈  src/main/java/.../task/package-info.java    allowedDependencies += "home :: spi"`를 출력한다.
- [ ] 없는 아이콘 명령은 `아이콘 'no-such-icon'가 Tabler Icons에 없습니다`를 출력하고 `$LASTEXITCODE`가 2다. `src\main\java\...\task\api`에 `TaskStatsController.java`가 생기지 않는다.
- [ ] `cozy check`가 `[cozy check] 이상 없음`, `cozy describe`가 `모듈 task`와 `TaskFacade`를 보여 준다.
- [ ] 만든 파일이 UTF-8(BOM 없음)이고 줄바꿈이 LF다. 한글 라벨이 IntelliJ에서 깨지지 않는다.
- [ ] `mvn -o verify`가 `BUILD SUCCESS`로 끝난다(`TaskCrudTest`, `TaskScreenTest` 포함).
- [ ] `cmd.exe`에서 `cozy.cmd add module memo --label "메모 보관"`도 같은 결과다.

## 번들 JDK (Windows x64 번들, ADR-0025 보완)

JDK가 설치되지 않은 PC를 쓴다. 없으면 JDK를 PATH에서 빼고 `JAVA_HOME`을 지운 PowerShell 창에서 한다(`$env:JAVA_HOME = $null`, `$env:Path`에서 JDK 폴더 제거, `Get-Command java`가 실패하는지 확인). 네트워크를 끊는다.

```powershell
Get-FileHash -Algorithm SHA256 cozy-offline-bundle-1.3.0-windows-x64.zip   # SHA256SUMS와 비교
Expand-Archive cozy-offline-bundle-1.3.0-windows-x64.zip C:\cozy-bundle
cd C:\cozy-bundle\cozy-offline-bundle-1.3.0-windows-x64
.\cozy.cmd version
.\cozy.cmd install
$env:Path = "$env:USERPROFILE\cozy\bin;" + $env:Path
mvn -v
```

이어서 M7의 `cozy new`부터 끝까지, M11의 cozy 명령까지 같은 창에서 한다.

- [ ] `.\cozy.cmd install`이 `JDK:         C:\Users\...\cozy\jdk`와 `PATH에 추가: $env:Path = '...\cozy\bin;' + $env:Path`를 출력한다. `%USERPROFILE%\cozy\jdk\bin\java.exe`와 `%USERPROFILE%\cozy\bin\mvn.cmd`가 있다. `jdk\lib\src.zip`은 없다.
- [ ] `mvn -v`의 `Java version:` 줄이 `25.0.5`이고 `runtime:`이 `%USERPROFILE%\cozy\jdk`다.
- [ ] 설치 뒤에도 시스템 설정이 그대로다. 새 PowerShell 창의 `$env:JAVA_HOME`과 시스템 환경 변수(`[Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')`, `'Machine'`)가 설치 전과 같다.
- [ ] M7의 `cozy new`, `mvn -o verify`, `mvn -o -Pdist package`, 앱 실행이 모두 통과한다. app-image의 런타임(`target\dist\image\voc-assistant\runtime\release`)이 `JAVA_VERSION="25.0.5"`다.
- [ ] M11의 cozy 명령이 모두 통과한다(`cozy add`, `cozy check`, `cozy describe`가 번들 JDK로 돈다).
- [ ] `cmd.exe`에서 `mvn.cmd -v`도 같은 `runtime:`을 보여 준다. 실패하는 빌드(예: 앱의 테스트 하나를 깨뜨린 뒤 `mvn -o verify`)의 `%ERRORLEVEL%`이 0이 아니다.
- [ ] `licenses\THIRD-PARTY.txt`의 머리말에 `Eclipse Temurin JDK in jdk/`와 `OpenJDK25U-jdk-sources_25.0.5_8.tar.gz` 줄이 있다.
- [ ] 다시 설치하기: JDK 25가 PATH에 있는 창에서 JDK 없는 번들로 `.\cozy.cmd install`을 하면 `%USERPROFILE%\cozy\jdk`가 사라지고, `mvn -v`의 `runtime:`이 시스템 JDK가 된다.
- [ ] IntelliJ: Project SDK를 `%USERPROFILE%\cozy\jdk`로, Maven home을 `%USERPROFILE%\cozy\apache-maven-3.10.0`으로, Maven JRE를 Project SDK로 두면 실행 설정 "mvn -o verify"가 통과한다.

## MSI 설치 파일 (ADR-0039)

CI의 Windows 러너가 조용한 설치·사용·제거를 이미 확인한다(`verify-msi.ps1`). 여기서는 사람이 쓰는 경로를 본다. JDK가 없고 Cozy가 설치되지 않은 PC에서, 네트워크를 끊고 한다.

1. `cozy-{버전}-windows-x64.msi`를 탐색기에서 더블클릭한다.
2. 끝나면 **새** PowerShell 창을 연다.

```powershell
cozy version
mvn -v
mkdir C:\work; cd C:\work
cozy new "-DgroupId=com.example" "-DartifactId=voc-assistant" "-Dpackage=com.example.voc" `
  "-DappName=VOC 도우미" "-Dpersistence=true" "-Devents=true" "-DinteractiveMode=false"
cd voc-assistant
cozy add crud task --entity Task --label "할 일" --icon checklist --field "title:text!:제목"
mvn -o verify
mvn -o -Pdist package
```

- [ ] 더블클릭 설치에 관리자 권한(UAC) 창이 뜨지 않는다. 끝나면 `%LOCALAPPDATA%\Programs\Cozy`에 `bin`, `jdk`, `apache-maven-3.10.0`, `repository`가 있다.
- [ ] 인터넷에서 받은 MSI면 SmartScreen 경고가 뜰 수 있다(서명하지 않았다). "추가 정보 → 실행"으로 설치되는지, 회사 정책에서 막히는지 기록한다.
- [ ] 새 창에서 `cozy version`이 버전을, `mvn -v`의 `runtime:`이 `%LOCALAPPDATA%\Programs\Cozy\jdk`를 보여 준다. PATH를 손으로 넣지 않았다.
- [ ] "설정 → 앱 → 설치된 앱"에 Cozy가 있다.
- [ ] `cozy new`의 `-DappName=VOC 도우미`(공백과 한글)가 깨지거나 나뉘지 않는다. 브라우저 제목이 "VOC 도우미"다.
- [ ] `mvn -o verify`, `mvn -o -Pdist package`가 통과하고 앱이 뜬다.
- [ ] `cmd.exe`에서도 `cozy version`, `mvn -v`가 된다.
- [ ] IntelliJ: Maven home을 `%LOCALAPPDATA%\Programs\Cozy\apache-maven-3.10.0`으로, JDK를 `%LOCALAPPDATA%\Programs\Cozy\jdk`로 두면 "mvn -o verify" 실행 설정이 통과한다. 로컬 저장소가 `...\Cozy\repository`로 잡힌다(설정의 `${maven.home}/../repository`).
- [ ] 같은 MSI를 다시 더블클릭하면 덮어쓴다. 더 낮은 판의 MSI는 "더 새로운 Cozy가 이미 설치되어 있습니다"로 막힌다.
- [ ] "설치된 앱"에서 제거하면 새 창에서 `cozy`를 찾지 못하고, 사용자 PATH에서 `...\Cozy\bin`이 빠진다. `%LOCALAPPDATA%\Programs\Cozy`에 남는 것이 있으면(Maven이 만든 파일) 기록한다.
- [ ] 관리자 PowerShell에서 `msiexec /i cozy-{버전}-windows-x64.msi ALLUSERS=1`을 하면 "Cozy는 사용자별로만 설치합니다" 메시지로 거부된다. 한글 메시지가 깨지지 않는다.

## 기록

| 날짜 | 마일스톤 | 결과 | 비고 |
| --- | --- | --- | --- |
| | M1 | | |
| | M2 | | |
| | M3 | | |
| | M4 | | |
| | M5 | | |
| | M6 | | |
| | M7 | | |
| | 번들 JDK | | |
| | MSI | | |
