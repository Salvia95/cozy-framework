package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 벤더 자산과 vendor.lock이 일치하고, 외부 네트워크 참조가 없다 (PRD §6.4, §11).
 */
class VendorAssetsTest {

	static final Path RESOURCES = Path.of("src/main/resources");

	static final Path VENDOR = RESOURCES.resolve("META-INF/resources/cozy/vendor");

	static final Path LOCK = Path.of("vendor.lock");

	@Test
	void filesMatchLock() throws Exception {
		Map<String, String> lock = readLock();
		Map<String, String> actual = new LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(VENDOR)) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				actual.put(VENDOR.relativize(file).toString().replace('\\', '/'), sha256(file));
			}
		}
		assertThat(actual).as("vendor.lock과 파일이 다르면 distribution/vendor-assets/fetch.sh를 다시 실행하세요")
			.containsExactlyInAnyOrderEntriesOf(lock);
	}

	@Test
	void everyVendorVersionHasLicense() throws Exception {
		try (Stream<Path> names = Files.list(VENDOR)) {
			for (Path name : names.filter(Files::isDirectory).toList()) {
				try (Stream<Path> versions = Files.list(name)) {
					for (Path version : versions.toList()) {
						assertThat(version.resolve("LICENSE")).as("%s에 LICENSE가 없습니다", version).isRegularFile();
					}
				}
			}
		}
	}

	@Test
	void templatesOnlyReferenceExistingLocalAssets() throws Exception {
		// 자산 참조(href, src)만 본다. 폼 action(/cozy/shutdown 등)은 런타임의 엔드포인트다
		Pattern asset = Pattern.compile("th:(?:href|src)=\"@\\{/cozy/([^}(]+)\\}");
		List<String> missing = new ArrayList<>();
		for (Path template : files(RESOURCES.resolve("templates"), ".html")) {
			Matcher m = asset.matcher(Files.readString(template));
			while (m.find()) {
				if (m.group(1).startsWith("dev/")) {
					continue;
				}
				Path file = RESOURCES.resolve("META-INF/resources/cozy").resolve(m.group(1));
				if (!Files.isRegularFile(file)) {
					missing.add(template + " → /cozy/" + m.group(1));
				}
			}
		}
		assertThat(missing).isEmpty();
	}

	@Test
	void noExternalNetworkReferences() throws Exception {
		// CSS의 url()/@import, 템플릿의 src/href/action: 외부 주소(http://, https://, //)가 없어야 한다
		Pattern cssRef = Pattern.compile("(?:url\\(\\s*['\"]?|@import\\s+['\"])\\s*((?:https?:)?//[^'\")\\s]+)");
		Pattern htmlRef = Pattern.compile("(?:src|href|action)\\s*=\\s*['\"]\\s*((?:https?:)?//[^'\"]+)");
		List<String> found = new ArrayList<>();
		for (Path css : files(RESOURCES.resolve("META-INF/resources/cozy"), ".css")) {
			Matcher m = cssRef.matcher(Files.readString(css));
			while (m.find()) {
				found.add(css + " → " + m.group(1));
			}
		}
		for (Path html : files(RESOURCES.resolve("templates"), ".html")) {
			Matcher m = htmlRef.matcher(Files.readString(html));
			while (m.find()) {
				found.add(html + " → " + m.group(1));
			}
		}
		assertThat(found).isEmpty();
	}

	private static List<Path> files(Path root, String suffix) throws IOException {
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter((p) -> p.toString().endsWith(suffix)).sorted().toList();
		}
	}

	private static Map<String, String> readLock() throws IOException {
		Map<String, String> lock = new LinkedHashMap<>();
		for (String line : Files.readAllLines(LOCK)) {
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			String[] parts = line.trim().split("\\s+");
			lock.put(parts[0], parts[1]);
		}
		assertThat(new TreeSet<>(lock.keySet())).isNotEmpty();
		return lock;
	}

	private static String sha256(Path file) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
	}
}
