#!/usr/bin/env bash
# 릴리스 빌드의 버전 확인. GitHub Actions의 GITHUB_OUTPUT 형식(version=...)으로 출력한다.
#
#   distribution/release/check-version.sh <ref 종류: tag|branch> <ref 이름>
#
# - 모든 cozy 모듈 POM이 cozy-parent의 cozy.version과 같은 버전을 쓴다
# - 태그 빌드면 태그가 v{버전}이고, 버전이 SNAPSHOT이 아니다
set -euo pipefail

REF_TYPE="${1:-branch}"
REF_NAME="${2:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
VERSION="$(sed -n 's|.*<cozy.version>\(.*\)</cozy.version>.*|\1|p' cozy-parent/pom.xml | head -1)"
if [ -z "$VERSION" ]; then
  echo "[cozy] cozy-parent/pom.xml에서 cozy.version을 찾지 못했습니다" >&2
  exit 1
fi

status=0
while read -r pom; do
  if ! grep -q -F "<version>$VERSION</version>" "$pom"; then
    echo "[cozy] $pom에 버전 $VERSION이 없습니다 (set-version.sh로 바꾸세요)" >&2
    status=1
  fi
done < <(git ls-files '*pom.xml' | grep -vE '/(src/it|archetype-resources)/|^samples/violations/checks/|^samples/recipes/(projects|hand)/')

if [ "$REF_TYPE" = "tag" ]; then
  if [ "$REF_NAME" != "v$VERSION" ]; then
    echo "[cozy] 태그 $REF_NAME이 프레임워크 버전 v$VERSION과 다릅니다" >&2
    status=1
  fi
  case "$VERSION" in
    *-SNAPSHOT) echo "[cozy] SNAPSHOT 버전($VERSION)은 릴리스하지 않습니다" >&2; status=1 ;;
  esac
fi
[ "$status" -eq 0 ] || exit 1
echo "version=$VERSION"
