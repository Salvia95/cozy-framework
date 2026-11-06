# 성능과 비기능 목표 측정

PRD §11의 목표는 reference-app을 Windows 11 x64에서 측정한다. 이 문서는 측정 방법과 결과를 남긴다. Windows 측정 전까지는 WSL 측정값을 참고값으로 둔다.

## 측정 방법

- **대상**: `mvn -Pdist package`로 만든 app-image. AOT 캐시가 들어 있다.
- **기동 시간**: Spring Boot 로그의 `process running for`(JVM 시작부터 웹 서버 준비까지)다.
  - 런처 실행과 브라우저 표시 시간은 빠진다. Windows에서는 실행 파일 클릭부터 화면 표시까지를 손으로 함께 잰다.
  - 같은 조건으로 한 번 예열한 뒤 5회를 잰다.
  - AOT 없음은 런처 cfg에서 `-XX:AOTCache` 줄만 빼고 잰다.
- **힙**: 기동 2초 뒤 `jcmd <pid> GC.heap_info`의 사용량이다(G1, `-Xmx512m`).
- **RSS**: 같은 시점의 `/proc/<pid>/status` `VmRSS`다. Windows에서는 작업 관리자의 "메모리(활성 개인 작업 집합)"으로 본다.
- **크기**: `du -sm`(app-image 폴더)과 zip 파일 크기다.

Linux 측정은 `distribution/perf/measure-startup.sh <app-image 폴더> <appId>`가 위 절차를 그대로 한다.

## 결과: WSL2 참고값 (2026-11-06)

환경: Ubuntu 24.04 on WSL2, i7-1165G7 (8 스레드), 메모리 9GB, Temurin 25.0.3. Linux app-image다.

### 기동 시간 (초, 5회)

| 앱 | AOT 캐시 | 중앙값 | 범위 |
| --- | --- | --- | --- |
| 생성 앱 (옵션 없음) | 있음 | 1.22 | 1.15–1.68 |
| 생성 앱 (옵션 없음) | 없음 | 2.60 | 2.46–2.73 |
| 생성 앱 (persistence + events) | 있음 | 1.80 | 1.69–1.82 |
| 생성 앱 (persistence + events) | 없음 | 3.90 | 3.84–3.98 |
| reference-app | 있음 | 1.53 | 1.48–1.58 |
| reference-app | 없음 | 3.50 | 3.43–4.04 |

AOT 캐시로 기동 시간이 절반 이하로 준다(reference-app 3.50초 → 1.53초). 목표인 "AOT 없이 5초 이내"는 WSL에서는 만족한다. M6 기록(5.2초 → 2.2초)과 차이가 나는 것은 측정 시점의 다른 부하 때문으로 보인다. 이 표는 예열 후 5회 중앙값이다.

### 메모리 (유휴, 기동 2초 뒤)

| 앱 | 힙 사용량 | RSS |
| --- | --- | --- |
| 생성 앱 (옵션 없음) | 약 33MB | 약 240MB |
| 생성 앱 (persistence + events) | 25–45MB | 260–300MB |
| reference-app | 약 32MB(AOT 있음), 40–48MB(없음) | 270–300MB |

목표인 "유휴 힙 256MB 이하"는 여유가 크다. RSS가 힙보다 큰 이유는 다음과 같다.

- JVM 코드 영역과 메타스페이스
- AOT 캐시 매핑(캐시를 쓰면 RSS가 20~30MB 늘고 기동은 빨라진다)
- Tomcat 스레드

### 크기

| 앱 | app-image | 그중 AOT 캐시 | 런타임(jlink) | zip |
| --- | --- | --- | --- | --- |
| 생성 앱 (옵션 없음) | 237MB | 71MB | 136MB | 94MB |
| 생성 앱 (persistence + events) | 268MB | 86MB | 136MB | 113MB |
| reference-app | 268MB | 86MB | 136MB | 113MB |

- 런타임은 jpackage 기본 jlink 결과다. 모듈 최소화는 이후 과제다(PRD §11).
- AOT 캐시는 크지만 zip에서 많이 줄어든다.
- 업데이트는 zip 전체를 받는다.

### 오프라인 빌드

| 항목 | 결과 |
| --- | --- |
| 번들 크기 | 195MB (Maven 저장소 + Maven 3.10.0 + 문서) |
| 번들만으로 생성·`mvn -o verify`·`mvn -o -Pdist package` | 3조합 통과. 네트워크 장치가 루프백뿐인 상태(`verify-offline.sh`)에서 확인했다 |
| 빌드 중 외부 요청 | 0건. 네트워크 장치가 없어 외부 요청은 실패할 수밖에 없고, 빌드가 성공했다 |

## Windows 11 측정 (사용자 확인 대기)

windows-verification.md M6 "직접 설치해서 확인" 절차로 reference-app을 설치한 뒤 다음을 적는다.

| 항목 | 목표 | 결과 |
| --- | --- | --- |
| 실행 파일 클릭 → 브라우저 화면 (AOT 없음) | 5초 이내 | |
| 실행 파일 클릭 → 브라우저 화면 (AOT 있음) | 개선폭 기록 | |
| 유휴 메모리(작업 관리자) | 힙 256MB 이하 | |
| app-image / zip 크기 | 기록 | |

AOT 없이 재려면 `app\cozy-reference.cfg`의 `java-options=-XX:AOTCache=...` 줄을 잠시 지운다.
