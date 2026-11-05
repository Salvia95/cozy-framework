# M1 runtime checks on Windows 11 (docs/conventions/windows-verification.md)
# ASCII only: Windows PowerShell 5.1 misreads non-BOM UTF-8 scripts.
param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [string]$AppId = 'cozy-reference',
    [int]$TimeoutSec = 60
)
$ErrorActionPreference = 'Stop'
$script:failures = 0

function Check([string]$Name, [bool]$Ok) {
    if ($Ok) { Write-Host "[PASS] $Name" -ForegroundColor Green }
    else { Write-Host "[FAIL] $Name" -ForegroundColor Red; $script:failures++ }
}

function Get-HttpStatus([int]$Port, [string]$HostHeader) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('127.0.0.1', $Port)
        $stream = $client.GetStream()
        $request = "GET / HTTP/1.1`r`nHost: $HostHeader`r`nConnection: close`r`n`r`n"
        $bytes = [System.Text.Encoding]::ASCII.GetBytes($request)
        $stream.Write($bytes, 0, $bytes.Length)
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::ASCII)
        return [int]($reader.ReadLine().Split(' ')[1])
    }
    finally { $client.Close() }
}

$Jar = (Resolve-Path $Jar).Path
$java = (Get-Command java).Source
$dataDir = Join-Path $env:LOCALAPPDATA $AppId
$portFile = Join-Path $dataDir 'app.port'
$logs = Join-Path (Get-Location) 'm1-logs'
New-Item -ItemType Directory -Force $logs | Out-Null

if (Test-Path $portFile) {
    $old = [int](Get-Content $portFile -Raw).Trim()
    $running = $true
    try { Get-HttpStatus $old "127.0.0.1:$old" | Out-Null } catch { $running = $false }
    if ($running) { Write-Host "An instance of $AppId is already running on port $old. Close it and run again." -ForegroundColor Yellow; exit 2 }
}

Write-Host "java: $java"
Write-Host "jar : $Jar"
Write-Host "Starting first instance (the browser should open)..."
$start = Get-Date
$first = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$Jar`"") -PassThru -NoNewWindow `
    -RedirectStandardOutput (Join-Path $logs 'first.out.log') -RedirectStandardError (Join-Path $logs 'first.err.log')

try {
    $port = $null
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline -and -not $first.HasExited) {
        if (Test-Path $portFile) {
            if ((Get-Item $portFile).LastWriteTime -ge $start.AddSeconds(-2)) {
                $port = [int](Get-Content $portFile -Raw).Trim()
                break
            }
        }
        Start-Sleep -Milliseconds 300
    }
    Check "app.port written under $dataDir" ($null -ne $port)
    if ($null -eq $port) { Write-Host "See $logs\first.*.log"; exit 1 }
    Write-Host "port: $port"

    foreach ($name in 'data', 'logs', 'config', 'backup', 'update', 'diagnostics') {
        Check "data folder $name exists" (Test-Path (Join-Path $dataDir $name) -PathType Container)
    }
    Check "Host 127.0.0.1:$port is not 403" ((Get-HttpStatus $port "127.0.0.1:$port") -ne 403)
    Check "Host localhost:$port is not 403" ((Get-HttpStatus $port "localhost:$port") -ne 403)
    Check "Host evil.example:$port is 403" ((Get-HttpStatus $port "evil.example:$port") -eq 403)

    Write-Host "Starting second instance (a second browser tab should open)..."
    $second = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$Jar`"") -PassThru -NoNewWindow -Wait `
        -RedirectStandardOutput (Join-Path $logs 'second.out.log') -RedirectStandardError (Join-Path $logs 'second.err.log')
    Check "second instance exit code is 0 (was $($second.ExitCode))" ($second.ExitCode -eq 0)
    $secondOutput = (Get-Content (Join-Path $logs 'second.out.log') -Raw) + (Get-Content (Join-Path $logs 'second.err.log') -Raw)
    Check "second instance points to http://127.0.0.1:$port/" ($secondOutput -like "*http://127.0.0.1:$port/*")
    Check "second instance did not start a web server" (-not ($secondOutput -like '*Tomcat started*'))
    Check "first instance is still running" (-not $first.HasExited)
    Check "log file logs\app.log exists" (Test-Path (Join-Path $dataDir 'logs\app.log'))
}
finally {
    if (-not $first.HasExited) { Stop-Process -Id $first.Id -Force }
}

Write-Host ""
Write-Host "Manual: did the browser open on the first run, and open another tab on the second run?"
if ($script:failures -eq 0) { Write-Host "All automatic checks passed." -ForegroundColor Green; exit 0 }
Write-Host "$($script:failures) check(s) failed. Logs: $logs" -ForegroundColor Red
exit 1
