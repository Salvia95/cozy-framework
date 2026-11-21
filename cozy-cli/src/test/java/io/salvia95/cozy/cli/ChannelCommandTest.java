package io.salvia95.cozy.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업데이트 채널: {@code cozy channel set·show}는 사용자 설정 파일에 채널 주소를 두고, {@code cozy channel release}는 릴리스 하나의
 * 색인을, {@code cozy channel add}는 받은 릴리스를 채널 폴더에 더한다. 판단할 수 없는 것은 아무것도 바꾸지 않고 실패한다.
 */
class ChannelCommandTest {

	@TempDir
	Path temp;

	Map<String, String> env;

	@BeforeEach
	void env() {
		this.env = new HashMap<>();
		this.env.put("XDG_CONFIG_HOME", this.temp.resolve("config").toString());
		this.env.put("HOME", this.temp.resolve("home").toString());
	}

	record Result(int code, String out, String err) {
	}

	Result run(String... args) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.runWith(args, this.temp, this.env, new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	Path config() {
		return this.temp.resolve("config/cozy/cozy.properties");
	}

	@Nested
	class SetAndShow {

		@Test
		void setStoresTheAddressInTheUserConfigAndShowReadsIt() throws IOException {
			Result set = run("channel", "set", "https://gitlab.example/api/v4/projects/42/packages/generic/cozy/channel/cozy-channel.json",
					"--header", "PRIVATE-TOKEN");

			assertThat(set.code()).as(set.err()).isZero();
			assertThat(Files.readString(config()))
				.contains("channel=https\\://gitlab.example/api/v4/projects/42/packages/generic/cozy/channel/cozy-channel.json")
				.contains("channel.header=PRIVATE-TOKEN");
			Result show = run("channel", "show");
			assertThat(show.code()).as(show.err()).isZero();
			assertThat(show.out())
				.contains("채널:   https://gitlab.example/api/v4/projects/42/packages/generic/cozy/channel/cozy-channel.json")
				.contains("출처:   설정 파일 " + config())
				.contains("머리:   PRIVATE-TOKEN")
				.contains("토큰:   없음");
		}

		@Test
		void everyKindOfServerIsAnAddress() {
			for (String address : List.of("\\\\fileserver\\share\\cozy\\cozy-channel.json", "file:///mnt/share/cozy/cozy-channel.json",
					"https://intra.example/cozy/cozy-channel.json", "http://intra.example/cozy/cozy-channel.json",
					"https://github.com/Salvia95/cozy-framework/releases/latest/download/cozy-channel.json",
					ChannelCommandTest.this.temp.resolve("share/cozy-channel.json").toString())) {
				Result set = run("channel", "set", address);
				assertThat(set.code()).as(address + " " + set.err()).isZero();
				assertThat(run("channel", "show").out()).contains("채널:   " + address);
			}
		}

		@Test
		void anAddressThatIsNotAChannelIndexIsRefused() throws IOException {
			for (String address : List.of("ftp://intra.example/cozy/cozy-channel.json", "share/cozy/cozy-channel.json",
					"https://intra.example/cozy/", "https://intra.example/cozy/index.json")) {
				Result set = run("channel", "set", address);
				assertThat(set.code()).as(address).isEqualTo(CozyCli.UNUSABLE);
				assertThat(set.err()).as(address).contains("채널 주소");
			}
			assertThat(config()).doesNotExist();
		}

		@Test
		void theEnvironmentComesBeforeTheConfigFileAndTheTokenIsNeverShown() {
			run("channel", "set", "https://intra.example/cozy/cozy-channel.json");
			ChannelCommandTest.this.env.put("COZY_CHANNEL", "\\\\fileserver\\cozy\\cozy-channel.json");
			ChannelCommandTest.this.env.put("COZY_CHANNEL_TOKEN", "secret-token-value");

			Result show = run("channel", "show");

			assertThat(show.out()).contains("채널:   \\\\fileserver\\cozy\\cozy-channel.json")
				.contains("출처:   환경 변수 COZY_CHANNEL")
				.contains("머리:   Authorization: Bearer")
				.contains("토큰:   있음 (환경 변수 COZY_CHANNEL_TOKEN)")
				.doesNotContain("secret-token-value");
			assertThat(run("channel", "show", "--json").out()).contains("\"address\": \"\\\\\\\\fileserver\\\\cozy\\\\cozy-channel.json\"")
				.contains("\"source\": \"COZY_CHANNEL\"")
				.contains("\"token\": true")
				.doesNotContain("secret-token-value");
		}

		@Test
		void withoutAChannelNothingIsChecked() {
			Result show = run("channel", "show");

			assertThat(show.code()).isZero();
			assertThat(show.out()).contains("채널을 정하지 않았습니다").contains("cozy channel set");
		}

		@Test
		void windowsKeepsTheConfigUnderAppData() {
			ChannelCommandTest.this.env.put("APPDATA", ChannelCommandTest.this.temp.resolve("appdata").toString());
			assertThat(CozyCli.configFile(ChannelCommandTest.this.env, true))
				.isEqualTo(ChannelCommandTest.this.temp.resolve("appdata/Cozy/cozy.properties"));
			ChannelCommandTest.this.env.remove("XDG_CONFIG_HOME");
			assertThat(CozyCli.configFile(ChannelCommandTest.this.env, false))
				.isEqualTo(ChannelCommandTest.this.temp.resolve("home/.config/cozy/cozy.properties"));
		}
	}

	/** 릴리스 폴더: 워크플로가 받은 MSI, 두 번들, SHA256SUMS */
	Path release(String version) throws IOException {
		Path dir = Files.createDirectories(this.temp.resolve("release-" + version));
		List<String> sums = new ArrayList<>();
		for (String name : List.of("cozy-" + version + "-windows-x64.msi", "cozy-offline-bundle-" + version + "-windows-x64.zip",
				"cozy-offline-bundle-" + version + ".zip")) {
			Files.writeString(dir.resolve(name), name + " content");
			sums.add(sha256(dir.resolve(name)) + "  " + name);
		}
		Files.write(dir.resolve("SHA256SUMS"), sums);
		return dir;
	}

	Path notes(String version, String appSteps) throws IOException {
		return Files.writeString(this.temp.resolve("channel-" + version + ".yml"), """
				# 이 릴리스의 채널 색인 내용
				version: %s
				summary: "업데이트 채널과 사용자 저장소"
				appSteps: %s
				""".formatted(version, appSteps));
	}

	static String sha256(Path file) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Nested
	class Release {

		@Test
		void theReleaseIndexListsTheThreeFilesNextToIt() throws IOException {
			Path dir = release("2.1.0");

			Result result = run("channel", "release", dir.toString(), notes("2.1.0", "[\"cozy sync agents\"]").toString());

			assertThat(result.code()).as(result.err()).isZero();
			String index = Files.readString(dir.resolve("cozy-channel.json"));
			assertThat(index).startsWith("{\n  \"schema\": 1,\n  \"releases\": [\n    {\n      \"version\": \"2.1.0\",")
				.contains("\"breaking\": false")
				.contains("\"summary\": \"업데이트 채널과 사용자 저장소\"")
				.contains("\"appSteps\": [\n        \"cozy sync agents\"\n      ]")
				.contains("\"kind\": \"msi\",\n          \"path\": \"cozy-2.1.0-windows-x64.msi\",\n          \"sha256\": \""
						+ sha256(dir.resolve("cozy-2.1.0-windows-x64.msi")) + "\",\n          \"size\": "
						+ Files.size(dir.resolve("cozy-2.1.0-windows-x64.msi")))
				.contains("\"kind\": \"windows-x64\",\n          \"path\": \"cozy-offline-bundle-2.1.0-windows-x64.zip\"")
				.contains("\"kind\": \"portable\",\n          \"path\": \"cozy-offline-bundle-2.1.0.zip\"");
			assertThat(index).containsPattern("\"date\": \"\\d{4}-\\d{2}-\\d{2}\"");
		}

		/** 게시한 이전 릴리스의 색인: 그 릴리스 자산 옆의 이름(바로 앞 판)과 이미 바꾼 경로(그 앞 판들) */
		Path previous(String version, String index) throws IOException {
			Path dir = Files.createDirectories(ChannelCommandTest.this.temp.resolve("previous"));
			return Files.writeString(dir.resolve("v" + version + ".json"), index);
		}

		static String entry(String version, String path, String sha) {
			return """
					{"version": "%s", "date": "2026-11-20", "breaking": false, "summary": "%s 요약", "appSteps": ["cozy sync agents"],
					 "files": [{"kind": "msi", "path": "%s", "sha256": "%s", "size": 10}]}
					""".formatted(version, version, path, sha);
		}

		@Test
		void theReleaseIndexCarriesThePublishedReleasesWithPathsToTheirOwnAssets() throws IOException {
			Path dir = release("2.4.0");
			previous("2.3.0", "{\"schema\": 1, \"releases\": [" + entry("2.3.0", "cozy-2.3.0-windows-x64.msi", "b".repeat(64)) + ","
					+ entry("2.2.0", "../../download/v2.2.0/cozy-2.2.0-windows-x64.msi", "c".repeat(64)) + "]}");
			Path previous = previous("2.2.0", "{\"schema\": 1, \"releases\": [" + entry("2.2.0", "cozy-2.2.0-windows-x64.msi",
					"c".repeat(64)) + "]}").getParent();

			Result result = run("channel", "release", dir.toString(), notes("2.4.0", "[]").toString(), "--previous", previous.toString());

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("이전 릴리스 2개");
			io.salvia95.cozy.cli.internal.channel.ChannelIndex index = io.salvia95.cozy.cli.internal.channel.ChannelIndex
				.read(dir.resolve("cozy-channel.json"));
			assertThat(index.releases()).extracting((r) -> r.version().toString()).containsExactly("2.4.0", "2.3.0", "2.2.0");
			assertThat(index.releases().get(0).files()).extracting((f) -> f.path()).contains("cozy-2.4.0-windows-x64.msi");
			assertThat(index.releases().get(1).files()).extracting((f) -> f.path())
				.containsExactly("../../download/v2.3.0/cozy-2.3.0-windows-x64.msi");
			assertThat(index.releases().get(2).files()).extracting((f) -> f.path())
				.containsExactly("../../download/v2.2.0/cozy-2.2.0-windows-x64.msi");
			assertThat(index.releases().get(2).appSteps()).containsExactly("cozy sync agents");
		}

		@Test
		void previousIndexesThatDisagreeAboutAReleaseAreRefused() throws IOException {
			Path dir = release("2.4.0");
			previous("2.3.0", "{\"schema\": 1, \"releases\": [" + entry("2.2.0", "x.msi", "b".repeat(64)) + "]}");
			Path previous = previous("2.2.0", "{\"schema\": 1, \"releases\": [" + entry("2.2.0", "x.msi", "c".repeat(64)) + "]}")
				.getParent();

			Result result = run("channel", "release", dir.toString(), notes("2.4.0", "[]").toString(), "--previous", previous.toString());

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("2.2.0").contains("서로 다르게 적었습니다");
			assertThat(dir.resolve("cozy-channel.json")).doesNotExist();
		}

		@Test
		void aMissingPreviousFolderIsRefused() throws IOException {
			Path dir = release("2.4.0");

			Result result = run("channel", "release", dir.toString(), notes("2.4.0", "[]").toString(), "--previous",
					ChannelCommandTest.this.temp.resolve("none").toString());

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("--previous");
		}

		@Test
		void aMajorVersionBreaksApps() throws IOException {
			Path dir = release("3.0.0");

			assertThat(run("channel", "release", dir.toString(), notes("3.0.0", "[\"cozy migrate something\"]").toString()).code())
				.isZero();
			assertThat(Files.readString(dir.resolve("cozy-channel.json"))).contains("\"breaking\": true");
		}

		@Test
		void notesForAnotherVersionAreRefused() throws IOException {
			Path dir = release("2.2.0");

			Result result = run("channel", "release", dir.toString(), notes("2.1.0", "[]").toString());

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("version: 2.1.0").contains("2.2.0");
			assertThat(dir.resolve("cozy-channel.json")).doesNotExist();
		}

		@Test
		void aFileThatDoesNotMatchSha256SumsIsRefused() throws IOException {
			Path dir = release("2.1.0");
			Files.writeString(dir.resolve("cozy-offline-bundle-2.1.0.zip"), "broken");

			Result result = run("channel", "release", dir.toString(), notes("2.1.0", "[]").toString());

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("cozy-offline-bundle-2.1.0.zip").contains("SHA256SUMS");
			assertThat(dir.resolve("cozy-channel.json")).doesNotExist();
		}

		@Test
		void aMissingFileOrASnapshotIsRefused() throws IOException {
			Path dir = release("2.1.0");
			Files.delete(dir.resolve("cozy-2.1.0-windows-x64.msi"));
			Result missing = run("channel", "release", dir.toString(), notes("2.1.0", "[]").toString());
			assertThat(missing.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(missing.err()).contains("cozy-2.1.0-windows-x64.msi");

			Path snapshot = release("2.1.0-SNAPSHOT");
			Result result = run("channel", "release", snapshot.toString(), notes("2.1.0-SNAPSHOT", "[]").toString());
			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("2.1.0-SNAPSHOT");
		}
	}

	@Nested
	class Add {

		Path released(String version) throws IOException {
			Path dir = release(version);
			Result result = run("channel", "release", dir.toString(), notes(version, "[]").toString());
			assertThat(result.code()).as(result.err()).isZero();
			return dir;
		}

		@Test
		void aReleaseIndexWithEarlierReleasesAddsOnlyItsOwnRelease() throws IOException {
			Path channel = ChannelCommandTest.this.temp.resolve("channel");
			Path dir = release("2.4.0");
			Path previous = Files.createDirectories(ChannelCommandTest.this.temp.resolve("previous"));
			Files.writeString(previous.resolve("v2.3.0.json"), "{\"schema\": 1, \"releases\": [{\"version\": \"2.3.0\", \"date\": "
					+ "\"2026-11-20\", \"breaking\": false, \"summary\": \"앞 판\", \"appSteps\": [], \"files\": [{\"kind\": \"msi\", "
					+ "\"path\": \"cozy-2.3.0-windows-x64.msi\", \"sha256\": \"" + "b".repeat(64) + "\", \"size\": 10}]}]}");
			assertThat(run("channel", "release", dir.toString(), notes("2.4.0", "[]").toString(), "--previous", previous.toString())
				.code()).isZero();

			Result result = run("channel", "add", channel.toString(), dir.toString());

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("판(2.4.0)을 채널에 더했습니다").contains("이전 릴리스 1개(2.3.0)는 더하지 않았습니다");
			String index = Files.readString(channel.resolve("index/cozy-channel.json"));
			assertThat(index).contains("\"version\": \"2.4.0\"").doesNotContain("2.3.0");
			assertThat(channel.resolve("2.3.0")).doesNotExist();
		}

		@Test
		void aReleaseIsCopiedUnderItsVersionAndTheIndexIsWrittenLast() throws IOException {
			Path channel = ChannelCommandTest.this.temp.resolve("channel");
			Path release = released("2.1.0");

			Result result = run("channel", "add", channel.toString(), release.toString());

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("판(2.1.0)을 채널에 더했습니다");
			assertThat(channel.resolve("2.1.0/cozy-2.1.0-windows-x64.msi")).hasSameBinaryContentAs(release.resolve("cozy-2.1.0-windows-x64.msi"));
			assertThat(channel.resolve("2.1.0/cozy-offline-bundle-2.1.0.zip")).exists();
			assertThat(channel.resolve("2.1.0/cozy-offline-bundle-2.1.0-windows-x64.zip")).exists();
			assertThat(Files.readString(channel.resolve("index/cozy-channel.json")))
				.contains("\"path\": \"../2.1.0/cozy-2.1.0-windows-x64.msi\"")
				.contains("\"sha256\": \"" + sha256(release.resolve("cozy-2.1.0-windows-x64.msi")) + "\"");
		}

		@Test
		void releasesAreKeptNewestFirstAndAddingTheSameOneAgainChangesNothing() throws IOException {
			Path channel = ChannelCommandTest.this.temp.resolve("channel");
			assertThat(run("channel", "add", channel.toString(), released("2.1.0").toString()).code()).isZero();
			assertThat(run("channel", "add", channel.toString(), released("2.10.0").toString()).code()).isZero();
			assertThat(run("channel", "add", channel.toString(), released("2.2.0").toString()).code()).isZero();
			String index = Files.readString(channel.resolve("index/cozy-channel.json"));

			Result again = run("channel", "add", channel.toString(), ChannelCommandTest.this.temp.resolve("release-2.2.0").toString());

			assertThat(again.code()).as(again.err()).isZero();
			assertThat(again.out()).contains("이 판(2.2.0)은 이미 채널에 있습니다");
			assertThat(Files.readString(channel.resolve("index/cozy-channel.json"))).isEqualTo(index);
			assertThat(index.indexOf("\"version\": \"2.10.0\"")).isLessThan(index.indexOf("\"version\": \"2.2.0\""));
			assertThat(index.indexOf("\"version\": \"2.2.0\"")).isLessThan(index.indexOf("\"version\": \"2.1.0\""));
		}

		@Test
		void aDifferentReleaseWithAVersionAlreadyInTheChannelIsRefused() throws IOException {
			Path channel = ChannelCommandTest.this.temp.resolve("channel");
			assertThat(run("channel", "add", channel.toString(), released("2.1.0").toString()).code()).isZero();
			String index = Files.readString(channel.resolve("index/cozy-channel.json"));
			Path other = ChannelCommandTest.this.temp.resolve("other");
			Files.createDirectories(other);
			for (String name : List.of("cozy-2.1.0-windows-x64.msi", "cozy-offline-bundle-2.1.0-windows-x64.zip",
					"cozy-offline-bundle-2.1.0.zip", "SHA256SUMS")) {
				Files.copy(ChannelCommandTest.this.temp.resolve("release-2.1.0/" + name), other.resolve(name));
			}
			Files.writeString(other.resolve("cozy-2.1.0-windows-x64.msi"), "rebuilt");
			Files.writeString(other.resolve("SHA256SUMS"), Files.readString(other.resolve("SHA256SUMS"))
				.replace(sha256(ChannelCommandTest.this.temp.resolve("release-2.1.0/cozy-2.1.0-windows-x64.msi")),
						sha256(other.resolve("cozy-2.1.0-windows-x64.msi"))));
			assertThat(run("channel", "release", other.toString(), notes("2.1.0", "[]").toString()).code()).isZero();

			Result result = run("channel", "add", channel.toString(), other.toString());

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("2.1.0").contains("다른 파일");
			assertThat(Files.readString(channel.resolve("index/cozy-channel.json"))).isEqualTo(index);
			assertThat(channel.resolve("2.1.0/cozy-2.1.0-windows-x64.msi")).hasContent("cozy-2.1.0-windows-x64.msi content");
		}

		@Test
		void aBrokenFileIsRefusedBeforeAnythingIsCopied() throws IOException {
			Path channel = ChannelCommandTest.this.temp.resolve("channel");
			Path release = released("2.1.0");
			Files.writeString(release.resolve("cozy-offline-bundle-2.1.0.zip"), "broken in transit");

			Result result = run("channel", "add", channel.toString(), release.toString());

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("cozy-offline-bundle-2.1.0.zip").contains("SHA-256");
			assertThat(channel).doesNotExist();
		}

		@Test
		void aReleaseWithoutItsIndexIsRefused() throws IOException {
			Result result = run("channel", "add", ChannelCommandTest.this.temp.resolve("channel").toString(), release("2.0.0").toString());

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("cozy-channel.json");
		}

		@Test
		void anIndexThatCannotBeReadIsNotOverwritten() throws IOException {
			Path channel = ChannelCommandTest.this.temp.resolve("channel");
			Files.createDirectories(channel.resolve("index"));
			for (String index : List.of("{\"schema\": 2, \"releases\": []}", "{\"schema\": 1}",
					"{\"schema\": 1, \"releases\": [{\"version\": \"2.1.0\"}]}", "not json: [")) {
				Files.writeString(channel.resolve("index/cozy-channel.json"), index);

				Result result = run("channel", "add", channel.toString(), released("2.1.0").toString());

				assertThat(result.code()).as(index).isEqualTo(CozyCli.UNUSABLE);
				assertThat(result.err()).as(index).contains("cozy-channel.json");
				assertThat(channel.resolve("index/cozy-channel.json")).as(index).hasContent(index);
				assertThat(channel.resolve("2.1.0")).as(index).doesNotExist();
			}
		}

		@Test
		void dryRunOnlyListsWhatWouldBeCopied() throws IOException {
			Path channel = ChannelCommandTest.this.temp.resolve("channel");

			Result result = run("channel", "add", channel.toString(), released("2.1.0").toString(), "--dry-run");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("복사 2.1.0/cozy-2.1.0-windows-x64.msi").contains("index/cozy-channel.json");
			assertThat(channel).doesNotExist();
		}
	}
}
