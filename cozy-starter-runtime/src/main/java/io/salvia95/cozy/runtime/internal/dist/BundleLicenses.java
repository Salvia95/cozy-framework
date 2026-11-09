package io.salvia95.cozy.runtime.internal.dist;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 오프라인 번들 저장소(repository/)의 제3자 라이선스 목록.
 *
 * 번들은 앱 빌드에 필요한 Maven 플러그인과 의존성을 그대로 재배포한다. 그래서 저장소의 모든 아티팩트를 검사한다.
 *
 * <ul>
 * <li>라이선스는 아티팩트 POM의 {@code <licenses>}, 없으면 부모 POM을 따라 올라가 찾는다(Maven 상속과 같다).
 * 이름이 제각각이라 SPDX ID로 맞춘다. 이름으로 정할 수 없으면 URL로 정한다.</li>
 * <li>POM으로 정할 수 없는 아티팩트는 예외 목록({@code g:a:v  SPDX  # 근거})에 근거와 함께 적는다. 버전까지 맞아야
 * 하므로 버전이 바뀌면 다시 검토하게 된다.</li>
 * <li>허용 라이선스 하나 이상이 있어야 한다. 허용 라이선스는 재배포를 허용하는 두 묶음이다.
 * <ul>
 * <li>허용형({@link #PERMISSIVE}): 목록과 라이선스 안내만 있으면 된다.</li>
 * <li>약한 카피레프트({@link #WEAK_COPYLEFT}): 바이너리를 재배포할 때 소스를 얻는 방법을 함께 줘야 한다. 그래서
 * 허용형 선택지가 없는 jar는 소스 jar({@code -sources.jar})를 번들에 함께 담는다.</li>
 * </ul>
 * 나머지(GPL, AGPL, 알 수 없는 이름 등)는 실패로 닫는다.</li>
 * </ul>
 *
 * 실행: {@code java -cp cozy-starter-runtime.jar io.salvia95.cozy.runtime.internal.dist.BundleLicenses
 * --repository=... --overrides=... --output=... [--title=...] [--maven-distribution=...] [--fetch-sources=URL]}
 */
public final class BundleLicenses {

	static final String GPL2_CPE = "GPL-2.0-only WITH Classpath-exception-2.0";

	/** 재배포 때 목록과 라이선스 안내만 있으면 되는 라이선스 */
	static final Set<String> PERMISSIVE = Set.of("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "0BSD", "ISC",
			"UPL-1.0", "Unicode-3.0", "W3C", "SAX-PD", "LicenseRef-Public-Domain", "LicenseRef-JDOM");

	/** 재배포 때 소스를 함께 주는 라이선스 */
	static final Set<String> WEAK_COPYLEFT = Set.of("EPL-1.0", "EPL-2.0", "MPL-2.0", "LGPL-2.1-only",
			"LGPL-2.1-or-later", "LGPL-3.0-only", "LGPL-3.0-or-later", "CDDL-1.0", "CDDL-1.1", GPL2_CPE);

	static final String COZY_GROUP = "io.salvia95.cozy";

	private static final String V = "\\W*(?:v|version|ver)?\\W*";

	/** 이름(소문자, 앞뒤 공백 제거)을 SPDX ID로. 앞의 것부터 맞춘다 */
	private static final Map<Pattern, String> NAMES = new LinkedHashMap<>();

	/** 이름으로 정하지 못했을 때 URL로 */
	private static final Map<Pattern, String> URLS = new LinkedHashMap<>();

	static {
		name("(?:the )?(?:apache|asf)(?: software| public)?(?: license)?,?" + V + "2(?:\\.0)?|apache-2\\.0", "Apache-2.0");
		name("(?:the )?mit(?: license)?(?: \\(mit\\))?", "MIT");
		name("(?:the )?(?:bsd[- ]2[- ]clause|simplified bsd)(?: license)?", "BSD-2-Clause");
		name("(?:the )?(?:bsd[- ]3[- ]clause|new bsd|revised bsd)(?: license)?", "BSD-3-Clause");
		name("0bsd|bsd zero clause license", "0BSD");
		name("(?:the )?isc(?: license)?", "ISC");
		name("(?:eclipse distribution license|edl)" + V + "1(?:\\.0)?", "BSD-3-Clause");
		name("(?:the )?(?:eclipse public license|epl)" + V + "1(?:\\.0)?", "EPL-1.0");
		name("(?:the )?(?:eclipse public license|epl)" + V + "2(?:\\.0)?", "EPL-2.0");
		name("(?:the )?(?:mozilla public license|mpl)" + V + "2(?:\\.0)?", "MPL-2.0");
		name("(?:common development and distribution license|cddl)" + V + "1\\.0", "CDDL-1.0");
		name("(?:common development and distribution license|cddl)" + V + "1\\.1", "CDDL-1.1");
		name(".*(?:gpl|general public license).*2.*(?:classpath|cpe).*", GPL2_CPE);
		name("(?:the )?(?:gnu )?(?:lesser|library) general public license(?: \\(lgpl\\))?" + V + "2\\.1|lgpl-2\\.1(?:-only)?", "LGPL-2.1-only");
		name("lgpl-2\\.1-or-later", "LGPL-2.1-or-later");
		name("(?:the )?(?:gnu )?lesser general public license(?: \\(lgpl\\))?" + V + "3(?:\\.0)?|lgpl-3\\.0(?:-only)?", "LGPL-3.0-only");
		name("lgpl-3\\.0-or-later", "LGPL-3.0-or-later");
		name("(?:the )?(?:gnu )?general public license(?: \\(gpl\\))?" + V + "2(?:\\.0)?|gpl-?2\\.0(?:-only)?", "GPL-2.0-only");
		name("(?:the )?(?:gnu )?general public license(?: \\(gpl\\))?" + V + "3(?:\\.0)?|gpl-?3\\.0(?:-only)?", "GPL-3.0-only");
		name("(?:the )?(?:gnu )?affero general public license" + V + "3(?:\\.0)?|agpl-3\\.0(?:-only)?", "AGPL-3.0-only");
		name("(?:universal permissive license|upl)" + V + "1(?:\\.0)?", "UPL-1.0");
		name("unicode-3\\.0", "Unicode-3.0");
		name("(?:the )?w3c(?: software)? license", "W3C");
		name("(?:the )?sax license", "SAX-PD");
		name("public domain", "LicenseRef-Public-Domain");

		url("apache\\.org/licenses/license-2\\.0|opensource\\.org/licenses?/apache-2\\.0", "Apache-2.0");
		url("opensource\\.org/licenses?/mit|mit-license", "MIT");
		url("opensource\\.org/licenses?/bsd-2-clause|opensource\\.org/licenses/bsd-license\\.php", "BSD-2-Clause");
		url("opensource\\.org/licenses?/bsd-3-clause|asm\\.ow2\\.io/license|antlr\\.org/license", "BSD-3-Clause");
	}

	private static void name(String regex, String id) {
		NAMES.put(Pattern.compile(regex), id);
	}

	private static void url(String regex, String id) {
		URLS.put(Pattern.compile(regex), id);
	}

	private BundleLicenses() {
	}

	/**
	 * 저장소의 아티팩트 하나.
	 * @param licenses SPDX ID. 맞추지 못한 이름은 {@code ?이름}
	 * @param from 라이선스를 정한 곳: {@code POM}, {@code parent g:a:v}, {@code override: 근거}, {@code Cozy}, 없으면 null
	 * @param jar jar가 있는지 (POM만 있는 부모·BOM은 false)
	 */
	record Artifact(String coordinates, Path folder, String fileBase, List<String> licenses, String from, boolean jar) {

		boolean permissive() {
			return this.licenses.stream().anyMatch(PERMISSIVE::contains);
		}

		boolean allowed() {
			return permissive() || this.licenses.stream().anyMatch(WEAK_COPYLEFT::contains);
		}

		/** 허용형 선택지가 없는 jar: 소스 jar를 함께 담는다 */
		boolean needsSources() {
			return this.jar && allowed() && !permissive();
		}

		Path sourcesJar() {
			return this.folder.resolve(this.fileBase + "-sources.jar");
		}
	}

	/** 예외 목록 한 줄: 좌표 → SPDX ID와 근거 */
	record Override(List<String> licenses, String reason) {
	}

	/** 이름과 URL을 SPDX ID로. 정할 수 없으면 null */
	static String normalize(String name, String url) {
		String n = name == null ? "" : name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		for (var entry : NAMES.entrySet()) {
			if (entry.getKey().matcher(n).matches()) {
				return entry.getValue();
			}
		}
		String u = url == null ? "" : url.strip().toLowerCase(Locale.ROOT);
		if (!u.isEmpty()) {
			for (var entry : URLS.entrySet()) {
				if (entry.getKey().matcher(u).find()) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	/** 예외 목록: {@code groupId:artifactId:version  SPDX[|SPDX]  # 근거}. 근거가 없으면 실패한다 */
	static Map<String, Override> parseOverrides(String text) {
		Map<String, Override> overrides = new LinkedHashMap<>();
		for (String line : text.split("\\R")) {
			String stripped = line.strip();
			if (stripped.isEmpty() || stripped.startsWith("#")) {
				continue;
			}
			int hash = stripped.indexOf('#');
			String reason = hash < 0 ? "" : stripped.substring(hash + 1).strip();
			String[] parts = (hash < 0 ? stripped : stripped.substring(0, hash)).strip().split("\\s+");
			if (parts.length != 2 || parts[0].split(":").length != 3) {
				throw new IllegalArgumentException("예외 목록 형식은 'groupId:artifactId:version  SPDX  # 근거'입니다: " + stripped);
			}
			if (reason.isEmpty()) {
				throw new IllegalArgumentException("예외 목록의 줄에 근거(# 뒤)가 없습니다: " + stripped);
			}
			overrides.put(parts[0], new Override(List.of(parts[1].split("\\|")), reason));
		}
		return overrides;
	}

	/** 저장소의 모든 POM을 읽는다. 좌표 순서로 돌려준다 */
	static List<Artifact> scan(Path repository, Map<String, Override> overrides) throws IOException {
		List<Path> poms;
		try (Stream<Path> files = Files.walk(repository)) {
			poms = files.filter((p) -> p.getFileName().toString().endsWith(".pom")).toList();
		}
		var artifacts = new TreeMap<String, Artifact>();
		for (Path pom : poms) {
			Path folder = pom.getParent();
			Path relative = repository.relativize(folder);
			int count = relative.getNameCount();
			if (count < 3) {
				continue;
			}
			String version = relative.getName(count - 1).toString();
			String artifactId = relative.getName(count - 2).toString();
			String fileBase = artifactId + "-" + version;
			if (!pom.getFileName().toString().equals(fileBase + ".pom")) {
				continue;
			}
			String groupId = relative.subpath(0, count - 2).toString().replace(folder.getFileSystem().getSeparator(), ".");
			String coordinates = groupId + ":" + artifactId + ":" + version;
			boolean jar = Files.isRegularFile(folder.resolve(fileBase + ".jar"));
			artifacts.put(coordinates, resolve(repository, coordinates, groupId, folder, fileBase, pom, jar, overrides));
		}
		return List.copyOf(artifacts.values());
	}

	private static Artifact resolve(Path repository, String coordinates, String groupId, Path folder, String fileBase,
			Path pom, boolean jar, Map<String, Override> overrides) {
		Override override = overrides.get(coordinates);
		if (override != null) {
			return new Artifact(coordinates, folder, fileBase, override.licenses(), "override: " + override.reason(), jar);
		}
		if (groupId.equals(COZY_GROUP) || groupId.startsWith(COZY_GROUP + ".")) {
			// cozy-parent는 앱이 상속하므로 라이선스를 선언하지 않는다. Cozy 모듈은 모두 Apache-2.0이다
			return new Artifact(coordinates, folder, fileBase, List.of("Apache-2.0"), "Cozy", jar);
		}
		Path current = pom;
		String from = "POM";
		for (int depth = 0; depth < 30 && current != null; depth++) {
			Element project = parse(current);
			if (project == null) {
				return new Artifact(coordinates, folder, fileBase, List.of("?POM을 읽을 수 없음: " + current.getFileName()),
						from, jar);
			}
			List<String> licenses = licenses(project);
			if (!licenses.isEmpty()) {
				return new Artifact(coordinates, folder, fileBase, licenses, from, jar);
			}
			Element parent = child(project, "parent");
			current = null;
			if (parent != null) {
				String g = text(child(parent, "groupId"));
				String a = text(child(parent, "artifactId"));
				String v = text(child(parent, "version"));
				Path parentPom = repository.resolve(g.replace('.', '/')).resolve(a).resolve(v).resolve(a + "-" + v + ".pom");
				if (!g.isEmpty() && !a.isEmpty() && !v.isEmpty() && Files.isRegularFile(parentPom)) {
					current = parentPom;
					from = "parent " + g + ":" + a + ":" + v;
				}
			}
		}
		return new Artifact(coordinates, folder, fileBase, List.of(), null, jar);
	}

	private static List<String> licenses(Element project) {
		Element licenses = child(project, "licenses");
		List<String> ids = new ArrayList<>();
		if (licenses == null) {
			return ids;
		}
		for (Node node = licenses.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element license && "license".equals(local(license))) {
				String name = text(child(license, "name"));
				String id = normalize(name, text(child(license, "url")));
				ids.add(id != null ? id : "?" + name);
			}
		}
		return ids;
	}

	private static Element parse(Path pom) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setExpandEntityReferences(false);
			factory.setNamespaceAware(true);
			return factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
		}
		catch (Exception ex) {
			return null;
		}
	}

	private static Element child(Element parent, String name) {
		if (parent == null) {
			return null;
		}
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && name.equals(local(element))) {
				return element;
			}
		}
		return null;
	}

	private static String local(Element element) {
		return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
	}

	private static String text(Element element) {
		return element == null ? "" : element.getTextContent().strip();
	}

	/** 허용 라이선스가 하나도 없는 아티팩트 */
	static List<String> problems(Collection<Artifact> artifacts) {
		return artifacts.stream()
			.filter((artifact) -> !artifact.allowed())
			.map((artifact) -> artifact.licenses().isEmpty() ? artifact.coordinates() + " [라이선스 정보 없음]"
					: artifact.coordinates() + " " + artifact.licenses() + " (" + artifact.from() + ")")
			.toList();
	}

	/** 소스 jar를 함께 담아야 하는데 아직 없는 아티팩트 */
	static List<Artifact> missingSources(Collection<Artifact> artifacts) {
		return artifacts.stream()
			.filter((artifact) -> artifact.needsSources() && !Files.isRegularFile(artifact.sourcesJar()))
			.toList();
	}

	/**
	 * 소스 jar를 Maven 저장소 배치로 받는다. 같은 곳의 {@code .sha1}과 맞지 않거나 받을 수 없으면 실패한다.
	 * @param base Maven 저장소 주소 (예: https://repo.maven.apache.org/maven2/)
	 */
	static void fetchSources(List<Artifact> artifacts, URI base, PrintStream out) throws IOException {
		URI root = base.toString().endsWith("/") ? base : URI.create(base + "/");
		for (Artifact artifact : artifacts) {
			String[] gav = artifact.coordinates().split(":");
			String path = gav[0].replace('.', '/') + "/" + gav[1] + "/" + gav[2] + "/" + artifact.fileBase() + "-sources.jar";
			try {
				byte[] jar = download(root.resolve(path));
				String expected = new String(download(root.resolve(path + ".sha1")), StandardCharsets.US_ASCII).strip()
					.split("\\s+")[0].toLowerCase(Locale.ROOT);
				String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(jar));
				if (!actual.equals(expected)) {
					throw new IOException("SHA-1이 맞지 않습니다 (받은 파일 " + actual + ", .sha1 " + expected + ")");
				}
				Path temp = Files.write(artifact.folder().resolve(artifact.fileBase() + "-sources.jar.part"), jar);
				Files.move(temp, artifact.sourcesJar(), StandardCopyOption.REPLACE_EXISTING);
				Files.writeString(artifact.folder().resolve(artifact.fileBase() + "-sources.jar.sha1"), expected);
				out.println("[cozy] 소스 jar: " + artifact.coordinates() + " (" + artifact.licenses() + ")");
			}
			catch (IOException | NoSuchAlgorithmException ex) {
				throw new IOException("소스 jar를 받지 못했습니다: " + artifact.coordinates() + " " + artifact.licenses()
						+ " ← " + root.resolve(path) + ": " + ex.getMessage(), ex);
			}
		}
	}

	private static byte[] download(URI uri) throws IOException {
		URLConnection connection = uri.toURL().openConnection();
		connection.setConnectTimeout(30_000);
		connection.setReadTimeout(60_000);
		try (InputStream in = connection.getInputStream()) {
			return in.readAllBytes();
		}
	}

	/** 목록 파일 내용 */
	static String render(String title, String mavenDistribution, List<Artifact> artifacts) {
		StringBuilder list = new StringBuilder(title).append(": third-party components\n\n");
		list.append("Cozy itself (").append(COZY_GROUP).append(":*) is licensed under the Apache License 2.0 (LICENSE, NOTICE).\n");
		if (mavenDistribution != null && !mavenDistribution.isBlank()) {
			list.append("Apache Maven (").append(mavenDistribution)
				.append(") is licensed under the Apache License 2.0; its LICENSE, NOTICE and lib/ license files are inside the archive.\n");
		}
		list.append("""
				repository/ is a Maven repository with the build tools and libraries needed to build Cozy apps offline.
				Each line below lists the licenses declared in the artifact's POM (or the nearest parent POM that declares them),
				as SPDX identifiers. The license texts are inside each jar (META-INF/) or at the URL in its POM.
				Artifacts available only under weak copyleft licenses (EPL, MPL, LGPL, CDDL, GPL with Classpath Exception)
				are accompanied by their source jar (*-sources.jar) next to the jar.

				""");
		list.append("Lists of ").append(artifacts.size()).append(" artifacts in repository/.\n");
		for (Artifact artifact : artifacts) {
			list.append("     ");
			for (String license : artifact.licenses()) {
				list.append('(').append(license).append(") ");
			}
			list.append(artifact.coordinates());
			if (artifact.needsSources()) {
				String[] gav = artifact.coordinates().split(":");
				list.append(" [source: repository/").append(gav[0].replace('.', '/')).append('/').append(gav[1]).append('/')
					.append(gav[2]).append('/').append(artifact.fileBase()).append("-sources.jar]");
			}
			if (artifact.from() != null && artifact.from().startsWith("override: ")) {
				list.append(" [").append(artifact.from()).append(']');
			}
			list.append('\n');
		}
		return list.toString();
	}

	static int run(List<String> args, PrintStream out) {
		Map<String, String> options = new LinkedHashMap<>();
		for (String arg : args) {
			int equals = arg.indexOf('=');
			if (!arg.startsWith("--") || equals < 0) {
				out.println("[cozy] 인수 형식은 --이름=값입니다: " + arg);
				return 2;
			}
			options.put(arg.substring(2, equals), arg.substring(equals + 1));
		}
		try {
			Path repository = Path.of(required(options, "repository"));
			Path output = Path.of(required(options, "output"));
			Map<String, Override> overrides = parseOverrides(
					Files.readString(Path.of(required(options, "overrides")), StandardCharsets.UTF_8));
			List<Artifact> artifacts = scan(repository, overrides);
			if (artifacts.isEmpty()) {
				throw new IllegalStateException("저장소에 POM이 없습니다: " + repository);
			}
			List<String> problems = problems(artifacts);
			if (!problems.isEmpty()) {
				out.println("[cozy] 재배포 허용 라이선스(" + String.join(", ", new TreeSet<>(PERMISSIVE)) + "; "
						+ String.join(", ", new TreeSet<>(WEAK_COPYLEFT))
						+ ")를 확인할 수 없는 아티팩트가 있습니다. 상위 저장소의 라이선스를 확인하고 예외 목록에 근거와 함께 적거나, 의존성을 바꾸세요:");
				problems.forEach((problem) -> out.println("  " + problem));
				return 1;
			}
			List<Artifact> missing = missingSources(artifacts);
			if (!missing.isEmpty() && options.containsKey("fetch-sources")) {
				fetchSources(missing, URI.create(options.get("fetch-sources")), out);
				missing = missingSources(artifacts);
			}
			if (!missing.isEmpty()) {
				out.println("[cozy] 소스 jar가 없습니다 (약한 카피레프트만 있는 jar, --fetch-sources로 받는다):");
				missing.forEach((artifact) -> out.println("  " + artifact.coordinates() + " " + artifact.licenses()));
				return 1;
			}
			Set<String> used = new TreeSet<>(overrides.keySet());
			artifacts.forEach((artifact) -> used.remove(artifact.coordinates()));
			used.forEach((stale) -> out.println("[cozy] 참고: 예외 목록의 " + stale + "는 저장소에 없습니다"));

			Files.createDirectories(output.toAbsolutePath().getParent());
			Files.writeString(output, render(options.getOrDefault("title", "Cozy offline bundle"),
					options.get("maven-distribution"), artifacts), StandardCharsets.UTF_8);
			long sources = artifacts.stream().filter(Artifact::needsSources).count();
			out.println("[cozy] 번들 라이선스 목록: 아티팩트 " + artifacts.size() + "개, 소스 jar " + sources + "개 → " + output);
			return 0;
		}
		catch (Exception ex) {
			out.println("[cozy] 번들 라이선스 목록을 만들지 못했습니다: " + ex.getMessage());
			return 1;
		}
	}

	private static String required(Map<String, String> options, String name) {
		String value = options.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("--" + name + "가 필요합니다");
		}
		return value;
	}

	public static void main(String[] args) {
		System.exit(run(List.of(args), System.out));
	}
}
