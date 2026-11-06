#!/usr/bin/env bash
# 릴리스 빌드용 Maven 3.10.0 설치 (ADR-0028). SHA-512를 확인하고 bin 폴더 경로를 출력한다.
#
#   distribution/release/install-maven.sh <설치 폴더>     → <설치 폴더>/apache-maven-3.10.0/bin
#
# 받은 zip은 <설치 폴더>/apache-maven-3.10.0-bin.zip에 남는다. build-bundle.sh의 두 번째 인자로 주면 다시 받지 않는다.
set -euo pipefail

DEST="${1:?설치 폴더가 필요합니다}"
URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip"
# build-bundle.sh의 MAVEN_SHA512와 같은 값 (Maven Central과 archive.apache.org의 .sha512, 2026-11-06 확인)
SHA512="22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337"

mkdir -p "$DEST"
ZIP="$DEST/apache-maven-3.10.0-bin.zip"
curl -fsSL -o "$ZIP" "$URL"
if [ "$(sha512sum "$ZIP" | cut -d' ' -f1)" != "$SHA512" ]; then
  echo "[cozy] $ZIP의 SHA-512가 맞지 않습니다" >&2
  exit 1
fi
rm -rf "$DEST/apache-maven-3.10.0"
(cd "$DEST" && unzip -q "$ZIP")
echo "$DEST/apache-maven-3.10.0/bin"
