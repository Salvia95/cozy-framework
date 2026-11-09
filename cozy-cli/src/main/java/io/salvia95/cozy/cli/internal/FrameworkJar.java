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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.TemplateFile;

/**
 * cozy-ui jar의 프레임워크 자원: 템플릿({@code templates/cozy/}, {@code templates/error/}), 매니페스트, Tabler Icons 스프라이트.
 * cozy 명령은 cozy-ui의 클래스를 쓰지 않고 jar를 zip으로 읽는다.
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

	public static FrameworkJar read(Path jar) {
		FrameworkJar result = new FrameworkJar();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			List<ZipEntry> sorted = new ArrayList<>();
			while (entries.hasMoreElements()) {
				sorted.add(entries.nextElement());
			}
			sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
			for (ZipEntry entry : sorted) {
				String name = entry.getName();
				if (entry.isDirectory()) {
					continue;
				}
				if ((name.startsWith("templates/cozy/") || name.startsWith("templates/error/")) && name.endsWith(".html")) {
					String path = name.substring("templates/".length());
					result.templates.add(new TemplateFile(path, Origin.FRAMEWORK, "[cozy-ui] " + name, read(zip, entry)));
				}
				else if (name.equals("cozy/fragments.yml")) {
					result.manifest = read(zip, entry);
				}
				else {
					Matcher sprite = SPRITE.matcher(name);
					if (sprite.matches()) {
						boolean filled = sprite.group(1) != null;
						Matcher symbol = (filled ? FILLED : OUTLINE).matcher(read(zip, entry));
						while (symbol.find()) {
							(filled ? result.filledIcons : result.icons).add(symbol.group(1));
						}
					}
				}
			}
		}
		catch (IOException ex) {
			throw new CliException(jar + "를 읽지 못했습니다: " + ex.getMessage());
		}
		return result;
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
