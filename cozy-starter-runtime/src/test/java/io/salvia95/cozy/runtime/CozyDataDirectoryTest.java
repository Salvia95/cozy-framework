package io.salvia95.cozy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CozyDataDirectoryTest {

	@Test
	void windowsUsesLocalAppData() {
		var dir = CozyDataDirectory.resolve("voc-assistant", null, "Windows 11",
				Map.of("LOCALAPPDATA", "/c/Users/u/AppData/Local"), "/c/Users/u");
		assertThat(dir.root()).isEqualTo(Path.of("/c/Users/u/AppData/Local/voc-assistant"));
	}

	@Test
	void windowsWithoutLocalAppDataFallsBackToUserHome() {
		var dir = CozyDataDirectory.resolve("voc-assistant", null, "Windows 11", Map.of(), "/c/Users/u");
		assertThat(dir.root()).isEqualTo(Path.of("/c/Users/u/AppData/Local/voc-assistant"));
	}

	@Test
	void otherOsUsesLocalShare() {
		var dir = CozyDataDirectory.resolve("voc-assistant", null, "Linux", Map.of(), "/home/u");
		assertThat(dir.root()).isEqualTo(Path.of("/home/u/.local/share/voc-assistant"));
	}

	@Test
	void explicitDirectoryWins() {
		var dir = CozyDataDirectory.resolve("voc-assistant", "/data/x", "Windows 11",
				Map.of("LOCALAPPDATA", "/c/l"), "/c/Users/u");
		assertThat(dir.root()).isEqualTo(Path.of("/data/x"));
	}

	@Test
	void createsAllSubdirectories(@TempDir Path temp) {
		var dir = CozyDataDirectory.of(temp.resolve("app")).createDirectories();
		assertThat(CozyDataDirectory.SUBDIRECTORIES).allSatisfy((name) -> assertThat(dir.root().resolve(name)).isDirectory());
		assertThat(Files.isDirectory(dir.data())).isTrue();
		assertThat(dir.lockFile()).isEqualTo(dir.root().resolve("app.lock"));
		assertThat(dir.portFile()).isEqualTo(dir.root().resolve("app.port"));
	}

	@Test
	void appIdIsRequired() {
		assertThatThrownBy(() -> CozyDataDirectory.requireValidAppId(null)).hasMessageContaining("cozy.app.id가 없습니다");
		assertThatThrownBy(() -> CozyDataDirectory.requireValidAppId(" ")).hasMessageContaining("cozy.app.id가 없습니다");
	}

	@Test
	void appIdFormat() {
		assertThat(CozyDataDirectory.requireValidAppId("voc-assistant2")).isEqualTo("voc-assistant2");
		for (String bad : new String[] { "Voc", "voc_assistant", "-voc", "voc-", "voc--x", "한글", "voc.app" }) {
			assertThatThrownBy(() -> CozyDataDirectory.requireValidAppId(bad)).as(bad)
				.hasMessageContaining("형식이 맞지 않습니다");
		}
	}
}
