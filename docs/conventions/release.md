# 프레임워크 릴리스

프레임워크는 GitHub Release에 오프라인 번들로 낸다. Maven 저장소(패키지)로는 내지 않는다. 결정과 이유는 ADR-0028, ADR-0025 보완, ADR-0039에 있다.

| 릴리스 자산 | 내용 |
| --- | --- |
| `cozy-{버전}-windows-x64.msi` | Windows 설치 파일. Windows x64 번들을 `cozy install`로 펼친 폴더를 `%LOCALAPPDATA%\Programs\Cozy`에 사용자별로 설치하고 사용자 PATH에 등록한다 |
| `cozy-offline-bundle-{버전}-windows-x64.zip` | Windows x64 번들. Temurin JDK(`jdk/`)를 담았다 |
| `cozy-offline-bundle-{버전}.zip` | JDK 없는 번들. 운영체제와 상관없다 |
| `OpenJDK25U-jdk-sources_*.tar.gz` | Windows x64 번들에 담은 JDK의 소스 묶음(GPL-2.0 with Classpath Exception) |
| `SHA256SUMS` | 위 네 파일의 SHA-256 |

## 절차

1. **버전 세트 확인**: 최신 GA를 확인하고 ADR-0017 표에 반영한다(RC, milestone, `next` 제외).
   - 번들 JDK: Temurin 25의 새 패치가 있으면 `distribution/offline-bundle/jdk.env`의 릴리스, 파일 이름, SHA-256 세 쌍을 바꾼다. Adoptium API(`api.adoptium.net/v3/assets/release_name/eclipse/<릴리스>`)의 checksum과 릴리스 자산의 `.sha256.txt`가 같은지 본다.
2. **Windows 확인**: windows-verification.md의 확인 항목을 끝낸다. 1.0.0 전에는 M1~M7 전부다.
   - CI가 만든 번들로 확인하려면 워크플로를 수동으로 돌린다: `gh workflow run release.yml`
   - 실행 결과의 `cozy-offline-bundle-{버전}`(두 번들과 JDK 소스 묶음)과 `cozy-msi-{버전}`(MSI)을 받는다. 14일 보관한다.
   - Windows x64 번들은 JDK를 설치하지 않은(또는 PATH에서 뺀) PC에서 확인한다.
3. **버전 바꾸기**:

   ```bash
   distribution/release/set-version.sh 1.0.0
   git diff                      # POM, -DarchetypeVersion= 예시, 번들 README의 zip 이름
   mvn -o -Pe2e install          # 워크플로와 같은 검증. 결과를 보고 나서 커밋한다
   git commit -am "릴리스 1.0.0"
   ```

   - `mvn verify`로는 부족하다. 아키타입 통합 테스트는 생성한 앱을 따로 빌드하면서 새 버전의 cozy 모듈(`cozy-workbench` 등)을 로컬 저장소에서 찾는데, `verify`는 설치하지 않아 오프라인에서 실패한다. `install`은 모듈을 차례로 설치하므로 통과한다.
   - 검증이 통과한 것을 확인한 뒤에 커밋하고 태그를 붙인다. 명령을 `&&`로 이어 결과를 보기 전에 태그를 push하지 않는다.

4. **태그 push**:

   ```bash
   git tag v1.0.0
   git push origin main v1.0.0
   ```

   워크플로가 다음을 한다.
   - 버전 확인
   - `-Pe2e install`
   - 번들 만들기(JDK 없는 번들, Windows x64 번들, 검증용 Linux x64 번들)와 JDK 소스 묶음 받기
   - 오프라인 검증(JDK 없는 번들, Linux x64 번들은 시스템 JDK를 막고)
   - MSI 만들기와 검증(Windows 러너: 설치, 앱 생성·빌드·패키징, 제거, 컴퓨터별 설치 거부)
   - 릴리스 초안 만들기

5. **게시**: 초안의 "변경 내용"을 채우고 Windows 확인 결과를 적은 뒤 게시한다.
6. **다음 개발 버전**:

   ```bash
   distribution/release/set-version.sh 1.1.0-SNAPSHOT
   git commit -am "다음 개발 버전 1.1.0-SNAPSHOT"
   ```

- **패치 릴리스**(`1.0.1`): 의존성은 패치 버전과 보안 수정만 올린다. Boot 마이너처럼 동작이 바뀔 수 있는 판올림은 다음 마이너 릴리스에서 한다.
- **태그를 잘못 붙였을 때**: 초안 릴리스와 태그를 지우고 다시 붙인다. 게시한 릴리스의 파일은 바꾸지 않는다. 고칠 것이 있으면 새 패치 버전으로 낸다.

## 스크립트

| 스크립트 | 하는 일 |
| --- | --- |
| `distribution/release/set-version.sh <버전>` | 레포의 POM(아키타입 템플릿과 invoker 픽스처 제외. 픽스처 POM이 바뀌면 실패한다), 문서의 `-DarchetypeVersion=` 예시, 번들 README의 zip 이름을 바꾼다. POM에 이전 버전이 남으면 실패한다. 생성 앱의 기본 버전(`cozy new`의 `-Dversion`)은 앱 버전이라 바꾸지 않는다 |
| `distribution/release/check-version.sh <tag\|branch> <ref>` | cozy 모듈 POM이 모두 `cozy.version`을 쓰는지 본다. 태그 빌드면 태그가 `v{버전}`이고 SNAPSHOT이 아닌지도 본다 |
| `distribution/release/install-maven.sh <폴더>` | Maven 3.10.0을 받아 SHA-512를 확인하고 푼다 |
| `distribution/release/release-notes.sh <버전> <릴리스 폴더>` | 릴리스 노트 초안(세 파일의 크기와 SHA-256, 설치 요약). 폴더에 두 번들과 JDK 소스 묶음이 있어야 한다 |
| `distribution/release/jdk-sources.sh <폴더>` | `jdk.env`의 JDK 소스 묶음을 받아 SHA-256을 확인한다 |
| `distribution/msi/build-msi.ps1 -Bundle <windows-x64 zip>` | (Windows, WiX 필요) 번들의 `cozy.cmd install`로 설치 폴더 모양을 만들고 `cozy.wxs`로 묶는다. 결과는 `cozy-{버전}-windows-x64.msi`. MSI 판은 `-SNAPSHOT`을 뗀 숫자 세 자리 |
| `distribution/msi/verify-msi.ps1 -Msi <msi>` | (Windows) 조용히 설치 → 설치 폴더·사용자 PATH·Maven 설정 → 시스템 JDK를 막고 앱 생성·`cozy add`·`verify`·`-Pdist package` → 실패한 빌드의 종료 코드 → 제거 → 컴퓨터별 설치 거부 |
| `distribution/offline-bundle/add-jdk.sh <번들 zip> <windows-x64\|linux-x64> [배포판]` | JDK 없는 번들에 `jdk.env`의 Temurin을 더한 플랫폼 번들을 만든다(SHA-256, 도구, 판 확인, `lib/src.zip` 제거, 라이선스 목록 다시 만들기) |

## 워크플로 (`.github/workflows/release.yml`)

| 실행 | 하는 일 | 결과 |
| --- | --- | --- |
| 태그 `v*` push | `bundle`: 버전 확인 → `-Pe2e install` → 번들 → JDK 번들·소스 묶음 → 오프라인 검증(두 번) → 체크섬. `windows`: MSI 만들기·검증. `release`: 체크섬 확인 → 노트 → 초안 | 실행 결과 + **릴리스 초안** |
| 수동(`workflow_dispatch`) | 같은 빌드와 검증. SNAPSHOT 버전도 된다 | 실행 결과만 |

- 액션은 커밋 SHA로 고정한다. 버전은 ADR-0017 표에 있다.
- 빌드 작업은 읽기 권한만 갖는다. 릴리스 작업만 `contents: write`를 갖는다.
- `windows` 작업은 WiX Toolset 7.0.1을 `dotnet tool install`로 받고 `wix eula accept wix7`로 약관(OSMF EULA v1.1)에 동의한 뒤 만든다. 동의는 레포 주인의 결정이다(ADR-0039). 수익 조건이 바뀌면 다시 본다.
- `windows` 작업은 받은 번들을 `SHA256SUMS`로 확인한 뒤 쓴다. MSI의 SHA-256은 릴리스 작업이 `SHA256SUMS`에 더한다.
- 릴리스 작업은 받은 파일을 `SHA256SUMS`로 다시 확인한 뒤 올린다.

## 번들 라이선스 목록

번들 빌드가 `BundleLicenses`로 `repository/`의 모든 아티팩트를 검사한다(ADR-0028). 결과는 번들의 `licenses/THIRD-PARTY.txt`다.

```
Lists of 822 artifacts in repository/.
     (Apache-2.0) org.apache.maven.plugins:maven-compiler-plugin:3.15.0
     (EPL-2.0) org.junit.jupiter:junit-jupiter-api:6.0.3 [source: repository/org/junit/jupiter/.../junit-jupiter-api-6.0.3-sources.jar]
     (MIT) isorelax:isorelax:20030108 [override: jar의 COPYING.txt가 MIT 문구]
```

- 줄마다 POM(또는 부모 POM)이 선언한 라이선스의 SPDX ID가 있다.
- 약한 카피레프트만 있는 jar는 소스 jar 위치가 붙는다.
- 예외 목록으로 정한 줄은 근거가 붙는다.
- Windows x64 번들은 머리말에 JDK 줄이 더해진다. 배포판 이름, 라이선스, `jdk/legal/`, 같은 릴리스의 소스 묶음 이름이 들어간다.

### 검사가 실패할 때

메시지에 아티팩트와 라이선스가 나온다.

- `[라이선스 정보 없음]`, `?이름`(SPDX로 맞추지 못함): jar 안의 LICENSE, 상위 저장소, 소스 머리말을 확인한다. 허용 라이선스면 예외 목록에 적는다.
- GPL, AGPL처럼 허용 묶음 밖의 라이선스: 그 아티팩트를 끌어오는 의존성이나 플러그인을 바꾼다. 허용 묶음을 바꾸려면 ADR-0028을 먼저 고친다.
- 소스 jar를 받지 못함: Maven Central에 소스 jar가 없는 경우다. 다른 버전으로 바꾸거나, 소스를 구할 방법을 ADR로 정한다.

### 예외 목록 형식 (`distribution/offline-bundle/license-overrides.txt`)

```
groupId:artifactId:version  SPDX[|SPDX]  # 근거
org.jdom:jdom2:2.0.6.1      LicenseRef-JDOM  # jar의 META-INF/LICENSE.txt. Apache 1.1식 허용형
```

- 근거(`#` 뒤)가 없으면 오류다.
- 버전까지 맞아야 적용된다. 버전이 바뀌면 다시 확인하게 된다.
- 저장소에 없는 줄은 "참고"로만 알린다.
- SPDX ID는 허용 묶음에 있는 것만 통과한다.
  - 허용형: Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, 0BSD, ISC, UPL-1.0, Unicode-3.0, W3C, SAX-PD, LicenseRef-Public-Domain, LicenseRef-JDOM
  - 약한 카피레프트: EPL-1.0, EPL-2.0, MPL-2.0, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, CDDL-1.0, CDDL-1.1, `GPL-2.0-only WITH Classpath-exception-2.0`

### 직접 돌리기

```bash
java -cp cozy-starter-runtime/target/classes io.salvia95.cozy.runtime.internal.dist.BundleLicenses \
  --repository=<Maven 저장소> --overrides=distribution/offline-bundle/license-overrides.txt \
  --output=<THIRD-PARTY.txt> [--title=...] [--maven-distribution=apache-maven-3.10.0-bin.zip] \
  [--fetch-sources=https://repo.maven.apache.org/maven2/] \
  [--jdk-distribution=OpenJDK25U-jdk_x64_windows_hotspot_25.0.5_8.zip --jdk-sources=OpenJDK25U-jdk-sources_25.0.5_8.tar.gz]
```

`--jdk-distribution`과 `--jdk-sources`는 함께 준다. 하나만 주면 실패한다(JDK를 담으면 소스를 얻는 곳도 적어야 한다).

`--fetch-sources`가 없으면 빠진 소스 jar를 받지 않고 실패한다. 오프라인 검증은 이 방식으로 번들의 목록을 다시 만들어 비교한다.
