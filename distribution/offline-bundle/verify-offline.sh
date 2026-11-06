#!/usr/bin/env bash
# 오프라인 번들 수용 기준 검증 (PRD §9.7, Linux). 네트워크를 끊은 상태에서 번들만으로
# 설치 → 아키타입 생성(옵션 3조합) → mvn -o verify → mvn -o -Pdist package가 되는지 본다.
#
#   distribution/offline-bundle/verify-offline.sh <번들 zip>
#
# unshare --net으로 네트워크 장치가 루프백뿐인 이름공간에서 돌린다. 랜선을 뽑은 PC처럼 루프백(127.0.0.1)만 켠다
# (앱은 루프백에서 뜨고, 패키징의 AOT 훈련 실행이 루프백으로 요청한다). HOME과 Maven의 user.home도 빈 폴더로 바꿔
# 기존 ~/.m2와 설정을 쓰지 않는다. Windows는 docs/conventions/windows-verification.md의 M7 절차를 따른다.
set -euo pipefail

ZIP="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
WORK="${2:-$(dirname "$ZIP")/offline-check}"
if [ "${COZY_OFFLINE_INNER:-}" != "1" ]; then
  rm -rf "$WORK"
  mkdir -p "$WORK/home"
fi

if [ "${COZY_OFFLINE_INNER:-}" != "1" ]; then
  # 네트워크를 끊은 이름공간에서 자신을 다시 실행한다
  exec unshare --net --map-root-user env COZY_OFFLINE_INNER=1 HOME="$WORK/home" PATH="$PATH" \
    JAVA_HOME="${JAVA_HOME:-}" MAVEN_OPTS="-Duser.home=$WORK/home" bash "$0" "$ZIP" "$WORK"
fi

ip link set lo up
echo "[cozy] 네트워크 장치: $(ip -o link 2>/dev/null | awk -F': ' '{print $2}' | tr '\n' ' ')"
if curl -s -m 5 -o /dev/null https://repo.maven.apache.org/ 2>/dev/null; then
  echo "[cozy] 네트워크가 끊기지 않았습니다" >&2
  exit 1
fi

cd "$WORK"
unzip -q "$ZIP"
BUNDLE="$(ls -d "$WORK"/cozy-offline-bundle-*/ | head -1)"
bash "$BUNDLE/install-offline.sh" "$WORK/cozy"
MAVEN_BIN="$(ls -d "$WORK"/cozy/apache-maven-*/bin | head -1)"
export PATH="$MAVEN_BIN:$PATH"

mkdir -p "$WORK/apps"
cd "$WORK/apps"
for combo in basic:false:false persistence:true:false events:true:true; do
  IFS=: read -r name persistence events <<< "$combo"
  "$WORK/cozy/new-app.sh" -B -DinteractiveMode=false -DgroupId=com.example -DartifactId="offline-$name" \
    -Dpackage="com.example.offline.$name" -DappName="오프라인 확인" -Dpersistence="$persistence" -Devents="$events"
  (cd "offline-$name" && mvn -B -ntp -o verify && mvn -B -ntp -o -Pdist package)
  test -d "offline-$name/target/dist/image/offline-$name"
  echo "[cozy] 통과: $name (persistence=$persistence, events=$events)"
done
echo "[cozy] 오프라인 수용 기준 통과: 번들만으로 생성·verify·-Pdist package"
