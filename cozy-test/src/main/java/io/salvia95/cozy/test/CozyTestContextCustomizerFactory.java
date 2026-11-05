package io.salvia95.cozy.test;

import java.util.List;
import java.util.Map;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * cozy-test가 클래스패스에 있으면 앱의 모든 Spring 테스트에서 컨트롤러·뷰 모듈 일치 검사(C7)를 실패 모드로 켠다
 * (PRD §6.5 검사 7). 운영에서는 경고 로그만 남긴다. 앱 설정의 값이 있으면 그것이 먼저다.
 */
class CozyTestContextCustomizerFactory implements ContextCustomizerFactory {

	static final Map<String, Object> DEFAULTS = Map.of("cozy.ui.view-module-check", "fail");

	@Override
	public ContextCustomizer createContextCustomizer(Class<?> testClass,
			List<ContextConfigurationAttributes> configAttributes) {
		return new Customizer();
	}

	private static final class Customizer implements ContextCustomizer {

		@Override
		public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config) {
			context.getEnvironment().getPropertySources().addLast(new MapPropertySource("cozyTestDefaults", DEFAULTS));
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Customizer;
		}

		@Override
		public int hashCode() {
			return Customizer.class.hashCode();
		}
	}
}
