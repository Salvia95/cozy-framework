# ADR-0013 프레임워크는 의존성으로 배포

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D13

## 결정

프레임워크는 버전이 있는 Maven 의존성으로 배포한다. 아키타입은 parent와 스타터만 참조하는 얇은 앱을 만든다.

## 근거

- 버그 수정이 버전 업으로 모든 도구에 전파된다.

## 결과

- cozy-parent의 `cozy.version`은 앱이 상속해도 프레임워크 버전을 가리키도록 리터럴로 둔다.
- 이 값과 project.version이 같은지는 cozy-parent에서만 실행되는 enforcer 규칙(cozy-version-consistency)이 검사한다.
