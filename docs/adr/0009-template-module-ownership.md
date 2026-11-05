# ADR-0009 템플릿 디렉터리 = 모듈 소속

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D9

## 결정

`templates/{module}/`은 그 모듈의 소유이고, `api/` 아래만 공개 영역이다. 허용 의존은 Java 쪽 `@ApplicationModule` 선언에서만 읽는다.

## 근거

- Java 쪽 선언 하나로 화면 경계까지 검사할 수 있다.

## 결과

- resources에는 경계 메타데이터를 두지 않는다.
- cozy-rules R1~R8이 이 결정을 강제한다(M4).
