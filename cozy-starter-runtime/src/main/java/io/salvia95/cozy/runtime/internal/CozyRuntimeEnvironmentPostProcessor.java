package io.salvia95.cozy.runtime.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import io.salvia95.cozy.runtime.CozyDataDirectory;

/**
 * 설정 파일을 읽은 직후, 로깅이 초기화되기 전에 런타임 환경을 확정한다.
 *
 * <ol>
 * <li>{@code cozy.app.id}를 검사하고 데이터 루트를 정해 폴더를 만든다. 정한 경로는 {@code cozy.data-dir}로 고정한다.</li>
 * <li>{@code {dataDir}/config/application.yml}이 있으면 패키지 안 설정보다 높은 우선순위로 읽는다.</li>
 * <li>{@code server.address=127.0.0.1}을 최우선 순위로 강제한다. 설정이나 명령줄로 바꿀 수 없다.</li>
 * <li>기본값을 정한다: 빈 포트, {@code {dataDir}/logs/app.log} 롤링(10MB, 14일).</li>
 * </ol>
 */
public class CozyRuntimeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	/** 설정 파일(ConfigData) 처리 바로 뒤. 로깅 초기화(LoggingApplicationListener)보다는 앞이다. */
	public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1;

	public static final String FORCED_SOURCE = "cozyForcedProperties";

	public static final String EXTERNAL_SOURCE_PREFIX = "cozyExternalConfig";

	public static final String LOOPBACK = "127.0.0.1";

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String appId = CozyDataDirectory.requireValidAppId(environment.getProperty("cozy.app.id"));
		CozyDataDirectory dataDir = CozyDataDirectory.resolve(appId, environment.getProperty("cozy.data-dir"))
			.createDirectories();

		MutablePropertySources sources = environment.getPropertySources();
		addExternalConfig(sources, dataDir.config().resolve("application.yml"));

		Map<String, Object> forced = new LinkedHashMap<>();
		forced.put("server.address", LOOPBACK);
		forced.put("cozy.data-dir", dataDir.root().toString());
		forced.put("cozy.app.id", appId);
		sources.addFirst(new MapPropertySource(FORCED_SOURCE, forced));

		Map<String, Object> defaults = new LinkedHashMap<>();
		defaults.put("server.port", "0");
		defaults.put("spring.application.name", appId);
		defaults.put("cozy.app.name", appId);
		defaults.put("logging.file.name", dataDir.logs().resolve("app.log").toString());
		defaults.put("logging.logback.rollingpolicy.max-file-size", "10MB");
		defaults.put("logging.logback.rollingpolicy.max-history", "14");
		DefaultPropertiesPropertySource.addOrMerge(defaults, sources);
	}

	/**
	 * 외부 설정은 패키지 안의 application*.yml보다 우선하고, 시스템 속성·환경 변수·명령줄보다는 뒤에 둔다.
	 * 앱 식별과 데이터 위치({@code cozy.app.id}, {@code cozy.data-dir})는 이 파일로 바꿀 수 없다.
	 */
	private void addExternalConfig(MutablePropertySources sources, Path file) {
		if (!Files.isRegularFile(file)) {
			return;
		}
		List<PropertySource<?>> loaded;
		try {
			loaded = new YamlPropertySourceLoader().load(EXTERNAL_SOURCE_PREFIX + " [" + file + "]",
					new FileSystemResource(file));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 외부 설정을 읽을 수 없습니다: " + file, ex);
		}
		// 여러 문서가 있으면 뒤 문서가 이긴다(Boot 규칙). 차례로 anchor 바로 뒤에 넣으면 마지막 문서가 가장 앞에 온다.
		String anchor = anchor(sources);
		for (PropertySource<?> source : loaded) {
			if (anchor != null) {
				sources.addAfter(anchor, source);
			}
			else {
				sources.addFirst(source);
			}
		}
	}

	private static String anchor(MutablePropertySources sources) {
		for (String name : List.of("random", StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
				StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
			if (sources.contains(name)) {
				return name;
			}
		}
		return null;
	}
}
