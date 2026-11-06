# jpackage

app-image 생성, SQLite 네이티브 라이브러리 반입, AOT 캐시 훈련 실행, zip과 업데이트 매니페스트 생성은 cozy-parent의 `dist` 프로필이 한다(`mvn -Pdist package`). 따로 둘 스크립트는 없다.

- 구현: `cozy-starter-runtime`의 `internal/dist/CozyPackager`
- 문서: [docs/conventions/distribution.md](../../docs/conventions/distribution.md)
- 결정: [ADR-0024](../../docs/adr/0024-distribution-and-update.md)
