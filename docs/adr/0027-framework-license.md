# ADR-0027 프레임워크 라이선스: Apache-2.0

- 상태: 승인 (2026-11-06, 사용자 요청: 상업적 이용과 재사용을 모두 허용)
- 출처: 레포 공개(github.com/Salvia95/cozy-framework). ADR-0026(제3자 라이선스 목록)과 함께 본다.

## 결정

Cozy는 Apache License 2.0으로 배포한다.

- 레포 루트에 `LICENSE`(apache.org 원문 그대로)와 `NOTICE`를 둔다.
- 프레임워크 모듈 POM(`cozy-rules`, `cozy-starter-*`, `cozy-ui`, `cozy-test`, `cozy-archetype`)에 `<licenses>`와 `<url>`을 적는다.
- `cozy-parent`에는 적지 않는다. 앱이 cozy-parent를 상속하므로, 적으면 사내 앱 POM까지 Apache-2.0으로 표시된다.
- 생성 앱의 라이선스는 앱 주인이 정한다. 아키타입은 라이선스를 넣지 않는다.

## 근거

### 상업적 이용과 재사용

- **허용 범위**: Apache-2.0은 다음을 모두 허용한다.
  - 상업적 이용, 수정, 재배포
  - 사내 비공개 앱에 넣기
- **명시적 특허 허여**: MIT에는 없는 조항이다. 회사가 쓸 때 법무 검토에 유리하다.
- **조건**: 라이선스와 NOTICE를 함께 배포해야 한다. 수정한 파일에는 수정 사실을 표시해야 한다.
  - 배포 패키지(`-Pdist`)가 `licenses/cozy-LICENSE.txt`, `cozy-NOTICE.txt`를 자동으로 담는다. 앱 쪽에서 따로 할 일이 없다.
- **생태계와의 일치**: 주요 의존성(Spring Framework·Boot·Modulith, MyBatis, Thymeleaf, Flyway, sqlite-jdbc, HikariCP)과 라이선스가 같다.

### 사용 중인 라이브러리와의 호환 (2026-11-06 확인)

프레임워크 전체 의존성 128개(테스트·빌드 범위 포함)를 `license:aggregate-add-third-party`로 확인했다. Cozy를 Apache-2.0으로 배포하는 데 걸리는 것은 없다.

| 라이선스 | 예 | 판단 |
| --- | --- | --- |
| Apache-2.0 | Spring, MyBatis, Thymeleaf, Flyway, sqlite-jdbc, Jackson, Tomcat | 같은 라이선스 |
| MIT, BSD-3-Clause, EDL-1.0 | slf4j, Mockito, Hamcrest, ASM, vnu, Jakarta Activation·XML Binding | 허용형. 고지만 유지하면 된다 |
| EPL-2.0 | JUnit, Jakarta API | 의존성으로 쓰는 것은 문제없다. Cozy 코드에 섞지 않는다 |
| EPL-2.0 또는 LGPL-2.1 | logback | EPL-2.0 쪽을 고른다 |
| EPL-2.0 또는 GPL-2.0 + Classpath 예외 | jakarta.annotation-api | EPL-2.0 쪽을 고른다 |
| MPL-2.0 | Saxon-HE (vnu가 쓴다, 테스트 범위) | 파일 단위 약한 카피레프트. 수정 없이 의존성으로만 쓰므로 Cozy에 영향이 없다 |
| Unicode-3.0 | icu4j (vnu, 테스트 범위) | 허용형 |
| POM에 정보 없음 | isorelax (vnu, 테스트 범위) | 배포 패키지에 들어가지 않는다. 실제 라이선스는 MIT로 알려져 있다 |

- 웹 자산도 확인했다. Cozy가 소스와 jar에 그대로 담아 재배포하는 것은 각 라이선스가 허용한다. 원문은 벤더 폴더에, 목록은 NOTICE에 둔다.
  - Tabler: MIT
  - htmx: 0BSD
  - Pretendard: SIL OFL-1.1. 글꼴만 따로 판매하지 않는 한 소프트웨어와 함께 배포할 수 있다. 예약 글꼴 이름은 수정본에 쓰지 않는다.
- GPL만 허용하는 의존성은 없다. 그래서 Cozy를 허용형 라이선스로 낼 수 있다.

### 검토한 대안

- **MIT**: 더 짧다. 하지만 특허 조항이 없다.
- **EPL·MPL·LGPL**: 수정본 공개 의무가 있다. "재사용을 모두 허용"과 맞지 않는다.

## 결과

- 앱 배포 패키지의 `licenses/THIRD-PARTY.txt`에 cozy 모듈도 `(Apache-2.0)`으로 나온다. ADR-0026의 cozy 모듈 제외는 없앴다.
- 이후 새 의존성이나 웹 자산은 허용 목록(ADR-0026)과 이 판단에 맞춰 검토한다. 맞지 않으면 추가하지 않고 먼저 제안한다.
