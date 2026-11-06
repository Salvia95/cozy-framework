#!/usr/bin/env bash
# 프레임워크 버전을 바꾼다 (ADR-0028). 모든 cozy 모듈이 같은 버전을 쓴다(ADR-0016).
#
#   distribution/release/set-version.sh 1.0.0            # 릴리스
#   distribution/release/set-version.sh 1.1.0-SNAPSHOT   # 릴리스 다음 개발 버전
#
# 바꾸는 곳: 레포의 pom.xml(아키타입 템플릿 제외. 템플릿은 빌드 때 @project.version@으로 채워진다),
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

mapfile -t POMS < <(git ls-files '*pom.xml' | grep -v '/archetype-resources/')
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
echo "[cozy] $OLD → $NEW (POM ${#POMS[@]}개). git diff로 확인한 뒤 커밋하세요"
