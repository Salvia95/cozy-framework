#!/usr/bin/env bash
# app-image의 기동 시간·힙·RSS·크기를 잰다 (Linux, docs/conventions/performance.md).
#
#   distribution/perf/measure-startup.sh <app-image 폴더> <appId> [횟수]
#
# AOT 캐시 있음/없음을 각각 한 번 예열한 뒤 [횟수]번 잰다. 없음은 런처 cfg에서 -XX:AOTCache 줄만 잠시 뺀다.
# 출력: "<aot|noaot> <기동 초> <힙 사용 KB> <RSS KB>"와 마지막 줄의 크기. jcmd(JDK)가 PATH에 있어야 한다.
set -uo pipefail
IMG="$1"; ID="$2"; RUNS="${3:-5}"
CFG="$IMG/lib/app/$ID.cfg"; BIN="$IMG/bin/$ID"
WORK="$(mktemp -d)"
cp "$CFG" "$WORK/cfg.orig"
trap 'cp "$WORK/cfg.orig" "$CFG"; rm -rf "$WORK"' EXIT

run_once() {
  local log="$WORK/run.log"
  COZY_DATA_DIR="$WORK/data-$RANDOM" "$BIN" --cozy.runtime.open-browser=false --cozy.runtime.tray=false > "$log" 2>&1 &
  local pid=$!
  for _ in $(seq 1 300); do grep -q 'Started .* in ' "$log" && break; sleep 0.1; done
  local seconds; seconds=$(grep -o 'process running for [0-9.]*' "$log" | grep -o '[0-9.]*$')
  sleep 2
  local heap; heap=$(jcmd "$pid" GC.heap_info 2>/dev/null | grep -oE 'used [0-9]+K' | head -1 | grep -oE '[0-9]+')
  local rss; rss=$(awk '/VmRSS/{print $2}' "/proc/$pid/status" 2>/dev/null)
  kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  echo "$seconds $heap $rss"
}

for mode in aot noaot; do
  if [ "$mode" = noaot ]; then grep -v 'AOTCache' "$WORK/cfg.orig" > "$CFG"; else cp "$WORK/cfg.orig" "$CFG"; fi
  run_once > /dev/null
  for _ in $(seq 1 "$RUNS"); do echo "$mode $(run_once)"; done
done
echo "image $(du -sm "$IMG" | cut -f1)MB aot=$(du -sm "$IMG/lib/app/$ID.aot" 2>/dev/null | cut -f1)MB"
