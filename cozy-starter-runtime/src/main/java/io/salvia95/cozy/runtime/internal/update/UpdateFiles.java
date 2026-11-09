package io.salvia95.cozy.runtime.internal.update;

import java.nio.file.Path;

/**
 * {@code {dataDir}/update/} 안의 파일. 앱, 교체 스크립트, 테스트가 같은 이름을 쓴다.
 */
public record UpdateFiles(Path directory) {

	/** 새로 뜬 앱이 기동을 마치면 자기 버전을 쓴다. 스크립트가 이 파일로 성공을 판단한다. */
	public Path ready() {
		return this.directory.resolve("ready");
	}

	/** 스크립트가 남기는 결과(status, from, to, message). 다음 기동이 읽고 지운다. */
	public Path result() {
		return this.directory.resolve("result.properties");
	}

	/** 롤백한 버전. 같은 버전을 다시 권하지 않는다. */
	public Path failedVersion() {
		return this.directory.resolve("failed-version");
	}

	/** 스크립트가 실행되자마자 쓴다. 앱은 이 파일을 보고서야 종료한다. */
	public Path scriptStarted() {
		return this.directory.resolve("script-started");
	}

	/** 앱이 스크립트 시작을 확인하지 못하고 설치를 포기했다. 늦게 뜬 스크립트는 이 파일을 보고 아무것도 하지 않는다. */
	public Path cancelled() {
		return this.directory.resolve("cancelled");
	}

	public Path log() {
		return this.directory.resolve("update.log");
	}

	/** 교체 직전 {@code {dataDir}/data}의 복사본. 롤백하면 이것으로 되돌린다. */
	public Path dataBackup() {
		return this.directory.resolve("data-backup");
	}

	public Path script(boolean windows) {
		return this.directory.resolve(windows ? "update.ps1" : "update.sh");
	}

	public Path windowsLauncherScript() {
		return this.directory.resolve("update-launch.ps1");
	}

	public Path download(String packageName) {
		return this.directory.resolve(packageName);
	}
}
