package io.salvia95.cozy.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.AnnotatedClassFinder;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;

import io.salvia95.cozy.rules.FragmentDeclaration;
import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.TemplateFile;

/**
 * 테스트 중인 앱 프로젝트의 파일과 클래스패스의 프레임워크 자원.
 *
 * <p>Maven은 모듈 디렉터리에서 테스트를 실행하므로 앱 소스는 작업 디렉터리 기준 {@code src/main/resources}에서 읽는다.
 * 프레임워크 템플릿과 매니페스트는 작업 디렉터리 밖(cozy-ui jar 등)의 클래스패스 자원이다.
 */
public final class CozyProject {

	public static final Path RESOURCES = Path.of("src/main/resources");

	public static final Path TEMPLATES = RESOURCES.resolve("templates");

	public static final Path MANIFEST = RESOURCES.resolve("cozy/fragments.yml");

	private static final List<String> FRAMEWORK_TEMPLATE_PATTERNS = List.of("classpath*:templates/cozy/**/*.html",
			"classpath*:templates/error/**/*.html");

	private CozyProject() {
	}

	/**
	 * 테스트 클래스의 패키지부터 위로 올라가며 {@code @SpringBootApplication}을 찾는다.
	 */
	public static Class<?> applicationClass(Class<?> testClass) {
		Class<?> found = new AnnotatedClassFinder(SpringBootConfiguration.class).findFromClass(testClass);
		if (found == null) {
			throw new IllegalStateException("[cozy] " + testClass.getName() + "의 패키지나 그 상위 패키지에서 "
					+ "@SpringBootApplication 클래스를 찾지 못했습니다. 테스트를 앱의 메인 패키지 아래에 두세요.");
		}
		return found;
	}

	/** 앱 템플릿 ({@code src/main/resources/templates}) */
	public static List<TemplateFile> appTemplates(Path root) {
		return TemplateFile.fromDirectory(root, Origin.APP, "templates/");
	}

	/** 클래스패스에 있는 프레임워크 템플릿 ({@code templates/cozy/}, {@code templates/error/}) */
	public static List<TemplateFile> frameworkTemplates() {
		List<TemplateFile> files = new ArrayList<>();
		for (String pattern : FRAMEWORK_TEMPLATE_PATTERNS) {
			for (Resource resource : resources(pattern)) {
				if (isAppResource(resource)) {
					continue;
				}
				String url = url(resource).toString();
				int index = Math.max(url.lastIndexOf("/templates/cozy/"), url.lastIndexOf("/templates/error/"));
				String path = url.substring(index + "/templates/".length());
				files.add(new TemplateFile(path, Origin.FRAMEWORK, "[" + origin(url) + "] templates/" + path,
						read(resource)));
			}
		}
		return files;
	}

	/** 프레임워크와 앱 매니페스트의 모든 항목. UI 모듈이 없으면 빈 목록 */
	public static List<FragmentDeclaration> manifest(Path appManifest) {
		if (!ClassUtils.isPresent("io.salvia95.cozy.ui.catalog.FragmentManifest", CozyProject.class.getClassLoader())) {
			return List.of();
		}
		return Manifests.read(appManifest);
	}

	/** 앱 설정 파일에서 속성 하나를 읽는다 ({@code application.yml}, {@code application.properties}) */
	public static String appProperty(String name) {
		for (String file : List.of("application.properties", "application.yml", "application.yaml")) {
			ClassPathResource resource = new ClassPathResource(file);
			if (!resource.exists()) {
				continue;
			}
			try {
				List<PropertySource<?>> sources = file.endsWith(".properties")
						? new PropertiesPropertySourceLoader().load(file, resource)
						: new YamlPropertySourceLoader().load(file, resource);
				for (PropertySource<?> source : sources) {
					Object value = source.getProperty(name);
					if (value != null) {
						return String.valueOf(value);
					}
				}
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return null;
	}

	static boolean isAppResource(Resource resource) {
		URL url = url(resource);
		if (!"file".equals(url.getProtocol())) {
			return false;
		}
		try {
			Path file = Path.of(url.toURI()).toAbsolutePath().normalize();
			return file.startsWith(Path.of("").toAbsolutePath().normalize());
		}
		catch (Exception ex) {
			return false;
		}
	}

	static Resource[] resources(String pattern) {
		try {
			return new PathMatchingResourcePatternResolver(CozyProject.class.getClassLoader()).getResources(pattern);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static String read(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static URL url(Resource resource) {
		try {
			return resource.getURL();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 메시지용 출처 이름: jar 파일 이름(버전 제외) 또는 모듈 디렉터리 */
	private static String origin(String url) {
		int bang = url.indexOf("!/");
		String base = (bang >= 0) ? url.substring(0, bang) : url.substring(0, url.lastIndexOf("/templates/"));
		String name = base.substring(base.lastIndexOf('/') + 1);
		if (name.equals("classes")) {
			String parent = base.substring(0, base.lastIndexOf('/'));
			parent = parent.substring(0, parent.lastIndexOf('/'));
			name = parent.substring(parent.lastIndexOf('/') + 1);
		}
		return name.replaceAll("-\\d.*\\.jar$", "").replace(".jar", "");
	}
}
