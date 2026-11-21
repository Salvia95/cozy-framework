# Cozy 설치 파일(MSI)의 cozy upgrade 검증. Windows에서 실행한다 (CI의 Windows 러너, 또는 Cozy가 설치되지 않은 PC).
#
#   powershell -ExecutionPolicy Bypass -File distribution\msi\verify-msi-upgrade.ps1 -Bundle <windows-x64 번들 zip> [-Work 폴더]
#
# 1. 번들에서 판만 다른 번들 두 개(앞 판, 앞 판의 끝자리 + 1)를 만들고 build-msi.ps1로 MSI 두 개를 만든다.
#    앞 판의 번들 저장소에만 표시 파일을 둔다(이전 판에만 있는 cozy 모듈의 자리). 개발 판(SNAPSHOT)이면 -SNAPSHOT을 뗀 판을 쓴다
# 2. 앞 판 MSI를 설치하고, 뒤 판 MSI를 담은 파일 채널을 COZY_CHANNEL로 정한다
# 3. cozy upgrade → cozy가 끝난 뒤 도는 설치 스크립트를 기다려 판, 사용자 저장소로 옮긴 표시 파일을 보고,
#    다음 cozy 명령이 결과를 보이고 기록과 받은 파일을 지웠는지 본다
# 4. 지운다
param(
  [Parameter(Mandatory = $true)][string]$Bundle,
  [string]$Work = (Join-Path (Get-Location) 'build\msi-upgrade-check')
)
$ErrorActionPreference = 'Stop'

function Check([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw "[cozy] 확인 실패: $Message" }
  Write-Host "[cozy] 확인: $Message"
}

function Invoke-Msiexec([string[]]$Arguments, [string]$Log) {
  $p = Start-Process -FilePath 'msiexec.exe' -ArgumentList ($Arguments + @('/qn', '/l*v', "`"$Log`"")) -Wait -PassThru
  return $p.ExitCode
}

$Bundle = (Resolve-Path $Bundle).Path
if (Test-Path $Work) { Remove-Item -Recurse -Force $Work }
New-Item -ItemType Directory -Force -Path $Work | Out-Null
$Work = (Resolve-Path $Work).Path
$dir = Join-Path $env:LOCALAPPDATA 'Programs\Cozy'
Check (-not (Test-Path (Join-Path $dir 'bin'))) "설치 전에 $dir\bin이 없다"

# 1. 판만 다른 번들 두 개와 MSI
$source = Join-Path $Work 'source'
Expand-Archive -Path $Bundle -DestinationPath $source
$root = Get-ChildItem -Path $source -Directory -Filter 'cozy-offline-bundle-*-windows-x64' | Select-Object -First 1
$base = (Get-Content -Raw (Join-Path $root.FullName 'COZY_VERSION')).Trim() -replace '-SNAPSHOT$', ''
Check ($base -match '^(\d+)\.(\d+)\.(\d+)$') "번들 판 ${base}은 숫자 세 자리"
$from = $base
$to = $Matches[1] + '.' + $Matches[2] + '.' + ([int]$Matches[3] + 1)
$marker = 'io\salvia95\cozy\cozy-upgrade-check\1.0\cozy-upgrade-check-1.0.jar'
$bundles = Join-Path $Work 'bundles'
New-Item -ItemType Directory -Force -Path $bundles | Out-Null

function New-VersionBundle([string]$Version, [bool]$WithMarker) {
  $name = "cozy-offline-bundle-$Version-windows-x64"
  $folder = Join-Path $source $name
  if ($script:root.FullName -ne $folder) {
    Rename-Item -LiteralPath $script:root.FullName -NewName $name
    $script:root = Get-Item -LiteralPath $folder
  }
  [System.IO.File]::WriteAllText((Join-Path $folder 'COZY_VERSION'), "$Version`n", (New-Object System.Text.ASCIIEncoding))
  $markerFile = Join-Path $folder "repository\$marker"
  if ($WithMarker) {
    New-Item -ItemType Directory -Force -Path (Split-Path $markerFile -Parent) | Out-Null
    [System.IO.File]::WriteAllText($markerFile, 'only in the old release', (New-Object System.Text.ASCIIEncoding))
  }
  elseif (Test-Path $markerFile) {
    Remove-Item -Recurse -Force (Split-Path (Split-Path $markerFile -Parent) -Parent)
  }
  $zip = Join-Path $bundles "$name.zip"
  & tar.exe -a -c -f $zip -C $source $name | Out-Host
  Check ($LASTEXITCODE -eq 0) "번들 $name.zip을 묶었다"
  # 함수의 결과가 MSI 경로 하나가 되도록 만드는 동안의 출력은 화면으로 보낸다
  & (Join-Path $PSScriptRoot 'build-msi.ps1') -Bundle $zip -Output (Join-Path $Work 'msi') | Out-Host
  return (Join-Path $Work "msi\cozy-$Version-windows-x64.msi")
}

$fromMsi = New-VersionBundle $from $true
$toMsi = New-VersionBundle $to $false
Check ((Test-Path $fromMsi) -and (Test-Path $toMsi)) "MSI 두 개: $from, $to"
Remove-Item -Recurse -Force $source, $bundles

# 2. 앞 판 설치와 뒤 판을 담은 파일 채널
$code = Invoke-Msiexec @('/i', "`"$fromMsi`"") (Join-Path $Work 'install.log')
Check ($code -eq 0) "msiexec /i $from 종료 코드 0 (지금 $code)"
Check ((Get-Content -Raw (Join-Path $dir 'COZY_INSTALLER')).Trim() -eq 'msi') 'MSI로 설치한 폴더에 COZY_INSTALLER(msi)'
Check (Test-Path (Join-Path $dir "repository\$marker")) '앞 판의 번들 저장소에 표시 파일'
$userMarker = Join-Path $env:LOCALAPPDATA "Cozy\repository\$marker"
if (Test-Path $userMarker) { Remove-Item -Force $userMarker }
$channel = Join-Path $Work 'channel'
New-Item -ItemType Directory -Force -Path (Join-Path $channel $to), (Join-Path $channel 'index') | Out-Null
$published = Join-Path $channel "$to\cozy-$to-windows-x64.msi"
Copy-Item $toMsi $published
$hash = (Get-FileHash -Algorithm SHA256 $published).Hash.ToLowerInvariant()
$size = (Get-Item $published).Length
$index = '{"schema": 1, "releases": [{"version": "' + $to + '", "date": "' + (Get-Date -Format 'yyyy-MM-dd') + '", "breaking": false, ' +
  '"summary": "upgrade check", "appSteps": [], "files": [{"kind": "msi", "path": "../' + $to + '/cozy-' + $to + '-windows-x64.msi", ' +
  '"sha256": "' + $hash + '", "size": ' + $size + '}]}]}'
$indexFile = Join-Path $channel 'index\cozy-channel.json'
[System.IO.File]::WriteAllText($indexFile, $index, (New-Object System.Text.ASCIIEncoding))
$env:COZY_CHANNEL = $indexFile
$cozy = Join-Path $dir 'bin\cozy.cmd'

# 3. cozy upgrade. 설치는 cozy가 끝난 뒤 따로 도는 스크립트가 한다
$update = (& $cozy upgrade --check --json) -join "`n"
Check ($LASTEXITCODE -eq 0 -and $update.Contains('"upgrade": "' + $to + '"')) "cozy upgrade --check가 올릴 판 ${to}을 알린다"
& $cozy upgrade
Check ($LASTEXITCODE -eq 0) "cozy upgrade 종료 코드 0 (지금 $LASTEXITCODE)"
$upgradeWork = Join-Path $env:LOCALAPPDATA "Cozy\upgrade\$to"
$result = Join-Path $upgradeWork 'result'
$deadline = (Get-Date).AddMinutes(10)
while (-not (Test-Path $result) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
foreach ($log in @('install.log', 'msiexec.log')) {
  $file = Join-Path $upgradeWork $log
  if (Test-Path $file) { Copy-Item $file (Join-Path $Work "upgrade-$log") }
}
Check (Test-Path $result) '설치 스크립트가 10분 안에 결과를 남겼다'
Check ((Get-Content -Raw $result).Trim() -eq '0') "설치 스크립트의 종료 코드 0 (지금 $((Get-Content -Raw $result).Trim()))"
Check ((Get-Content -Raw (Join-Path $dir 'COZY_VERSION')).Trim() -eq $to) "설치 폴더의 판이 $to"
Check (-not (Test-Path (Join-Path $dir "repository\$marker"))) '새 판의 번들 저장소에는 표시 파일이 없다'
Check (Test-Path $userMarker) '이전 판에만 있던 표시 파일을 사용자 저장소로 옮겼다'
Check (-not (Test-Path (Join-Path $upgradeWork 'previous-repository'))) '떠 둔 번들 저장소를 지웠다'
$record = Join-Path $env:APPDATA 'Cozy\upgrade.properties'
Check (Test-Path $record) '다음 cozy 명령 전에는 설치 기록이 남아 있다'
$update = (& $cozy upgrade --check --json) -join "`n"
Check ($LASTEXITCODE -eq 0 -and $update.Contains('"upToDate": true') -and $update.Contains('"current": "' + $to + '"')) "올린 뒤 cozy upgrade --check는 최신 판 $to"
Check (-not (Test-Path $record)) '다음 cozy 명령이 결과를 보이고 설치 기록을 지웠다'
Check (-not (Test-Path $upgradeWork)) '성공한 설치의 받은 파일을 지웠다'
Remove-Item Env:COZY_CHANNEL

# 4. 지우기
$code = Invoke-Msiexec @('/x', "`"$toMsi`"") (Join-Path $Work 'uninstall.log')
Check ($code -eq 0) "msiexec /x 종료 코드 0 (지금 $code)"
Check (-not (Test-Path (Join-Path $dir 'bin'))) "지운 뒤 $dir\bin이 없다"
Remove-Item -Force $userMarker

Write-Host "[cozy] MSI 업그레이드 검증 통과: $from → $to"
exit 0
