# Cozy 설치 파일(MSI) 검증. Windows에서 실행한다 (CI의 Windows 러너, 또는 Cozy가 설치되지 않은 PC).
#
#   powershell -ExecutionPolicy Bypass -File distribution\msi\verify-msi.ps1 -Msi <cozy-*.msi> [-Work 폴더]
#
# 1. 조용히 설치(사용자별)하고 설치 폴더, 사용자 PATH, Maven 설정을 본다
# 2. 시스템 JDK 자리에 부르면 실패하는 java를 두고, 설치한 cozy와 mvn으로 앱 생성·cozy add·verify·-Pdist package를 한다
# 3. 실패하는 빌드의 종료 코드가 mvn.cmd를 거쳐 그대로 나오는지 본다
# 4. 지우고 PATH와 설치한 파일이 사라졌는지 본다
# 5. 컴퓨터별 설치(ALLUSERS=1)는 거부되고 아무것도 깔리지 않는지 본다
param(
  [Parameter(Mandatory = $true)][string]$Msi,
  [string]$Work = (Join-Path (Get-Location) 'build\msi-check')
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

function Test-UserPath([string]$Dir) {
  $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
  return [bool]($userPath -and (($userPath -split ';') -contains $Dir))
}

$Msi = (Resolve-Path $Msi).Path
if (Test-Path $Work) { Remove-Item -Recurse -Force $Work }
New-Item -ItemType Directory -Force -Path $Work | Out-Null
$Work = (Resolve-Path $Work).Path
$dir = Join-Path $env:LOCALAPPDATA 'Programs\Cozy'
Check (-not (Test-Path (Join-Path $dir 'bin'))) "설치 전에 $dir\bin이 없다"

# 1. 설치
$code = Invoke-Msiexec @('/i', "`"$Msi`"") (Join-Path $Work 'install.log')
Check ($code -eq 0) "msiexec /i 종료 코드 0 (지금 $code)"
foreach ($path in @('bin\cozy.cmd', 'bin\mvn.cmd', 'lib\cozy.ps1', 'jdk\bin\java.exe', 'jdk\bin\jpackage.exe', 'repository', 'licenses\THIRD-PARTY.txt')) {
  Check (Test-Path (Join-Path $dir $path)) "설치 폴더에 $path"
}
Check (-not (Test-Path (Join-Path $dir 'jdk\lib\src.zip'))) 'jdk\lib\src.zip은 없다'
Check (Test-UserPath "$dir\bin") "사용자 PATH에 $dir\bin"
$settings = Get-ChildItem -Path $dir -Directory -Filter 'apache-maven-*' | Select-Object -First 1
$settingsText = Get-Content -Raw -Encoding UTF8 (Join-Path $settings.FullName 'conf\settings.xml')
Check ($settingsText.Contains('<localRepository>${maven.home}/../repository</localRepository>')) 'Maven 설정의 저장소가 Maven 폴더 옆'
$version = (Get-Content -Raw (Join-Path $dir 'COZY_VERSION')).Trim()

# 2. 새 창처럼: PATH는 설치한 bin + 시스템 JDK를 막는 가짜 java + 시스템 PATH. JAVA_HOME도 가짜
$fake = Join-Path $Work 'system-jdk'
New-Item -ItemType Directory -Force -Path (Join-Path $fake 'bin') | Out-Null
Set-Content -Encoding ASCII -Path (Join-Path $fake 'bin\java.cmd') -Value "@echo [cozy] system JDK was called instead of the bundled one: java %* 1>&2`r`n@exit /b 97"
$env:JAVA_HOME = $fake
$env:Path = "$dir\bin;$fake\bin;" + [Environment]::GetEnvironmentVariable('Path', 'Machine')
Check ((Get-Command mvn).Source -eq "$dir\bin\mvn.cmd") "mvn은 $dir\bin\mvn.cmd"
Check ((& cozy version) -eq $version) "cozy version = $version"
$mvnVersion = (& mvn -v) -join "`n"
Check ($LASTEXITCODE -eq 0 -and $mvnVersion.Contains("runtime: $dir\jdk")) "mvn -v의 runtime이 $dir\jdk"

$apps = Join-Path $Work 'apps'
New-Item -ItemType Directory -Force -Path $apps | Out-Null
Push-Location $apps
try {
  & cozy new '-B' '-DinteractiveMode=false' '-DgroupId=com.example' '-DartifactId=msi-check' '-Dpackage=com.example.msi' `
    '-DappName=설치 확인' '-Dpersistence=true' '-Devents=false'
  Check ($LASTEXITCODE -eq 0) 'cozy new'
  Set-Location (Join-Path $apps 'msi-check')
  & cozy add crud task --entity Task --label '할 일' --icon checklist --field 'title:text!:제목' --field 'dueOn:date:마감일'
  Check ($LASTEXITCODE -eq 0) 'cozy add crud'
  & cozy check
  Check ($LASTEXITCODE -eq 0) 'cozy check'
  & mvn -B -ntp -o verify
  Check ($LASTEXITCODE -eq 0) 'mvn -o verify'
  & mvn -B -ntp -o -Pdist package
  Check ($LASTEXITCODE -eq 0) 'mvn -o -Pdist package'
  $image = Join-Path $apps 'msi-check\target\dist\image\msi-check'
  Check (Test-Path (Join-Path $image 'msi-check.exe')) 'app-image의 msi-check.exe'
  $runtime = Get-Content -Raw (Join-Path $image 'runtime\release')
  Check ($runtime.Contains('JAVA_VERSION="25.')) 'app-image 런타임이 JDK 25'
  # 3. 실패하는 빌드는 0이 아닌 종료 코드로 끝난다
  # stderr를 2>&1로 받지 않는다. Windows PowerShell 5.1은 그것을 오류로 바꿔 스크립트를 멈춘다
  & mvn -B -ntp -o no-such-phase | Out-Null
  Check ($LASTEXITCODE -ne 0) "실패한 mvn의 종료 코드가 mvn.cmd를 거쳐 나온다 (지금 $LASTEXITCODE)"
}
finally {
  Pop-Location
}

# 4. 지우기
$code = Invoke-Msiexec @('/x', "`"$Msi`"") (Join-Path $Work 'uninstall.log')
Check ($code -eq 0) "msiexec /x 종료 코드 0 (지금 $code)"
Check (-not (Test-UserPath "$dir\bin")) "사용자 PATH에서 $dir\bin이 빠졌다"
foreach ($path in @('bin', 'lib', 'jdk', 'docs', 'licenses')) {
  Check (-not (Test-Path (Join-Path $dir $path))) "지운 뒤 ${path}가 없다"
}
if (Test-Path $dir) {
  $left = @(Get-ChildItem -Path $dir -Recurse -File)
  Write-Host "[cozy] 참고: 지운 뒤 남은 파일 $($left.Count)개 (설치 뒤 Maven이 저장소에 만든 파일)"
  $left | Select-Object -First 10 | ForEach-Object { Write-Host "  $($_.FullName)" }
  Remove-Item -Recurse -Force $dir
}

# 5. 컴퓨터별 설치는 거부한다
$code = Invoke-Msiexec @('/i', "`"$Msi`"", 'ALLUSERS=1') (Join-Path $Work 'per-machine.log')
Check ($code -eq 1603) "ALLUSERS=1 설치는 거부된다 (종료 코드 1603, 지금 $code)"
Check (-not (Test-Path (Join-Path $env:ProgramFiles 'Cozy'))) "$env:ProgramFiles\Cozy가 생기지 않았다"
Check (-not (Test-Path (Join-Path $dir 'bin'))) "$dir\bin이 생기지 않았다"

Write-Host "[cozy] MSI 검증 통과: $Msi"
