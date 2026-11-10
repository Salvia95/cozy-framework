package io.salvia95.cozy.cli.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.TemplateFile;

/**
 * cozy-ui jar의 프레임워크 자원: 템플릿({@code templates/cozy/}, {@code templates/error/}), 매니페스트, Tabler Icons 스프라이트.
 * cozy 명령은 cozy-ui의 클래스를 쓰지 않고 jar를 zip으로(또는 풀어 놓은 폴더를) 읽는다.
 */
public final class FrameworkJar {

	private static final Pattern OUTLINE = Pattern.compile("<symbol id=\"tabler-(?!filled-)([a-z0-9-]+)\"");

	private static final Pattern FILLED = Pattern.compile("<symbol id=\"tabler-filled-([a-z0-9-]+)\"");

	private static final Pattern SPRITE = Pattern.compile(
			"META-INF/resources/cozy/vendor/tabler-icons/[^/]+/tabler-sprite(-filled)?\\.svg");

	private final List<TemplateFile> templates = new ArrayList<>();

	private String manifest;

	private final Set<String> icons = new TreeSet<>();

	private final Set<String> filledIcons = new TreeSet<>();

	private FrameworkJar() {
	}

	/** cozy-ui를 쓰지 않는 앱: 프레임워크 자원이 없다 (cozy-test와 같다) */
	public static FrameworkJar none() {
		return new FrameworkJar();
	}

	/** 저장소 배치(Maven)의 cozy-ui jar 위치 */
	public static Path locate(Path repository, String version) {
		return repository.resolve("io/salvia95/cozy/cozy-ui/" + version + "/cozy-ui-" + version + ".jar");
	}

	/**
	 * @param location cozy-ui jar, 또는 그 내용을 풀어 놓은 폴더(빌드의 {@code target/classes})
	 */
	public static FrameworkJar read(Path location) {
		FrameworkJar result = new FrameworkJar();
		Map<String, String> entries = Files.isDirectory(location) ? folder(location) : zip(location);
		entries.forEach((name, content) -> {
			if ((name.startsWith("templates/cozy/") || name.startsWith("templates/error/")) && name.endsWith(".html")) {
				String path = name.substring("templates/".length());
				result.templates.add(new TemplateFile(path, Origin.FRAMEWORK, "[cozy-ui] " + name, content));
			}
			else if (name.equals("cozy/fragments.yml")) {
				result.manifest = content;
			}
			else {
				Matcher sprite = SPRITE.matcher(name);
				if (sprite.matches()) {
					boolean filled = sprite.group(1) != null;
					Matcher symbol = (filled ? FILLED : OUTLINE).matcher(content);
					while (symbol.find()) {
						(filled ? result.filledIcons : result.icons).add(symbol.group(1));
					}
				}
			}
		});
		return result;
	}

	/** 읽을 자원인가: 프레임워크 템플릿, 매니페스트, 아이콘 스프라이트 */
	private static boolean wanted(String name) {
		return ((name.startsWith("templates/cozy/") || name.startsWith("templates/error/")) && name.endsWith(".html"))
				|| name.equals("cozy/fragments.yml") || SPRITE.matcher(name).matches();
	}

	/** 이름 순서의 자원 이름 → 내용 */
	private static Map<String, String> zip(Path jar) {
		Map<String, String> entries = new TreeMap<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> all = zip.entries();
			while (all.hasMoreElements()) {
				ZipEntry entry = all.nextElement();
				if (!entry.isDirectory() && wanted(entry.getName())) {
					entries.put(entry.getName(), read(zip, entry));
				}
			}
		}
		catch (IOException ex) {
			throw new CliException(jar + "를 읽지 못했습니다: " + ex.getMessage());
		}
		return entries;
	}

	private static Map<String, String> folder(Path root) {
		Map<String, String> entries = new TreeMap<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				String name = root.relativize(file).toString().replace('\\', '/');
				if (wanted(name)) {
					entries.put(name, Files.readString(file, StandardCharsets.UTF_8));
				}
			}
		}
		catch (IOException ex) {
			throw new CliException(root + "를 읽지 못했습니다: " + ex.getMessage());
		}
		return entries;
	}

	public List<TemplateFile> templates() {
		return List.copyOf(this.templates);
	}

	/** 프레임워크 매니페스트(YAML). 없으면 {@code null} */
	public String manifest() {
		return this.manifest;
	}

	public Set<String> icons() {
		return Set.copyOf(this.icons);
	}

	public Set<String> filledIcons() {
		return Set.copyOf(this.filledIcons);
	}

	private static String read(ZipFile zip, ZipEntry entry) {
		try (InputStream in = zip.getInputStream(entry)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static boolean exists(Path jar) {
		return Files.isRegularFile(jar);
	}
}
