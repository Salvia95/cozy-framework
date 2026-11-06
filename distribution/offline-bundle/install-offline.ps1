# Cozy 오프라인 번들 설치 (인터넷이 없는 Windows PC). 번들 폴더 안에서 실행한다.
#
#   powershell -ExecutionPolicy Bypass -File .\install-offline.ps1 [-Destination 폴더]   (기본: %USERPROFILE%\cozy)
#
# Maven 배포판을 풀고, 그 Maven의 conf\settings.xml이 번들 저장소를 가리키며 항상 오프라인으로 동작하게 한다.
param([string]$Destination = (Join-Path $env:USERPROFILE 'cozy'))
$ErrorActionPreference = 'Stop'

$Bundle = $PSScriptRoot
$Version = (Get-Content -Raw (Join-Path $Bundle 'COZY_VERSION')).Trim()

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { throw '[cozy] JDK 25가 필요합니다. JAVA_HOME과 PATH를 확인하세요.' }
$javaVersion = (& java -XshowSettings:properties -version 2>&1 | Select-String 'java.specification.version = (\d+)').Matches[0].Groups[1].Value
if ([int]$javaVersion -lt 25) { throw "[cozy] JDK 25가 필요합니다 (지금: $javaVersion)" }

New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$zip = Get-ChildItem -Path $Bundle -Filter 'apache-maven-*-bin.zip' | Select-Object -First 1
$mavenDir = Join-Path $Destination ($zip.BaseName -replace '-bin$', '')
if (Test-Path $mavenDir) { Remove-Item -Recurse -Force $mavenDir }
Expand-Archive -Path $zip.FullName -DestinationPath $Destination

$repo = Join-Path $Destination 'repository'
if ((Resolve-Path (Join-Path $Bundle 'repository')).Path -ne $repo) {
  if (Test-Path $repo) { Remove-Item -Recurse -Force $repo }
  Copy-Item -Recurse (Join-Path $Bundle 'repository') $repo
}
$docs = Join-Path $Destination 'docs'
if (Test-Path $docs) { Remove-Item -Recurse -Force $docs }
Copy-Item -Recurse (Join-Path $Bundle 'docs') $docs
Copy-Item (Join-Path $Bundle 'new-app.ps1'), (Join-Path $Bundle 'COZY_VERSION') $Destination -Force

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
Write-Host "  PATH에 추가: `$env:Path = '$mavenDir\bin;' + `$env:Path"
Write-Host "  새 앱:       powershell -ExecutionPolicy Bypass -File $Destination\new-app.ps1"
