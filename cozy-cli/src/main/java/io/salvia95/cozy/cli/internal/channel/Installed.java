package io.salvia95.cozy.cli.internal.channel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 이 cozy 명령을 설치한 곳. 번들의 cozy 명령(bash, PowerShell)이 설치 폴더를 환경 변수 {@code COZY_HOME}으로 알려 주고, 판은 그 폴더의
 * {@code COZY_VERSION}에 있다.
 *
 * @param home 설치 폴더(번들 폴더에서 바로 실행하면 번들 폴더)
 * @param version {@code COZY_VERSION}의 내용
 */
public record Installed(Path home, String version) {

	/** 설치 폴더를 알리는 환경 변수 */
	public static final String HOME_ENV = "COZY_HOME";

	public static final String VERSION_FILE = "COZY_VERSION";

	/** MSI로 설치한 폴더에만 있는 표시 파일. 내용은 {@code msi}다 */
	public static final String INSTALLER_FILE = "COZY_INSTALLER";

	/** 설치 폴더를 모르거나 판을 읽을 수 없으면 {@code null} */
	public static Installed find(Map<String, String> env) {
		String home = env.get(HOME_ENV);
		if (home == null || home.isBlank()) {
			return null;
		}
		Path file = Path.of(home).resolve(VERSION_FILE);
		try {
			return Files.isRegularFile(file) ? new Installed(Path.of(home), Files.readString(file).strip()) : null;
		}
		catch (IOException ex) {
			return null;
		}
	}

	/** 릴리스 판으로 설치한 cozy. 설치 폴더를 모르거나 개발 판이면 판단할 수 없으므로 실패한다 */
	public static Installed require(Map<String, String> env) {
		Installed installed = find(env);
		if (installed == null) {
			throw new CliException("지금 판을 알 수 없습니다. 설치한 cozy 명령(설치 폴더의 bin/cozy나 bin\\cozy.cmd, 번들 폴더의 cozy)에서 "
					+ "실행합니다. 그 명령이 설치 폴더를 " + HOME_ENV + "로 알려 줍니다.");
		}
		Version version = installed.release();
		if (version == null) {
			throw new CliException("지금 판(" + installed.version() + ")은 릴리스 판이 아니라 채널과 비교하지 않습니다. 릴리스한 번들이나 "
					+ "MSI로 설치한 cozy 명령에서 실행합니다.");
		}
		return installed;
	}

	/** MSI로 설치했는가 */
	public boolean msi() {
		Path marker = this.home.resolve(INSTALLER_FILE);
		try {
			return Files.isRegularFile(marker) && Files.readString(marker).strip().equals("msi");
		}
		catch (IOException ex) {
			return false;
		}
	}

	/** 번들의 JDK({@code jdk/})를 담아 설치했는가 */
	public boolean bundledJdk() {
		return Files.isDirectory(this.home.resolve("jdk"));
	}

	/** 릴리스 판. 개발 판(SNAPSHOT 등)이면 {@code null} */
	public Version release() {
		try {
			return Version.parse(this.version, VERSION_FILE);
		}
		catch (CliException ex) {
			return null;
		}
	}
}
