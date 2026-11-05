# ADR-0004 MyBatis와 SQLite, JPA 미사용

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D4

## 결정

영속성은 MyBatis와 SQLite로 구현한다. JPA, Hibernate, H2는 빌드에서 차단한다.

## 근거

- Hibernate의 SQLite 방언은 커뮤니티 모듈이라 공식 지원이나 CI 테스트 대상이 아니다.

## 결과

- cozy-parent의 bannedDependencies 규칙이 강제한다. invoker 픽스처 banned-jpa와 banned-h2가 이를 검증한다.
- 테스트 DB도 SQLite를 쓴다.
