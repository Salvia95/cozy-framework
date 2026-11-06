# 새 Cozy 앱 만들기 (오프라인). 물어보는 값에 답하거나 -D로 미리 준다.
#
#   powershell -ExecutionPolicy Bypass -File .\new-app.ps1
#   powershell -ExecutionPolicy Bypass -File .\new-app.ps1 "-DgroupId=com.example" "-DartifactId=voc-assistant" `
#     "-Dpackage=com.example.voc" "-DappName=VOC 도우미" "-Dpersistence=true" "-Devents=false" "-DinteractiveMode=false"
#
# PowerShell에서는 -D 인수를 따옴표로 감싼다. 감싸지 않으면 점(.)에서 인수가 나뉜다.
# 옵션: appId(기본 artifactId), appName(한글 가능), persistence(SQLite, 기본 true), events(이벤트, persistence 필요, 기본 false)
$ErrorActionPreference = 'Stop'
$Version = (Get-Content -Raw (Join-Path $PSScriptRoot 'COZY_VERSION')).Trim()
& mvn.cmd -o -ntp 'org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate' '-DarchetypeCatalog=local' `
  '-DarchetypeGroupId=io.salvia95.cozy' '-DarchetypeArtifactId=cozy-archetype' "-DarchetypeVersion=$Version" `
  '-Dversion=1.0.0-SNAPSHOT' @args
exit $LASTEXITCODE
