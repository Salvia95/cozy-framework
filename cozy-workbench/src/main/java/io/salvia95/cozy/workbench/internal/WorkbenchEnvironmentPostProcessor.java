package io.salvia95.cozy.workbench.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import io.salvia95.cozy.runtime.internal.CozyRuntimeEnvironmentPostProcessor;

/** dev 프로필이면 워크벤치를 기본으로 켠다. 운영에서는 꺼져 있다. */
public class WorkbenchEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	public static final String ENABLED = "cozy.dev.workbench.enabled";

	@Override
	public int getOrder() {
		return CozyRuntimeEnvironmentPostProcessor.ORDER + 3;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
			DefaultPropertiesPropertySource.addOrMerge(new HashMap<>(Map.of(ENABLED, "true")),
					environment.getPropertySources());
		}
	}
}
