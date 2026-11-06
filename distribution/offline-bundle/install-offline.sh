#!/usr/bin/env bash
# Cozy 오프라인 번들 설치 (인터넷이 없는 PC). 번들 폴더 안에서 실행한다.
#
#   ./install-offline.sh [설치 폴더]      (기본: ~/cozy)
#
# Maven 배포판을 풀고, 그 Maven의 conf/settings.xml이 번들 저장소를 가리키며 항상 오프라인으로 동작하게 한다.
set -euo pipefail

BUNDLE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${1:-$HOME/cozy}"
VERSION="$(cat "$BUNDLE/COZY_VERSION")"

java_major() { java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p'; }
if ! command -v java > /dev/null || [ "$(java_major)" -lt 25 ]; then
  echo "[cozy] JDK 25가 필요합니다. JAVA_HOME과 PATH를 확인하세요." >&2
  exit 1
fi

mkdir -p "$DEST"
ZIP="$(ls "$BUNDLE"/apache-maven-*-bin.zip | head -1)"
MAVEN_DIR="$DEST/$(basename "$ZIP" -bin.zip)"
rm -rf "$MAVEN_DIR"
(cd "$DEST" && unzip -q "$ZIP")
if [ "$BUNDLE/repository" != "$DEST/repository" ]; then
  rm -rf "$DEST/repository"
  cp -r "$BUNDLE/repository" "$DEST/repository"
fi
rm -rf "$DEST/docs" && cp -r "$BUNDLE/docs" "$DEST/docs"
rm -rf "$DEST/licenses" && cp -r "$BUNDLE/licenses" "$DEST/licenses"
cp "$BUNDLE/LICENSE" "$BUNDLE/NOTICE" "$DEST/"
cp "$BUNDLE/new-app.sh" "$BUNDLE/COZY_VERSION" "$DEST/"

cat > "$MAVEN_DIR/conf/settings.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<!-- Cozy 오프라인 번들 $VERSION: 번들 저장소만 쓰고 네트워크에 나가지 않는다 -->
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>$DEST/repository</localRepository>
  <offline>true</offline>
</settings>
XML

echo "[cozy] 설치했습니다: $DEST"
echo "  PATH에 추가: export PATH=\"$MAVEN_DIR/bin:\$PATH\""
echo "  새 앱:       $DEST/new-app.sh"
