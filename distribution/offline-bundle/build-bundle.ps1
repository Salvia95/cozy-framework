# Cozy 오프라인 번들 만들기 (PRD §9.7). 인터넷이 되는 Windows PC에서 프레임워크 레포 루트를 기준으로 실행한다.
#
#   powershell -ExecutionPolicy Bypass -File distribution\offline-bundle\build-bundle.ps1 [-Output 폴더] [-MavenZip 경로또는URL]
#
# 순서는 build-bundle.sh와 같다: 빈 로컬 저장소로 프레임워크 빌드 → 아키타입 3조합 verify·-Pdist package
# → 원격 저장소 표시 정리 → 제3자 라이선스 목록(소스 jar 포함, ADR-0028) → Maven 배포판(SHA-512 확인)·스크립트·문서와 함께 zip.
param(
  [string]$Output,
  [string]$MavenZip = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip',
  # apache-maven-3.10.0-bin.zip의 SHA-512 (Maven Central과 archive.apache.org의 .sha512가 같다, 2026-11-06 확인)
  [string]$MavenSha512 = '22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337',
  # 소스 jar를 받는 Maven 저장소 (사내 미러가 있으면 바꾼다)
  [string]$SourcesRepo = 'https://repo.maven.apache.org/maven2/',
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
# 번들 검증용으로 만든 앱과 프레임워크의 위반 픽스처는 담지 않는다
foreach ($skip in @('com\example', 'io\salvia95\cozy\cozy-violations')) {
  $path = Join-Path $Repo $skip
  if (Test-Path $path) { Remove-Item -Recurse -Force $path }
}

# 4. 제3자 라이선스 목록 (Cozy 자신의 LICENSE, NOTICE와 예외 목록도 함께)
$MavenFile = Split-Path $MavenZip -Leaf
$licenses = New-Item -ItemType Directory -Force -Path (Join-Path $Bundle 'licenses')
Copy-Item (Join-Path $Root 'LICENSE'), (Join-Path $Root 'NOTICE') $Bundle
Copy-Item (Join-Path $PSScriptRoot 'license-overrides.txt') $licenses
$runtimeJar = Join-Path $Repo "io\salvia95\cozy\cozy-starter-runtime\$Version\cozy-starter-runtime-$Version.jar"
& java -cp $runtimeJar io.salvia95.cozy.runtime.internal.dist.BundleLicenses "--repository=$Repo" `
  "--overrides=$(Join-Path $licenses 'license-overrides.txt')" "--output=$(Join-Path $licenses 'THIRD-PARTY.txt')" `
  "--title=Cozy offline bundle $Version" "--maven-distribution=$MavenFile" "--fetch-sources=$SourcesRepo"
if ($LASTEXITCODE -ne 0) { throw "[cozy] 번들 라이선스 목록을 만들지 못했습니다 (종료 코드 $LASTEXITCODE)" }

# 5. Maven 배포판, 스크립트, 문서
$mavenTarget = Join-Path $Bundle $MavenFile
if ($MavenZip -match '^https?://') {
  Invoke-WebRequest -UseBasicParsing -Uri $MavenZip -OutFile $mavenTarget
}
else { Copy-Item $MavenZip $Bundle }
if ((Get-FileHash -Algorithm SHA512 $mavenTarget).Hash.ToLowerInvariant() -ne $MavenSha512) {
  throw "[cozy] $MavenFile의 SHA-512가 맞지 않습니다 (다른 판이면 -MavenSha512를 주세요)"
}
foreach ($file in @('cozy', 'cozy.cmd', 'cozy.ps1', 'README.md')) {
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
