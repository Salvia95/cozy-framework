# ADR-0001 JDK 25와 jpackage 런타임 번들

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D1

## 결정

JDK 25(LTS)로 빌드하고, jpackage app-image에 런타임을 함께 넣어 배포한다.

## 근거

- 회사 표준 JDK와 무관하게 실행 환경을 고정할 수 있다.
- 가상 스레드가 안정화되었고, AOT 캐시(JEP 483·514·515)를 쓸 수 있다.

## 결과

- cozy-parent의 enforcer가 Java 25 이상을 요구한다.
- 빌드 PC에는 JDK 25가 필요하지만 사용자 PC에는 JDK가 필요 없다.
