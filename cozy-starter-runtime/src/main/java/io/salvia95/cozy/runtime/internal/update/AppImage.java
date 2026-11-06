package io.salvia95.cozy.runtime.internal.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 실행 중인 jpackage app-image의 위치 (PRD §9.6).
 *
 * <p>jpackage 런처는 {@code jpackage.app-path} 시스템 속성에 런처 파일 경로를 넣는다.
 * Windows는 {@code {앱 폴더}\{이름}.exe}, Linux는 {@code {앱 폴더}/bin/{이름}}이다.
 * 이 속성이 없으면(jar 직접 실행, 개발 중) 설치본이 아니므로 업데이트를 설치할 수 없다.
 *
 * @param directory 앱 폴더. 업데이트가 통째로 바꾼다.
 * @param launcher 앱 폴더 안의 런처 상대 경로
 */
public record AppImage(Path directory, Path launcher) {

	public static final String APP_PATH_PROPERTY = "jpackage.app-path";

	public static Optional<AppImage> current() {
		return of(System.getProperty(APP_PATH_PROPERTY), System.getProperty("os.name", ""));
	}

	static Optional<AppImage> of(@Nullable String appPath, String osName) {
		if (appPath == null || appPath.isBlank()) {
			return Optional.empty();
		}
		Path launcher = Path.of(appPath).toAbsolutePath().normalize();
		Path parent = launcher.getParent();
		if (parent == null) {
			return Optional.empty();
		}
		Path directory;
		if (osName.toLowerCase(Locale.ROOT).startsWith("windows")) {
			directory = parent;
		}
		else if (parent.getFileName() != null && parent.getFileName().toString().equals("bin")
				&& parent.getParent() != null) {
			directory = parent.getParent();
		}
		else {
			return Optional.empty();
		}
		return Optional.of(new AppImage(directory, directory.relativize(launcher)));
	}

	public Path launcherPath() {
		return this.directory.resolve(this.launcher);
	}

	/** 업데이트 중 새 버전을 풀어 두는 폴더. 같은 볼륨이어야 이름 바꾸기로 교체할 수 있어 앱 폴더 옆에 둔다. */
	public Path staging() {
		return sibling(".new");
	}

	/** 교체 직전의 앱 폴더. 다음 업데이트 전까지 남겨 둔다. */
	public Path previous() {
		return sibling(".old");
	}

	/** 준비 신호를 남기지 못한 새 버전. 롤백할 때 이 이름으로 옮겨 둔다. */
	public Path failed() {
		return sibling(".failed");
	}

	/** 앱 폴더를 바꿀 수 있는지: 앱 폴더의 부모에 쓸 수 있어야 한다. */
	public boolean isReplaceable() {
		Path parent = this.directory.getParent();
		return parent != null && Files.isWritable(parent) && Files.isWritable(this.directory);
	}

	private Path sibling(String suffix) {
		return this.directory.resolveSibling(this.directory.getFileName() + suffix);
	}
}
