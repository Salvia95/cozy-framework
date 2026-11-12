# ADR-0039 개발 도구 설치 파일(MSI): 사용자별 설치, 영구 PATH, WiX v7

- 상태: 승인 (2026-11-13, 사용자 결정 "msi로 가자 수익을 낼 목적이 없기때문에 약관에 동의할거야")
- 출처: 사용자 지적 "설치과정이 너무 복잡하게 되어있고 경로가 직관적이지 않아"
- 이어받는 결정: [ADR-0025](0025-archetype-and-offline-bundle.md) 보완(Windows x64 번들의 JDK), [ADR-0028](0028-release.md)(GitHub Release)
- 바꾸는 결정:
  - ADR-0025 §6 "Maven 설정": `settings.xml`의 `localRepository`를 절대 경로에서 `${maven.home}/../repository`로 바꾼다(§3).
  - ADR-0028 §1 배포물: 릴리스 자산에 MSI를 더한다(§5).

## 배경

zip 번들의 설치는 네 단계였다.

1. zip을 푼다.
2. 그 폴더로 들어가 `.\cozy.cmd install`을 실행한다.
3. 안내대로 PATH를 넣는다. 그런데 `$env:Path = ...`는 지금 창에만 적용돼서 새 창마다 다시 넣어야 했다.
4. 지울 때는 폴더를 손으로 지운다.

설치 위치도 직관적이지 않았다. 기본값이 `%USERPROFILE%\cozy`이고, Windows의 일반 프로그램 위치가 아니며, "앱 및 기능"에도 나오지 않았다.

## 결정

### 1. Windows에는 MSI `cozy-{버전}-windows-x64.msi`를 낸다

- 더블클릭으로 설치하고 "앱 및 기능"에서 제거한다.
- 내용은 Windows x64 번들(JDK 포함)을 `cozy install`로 펼친 폴더 그대로다. JDK, Maven, 오프라인 Maven 저장소, cozy 명령, 문서, 라이선스가 들어 있다.
- zip 번들 두 가지(JDK 포함, JDK 없음)도 그대로 낸다. MSI를 쓸 수 없는 PC와 Linux·WSL을 위해서다.

### 2. 사용자별 설치: `%LOCALAPPDATA%\Programs\Cozy`

- 관리자 권한이 필요 없다. 폐쇄망 PC에서 개발자가 직접 설치할 수 있다.
- 패키지는 사용자별·컴퓨터별 겸용(`Scope="perUserOrMachine"`, 기본은 사용자별)이다. 이렇게 하면 `ProgramFiles6432Folder`가 사용자별 설치에서 `%LOCALAPPDATA%\Programs`가 된다.
  - 순수 사용자별 패키지(`perUser`)에 파일을 한꺼번에 담으면 ICE 검사를 통과하지 못한다(WiX 문서의 `Files` 요소). 그래서 겸용 패키지로 만든다.
- **컴퓨터별 설치는 막는다**(`ALLUSERS=1`이면 설치 조건에서 실패, 종료 코드 1603).
  - 컴퓨터별로 깔면 `Program Files` 아래의 Maven 저장소에 사용자가 쓸 수 없다. 앱의 `mvn install`과 Maven의 추적 파일 쓰기가 실패한다.
  - 사내 IT가 일괄 배포하려면 사용자 컨텍스트로 배포한다.
- 경로 길이: 번들에서 가장 긴 상대 경로가 126자다. `C:\Users\<사용자>\AppData\Local\Programs\Cozy\`를 붙여도 260자 안이다.

### 3. 설치 폴더가 어디든 맞는 설정

- `cozy install`이 쓰는 Maven `settings.xml`의 `localRepository`를 `${maven.home}/../repository`로 한다. Maven 3.10.0에서 실제로 확인했다(`Using local repository at <maven.home>/../repository`).
- 그래서 MSI는 `cozy install`로 만든 스테이징 폴더를 그대로 묶을 수 있다. 설치 위치가 정해지는 것은 MSI를 설치할 때지만, 설정에는 고칠 절대 경로가 없다.
- `bin\mvn.cmd`, `cozy.ps1`은 이미 자기 위치를 기준으로 설치 폴더를 찾는다.
- zip 설치에도 같은 설정을 쓴다. 설치 폴더를 옮겨도 맞는다.

### 4. PATH는 MSI가 사용자 PATH에 영구로 넣고, 지우면 뺀다

- `<설치 폴더>\bin`을 사용자 PATH 끝에 더한다(`Environment` 요소, `System="no"`, `Part="last"`).
- 시스템 PATH와 `JAVA_HOME`은 건드리지 않는다. 번들 JDK는 ADR-0025 보완 4대로 `cozy`와 `bin\mvn.cmd`만 쓴다.
- 설치 직후 이미 열려 있던 터미널에는 반영되지 않는다. 새 터미널부터 쓴다.

### 5. 만들기: WiX Toolset v7, Windows CI

- WiX Toolset 7.0.1(`wix` .NET 도구)로 만든다. ADR-0017 표에 있다. WiX는 Windows에서만 돈다.
- **약관**: WiX v7 바이너리는 Open Source Maintenance Fee EULA v1.1을 따른다. 소스는 MS-RL이다.
  - WiX를 쓰는 프로젝트로 연 1만 달러 이상을 벌기 전에는 수수료가 없다. 대신 명시적인 동의가 필요하다.
  - 레포 주인이 동의했다(2026-11-13, 위 상태 줄). Cozy는 수익을 낼 목적이 없다.
  - 워크플로는 `wix eula accept wix7`로 동의한 뒤 만든다. 수익 조건이 바뀌면 이 결정을 다시 본다.
- **순서**: 릴리스 워크플로의 `windows` 작업(Windows 러너)이 한다.
  1. 같은 실행의 `bundle` 작업이 만든 Windows x64 번들을 받는다.
  2. `build-msi.ps1`이 번들의 `cozy.cmd install`로 스테이징 폴더를 만들고 `wix build`로 묶는다.
- **MSI 판**: `ProductVersion`은 숫자 세 자리다. `-SNAPSHOT`을 뗀다. 같은 판끼리도 업그레이드로 덮어쓴다(`AllowSameVersionUpgrades`). 낮은 판은 막는다.
- **UpgradeCode**: `F191D6D6-32AC-47DD-A32E-BE21E5045AF9`로 고정한다. 바꾸면 이전 판을 업그레이드하지 못한다.
- 서명하지 않는다. 인터넷에서 받은 파일이면 SmartScreen이 경고할 수 있다(남은 위험).

### 6. 검증: Windows 러너에서 설치부터 제거까지

`verify-msi.ps1`이 MSI를 만든 러너에서 다음을 확인한다. 지금까지 사람이 Windows에서 하던 확인의 일부를 CI로 옮긴다.

1. 조용히 설치(`msiexec /i /qn`)한다. 설치 폴더, `src.zip`이 없는 JDK, 사용자 PATH, 위치와 상관없는 Maven 설정을 본다.
2. 시스템 JDK 자리에 부르면 실패하는 `java`를 둔다(`JAVA_HOME`도 그 자리). 그 상태에서 설치한 `cozy`와 `mvn`으로 다음을 한다.
   - 앱 생성, `cozy add crud`, `cozy check`, `mvn -o verify`, `mvn -o -Pdist package`
   - app-image의 런타임이 JDK 25인지 확인
3. 실패하는 빌드의 종료 코드가 `mvn.cmd`를 거쳐 그대로 나온다.
4. 제거(`msiexec /x /qn`)하면 사용자 PATH에서 빠지고 설치한 파일이 사라진다. 설치 뒤 Maven이 저장소에 만든 파일은 남을 수 있어 목록만 남긴다.
5. 컴퓨터별 설치(`ALLUSERS=1`)는 1603으로 거부되고 아무것도 깔리지 않는다.

네트워크는 끊지 않는다. Windows 러너에서 끊기 어렵기 때문이다. 대신 Maven은 설정의 `offline=true`와 `-o`로 네트워크에 나가지 않는다. 번들만으로 되는지는 Linux의 오프라인 검증(ADR-0025 §6, 보완 6)이 확인한다.

사람이 Windows에서 볼 것(windows-verification.md)은 다음과 같다. 더블클릭 설치 화면, 새 터미널의 PATH, "앱 및 기능", IntelliJ 설정, 네트워크를 끊은 PC다.

## 결과

- 릴리스 자산: MSI, 두 zip 번들, JDK 소스 묶음, `SHA256SUMS`.
- 새 Maven 의존성과 플러그인은 없다. 빌드 도구(WiX, CI에서만)와 Windows 러너가 늘었다.
- 설치 방법이 두 가지(MSI, zip)가 된다. 둘이 같은 폴더 모양과 같은 설정을 쓰도록 MSI는 `cozy install` 결과를 그대로 묶는다.
- 제거해도 Maven이 저장소에 만든 파일은 남는다. Maven 3.10은 저장소를 읽을 때마다 `repository\.locks\`에 아티팩트별 잠금 파일을 만든다(CI 확인 때 앱 하나로 1,070개). 앱을 `mvn install`한 결과도 남는다. 폴더를 지우려면 제거 뒤 `%LOCALAPPDATA%\Programs\Cozy`를 지운다.

## 도입 검토 (CLAUDE.md: 이유, 라이선스, 폐쇄망 반입 영향)

| 항목 | 이유 | 라이선스 | 폐쇄망 반입 |
| --- | --- | --- | --- |
| WiX Toolset 7.0.1 (`wix` .NET 도구) | MSI 만들기 | 소스 MS-RL, 바이너리 OSMF EULA v1.1(연 1만 달러 미만 무료, 동의 필요. 2026-11-13 동의) | 없음. CI(Windows 러너)에서만 쓰고 MSI에 들어가지 않는다 |
| GitHub Actions `windows-2025` 러너 | WiX 실행, MSI 설치 검증 | GitHub 이용 약관 | 없음 |
| 새 Maven 의존성·플러그인 | 없음 | | |
