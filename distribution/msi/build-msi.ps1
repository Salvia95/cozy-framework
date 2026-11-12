# Cozy 설치 파일(MSI) 만들기. Windows에서 WiX Toolset(wix 명령, 약관 동의 필요)으로 실행한다.
#
#   powershell -ExecutionPolicy Bypass -File distribution\msi\build-msi.ps1 -Bundle <windows-x64 번들 zip> [-Output 폴더]
#
# 1. 번들을 풀고 번들의 cozy.cmd install로 설치 폴더 모양을 만든다 (zip으로 설치할 때와 같은 폴더)
# 2. 그 폴더를 cozy.wxs로 묶는다. 결과: cozy-{버전}-windows-x64.msi
#    MSI 판은 숫자 세 자리라 -SNAPSHOT을 뗀다. 같은 판끼리도 업그레이드로 덮어쓴다
param(
  [Parameter(Mandatory = $true)][string]$Bundle,
  [string]$Output = (Join-Path (Get-Location) 'build\msi')
)
$ErrorActionPreference = 'Stop'

$Bundle = (Resolve-Path $Bundle).Path
New-Item -ItemType Directory -Force -Path $Output | Out-Null
$Output = (Resolve-Path $Output).Path
$work = Join-Path $Output 'work'
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force -Path $work | Out-Null

# 1. 번들 → 설치 폴더 모양
Write-Host "[cozy] 번들 풀기: $Bundle"
Expand-Archive -Path $Bundle -DestinationPath $work
$root = Get-ChildItem -Path $work -Directory -Filter 'cozy-offline-bundle-*-windows-x64' | Select-Object -First 1
if (-not $root -or -not (Test-Path (Join-Path $root.FullName 'jdk\bin\java.exe'))) {
  throw "[cozy] JDK를 담은 windows-x64 번들이 아닙니다: $Bundle"
}
$staging = Join-Path $work 'staging'
& (Join-Path $root.FullName 'cozy.cmd') install $staging
if ($LASTEXITCODE -ne 0) { throw "[cozy] cozy install 실패 (종료 코드 $LASTEXITCODE)" }
foreach ($path in @('bin\cozy.cmd', 'bin\mvn.cmd', 'lib\cozy.ps1', 'jdk\bin\java.exe', 'repository', 'COZY_VERSION')) {
  if (-not (Test-Path (Join-Path $staging $path))) { throw "[cozy] 설치 폴더에 $path가 없습니다" }
}

# 2. MSI
$version = (Get-Content -Raw (Join-Path $staging 'COZY_VERSION')).Trim()
$msiVersion = $version -replace '-SNAPSHOT$', ''
if ($msiVersion -notmatch '^\d+\.\d+\.\d+$') { throw "[cozy] MSI 판으로 바꿀 수 없는 버전입니다: $version" }
$msi = Join-Path $Output "cozy-$version-windows-x64.msi"
if (Test-Path $msi) { Remove-Item -Force $msi }
Write-Host "[cozy] wix build: Cozy $version (MSI 판 $msiVersion)"
& wix build -arch x64 -d "CozyMsiVersion=$msiVersion" -d "CozyVersion=$version" -bindpath "Staging=$staging" `
  -o $msi (Join-Path $PSScriptRoot 'cozy.wxs')
if ($LASTEXITCODE -ne 0) { throw "[cozy] wix build 실패 (종료 코드 $LASTEXITCODE)" }

Remove-Item -Recurse -Force $work
$size = [math]::Round((Get-Item $msi).Length / 1MB)
Write-Host "[cozy] 완료: $msi (${size}MB)"
