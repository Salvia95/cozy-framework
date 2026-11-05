package io.salvia95.cozy.ui.autoconfigure;

import java.util.List;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.salvia95.cozy.runtime.CozyApplication;
import io.salvia95.cozy.runtime.CozyRuntimeProperties;
import io.salvia95.cozy.runtime.autoconfigure.CozySecurityAutoConfiguration;
import io.salvia95.cozy.ui.CozyNavItem;
import io.salvia95.cozy.ui.CozyViews;
import io.salvia95.cozy.ui.internal.CatalogController;
import io.salvia95.cozy.ui.internal.CatalogTemplateResolver;
import io.salvia95.cozy.ui.internal.CozyLayoutAdvice;
import io.salvia95.cozy.ui.internal.CozyLayoutAdvice.CozyAppInfo;
import io.salvia95.cozy.ui.internal.EmptyView;
import io.salvia95.cozy.ui.internal.FragmentManifest;
import io.salvia95.cozy.ui.internal.HtmxRedirectInterceptor;
import io.salvia95.cozy.ui.internal.HtmxRequestArgumentResolver;

/**
 * UI 자동 설정 (PRD §6.4): 레이아웃 모델, 내비, htmx 지원, 패턴 카탈로그.
 */
@AutoConfiguration(before = ThymeleafAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CozyUiAutoConfiguration {

	@Bean
	CozyAppInfo cozyAppInfo(CozyRuntimeProperties properties, ObjectProvider<BuildProperties> buildProperties) {
		BuildProperties build = buildProperties.getIfAvailable();
		String cozyVersion = CozyApplication.class.getPackage().getImplementationVersion();
		return new CozyAppInfo(properties.app().displayName(), (build != null) ? build.getVersion() : "dev",
				(cozyVersion != null) ? cozyVersion : "dev");
	}

	@Bean
	CozyLayoutAdvice cozyLayoutAdvice(CozyAppInfo appInfo, ObjectProvider<CozyNavItem> navItems) {
		return new CozyLayoutAdvice(appInfo, navItems.orderedStream().toList());
	}

	@Bean
	WebMvcConfigurer cozyHtmxWebMvcConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
				resolvers.add(new HtmxRequestArgumentResolver());
			}

			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(new HtmxRedirectInterceptor());
			}
		};
	}

	@Bean(CozyViews.EMPTY)
	View cozyEmptyView() {
		return new EmptyView();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "cozy.dev.catalog.enabled", havingValue = "true")
	static class CatalogConfiguration {

		@Bean
		FragmentManifest cozyFragmentManifest() {
			return FragmentManifest.load(CozyUiAutoConfiguration.class.getClassLoader());
		}

		@Bean
		CatalogTemplateResolver cozyCatalogTemplateResolver(FragmentManifest manifest) {
			return new CatalogTemplateResolver(manifest);
		}

		/** 미리보기는 카탈로그 페이지의 iframe 안에서만 보이도록, 이 경로만 같은 출처 프레임을 허용한다. */
		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		SecurityFilterChain cozyCatalogPreviewSecurityFilterChain(HttpSecurity http) throws Exception {
			http.securityMatcher("/cozy/dev/catalog/render")
				.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
				.headers((headers) -> headers.frameOptions((frame) -> frame.sameOrigin())
					.contentSecurityPolicy((csp) -> csp.policyDirectives(CozySecurityAutoConfiguration.CONTENT_SECURITY_POLICY
						.replace("frame-ancestors 'none'", "frame-ancestors 'self'"))));
			return http.build();
		}

		@Bean
		CatalogController cozyCatalogController(FragmentManifest manifest, ObjectProvider<JsonMapper> jsonMapper) {
			return new CatalogController(manifest, jsonMapper.getIfAvailable(JsonMapper::new));
		}

	}
}
