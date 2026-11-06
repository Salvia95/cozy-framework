package io.salvia95.cozy.runtime.internal.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 버전 비교, 매니페스트 형식, app-image 위치 (PRD §9.4).
 */
class VersionsAndManifestTest {

	@Test
	void versionsCompareNumericallyAndReleaseBeatsQualifier() {
		assertThat(Versions.compare("1.10.0", "1.9.9")).isPositive();
		assertThat(Versions.compare("1.1", "1.1.0")).isZero();
		assertThat(Versions.compare("1.1.0-SNAPSHOT", "1.1.0")).isNegative();
		assertThat(Versions.compare("1.1.0", "1.1.0-SNAPSHOT")).isPositive();
		assertThat(Versions.compare("1.1.0-SNAPSHOT", "1.0.9")).isPositive();
		assertThat(Versions.numericPart("1.1.0-SNAPSHOT")).isEqualTo("1.1.0");
		assertThat(Versions.isValid("v1.0")).isFalse();
		assertThatThrownBy(() -> Versions.compare("abc", "1.0")).hasMessageContaining("버전 형식이 아닙니다");
	}

	@Test
	void manifestRoundTripsInPrdFieldOrder() {
		UpdateManifest manifest = new UpdateManifest("voc-assistant", "1.1.0", "1.0.0",
				"voc-assistant-1.1.0-win-x64.zip", "a".repeat(64), "2026-11-20", "수정 사항 한 줄");
		String json = manifest.toJson();
		assertThat(json.indexOf("appId")).isLessThan(json.indexOf("\"version\""));
		assertThat(json.indexOf("minimumVersion")).isLessThan(json.indexOf("\"package\""));
		assertThat(json.indexOf("sha256")).isLessThan(json.indexOf("releasedAt"));
		assertThat(UpdateManifest.parse(json.getBytes(StandardCharsets.UTF_8), "test")).isEqualTo(manifest);
	}

	@Test
	void malformedManifestIsRejectedWithEveryProblem() {
		String json = """
				{"appId": "voc", "version": "latest", "package": "../evil.zip", "sha256": "ABC", "minimumVersion": 1}
				""";
		assertThatThrownBy(() -> UpdateManifest.parse(json.getBytes(StandardCharsets.UTF_8), "share"))
			.hasMessageContaining("[cozy] 업데이트 매니페스트 형식 오류 (share)")
			.hasMessageContaining("version 'latest'")
			.hasMessageContaining("package '../evil.zip'")
			.hasMessageContaining("sha256")
			.hasMessageContaining("minimumVersion는 문자열");
		assertThatThrownBy(() -> UpdateManifest.parse("[]".getBytes(StandardCharsets.UTF_8), "share"))
			.hasMessageContaining("JSON 객체가 아닙니다");
		assertThatThrownBy(() -> UpdateManifest.parse("{".getBytes(StandardCharsets.UTF_8), "share"))
			.hasMessageContaining("JSON이 아닙니다");
	}

	@Test
	void appImageIsFoundFromJpackageLauncherPath() {
		assertThat(AppImage.of("/opt/apps/voc/bin/voc", "Linux"))
			.contains(new AppImage(Path.of("/opt/apps/voc"), Path.of("bin/voc")));
		assertThat(AppImage.of("/opt/apps/voc/voc", "Linux")).isEmpty();
		assertThat(AppImage.of(null, "Linux")).isEqualTo(Optional.empty());
		AppImage image = AppImage.of("/opt/apps/voc/bin/voc", "Linux").orElseThrow();
		assertThat(image.staging()).isEqualTo(Path.of("/opt/apps/voc.new"));
		assertThat(image.previous()).isEqualTo(Path.of("/opt/apps/voc.old"));
		assertThat(image.failed()).isEqualTo(Path.of("/opt/apps/voc.failed"));
	}

	@Test
	void channelAcceptsFileHttpAndPathsOnly() {
		assertThat(UpdateChannel.of("file:///srv/share/voc/voc-update.json").manifestUri().getScheme()).isEqualTo("file");
		assertThat(UpdateChannel.of("https://intra/voc/voc-update.json").manifestUri().getScheme()).isEqualTo("https");
		assertThat(UpdateChannel.of("/srv/share/voc/voc-update.json").manifestUri().getScheme()).isEqualTo("file");
		assertThat(UpdateChannel.of("https://intra/voc/voc-update.json").packageUri("voc-1.1.0-win-x64.zip"))
			.hasToString("https://intra/voc/voc-1.1.0-win-x64.zip");
		assertThatThrownBy(() -> UpdateChannel.of("ftp://intra/voc.json")).hasMessageContaining("file:, http:, https:만");
	}
}
