# ADR-0019 빌드 규칙의 검증 방식

- 상태: 승인 (2026-11-05)
- 출처: PRD §5 모듈 의존 규칙, §6.1 수용 기준, §14 (새 의존성 제안)

## 결정

- **금지 의존성과 금지 플러그인**: cozy-parent의 `cozy-enforce` 실행이 검사한다(bannedDependencies, bannedPlugins). 이 실행은 앱에도 상속된다.
- **모듈 의존 방향(§5)**: 각 모듈의 `cozy-module-boundary` 실행(bannedDependencies)이 검사한다.
  - cozy-rules: compile, runtime, provided, system 범위에서 AttoParser만 허용한다.
  - 스타터: events → sqlite → runtime 한 방향만 허용한다.
  - cozy-ui: cozy-starter-runtime만 허용한다.
- **cozy-test의 optional 참조**: enforcer는 optional 여부를 볼 수 없다. 그래서 `PomBoundaryTest`가 POM을 직접 읽어 검사한다.
- **"빌드가 실패해야 한다"는 기준**: `maven-invoker-plugin`(Apache-2.0)으로 검증한다. 픽스처는 `cozy-parent/src/it/`에 있고, 각 픽스처의 `verify.groovy`가 실패 원인이 해당 enforcer 규칙인지까지 확인한다. 의존성 해석 실패처럼 다른 이유로 실패해도 통과하는 일을 막기 위해서다.
- invoker는 기본 로컬 저장소(`~/.m2`)를 쓴다. 그래서 `mvn verify`를 실행하면 cozy-parent가 로컬 저장소에 설치된다.

## 근거

- invoker는 PRD §4 표에 없는 빌드 플러그인이다. 2026-11-05 구현 계획과 함께 승인받았다.
- `archetype:integration-test`도 같은 방식으로 동작하므로, 폐쇄망에 추가로 반입할 부담이 작다.
- 별도 로컬 저장소를 쓰면 오프라인 번들(`~/.m2` 통째 반입)에 IT용 의존성이 들어가지 않는다. 기본 로컬 저장소를 쓰면 번들을 만들 때 함께 채워진다.

## 결과

- M0에서 각 규칙을 일부러 어긴 사본으로 실패하는지 확인했다:
  - cozy-ui → sqlite
  - cozy-rules → spring-core
  - cozy-rules → runtime
  - sqlite → ui
  - cozy-test의 optional이 아닌 참조
  - cozy.version 불일치
- optional로 참조하면 통과하는 것도 확인했다.
- M7의 아키타입 통합 테스트도 이 방식을 따른다.
