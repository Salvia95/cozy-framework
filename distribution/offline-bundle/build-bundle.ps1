# Cozy 오프라인 번들 만들기 (PRD §9.7). 인터넷이 되는 Windows PC에서 프레임워크 레포 루트를 기준으로 실행한다.
#
#   powershell -ExecutionPolicy Bypass -File distribution\offline-bundle\build-bundle.ps1 [-Output 폴더] [-MavenZip 경로또는URL]
#
# 순서는 build-bundle.sh와 같다: 빈 로컬 저장소로 프레임워크 빌드 → 아키타입 3조합 verify·-Pdist package
# → 원격 저장소 표시 정리 → Maven 배포판·스크립트·문서와 함께 zip.
param(
  [string]$Output,
  [string]$MavenZip = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip',
  [string]$Mvn = 'mvn.cmd'
)
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
# 루트 target\은 빌드의 clean이 지우므로 build\에 만든다
if (-not $Output) { $Output = Join-Path $Root 'build\offline-bundle' }

$javaVersion = (& java -XshowSettings:properties -version 2>&1 | Select-String 'java.specification.version = (\d+)').Matches[0].Groups[1].Value
if ([int]$javaVersion -lt 25) { throw "[cozy] JDK 25가 필요합니다 (지금: $javaVersion)" }

$Version = ([xml](Get-Content -Raw -Encoding UTF8 (Join-Path $Root 'cozy-parent\pom.xml'))).project.properties.'cozy.version'
$Bundle = Join-Path $Output "cozy-offline-bundle-$Version"
$Repo = Join-Path $Bundle 'repository'
$Work = Join-Path $Output 'work'
Write-Host "[cozy] 번들 $Version → $Bundle"
foreach ($dir in @($Bundle, $Work)) { if (Test-Path $dir) { Remove-Item -Recurse -Force $dir } }
New-Item -ItemType Directory -Force -Path $Repo, $Work | Out-Null

function Invoke-Mvn([string]$Dir, [string[]]$MvnArgs) {
  Write-Host "[cozy] mvn $($MvnArgs -join ' ')"
  Push-Location $Dir
  try {
    & $Mvn -B -ntp "-Dmaven.repo.local=$Repo" @MvnArgs
    if ($LASTEXITCODE -ne 0) { throw "[cozy] mvn 실패 (종료 코드 $LASTEXITCODE): $($MvnArgs -join ' ')" }
  }
  finally { Pop-Location }
}

# 1. 프레임워크
Invoke-Mvn $Root @('clean', 'install')

# 2. 옵션 3조합
foreach ($combo in @(@('basic', 'false', 'false'), @('persistence', 'true', 'false'), @('events', 'true', 'true'))) {
  $name = $combo[0]
  Invoke-Mvn $Work @('org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate', '-DinteractiveMode=false',
    '-DarchetypeCatalog=local', '-DarchetypeGroupId=io.salvia95.cozy', '-DarchetypeArtifactId=cozy-archetype',
    "-DarchetypeVersion=$Version", '-DgroupId=com.example', "-DartifactId=bundle-$name", '-Dversion=1.0.0',
    "-Dpackage=com.example.bundle.$name", "-Dpersistence=$($combo[1])", "-Devents=$($combo[2])")
  Invoke-Mvn (Join-Path $Work "bundle-$name") @('verify')
  Invoke-Mvn (Join-Path $Work "bundle-$name") @('-Pdist', 'package')
}

# 3. 원격 저장소 표시 정리
Get-ChildItem -Path $Repo -Recurse -File -Include '_remote.repositories', '*.lastUpdated', 'resolver-status.properties' |
  Remove-Item -Force
$examples = Join-Path $Repo 'com\example'
if (Test-Path $examples) { Remove-Item -Recurse -Force $examples }

# 4. Maven 배포판, 스크립트, 문서
if ($MavenZip -match '^https?://') {
  Invoke-WebRequest -UseBasicParsing -Uri $MavenZip -OutFile (Join-Path $Bundle (Split-Path $MavenZip -Leaf))
}
else { Copy-Item $MavenZip $Bundle }
foreach ($file in @('install-offline.sh', 'install-offline.ps1', 'new-app.sh', 'new-app.ps1', 'README.md')) {
  Copy-Item (Join-Path $PSScriptRoot $file) $Bundle
}
$docs = New-Item -ItemType Directory -Force -Path (Join-Path $Bundle 'docs')
Copy-Item -Recurse (Join-Path $Root 'docs\conventions'), (Join-Path $Root 'docs\adr') $docs
Copy-Item (Join-Path $Root 'docs\AGENTS.md') $docs
Set-Content -Path (Join-Path $Bundle 'COZY_VERSION') -Value $Version -Encoding ASCII

$zip = Join-Path $Output "cozy-offline-bundle-$Version.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path $Bundle -DestinationPath $zip
Write-Host "[cozy] 완료: $zip ($([math]::Round((Get-Item $zip).Length / 1MB))MB)"
