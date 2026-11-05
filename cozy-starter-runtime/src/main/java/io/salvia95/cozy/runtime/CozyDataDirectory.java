package io.salvia95.cozy.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 사용자 데이터 루트와 하위 폴더 (PRD §9.1, ADR-0010).
 *
 * <p>앱 폴더 밖에 두어 업데이트로 앱 폴더를 교체해도 데이터가 남는다.
 * Windows는 {@code %LOCALAPPDATA%\{appId}}, 그 외(개발용)는 {@code ~/.local/share/{appId}}이고,
 * {@code cozy.data-dir}로 재정의할 수 있다.
 */
public final class CozyDataDirectory {

	/** 영문 소문자·숫자, 단어 사이에만 하이픈. */
	public static final Pattern APP_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	/** 루트 아래에 항상 만드는 하위 폴더. */
	public static final List<String> SUBDIRECTORIES = List.of("data", "logs", "config", "backup", "update",
			"diagnostics");

	private final Path root;

	private CozyDataDirectory(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	/** 이미 정해진 루트로 만든다. 폴더는 만들지 않는다. */
	public static CozyDataDirectory of(Path root) {
		return new CozyDataDirectory(root);
	}

	/**
	 * 현재 OS와 환경 변수로 루트를 정한다.
	 * @param appId {@code cozy.app.id}
	 * @param explicitDir {@code cozy.data-dir} (없으면 {@code null})
	 */
	public static CozyDataDirectory resolve(String appId, String explicitDir) {
		return resolve(appId, explicitDir, System.getProperty("os.name", ""), System.getenv(),
				System.getProperty("user.home"));
	}

	static CozyDataDirectory resolve(String appId, String explicitDir, String osName, Map<String, String> env,
			String userHome) {
		requireValidAppId(appId);
		if (explicitDir != null && !explicitDir.isBlank()) {
			return new CozyDataDirectory(Path.of(explicitDir));
		}
		if (osName.toLowerCase(Locale.ROOT).startsWith("windows")) {
			String localAppData = env.get("LOCALAPPDATA");
			Path base = (localAppData != null && !localAppData.isBlank()) ? Path.of(localAppData)
					: Path.of(userHome, "AppData", "Local");
			return new CozyDataDirectory(base.resolve(appId));
		}
		return new CozyDataDirectory(Path.of(userHome, ".local", "share", appId));
	}

	/** {@code cozy.app.id} 형식을 검사한다. 어기면 고치는 방법이 담긴 메시지로 실패한다. */
	public static String requireValidAppId(String appId) {
		if (appId == null || appId.isBlank()) {
			throw new IllegalStateException(
					"[cozy] cozy.app.id가 없습니다. application.yml에 'cozy.app.id: my-tool'처럼 영문 소문자·숫자·하이픈으로 지정하세요.");
		}
		if (!APP_ID.matcher(appId).matches()) {
			throw new IllegalStateException("[cozy] cozy.app.id '" + appId
					+ "'는 형식이 맞지 않습니다. 영문 소문자·숫자와 단어 사이 하이픈만 씁니다 (예: voc-assistant).");
		}
		return appId;
	}

	/** 루트와 하위 폴더를 만든다. */
	public CozyDataDirectory createDirectories() {
		try {
			for (String name : SUBDIRECTORIES) {
				Files.createDirectories(this.root.resolve(name));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 데이터 폴더를 만들 수 없습니다: " + this.root, ex);
		}
		return this;
	}

	public Path root() {
		return this.root;
	}

	public Path data() {
		return this.root.resolve("data");
	}

	public Path logs() {
		return this.root.resolve("logs");
	}

	public Path config() {
		return this.root.resolve("config");
	}

	public Path backup() {
		return this.root.resolve("backup");
	}

	public Path update() {
		return this.root.resolve("update");
	}

	public Path diagnostics() {
		return this.root.resolve("diagnostics");
	}

	/** 단일 인스턴스 잠금 파일. */
	public Path lockFile() {
		return this.root.resolve("app.lock");
	}

	/** 실행 중인 인스턴스의 포트 파일. */
	public Path portFile() {
		return this.root.resolve("app.port");
	}

	@Override
	public String toString() {
		return this.root.toString();
	}
}
