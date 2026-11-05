# ADR-0002 GraalVM 네이티브 이미지 미사용

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D2

## 결정

GraalVM 네이티브 이미지를 쓰지 않는다. native-maven-plugin과 GraalVM SDK 의존성은 빌드에서 차단한다.

## 근거

- MyBatis와 이벤트 직렬화의 리플렉션 힌트가 빠지면 네이티브에서만 런타임 오류로 드러난다.
- Windows 네이티브 빌드 도구를 폐쇄망에 반입하는 부담이 크다.

## 결과

- 기동 시간은 AOT 캐시(ADR-0001)로 개선한다.
- cozy-parent의 bannedPlugins와 bannedDependencies 규칙이 이 결정을 강제한다. invoker 픽스처 banned-native-plugin이 이를 검증한다.
