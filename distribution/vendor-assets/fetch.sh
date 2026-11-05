#!/usr/bin/env bash
# 인터넷 PC에서 벤더 자산을 내려받아 cozy-ui에 넣고 vendor.lock을 다시 쓴다 (PRD §6.4).
# - npm tarball은 레지스트리가 공개하는 sha512(dist.integrity)로 검증한다.
# - 결과 파일의 SHA-256을 cozy-ui/vendor.lock에 기록한다. 빌드(VendorAssetsTest)가 이 값과 파일을 대조한다.
# 필요: bash, curl, tar, python3, sha256sum, openssl
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CONF="$ROOT/distribution/vendor-assets/assets.conf"
TARGET="$ROOT/cozy-ui/src/main/resources/META-INF/resources/cozy/vendor"
LOCK="$ROOT/cozy-ui/vendor.lock"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

rm -rf "$TARGET"
mkdir -p "$TARGET"
{
  echo "# 생성: distribution/vendor-assets/fetch.sh — 직접 고치지 않는다"
  echo "# 경로(cozy/vendor 기준)  SHA-256"
} > "$LOCK.tmp"

grep -vE '^\s*(#|$)' "$CONF" | while read -r name version package rest; do
  echo "== $name $version ($package)"
  meta="$(curl -fsSL "https://registry.npmjs.org/${package/\//%2F}/$version")"
  tarball="$(python3 -I -c 'import sys,json;print(json.load(sys.stdin)["dist"]["tarball"])' <<<"$meta")"
  integrity="$(python3 -I -c 'import sys,json;print(json.load(sys.stdin)["dist"]["integrity"])' <<<"$meta")"
  tgz="$WORK/$name.tgz"
  curl -fsSL -o "$tgz" "$tarball"
  actual="sha512-$(openssl dgst -sha512 -binary "$tgz" | openssl base64 -A)"
  if [[ "$actual" != "$integrity" ]]; then
    echo "무결성 불일치: $tarball" >&2; exit 1
  fi
  mkdir -p "$WORK/$name"
  tar xzf "$tgz" -C "$WORK/$name"
  dest="$TARGET/$name/$version"
  for mapping in $rest; do
    src="${mapping%%=>*}"; dst="${mapping##*=>}"
    mkdir -p "$(dirname "$dest/$dst")"
    if [[ "$src" == https://* ]]; then
      curl -fsSL -o "$dest/$dst" "$src"
    else
      cp "$WORK/$name/package/$src" "$dest/$dst"
    fi
  done
  (cd "$TARGET" && find "$name/$version" -type f | sort | while read -r f; do
     printf '%s  %s\n' "$f" "$(sha256sum "$f" | cut -d' ' -f1)"; done) >> "$LOCK.tmp"
done

mv "$LOCK.tmp" "$LOCK"
echo "완료: $LOCK"
