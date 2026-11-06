package io.salvia95.cozy.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.salvia95.cozy.runtime.internal.InstanceLock;
import io.salvia95.cozy.runtime.internal.PortFile;
import io.salvia95.cozy.runtime.internal.update.ZipArchives;

/**
 * M6 완료 기준 (PRD §12): reference-app v1.0.0 → v1.1.0 자동 업데이트가 데이터를 보존한 채 성공하고,
 * 실패하도록 만든 v1.1.1은 자동 롤백된다.
 *
 * <ol>
 * <li>reference-app 사본을 1.0.0, 1.1.0(마이그레이션 추가), 1.1.1으로 {@code -Pdist} 패키징한다. 1.1.1의 마이그레이션은
 * 빈 DB(패키징의 훈련 실행)에서는 통과하지만 제목이 겹치는 사용자 데이터에서는 실패한다.</li>
 * <li>1.0.0 zip을 설치 폴더에 풀고 실행한다. AOT 캐시를 일부러 망가뜨려도 정상 기동해야 한다.</li>
 * <li>메모를 만들고, 채널에 1.1.0을 올리면 배너가 뜬다. 업데이트를 누르면 앱이 끝나고 1.1.0이 뜬다.</li>
 * <li>1.1.0에서 메모와 마이그레이션 이력을 확인하고, 1.1.1을 올려 업데이트한다. 1.1.1은 기동에 실패하고 1.1.0으로 돌아온다.</li>
 * </ol>
 * 실제 OS의 스크립트(Windows PowerShell, Linux bash)와 jpackage 런처를 쓴다.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpdateEndToEndTest {

	static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	static final String APP_ID = "cozy-reference";

	static final Duration START_TIMEOUT = Duration.ofSeconds(90);

	static Path work;

	static Path programs;

	static Path appDir;

	static Path channel;

	static Path dataDir;

	static final Map<String, Path> builds = new LinkedHashMap<>();

	static Process firstProcess;

	@BeforeAll
	static void packageThreeVersions() throws Exception {
		work = Path.of(System.getProperty("cozy.e2e.work", "target/e2e")).toAbsolutePath();
		ZipArchives.deleteRecursively(work);
		programs = Files.createDirectories(work.resolve("Programs"));
		appDir = programs.resolve(APP_ID);
		channel = Files.createDirectories(work.resolve("share"));
		dataDir = work.resolve("data");
		Files.createDirectories(dataDir.resolve("config"));
		Files.writeString(dataDir.resolve("config/application.yml"), """
				cozy:
				  runtime:
				    open-browser: false
				    tray: false
				  update:
				    manifest-url: %s
				    check-interval: 2s
				""".formatted(channel.resolve(APP_ID + "-update.json").toUri()), StandardCharsets.UTF_8);

		builds.put("1.0.0", build("1.0.0", Map.of()));
		builds.put("1.1.0", build("1.1.0", Map.of("V202611071000__memo_add_color.sql",
				"ALTER TABLE memo ADD COLUMN color TEXT NOT NULL DEFAULT '';\n")));
		// 훈련 실행(빈 DB)은 통과하고, 제목이 겹치는 메모가 있는 사용자 DB에서만 실패한다
		builds.put("1.1.1", build("1.1.1", Map.of("V202611071000__memo_add_color.sql",
				"ALTER TABLE memo ADD COLUMN color TEXT NOT NULL DEFAULT '';\n", "V202611071100__memo_unique_title.sql",
				"CREATE UNIQUE INDEX memo_title_unique ON memo (title);\n")));
	}

	@AfterAll
	static void stopEverything() {
		if (work == null) {
			return;
		}
		String marker = work.toString();
		ProcessHandle.allProcesses()
			.filter((p) -> p.info().command().map((c) -> c.startsWith(marker)).orElse(false)
					|| p.info().commandLine().map((c) -> c.contains(marker)).orElse(false))
			.filter((p) -> p.pid() != ProcessHandle.current().pid())
			.forEach(ProcessHandle::destroyForcibly);
	}

	@Test
	@Order(1)
	void packagedImageHasAotCacheAndManifest() throws Exception {
		Path dist = builds.get("1.0.0");
		Path image = dist.resolve("image").resolve(APP_ID);
		Path cfg = WINDOWS ? image.resolve("app/" + APP_ID + ".cfg") : image.resolve("lib/app/" + APP_ID + ".cfg");
		assertThat(Files.readString(cfg)).contains("java-options=-XX:AOTCache=$APPDIR/" + APP_ID + ".aot")
			.contains("java-options=-Xmx512m");
		assertThat(cfg.resolveSibling(APP_ID + ".aot")).isNotEmptyFile();
		assertThat(cfg.resolveSibling(APP_ID + ".aotconf")).doesNotExist();
		// SQLite 네이티브 라이브러리는 %TEMP%가 아니라 앱 폴더에서 읽는다 (AppLocker)
		String nativeLib = WINDOWS ? "sqlitejdbc.dll" : "libsqlitejdbc.so";
		assertThat(cfg.resolveSibling("native").resolve(nativeLib)).isNotEmptyFile();
		assertThat(Files.readString(cfg)).contains("java-options=-Dorg.sqlite.lib.path=$APPDIR/native")
			.contains("java-options=-Dorg.sqlite.lib.name=" + nativeLib);
		// 제3자 라이선스 목록 (PRD §11): 런타임 의존성과 웹 자산
		assertThat(image.resolve("licenses/THIRD-PARTY.txt")).content()
			.contains("org.xerial:sqlite-jdbc:")
			.contains("org.springframework:spring-webmvc:")
			.doesNotContain("io.salvia95.cozy:")
			.doesNotContain("org.junit");
		assertThat(image.resolve("licenses/vendor")).isDirectoryContaining("glob:**/tabler-*-LICENSE.txt")
			.isDirectoryContaining("glob:**/htmx-*-LICENSE.txt")
			.isDirectoryContaining("glob:**/pretendard-*-LICENSE.txt");
		String manifest = Files.readString(dist.resolve(APP_ID + "-update.json"));
		assertThat(manifest).contains("\"appId\" : \"cozy-reference\"").contains("\"version\" : \"1.0.0\"");
		assertThat(Files.readString(dist.resolve("training.log"))).contains("훈련 요청").contains("/cozy/dev/catalog → 200");

		// zip을 풀어도 jar 시각이 같아야 AOT 캐시가 쓰인다
		Path extracted = work.resolve("extract-check");
		ZipArchives.extractSingleRoot(onlyZip(dist), extracted);
		Path appJars = WINDOWS ? Path.of("app") : Path.of("lib/app");
		try (Stream<Path> jars = Files.list(image.resolve(appJars))) {
			for (Path jar : jars.filter((p) -> p.toString().endsWith(".jar")).toList()) {
				assertThat(Files.getLastModifiedTime(extracted.resolve(appJars).resolve(jar.getFileName())))
					.as(jar.getFileName().toString())
					.isEqualTo(Files.getLastModifiedTime(jar));
			}
		}
		ZipArchives.deleteRecursively(extracted);
	}

	@Test
	@Order(2)
	void installedVersionStartsEvenWithMismatchedAotCache() throws Exception {
		Path zip = onlyZip(builds.get("1.0.0"));
		ZipArchives.extractSingleRoot(zip, appDir);
		// 캐시가 맞지 않아도 앱은 정상 기동해야 한다 (PRD §9.6)
		Path cache = WINDOWS ? appDir.resolve("app/" + APP_ID + ".aot") : appDir.resolve("lib/app/" + APP_ID + ".aot");
		Files.writeString(cache, "not an aot cache");

		firstProcess = launch(appDir, work.resolve("run-1.0.0.log"));
		App app = awaitApp("1.0.0");
		assertThat(app.page("/dashboard")).contains("1.0.0").doesNotContain("새 버전");
		app.createMemo("업데이트 전 메모");
		assertThat(app.page("/memo")).contains("업데이트 전 메모");
	}

	@Test
	@Order(3)
	void updateToNewVersionKeepsData() throws Exception {
		publish("1.1.0");
		App app = awaitApp("1.0.0");
		String page = app.awaitPage("/dashboard", "새 버전 1.1.0이 있습니다.");
		assertThat(page).contains("지금 업데이트");

		HttpResponse<String> response = app.post("/cozy/update", Map.of("version", "1.1.0"), page);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("업데이트를 설치합니다");
		assertThat(firstProcess.waitFor(60, TimeUnit.SECONDS)).as("이전 버전이 종료된다").isTrue();

		App updated = awaitApp("1.1.0");
		// 스크립트는 새 버전이 준비된 것을 본 뒤 결과를 쓴다. 화면은 다음 요청부터 결과를 보인다
		String dashboard = updated.awaitPage("/dashboard", "v1.1.0으로 업데이트했습니다.");
		assertThat(dashboard).contains("1.1.0").doesNotContain("새 버전");
		assertThat(updated.page("/memo")).contains("업데이트 전 메모");
		assertThat(appDir.resolveSibling(APP_ID + ".old")).isDirectory();
		assertThat(appDir.resolveSibling(APP_ID + ".new")).doesNotExist();
		assertThat(flywayHistory(updated)).contains("memo add color");
	}

	@Test
	@Order(4)
	void brokenVersionIsRolledBack() throws Exception {
		App app = awaitApp("1.1.0");
		app.createMemo("1.1.0에서 만든 메모");
		app.createMemo("1.1.0에서 만든 메모");
		publish("1.1.1");
		String page = app.awaitPage("/dashboard", "새 버전 1.1.1이 있습니다.");
		HttpResponse<String> response = app.post("/cozy/update", Map.of("version", "1.1.1"), page);
		assertThat(response.statusCode()).isEqualTo(200);

		// 1.1.1은 제목이 겹친 데이터에서 마이그레이션이 실패해 준비 신호를 남기지 못한다 → 스크립트가 1.1.0으로 되돌린다
		Path result = dataDir.resolve("update/result.properties");
		awaitCondition(() -> Files.exists(result) || updateLog().contains("되돌립니다"), Duration.ofSeconds(150),
				"롤백");
		App restored = awaitApp("1.1.0");
		String dashboard = restored.page("/dashboard");
		assertThat(dashboard).contains("v1.1.1 업데이트를 시작하지 못해 v1.1.0으로 되돌렸습니다.")
			.doesNotContain("새 버전 1.1.1");
		String memos = restored.page("/memo");
		assertThat(memos).contains("업데이트 전 메모").contains("1.1.0에서 만든 메모");
		assertThat(appDir.resolveSibling(APP_ID + ".failed")).isDirectory();
		assertThat(dataDir.resolve("update/failed-version")).hasContent("1.1.1");
		assertThat(flywayHistory(restored)).contains("memo add color").doesNotContain("memo unique title");
	}

	@Test
	@Order(5)
	void shutdownFromMenuStopsTheApp() throws Exception {
		App app = awaitApp("1.1.0");
		String page = app.page("/dashboard");
		HttpResponse<String> response = app.post("/cozy/shutdown", Map.of(), page);
		assertThat(response.body()).contains("앱을 종료했습니다");
		awaitCondition(() -> !Files.exists(dataDir.resolve("app.port")), Duration.ofSeconds(30), "종료");
		awaitCondition(() -> {
			try (InstanceLock lock = InstanceLock.tryAcquire(dataDir.resolve("app.lock")).orElse(null)) {
				return lock != null;
			}
		}, Duration.ofSeconds(30), "잠금 해제");
	}

	// ---- 패키징

	static Path build(String version, Map<String, String> extraMigrations) throws Exception {
		Path source = Path.of(System.getProperty("cozy.e2e.reference-app")).toAbsolutePath().normalize();
		Path copy = work.resolve("src-" + version);
		copyTree(source.resolve("src/main"), copy.resolve("src/main"));
		String pom = Files.readString(source.resolve("pom.xml"))
			.replaceFirst("<relativePath>[^<]*</relativePath>", "<relativePath/>")
			.replaceFirst("<artifactId>cozy-reference-app</artifactId>",
					"<artifactId>cozy-reference-app</artifactId>\n  <version>" + version + "</version>");
		Files.writeString(copy.resolve("pom.xml"), pom);
		for (Map.Entry<String, String> migration : extraMigrations.entrySet()) {
			Files.writeString(copy.resolve("src/main/resources/db/migration").resolve(migration.getKey()),
					migration.getValue());
		}
		List<String> command = new ArrayList<>(List.of(mavenExecutable().toString(), "-B", "-ntp", "-Pdist", "package",
				// reference-app의 테스트는 본 빌드가 이미 돌렸다. 여기서는 배포 패키지만 만든다
				"-Dmaven.test.skip=true", "-Dcozy.dist.notes=" + version + " 변경"));
		if ("offline=true".equals(System.getProperty("cozy.e2e.mode"))) {
			command.add("-o");
		}
		String localRepository = System.getProperty("cozy.e2e.local-repository");
		if (localRepository != null && !localRepository.isBlank()) {
			command.add("-Dmaven.repo.local=" + localRepository);
		}
		Path log = work.resolve("build-" + version + ".log");
		ProcessBuilder builder = new ProcessBuilder(command).directory(copy.toFile())
			.redirectErrorStream(true)
			.redirectOutput(log.toFile());
		builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
		builder.environment().keySet().removeIf((name) -> name.toUpperCase(Locale.ROOT).startsWith("COZY_"));
		Process process = builder.start();
		if (!process.waitFor(15, TimeUnit.MINUTES) || process.exitValue() != 0) {
			process.destroyForcibly();
			throw new AssertionError(version + " 패키징 실패. " + log + ":\n" + tail(log, 60));
		}
		return copy.resolve("target/dist");
	}

	static void publish(String version) throws IOException {
		Path dist = builds.get(version);
		Path zip = onlyZip(dist);
		Files.copy(zip, channel.resolve(zip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		// 패키지를 먼저 올리고 매니페스트를 나중에 바꾼다 (배포 절차와 같다)
		Path tmp = channel.resolve(APP_ID + "-update.json.tmp");
		Files.copy(dist.resolve(APP_ID + "-update.json"), tmp, StandardCopyOption.REPLACE_EXISTING);
		Files.move(tmp, channel.resolve(APP_ID + "-update.json"), StandardCopyOption.REPLACE_EXISTING);
	}

	static Path onlyZip(Path dist) throws IOException {
		try (Stream<Path> list = Files.list(dist)) {
			return list.filter((p) -> p.getFileName().toString().endsWith(".zip")).findFirst().orElseThrow();
		}
	}

	static Path mavenExecutable() {
		String home = System.getProperty("cozy.e2e.maven-home");
		return Path.of(home, "bin", WINDOWS ? "mvn.cmd" : "mvn");
	}

	// ---- 실행

	static Process launch(Path app, Path log) throws IOException {
		Path launcher = WINDOWS ? app.resolve(APP_ID + ".exe") : app.resolve("bin").resolve(APP_ID);
		ProcessBuilder builder = new ProcessBuilder(launcher.toString()).directory(work.toFile())
			.redirectErrorStream(true)
			.redirectOutput(log.toFile());
		builder.environment().keySet().removeIf((name) -> name.toUpperCase(Locale.ROOT).startsWith("COZY_"));
		builder.environment().put("COZY_DATA_DIR", dataDir.toString());
		return builder.start();
	}

	/** 지정한 버전이 준비 신호를 남기고 포트를 열 때까지 기다린다. */
	static App awaitApp(String version) throws Exception {
		Path ready = dataDir.resolve("update/ready");
		awaitCondition(() -> Files.isRegularFile(ready) && Files.readString(ready).strip().equals(version)
				&& PortFile.read(dataDir.resolve("app.port")).isPresent(), START_TIMEOUT, version + " 기동");
		return new App(PortFile.read(dataDir.resolve("app.port")).orElseThrow());
	}

	static String flywayHistory(App app) throws Exception {
		app.post("/cozy/diagnostics", Map.of(), app.page("/dashboard"));
		Path zip;
		try (Stream<Path> list = Files.list(dataDir.resolve("diagnostics"))) {
			zip = list.filter((p) -> p.toString().endsWith(".zip"))
				.max((a, b) -> a.getFileName().compareTo(b.getFileName()))
				.orElseThrow();
		}
		try (ZipFile file = new ZipFile(zip.toFile())) {
			return new String(file.getInputStream(file.getEntry("sqlite.txt")).readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	static String updateLog() throws IOException {
		Path log = dataDir.resolve("update/update.log");
		return Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8) : "";
	}

	static void awaitCondition(Check check, Duration timeout, String what) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			try {
				if (check.ok()) {
					return;
				}
			}
			catch (IOException ex) {
				// 아직 준비되지 않았다
			}
			Thread.sleep(500);
		}
		throw new AssertionError(what + "을(를) " + timeout.toSeconds() + "초 안에 확인하지 못했습니다.\nupdate.log:\n"
				+ updateLog() + "\napp.log 끝:\n" + tail(dataDir.resolve("logs/app.log"), 40));
	}

	static String tail(Path file, int lines) {
		try {
			List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
			return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
		}
		catch (IOException ex) {
			return "(읽을 수 없음: " + file + ")";
		}
	}

	static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path path : (Iterable<Path>) walk::iterator) {
				Path target = to.resolve(from.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(target);
				}
				else {
					Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	@FunctionalInterface
	interface Check {

		boolean ok() throws Exception;

	}

	/** 실행 중인 앱에 브라우저처럼 요청한다 (세션 쿠키, CSRF). */
	static final class App {

		static final Pattern CSRF = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]+)\"");

		final URI base;

		final HttpClient client;

		App(int port) {
			this.base = URI.create("http://127.0.0.1:" + port);
			this.client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager())
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		}

		String page(String path) throws Exception {
			HttpResponse<String> response = this.client.send(
					HttpRequest.newBuilder(this.base.resolve(path)).timeout(Duration.ofSeconds(30)).build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			assertThat(response.statusCode()).as("GET " + path).isEqualTo(200);
			return response.body();
		}

		String awaitPage(String path, String text) throws Exception {
			String[] last = { "" };
			awaitCondition(() -> {
				last[0] = page(path);
				return last[0].contains(text);
			}, Duration.ofSeconds(30), path + "에 '" + text + "'");
			return last[0];
		}

		HttpResponse<String> post(String path, Map<String, String> form, String pageWithToken) throws Exception {
			Matcher matcher = CSRF.matcher(pageWithToken);
			assertThat(matcher.find()).as("CSRF 토큰").isTrue();
			Map<String, String> fields = new LinkedHashMap<>(form);
			fields.put("_csrf", matcher.group(1));
			String body = fields.entrySet()
				.stream()
				.map((e) -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
						+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
			return this.client.send(HttpRequest.newBuilder(this.base.resolve(path))
				.timeout(Duration.ofMinutes(3))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Referer", this.base.resolve("/dashboard").toString())
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}

		void createMemo(String title) throws Exception {
			String form = page("/memo/new");
			HttpResponse<String> response = post("/memo", Map.of("title", title, "body", "E2E"), form);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains(title);
		}

	}
}
