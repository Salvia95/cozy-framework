#!/usr/bin/env bash
# 오프라인 번들에 JDK를 더한 플랫폼 번들을 만든다. 프레임워크 레포에서 build-bundle.sh 다음에 실행한다.
#
#   distribution/offline-bundle/add-jdk.sh <번들 zip> <windows-x64|linux-x64> [JDK 배포판 경로 또는 URL]
#
# 결과: 번들 zip 옆의 cozy-offline-bundle-{버전}-{플랫폼}.zip. 안의 폴더 이름도 같다.
# 1. jdk.env에 고정한 Temurin 배포판을 받아 SHA-256을 확인한다 (경로를 주면 그 파일을 확인한다)
# 2. 번들 폴더의 jdk/에 푼다. lib/src.zip(Java 클래스 라이브러리 소스, 약 50MB)만 지운다.
#    이 JDK는 jmods 없이 자기 런타임에서 jlink하므로, 모듈에 속한 파일(man, legal, conf 등)을 지우거나 고치면
#    jlink와 jpackage(mvn -Pdist package)가 거부한다
# 3. 배포판, 도구(java, javac, jlink, jpackage), 판(release 파일)을 확인한다
# 4. 라이선스 목록을 JDK와 소스 묶음(jdk.env의 JDK_SOURCES, 같은 릴리스에 올린다)까지 넣어 다시 만든다
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=jdk.env
. "$ROOT/distribution/offline-bundle/jdk.env"

ZIP="$(cd "$(dirname "${1:?번들 zip이 필요합니다}")" && pwd)/$(basename "$1")"
PLATFORM="${2:?플랫폼이 필요합니다 (windows-x64, linux-x64)}"
case "$PLATFORM" in
  windows-x64) JDK_FILE="$JDK_WINDOWS_X64" JDK_SHA256="$JDK_WINDOWS_X64_SHA256" EXE=.exe ;;
  linux-x64) JDK_FILE="$JDK_LINUX_X64" JDK_SHA256="$JDK_LINUX_X64_SHA256" EXE= ;;
  *)
    echo "[cozy] 모르는 플랫폼: $PLATFORM (windows-x64, linux-x64)" >&2
    exit 2
    ;;
esac
SOURCE="${3:-$JDK_URL/$JDK_FILE}"

OUT="$(dirname "$ZIP")"
NAME="$(basename "$ZIP" .zip)"
TARGET="$NAME-$PLATFORM"
WORK="$OUT/$TARGET.work"
rm -rf "$WORK" "$OUT/$TARGET.zip"
mkdir -p "$WORK/jdk"
trap 'rm -rf "$WORK"' EXIT
echo "[cozy] $TARGET: $NAME + $JDK_FILE"

# 1. 배포판
case "$SOURCE" in
  http://*|https://*) curl -fsSL -o "$WORK/$JDK_FILE" "$SOURCE" ;;
  *) cp "$SOURCE" "$WORK/$JDK_FILE" ;;
esac
if [ "$(sha256sum "$WORK/$JDK_FILE" | cut -d' ' -f1)" != "$JDK_SHA256" ]; then
  echo "[cozy] $JDK_FILE의 SHA-256이 jdk.env와 다릅니다" >&2
  exit 1
fi

# 2. 번들 폴더와 jdk/
(cd "$WORK" && unzip -q "$ZIP" && mv "$NAME" "$TARGET")
BUNDLE="$WORK/$TARGET"
if [ -e "$BUNDLE/jdk" ]; then
  echo "[cozy] 번들에 이미 jdk/가 있습니다: $ZIP" >&2
  exit 1
fi
case "$JDK_FILE" in
  *.zip) (cd "$WORK/jdk" && unzip -q "$WORK/$JDK_FILE") ;;
  *.tar.gz) tar -xzf "$WORK/$JDK_FILE" -C "$WORK/jdk" ;;
esac
mv "$WORK/jdk/$JDK_RELEASE" "$BUNDLE/jdk"
rm -f "$BUNDLE/jdk/lib/src.zip"

# 3. 확인
for tool in java javac jlink jpackage; do
  if [ ! -f "$BUNDLE/jdk/bin/$tool$EXE" ]; then
    echo "[cozy] JDK에 bin/$tool$EXE가 없습니다" >&2
    exit 1
  fi
done
# Windows 판의 release 파일은 줄 끝이 CRLF다
if ! tr -d '\r' < "$BUNDLE/jdk/release" | grep -qx "IMPLEMENTOR_VERSION=\"Temurin-${JDK_RELEASE#jdk-}\""; then
  echo "[cozy] JDK의 release 파일이 $JDK_RELEASE가 아닙니다" >&2
  exit 1
fi

# 4. 라이선스 목록
VERSION="$(cat "$BUNDLE/COZY_VERSION")"
MAVEN_FILE="$(basename "$(ls "$BUNDLE"/apache-maven-*-bin.zip | head -1)")"
java -cp "$BUNDLE/repository/io/salvia95/cozy/cozy-starter-runtime/$VERSION/cozy-starter-runtime-$VERSION.jar" \
  io.salvia95.cozy.runtime.internal.dist.BundleLicenses --repository="$BUNDLE/repository" \
  --overrides="$BUNDLE/licenses/license-overrides.txt" --output="$BUNDLE/licenses/THIRD-PARTY.txt" \
  --title="Cozy offline bundle $VERSION" --maven-distribution="$MAVEN_FILE" \
  --jdk-distribution="$JDK_FILE" --jdk-sources="$JDK_SOURCES"

(cd "$WORK" && zip -qr "$OUT/$TARGET.zip" "$TARGET")
echo "[cozy] 완료: $OUT/$TARGET.zip ($(du -sh "$OUT/$TARGET.zip" | cut -f1))"
