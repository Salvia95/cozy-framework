package io.salvia95.cozy.runtime.internal.dist;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import io.salvia95.cozy.runtime.CozyDataDirectory;
import io.salvia95.cozy.runtime.internal.update.UpdateManifest;
import io.salvia95.cozy.runtime.internal.update.Versions;
import io.salvia95.cozy.runtime.internal.update.ZipArchives;

/**
 * {@code mvn -Pdist package}의 패키징. cozy-parent의 dist 프로필이 앱의 런타임 클래스패스로 실행한다.
 *
 * <ol>
 * <li>Boot jar를 풀어(jarmode tools extract) 일반 클래스패스 jar로 만든다. AOT 캐시는 풀린 jar에서만 효과가 있다.</li>
 * <li>{@code jpackage --type app-image}로 앱 폴더를 만든다. 런타임이 들어 있어 사용자 PC에 JDK가 필요 없다.</li>
 * <li>AOT 캐시: 만든 앱의 런처로 훈련 실행(record)과 캐시 생성(create)을 하고, 실행 옵션에 {@code -XX:AOTCache}를 등록한다.
 * 캐시가 맞지 않거나 없어도 JVM은 경고만 남기고 정상 기동한다.</li>
 * <li>sqlite-jdbc 네이티브 라이브러리와 제3자 라이선스 목록({@link ThirdPartyLicenses})을 실행 폴더에 담는다.</li>
 * <li>zip과 업데이트 매니페스트(SHA-256 포함)를 만든다.</li>
 * </ol>
 *
 * 결과는 {@code target/dist/}에 있다: {@code image/{appId}/}(실행 폴더), {@code {appId}-{version}-{platform}.zip},
 * {@code {appId}-update.json}, {@code training.log}. 문서: docs/conventions/distribution.md
 */
public final class CozyPackager {

	static final String CATALOG_CLASS = "io.salvia95.cozy.ui.internal.CatalogController";

	private final Map<String, String> options;

	private final PrintStream out;

	CozyPackager(Map<String, String> options, PrintStream out) {
		this.options = options;
		this.out = out;
	}

	public static void main(String[] args) {
		Map<String, String> options = new LinkedHashMap<>();
		for (String arg : args) {
			int equals = arg.indexOf('=');
			if (!arg.startsWith("--") || equals < 0) {
				fail("인수 형식은 --이름=값입니다: " + arg);
			}
			options.put(arg.substring(2, equals), arg.substring(equals + 1));
		}
		try {
			new CozyPackager(options, System.out).run();
		}
		catch (Exception ex) {
			System.err.println("[cozy] 패키징 실패: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
	}

	void run() throws Exception {
		Path jar = Path.of(require("jar")).toAbsolutePath();
		Path outDir = Path.of(require("out")).toAbsolutePath();
		Path work = Path.of(require("work")).toAbsolutePath();
		String version = require("version");
		if (!Versions.isValid(version)) {
			throw new IllegalArgumentException("버전 형식이 아닙니다: " + version);
		}
		String minimumVersion = blankToNull(this.options.get("minimum-version"));
		if (minimumVersion != null && !Versions.isValid(minimumVersion)) {
			throw new IllegalArgumentException("cozy.dist.minimum-version 형식이 아닙니다: " + minimumVersion);
		}
		String notes = this.options.getOrDefault("notes", "").strip();
		boolean training = Boolean.parseBoolean(this.options.getOrDefault("training", "true"));
		Duration trainingTimeout = Duration.ofSeconds(Long.parseLong(this.options.getOrDefault("training-timeout", "300")));
		List<String> javaOptions = splitOptions(this.options.getOrDefault("java-options", "-Xmx512m"));

		requireBootJar(jar);
		StandardEnvironment app = appEnvironment();
		String appId = CozyDataDirectory.requireValidAppId(app.getProperty("cozy.app.id"));
		String appName = app.getProperty("cozy.app.name", appId);
		Platform platform = Platform.current();

		ZipArchives.deleteRecursively(outDir);
		ZipArchives.deleteRecursively(work);
		Files.createDirectories(outDir);
		Path input = work.resolve("input");
		log("1/4 jar 풀기: " + jar.getFileName());
		Path mainJar = extract(jar, input, work);

		log("2/4 jpackage app-image: " + appId + " " + version + " (" + platform.id() + ")");
		Path imageRoot = outDir.resolve("image");
		Path image = jpackage(appId, appName, version, input, mainJar, imageRoot, javaOptions);

		bundleSqliteNative(image, platform);
		int[] licenses = ThirdPartyLicenses.bundle(Path.of(require("licenses")).toAbsolutePath(),
				require("allowed-licenses"), platform.cfg(image, appId).getParent().resolve("lib"), image);
		log("   제3자 라이선스: 의존성 " + licenses[0] + "개, 웹 자산 " + licenses[1] + "개 → " + ThirdPartyLicenses.FOLDER + "/");
		normalizeTimes(image);
		Path trainingLog = outDir.resolve("training.log");
		if (training) {
			log("3/4 AOT 캐시 훈련");
			train(appId, image, platform, trainingPaths(app), work.resolve("training-data"), trainingLog,
					trainingTimeout);
		}
		else {
			log("3/4 AOT 캐시 훈련 건너뜀 (cozy.dist.training=false)");
		}

		String packageName = appId + "-" + version + "-" + platform.id() + ".zip";
		Path zip = outDir.resolve(packageName);
		log("4/4 zip과 매니페스트: " + packageName);
		ZipArchives.create(image, zip);
		UpdateManifest manifest = new UpdateManifest(appId, version, minimumVersion, packageName,
				ZipArchives.sha256(zip), LocalDate.now().toString(), notes);
		Path manifestFile = outDir.resolve(appId + "-update.json");
		Files.writeString(manifestFile, manifest.toJson(), StandardCharsets.UTF_8);
		log("완료: " + image + "\n       " + zip + "\n       " + manifestFile);
	}

	private Path extract(Path jar, Path input, Path work) throws IOException, InterruptedException {
		Files.createDirectories(work);
		List<String> command = List.of(javaExecutable().toString(), "-Djarmode=tools", "-jar", jar.toString(),
				"extract", "--destination", input.toString());
		run(command, work, work.resolve("extract.log"), Duration.ofMinutes(5), Map.of(), "jar 풀기");
		try (Stream<Path> list = Files.list(input)) {
			List<Path> jars = list.filter((p) -> p.getFileName().toString().endsWith(".jar")).toList();
			if (jars.size() != 1) {
				throw new IllegalStateException("풀린 앱 jar를 찾을 수 없습니다: " + input);
			}
			return jars.get(0);
		}
	}

	private Path jpackage(String appId, String appName, String version, Path input, Path mainJar, Path dest,
			List<String> javaOptions) {
		List<String> args = new ArrayList<>(List.of("--type", "app-image", "--name", appId, "--input",
				input.toString(), "--main-jar", mainJar.getFileName().toString(), "--dest", dest.toString(),
				"--app-version", Versions.numericPart(version), "--description", appName));
		List<String> allOptions = new ArrayList<>(javaOptions);
		// sqlite-jdbc가 네이티브 라이브러리를 읽는다 (JDK 25의 제한 메서드 경고 방지)
		allOptions.add("--enable-native-access=ALL-UNNAMED");
		for (String option : allOptions) {
			args.add("--java-options");
			args.add(option);
		}
		ToolProvider jpackage = ToolProvider.findFirst("jpackage")
			.orElseThrow(() -> new IllegalStateException("jpackage가 없습니다. JDK 25로 빌드하세요."));
		int code = jpackage.run(new PrintWriter(this.out, true), new PrintWriter(System.err, true),
				args.toArray(String[]::new));
		if (code != 0) {
			throw new IllegalStateException("jpackage 실패 (종료 코드 " + code + ")");
		}
		return dest.resolve(appId);
	}

	private void train(String appId, Path image, Platform platform, List<String> paths, Path dataDir, Path logFile,
			Duration timeout) throws IOException, InterruptedException {
		Path cfg = platform.cfg(image, appId);
		Path launcher = platform.launcher(image, appId);
		String original = Files.readString(cfg, StandardCharsets.UTF_8);
		String config = "$APPDIR/" + appId + ".aotconf";
		String cache = "$APPDIR/" + appId + ".aot";
		Map<String, String> env = Map.of("COZY_DATA_DIR", dataDir.toString());
		try {
			Files.writeString(cfg, withJavaOptions(original, "-XX:AOTMode=record", "-XX:AOTConfiguration=" + config),
					StandardCharsets.UTF_8);
			List<String> trainingRun = new ArrayList<>(List.of(launcher.toString(), "--cozy.runtime.training=true",
					"--cozy.runtime.open-browser=false", "--cozy.runtime.tray=false", "--cozy.update.manifest-url=",
					"--cozy.runtime.training-paths=" + String.join(",", paths)));
			if (paths.stream().anyMatch((p) -> p.startsWith("/cozy/dev/catalog"))) {
				trainingRun.add("--cozy.dev.catalog.enabled=true");
			}
			run(trainingRun, image, logFile, timeout, env, "훈련 실행");

			Files.writeString(cfg, withJavaOptions(original, "-XX:AOTMode=create", "-XX:AOTConfiguration=" + config,
					"-XX:AOTCache=" + cache), StandardCharsets.UTF_8);
			run(List.of(launcher.toString()), image, logFile, timeout, env, "AOT 캐시 생성");
		}
		finally {
			Files.writeString(cfg, original, StandardCharsets.UTF_8);
		}
		Path appDir = cfg.getParent();
		Files.deleteIfExists(appDir.resolve(appId + ".aotconf"));
		Path cacheFile = appDir.resolve(appId + ".aot");
		if (!Files.isRegularFile(cacheFile) || Files.size(cacheFile) == 0) {
			throw new IllegalStateException("AOT 캐시가 만들어지지 않았습니다. " + logFile + "를 보세요.");
		}
		Files.writeString(cfg, withJavaOptions(original, "-XX:AOTCache=" + cache), StandardCharsets.UTF_8);
		log("   AOT 캐시 " + (Files.size(cacheFile) / (1024 * 1024)) + "MB, 훈련 경로 " + paths);
	}

	/**
	 * sqlite-jdbc의 네이티브 라이브러리를 실행 폴더의 {@code native/}에 꺼내 두고 그 경로를 쓰게 한다.
	 * 기본 동작은 실행할 때마다 %TEMP%에 풀어 읽는 것인데, AppLocker 등이 임시 폴더의 DLL을 막는 PC가 있다.
	 * 앱 폴더의 DLL은 실행 파일과 같은 위치라 같은 정책을 받는다. sqlite-jdbc가 없는 앱은 건너뛴다.
	 */
	void bundleSqliteNative(Path image, Platform platform) throws IOException {
		Path appDir = platform.cfg(image, image.getFileName().toString()).getParent();
		Path jar;
		try (Stream<Path> files = Files.list(appDir.resolve("lib"))) {
			jar = files.filter((p) -> p.getFileName().toString().matches("sqlite-jdbc-.*\\.jar")).findFirst().orElse(null);
		}
		if (jar == null) {
			return;
		}
		String entry = platform.sqliteNativeEntry();
		Path target = appDir.resolve("native").resolve(entry.substring(entry.lastIndexOf('/') + 1));
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry nativeEntry = zip.getEntry(entry);
			if (nativeEntry == null) {
				throw new IllegalStateException(jar.getFileName() + "에 " + entry + "가 없습니다");
			}
			Files.createDirectories(target.getParent());
			try (InputStream in = zip.getInputStream(nativeEntry)) {
				Files.copy(in, target);
			}
		}
		Path cfg = platform.cfg(image, image.getFileName().toString());
		Files.writeString(cfg, withJavaOptions(Files.readString(cfg, StandardCharsets.UTF_8),
				"-Dorg.sqlite.lib.path=$APPDIR/native", "-Dorg.sqlite.lib.name=" + target.getFileName()),
				StandardCharsets.UTF_8);
		log("   SQLite 네이티브 라이브러리: native/" + target.getFileName());
	}

	/**
	 * 실행 폴더의 모든 파일 시각을 짝수 초로 맞춘다. AOT 캐시는 jar의 수정 시각(초)을 대조하는데, zip은 시각을 2초 단위로
	 * 담는다. 훈련 전에 맞춰 두면 zip을 풀어도(탐색기 포함) 시각이 같아 캐시가 그대로 쓰인다.
	 */
	static void normalizeTimes(Path image) throws IOException {
		long seconds = Instant.now().getEpochSecond();
		FileTime time = FileTime.from(Instant.ofEpochSecond(seconds - (seconds % 2)));
		try (Stream<Path> walk = Files.walk(image)) {
			for (Path path : (Iterable<Path>) walk::iterator) {
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					Files.setLastModifiedTime(path, time);
				}
			}
		}
	}

	/** cfg의 [JavaOptions] 끝에 옵션을 더한다. */
	static String withJavaOptions(String cfg, String... options) {
		StringBuilder text = new StringBuilder(cfg);
		if (!cfg.contains("[JavaOptions]")) {
			text.append(cfg.endsWith("\n") ? "" : "\n").append("[JavaOptions]\n");
		}
		else if (!cfg.endsWith("\n")) {
			text.append('\n');
		}
		String section = text.toString();
		int sectionStart = section.indexOf("[JavaOptions]");
		int next = section.indexOf("\n[", sectionStart + 1);
		StringBuilder lines = new StringBuilder();
		for (String option : options) {
			lines.append("java-options=").append(option).append('\n');
		}
		if (next < 0) {
			return section + lines;
		}
		return section.substring(0, next + 1) + lines + section.substring(next + 1);
	}

	private void run(List<String> command, Path directory, Path logFile, Duration timeout, Map<String, String> env,
			String what) throws IOException, InterruptedException {
		Files.createDirectories(logFile.getParent());
		Files.writeString(logFile, "\n=== " + what + ": " + String.join(" ", command) + "\n", StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
		// 빌드 PC의 COZY_* 환경 변수가 훈련 실행에 섞이지 않게 한다
		builder.environment().keySet().removeIf((name) -> name.toUpperCase(Locale.ROOT).startsWith("COZY_"));
		builder.environment().putAll(env);
		Process process = builder.start();
		if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IllegalStateException(what + "이 " + timeout.toSeconds() + "초 안에 끝나지 않았습니다. " + logFile + "를 보세요.");
		}
		if (process.exitValue() != 0) {
			throw new IllegalStateException(what + " 실패 (종료 코드 " + process.exitValue() + "). " + logFile + "를 보세요.");
		}
	}

	private List<String> trainingPaths(StandardEnvironment app) {
		String configured = app.getProperty("cozy.runtime.training-paths");
		if (configured != null && !configured.isBlank()) {
			return List.of(configured.split("\\s*,\\s*"));
		}
		List<String> paths = new ArrayList<>(List.of("/"));
		try {
			Class.forName(CATALOG_CLASS, false, CozyPackager.class.getClassLoader());
			paths.add("/cozy/dev/catalog");
		}
		catch (ClassNotFoundException ex) {
			// cozy-ui가 없는 앱
		}
		return paths;
	}

	/** 앱의 application.yml(또는 .properties)에서 앱 식별을 읽는다. */
	static StandardEnvironment appEnvironment() throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		Resource yaml = new ClassPathResource("application.yml");
		if (yaml.exists()) {
			new YamlPropertySourceLoader().load("application.yml", yaml).forEach(environment.getPropertySources()::addLast);
		}
		Resource properties = new ClassPathResource("application.properties");
		if (properties.exists()) {
			new PropertiesPropertySourceLoader().load("application.properties", properties)
				.forEach(environment.getPropertySources()::addLast);
		}
		return environment;
	}

	private static void requireBootJar(Path jar) throws IOException {
		if (!Files.isRegularFile(jar)) {
			throw new IllegalStateException("앱 jar가 없습니다: " + jar + ". spring-boot-maven-plugin을 앱 POM의 plugins에 두세요.");
		}
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			if (zip.getEntry("BOOT-INF/") == null && zip.stream().noneMatch((e) -> e.getName().startsWith("BOOT-INF/"))) {
				throw new IllegalStateException("Boot 실행 jar가 아닙니다(repackage 전): " + jar);
			}
		}
	}

	static List<String> splitOptions(String value) {
		return (value == null || value.isBlank()) ? List.of() : List.of(value.strip().split("\\s+"));
	}

	private static Path javaExecutable() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
		return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
	}

	private String require(String name) {
		String value = this.options.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("--" + name + "가 필요합니다");
		}
		return value;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	private void log(String message) {
		this.out.println("[cozy:dist] " + message);
	}

	private static void fail(String message) {
		System.err.println("[cozy] " + message);
		System.exit(2);
	}

	/** 빌드 PC의 OS와 CPU */
	record Platform(String os, String arch) {

		static Platform current() {
			String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			String os = name.startsWith("windows") ? "win" : name.startsWith("mac") ? "mac" : "linux";
			String arch = switch (System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)) {
				case "amd64", "x86_64" -> "x64";
				case "aarch64", "arm64" -> "arm64";
				default -> System.getProperty("os.arch");
			};
			return new Platform(os, arch);
		}

		String id() {
			return this.os + "-" + this.arch;
		}

		Path cfg(Path image, String appId) {
			return this.os.equals("win") ? image.resolve("app").resolve(appId + ".cfg")
					: image.resolve("lib").resolve("app").resolve(appId + ".cfg");
		}

		/** sqlite-jdbc jar 안의 네이티브 라이브러리 위치 */
		String sqliteNativeEntry() {
			String arch = this.arch.equals("arm64") ? "aarch64" : "x86_64";
			return switch (this.os) {
				case "win" -> "org/sqlite/native/Windows/" + arch + "/sqlitejdbc.dll";
				case "mac" -> "org/sqlite/native/Mac/" + arch + "/libsqlitejdbc.dylib";
				default -> "org/sqlite/native/Linux/" + arch + "/libsqlitejdbc.so";
			};
		}

		Path launcher(Path image, String appId) {
			return this.os.equals("win") ? image.resolve(appId + ".exe") : image.resolve("bin").resolve(appId);
		}
	}
}
