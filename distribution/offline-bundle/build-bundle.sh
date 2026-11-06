#!/usr/bin/env bash
# Cozy 오프라인 번들 만들기 (PRD §9.7). 인터넷이 되는 PC에서 프레임워크 레포 루트를 기준으로 실행한다.
#
#   distribution/offline-bundle/build-bundle.sh [출력 폴더] [Maven 배포판 zip 경로 또는 URL]
#
# 1. 비어 있는 로컬 저장소로 프레임워크 전체를 빌드·설치한다 (테스트 포함)
# 2. 아키타입으로 옵션 3조합 앱을 만들어 verify와 -Pdist package를 돌린다 → 앱 빌드에 필요한 것이 모두 받아진다
# 3. 저장소에서 원격 저장소 표시(_remote.repositories 등)를 지운다. 사내 미러 설정과 상관없이 오프라인에서 쓸 수 있다
# 4. Maven 배포판, 오프라인 쪽 스크립트, 문서를 담아 zip으로 묶는다
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 루트 target/은 빌드의 clean이 지우므로 build/에 만든다
OUT="${1:-$ROOT/build/offline-bundle}"
MAVEN_ZIP="${2:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip}"
MVN="${MVN:-mvn}"

java_major() { java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p'; }
if [ "$(java_major)" -lt 25 ]; then
  echo "[cozy] JDK 25가 필요합니다 (지금: $(java_major))" >&2
  exit 1
fi

VERSION="$(sed -n 's|.*<cozy.version>\(.*\)</cozy.version>.*|\1|p' "$ROOT/cozy-parent/pom.xml" | head -1)"
BUNDLE="$OUT/cozy-offline-bundle-$VERSION"
REPO="$BUNDLE/repository"
WORK="$OUT/work"
echo "[cozy] 번들 $VERSION → $BUNDLE"
rm -rf "$BUNDLE" "$WORK"
mkdir -p "$REPO" "$WORK"

run() { echo "[cozy] mvn $*"; "$MVN" -B -ntp -Dmaven.repo.local="$REPO" "$@"; }

# 1. 프레임워크 (테스트·검사 픽스처까지 돌려 필요한 플러그인을 모두 받는다)
(cd "$ROOT" && run clean install)

# 2. 옵션 3조합
for combo in basic:false:false persistence:true:false events:true:true; do
  IFS=: read -r name persistence events <<< "$combo"
  (cd "$WORK" && run org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate -DinteractiveMode=false \
    -DarchetypeCatalog=local -DarchetypeGroupId=io.salvia95.cozy -DarchetypeArtifactId=cozy-archetype \
    -DarchetypeVersion="$VERSION" -DgroupId=com.example -DartifactId="bundle-$name" -Dversion=1.0.0 \
    -Dpackage="com.example.bundle.$name" -Dpersistence="$persistence" -Devents="$events")
  (cd "$WORK/bundle-$name" && run verify && run -Pdist package)
done

# 3. 원격 저장소 표시 정리
find "$REPO" \( -name '_remote.repositories' -o -name '*.lastUpdated' -o -name 'resolver-status.properties' \) -delete
# 번들 검증용으로 만든 앱은 담지 않는다
rm -rf "$REPO/com/example"

# 4. Maven 배포판, 스크립트, 문서
case "$MAVEN_ZIP" in
  http://*|https://*) curl -fsSL -o "$BUNDLE/$(basename "$MAVEN_ZIP")" "$MAVEN_ZIP" ;;
  *) cp "$MAVEN_ZIP" "$BUNDLE/" ;;
esac
cp "$ROOT/distribution/offline-bundle/install-offline.sh" "$ROOT/distribution/offline-bundle/install-offline.ps1" \
   "$ROOT/distribution/offline-bundle/new-app.sh" "$ROOT/distribution/offline-bundle/new-app.ps1" \
   "$ROOT/distribution/offline-bundle/README.md" "$BUNDLE/"
mkdir -p "$BUNDLE/docs"
cp -r "$ROOT/docs/conventions" "$ROOT/docs/adr" "$ROOT/docs/AGENTS.md" "$BUNDLE/docs/"
printf '%s\n' "$VERSION" > "$BUNDLE/COZY_VERSION"

(cd "$OUT" && rm -f "cozy-offline-bundle-$VERSION.zip" && zip -qr "cozy-offline-bundle-$VERSION.zip" "cozy-offline-bundle-$VERSION")
echo "[cozy] 완료: $OUT/cozy-offline-bundle-$VERSION.zip ($(du -sh "$OUT/cozy-offline-bundle-$VERSION.zip" | cut -f1))"
