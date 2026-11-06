package io.salvia95.cozy.runtime.internal.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.runtime.CozyDataDirectory;
import io.salvia95.cozy.runtime.internal.update.UpdateService.UpdateException;
import io.salvia95.cozy.runtime.testapp.Sleeper;
import io.salvia95.cozy.runtime.update.CozyUpdates.Offer;
import io.salvia95.cozy.runtime.update.CozyUpdates.Outcome;

/**
 * 자체 업데이트 (PRD §9.4): 매니페스트 확인, SHA-256 검증, 교체 스크립트의 교체·롤백을 실제 스크립트로 검증한다.
 *
 * <p>앱 대신 가짜 app-image(런처 스크립트)를 쓴다. Linux는 bash 스크립트, Windows는 PowerShell 스크립트가 돈다.
 * 실제 reference-app으로 하는 전체 시나리오는 samples/dist-e2e에 있다.
 */
class UpdateServiceTest {

	static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(60);

	@TempDir
	Path temp;

	Path apps;

	Path app;

	Path channel;

	CozyDataDirectory dataDir;

	Process oldProcess;

	@BeforeEach
	void setUp() throws IOException {
		this.apps = Files.createDirectories(this.temp.resolve("Programs"));
		this.channel = Files.createDirectories(this.temp.resolve("share"));
		this.dataDir = CozyDataDirectory.of(this.temp.resolve("data-root")).createDirectories();
		Files.writeString(this.dataDir.data().resolve("app.db"), "memo-1");
		this.app = buildApp(this.apps, "1.0.0", Behaviour.READY);
	}

	@AfterEach
	void tearDown() {
		if (this.oldProcess != null) {
			this.oldProcess.destroyForcibly();
		}
	}

	@Test
	void installReplacesAppFolderKeepsDataAndReportsSuccess() throws Exception {
		publish("1.1.0", Behaviour.READY, null);
		UpdateService service = service("1.0.0", true);
		service.check();
		assertThat(service.offer()).contains(new Offer("1.1.0", false, "1.1.0 변경", true, ""));

		service.install("1.1.0");
		// 스크립트는 시작했지만 앱(가짜 이전 프로세스)이 끝나기 전에는 아무것도 바꾸지 않는다
		assertThat(this.dataDir.update().resolve("script-started")).exists();
		assertThat(version(this.app)).isEqualTo("1.0.0");
		assertThat(this.apps.resolve("demo.new")).isDirectory();

		this.oldProcess.destroy();
		Properties result = awaitResult();
		assertThat(result.getProperty("status")).isEqualTo("success");
		assertThat(version(this.app)).isEqualTo("1.1.0");
		assertThat(version(this.apps.resolve("demo.old"))).isEqualTo("1.0.0");
		assertThat(this.apps.resolve("demo.new")).doesNotExist();
		assertThat(this.dataDir.data().resolve("app.db")).hasContent("memo-1");
		assertThat(this.dataDir.update().resolve("data-backup/app.db")).hasContent("memo-1");
		assertThat(this.dataDir.update().resolve("ready")).hasContent("1.1.0");

		Outcome outcome = service("1.1.0", true).lastOutcome().orElseThrow();
		assertThat(outcome.success()).isTrue();
		assertThat(outcome.message()).isEqualTo("v1.1.0으로 업데이트했습니다.");
		assertThat(this.dataDir.update().resolve("result.properties")).doesNotExist();
	}

	@Test
	void versionThatNeverBecomesReadyIsRolledBackWithItsData() throws Exception {
		publish("1.1.1", Behaviour.CORRUPT_DATA_AND_EXIT, null);
		UpdateService service = service("1.0.0", true);
		service.check();
		service.install("1.1.1");
		this.oldProcess.destroy();

		Properties result = awaitResult();
		assertThat(result.getProperty("status")).isEqualTo("rolled-back");
		assertThat(version(this.app)).isEqualTo("1.0.0");
		assertThat(version(this.apps.resolve("demo.failed"))).isEqualTo("1.1.1");
		// 새 버전이 망가뜨린 데이터는 교체 직전 백업으로 돌아온다
		assertThat(this.dataDir.data().resolve("app.db")).hasContent("memo-1");
		assertThat(this.dataDir.update().resolve("failed-version")).hasContent("1.1.1");
		// 이전 버전이 다시 실행되어 준비 신호를 남긴다
		awaitContent(this.dataDir.update().resolve("ready"), "1.0.0");

		UpdateService restarted = service("1.0.0", true);
		Outcome outcome = restarted.lastOutcome().orElseThrow();
		assertThat(outcome.success()).isFalse();
		assertThat(outcome.message()).isEqualTo("v1.1.1 업데이트를 시작하지 못해 v1.0.0으로 되돌렸습니다.");
		restarted.check();
		assertThat(restarted.offer()).as("실패한 버전은 다시 권하지 않는다").isEmpty();

		publish("1.1.2", Behaviour.READY, null);
		restarted.check();
		assertThat(restarted.offer()).map(Offer::version).contains("1.1.2");
	}

	@Test
	void checksumMismatchInstallsNothing() throws Exception {
		publish("1.1.0", Behaviour.READY, null);
		Path manifest = this.channel.resolve("demo-update.json");
		Files.writeString(manifest, Files.readString(manifest).replaceAll("\"sha256\" : \"[0-9a-f]+\"",
				"\"sha256\" : \"" + "0".repeat(64) + "\""));
		UpdateService service = service("1.0.0", true);
		service.check();
		assertThatThrownBy(() -> service.install("1.1.0")).isInstanceOf(UpdateException.class)
			.hasMessageContaining("SHA-256이 매니페스트와 다릅니다");
		assertThat(this.apps.resolve("demo.new")).doesNotExist();
		assertThat(this.dataDir.update().resolve("script-started")).doesNotExist();
		assertThat(version(this.app)).isEqualTo("1.0.0");
		assertThatThrownBy(() -> service.install("9.9.9")).hasMessageContaining("설치할 업데이트가 없습니다");
	}

	@Test
	void scriptThatStartsAfterTheAppGaveUpChangesNothing() throws Exception {
		publish("1.1.0", Behaviour.READY, null);
		UpdateService service = service("1.0.0", true);
		service.check();
		service.install("1.1.0");
		// 앱이 시작 확인을 못 해 취소한 상황: 이후 앱이 끝나도 스크립트는 아무것도 바꾸지 않는다
		Files.writeString(this.dataDir.update().resolve("cancelled"), "test");
		this.oldProcess.destroy();
		awaitLog("앱이 설치를 취소했습니다");
		assertThat(version(this.app)).isEqualTo("1.0.0");
		assertThat(this.apps.resolve("demo.new")).doesNotExist();
		assertThat(this.dataDir.update().resolve("result.properties")).doesNotExist();
	}

	@Test
	void offerFollowsVersionAppIdMinimumVersionAndInstallability() throws Exception {
		publish("1.1.0", Behaviour.READY, "1.0.5");
		assertThat(offerFor("1.1.0", true)).isEmpty();
		assertThat(offerFor("1.2.0", true)).isEmpty();
		assertThat(offerFor("1.0.0", true)).map(Offer::required).contains(true);
		assertThat(offerFor("1.0.5", true)).map(Offer::required).contains(false);
		Offer notInstalled = offerFor("1.0.0", false).orElseThrow();
		assertThat(notInstalled.installable()).isFalse();
		assertThat(notInstalled.reason()).contains("설치된 앱(app-image)");
		assertThatThrownBy(() -> {
			UpdateService service = service("1.0.0", false);
			service.check();
			service.install("1.1.0");
		}).hasMessageContaining("설치된 앱(app-image)");

		UpdateService other = new UpdateService("other-app", "1.0.0", manifestUrl(), Duration.ofHours(6),
				Duration.ofSeconds(20), this.dataDir, Optional.empty());
		other.check();
		assertThat(other.offer()).as("다른 앱의 매니페스트는 무시한다").isEmpty();

		UpdateService noVersion = new UpdateService("demo", null, manifestUrl(), Duration.ofHours(6),
				Duration.ofSeconds(20), this.dataDir, Optional.empty());
		assertThat(noVersion.isEnabled()).isFalse();
		noVersion.check();
		assertThat(noVersion.offer()).isEmpty();
	}

	@Test
	void manifestCanComeFromAnInternalHttpServer() throws Exception {
		publish("1.1.0", Behaviour.READY, null);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", (exchange) -> {
			Path file = this.channel.resolve(exchange.getRequestURI().getPath().substring("/updates/".length()));
			if (!Files.isRegularFile(file)) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			byte[] body = Files.readAllBytes(file);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		try {
			String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/updates/demo-update.json";
			UpdateService service = new UpdateService("demo", "1.0.0", url, Duration.ofHours(6), Duration.ofSeconds(20),
					this.dataDir, Optional.of(image(this.app)));
			service.check();
			assertThat(service.offer()).map(Offer::version).contains("1.1.0");

			UpdateService missing = new UpdateService("demo", "1.0.0", url.replace("demo-update", "nothing"),
					Duration.ofHours(6), Duration.ofSeconds(20), this.dataDir, Optional.empty());
			missing.check();
			assertThat(missing.offer()).as("읽지 못하면 권하지 않는다").isEmpty();
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void jpackageLauncherVariablesAreNotPassedToTheScript() {
		// jpackage 런처가 넣는 변수를 새 런처가 물려받으면 cfg를 읽지 않고 실패한다 (M6 종단 검증에서 발견)
		ProcessBuilder builder = new ProcessBuilder("true");
		builder.environment().put("_JPACKAGE_LAUNCHER", "x");
		builder.environment().put("KEEP_ME", "y");
		UpdateService.removeLauncherEnvironment(builder);
		assertThat(builder.environment()).doesNotContainKey("_JPACKAGE_LAUNCHER").containsEntry("KEEP_ME", "y");
		assertThat(UpdateScript.bash(parameters())).contains("unset \"$name\"").contains("_JPACKAGE");
		assertThat(UpdateScript.powershell(parameters())).contains("-like '_JPACKAGE*'");
	}

	private UpdateScript.Parameters parameters() {
		return new UpdateScript.Parameters(1, image(this.app), this.dataDir.root(), new UpdateFiles(this.dataDir.update()),
				"1.0.0", "1.1.0", Duration.ofSeconds(60));
	}

	private Optional<Offer> offerFor(String current, boolean installed) {
		UpdateService service = service(current, installed);
		service.check();
		return service.offer();
	}

	private UpdateService service(String current, boolean installed) {
		UpdateService service = new UpdateService("demo", current, manifestUrl(), Duration.ofHours(6),
				Duration.ofSeconds(20), this.dataDir, installed ? Optional.of(image(this.app)) : Optional.empty());
		if (this.oldProcess == null) {
			this.oldProcess = sleeper();
		}
		Process old = this.oldProcess;
		service.waitForProcess(old::pid);
		return service;
	}

	private String manifestUrl() {
		return this.channel.resolve("demo-update.json").toUri().toString();
	}

	private static AppImage image(Path appDir) {
		return new AppImage(appDir, Path.of(WINDOWS ? "demo.cmd" : "bin/demo"));
	}

	private void publish(String version, Behaviour behaviour, String minimumVersion) throws IOException {
		Path build = Files.createDirectories(this.temp.resolve("build-" + version));
		Path appDir = buildApp(build, version, behaviour);
		String packageName = "demo-" + version + "-test.zip";
		Path zip = this.channel.resolve(packageName);
		ZipArchives.create(appDir, zip);
		UpdateManifest manifest = new UpdateManifest("demo", version, minimumVersion, packageName,
				ZipArchives.sha256(zip), "2026-11-06", version + " 변경");
		Files.writeString(this.channel.resolve("demo-update.json"), manifest.toJson(), StandardCharsets.UTF_8);
	}

	/** 가짜 app-image: 버전 파일과, 동작을 흉내 내는 런처 */
	private static Path buildApp(Path parent, String version, Behaviour behaviour) throws IOException {
		Path appDir = Files.createDirectories(parent.resolve("demo"));
		Files.writeString(appDir.resolve("VERSION.txt"), version);
		if (WINDOWS) {
			String body = switch (behaviour) {
				case READY -> "<nul set /p =\"" + version + "\" > \"%COZY_DATA_DIR%\\update\\ready\"\r\n"
						+ "ping -n 3 127.0.0.1 > nul\r\n";
				case CORRUPT_DATA_AND_EXIT -> "echo broken> \"%COZY_DATA_DIR%\\data\\app.db\"\r\nexit /b 1\r\n";
			};
			Files.writeString(appDir.resolve("demo.cmd"), "@echo off\r\n" + body);
		}
		else {
			Path bin = Files.createDirectories(appDir.resolve("bin"));
			String body = switch (behaviour) {
				case READY -> "printf '%s' '" + version + "' > \"$COZY_DATA_DIR/update/ready\"\nsleep 3\n";
				case CORRUPT_DATA_AND_EXIT -> "echo broken > \"$COZY_DATA_DIR/data/app.db\"\nexit 1\n";
			};
			Path launcher = Files.writeString(bin.resolve("demo"), "#!/bin/sh\n" + body);
			Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
		return appDir;
	}

	private static String version(Path appDir) throws IOException {
		return Files.readString(appDir.resolve("VERSION.txt"));
	}

	private Properties awaitResult() throws Exception {
		Path file = this.dataDir.update().resolve("result.properties");
		await(() -> Files.isRegularFile(file), "result.properties");
		Properties result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			result.load(reader);
		}
		return result;
	}

	private void awaitContent(Path file, String content) throws Exception {
		await(() -> Files.isRegularFile(file) && Files.readString(file).equals(content), file + " = " + content);
	}

	private void awaitLog(String text) throws Exception {
		Path log = this.dataDir.update().resolve("update.log");
		await(() -> Files.isRegularFile(log) && Files.readString(log, StandardCharsets.UTF_8).contains(text),
				"update.log: " + text);
	}

	private void await(Check check, String what) throws Exception {
		long deadline = System.nanoTime() + SCRIPT_TIMEOUT.toNanos();
		while (System.nanoTime() < deadline) {
			if (check.ok()) {
				return;
			}
			Thread.sleep(200);
		}
		Path log = this.dataDir.update().resolve("update.log");
		throw new AssertionError(what + "를 기다리다 시간이 지났습니다. update.log:\n"
				+ (Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8) : "(없음)"));
	}

	private static Process sleeper() {
		String java = ProcessHandle.current().info().command().orElse("java");
		try {
			return new ProcessBuilder(List.of(java, "-cp", System.getProperty("java.class.path"),
					Sleeper.class.getName())).redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	enum Behaviour {

		READY, CORRUPT_DATA_AND_EXIT

	}

	@FunctionalInterface
	interface Check {

		boolean ok() throws Exception;

	}
}
