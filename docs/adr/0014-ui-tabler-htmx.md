# ADR-0014 UI는 Tabler, htmx, Pretendard와 Thymeleaf fragment

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D14

## 결정

UI는 Tabler(MIT), htmx(0BSD), Pretendard(OFL-1.1)로 구성한다. 화면은 Thymeleaf fragment 중심으로 만든다.

## 근거

- 프론트 빌드 도구가 필요 없다.
- Bootstrap 클래스는 작은 LLM이 정확하게 생성하는 편이다.
- fragment가 곧 htmx 응답 단위다.

## 결과

- 벤더 자산은 레포에 커밋하고, vendor.lock의 SHA-256으로 검증한다(M3).
- 버전은 ADR-0017을 따른다. 2026-11-05 기준 Tabler 1.7.3, htmx 2.0.11, Pretendard 1.3.9.
- 2026-11-06: 아이콘은 Tabler Icons 3.50.0 전체를 쓴다([ADR-0030](0030-ui-quality.md)).
