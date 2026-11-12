# Cozy 명령 (오프라인 번들, Windows). 형식: cozy <동작> [인자...]
#
#   .\cozy.cmd install [설치 폴더]   번들을 이 PC에 설치한다. 번들 폴더에서 실행한다 (기본: %USERPROFILE%\cozy)
#   cozy new ["-D이름=값" ...]       새 앱을 현재 폴더 아래에 만든다. 물어보는 값에 답하거나 -D로 미리 준다
#   cozy add <레시피> ...             앱 폴더에서 구조를 만든다 (module, crud, screen, fragment, event, listener, migration, dto, query, command, event-version)
#   cozy link <모듈> --uses <모듈>    모듈 의존 선언. 그 밖에 cozy describe | check | recipes | enable events | sync agents | set arg (자세히: cozy recipes, AGENTS.md)
#   cozy version                     프레임워크 버전
#   cozy help                        이 도움말
#
# 설치하면 <설치 폴더>\bin에 cozy.cmd와 mvn.cmd가 생긴다. PATH에 <설치 폴더>\bin을 더하면 어디서나 cozy와 번들의 Maven을 쓴다.
# 번들에 JDK(jdk\)가 있으면 cozy와 그 mvn은 그 JDK로 돈다. 없으면 시스템의 JDK 25(JAVA_HOME, PATH)를 쓴다.
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
# @(...)로 감싼다. if의 결과는 펼쳐지므로 인수가 하나면 배열이 아니라 문자열이 되고, $Rest[0]이 첫 글자가 된다
$Rest = @(if ($args.Count -gt 1) { $args[1..($args.Count - 1)] })

# 번들의 JDK는 이 명령(과 여기서 부르는 Maven) 안에서만 쓴다. 시스템의 JAVA_HOME과 PATH 설정은 바꾸지 않는다
$BundledJdk = Join-Path $Root 'jdk'
if (Test-Path (Join-Path $BundledJdk 'bin\java.exe')) {
  $env:JAVA_HOME = $BundledJdk
  $env:Path = (Join-Path $BundledJdk 'bin') + ';' + $env:Path
}

function Show-Usage {
  Get-Content -Encoding UTF8 $PSCommandPath | Select-Object -First 16 | ForEach-Object { $_ -replace '^# ?', '' }
}

function Assert-Java {
  if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw '[cozy] JDK 25가 필요합니다. JAVA_HOME과 PATH를 확인하세요.' }
  # java는 설정을 stderr에 쓴다. Windows PowerShell 5.1은 Stop일 때 2>&1로 받은 stderr를 오류로 바꿔 멈추므로 잠시 Continue로 둔다
  $previous = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { $settings = @(& java -XshowSettings:properties -version 2>&1 | ForEach-Object { "$_" }) }
  finally { $ErrorActionPreference = $previous }
  $match = $settings | Select-String 'java.specification.version = (\d+)' | Select-Object -First 1
  $major = if ($match) { [int]$match.Matches[0].Groups[1].Value } else { 0 }
  if ($major -lt 25) { throw "[cozy] JDK 25가 필요합니다 (지금: $major)" }
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

  # JDK: 이전 번들의 JDK가 남지 않게 지우고, 이 번들에 있으면 복사한다
  $jdkTarget = Join-Path $Destination 'jdk'
  if ($BundledJdk -ne $jdkTarget) {
    if (Test-Path $jdkTarget) { Remove-Item -Recurse -Force $jdkTarget }
    if (Test-Path $BundledJdk) { Copy-Item -Recurse $BundledJdk $jdkTarget }
  }
  # bin\mvn.cmd: 설치 폴더의 Maven을 부른다. 설치 폴더에 JDK가 있으면 그 JDK로 돈다
  $mvnCmd = @(
    '@echo off',
    'rem Runs the Maven in this install folder, on the JDK in jdk\ when there is one (written by cozy install).',
    'setlocal',
    'for %%i in ("%~dp0..") do set "COZY_HOME=%%~fi"',
    'if exist "%COZY_HOME%\jdk\bin\java.exe" set "JAVA_HOME=%COZY_HOME%\jdk"',
    ('call "%COZY_HOME%\' + (Split-Path $mavenDir -Leaf) + '\bin\mvn.cmd" %*'),
    'exit /b %ERRORLEVEL%'
  ) -join "`r`n"
  [System.IO.File]::WriteAllText((Join-Path $bin 'mvn.cmd'), $mvnCmd + "`r`n", (New-Object System.Text.ASCIIEncoding))

  # 저장소는 Maven 폴더 옆이다. 절대 경로를 쓰지 않으므로 설치 폴더를 옮기거나 MSI가 다른 곳에 깔아도 맞다
  $settings = @"
<?xml version="1.0" encoding="UTF-8"?>
<!-- Cozy 오프라인 번들 ${Version}: 번들 저장소만 쓰고 네트워크에 나가지 않는다 -->
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>`${maven.home}/../repository</localRepository>
  <offline>true</offline>
</settings>
"@
  [System.IO.File]::WriteAllText((Join-Path $mavenDir 'conf\settings.xml'), $settings, (New-Object System.Text.UTF8Encoding($false)))

  Write-Host "[cozy] 설치했습니다: $Destination"
  if (Test-Path $jdkTarget) {
    Write-Host "  JDK:         $jdkTarget (cozy와 mvn이 쓴다. 시스템 설정은 바꾸지 않았다)"
  }
  Write-Host "  PATH에 추가: `$env:Path = '$bin;' + `$env:Path"
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

# describe, check, recipes, add, link, enable, sync, set은 cozy-cli가 한다. 번들 저장소의 cozy-ui jar를 읽는다
function Invoke-CozyCli([object[]]$CliArgs) {
  $jar = Join-Path $Root 'lib\cli\cozy-cli.jar'
  if (-not (Test-Path $jar)) {
    Write-Host "[cozy] ${jar}가 없습니다. 번들을 다시 설치하세요."
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
  { $_ -in @('describe', 'check', 'recipes', 'add', 'link', 'enable', 'sync', 'set') } { Invoke-CozyCli (@($Action) + $Rest) }
  { $_ -in @('version', '--version', '-v') } { Write-Output $Version }
  { $_ -in @('help', '--help', '-h') } { Show-Usage }
  default {
    Write-Host "[cozy] 알 수 없는 동작: $Action"
    Show-Usage
    exit 2
  }
}
