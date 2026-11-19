package io.salvia95.cozy.workbench.internal;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import io.salvia95.cozy.runtime.internal.CozyRuntimeEnvironmentPostProcessor;

/**
 * dev 프로필이면 워크벤치를 기본으로 켠다. 운영에서는 꺼져 있다.
 *
 * <p>
 * 워크벤치가 켜지고 앱 프로젝트 폴더를 찾으면, 정적 자원을 소스 폴더({@code src/main/resources/static/})에서 먼저 읽는다
 * ({@code spring.web.resources.static-locations}의 맨 앞). 워크벤치가 적용한 만든 JS와 정적 파일이 새로고침만으로 보인다. 템플릿은
 * {@link SourceTemplateResolver}가 맡는다. {@code cozy.dev.workbench.live-resources=false}면 지금처럼 빌드 결과만 읽는다. 앱이
 * {@code spring.web.resources.static-locations}를 정했으면 그 값이 이긴다(이 값은 맨 뒤의 속성 출처다).
 */
public class WorkbenchEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	public static final String ENABLED = "cozy.dev.workbench.enabled";

	/** 템플릿과 {@code static/}을 소스 폴더에서 먼저 읽는가 (기본 {@code true}) */
	public static final String LIVE_RESOURCES = "cozy.dev.workbench.live-resources";

	static final String STATIC_LOCATIONS = "spring.web.resources.static-locations";

	/** cozy-ui의 모듈 JS 묶음이 먼저 읽는 폴더 ({@code CozyModuleScripts.FOLDERS}) */
	static final String MODULE_SCRIPT_FOLDERS = "cozy.ui.module-script-folders";

	/** Spring Boot의 기본 정적 위치 */
	static final String DEFAULT_LOCATIONS = "classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/";

	@Override
	public int getOrder() {
		return CozyRuntimeEnvironmentPostProcessor.ORDER + 3;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		boolean dev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
		if (dev) {
			DefaultPropertiesPropertySource.addOrMerge(new HashMap<>(Map.of(ENABLED, "true")), environment.getPropertySources());
		}
		if (environment.getProperty(ENABLED, Boolean.class, dev)) {
			// 맨 뒤: 앱이 정한 값이 이긴다. 값은 읽을 때 정한다(프로젝트 폴더를 정하는 속성이 이 뒤에 더해질 수 있다)
			environment.getPropertySources().addLast(new LiveStaticLocations(environment));
		}
	}

	/**
	 * {@code spring.web.resources.static-locations}: 소스의 {@code static/}을 맨 앞에. {@code cozy.ui.module-script-folders}: 소스의
	 * {@code static/} (모듈 JS 묶음). 끄거나 앱 프로젝트 폴더가 아니면 값이 없다
	 */
	static final class LiveStaticLocations extends PropertySource<Object> {

		/** 출처 객체로 두지 않는다: 환경을 출처로 가진 속성 출처는 Spring Boot가 다시 펼쳐 본다 */
		private final ConfigurableEnvironment environment;

		LiveStaticLocations(ConfigurableEnvironment environment) {
			super("cozyWorkbenchLiveResources", new Object());
			this.environment = environment;
		}

		@Override
		public Object getProperty(String name) {
			boolean locations = STATIC_LOCATIONS.equals(name);
			if ((!locations && !MODULE_SCRIPT_FOLDERS.equals(name)) || !live(this.environment)) {
				return null;
			}
			return sourceFolder(this.environment).map((resources) -> folder(resources.resolve("static")) + (locations ? "," + DEFAULT_LOCATIONS : ""))
				.orElse(null);
		}
	}

	/** 폴더의 {@code file:} 주소. 정적 위치는 {@code /}로 끝나야 한다 */
	static String folder(Path dir) {
		String uri = dir.toAbsolutePath().normalize().toUri().toString();
		return uri.endsWith("/") ? uri : uri + "/";
	}

	static boolean live(org.springframework.core.env.Environment environment) {
		return environment.getProperty(LIVE_RESOURCES, Boolean.class, true);
	}

	/** 앱의 {@code src/main/resources}. 앱 프로젝트 폴더가 아니면 빈 값 */
	static Optional<Path> sourceFolder(org.springframework.core.env.Environment environment) {
		return WorkbenchEngine.locate(environment.getProperty("cozy.dev.workbench.project-dir", ""), Path.of(""))
			.map((dir) -> dir.resolve("src/main/resources"));
	}
}
