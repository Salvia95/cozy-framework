package io.salvia95.cozy.runtime.internal.diagnostics;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.salvia95.cozy.runtime.CozyDataDirectory;
import io.salvia95.cozy.runtime.CozyDiagnosticsContributor;
import io.salvia95.cozy.runtime.internal.update.AppImage;
import io.salvia95.cozy.runtime.update.CozyUpdates;

/**
 * 진단 내보내기. {@code {dataDir}/diagnostics/diagnostics-{appId}-{시각}.zip}을 만든다.
 *
 * <ul>
 * <li>{@code summary.txt}: 앱·Cozy 버전, JVM, OS, 경로, 업데이트 상태</li>
 * <li>{@code config.txt}: 앱 설정 요약. 시스템 환경 변수와 시스템 속성은 넣지 않는다. 비밀로 보이는 키의 값과 주소 안의 사용자 정보는 가린다.</li>
 * <li>{@code logs/}: {@code {dataDir}/logs}의 최근 파일 (최대 {@value #LOG_LIMIT_MB}MB)</li>
 * <li>{@link CozyDiagnosticsContributor}가 더하는 파일 (Flyway 이력, 미완료 이벤트 개수 등)</li>
 * </ul>
 * {@code secrets.properties}는 읽지도 넣지도 않는다.
 */
public class DiagnosticsExporter {

	static final int LOG_LIMIT_MB = 50;

	public static final String MASK = "******";

	/** 값을 가리는 키 */
	static final Pattern SECRET_KEY = Pattern.compile(
			"(?i).*(password|passwd|pwd|secret|token|credential|private[-_.]?key|api[-_.]?key|access[-_.]?key|auth).*");

	/** 주소 안의 사용자 정보 ({@code scheme://user:pass@host}) */
	static final Pattern URL_USER_INFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)[^/@\\s]+@");

	static final Pattern FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*\\.[a-z0-9]+");

	private static final Set<String> EXCLUDED_SOURCES = Set.of(
			StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
			StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, "random", "configurationProperties");

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final Log logger = LogFactory.getLog(DiagnosticsExporter.class);

	private final ConfigurableEnvironment environment;

	private final CozyDataDirectory dataDirectory;

	private final String appId;

	private final String appName;

	private final String appVersion;

	private final String cozyVersion;

	private final List<CozyDiagnosticsContributor> contributors;

	private final @Nullable CozyUpdates updates;

	private final Clock clock;

	public DiagnosticsExporter(ConfigurableEnvironment environment, CozyDataDirectory dataDirectory, String appId,
			String appName, String appVersion, String cozyVersion, List<CozyDiagnosticsContributor> contributors,
			@Nullable CozyUpdates updates, Clock clock) {
		this.environment = environment;
		this.dataDirectory = dataDirectory;
		this.appId = appId;
		this.appName = appName;
		this.appVersion = appVersion;
		this.cozyVersion = cozyVersion;
		this.contributors = List.copyOf(contributors);
		this.updates = updates;
		this.clock = clock;
	}

	/** 진단 zip을 만들고 경로를 돌려준다. */
	public Path export() throws IOException {
		Path directory = this.dataDirectory.diagnostics();
		Files.createDirectories(directory);
		String time = LocalDateTime.now(this.clock).format(FILE_TIME);
		Path zip = directory.resolve("diagnostics-" + this.appId + "-" + time + ".zip");
		Path part = directory.resolve(zip.getFileName() + ".part");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(part), StandardCharsets.UTF_8)) {
			text(out, "summary.txt", summary());
			text(out, "config.txt", configSummary());
			logs(out);
			for (CozyDiagnosticsContributor contributor : this.contributors) {
				contribute(out, contributor);
			}
		}
		Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING);
		logger.info("[cozy] 진단 파일을 만들었습니다: " + zip);
		return zip;
	}

	String summary() {
		RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
		StringBuilder text = new StringBuilder();
		line(text, "생성 시각", LocalDateTime.now(this.clock).toString());
		line(text, "앱", this.appName + " (" + this.appId + ")");
		line(text, "앱 버전", this.appVersion);
		line(text, "Cozy 버전", this.cozyVersion);
		line(text, "Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ", "
				+ System.getProperty("java.vm.name") + ")");
		line(text, "OS", System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
				+ System.getProperty("os.arch") + ")");
		line(text, "프로세서", Integer.toString(Runtime.getRuntime().availableProcessors()));
		line(text, "최대 메모리", (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + "MB");
		line(text, "실행 시간", Duration.ofMillis(runtime.getUptime()).withNanos(0).toString());
		line(text, "JVM 인수",
				String.join(" ", runtime.getInputArguments().stream().map(DiagnosticsExporter::maskArgument).toList()));
		line(text, "데이터 폴더", this.dataDirectory.root().toString());
		line(text, "앱 폴더", AppImage.current().map((image) -> image.directory().toString()).orElse("(설치본 아님)"));
		if (this.updates != null) {
			line(text, "업데이트", this.updates.offer()
				.map((offer) -> offer.version() + (offer.required() ? " (필수)" : "")
						+ (offer.installable() ? "" : " - " + offer.reason()))
				.orElse("권할 업데이트 없음"));
		}
		return text.toString();
	}

	String configSummary() {
		Map<String, String> sourceOf = new TreeMap<>();
		for (PropertySource<?> source : this.environment.getPropertySources()) {
			if (EXCLUDED_SOURCES.contains(source.getName()) || !(source instanceof EnumerablePropertySource<?> enumerable)) {
				continue;
			}
			for (String name : enumerable.getPropertyNames()) {
				sourceOf.putIfAbsent(name, source.getName());
			}
		}
		StringBuilder text = new StringBuilder("# 이름 = 값  [출처]. 비밀로 보이는 값은 " + MASK + "로 가렸다.\n");
		sourceOf.forEach((name, source) -> text.append(name)
			.append(" = ")
			.append(maskValue(name, resolve(name)))
			.append("  [")
			.append(source)
			.append("]\n"));
		return text.toString();
	}

	private String resolve(String name) {
		try {
			String value = this.environment.getProperty(name);
			return (value != null) ? value : "";
		}
		catch (RuntimeException ex) {
			return "(값을 풀 수 없음: " + ex.getMessage() + ")";
		}
	}

	static String maskValue(String key, String value) {
		if (SECRET_KEY.matcher(key).matches()) {
			return value.isEmpty() ? "" : MASK;
		}
		return URL_USER_INFO.matcher(value).replaceAll("$1" + MASK + "@");
	}

	static String maskArgument(String argument) {
		int equals = argument.indexOf('=');
		if (argument.startsWith("-D") && equals > 2) {
			String key = argument.substring(2, equals);
			return "-D" + key + "=" + maskValue(key, argument.substring(equals + 1));
		}
		return URL_USER_INFO.matcher(argument).replaceAll("$1" + MASK + "@");
	}

	private void logs(ZipOutputStream out) throws IOException {
		Path logs = this.dataDirectory.logs();
		if (!Files.isDirectory(logs)) {
			return;
		}
		List<Path> files;
		try (Stream<Path> list = Files.list(logs)) {
			files = new ArrayList<>(list.filter(Files::isRegularFile).toList());
		}
		files.sort(Comparator.comparing(DiagnosticsExporter::lastModified).reversed());
		long budget = LOG_LIMIT_MB * 1024L * 1024L;
		List<String> skipped = new ArrayList<>();
		for (Path file : files) {
			long size = Files.size(file);
			if (size > budget) {
				skipped.add(file.getFileName().toString());
				continue;
			}
			budget -= size;
			out.putNextEntry(new ZipEntry("logs/" + file.getFileName()));
			Files.copy(file, out);
			out.closeEntry();
		}
		if (!skipped.isEmpty()) {
			text(out, "logs/SKIPPED.txt", LOG_LIMIT_MB + "MB를 넘어 넣지 않은 파일:\n" + String.join("\n", skipped) + "\n");
		}
	}

	private void contribute(ZipOutputStream out, CozyDiagnosticsContributor contributor) throws IOException {
		String name = contributor.fileName();
		if (name == null || !FILE_NAME.matcher(name).matches()) {
			logger.warn("[cozy] 진단 파일 이름이 규칙과 달라 건너뜁니다: " + name + " (" + contributor.getClass().getName() + ")");
			return;
		}
		String content;
		try {
			content = contributor.content();
		}
		catch (Exception ex) {
			content = "(내용을 만들 수 없습니다: " + ex + ")\n";
		}
		text(out, name, content);
	}

	private static void text(ZipOutputStream out, String name, String content) throws IOException {
		out.putNextEntry(new ZipEntry(name));
		out.write(content.getBytes(StandardCharsets.UTF_8));
		out.closeEntry();
	}

	private static void line(StringBuilder text, String label, String value) {
		text.append(label).append(": ").append(value).append('\n');
	}

	private static long lastModified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		}
		catch (IOException ex) {
			return 0;
		}
	}
}
