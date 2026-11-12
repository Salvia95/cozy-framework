#!/usr/bin/env bash
# 릴리스 노트 초안. GitHub Release 초안의 본문이 된다. 게시 전에 변경 내용을 손으로 채운다.
#
#   distribution/release/release-notes.sh <버전> <릴리스 폴더>
#
# 릴리스 폴더에는 JDK 없는 번들, Windows x64 번들(JDK 포함), JDK 소스 묶음이 있다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=../offline-bundle/jdk.env
. "$ROOT/distribution/offline-bundle/jdk.env"

VERSION="${1:?버전이 필요합니다}"
DIR="${2:?릴리스 폴더가 필요합니다}"
NAME="cozy-offline-bundle-$VERSION.zip"
WINDOWS="cozy-offline-bundle-$VERSION-windows-x64.zip"
for file in "$NAME" "$WINDOWS" "$JDK_SOURCES"; do
  test -f "$DIR/$file" || { echo "[cozy] $DIR/$file이 없습니다" >&2; exit 1; }
done
row() { printf '| `%s` | %sMB | `%s` |\n' "$1" "$(du -m "$DIR/$1" | cut -f1)" "$(sha256sum "$DIR/$1" | cut -d' ' -f1)"; }
LIST="$(unzip -p "$DIR/$NAME" "cozy-offline-bundle-$VERSION/licenses/THIRD-PARTY.txt")"
ARTIFACTS="$(sed -n 's/^Lists of \([0-9]*\) artifacts.*/\1/p' <<< "$LIST")"
SOURCES="$(grep -c 'sources.jar\]' <<< "$LIST")"

cat <<MD
# Cozy $VERSION

폐쇄망 Windows PC용 내부 업무 도구 프레임워크 Cozy의 $VERSION 릴리스입니다.

## 변경 내용

<!-- 게시 전에 채운다: 주요 변경, 호환성, 올린 의존성 버전 -->

## 받을 파일

| 파일 | 크기 | SHA-256 |
| --- | --- | --- |
$(row "$WINDOWS")
$(row "$NAME")
$(row "$JDK_SOURCES")

- \`$WINDOWS\`: Windows x64 PC용. JDK($JDK_RELEASE, Eclipse Temurin)가 들어 있어 따로 설치하지 않아도 됩니다.
- \`$NAME\`: JDK가 없는 번들. 운영체제와 상관없이 쓰고, JDK 25는 따로 설치합니다.
- \`$JDK_SOURCES\`: Windows x64 번들에 담은 JDK의 소스(GPL-2.0 with Classpath Exception). 번들을 쓰는 데는 필요 없습니다.

\`SHA256SUMS\`로 확인할 수 있습니다: \`sha256sum -c SHA256SUMS\` (Windows: \`Get-FileHash -Algorithm SHA256 $WINDOWS\`).

두 번들에 공통으로 다음이 들어 있습니다.

- Maven 저장소(\`repository/\`): Cozy 모듈 전체와, 앱 빌드·검사·패키징에 필요한 라이브러리와 플러그인
- Maven 3.10.0 배포판, Cozy 명령(\`cozy.cmd\`·\`cozy\`: \`install\`, \`new\`, \`describe\`, \`check\`, \`recipes\`, \`add\`, \`link\`, \`enable\`, \`sync\`, \`set\`, \`version\`, \`help\`)과 \`lib/cli/cozy-cli.jar\`
- 문서(\`docs/\`: 규약, 결정 기록, 에이전트 문서 원본 \`agents/\`)
- 라이선스: Cozy는 Apache-2.0(\`LICENSE\`, \`NOTICE\`). 저장소의 아티팩트 ${ARTIFACTS}개의 라이선스 목록은 \`licenses/THIRD-PARTY.txt\`에 있습니다. 약한 카피레프트 라이선스만 있는 jar ${SOURCES}개는 소스 jar를 함께 담았습니다.

## 설치

1. 번들을 오프라인 PC로 옮겨 풀고 번들 폴더에서 \`.\\cozy.cmd install\`을 실행합니다. JDK 없는 번들이면 먼저 JDK 25(Temurin 등)를 설치합니다.
2. 안내대로 PATH에 \`<설치 폴더>\\bin\`을 더합니다. 그 안의 \`cozy\`와 \`mvn\`은 설치 폴더의 JDK(있으면)로 돌고, 시스템의 \`JAVA_HOME\`은 바꾸지 않습니다.
3. \`cozy new\`로 앱을 만들고 \`mvn -o verify\`, \`mvn -o -Pdist package\`로 빌드합니다.

앱 사용자 PC에는 JDK가 필요 없습니다(app-image에 런타임이 들어 있습니다).

자세한 절차는 번들의 \`README.md\`와 \`docs/conventions/getting-started.md\`에 있습니다.

## 확인한 것

- 빌드: 프레임워크 전체 테스트, 배포·업데이트 종단 검증(Linux)
- 번들: 옵션 3조합(없음 / persistence / persistence+events)을 네트워크 장치가 루프백뿐인 상태에서 생성·\`cozy add\`·\`enable\`·\`sync\`·\`verify\`·\`-Pdist package\`
- 번들 JDK: 같은 JDK의 Linux x64 판을 담은 번들로, 시스템 JDK를 막은 상태에서 위 검증을 한 번 더(Windows x64 번들은 JDK 파일·판·라이선스 목록만 Linux에서 확인)
- Windows 확인 기록: \`docs/conventions/windows-verification.md\`
MD
