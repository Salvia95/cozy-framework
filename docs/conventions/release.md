# 프레임워크 릴리스

프레임워크는 GitHub Release에 오프라인 번들 zip과 `SHA256SUMS`로 낸다. Maven 저장소(패키지)로는 내지 않는다. 결정과 이유는 ADR-0028에 있다.

## 절차

1. **버전 세트 확인**: 최신 GA를 확인하고 ADR-0017 표에 반영한다(RC, milestone, `next` 제외).
2. **Windows 확인**: windows-verification.md의 확인 항목을 끝낸다. 1.0.0 전에는 M1~M7 전부다.
   - CI가 만든 번들로 확인하려면 워크플로를 수동으로 돌린다: `gh workflow run release.yml`
   - 실행 결과의 `cozy-offline-bundle-{버전}`(14일 보관)을 받는다.
3. **버전 바꾸기**:

   ```bash
   distribution/release/set-version.sh 1.0.0
   git diff                      # POM, -DarchetypeVersion= 예시, 번들 README의 zip 이름
   mvn verify
   git commit -am "릴리스 1.0.0"
   ```

4. **태그 push**:

   ```bash
   git tag v1.0.0
   git push origin main v1.0.0
   ```

   워크플로가 다음을 한다.
   - 버전 확인
   - `-Pe2e install`
   - 번들 만들기
   - 오프라인 검증
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
| `distribution/release/release-notes.sh <버전> <zip>` | 릴리스 노트 초안(파일, 크기, SHA-256, 설치 요약) |

## 워크플로 (`.github/workflows/release.yml`)

| 실행 | 하는 일 | 결과 |
| --- | --- | --- |
| 태그 `v*` push | 버전 확인 → `-Pe2e install` → 번들 → 오프라인 검증 → 체크섬·노트 | 실행 결과 + **릴리스 초안** |
| 수동(`workflow_dispatch`) | 같은 빌드와 검증. SNAPSHOT 버전도 된다 | 실행 결과만 |

- 액션은 커밋 SHA로 고정한다. 버전은 ADR-0017 표에 있다.
- 빌드 작업은 읽기 권한만 갖는다. 릴리스 작업만 `contents: write`를 갖는다.
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
  [--fetch-sources=https://repo.maven.apache.org/maven2/]
```

`--fetch-sources`가 없으면 빠진 소스 jar를 받지 않고 실패한다. 오프라인 검증은 이 방식으로 번들의 목록을 다시 만들어 비교한다.
