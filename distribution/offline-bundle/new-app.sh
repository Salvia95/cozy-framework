#!/usr/bin/env bash
# 새 Cozy 앱 만들기 (오프라인). 물어보는 값에 답하거나 -D로 미리 준다.
#
#   ./new-app.sh                                                     # 대화형
#   ./new-app.sh -DgroupId=com.example -DartifactId=voc-assistant -Dpackage=com.example.voc \
#                -DappName="VOC 도우미" -Dpersistence=true -Devents=false -DinteractiveMode=false
#
# 옵션: appId(기본 artifactId), appName(한글 가능), persistence(SQLite, 기본 true), events(이벤트, persistence 필요, 기본 false)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="$(cat "$HERE/COZY_VERSION")"
exec mvn -o -ntp org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate -DarchetypeCatalog=local \
  -DarchetypeGroupId=io.salvia95.cozy -DarchetypeArtifactId=cozy-archetype -DarchetypeVersion="$VERSION" \
  -Dversion=1.0.0-SNAPSHOT "$@"
