# ADR-0026 제3자 라이선스 목록과 허용 목록

- 상태: 승인 (2026-11-06, 사용자가 플러그인 도입을 승인)
- 출처: PRD §11 "포함 의존성·자산은 Apache-2.0, MIT, BSD, OFL, EPL 등 허용 목록만, 제3자 라이선스 목록을 배포물에 포함". ADR-0017(버전 정책)의 표에 플러그인을 더한다.

## 결정

### 1. license-maven-plugin 2.7.1을 dist 프로필에서만 쓴다

- **도구**: `org.codehaus.mojo:license-maven-plugin:2.7.1`의 `add-third-party`
  - 2026-11-06 기준 최신 GA다(2026-01 릴리스).
  - 의존성 POM의 `<licenses>`를 읽어 목록을 만든다.
  - 수작업 목록은 의존성이 바뀔 때마다 어긋나므로 쓰지 않는다.
- **위치**: cozy-parent의 `dist` 프로필, `prepare-package` 단계에서 돈다. 평소 `mvn verify`에는 영향이 없다.
- **대상**: 런타임 의존성(test, provided, system 범위 제외)이다.
  - cozy 모듈 자신은 뺀다. 전이 의존성은 남는다.
- **실패 조건**: 다음 경우 패키징이 실패한다.
  - 라이선스 정보가 없는 의존성(`failOnMissing`)
  - 허용 목록에 없는 라이선스(`failOnBlacklist`)
- **`force=true`**: 결과 파일이 최신이면 플러그인이 검사를 건너뛴다. 그래서 매번 다시 만든다(확인함).
- **`useRepositoryMissingFiles=false`**: 기본값(true)은 원격 저장소에서 보충 파일을 찾는다. 오프라인 빌드를 위해 끈다.

### 2. 허용 목록은 SPDX ID로 맞춰 한 곳에 둔다

- **허용 목록**: `cozy.dist.allowed-licenses` = `Apache-2.0|MIT|BSD-2-Clause|BSD-3-Clause|EPL-1.0|EPL-2.0`
- **이름 맞추기**: 의존성마다 라이선스 이름 표기가 다르다(`The Apache Software License, Version 2.0` 등). `licenseMerges`로 SPDX ID에 맞춘다. 맞추지 못한 이름은 허용 목록에 없으므로 실패로 닫는다.
- **이중 라이선스**: 하나만 허용 목록에 있으면 된다. 예: logback(EPL-2.0 또는 LGPL-2.1), jakarta.annotation-api(EPL-2.0 또는 GPL2 w/ CPE).
- **OFL 등 웹 자산 라이선스**: Maven 의존성이 아니라 이 목록의 대상이 아니다. 웹 자산은 cozy-ui의 벤더 폴더에 LICENSE와 함께 들어오고, 추가할 때 ADR-0017 표에서 검토한다.
- **목록을 바꿀 때**: 이 ADR을 먼저 고친다. 앱 POM에서 속성을 덮어쓸 수는 있지만 규약상 하지 않는다.

### 3. CozyPackager가 목록을 다시 검사하고 실행 폴더에 담는다

- **다시 검사하는 이유**: 플러그인의 허용 목록 경고가 같은 라이선스를 쓰는 의존성 중 일부만 보여 주는 경우가 있었다(MIT 2개 중 1개만 표시). 그래서 `ThirdPartyLicenses`가 결과 파일을 다시 읽어 검사한다.
  - 모든 줄에 허용 라이선스가 하나 이상 있어야 한다.
  - 줄 형식이 틀리거나, 머리말의 개수와 줄 수가 다르면 실패한다.
- **배포물**: 실행 폴더의 `licenses/`에 담는다. 업데이트 zip에도 같이 들어간다.

  | 파일 | 내용 |
  | --- | --- |
  | `THIRD-PARTY.txt` | 의존성 목록 + 웹 자산 목록 |
  | `vendor/{이름}-{버전}-LICENSE.txt` | 클래스패스 jar의 `META-INF/resources/cozy/vendor/*/*/LICENSE` |

  - JDK 런타임의 라이선스는 jpackage가 런타임 폴더의 `legal/`에 둔다.

## 플러그인 도입 검토 (CLAUDE.md: 이유, 라이선스, 폐쇄망 반입 영향)

| 항목 | 내용 |
| --- | --- |
| 이유 | PRD §11의 라이선스 목록·허용 목록 요구. 수작업 목록은 의존성이 바뀔 때마다 어긋난다 |
| 플러그인 라이선스 | LGPL-3.0. 빌드 도구로만 쓰고 배포물에 들어가지 않는다 |
| 폐쇄망 반입 | 오프라인 번들 빌드가 `-Pdist`를 돌리므로 플러그인과 그 의존성이 번들 저장소에 들어간다. 원격 보충 파일 조회를 껐다. 오프라인 검증(`verify-offline.sh`)으로 확인한다 |
| 빌드 시간 | `-Pdist`에 1초 안팎 |

## 결과

- reference-app 기준 의존성 77개(Apache-2.0 72, EPL-2.0 3, MIT 2)와 웹 자산 3개(Tabler, htmx, Pretendard)가 목록에 들어간다.
- 허용 목록 밖 라이선스의 의존성을 더하면 `-Pdist`가 실패한다. 이때 의존성과 라이선스가 메시지에 나온다.
