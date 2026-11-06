#!/usr/bin/env bash
# 릴리스 노트 초안 (ADR-0028). GitHub Release 초안의 본문이 된다. 게시 전에 변경 내용을 손으로 채운다.
#
#   distribution/release/release-notes.sh <버전> <번들 zip>
set -euo pipefail

VERSION="${1:?버전이 필요합니다}"
ZIP="${2:?번들 zip이 필요합니다}"
NAME="$(basename "$ZIP")"
SHA256="$(sha256sum "$ZIP" | cut -d' ' -f1)"
SIZE="$(du -m "$ZIP" | cut -f1)"
LIST="$(dirname "$ZIP")/cozy-offline-bundle-$VERSION/licenses/THIRD-PARTY.txt"
ARTIFACTS="$(sed -n 's/^Lists of \([0-9]*\) artifacts.*/\1/p' "$LIST")"
SOURCES="$(grep -c 'sources.jar\]' "$LIST")"

cat <<MD
# Cozy $VERSION

폐쇄망 Windows PC용 내부 업무 도구 프레임워크 Cozy의 $VERSION 릴리스입니다.

## 변경 내용

<!-- 게시 전에 채운다: 주요 변경, 호환성, 올린 의존성 버전 -->

## 받을 파일

| 파일 | 크기 | SHA-256 |
| --- | --- | --- |
| \`$NAME\` | ${SIZE}MB | \`$SHA256\` |

\`SHA256SUMS\`로 확인할 수 있습니다: \`sha256sum -c SHA256SUMS\` (Windows: \`Get-FileHash -Algorithm SHA256 $NAME\`).

오프라인 번들 하나에 다음이 들어 있습니다.

- Maven 저장소(\`repository/\`): Cozy 모듈 전체와, 앱 빌드·검사·패키징에 필요한 라이브러리와 플러그인
- Maven 3.10.0 배포판, 설치 스크립트(\`install-offline.ps1\`·\`.sh\`), 새 앱 스크립트(\`new-app.ps1\`·\`.sh\`)
- 문서(\`docs/\`: 규약, 결정 기록, AGENTS.md)
- 라이선스: Cozy는 Apache-2.0(\`LICENSE\`, \`NOTICE\`). 저장소의 아티팩트 ${ARTIFACTS}개의 라이선스 목록은 \`licenses/THIRD-PARTY.txt\`에 있습니다. 약한 카피레프트 라이선스만 있는 jar ${SOURCES}개는 소스 jar를 함께 담았습니다.

## 설치

1. JDK 25(Temurin 등)를 설치합니다. 번들에는 JDK가 없습니다. 앱 사용자 PC에는 JDK가 필요 없습니다.
2. 번들을 오프라인 PC로 옮겨 풀고 \`install-offline.ps1\`을 실행합니다.
3. \`new-app.ps1\`로 앱을 만들고 \`mvn -o verify\`, \`mvn -o -Pdist package\`로 빌드합니다.

자세한 절차는 번들의 \`README.md\`와 \`docs/conventions/getting-started.md\`에 있습니다.

## 확인한 것

- 빌드: 프레임워크 전체 테스트, 배포·업데이트 종단 검증(Linux)
- 번들: 옵션 3조합(없음 / persistence / persistence+events)을 네트워크 장치가 루프백뿐인 상태에서 생성·\`verify\`·\`-Pdist package\`
- Windows 확인 기록: \`docs/conventions/windows-verification.md\`
MD
