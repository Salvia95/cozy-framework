# Cozy

폐쇄망 Windows PC에서 도는 내부 업무 도구를 위한 경량 프레임워크다. 앱 하나가 실행 파일 하나(jpackage app-image)로 배포되고, 브라우저 화면으로 쓴다.

- 스택: JDK 25, Spring Boot 4.1 (Spring MVC), Thymeleaf, htmx, Tabler, MyBatis, SQLite, Spring Modulith
- 배포: 런타임이 들어 있는 app-image, AOT 캐시, 공유 폴더 자체 업데이트와 자동 롤백
- 규칙: 모듈 경계, 템플릿 경계(R1~R8), 렌더링·HTML 검사(C1~C7)를 `mvn verify`가 검사한다
- 오프라인: 인터넷 없는 PC에서 앱을 만들고 빌드·패키징하는 번들

## 모듈

| 모듈 | 내용 |
| --- | --- |
| `cozy-parent` | 앱과 프레임워크 모듈의 부모 POM. 빌드 규칙, `-Pdist` 패키징 |
| `cozy-starter-runtime` | 데이터 폴더, 단일 인스턴스, 보안 최소선, 트레이, 업데이트, 진단, 패키저 |
| `cozy-starter-sqlite` | SQLite + MyBatis + Flyway |
| `cozy-starter-events` | 모듈 사이 이벤트(SQLite 발행 기록, 인박스) |
| `cozy-ui` | 레이아웃·패턴·컴포넌트, 패턴 카탈로그 |
| `cozy-rules` | 템플릿 경계 규칙 |
| `cozy-test` | 아키텍처·렌더링 테스트 베이스 클래스 |
| `cozy-archetype` | 새 앱 생성기 |
| `samples/` | reference-app, 위반 픽스처, 배포 종단 검증 |
| `distribution/` | 오프라인 번들, Windows 확인 스크립트, 측정 스크립트 |

## 시작

```bash
mvn install                                 # JDK 25, Maven 3.10
mvn org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate -DarchetypeCatalog=local \
  -DarchetypeGroupId=io.salvia95.cozy -DarchetypeArtifactId=cozy-archetype -DarchetypeVersion=1.0.0-SNAPSHOT
```

## 문서

- 새 앱 만들기: [docs/conventions/getting-started.md](docs/conventions/getting-started.md)
- 공개 API와 규약: [docs/conventions/](docs/conventions/README.md)
- 결정 기록: [docs/adr/](docs/adr/README.md)
- 코딩 에이전트용 규칙(생성 앱에 들어간다): [docs/AGENTS.md](docs/AGENTS.md)
- 오프라인 번들: [distribution/offline-bundle/README.md](distribution/offline-bundle/README.md)
