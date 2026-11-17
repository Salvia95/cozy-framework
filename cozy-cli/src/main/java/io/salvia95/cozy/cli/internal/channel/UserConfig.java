package io.salvia95.cozy.cli.internal.channel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 설치 폴더 밖의 사용자 설정 파일 {@code cozy.properties}. 업그레이드하거나 지워도 남는다.
 * Windows는 {@code %APPDATA%\Cozy}, 그 밖은 {@code $XDG_CONFIG_HOME/cozy}(없으면 {@code ~/.config/cozy})에 둔다.
 */
public final class UserConfig {

	/** 채널 주소 */
	public static final String CHANNEL = "channel";

	/** 인증 머리 이름 */
	public static final String HEADER = "channel.header";

	/** 인증 토큰. 환경 변수 {@code COZY_CHANNEL_TOKEN}이 앞선다 */
	public static final String TOKEN = "channel.token";

	private UserConfig() {
	}

	/**
	 * @param env 환경 변수
	 * @param windows Windows인가
	 */
	public static Path file(Map<String, String> env, boolean windows) {
		if (windows) {
			String appData = env.get("APPDATA");
			if (appData == null || appData.isBlank()) {
				throw new CliException("환경 변수 APPDATA가 없어 사용자 설정 파일(%APPDATA%\\Cozy\\cozy.properties)의 자리를 알 수 없습니다.");
			}
			return Path.of(appData, "Cozy", "cozy.properties");
		}
		String xdg = env.get("XDG_CONFIG_HOME");
		if (xdg != null && !xdg.isBlank()) {
			return Path.of(xdg, "cozy", "cozy.properties");
		}
		String home = env.get("HOME");
		if (home == null || home.isBlank()) {
			home = System.getProperty("user.home");
		}
		return Path.of(home, ".config", "cozy", "cozy.properties");
	}

	public static Properties read(Path file) {
		Properties properties = new Properties();
		if (Files.exists(file)) {
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				properties.load(in);
			}
			catch (IOException | IllegalArgumentException ex) {
				throw new CliException("사용자 설정 파일 " + file + "을 읽지 못했습니다: " + ex.getMessage());
			}
		}
		return properties;
	}

	/** 같은 폴더의 임시 파일에 쓰고 바꾼다. 쓰다 멈춰도 이전 설정이 남는다 */
	public static void write(Path file, Properties properties) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = Files.createTempFile(file.getParent(), "cozy", ".properties");
			try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				properties.store(out, "cozy 사용자 설정 (cozy channel set이 쓴다)");
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException ex) {
			throw new CliException("사용자 설정 파일 " + file + "에 쓰지 못했습니다: " + ex.getMessage());
		}
	}
}
