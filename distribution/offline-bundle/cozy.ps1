# Cozy 명령 (오프라인 번들, PRD §9.7, Windows). 형식: cozy <동작> [인자...]
#
#   .\cozy.cmd install [설치 폴더]   번들을 이 PC에 설치한다. 번들 폴더에서 실행한다 (기본: %USERPROFILE%\cozy)
#   cozy new ["-D이름=값" ...]       새 앱을 현재 폴더 아래에 만든다. 물어보는 값에 답하거나 -D로 미리 준다
#   cozy add <레시피> ...             앱 폴더에서 구조를 만든다 (module, crud, screen, fragment, event, listener, migration, dto, query, command)
#   cozy link <모듈> --uses <모듈>    모듈 의존 선언. 그 밖에 cozy describe | check | recipes | enable events (자세히: cozy recipes, AGENTS.md)
#   cozy version                     프레임워크 버전
#   cozy help                        이 도움말
#
# 설치하면 <설치 폴더>\bin\cozy.cmd가 생긴다. PATH에 <설치 폴더>\bin을 더하면 어디서나 cozy를 쓴다.
# PowerShell에서는 -D 인수를 따옴표로 감싼다. 감싸지 않으면 점(.)에서 인수가 나뉜다.
#   cozy new "-DgroupId=com.example" "-DartifactId=voc-assistant" "-Dpackage=com.example.voc" `
#            "-DappName=VOC 도우미" "-Dpersistence=true" "-Devents=false" "-DinteractiveMode=false"
# new의 옵션: groupId, artifactId, package, appId(기본 artifactId), appName(한글 가능), persistence(기본 true),
# events(기본 false, persistence 필요).
$ErrorActionPreference = 'Stop'

# 번들 폴더는 cozy.ps1 옆에 COZY_VERSION이 있고, 설치 폴더는 lib\cozy.ps1의 한 단계 위에 있다
$Root = if (Test-Path (Join-Path $PSScriptRoot 'COZY_VERSION')) { $PSScriptRoot } else { Split-Path $PSScriptRoot -Parent }
$VersionFile = Join-Path $Root 'COZY_VERSION'
$Version = if (Test-Path $VersionFile) { (Get-Content -Raw $VersionFile).Trim() } else { 'unknown' }
$Action = if ($args.Count -gt 0) { [string]$args[0] } else { 'help' }
$Rest = if ($args.Count -gt 1) { @($args[1..($args.Count - 1)]) } else { @() }

function Show-Usage {
  Get-Content -Encoding UTF8 $PSCommandPath | Select-Object -First 15 | ForEach-Object { $_ -replace '^# ?', '' }
}

function Assert-Java {
  if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw '[cozy] JDK 25가 필요합니다. JAVA_HOME과 PATH를 확인하세요.' }
  $major = (& java -XshowSettings:properties -version 2>&1 | Select-String 'java.specification.version = (\d+)').Matches[0].Groups[1].Value
  if ([int]$major -lt 25) { throw "[cozy] JDK 25가 필요합니다 (지금: $major)" }
}

function Install-Cozy([string]$Destination) {
  if (-not $Destination) { $Destination = Join-Path $env:USERPROFILE 'cozy' }
  $zip = Get-ChildItem -Path $Root -Filter 'apache-maven-*-bin.zip' -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $zip -or -not (Test-Path (Join-Path $Root 'repository'))) {
    throw '[cozy] install은 번들 폴더(repository\와 apache-maven-*-bin.zip이 있는 곳)의 cozy.cmd로 실행합니다.'
  }
  Assert-Java
  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  $Destination = (Resolve-Path $Destination).Path
  $mavenDir = Join-Path $Destination ($zip.BaseName -replace '-bin$', '')
  if (Test-Path $mavenDir) { Remove-Item -Recurse -Force $mavenDir }
  Expand-Archive -Path $zip.FullName -DestinationPath $Destination

  $repo = Join-Path $Destination 'repository'
  if ((Resolve-Path (Join-Path $Root 'repository')).Path -ne $repo) {
    if (Test-Path $repo) { Remove-Item -Recurse -Force $repo }
    Copy-Item -Recurse (Join-Path $Root 'repository') $repo
  }
  foreach ($dir in @('docs', 'licenses', 'bin', 'lib')) {
    $target = Join-Path $Destination $dir
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
  }
  Copy-Item -Recurse (Join-Path $Root 'docs'), (Join-Path $Root 'licenses') $Destination
  Copy-Item (Join-Path $Root 'LICENSE'), (Join-Path $Root 'NOTICE'), $VersionFile $Destination -Force
  $bin = New-Item -ItemType Directory -Force -Path (Join-Path $Destination 'bin')
  $lib = New-Item -ItemType Directory -Force -Path (Join-Path $Destination 'lib')
  Copy-Item (Join-Path $Root 'cozy.cmd'), (Join-Path $Root 'cozy') $bin
  Copy-Item (Join-Path $Root 'cozy.ps1') $lib
  Copy-Item -Recurse (Join-Path $Root 'lib\cli') $lib

  $settings = @"
<?xml version="1.0" encoding="UTF-8"?>
<!-- Cozy 오프라인 번들 ${Version}: 번들 저장소만 쓰고 네트워크에 나가지 않는다 -->
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>$repo</localRepository>
  <offline>true</offline>
</settings>
"@
  [System.IO.File]::WriteAllText((Join-Path $mavenDir 'conf\settings.xml'), $settings, (New-Object System.Text.UTF8Encoding($false)))

  Write-Host "[cozy] 설치했습니다: $Destination"
  Write-Host "  PATH에 추가: `$env:Path = '$bin;$mavenDir\bin;' + `$env:Path"
  Write-Host "  새 앱:       cozy new"
}

function New-CozyApp([object[]]$MvnArgs) {
  $mavenBin = Get-ChildItem -Path $Root -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue | Select-Object -First 1
  $mvn = if ($mavenBin) { Join-Path $mavenBin.FullName 'bin\mvn.cmd' } else { $null }
  if (-not $mvn -or -not (Test-Path $mvn)) {
    throw '[cozy] 설치한 폴더의 cozy로 실행합니다. 번들 폴더라면 먼저 .\cozy.cmd install을 실행하세요.'
  }
  Assert-Java
  # 설치한 Maven은 conf\settings.xml이 번들 저장소를 가리키고 항상 오프라인이다
  & $mvn -o -ntp 'org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate' '-DarchetypeCatalog=local' `
    '-DarchetypeGroupId=io.salvia95.cozy' '-DarchetypeArtifactId=cozy-archetype' "-DarchetypeVersion=$Version" `
    '-Dversion=1.0.0-SNAPSHOT' @MvnArgs
  exit $LASTEXITCODE
}

# describe, check, recipes, add, link, enable은 cozy-cli가 한다 (ADR-0032 §5). 번들 저장소의 cozy-ui jar를 읽는다
function Invoke-CozyCli([object[]]$CliArgs) {
  $jar = Join-Path $Root 'lib\cli\cozy-cli.jar'
  if (-not (Test-Path $jar)) {
    Write-Host "[cozy] $jar가 없습니다. 번들을 다시 설치하세요."
    exit 2
  }
  Assert-Java
  if (-not $env:COZY_REPOSITORY) { $env:COZY_REPOSITORY = Join-Path $Root 'repository' }
  & java -jar $jar @CliArgs
  exit $LASTEXITCODE
}

switch ($Action) {
  'install' { Install-Cozy ($(if ($Rest.Count -gt 0) { [string]$Rest[0] } else { $null })) }
  'new' { New-CozyApp $Rest }
  { $_ -in @('describe', 'check', 'recipes', 'add', 'link', 'enable') } { Invoke-CozyCli (@($Action) + $Rest) }
  { $_ -in @('version', '--version', '-v') } { Write-Output $Version }
  { $_ -in @('help', '--help', '-h') } { Show-Usage }
  default {
    Write-Host "[cozy] 알 수 없는 동작: $Action"
    Show-Usage
    exit 2
  }
}
