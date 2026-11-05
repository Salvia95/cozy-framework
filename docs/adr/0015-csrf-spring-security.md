# ADR-0015 CSRF는 Spring Security로 처리

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D15

## 결정

로그인은 없지만, CSRF 방어와 보안 헤더를 위해 Spring Security를 쓴다.

## 근거

- Thymeleaf 폼에 토큰을 자동으로 넣어 준다.
- 검증된 구현이다.

## 결과

- 모든 요청을 허용하고 CSRF는 켠다. htmx는 meta 태그에서 토큰을 읽어 헤더로 보낸다(M1, M3).
