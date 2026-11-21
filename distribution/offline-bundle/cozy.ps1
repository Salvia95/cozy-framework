# Cozy command (offline bundle, Windows). Usage: cozy <action> [args...]
#
#   .\cozy.cmd install [folder]        Install the bundle on this PC. Run it in the bundle folder (default: %USERPROFILE%\cozy)
#   cozy new ["-D<name>=<value>" ...]   Create a new app under the current folder. Answer the prompts or pass -D values
#   cozy version                       Print the framework version
#   cozy help                          Print this help, then the app commands of cozy-cli
#
# Installing puts cozy.cmd and mvn.cmd in <folder>\bin. Add <folder>\bin to PATH to use cozy and the bundled Maven anywhere.
# With a bundled JDK (jdk\), cozy and its mvn run on it. Otherwise they use the system JDK 25 (JAVA_HOME, PATH).
# In PowerShell, quote each -D argument. Without quotes PowerShell splits it at the dots.
#   cozy new "-DgroupId=com.example" "-DartifactId=voc-assistant" "-Dpackage=com.example.voc" `
#            "-DappName=VOC 도우미" "-Dpersistence=true" "-Devents=false" "-DinteractiveMode=false"
# Options of new: groupId, artifactId, package, appId (default: artifactId), appName (may be Korean), persistence (default: true),
# events (default: false, needs persistence).
$ErrorActionPreference = 'Stop'

# 번들 폴더는 cozy.ps1 옆에 COZY_VERSION이 있고, 설치 폴더는 lib\cozy.ps1의 한 단계 위에 있다
$Root = if (Test-Path (Join-Path $PSScriptRoot 'COZY_VERSION')) { $PSScriptRoot } else { Split-Path $PSScriptRoot -Parent }
$VersionFile = Join-Path $Root 'COZY_VERSION'
$Version = if (Test-Path $VersionFile) { (Get-Content -Raw $VersionFile).Trim() } else { 'unknown' }
# Maven이 쓰는 사용자 저장소. 설치한 Maven의 settings.xml이 같은 곳(${env.LOCALAPPDATA}/Cozy/repository)을 가리킨다
$UserRepository = Join-Path $env:LOCALAPPDATA 'Cozy\repository'
$Action = if ($args.Count -gt 0) { [string]$args[0] } else { 'help' }
# @(...)로 감싼다. if의 결과는 펼쳐지므로 인수가 하나면 배열이 아니라 문자열이 되고, $Rest[0]이 첫 글자가 된다
# [string]으로 바꿔 둔다. Windows PowerShell 5.1은 -File로 받은 '-'로 시작하는 인수를 외부 명령에 넘길 때 따옴표를 빼서,
# "-DappName=VOC 도우미"가 공백에서 둘로 나뉜다
$Rest = @(if ($args.Count -gt 1) { $args[1..($args.Count - 1)] | ForEach-Object { [string]$_ } })

# 번들의 JDK는 이 명령(과 여기서 부르는 Maven) 안에서만 쓴다. 시스템의 JAVA_HOME과 PATH 설정은 바꾸지 않는다
$BundledJdk = Join-Path $Root 'jdk'
if (Test-Path (Join-Path $BundledJdk 'bin\java.exe')) {
  $env:JAVA_HOME = $BundledJdk
  $env:Path = (Join-Path $BundledJdk 'bin') + ';' + $env:Path
}

# 머리의 # 줄들, 그리고 cozy-cli를 실행할 수 있으면 그 도움말(앱 명령)
function Show-Usage {
  foreach ($line in (Get-Content -Encoding UTF8 $PSCommandPath)) {
    $text = $line.TrimStart([char]0xFEFF)
    if (-not $text.StartsWith('#')) { break }
    $text -replace '^# ?', ''
  }
  $jar = Join-Path $Root 'lib\cli\cozy-cli.jar'
  if ((Test-Path $jar) -and (Get-Command java -ErrorAction SilentlyContinue)) {
    ''
    $ErrorActionPreference = 'Continue'
    & java -jar $jar help 2>$null
    $ErrorActionPreference = 'Stop'
  }
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

# 이전 설치의 저장소에만 있는 파일(이전 판의 cozy 모듈, 그 판만 쓰는 의존성, 예전 설치가 쓴 앱)을 사용자 저장소로 옮긴다.
# 새 번들에도 있는 파일, 사용자 저장소에 이미 있는 파일, Maven의 잠금 파일은 그대로 둔다
function Move-OldOnlyFiles([string]$Old, [string]$New) {
  $moved = 0
  $prefix = $Old.TrimEnd('\') + '\'
  foreach ($file in Get-ChildItem -LiteralPath $Old -Recurse -File -Force) {
    $relative = $file.FullName.Substring($prefix.Length)
    if ($relative -like '.locks\*') { continue }
    $target = Join-Path $UserRepository $relative
    if ((Test-Path -LiteralPath (Join-Path $New $relative)) -or (Test-Path -LiteralPath $target)) { continue }
    New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
    Move-Item -LiteralPath $file.FullName -Destination $target
    $moved++
  }
  if ($moved -gt 0) {
    Write-Host "[cozy] 이전 판에만 있던 파일 ${moved}개를 사용자 저장소로 옮겼습니다: ${UserRepository}"
  }
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

  New-Item -ItemType Directory -Force -Path $UserRepository | Out-Null
  $repo = Join-Path $Destination 'repository'
  $bundleRepo = (Resolve-Path (Join-Path $Root 'repository')).Path
  if ($bundleRepo -ne $repo) {
    if (Test-Path $repo) {
      Move-OldOnlyFiles $repo $bundleRepo
      Remove-Item -Recurse -Force $repo
    }
    Copy-Item -Recurse $bundleRepo $repo
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

  # Maven은 사용자 저장소에 쓰고, Maven 폴더 옆의 번들 저장소는 읽기만 한다(꼬리). 절대 경로를 쓰지 않으므로
  # 설치 폴더를 옮기거나 MSI가 다른 곳에 깔아도 맞다. 사용자 저장소는 다시 설치하거나 지워도 남는다
  $settings = @"
<?xml version="1.0" encoding="UTF-8"?>
<!-- Cozy 오프라인 번들 ${Version}: 사용자 저장소와 번들 저장소만 쓰고 네트워크에 나가지 않는다 -->
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>`${env.LOCALAPPDATA}/Cozy/repository</localRepository>
  <offline>true</offline>
  <profiles>
    <profile>
      <id>cozy-bundle</id>
      <properties>
        <maven.repo.local.tail>`${maven.home}/../repository</maven.repo.local.tail>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>cozy-bundle</activeProfile>
  </activeProfiles>
</settings>
"@
  [System.IO.File]::WriteAllText((Join-Path $mavenDir 'conf\settings.xml'), $settings, (New-Object System.Text.UTF8Encoding($false)))

  Write-Host "[cozy] 설치했습니다: $Destination"
  Write-Host "  저장소:      ${UserRepository} (Maven이 쓰는 곳. 다시 설치하거나 지워도 남는다)"
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

# describe, check, recipes, add, link, enable, sync, set, migrate, attach, remove, rename, channel, update, upgrade는 cozy-cli가 한다. 번들 저장소의 cozy-ui jar를 읽는다.
# 설치 폴더(COZY_HOME)를 알려 주면 cozy-cli가 그 COZY_VERSION으로 지금 판을 알고, cozy upgrade가 그 폴더에 다시 설치한다
function Invoke-CozyCli([object[]]$CliArgs) {
  $jar = Join-Path $Root 'lib\cli\cozy-cli.jar'
  if (-not (Test-Path $jar)) {
    Write-Host "[cozy] ${jar}가 없습니다. 번들을 다시 설치하세요."
    exit 2
  }
  Assert-Java
  if (-not $env:COZY_REPOSITORY) { $env:COZY_REPOSITORY = $UserRepository + ';' + (Join-Path $Root 'repository') }
  $env:COZY_HOME = $Root
  & java -jar $jar @CliArgs
  exit $LASTEXITCODE
}

switch ($Action) {
  'install' { Install-Cozy ($(if ($Rest.Count -gt 0) { [string]$Rest[0] } else { $null })) }
  'new' { New-CozyApp $Rest }
  { $_ -in @('describe', 'check', 'recipes', 'add', 'link', 'enable', 'sync', 'set', 'migrate', 'attach', 'remove', 'rename', 'channel', 'update', 'upgrade') } { Invoke-CozyCli (@($Action) + $Rest) }
  { $_ -in @('version', '--version', '-v') } { Write-Output $Version }
  { $_ -in @('help', '--help', '-h') } { Show-Usage }
  default {
    Write-Host "[cozy] 알 수 없는 동작: $Action"
    Show-Usage
    exit 2
  }
}
