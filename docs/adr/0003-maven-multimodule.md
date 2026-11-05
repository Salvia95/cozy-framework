# ADR-0003 Maven 멀티모듈

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D3

## 결정

단일 레포의 Maven 멀티모듈로 구성하고, 모든 cozy-* 모듈을 하나의 버전으로 함께 릴리스한다.

## 근거

- .m2를 통째로 반입하면 오프라인 빌드가 된다.
- archetype이 표준 생성기다.
- 선언형 POM은 작은 LLM이 다루기 쉽다.

## 결과

- 루트 pom.xml은 aggregator(cozy-build)이고, 모든 모듈은 cozy-parent를 상속한다.
- 앱도 같은 cozy-parent를 상속한다.
