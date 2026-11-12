# ADR-0028 릴리스: GitHub Release의 오프라인 번들, 버전 고정, 번들 라이선스

- 상태: 제안 (2026-11-06, 배포 방식 논의 후 사용자가 진행을 요청)
- 출처: PRD §9.7(오프라인 번들), §11(라이선스). ADR-0016(좌표), ADR-0017(버전 정책), ADR-0025(오프라인 번들), ADR-0026(제3자 라이선스)을 잇는다.

## 결정

### 1. 프레임워크는 GitHub Release의 오프라인 번들로 배포한다

- **배포물**: 태그 `vX.Y.Z`의 GitHub Release에 다음을 붙인다.
  - 오프라인 번들 zip
  - `SHA256SUMS`
  - 소스 코드 압축은 GitHub가 태그에서 자동으로 만든다.
- **이유**: 쓰는 사람은 폐쇄망 PC의 개발자다. 어차피 인터넷 PC에서 번들을 받아 반입해야 한다(PRD §9.7).
  - 프레임워크 jar는 번들의 Maven 저장소 안에 들어 있다.
  - zip 하나와 해시 하나면 반입 승인 절차에 바로 쓸 수 있다.
- **Maven 저장소(패키지)로는 지금 내지 않는다.**
  - GitHub Packages는 공개 패키지도 Maven으로 받으려면 토큰이 필요하다.
  - Maven Central은 `io.salvia95` 네임스페이스 소유 증명이 필요하다. 도메인 `salvia95.io`가 없으면 `io.github.salvia95`로 바꿔야 하고, 이는 ADR-0016 개정이다. 서명과 javadoc jar 요구도 붙는다.
  - 폐쇄망 안에서는 패키지가 있어도 결국 번들이 필요하다.
- **다시 볼 조건**: 인터넷이 되는 환경의 사용자가 생기면 Maven Central을 더한다. 좌표를 먼저 정한다.

### 2. 버전은 릴리스 때 고정한다

- **개발 중**: main은 `X.Y.Z-SNAPSHOT`이다. 모든 cozy 모듈이 같은 버전을 쓴다(ADR-0016).
- **릴리스 절차**:
  1. ADR-0017의 방식대로 최신 GA를 확인하고 표에 반영한다. 이 표가 그 릴리스의 고정 버전 세트다.
  2. `distribution/release/set-version.sh X.Y.Z`로 버전을 바꾸고 커밋한다.
  3. 태그 `vX.Y.Z`를 push한다.
  4. 워크플로가 릴리스 초안을 만든다. 사람이 노트를 채워 게시한다.
  5. `set-version.sh X.Y+1.0-SNAPSHOT`으로 다음 개발 버전을 연다.
- **ADR-0017과의 관계**:
  - 1.0.0 전에는 ADR-0017(마일스톤마다 최신 GA)을 그대로 따른다.
  - 1.0.0부터 최신 GA 확인 시점은 릴리스 준비 때다.
  - 패치 릴리스(`X.Y.Z+1`)에서는 의존성의 패치 버전과 보안 수정만 올린다.
  - Boot 마이너(4.2 등)처럼 동작이 바뀔 수 있는 판올림은 다음 마이너 릴리스에서 한다.
- **실패로 닫는 곳**(`check-version.sh`):
  - 태그와 `cozy.version`이 다르면 실패한다.
  - SNAPSHOT 버전의 태그면 실패한다.
  - cozy 모듈 POM에 버전이 섞여 있으면 실패한다.
- **첫 릴리스 1.0.0**: Windows 확인(M1~M7, windows-verification.md) 뒤에 낸다.

### 3. 번들 저장소의 모든 아티팩트에 재배포 허용 라이선스가 있어야 한다

번들은 앱 빌드에 필요한 Maven 플러그인과 라이브러리를 그대로 재배포한다. 앱 배포물(ADR-0026)보다 대상이 넓다. 빌드 도구인 license-maven-plugin(LGPL-3.0), Saxon-HE(MPL-2.0)도 들어간다. 그래서 별도로 검사한다.

- **도구**: `BundleLicenses`(cozy-starter-runtime의 `internal/dist`, JDK만 쓴다)
  - 번들 빌드가 저장소를 다 채운 뒤 돈다.
  - 오프라인 검증도 번들 안의 jar로 목록을 다시 만들어 담긴 목록과 비교한다.
- **라이선스를 정하는 순서**:
  1. 예외 목록(`distribution/offline-bundle/license-overrides.txt`)
  2. Cozy 자신(`io.salvia95.cozy`: Apache-2.0)
  3. POM의 `<licenses>`
  4. 없으면 부모 POM을 따라 올라간다(Maven 상속과 같다)
- **이름 맞추기**: POM의 이름을 SPDX ID로 맞춘다. 이름으로 정할 수 없으면 URL로 정한다(예: `BSD` + asm.ow2.io → BSD-3-Clause).
- **허용 라이선스는 두 묶음이다.** 이중 라이선스는 하나만 맞으면 된다.

  | 묶음 | 라이선스 | 번들에 담는 것 |
  | --- | --- | --- |
  | 허용형 | Apache-2.0, MIT, BSD-2/3-Clause, 0BSD, ISC, UPL-1.0, Unicode-3.0, W3C, SAX-PD, Public Domain, JDOM | 목록과 라이선스 안내 |
  | 약한 카피레프트 | EPL-1.0/2.0, MPL-2.0, LGPL-2.1/3.0, CDDL-1.0/1.1, GPL-2.0 with Classpath Exception | 목록과 **소스 jar** |

  - 약한 카피레프트는 바이너리를 재배포할 때 소스를 얻는 방법을 함께 줘야 한다.
  - 상위 사이트 링크는 시간이 지나면 사라질 수 있다. 그래서 허용형 선택지가 없는 jar는 `*-sources.jar`를 같은 폴더에 담는다.
  - 받을 때 같은 저장소의 `.sha1`과 맞춰 본다.
  - 2026-11-06 기준 25개, 8.5MB다(JUnit, Sisu, logback, Saxon, license-maven-plugin 등).
- **실패 조건**: 다음 경우 번들 빌드가 실패한다.
  - 그 밖의 라이선스(GPL, AGPL 등)
  - 알 수 없는 이름
  - 라이선스 정보가 없는 아티팩트
  - 받을 수 없는 소스 jar
- **예외 목록**:
  - 형식은 `g:a:v  SPDX  # 근거`다. 근거가 없는 줄은 오류다.
  - 근거에는 확인한 곳(jar 안의 파일, 상위 저장소의 LICENSE, 소스 머리말)을 적는다.
  - 버전까지 맞아야 적용된다. 그래서 버전이 바뀌면 다시 실패하고, 그때 다시 확인한다.
  - 처음 목록은 13개다. 오래된 부모 POM, 라이선스가 jar 안에만 있는 것, `BSD`처럼 이름이 모호한 것이다.
  - spring-session-bom 4.1.1의 POM은 "Broadcom Foundation License"라고 적혀 있다. 그러나 같은 태그의 LICENSE.txt는 Apache-2.0이다. Spring 빌드 규약의 상용판 값이 들어간 것으로 보인다. POM만 있는 BOM이다.
- **번들에 담는 것**:

  | 경로 | 내용 |
  | --- | --- |
  | `LICENSE`, `NOTICE` | Cozy (Apache-2.0) |
  | `licenses/THIRD-PARTY.txt` | 저장소 아티팩트 전부의 라이선스(SPDX), 소스 jar 위치, 예외 근거 |
  | `licenses/license-overrides.txt` | 예외 목록 |

  - Maven 배포판의 라이선스는 zip 안의 `LICENSE`, `NOTICE`, `lib/`에 있다.

### 4. 릴리스 빌드는 GitHub Actions에서 한다

- **워크플로**: `.github/workflows/release.yml`
  - 태그 push: 버전 확인 → `-Pe2e install` → 번들 → 오프라인 검증 → 초안 릴리스
  - 수동 실행: 같은 빌드와 검증을 하고, 번들을 실행 결과(artifact, 14일)로만 남긴다. Windows 확인용 번들을 받을 때도 쓴다.
- **공급망**:
  - 액션은 커밋 SHA로 고정한다.
  - Maven 배포판은 SHA-512를 스크립트에 고정한다. Maven Central과 archive.apache.org의 값이 같은 것을 확인했다.
  - 빌드 작업의 토큰 권한은 읽기다. 릴리스를 만드는 작업만 `contents: write`를 받는다.
- **초안 릴리스**: 게시는 사람이 한다. 변경 내용과 Windows 확인 결과를 노트에 채운 뒤에 게시한다.
- **오프라인 검증**: Ubuntu 24.04 러너는 권한 없는 사용자 이름공간을 AppArmor로 막는다. 그래서 검증 전에 `kernel.apparmor_restrict_unprivileged_userns=0`으로 푼다(러너 안에서만).

## 도입 검토 (CLAUDE.md: 이유, 라이선스, 폐쇄망 반입 영향)

| 항목 | 이유 | 라이선스 | 폐쇄망 반입 |
| --- | --- | --- | --- |
| actions/checkout 7.0.1, setup-java 6.2.0, upload-artifact 7.0.2, download-artifact 8.0.2 | 릴리스 빌드 | MIT | 없음(CI에서만) |
| 새 Maven 의존성·플러그인 | 없음 | | |

## 결과

- 번들 크기가 소스 jar만큼(약 8.5MB) 늘어난다.
- 의존성이 바뀌어 새 아티팩트가 들어오면, 라이선스를 정할 수 없을 때 번들 빌드가 실패한다. 예외 목록에 근거를 적거나 의존성을 바꾼다.
- 번들은 Linux 러너에서 만든다. 번들의 라이브러리와 플러그인은 운영체제와 상관없다(ADR-0025). 그래도 첫 릴리스 전에 CI가 만든 번들로 Windows 확인(M7)을 한다.

## 보완 (2026-11-12, ADR-0025 보완: Windows x64 번들에 JDK를 담는다)

- **배포물**: 1의 목록이 바뀐다. 태그의 GitHub Release에 다음을 붙인다.
  - JDK 없는 번들 `cozy-offline-bundle-{버전}.zip`
  - Windows x64 번들 `cozy-offline-bundle-{버전}-windows-x64.zip`(Temurin JDK 포함)
  - JDK 소스 묶음 `OpenJDK25U-jdk-sources_*.tar.gz`
  - 세 파일의 `SHA256SUMS`
- **번들 라이선스**: JDK(`GPL-2.0-only WITH Classpath-exception-2.0`)는 약한 카피레프트 묶음을 따른다. 다만 소스는 번들 안이 아니라 같은 릴리스의 자산으로 둔다. jar의 소스 jar는 번들 안에 두는 규칙을 그대로 지킨다. 이유는 ADR-0025 보완 5에 있다.
- **워크플로**: 번들 다음에 `add-jdk.sh`(windows-x64, 검증용 linux-x64)와 `jdk-sources.sh`를 돌린다. 오프라인 검증은 JDK 없는 번들과 linux-x64 번들에 각각 한다.
