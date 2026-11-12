#!/usr/bin/env bash
# 번들에 담은 JDK의 소스 묶음을 받아 SHA-256을 확인한다. JDK를 담은 번들과 같은 릴리스에 올린다.
#
#   distribution/release/jdk-sources.sh <폴더>
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=../offline-bundle/jdk.env
. "$ROOT/distribution/offline-bundle/jdk.env"

OUT="${1:?폴더가 필요합니다}"
mkdir -p "$OUT"
curl -fsSL -o "$OUT/$JDK_SOURCES" "$JDK_URL/$JDK_SOURCES"
if [ "$(sha256sum "$OUT/$JDK_SOURCES" | cut -d' ' -f1)" != "$JDK_SOURCES_SHA256" ]; then
  echo "[cozy] $JDK_SOURCES의 SHA-256이 jdk.env와 다릅니다" >&2
  rm -f "$OUT/$JDK_SOURCES"
  exit 1
fi
echo "$OUT/$JDK_SOURCES"
