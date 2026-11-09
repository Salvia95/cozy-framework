package io.salvia95.cozy.ui.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import io.salvia95.cozy.runtime.internal.CozyRuntimeEnvironmentPostProcessor;

/** dev 프로필이면 패턴 카탈로그를 기본으로 켠다. 운영에서는 꺼져 있다. */
public class CozyUiEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	@Override
	public int getOrder() {
		return CozyRuntimeEnvironmentPostProcessor.ORDER + 2;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
			DefaultPropertiesPropertySource.addOrMerge(new HashMap<>(Map.of("cozy.dev.catalog.enabled", "true")),
					environment.getPropertySources());
		}
	}
}
