# ADR-0005 Spring Modulith 상시 포함, 이벤트는 옵션

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D5

## 결정

모든 앱에 Spring Modulith를 포함한다. 이벤트 저장(cozy-starter-events)은 선택한다.

## 근거

- 템플릿 경계 검사가 Modulith의 모듈 모델을 기준으로 한다.
- 그래서 단순한 앱과 확장된 앱이 같은 템플릿 구조를 쓴다.

## 결과

- spring-modulith-bom을 cozy-parent에서 import한다.
- Modulith 버전은 계약 테스트(PRD §8.5)와 함께 올린다.
