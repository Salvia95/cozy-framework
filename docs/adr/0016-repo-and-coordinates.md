# ADR-0016 레포 위치와 Maven 좌표

- 상태: 승인 (2026-11-05)
- 출처: 구현 전 확인 사항 (PRD §5, §13의 groupId 미결 항목)

## 결정

- groupId와 기본 Java 패키지는 `io.salvia95.cozy`로 한다. PRD의 임시값 `com.example.cozy`를 대체한다.
- 레포 루트는 `cozy-framework/`로 한다. PRD는 레포 이름을 `cozy/`로 적었지만, 모듈 이름(`cozy-*`)과 그 밖의 명명 규칙은 PRD를 그대로 따른다.
- 프레임워크 버전은 `1.0.0-SNAPSHOT`에서 시작한다. 모든 cozy-* 모듈이 이 버전을 함께 쓴다.
- 루트 aggregator의 artifactId는 `cozy-build`, reference-app의 artifactId는 `cozy-reference-app`이다.

## 근거

- 개인 GitHub 계정(salvia95)을 기준으로 좌표를 지금 확정하면, 나중에 일괄 치환할 필요가 없다.
- 폴더 이름은 빌드 결과에 영향을 주지 않는다.

## 결과

- PRD 본문의 `com.example.cozy`는 모두 `io.salvia95.cozy`로 읽는다.
- `cozy-framework/` 폴더 이름은 산출물에 드러나지 않는다.
