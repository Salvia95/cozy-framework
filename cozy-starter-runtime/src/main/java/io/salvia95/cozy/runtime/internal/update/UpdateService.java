package io.salvia95.cozy.runtime.internal.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.context.SmartLifecycle;

import io.salvia95.cozy.runtime.CozyDataDirectory;
import io.salvia95.cozy.runtime.update.CozyUpdates;

/**
 * 자체 업데이트 (PRD §9.4): 매니페스트 확인, 내려받기, 검증, 압축 해제, 교체 스크립트 실행.
 *
 * <ul>
 * <li>{@code cozy.update.manifest-url}이 없거나 앱 버전(build-info)을 모르면 확인하지 않는다.</li>
 * <li>기동할 때와 {@code cozy.update.check-interval}마다 매니페스트를 읽는다. 읽지 못하면 이전 상태를 유지한다.</li>
 * <li>롤백한 버전({@code update/failed-version})은 다시 권하지 않는다. 더 새 버전이 나오면 권한다.</li>
 * <li>{@link #install}은 스크립트가 시작된 것을 확인한 뒤에야 돌아온다. 호출한 쪽이 앱을 종료한다.</li>
 * </ul>
 */
public class UpdateService implements CozyUpdates, SmartLifecycle {

	private static final Log logger = LogFactory.getLog(UpdateService.class);

	static final Duration SCRIPT_START_TIMEOUT = Duration.ofSeconds(20);

	private final String appId;

	private final @Nullable String currentVersion;

	private final @Nullable UpdateChannel channel;

	private final Duration checkInterval;

	private final Duration readyTimeout;

	private final CozyDataDirectory dataDirectory;

	private final UpdateFiles files;

	private final Optional<AppImage> image;

	private final boolean windows;

	private final AtomicBoolean installing = new AtomicBoolean();

	/** 교체 스크립트가 끝나기를 기다릴 프로세스. 테스트는 다른 프로세스로 바꾼다. */
	private LongSupplier pid = () -> ProcessHandle.current().pid();

	private volatile @Nullable UpdateManifest manifest;

	private volatile @Nullable Offer offer;

	private @Nullable Outcome outcome;

	private @Nullable ScheduledExecutorService scheduler;

	public UpdateService(String appId, @Nullable String currentVersion, @Nullable String manifestUrl,
			Duration checkInterval, Duration readyTimeout, CozyDataDirectory dataDirectory, Optional<AppImage> image) {
		this.appId = appId;
		this.currentVersion = currentVersion;
		this.channel = (manifestUrl != null && !manifestUrl.isBlank()) ? UpdateChannel.of(manifestUrl) : null;
		this.checkInterval = checkInterval;
		this.readyTimeout = readyTimeout;
		this.dataDirectory = dataDirectory;
		this.files = new UpdateFiles(dataDirectory.update());
		this.image = image;
		this.windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	@Override
	public Optional<Offer> offer() {
		return Optional.ofNullable(this.offer);
	}

	/**
	 * 교체 스크립트는 새 버전이 준비 신호를 남긴 뒤에 결과를 쓴다. 그래서 기동할 때가 아니라 물어볼 때 파일을 확인하고,
	 * 한 번 읽으면 이 실행 동안 그 결과를 계속 보여 준다.
	 */
	@Override
	public synchronized Optional<Outcome> lastOutcome() {
		if (this.outcome == null) {
			this.outcome = readOutcome(this.files.result());
		}
		return Optional.ofNullable(this.outcome);
	}

	public boolean isEnabled() {
		return this.channel != null && this.currentVersion != null;
	}

	void waitForProcess(LongSupplier pid) {
		this.pid = pid;
	}

	public UpdateFiles files() {
		return this.files;
	}

	/** 새로 뜬 앱이 기동을 마쳤다는 신호. 교체 스크립트가 이 파일로 성공을 판단한다. */
	public void signalReady() {
		try {
			Files.writeString(this.files.ready(), (this.currentVersion != null) ? this.currentVersion : "dev",
					StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			logger.warn("[cozy] 준비 신호를 쓸 수 없습니다: " + this.files.ready(), ex);
		}
	}

	/** 매니페스트를 지금 읽고 상태를 갱신한다. */
	public void check() {
		if (!isEnabled()) {
			return;
		}
		UpdateManifest read;
		try {
			read = UpdateManifest.parse(this.channel.readManifest(), this.channel.toString());
		}
		catch (IOException | RuntimeException ex) {
			logger.warn("[cozy] 업데이트 매니페스트를 읽을 수 없습니다 (" + this.channel + "): " + ex.getMessage());
			return;
		}
		if (!read.appId().equals(this.appId)) {
			logger.warn("[cozy] 업데이트 매니페스트의 appId '" + read.appId() + "'가 이 앱(" + this.appId + ")과 달라 무시합니다: "
					+ this.channel);
			this.manifest = null;
			this.offer = null;
			return;
		}
		this.manifest = read;
		this.offer = evaluate(read);
	}

	private @Nullable Offer evaluate(UpdateManifest read) {
		String current = this.currentVersion;
		if (current == null || Versions.compare(read.version(), current) <= 0) {
			return null;
		}
		String failed = readFailedVersion();
		if (read.version().equals(failed)) {
			logger.info("[cozy] 업데이트 " + read.version() + "는 이전에 실패해 되돌렸으므로 권하지 않습니다");
			return null;
		}
		boolean required = read.minimumVersion() != null && Versions.compare(current, read.minimumVersion()) < 0;
		String reason = installBlocker();
		return new Offer(read.version(), required, read.notes(), reason == null, (reason != null) ? reason : "");
	}

	private @Nullable String installBlocker() {
		if (this.image.isEmpty()) {
			return "설치된 앱(app-image)으로 실행할 때만 업데이트할 수 있습니다";
		}
		if (!this.image.get().isReplaceable()) {
			return "앱 폴더에 쓸 수 없습니다(" + this.image.get().directory()
					+ "). 앱을 사용자 폴더(%LOCALAPPDATA%\\Programs)에 설치하세요";
		}
		return null;
	}

	/**
	 * 업데이트를 설치한다. 성공하면 교체 스크립트가 앱 종료를 기다리고 있으므로 호출한 쪽이 앱을 종료해야 한다.
	 * @param version 사용자가 화면에서 고른 버전. 지금 권하는 버전과 다르면 설치하지 않는다.
	 * @throws UpdateException 설치할 수 없을 때. 메시지는 화면에 그대로 보인다.
	 */
	public void install(String version) throws UpdateException {
		UpdateManifest target = this.manifest;
		Offer current = this.offer;
		if (target == null || current == null || !current.version().equals(version)) {
			throw new UpdateException("설치할 업데이트가 없습니다. 화면을 새로 고치세요.");
		}
		if (!current.installable()) {
			throw new UpdateException(current.reason());
		}
		if (!this.installing.compareAndSet(false, true)) {
			throw new UpdateException("이미 업데이트를 설치하고 있습니다.");
		}
		boolean launched = false;
		AppImage app = this.image.orElseThrow();
		try {
			Files.createDirectories(this.files.directory());
			Path zip = download(target);
			try {
				ZipArchives.deleteRecursively(app.staging());
				ZipArchives.extractSingleRoot(zip, app.staging());
			}
			catch (IOException ex) {
				throw new UpdateException("업데이트 파일을 풀 수 없습니다: " + ex.getMessage(), ex);
			}
			finally {
				Files.deleteIfExists(zip);
			}
			checkLauncher(app);
			launch(app, target);
			launched = true;
			logger.info("[cozy] 업데이트 " + this.currentVersion + " → " + target.version() + " 교체 스크립트를 시작했습니다");
		}
		catch (IOException ex) {
			throw new UpdateException("업데이트를 준비할 수 없습니다: " + ex.getMessage(), ex);
		}
		finally {
			if (!launched) {
				deleteQuietly(app.staging());
				this.installing.set(false);
			}
		}
	}

	private Path download(UpdateManifest target) throws UpdateException, IOException {
		Path part = this.files.download(target.packageName() + ".part");
		Path zip = this.files.download(target.packageName());
		String actual;
		try (InputStream in = this.channel.openPackage(target.packageName());
				OutputStream out = Files.newOutputStream(part)) {
			actual = ZipArchives.copyWithSha256(in, out);
		}
		catch (IOException ex) {
			Files.deleteIfExists(part);
			throw new UpdateException("업데이트 파일을 내려받을 수 없습니다(" + this.channel.packageUri(target.packageName())
					+ "): " + ex.getMessage(), ex);
		}
		if (!actual.equals(target.sha256())) {
			Files.deleteIfExists(part);
			throw new UpdateException("내려받은 파일의 SHA-256이 매니페스트와 다릅니다. 설치하지 않았습니다. 배포 담당자에게 알리세요.");
		}
		Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING);
		return zip;
	}

	private void checkLauncher(AppImage app) throws UpdateException {
		Path launcher = app.staging().resolve(app.launcher());
		if (!Files.isRegularFile(launcher)) {
			throw new UpdateException("새 버전에 실행 파일(" + app.launcher() + ")이 없습니다. 이 앱의 패키지가 맞는지 확인하세요.");
		}
		if (!this.windows && !Files.isExecutable(launcher)) {
			throw new UpdateException("새 버전의 실행 파일에 실행 권한이 없습니다: " + app.launcher());
		}
	}

	private void launch(AppImage app, UpdateManifest target) throws IOException, UpdateException {
		Files.deleteIfExists(this.files.scriptStarted());
		Files.deleteIfExists(this.files.cancelled());
		UpdateScript.Parameters parameters = new UpdateScript.Parameters(this.pid.getAsLong(), app,
				this.dataDirectory.root(), this.files, this.currentVersion, target.version(), this.readyTimeout);
		Path script = this.files.script(this.windows);
		if (this.windows) {
			writeWithBom(script, UpdateScript.powershell(parameters));
			writeWithBom(this.files.windowsLauncherScript(),
					UpdateScript.windowsLauncher(script, this.files.directory()));
			startWindows(script);
		}
		else {
			Files.writeString(script, UpdateScript.bash(parameters), StandardCharsets.UTF_8);
			startDetached(bashCommand(script));
		}
		if (!waitFor(this.files.scriptStarted(), SCRIPT_START_TIMEOUT)) {
			Files.writeString(this.files.cancelled(), "script-start-timeout", StandardCharsets.UTF_8);
			throw new UpdateException("업데이트 스크립트를 실행하지 못했습니다. PC의 스크립트 실행 정책을 확인하세요(update/update.log).");
		}
	}

	private void startWindows(Path script) throws IOException {
		List<String> viaWmi = List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-File", this.files.windowsLauncherScript().toString());
		try {
			Process process = processBuilder(viaWmi).start();
			if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
				return;
			}
			logger.warn("[cozy] WMI로 업데이트 스크립트를 시작하지 못해 직접 시작합니다");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		startDetached(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-WindowStyle", "Hidden", "-File", script.toString()));
	}

	private List<String> bashCommand(Path script) {
		List<String> command = new ArrayList<>();
		if (Files.isExecutable(Path.of("/usr/bin/setsid")) || Files.isExecutable(Path.of("/bin/setsid"))) {
			command.add("setsid");
		}
		command.add("bash");
		command.add(script.toString());
		return command;
	}

	private void startDetached(List<String> command) throws IOException {
		processBuilder(command).start();
	}

	private ProcessBuilder processBuilder(List<String> command) {
		Path output = this.files.directory().resolve("script-output.log");
		ProcessBuilder builder = new ProcessBuilder(command).directory(this.files.directory().toFile())
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.appendTo(output.toFile()));
		removeLauncherEnvironment(builder);
		builder.environment().put("COZY_DATA_DIR", this.dataDirectory.root().toString());
		return builder;
	}

	/**
	 * jpackage 런처는 자기 프로세스에 {@code _JPACKAGE_LAUNCHER}를 넣는다. 자식이 이것을 물려받으면 새 버전의 런처가
	 * cfg를 읽지 않고 바로 실패하므로(종료 코드 1), 스크립트에 넘기지 않는다.
	 */
	static void removeLauncherEnvironment(ProcessBuilder builder) {
		builder.environment().keySet().removeIf((name) -> name.toUpperCase(Locale.ROOT).startsWith("_JPACKAGE"));
	}

	private static boolean waitFor(Path file, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (Files.exists(file)) {
				return true;
			}
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return Files.exists(file);
	}

	private @Nullable String readFailedVersion() {
		try {
			return Files.isRegularFile(this.files.failedVersion())
					? Files.readString(this.files.failedVersion(), StandardCharsets.UTF_8).strip() : null;
		}
		catch (IOException ex) {
			return null;
		}
	}

	static @Nullable Outcome readOutcome(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		Properties result = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			result.load(reader);
		}
		catch (IOException ex) {
			logger.warn("[cozy] 업데이트 결과를 읽을 수 없습니다: " + file, ex);
			return null;
		}
		finally {
			deleteQuietly(file);
		}
		String status = result.getProperty("status", "");
		String message = result.getProperty("message", "업데이트 결과: " + status);
		logger.info("[cozy] 직전 업데이트 결과: " + status + " (" + result.getProperty("from") + " → "
				+ result.getProperty("to") + ")");
		return new Outcome("success".equals(status), message);
	}

	private static void writeWithBom(Path file, String content) throws IOException {
		// Windows PowerShell 5.1은 BOM이 없는 스크립트를 시스템 코드 페이지로 읽어 한글 경로가 깨진다
		byte[] body = content.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
		byte[] bytes = new byte[body.length + 3];
		bytes[0] = (byte) 0xEF;
		bytes[1] = (byte) 0xBB;
		bytes[2] = (byte) 0xBF;
		System.arraycopy(body, 0, bytes, 3, body.length);
		Files.write(file, bytes);
	}

	private static void deleteQuietly(Path path) {
		try {
			ZipArchives.deleteRecursively(path);
		}
		catch (IOException ex) {
			logger.debug("지울 수 없습니다: " + path, ex);
		}
	}

	@Override
	public void start() {
		if (this.channel == null) {
			return;
		}
		if (this.currentVersion == null) {
			logger.warn("[cozy] 앱 버전(META-INF/build-info.properties)을 알 수 없어 업데이트를 확인하지 않습니다. "
					+ "spring-boot-maven-plugin의 build-info를 켜세요.");
			return;
		}
		logger.info("[cozy] 업데이트 채널: " + this.channel + " (확인 주기 " + this.checkInterval + ")");
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor((runnable) -> {
			Thread thread = new Thread(runnable, "cozy-update-check");
			thread.setDaemon(true);
			return thread;
		});
		executor.scheduleWithFixedDelay(this::checkSafely, 0, this.checkInterval.toMillis(), TimeUnit.MILLISECONDS);
		this.scheduler = executor;
	}

	private void checkSafely() {
		try {
			check();
		}
		catch (RuntimeException ex) {
			logger.warn("[cozy] 업데이트 확인 실패", ex);
		}
	}

	@Override
	public void stop() {
		if (this.scheduler != null) {
			this.scheduler.shutdownNow();
			this.scheduler = null;
		}
	}

	@Override
	public boolean isRunning() {
		return this.scheduler != null;
	}

	/** 설치할 수 없는 이유. 메시지는 화면에 그대로 보인다. */
	public static class UpdateException extends Exception {

		private static final long serialVersionUID = 1L;

		public UpdateException(String message) {
			super(message);
		}

		public UpdateException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
