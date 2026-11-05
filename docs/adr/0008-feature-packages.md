# ADR-0008 기능형 패키지

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D8

## 결정

패키지는 기능 단위(`app/{module}/`)로 나눈다. 계층형 패키지(controller/, service/ 등)는 금지한다.

## 근거

- Modulith의 모듈 단위와 일치한다.
- 작은 LLM이 한 모듈만 보면 되므로 컨텍스트를 아낄 수 있다.

## 결과

- ArchitectureTest(Modulith verify)가 모듈 경계를 검사한다(M4).
