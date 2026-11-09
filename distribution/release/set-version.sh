#!/usr/bin/env bash
# 프레임워크 버전을 바꾼다. 모든 cozy 모듈이 같은 버전을 쓴다.
#
#   distribution/release/set-version.sh 1.0.0            # 릴리스
#   distribution/release/set-version.sh 1.1.0-SNAPSHOT   # 릴리스 다음 개발 버전
#
# 바꾸는 곳: 레포의 pom.xml(아키타입 템플릿과 invoker 픽스처 제외. 둘 다 빌드 때 @project.version@으로 채워지고,
# 픽스처 자신의 <version>은 앱 버전이라 프레임워크 버전과 우연히 같아도 바꾸지 않는다. check-version.sh와 같은 범위),
# 문서의 -DarchetypeVersion= 예시, 오프라인 번들 README의 zip 이름. 생성 앱의 기본 버전(cozy new의 -Dversion)은 앱 버전이라 그대로 둔다.
set -euo pipefail

NEW="${1:?새 버전이 필요합니다 (예: 1.0.0)}"
if ! [[ "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
  echo "[cozy] 버전 형식은 X.Y.Z 또는 X.Y.Z-SNAPSHOT입니다: $NEW" >&2
  exit 1
fi
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
OLD="$(sed -n 's|.*<cozy.version>\(.*\)</cozy.version>.*|\1|p' cozy-parent/pom.xml | head -1)"
if [ "$OLD" = "$NEW" ]; then
  echo "[cozy] 이미 $NEW입니다"
  exit 0
fi
OLD_RE="${OLD//./\\.}"

FIXTURES='/(src/it|archetype-resources)/|^samples/violations/checks/'
mapfile -t POMS < <(git ls-files '*pom.xml' | grep -vE "$FIXTURES")
for pom in "${POMS[@]}"; do
  sed -i "s|<version>$OLD_RE</version>|<version>$NEW</version>|g; s|<cozy.version>$OLD_RE</cozy.version>|<cozy.version>$NEW</cozy.version>|g" "$pom"
done
mapfile -t DOCS < <(git ls-files '*.md')
for doc in "${DOCS[@]}"; do
  sed -i "s|-DarchetypeVersion=$OLD_RE|-DarchetypeVersion=$NEW|g" "$doc"
done
sed -i "s|cozy-offline-bundle-$OLD_RE|cozy-offline-bundle-$NEW|g" distribution/offline-bundle/README.md

# 남은 곳이 있으면 실패한다 (cozy 모듈의 버전이 섞이면 리액터가 깨진다)
if git grep -n -F ">$OLD<" -- "${POMS[@]}"; then
  echo "[cozy] 위 POM에 이전 버전 $OLD이 남아 있습니다" >&2
  exit 1
fi
# 픽스처 POM이 바뀌었으면 실패한다
if git diff --name-only -- '*pom.xml' | grep -E "$FIXTURES"; then
  echo "[cozy] 위 픽스처 POM이 바뀌었습니다. 픽스처는 @project.version@을 쓰므로 바꾸지 않습니다" >&2
  exit 1
fi
echo "[cozy] $OLD → $NEW (POM ${#POMS[@]}개). git diff로 확인한 뒤 커밋하세요"
