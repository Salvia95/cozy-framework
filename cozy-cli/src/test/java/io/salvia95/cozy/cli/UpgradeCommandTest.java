package io.salvia95.cozy.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.channel.ChannelClient;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.ChannelFile;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.Release;
import io.salvia95.cozy.cli.internal.channel.UpgradeCommands;
import io.salvia95.cozy.cli.internal.channel.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code cozy upgrade}: 채널에서 판을 받아 크기와 SHA-256을 확인하고, 설치는 cozy가 끝난 뒤에 도는 스크립트에 맡긴다. 다음 cozy 명령이
 * 결과를 보인다. 호환이 깨지는 판은 {@code --to}로 적어야 하고, 낮은 판으로는 가지 않는다. 설치 스크립트는 띄우지 않고 받은 명령만 적어
 * 두었다가, 필요한 테스트에서 직접 돌린다.
 */
class UpgradeCommandTest {

	static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

	@TempDir
	Path temp;

	Map<String, String> env;

	Path home;

	ChannelIndex index = ChannelIndex.empty();

	final List<List<String>> launched = new CopyOnWriteArrayList<>();

	final List<HttpServer> servers = new ArrayList<>();

	final ChannelClient client = new ChannelClient(ChannelClient.trust(List.of()), Duration.ofSeconds(5), Duration.ofSeconds(5));

	@BeforeEach
	void env() throws IOException {
		this.env = new HashMap<>();
		this.env.put("XDG_CONFIG_HOME", this.temp.resolve("config").toString());
		this.env.put("HOME", this.temp.resolve("home").toString());
		this.home = Files.createDirectories(this.temp.resolve("install"));
		Files.writeString(this.home.resolve("COZY_VERSION"), "2.1.0\n");
		write(this.home.resolve("repository/io/salvia95/cozy/cozy-ui/2.1.0/cozy-ui-2.1.0.jar"), "ui 2.1.0");
		this.env.put("COZY_HOME", this.home.toString());
		this.env.put("COZY_CHANNEL", this.temp.resolve("channel/index/cozy-channel.json").toString());
	}

	@AfterEach
	void stop() {
		this.servers.forEach((server) -> server.stop(0));
	}

	record Result(String out, String err) {
	}

	/** cozy upgrade. 설치 스크립트는 띄우지 않고 명령만 적는다. 끝난 프로세스를 기다리게 한다 */
	String upgrade(String... args) {
		return upgrade(false, args);
	}

	String upgrade(boolean windows, String... args) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		UpgradeCommands.upgrade(List.of(args), this.env, windows, NOW, this.client, this.launched::add, List.of(finishedPid()),
				new PrintStream(out, true, StandardCharsets.UTF_8));
		return out.toString(StandardCharsets.UTF_8);
	}

	/** 다음 cozy 명령이 처음에 보이는 설치 결과 */
	String report(Instant now) {
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		UpgradeCommands.report(this.env, false, now, new PrintStream(err, true, StandardCharsets.UTF_8));
		return err.toString(StandardCharsets.UTF_8);
	}

	static long finishedPid() {
		try {
			Process process = new ProcessBuilder("true").start();
			process.waitFor(10, TimeUnit.SECONDS);
			return process.pid();
		}
		catch (IOException ex) {
			// Windows에는 true가 없다. 이 프로세스의 부모가 아닌 큰 수로 대신한다
			return Long.MAX_VALUE - 1;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	Path work(String version) {
		return this.temp.resolve("home/.local/share/cozy/upgrade").resolve(version);
	}

	Path record() {
		return this.temp.resolve("config/cozy/upgrade.properties");
	}

	/**
	 * 가짜 번들 zip. {@code cozy install <폴더>}는 {@code code}가 0이면 그 폴더의 판을 바꾸고, 아니면 그 코드로 끝난다.
	 *
	 * @param inside 번들 안의 COZY_VERSION
	 */
	Path bundle(String version, String inside, int code) throws IOException {
		Map<String, String> files = new LinkedHashMap<>();
		String root = "cozy-offline-bundle-" + version + "/";
		files.put(root + "COZY_VERSION", inside + "\n");
		files.put(root + "cozy", """
				#!/usr/bin/env bash
				code=%d
				if [ "$1" = install ]; then
				  echo "installed $2"
				  if [ "$code" -ne 0 ]; then exit "$code"; fi
				  printf '%%s\\n' %s > "$2/COZY_VERSION"
				fi
				""".formatted(code, inside));
		files.put(root + "cozy.ps1", "# cozy");
		files.put(root + "repository/io/salvia95/cozy/cozy-ui/" + version + "/cozy-ui-" + version + ".jar", "ui " + version);
		return zip(this.temp.resolve("build/cozy-offline-bundle-" + version + ".zip"), files);
	}

	static Path zip(Path file, Map<String, String> entries) throws IOException {
		Files.createDirectories(file.getParent());
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				out.putNextEntry(new ZipEntry(entry.getKey()));
				out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return file;
	}

	/** 채널 폴더에 판을 더한다: {@code {판}/파일}과 {@code index/cozy-channel.json}. 크기와 SHA-256은 파일에서 잰다 */
	void publish(String version, String summary, List<String> appSteps, Map<String, Path> files) throws IOException {
		Path channel = this.temp.resolve("channel");
		List<ChannelFile> entries = new ArrayList<>();
		for (Map.Entry<String, Path> file : files.entrySet()) {
			Path target = Files.createDirectories(channel.resolve(version)).resolve(file.getValue().getFileName());
			Files.copy(file.getValue(), target);
			entries.add(new ChannelFile(file.getKey(), "../" + version + "/" + target.getFileName(), ChannelIndex.sha256(target),
					Files.size(target)));
		}
		Version parsed = Version.parse(version, "test");
		this.index = this.index.with(new Release(parsed, LocalDate.parse("2026-12-01"), parsed.breaking(), summary, appSteps,
				entries));
		write(channel.resolve("index/cozy-channel.json"), this.index.toJson());
	}

	void publishBundle(String version, String summary, String... appSteps) throws IOException {
		publish(version, summary, List.of(appSteps), Map.of("portable", bundle(version, version, 0)));
	}

	/** 맡긴 설치 스크립트를 직접 돌린다(cozy는 이미 끝났다) */
	int runScript(String version) throws Exception {
		Process process = new ProcessBuilder("bash", work(version).resolve("install.sh").toString()).redirectErrorStream(true).start();
		process.getInputStream().readAllBytes();
		assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
		return process.exitValue();
	}

	@Nested
	@DisabledOnOs(value = OS.WINDOWS, disabledReason = "zip 설치 스크립트는 bash다. Windows 스크립트는 WindowsInstalls가 내용으로 본다")
	class FromAZipInstall {

		@Test
		void theReleaseIsDownloadedCheckedUnpackedAndInstalledByAScriptAfterCozyEnds() throws Exception {
			publishBundle("2.1.1", "고친 판", "cozy sync agents");

			String out = upgrade();

			assertThat(out).contains("지금 판:  2.1.0", "올릴 판:  2.1.1", "  2.1.1 (2026-12-01)  고친 판",
					"[cozy] 받았습니다. 크기와 SHA-256이 채널 색인과 같습니다: " + work("2.1.1").resolve("cozy-offline-bundle-2.1.1.zip"),
					"[cozy] cozy 명령이 끝나면 받은 번들의 cozy install로 다시 설치합니다(" + UpgradeCommandTest.this.home + ")",
					"[cozy] 기록: " + work("2.1.1").resolve("install.log"),
					"[cozy] 앱을 이 판으로 올릴 때 앱 폴더에서 돌릴 명령: cozy sync agents");
			assertThat(UpgradeCommandTest.this.launched).containsExactly(List.of("bash", work("2.1.1").resolve("install.sh").toString()));
			// cozy가 끝나기 전에는 설치 폴더를 바꾸지 않는다
			assertThat(UpgradeCommandTest.this.home.resolve("COZY_VERSION")).hasContent("2.1.0");
			assertThat(work("2.1.1").resolve("bundle/cozy-offline-bundle-2.1.1/COZY_VERSION")).hasContent("2.1.1");
			assertThat(Files.readString(work("2.1.1").resolve("install.sh")))
				.contains(" install '" + UpgradeCommandTest.this.home + "'")
				.contains("while kill -0");

			assertThat(runScript("2.1.1")).isZero();

			assertThat(UpgradeCommandTest.this.home.resolve("COZY_VERSION")).hasContent("2.1.1");
			assertThat(work("2.1.1").resolve("install.log")).content().contains("installed " + UpgradeCommandTest.this.home);
			// 성공하면 받은 파일과 푼 번들을 지운다
			assertThat(work("2.1.1").resolve("cozy-offline-bundle-2.1.1.zip")).doesNotExist();
			assertThat(work("2.1.1").resolve("bundle")).doesNotExist();
			assertThat(report(NOW)).isEqualTo("[cozy] 판을 올렸습니다: 2.1.0 → 2.1.1.\n");
			assertThat(work("2.1.1")).doesNotExist();
			assertThat(record()).doesNotExist();
			// 결과는 한 번만 보인다
			assertThat(report(NOW)).isEmpty();
		}

		@Test
		void aFailedInstallIsReportedOnceAndTheNextUpgradeReusesTheDownload() throws Exception {
			publish("2.1.1", "고친 판", List.of(), Map.of("portable", bundle("2.1.1", "2.1.1", 3)));
			upgrade();

			assertThat(runScript("2.1.1")).isZero();

			Path download = work("2.1.1").resolve("cozy-offline-bundle-2.1.1.zip");
			assertThat(report(NOW)).contains("[cozy] 판(2.1.1)을 설치하지 못했습니다(종료 코드 3). 기록: " + work("2.1.1").resolve("install.log"))
				.contains("손으로 설치할 수도 있습니다: " + download);
			assertThat(download).isRegularFile();
			assertThat(record()).doesNotExist();
			assertThat(upgrade()).contains("[cozy] 전에 받은 파일을 씁니다. SHA-256이 채널 색인과 같습니다: " + download)
				.doesNotContain("받는 중");
		}

		@Test
		void whileTheScriptHasNotFinishedAnotherUpgradeIsRefused() throws Exception {
			publishBundle("2.1.1", "고친 판");
			upgrade();

			assertThat(report(NOW.plusSeconds(30))).isEqualTo("[cozy] 판(2.1.1)을 설치하는 중입니다. 기록: "
					+ work("2.1.1").resolve("install.log") + "\n");
			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class).hasMessageContaining("판(2.1.1)을 설치하는 중입니다");
			// 스크립트가 시작하지 않은 채 시간이 지나면 시작하지 못한 것으로 알린다
			assertThat(report(NOW.plus(Duration.ofMinutes(10)))).contains("설치 스크립트가 결과를 남기지 않고 끝났습니다");
			assertThat(record()).doesNotExist();
		}

		@Test
		void theScriptWaitsForCozyToEnd() throws Exception {
			publishBundle("2.1.1", "고친 판");
			Process cozy = new ProcessBuilder("sleep", "2").start();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			UpgradeCommands.upgrade(List.of(), UpgradeCommandTest.this.env, false, NOW, UpgradeCommandTest.this.client,
					UpgradeCommandTest.this.launched::add, List.of(cozy.pid()), new PrintStream(out, true, StandardCharsets.UTF_8));
			long started = System.nanoTime();

			assertThat(runScript("2.1.1")).isZero();

			assertThat(cozy.isAlive()).isFalse();
			assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(Duration.ofMillis(1000));
			assertThat(UpgradeCommandTest.this.home.resolve("COZY_VERSION")).hasContent("2.1.1");
		}

		@Test
		void aBundleOfAnotherVersionIsNotInstalled() throws Exception {
			publish("2.1.1", "고친 판", List.of(), Map.of("portable", bundle("2.1.1", "2.1.2", 0)));

			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class)
				.hasMessageContaining("받은 번들의 판(2.1.2)이 채널 색인의 판(2.1.1)과 다릅니다");
			assertThat(UpgradeCommandTest.this.launched).isEmpty();
			assertThat(record()).doesNotExist();
		}
	}

	@Nested
	class ChoosingTheVersion {

		@Test
		void theNewestReleaseOfTheSameMajorIsChosenAndABreakingOneIsMentioned() throws Exception {
			publishBundle("2.1.1", "고친 판");
			publishBundle("2.2.0", "새 기능");
			publishBundle("3.0.0", "새 주 버전", "cozy migrate events");

			String out = upgrade();

			assertThat(out).contains("올릴 판:  2.2.0", "  2.2.0 (2026-12-01)  새 기능", "  2.1.1 (2026-12-01)  고친 판")
				.contains("[cozy] 호환이 깨지는 판(3.0.0)도 있습니다. 앱에서 돌릴 명령은 cozy update로 봅니다.")
				.doesNotContain("새 주 버전");
		}

		@Test
		void onlyABreakingReleaseStopsAndShowsWhatTheAppsNeed() throws Exception {
			publishBundle("3.0.0", "새 주 버전", "cozy migrate events");
			ByteArrayOutputStream out = new ByteArrayOutputStream();

			assertThatThrownBy(() -> UpgradeCommands.upgrade(List.of(), UpgradeCommandTest.this.env, false, NOW,
					UpgradeCommandTest.this.client, UpgradeCommandTest.this.launched::add, List.of(),
					new PrintStream(out, true, StandardCharsets.UTF_8)))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("cozy upgrade --to 3.0.0");

			assertThat(out.toString(StandardCharsets.UTF_8)).contains("  3.0.0 (2026-12-01)  [호환 깨짐] 새 주 버전",
					"      앱에서 돌릴 명령: cozy migrate events");
			assertThat(UpgradeCommandTest.this.launched).isEmpty();
			assertThat(work("3.0.0")).doesNotExist();
		}

		@Test
		@DisabledOnOs(value = OS.WINDOWS, disabledReason = "zip을 unzip으로 푼다")
		void aBreakingReleaseIsInstalledWhenItsVersionIsWritten() throws Exception {
			publishBundle("3.0.0", "새 주 버전", "cozy migrate events");

			String out = upgrade("--to", "3.0.0");

			assertThat(out).contains("올릴 판:  3.0.0", "[cozy] 앱을 이 판으로 올릴 때 앱 폴더에서 돌릴 명령: cozy migrate events");
			assertThat(UpgradeCommandTest.this.launched).hasSize(1);
		}

		@Test
		void aLowerVersionIsNeverInstalled() throws Exception {
			publishBundle("2.0.5", "옛 판");

			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class)
				.hasMessageContaining("채널의 최신 판(2.0.5)이 지금 판(2.1.0)보다 낮습니다. 낮은 판으로는 가지 않습니다");
			assertThatThrownBy(() -> upgrade("--to", "2.0.5")).isInstanceOf(CliException.class)
				.hasMessageContaining("낮은 판으로는 가지 않습니다(지금 판 2.1.0, 적은 판 2.0.5)");
			assertThat(UpgradeCommandTest.this.launched).isEmpty();
		}

		@Test
		void theCurrentVersionAndAnUpToDateInstallChangeNothing() throws Exception {
			publishBundle("2.1.0", "지금 판");

			assertThat(upgrade()).isEqualTo("[cozy] 최신 판입니다.\n");
			assertThat(upgrade("--to", "2.1.0")).isEqualTo("[cozy] 지금 판입니다: 2.1.0.\n");
			assertThat(UpgradeCommandTest.this.launched).isEmpty();
		}

		@Test
		void aVersionTheChannelDoesNotHaveIsRefused() throws Exception {
			publishBundle("2.1.1", "고친 판");

			assertThatThrownBy(() -> upgrade("--to", "2.5.0")).isInstanceOf(CliException.class)
				.hasMessageContaining("채널에 판(2.5.0)이 없습니다");
			assertThatThrownBy(() -> upgrade("2.5.0")).isInstanceOf(CliException.class)
				.hasMessageContaining("cozy upgrade [--to <판>]로 씁니다");
		}

		@Test
		void machineOutputAndDryRunAreNotOffered() {
			ByteArrayOutputStream err = new ByteArrayOutputStream();

			int code = CozyCli.runWith(new String[] { "upgrade", "--dry-run" }, UpgradeCommandTest.this.temp,
					UpgradeCommandTest.this.env, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true,
							StandardCharsets.UTF_8));

			assertThat(code).isEqualTo(CozyCli.UNUSABLE);
			assertThat(err.toString(StandardCharsets.UTF_8)).contains("cozy upgrade에는 --dry-run이 없습니다");
		}
	}

	@Nested
	class TheDownload {

		@Test
		void aFileThatDoesNotMatchTheIndexIsNotKept() throws Exception {
			publishBundle("2.1.1", "고친 판");
			Path published = UpgradeCommandTest.this.temp.resolve("channel/2.1.1/cozy-offline-bundle-2.1.1.zip");
			byte[] bytes = Files.readAllBytes(published);
			bytes[bytes.length / 2] ^= 1;
			Files.write(published, bytes);

			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class)
				.hasMessageContaining("받은 cozy-offline-bundle-2.1.1.zip의 SHA-256이 채널 색인과 다릅니다");

			assertThat(work("2.1.1")).isEmptyDirectory();
			assertThat(UpgradeCommandTest.this.launched).isEmpty();
			assertThat(record()).doesNotExist();
		}

		@Test
		void aFileOfAnotherSizeIsNotKept() throws Exception {
			publishBundle("2.1.1", "고친 판");
			Files.writeString(UpgradeCommandTest.this.temp.resolve("channel/2.1.1/cozy-offline-bundle-2.1.1.zip"), "short",
					java.nio.file.StandardOpenOption.APPEND);

			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class)
				.hasMessageContaining("채널 색인에 적힌 크기");
			assertThat(work("2.1.1")).isEmptyDirectory();
		}

		@Test
		@DisabledOnOs(value = OS.WINDOWS, disabledReason = "zip을 unzip으로 푼다")
		void aWebChannelSendsTheTokenForTheFileToo() throws Exception {
			publishBundle("2.1.1", "고친 판");
			Path channel = UpgradeCommandTest.this.temp.resolve("channel");
			List<String> tokens = new CopyOnWriteArrayList<>();
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/", (exchange) -> {
				try (exchange) {
					tokens.add(exchange.getRequestURI().getPath() + " " + exchange.getRequestHeaders().getFirst("Authorization"));
					Path file = channel.resolve(exchange.getRequestURI().getPath().substring("/cozy/".length()));
					byte[] body = Files.readAllBytes(file);
					exchange.sendResponseHeaders(200, body.length);
					try (OutputStream out = exchange.getResponseBody()) {
						out.write(body);
					}
				}
			});
			server.start();
			UpgradeCommandTest.this.servers.add(server);
			UpgradeCommandTest.this.env.put("COZY_CHANNEL",
					"http://127.0.0.1:" + server.getAddress().getPort() + "/cozy/index/cozy-channel.json");
			UpgradeCommandTest.this.env.put("COZY_CHANNEL_TOKEN", "secret-token");

			String out = upgrade();

			assertThat(out).contains("받았습니다").doesNotContain("secret-token");
			assertThat(tokens).containsExactly("/cozy/index/cozy-channel.json Bearer secret-token",
					"/cozy/2.1.1/cozy-offline-bundle-2.1.1.zip Bearer secret-token");
		}

		@Test
		void aReleaseWithoutAFileForThisInstallIsRefused() throws Exception {
			publish("2.1.1", "고친 판", List.of(), Map.of("msi", write(UpgradeCommandTest.this.temp.resolve("build/cozy.msi"), "msi")));

			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class)
				.hasMessageContaining("채널의 판(2.1.1)에 이 설치에 맞는 파일(portable)이 없습니다. 있는 파일: msi");
		}

		@Test
		void anInstallWithABundledJdkIsNotReplacedByOneWithout() throws Exception {
			Files.createDirectories(UpgradeCommandTest.this.home.resolve("jdk/bin"));
			publishBundle("2.1.1", "고친 판");

			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class)
				.hasMessageContaining("JDK를 담은 번들입니다");
		}

		@Test
		void withoutAChannelOrARelease() throws IOException {
			UpgradeCommandTest.this.env.remove("COZY_CHANNEL");
			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class).hasMessageContaining("채널을 정하지 않았습니다");

			UpgradeCommandTest.this.env.put("COZY_CHANNEL", "/nowhere/cozy-channel.json");
			Files.writeString(UpgradeCommandTest.this.home.resolve("COZY_VERSION"), "2.2.0-SNAPSHOT\n");
			assertThatThrownBy(() -> upgrade()).isInstanceOf(CliException.class).hasMessageContaining("릴리스 판이 아니라");
		}
	}

	/** Windows의 설치 스크립트는 PowerShell이다. Linux에서는 만든 스크립트의 내용과 MSI 앞의 저장소 뜨기를 본다 */
	@Nested
	class WindowsInstalls {

		@BeforeEach
		void windows() {
			UpgradeCommandTest.this.env.put("APPDATA", UpgradeCommandTest.this.temp.resolve("appdata").toString());
			UpgradeCommandTest.this.env.put("LOCALAPPDATA", UpgradeCommandTest.this.temp.resolve("local").toString());
			UpgradeCommandTest.this.env.remove("XDG_CONFIG_HOME");
		}

		Path work(String version) {
			return UpgradeCommandTest.this.temp.resolve("local/Cozy/upgrade").resolve(version);
		}

		@Test
		void anMsiInstallGetsTheMsiAndTheOldRepositoryIsKeptAsHardLinksForTheMove() throws Exception {
			Files.writeString(UpgradeCommandTest.this.home.resolve("COZY_INSTALLER"), "msi\n");
			write(UpgradeCommandTest.this.home.resolve("repository/.locks/artifact.lock"), "");
			Path msi = write(UpgradeCommandTest.this.temp.resolve("build/cozy-2.1.1-windows-x64.msi"), "msi 2.1.1");
			publish("2.1.1", "고친 판", List.of(), Map.of("msi", msi, "portable", bundle("2.1.1", "2.1.1", 0)));

			String out = upgrade(true);

			Path script = work("2.1.1").resolve("install.ps1");
			assertThat(out).contains("[cozy] cozy 명령이 끝나면 받은 MSI를 창 없이 설치합니다");
			assertThat(UpgradeCommandTest.this.launched).containsExactly(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy",
					"Bypass", "-WindowStyle", "Hidden", "-File", script.toString()));
			byte[] bytes = Files.readAllBytes(script);
			assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
			String text = new String(bytes, StandardCharsets.UTF_8);
			assertThat(text).doesNotContain("\r\r").doesNotContainPattern("[^\r]\n")
				.contains("-FilePath 'msiexec.exe' -ArgumentList ('/i \"' + '" + work("2.1.1").resolve("cozy-2.1.1-windows-x64.msi")
						+ "' + '\" /qn /norestart")
				.contains("$user = '" + UpgradeCommandTest.this.temp.resolve("local/Cozy/repository") + "'")
				.contains("$new = '" + UpgradeCommandTest.this.home.resolve("repository") + "'")
				.contains("Set-Content -LiteralPath (Join-Path $work 'result') -Value $code");
			// 설치하기 전의 번들 저장소: 하드 링크라 크기가 늘지 않는다. 잠금 파일은 뜨지 않는다
			Path jar = Path.of("io/salvia95/cozy/cozy-ui/2.1.0/cozy-ui-2.1.0.jar");
			Path kept = work("2.1.1").resolve("previous-repository").resolve(jar.toString());
			assertThat(kept).hasContent("ui 2.1.0");
			assertThat(Files.isSameFile(kept, UpgradeCommandTest.this.home.resolve("repository").resolve(jar.toString()))).isTrue();
			assertThat(Files.getAttribute(kept, "unix:nlink")).isEqualTo(2);
			assertThat(work("2.1.1").resolve("previous-repository/.locks")).doesNotExist();
		}

		@Test
		void aZipInstallWithTheJdkGetsTheWindowsBundleUnpackedWithoutUnzip() throws Exception {
			Files.createDirectories(UpgradeCommandTest.this.home.resolve("jdk/bin"));
			publish("2.1.1", "고친 판", List.of(), Map.of("windows-x64", bundle("2.1.1", "2.1.1", 0)));

			String out = upgrade(true);

			assertThat(out).contains("받은 번들의 cozy install로 다시 설치합니다");
			assertThat(work("2.1.1").resolve("bundle/cozy-offline-bundle-2.1.1/cozy.ps1")).isRegularFile();
			assertThat(new String(Files.readAllBytes(work("2.1.1").resolve("install.ps1")), StandardCharsets.UTF_8))
				.contains("-File \"' + '" + work("2.1.1").resolve("bundle/cozy-offline-bundle-2.1.1/cozy.ps1") + "' + '\" install \"' + '"
						+ UpgradeCommandTest.this.home + "' + '\"'");
		}

		@Test
		void aBundleWithEntriesOutsideItsFolderIsNotUnpacked() throws Exception {
			Path evil = zip(UpgradeCommandTest.this.temp.resolve("build/cozy-offline-bundle-2.1.1.zip"),
					Map.of("cozy-offline-bundle-2.1.1/COZY_VERSION", "2.1.1\n", "../../outside.txt", "x"));
			publish("2.1.1", "고친 판", List.of(), Map.of("portable", evil));

			assertThatThrownBy(() -> upgrade(true)).isInstanceOf(CliException.class)
				.hasMessageContaining("폴더 밖을 가리키는 항목(../../outside.txt)");
			assertThat(UpgradeCommandTest.this.temp.resolve("local/Cozy/outside.txt")).doesNotExist();
			assertThat(UpgradeCommandTest.this.launched).isEmpty();
		}

		@Test
		void anMsiInstallIsUpgradedOnlyOnWindows() throws Exception {
			Files.writeString(UpgradeCommandTest.this.home.resolve("COZY_INSTALLER"), "msi\n");
			UpgradeCommandTest.this.env.put("XDG_CONFIG_HOME", UpgradeCommandTest.this.temp.resolve("config").toString());
			publish("2.1.1", "고친 판", List.of(), Map.of("msi", write(UpgradeCommandTest.this.temp.resolve("build/c.msi"), "m")));

			assertThatThrownBy(() -> upgrade(false)).isInstanceOf(CliException.class)
				.hasMessageContaining("MSI로 설치한 cozy");
		}
	}

	static Path write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		return Files.writeString(file, text, StandardCharsets.UTF_8);
	}
}
