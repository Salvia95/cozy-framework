#!/usr/bin/env bash
# 오프라인 번들 수용 기준 검증 (Linux). 네트워크를 끊은 상태에서 번들만으로
# 설치 → 아키타입 생성(옵션 3조합) → mvn -o verify → mvn -o -Pdist package가 되는지 본다.
# 앱마다 에이전트 문서를 확인하고, basic 앱에는 cozy sync agents, persistence 앱에는 cozy enable events, 이벤트 앱에는 cozy add·link·2단계 명령으로 구조를 더한 뒤 verify한다.
#
#   distribution/offline-bundle/verify-offline.sh <번들 zip> [작업 폴더]
#
# JDK를 담은 번들(add-jdk.sh의 linux-x64)이면 시스템 JDK 자리에 부르면 실패하는 java를 둔다. 설치, 생성, 빌드, 패키징이
# 모두 번들의 JDK로 돌아야 통과한다. windows-x64 번들은 Linux에서 실행할 수 없어 Windows에서 확인한다.
#
# unshare --net으로 네트워크 장치가 루프백뿐인 이름공간에서 돌린다. 랜선을 뽑은 PC처럼 루프백(127.0.0.1)만 켠다
# (앱은 루프백에서 뜨고, 패키징의 AOT 훈련 실행이 루프백으로 요청한다). HOME과 Maven의 user.home도 빈 폴더로 바꿔
# 기존 ~/.m2와 설정을 쓰지 않는다. Windows는 docs/conventions/windows-verification.md의 아키타입과 오프라인 번들 절차를 따른다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ZIP="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
WORK="${2:-$(dirname "$ZIP")/offline-check}"
if [ "${COZY_OFFLINE_INNER:-}" != "1" ]; then
  rm -rf "$WORK"
  mkdir -p "$WORK/home"
fi
# 네트워크를 끊은 쪽은 작업 폴더로 cd한 뒤에도 같은 경로를 쓴다
WORK="$(cd "$WORK" && pwd)"

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
BUNDLE="${BUNDLE%/}"

# 번들의 JDK: 있으면 시스템 JDK를 쓰지 못하게 막고, 라이선스 목록의 JDK 줄을 jdk.env로 다시 만든다
JAVA=java
JDK_OPTIONS=()
if [ -d "$BUNDLE/jdk" ]; then
  # shellcheck source=jdk.env
  . "$ROOT/distribution/offline-bundle/jdk.env"
  case "$BUNDLE" in
    *-linux-x64) JDK_FILE="$JDK_LINUX_X64" ;;
    *)
      echo "[cozy] 이 JDK는 Linux에서 실행할 수 없습니다: $(basename "$BUNDLE"). windows-x64 번들은 Windows에서 확인합니다" >&2
      exit 2
      ;;
  esac
  test ! -e "$BUNDLE/jdk/lib/src.zip"
  JAVA="$BUNDLE/jdk/bin/java"
  JDK_OPTIONS=(--jdk-distribution="$JDK_FILE" --jdk-sources="$JDK_SOURCES")
  mkdir -p "$WORK/system-jdk/bin"
  printf '#!/bin/sh\necho "[cozy] 번들의 JDK 대신 시스템 JDK를 불렀습니다: java $*" >&2\nexit 97\n' > "$WORK/system-jdk/bin/java"
  chmod +x "$WORK/system-jdk/bin/java"
  export PATH="$WORK/system-jdk/bin:$PATH" JAVA_HOME="$WORK/system-jdk"
  echo "[cozy] 번들 JDK: $(grep '^IMPLEMENTOR_VERSION=' "$BUNDLE/jdk/release"). 시스템 JDK는 막았다"
fi

# 라이선스 목록: 번들의 저장소만으로 다시 만들어 담긴 목록과 같은지 본다.
# 네트워크가 없으므로 약한 카피레프트 jar의 소스 jar가 빠져 있으면 여기서 실패한다
VERSION="$(cat "$BUNDLE/COZY_VERSION")"
MAVEN_FILE="$(basename "$(ls "$BUNDLE"/apache-maven-*-bin.zip | head -1)")"
test -f "$BUNDLE/LICENSE" && test -f "$BUNDLE/NOTICE"
"$JAVA" -cp "$BUNDLE/repository/io/salvia95/cozy/cozy-starter-runtime/$VERSION/cozy-starter-runtime-$VERSION.jar" \
  io.salvia95.cozy.runtime.internal.dist.BundleLicenses --repository="$BUNDLE/repository" \
  --overrides="$BUNDLE/licenses/license-overrides.txt" --output="$WORK/THIRD-PARTY.check.txt" \
  --title="Cozy offline bundle $VERSION" --maven-distribution="$MAVEN_FILE" ${JDK_OPTIONS[@]+"${JDK_OPTIONS[@]}"}
diff -u "$BUNDLE/licenses/THIRD-PARTY.txt" "$WORK/THIRD-PARTY.check.txt"
echo "[cozy] 라이선스 목록 확인: $(grep -c 'sources.jar\]' "$BUNDLE/licenses/THIRD-PARTY.txt")개 jar의 소스 jar 포함"

"$BUNDLE/cozy" version | grep -qx "$VERSION"
"$BUNDLE/cozy" install "$WORK/cozy"
# PATH에는 설치 폴더의 bin 하나만 더한다. bin/mvn이 설치한 Maven을 (JDK가 있으면 그 JDK로) 부른다
export PATH="$WORK/cozy/bin:$PATH"
test "$(cozy version)" = "$VERSION"
test "$(command -v mvn)" = "$WORK/cozy/bin/mvn"
if [ -d "$BUNDLE/jdk" ]; then
  test -x "$WORK/cozy/jdk/bin/java"
  out="$(mvn -v)"
  grep -q "runtime: $WORK/cozy/jdk" <<< "$out"
  echo "[cozy] 설치한 mvn이 번들 JDK로 돈다: $WORK/cozy/jdk"
fi

# cozy 명령(cozy-cli)으로 구조를 더한다. 빈칸이 남지 않는 레시피만 쓰므로 바로 verify를 통과해야 한다
cozy_recipes() {
  (
    cd "$1"
    # 출력은 변수로 받아 확인한다 (grep -q가 파이프를 먼저 닫으면 pipefail에 걸린다)
    out="$(cozy recipes)"
    grep -q "cozy add crud" <<< "$out"
    cozy add crud task --entity Task --label "할 일" --icon checklist --field "title:text!:제목" --field dueOn:date:마감일 \
      --field done:bool:완료 --publish created
    # 하위 폴더에서도 앱을 찾는다. 같은 명령을 다시 실행하면 바뀐 것이 없다
    out="$(cd src/main && cozy add crud task --entity Task --label "할 일" --icon checklist --field "title:text!:제목" \
      --field dueOn:date:마감일 --field done:bool:완료 --publish created)"
    grep -q "바뀐 것 없음" <<< "$out"
    cozy link task --uses home
    # 2단계: spi DTO, 읽기(빈칸 SQL은 에이전트가 채운다), 채운 뒤 다시 실행, 이벤트 필드 추가
    cozy add dto task TaskSummary --field total:long:전체 --field done:long:완료
    cozy add query task summary --returns TaskSummary
    sed -i 's#<!-- cozy:slot query-sql[^>]*-->#SELECT count(*) AS total, coalesce(sum(done), 0) AS done FROM task#' \
      src/main/resources/mapper/task/TaskMapper.xml
    out="$(cozy add query task summary --returns TaskSummary)"
    grep -q "바뀐 것 없음" <<< "$out"
    cozy add event-version task TaskCreated --field dueOn:date
    test -f src/test/resources/events/TaskCreated/v2.json
    # crud에 필드 하나 더하기: 목록과 상세까지 고치고 손으로 할 일이 남지 않는다
    out="$(cozy add field task --entity Task --field note:text:비고)"
    grep -q "적용했습니다" <<< "$out"
    if grep -q "남은 일" <<< "$out"; then
      echo "$out"
      exit 1
    fi
    grep -q '<th class="w-1">비고</th>' src/main/resources/templates/task/api/list.html
    # 실패하는 명령은 종료 코드 2이고 아무것도 바꾸지 않는다
    set +e
    cozy add screen task stats --pattern details --title 통계 --nav 통계:no-such-icon > /dev/null 2>&1
    code=$?
    set -e
    test "$code" = 2
    test -z "$(find src -newer pom.xml -name '*Stats*')"
    cozy check
    out="$(cozy describe)"
    grep -q "^모듈 task " <<< "$out"
    echo "[cozy] cozy add·link 통과: $1"
  )
}

# 에이전트 문서가 없는 앱(옛 판으로 만든 앱)에 cozy sync agents로 넣는다. 다시 실행하면 바뀐 것이 없다
cozy_sync_agents() {
  (
    cd "$1"
    cp AGENTS.md ../AGENTS.expected
    rm -rf AGENTS.md .agents
    out="$(cozy sync agents)"
    grep -q "적용했습니다" <<< "$out"
    cmp AGENTS.md ../AGENTS.expected
    test -f .agents/skills/cozy-helper/SKILL.md
    test -f .agents/skills/cozy-helper/references/sync-agents/PLAYBOOK.md
    out="$(cozy sync agents)"
    grep -q "바뀐 것 없음" <<< "$out"
    echo "[cozy] cozy sync agents 통과: $1"
  )
}

cozy_enable_events() {
  (
    cd "$1"
    out="$(cozy enable events)"
    grep -q "적용했습니다" <<< "$out"
    out="$(cozy enable events)"
    grep -q "바뀐 것 없음" <<< "$out"
    grep -q "<artifactId>cozy-starter-events</artifactId>" pom.xml
    echo "[cozy] cozy enable events 통과: $1"
  )
}

mkdir -p "$WORK/apps"
cd "$WORK/apps"
for combo in basic:false:false persistence:true:false events:true:true; do
  IFS=: read -r name persistence events <<< "$combo"
  cozy new -B -DinteractiveMode=false -DgroupId=com.example -DartifactId="offline-$name" \
    -Dpackage="com.example.offline.$name" -DappName="오프라인 확인" -Dpersistence="$persistence" -Devents="$events"
  # 워크벤치: 새 앱의 pom에 dev 전용 cozy-workbench가 있고, 다시 넣으면 바뀐 것이 없다
  grep -q "<artifactId>cozy-workbench</artifactId>" "offline-$name/pom.xml"
  out="$(cd "offline-$name" && cozy enable workbench)"
  grep -q "바뀐 것 없음" <<< "$out"
  # 에이전트 문서: 가벼운 AGENTS.md와 cozy-helper 스킬
  grep -q "cozy-helper" "offline-$name/AGENTS.md"
  test -f "offline-$name/.agents/skills/cozy-helper/SKILL.md"
  test -f "offline-$name/.agents/skills/cozy-helper/references/add-crud/PLAYBOOK.md"
  if [ "$name" = events ]; then
    cozy_recipes "offline-$name"
  fi
  if [ "$name" = basic ]; then
    cozy_sync_agents "offline-$name"
  fi
  if [ "$name" = persistence ]; then
    cozy_enable_events "offline-$name"
  fi
  (cd "offline-$name" && mvn -B -ntp -o verify && mvn -B -ntp -o -Pdist package)
  test -d "offline-$name/target/dist/image/offline-$name"
  echo "[cozy] 통과: $name (persistence=$persistence, events=$events)"
done
echo "[cozy] 오프라인 수용 기준 통과: 번들만으로 생성·워크벤치·에이전트 문서·cozy add·enable·sync·verify·-Pdist package$([ -d "$BUNDLE/jdk" ] && echo " (번들 JDK)")"
