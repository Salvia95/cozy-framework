package io.salvia95.cozy.runtime.autoconfigure;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Clock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import io.salvia95.cozy.runtime.CozyApplication;
import io.salvia95.cozy.runtime.CozyDataDirectory;
import io.salvia95.cozy.runtime.CozyDiagnosticsContributor;
import io.salvia95.cozy.runtime.CozyRuntimeProperties;
import io.salvia95.cozy.runtime.CozySecretStore;
import io.salvia95.cozy.runtime.internal.CozyControlController;
import io.salvia95.cozy.runtime.internal.CozyRuntimeEnvironmentPostProcessor;
import io.salvia95.cozy.runtime.internal.CozyShutdown;
import io.salvia95.cozy.runtime.internal.diagnostics.DiagnosticsExporter;
import io.salvia95.cozy.runtime.internal.update.AppImage;
import io.salvia95.cozy.runtime.internal.update.UpdateService;
import io.salvia95.cozy.runtime.internal.HostHeaderFilter;
import io.salvia95.cozy.runtime.internal.PropertiesFileSecretStore;

/**
 * 런타임 기반 자동 설정 (PRD §9.1~9.5): 데이터 경로, 비밀 값, 127.0.0.1 고정, Host 필터, 종료, 업데이트, 진단.
 */
@AutoConfiguration
@EnableConfigurationProperties(CozyRuntimeProperties.class)
public class CozyRuntimeAutoConfiguration {

	private static final Log logger = LogFactory.getLog(CozyRuntimeAutoConfiguration.class);

	@Bean
	CozyDataDirectory cozyDataDirectory(Environment environment) {
		// CozyRuntimeEnvironmentPostProcessor가 정한 경로. 여기서는 다시 정하지 않는다.
		return CozyDataDirectory.of(Path.of(environment.getRequiredProperty("cozy.data-dir")));
	}

	@Bean
	@ConditionalOnMissingBean
	CozySecretStore cozySecretStore(CozyDataDirectory dataDirectory) {
		return new PropertiesFileSecretStore(dataDirectory.config().resolve("secrets.properties"));
	}

	@Bean
	CozyShutdown cozyShutdown(ConfigurableApplicationContext context, Environment environment) {
		return new CozyShutdown(context, environment.getProperty(CozyShutdown.EXIT_JVM_PROPERTY, Boolean.class, false));
	}

	@Bean
	UpdateService cozyUpdateService(CozyRuntimeProperties properties, ObjectProvider<BuildProperties> buildProperties,
			CozyDataDirectory dataDirectory) {
		BuildProperties build = buildProperties.getIfAvailable();
		return new UpdateService(properties.app().id(), (build != null) ? build.getVersion() : null,
				properties.update().manifestUrl(), properties.update().checkInterval(),
				properties.update().readyTimeout(), dataDirectory, AppImage.current());
	}

	@Bean
	DiagnosticsExporter cozyDiagnosticsExporter(ConfigurableEnvironment environment, CozyDataDirectory dataDirectory,
			CozyRuntimeProperties properties, ObjectProvider<BuildProperties> buildProperties,
			ObjectProvider<CozyDiagnosticsContributor> contributors, UpdateService updates) {
		BuildProperties build = buildProperties.getIfAvailable();
		String cozyVersion = CozyApplication.class.getPackage().getImplementationVersion();
		return new DiagnosticsExporter(environment, dataDirectory, properties.app().id(),
				properties.app().displayName(), (build != null) ? build.getVersion() : "dev",
				(cozyVersion != null) ? cozyVersion : "dev", contributors.orderedStream().toList(), updates,
				Clock.systemDefaultZone());
	}

	@Bean
	ApplicationListener<ApplicationStartedEvent> cozyStartupLog(CozyRuntimeProperties properties,
			CozyDataDirectory dataDirectory) {
		return (event) -> logger.info("[cozy] app=" + properties.app().id() + " (" + properties.app().displayName()
				+ "), dataDir=" + dataDirectory);
	}

	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	static class WebConfiguration {

		/** 127.0.0.1 바인딩을 웹 서버 팩토리에서 한 번 더 고정한다. 설정 속성이 어떻게 바뀌어도 이 값이 마지막이다. */
		@Bean
		WebServerFactoryCustomizer<ConfigurableWebServerFactory> cozyLoopbackCustomizer() {
			return new LoopbackCustomizer();
		}

		@Bean
		CozyControlController cozyControlController(CozyShutdown shutdown, DiagnosticsExporter diagnostics,
				UpdateService updates, CozyRuntimeProperties properties) {
			return new CozyControlController(shutdown, diagnostics, updates, properties.runtime().openBrowser());
		}

		@Bean
		FilterRegistrationBean<HostHeaderFilter> cozyHostHeaderFilter() {
			FilterRegistrationBean<HostHeaderFilter> registration = new FilterRegistrationBean<>(new HostHeaderFilter());
			// Spring Security 필터 체인(-100)보다 앞
			registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
			return registration;
		}

	}

	static final class LoopbackCustomizer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>, Ordered {

		@Override
		public void customize(ConfigurableWebServerFactory factory) {
			try {
				factory.setAddress(InetAddress.getByName(CozyRuntimeEnvironmentPostProcessor.LOOPBACK));
			}
			catch (UnknownHostException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}

	}
}
