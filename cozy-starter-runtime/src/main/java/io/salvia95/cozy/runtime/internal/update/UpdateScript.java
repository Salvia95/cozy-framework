package io.salvia95.cozy.runtime.internal.update;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 앱 폴더 교체 스크립트. Windows는 PowerShell, 그 외(개발·테스트용 Linux)는 bash다.
 *
 * <ol>
 * <li>{@code script-started}를 쓴다. 앱은 이것을 확인하고서야 종료한다.</li>
 * <li>앱 프로세스가 끝나기를 기다린다(최대 10분). {@code cancelled}가 있으면 아무것도 하지 않는다.</li>
 * <li>{@code {dataDir}/data}를 {@code update/data-backup}에 복사한다.</li>
 * <li>앱 폴더를 {@code {폴더}.old}로, 새 버전({@code {폴더}.new})을 앱 폴더로 옮긴다.</li>
 * <li>새 버전을 실행하고, {@code update/ready}에 새 버전 번호가 쓰이기를 기다린다.</li>
 * <li>시간 안에 준비되지 않거나 프로세스가 먼저 끝나면 되돌린다: 새 프로세스를 끝내고, 새 폴더를 {@code {폴더}.failed}로,
 * {@code {폴더}.old}를 앱 폴더로 옮기고, 데이터를 백업으로 바꾼 뒤 이전 버전을 실행한다.</li>
 * <li>결과를 {@code update/result.properties}에 남긴다. 다음 기동이 화면에 알린다.</li>
 * </ol>
 * 새 앱은 {@code COZY_DATA_DIR} 환경 변수로 같은 데이터 폴더를 받는다.
 */
public final class UpdateScript {

	/** 이전 프로세스 종료를 기다리는 최대 시간(초). */
	static final int OLD_PROCESS_WAIT_SECONDS = 600;

	private UpdateScript() {
	}

	/**
	 * @param oldPid 지금 실행 중인 앱의 PID
	 * @param image 지금 앱 폴더
	 * @param dataDir 데이터 루트
	 * @param files update 폴더
	 * @param fromVersion 지금 버전
	 * @param toVersion 새 버전
	 * @param readyTimeout 준비 신호 대기 시간
	 */
	public record Parameters(long oldPid, AppImage image, Path dataDir, UpdateFiles files, String fromVersion,
			String toVersion, Duration readyTimeout) {
	}

	public static String bash(Parameters p) {
		String launcher = p.image().launcher().toString().replace('\\', '/');
		return """
				#!/usr/bin/env bash
				# Cozy 업데이트 교체 스크립트 (앱이 만든 파일). docs/conventions/distribution.md
				set -u
				OLD_PID=%d
				APP_DIR=%s
				NEW_DIR=%s
				PREV_DIR=%s
				FAILED_DIR=%s
				LAUNCHER=%s
				DATA_DIR=%s
				UPDATE_DIR=%s
				FROM_VERSION=%s
				TO_VERSION=%s
				READY_TIMEOUT=%d
				OLD_WAIT=%d
				BACKUP_DIR="$UPDATE_DIR/data-backup"
				LOG="$UPDATE_DIR/update.log"

				log() { printf '%%s %%s\\n' "$(date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ)" "$*" >> "$LOG"; }
				alive() {
				  kill -0 "$1" 2>/dev/null || return 1
				  [ "$(ps -o stat= -p "$1" 2>/dev/null | cut -c1)" != "Z" ]
				}
				result() {
				  printf 'status=%%s\\nfrom=%%s\\nto=%%s\\nmessage=%%s\\n' "$1" "$FROM_VERSION" "$TO_VERSION" "$2" \\
				    > "$UPDATE_DIR/result.properties.tmp" && mv -f "$UPDATE_DIR/result.properties.tmp" "$UPDATE_DIR/result.properties"
				}
				start_app() {
				  # jpackage 런처가 남긴 변수를 물려받으면 새 런처가 바로 실패한다
				  for name in $(env | grep -o '^_JPACKAGE[A-Za-z0-9_]*'); do unset "$name"; done
				  COZY_DATA_DIR="$DATA_DIR" "$1/$LAUNCHER" > /dev/null 2>&1 < /dev/null &
				  STARTED_PID=$!
				  log "실행: $1/$LAUNCHER (pid $STARTED_PID)"
				}
				give_up() {
				  log "$1"
				  result failed "$2"
				  rm -rf "$NEW_DIR"
				  start_app "$APP_DIR"
				  exit 1
				}

				cd "$UPDATE_DIR" || exit 1
				echo "$$" > "$UPDATE_DIR/script-started"
				log "업데이트 시작: $FROM_VERSION -> $TO_VERSION (앱 pid $OLD_PID)"

				waited=0
				while alive "$OLD_PID" && [ "$waited" -lt $((OLD_WAIT * 2)) ]; do sleep 0.5; waited=$((waited + 1)); done
				if alive "$OLD_PID"; then
				  log "앱 프로세스가 끝나지 않아 업데이트를 취소합니다"
				  rm -rf "$NEW_DIR"
				  exit 1
				fi
				if [ -e "$UPDATE_DIR/cancelled" ]; then
				  log "앱이 설치를 취소했습니다"
				  rm -rf "$NEW_DIR"
				  exit 0
				fi

				rm -rf "$BACKUP_DIR"
				cp -a "$DATA_DIR/data" "$BACKUP_DIR" || give_up "데이터 백업 실패" "데이터를 백업하지 못해 업데이트를 취소했습니다."
				rm -rf "$PREV_DIR"
				mv "$APP_DIR" "$PREV_DIR" || give_up "앱 폴더 이동 실패" "앱 폴더를 옮기지 못해 업데이트를 취소했습니다."
				if ! mv "$NEW_DIR" "$APP_DIR"; then
				  mv "$PREV_DIR" "$APP_DIR"
				  give_up "새 폴더 이동 실패" "새 버전을 옮기지 못해 업데이트를 취소했습니다."
				fi

				rm -f "$UPDATE_DIR/ready"
				start_app "$APP_DIR"
				NEW_PID=$STARTED_PID
				ticks=0
				while [ "$ticks" -lt $((READY_TIMEOUT * 2)) ]; do
				  if [ -f "$UPDATE_DIR/ready" ] && [ "$(cat "$UPDATE_DIR/ready")" = "$TO_VERSION" ]; then
				    rm -f "$UPDATE_DIR/failed-version"
				    result success "v${TO_VERSION}으로 업데이트했습니다."
				    log "완료: $TO_VERSION 준비됨"
				    exit 0
				  fi
				  if ! alive "$NEW_PID"; then
				    wait "$NEW_PID" 2>/dev/null
				    log "새 버전 프로세스가 준비 전에 끝났습니다 (종료 코드 $?)"
				    break
				  fi
				  sleep 0.5
				  ticks=$((ticks + 1))
				done

				log "새 버전이 준비되지 않아 되돌립니다"
				if alive "$NEW_PID"; then
				  kill "$NEW_PID" 2>/dev/null
				  for _ in $(seq 1 20); do alive "$NEW_PID" || break; sleep 0.5; done
				  alive "$NEW_PID" && kill -9 "$NEW_PID" 2>/dev/null
				fi
				rm -rf "$FAILED_DIR"
				mv "$APP_DIR" "$FAILED_DIR" && mv "$PREV_DIR" "$APP_DIR" || log "앱 폴더를 되돌리지 못했습니다"
				rm -rf "$DATA_DIR/data" && mv "$BACKUP_DIR" "$DATA_DIR/data" || log "데이터를 되돌리지 못했습니다"
				printf '%%s' "$TO_VERSION" > "$UPDATE_DIR/failed-version"
				result rolled-back "v${TO_VERSION} 업데이트를 시작하지 못해 v${FROM_VERSION}으로 되돌렸습니다."
				start_app "$APP_DIR"
				exit 2
				""".formatted(p.oldPid(), sh(p.image().directory()), sh(p.image().staging()), sh(p.image().previous()),
				sh(p.image().failed()), sh(launcher), sh(p.dataDir()), sh(p.files().directory()), sh(p.fromVersion()),
				sh(p.toVersion()), p.readyTimeout().toSeconds(), OLD_PROCESS_WAIT_SECONDS);
	}

	public static String powershell(Parameters p) {
		return """
				# Cozy 업데이트 교체 스크립트 (앱이 만든 파일). docs/conventions/distribution.md
				$ErrorActionPreference = 'Stop'
				$OldPid = %d
				$AppDir = %s
				$NewDir = %s
				$PrevDir = %s
				$FailedDir = %s
				$Launcher = %s
				$DataDir = %s
				$UpdateDir = %s
				$FromVersion = %s
				$ToVersion = %s
				$ReadyTimeout = %d
				$OldWait = %d
				$BackupDir = Join-Path $UpdateDir 'data-backup'
				$LogFile = Join-Path $UpdateDir 'update.log'
				$Utf8 = New-Object System.Text.UTF8Encoding($false)

				function Log([string]$Message) {
				  $line = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ') + ' ' + $Message + [Environment]::NewLine
				  [System.IO.File]::AppendAllText($LogFile, $line, $Utf8)
				}
				function Write-Result([string]$Status, [string]$Message) {
				  $tmp = Join-Path $UpdateDir 'result.properties.tmp'
				  $text = "status=$Status`nfrom=$FromVersion`nto=$ToVersion`nmessage=$Message`n"
				  [System.IO.File]::WriteAllText($tmp, $text, $Utf8)
				  Move-Item -LiteralPath $tmp -Destination (Join-Path $UpdateDir 'result.properties') -Force
				}
				function Remove-Tree([string]$Path) {
				  if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Recurse -Force }
				}
				function Move-Retry([string]$From, [string]$To) {
				  for ($i = 0; $i -lt 40; $i++) {
				    try { Move-Item -LiteralPath $From -Destination $To -ErrorAction Stop; return $true }
				    catch { $last = $_; Start-Sleep -Milliseconds 500 }
				  }
				  Log "이동 실패: $From -> $To ($last)"
				  return $false
				}
				function Start-App([string]$Dir) {
				  # jpackage 런처가 남긴 변수를 물려받으면 새 런처가 cfg를 읽지 않는다
				  Get-ChildItem Env: | Where-Object { $_.Name -like '_JPACKAGE*' } | ForEach-Object { Remove-Item -LiteralPath ("Env:" + $_.Name) }
				  $env:COZY_DATA_DIR = $DataDir
				  $process = Start-Process -FilePath (Join-Path $Dir $Launcher) -WorkingDirectory $UpdateDir -PassThru
				  Log "실행: $(Join-Path $Dir $Launcher) (pid $($process.Id))"
				  return $process
				}
				function Give-Up([string]$LogMessage, [string]$Message) {
				  Log $LogMessage
				  Write-Result 'failed' $Message
				  Remove-Tree $NewDir
				  Start-App $AppDir | Out-Null
				  exit 1
				}

				try {
				  Set-Location -LiteralPath $UpdateDir
				  [System.IO.File]::WriteAllText((Join-Path $UpdateDir 'script-started'), [string]$PID, $Utf8)
				  Log "업데이트 시작: $FromVersion -> $ToVersion (앱 pid $OldPid)"

				  $deadline = (Get-Date).AddSeconds($OldWait)
				  while ((Get-Process -Id $OldPid -ErrorAction SilentlyContinue) -and ((Get-Date) -lt $deadline)) {
				    Start-Sleep -Milliseconds 500
				  }
				  if (Get-Process -Id $OldPid -ErrorAction SilentlyContinue) {
				    Log '앱 프로세스가 끝나지 않아 업데이트를 취소합니다'
				    Remove-Tree $NewDir
				    exit 1
				  }
				  if (Test-Path -LiteralPath (Join-Path $UpdateDir 'cancelled')) {
				    Log '앱이 설치를 취소했습니다'
				    Remove-Tree $NewDir
				    exit 0
				  }

				  try {
				    Remove-Tree $BackupDir
				    Copy-Item -LiteralPath (Join-Path $DataDir 'data') -Destination $BackupDir -Recurse
				  }
				  catch { Give-Up "데이터 백업 실패: $_" '데이터를 백업하지 못해 업데이트를 취소했습니다.' }
				  Remove-Tree $PrevDir
				  if (-not (Move-Retry $AppDir $PrevDir)) { Give-Up '앱 폴더 이동 실패' '앱 폴더를 옮기지 못해 업데이트를 취소했습니다.' }
				  if (-not (Move-Retry $NewDir $AppDir)) {
				    Move-Retry $PrevDir $AppDir | Out-Null
				    Give-Up '새 폴더 이동 실패' '새 버전을 옮기지 못해 업데이트를 취소했습니다.'
				  }

				  $readyFile = Join-Path $UpdateDir 'ready'
				  Remove-Item -LiteralPath $readyFile -Force -ErrorAction SilentlyContinue
				  $new = Start-App $AppDir
				  $deadline = (Get-Date).AddSeconds($ReadyTimeout)
				  while ((Get-Date) -lt $deadline) {
				    if ((Test-Path -LiteralPath $readyFile) -and ((Get-Content -LiteralPath $readyFile -Raw -ErrorAction SilentlyContinue) -eq $ToVersion)) {
				      Remove-Item -LiteralPath (Join-Path $UpdateDir 'failed-version') -Force -ErrorAction SilentlyContinue
				      Write-Result 'success' "v${ToVersion}으로 업데이트했습니다."
				      Log "완료: $ToVersion 준비됨"
				      exit 0
				    }
				    if ($new.HasExited) { Log "새 버전 프로세스가 준비 전에 끝났습니다 (종료 코드 $($new.ExitCode))"; break }
				    Start-Sleep -Milliseconds 500
				  }

				  Log '새 버전이 준비되지 않아 되돌립니다'
				  if (-not $new.HasExited) {
				    Stop-Process -Id $new.Id -Force -ErrorAction SilentlyContinue
				    $new.WaitForExit(10000) | Out-Null
				  }
				  Remove-Tree $FailedDir
				  if ((Move-Retry $AppDir $FailedDir) -and (Move-Retry $PrevDir $AppDir)) { } else { Log '앱 폴더를 되돌리지 못했습니다' }
				  try {
				    Remove-Tree (Join-Path $DataDir 'data')
				    Move-Item -LiteralPath $BackupDir -Destination (Join-Path $DataDir 'data')
				  }
				  catch { Log "데이터를 되돌리지 못했습니다: $_" }
				  [System.IO.File]::WriteAllText((Join-Path $UpdateDir 'failed-version'), $ToVersion, $Utf8)
				  Write-Result 'rolled-back' "v${ToVersion} 업데이트를 시작하지 못해 v${FromVersion}으로 되돌렸습니다."
				  Start-App $AppDir | Out-Null
				  exit 2
				}
				catch {
				  Log "예상하지 못한 오류: $_"
				  exit 3
				}
				""".formatted(p.oldPid(), ps(p.image().directory()), ps(p.image().staging()), ps(p.image().previous()),
				ps(p.image().failed()), ps(p.image().launcher()), ps(p.dataDir()), ps(p.files().directory()),
				ps(p.fromVersion()), ps(p.toVersion()), p.readyTimeout().toSeconds(), OLD_PROCESS_WAIT_SECONDS);
	}

	/**
	 * Windows에서 스크립트를 앱 프로세스와 분리해 실행한다. WMI(Win32_Process.Create)로 만든 프로세스는 앱이 속한 작업 개체(Job)
	 * 밖에서 돌아서, 앱이 끝나도 함께 끝나지 않는다.
	 */
	public static String windowsLauncher(Path script, Path workingDirectory) {
		String commandLine = "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File \""
				+ script + "\"";
		return """
				# Cozy 업데이트 스크립트 실행기 (앱이 만든 파일)
				$ErrorActionPreference = 'Stop'
				$result = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = %s; CurrentDirectory = %s }
				exit [int]$result.ReturnValue
				""".formatted(ps(commandLine), ps(workingDirectory));
	}

	/** bash 작은따옴표 문자열 */
	static String sh(Object value) {
		return "'" + value.toString().replace("'", "'\\''") + "'";
	}

	/** PowerShell 작은따옴표 문자열 */
	static String ps(Object value) {
		return "'" + value.toString().replace("'", "''") + "'";
	}
}
