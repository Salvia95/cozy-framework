package io.salvia95.cozy.runtime.internal.dist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 배포물의 제3자 라이선스 목록 (PRD §11).
 *
 * <ul>
 * <li>의존성: license-maven-plugin(add-third-party)이 만든 {@code THIRD-PARTY.txt}를 다시 읽어, 모든 의존성에 허용
 * 라이선스가 하나 이상 있는지 확인한다. 플러그인도 같은 목록으로 검사하지만, 이 검사를 한 번 더 두어 플러그인 설정이나
 * 동작이 바뀌어도 실패로 닫는다. 이중 라이선스(예: EPL-2.0 또는 LGPL-2.1)는 하나만 허용 목록에 있으면 된다.</li>
 * <li>웹 자산: 클래스패스 jar의 {@code META-INF/resources/cozy/vendor/{이름}/{버전}/LICENSE}를 함께 담는다
 * (Tabler, htmx, Pretendard). Maven 의존성이 아니라 플러그인 목록에 나오지 않는다.</li>
 * <li>Cozy 자신: 이 jar의 {@code META-INF/cozy/LICENSE}, {@code NOTICE}(Apache-2.0)를 함께 담는다.</li>
 * </ul>
 *
 * 결과는 실행 폴더의 {@code licenses/}다. JDK 런타임의 라이선스는 jpackage가 런타임 폴더의 {@code legal/}에 둔다.
 */
final class ThirdPartyLicenses {

	static final String FOLDER = "licenses";

	private static final Pattern HEADER = Pattern.compile("Lists of (\\d+) third-party dependencies\\.");

	private static final Pattern LICENSE = Pattern.compile("\\G\\s*\\(([^()]*)\\)");

	private static final Pattern COORDINATES = Pattern.compile("\\(([\\w.\\-]+:[\\w.\\-]+:[^\\s()]+)");

	private static final Pattern VENDOR_LICENSE = Pattern.compile("META-INF/resources/cozy/vendor/([^/]+)/([^/]+)/LICENSE");

	private ThirdPartyLicenses() {
	}

	/** 목록의 한 줄: 의존성 좌표와 라이선스들 */
	record Dependency(String coordinates, List<String> licenses) {
	}

	/** 목록을 읽는다. 형식이 맞지 않거나 머리말의 개수와 줄 수가 다르면 실패한다 */
	static List<Dependency> parse(String text) {
		if (text.strip().equals("The project has no dependencies.")) {
			return List.of();
		}
		Matcher header = HEADER.matcher(text);
		if (!header.find()) {
			throw new IllegalStateException("THIRD-PARTY.txt 형식이 아닙니다 (머리말 'Lists of N third-party dependencies.'가 없습니다)");
		}
		int expected = Integer.parseInt(header.group(1));
		List<Dependency> dependencies = new ArrayList<>();
		for (String line : text.substring(header.end()).split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			List<String> licenses = new ArrayList<>();
			Matcher license = LICENSE.matcher(line);
			int end = 0;
			while (license.find()) {
				licenses.add(license.group(1).strip());
				end = license.end();
			}
			String rest = line.substring(end);
			Matcher coordinates = COORDINATES.matcher(rest);
			String found = null;
			while (coordinates.find()) {
				found = coordinates.group(1);
			}
			if (licenses.isEmpty() || found == null) {
				throw new IllegalStateException("THIRD-PARTY.txt의 줄을 읽을 수 없습니다: " + line.strip());
			}
			dependencies.add(new Dependency(found, List.copyOf(licenses)));
		}
		if (dependencies.size() != expected) {
			throw new IllegalStateException("THIRD-PARTY.txt의 의존성 수가 머리말(" + expected + ")과 다릅니다: "
					+ dependencies.size());
		}
		return dependencies;
	}

	/** 허용 라이선스가 하나도 없는 의존성 */
	static List<String> violations(List<Dependency> dependencies, Set<String> allowed) {
		return dependencies.stream()
			.filter((dependency) -> dependency.licenses().stream().noneMatch(allowed::contains))
			.map((dependency) -> dependency.coordinates() + " " + dependency.licenses())
			.toList();
	}

	static Set<String> allowedSet(String allowed) {
		return Arrays.stream(allowed.split("\\|")).map(String::strip).filter((s) -> !s.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * 목록을 검사하고 {@code image/licenses/}에 담는다.
	 * @return 담은 의존성 수와 웹 자산 수
	 */
	static int[] bundle(Path thirdParty, String allowed, Path libDir, Path image) throws IOException {
		if (!Files.isRegularFile(thirdParty)) {
			throw new IllegalStateException("제3자 라이선스 목록이 없습니다: " + thirdParty
					+ " (cozy-parent dist 프로필의 license-maven-plugin이 만든다)");
		}
		Set<String> allowedSet = allowedSet(allowed);
		if (allowedSet.isEmpty()) {
			throw new IllegalArgumentException("cozy.dist.allowed-licenses가 비어 있습니다");
		}
		String text = Files.readString(thirdParty, StandardCharsets.UTF_8);
		List<Dependency> dependencies = parse(text);
		List<String> violations = violations(dependencies, allowedSet);
		if (!violations.isEmpty()) {
			throw new IllegalStateException("허용 목록(" + String.join(", ", new TreeSet<>(allowedSet))
					+ ")에 없는 라이선스의 의존성이 있습니다. 의존성을 바꾸거나, 라이선스를 검토한 뒤 cozy-parent의 cozy.dist.allowed-licenses를 고치세요:\n  "
					+ String.join("\n  ", violations));
		}

		Path folder = image.resolve(FOLDER);
		Files.createDirectories(folder);
		for (String name : List.of("LICENSE", "NOTICE")) {
			try (InputStream in = ThirdPartyLicenses.class.getResourceAsStream("/META-INF/cozy/" + name)) {
				if (in == null) {
					throw new IllegalStateException("cozy-starter-runtime jar에 META-INF/cozy/" + name + "가 없습니다");
				}
				Files.copy(in, folder.resolve("cozy-" + name + ".txt"));
			}
		}
		var vendors = new TreeMap<String, byte[]>();
		try (Stream<Path> jars = Files.list(libDir)) {
			for (Path jar : jars.filter((p) -> p.toString().endsWith(".jar")).sorted().toList()) {
				try (ZipFile zip = new ZipFile(jar.toFile())) {
					Enumeration<? extends ZipEntry> entries = zip.entries();
					while (entries.hasMoreElements()) {
						ZipEntry entry = entries.nextElement();
						Matcher vendor = VENDOR_LICENSE.matcher(entry.getName());
						if (vendor.matches()) {
							try (InputStream in = zip.getInputStream(entry)) {
								vendors.put(vendor.group(1) + "-" + vendor.group(2), in.readAllBytes());
							}
						}
					}
				}
			}
		}
		StringBuilder list = new StringBuilder("Cozy is licensed under the Apache License 2.0 (cozy-LICENSE.txt, cozy-NOTICE.txt).\n")
			.append(text.stripTrailing()).append("\n\n");
		if (!vendors.isEmpty()) {
			list.append("Bundled web assets (license texts in vendor/):\n\n");
			for (var vendor : vendors.entrySet()) {
				Path target = folder.resolve("vendor").resolve(vendor.getKey() + "-LICENSE.txt");
				Files.createDirectories(target.getParent());
				Files.write(target, vendor.getValue());
				list.append("     ").append(vendor.getKey()).append(" (vendor/").append(target.getFileName()).append(")\n");
			}
			list.append('\n');
		}
		list.append("Java runtime: see the legal/ folder of the bundled runtime.\n");
		Files.writeString(folder.resolve("THIRD-PARTY.txt"), list, StandardCharsets.UTF_8);
		return new int[] { dependencies.size(), vendors.size() };
	}
}
