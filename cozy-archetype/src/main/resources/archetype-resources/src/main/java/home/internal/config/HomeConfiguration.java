package ${package}.home.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.salvia95.cozy.ui.CozyNavItem;

@Configuration(proxyBeanMethods = false)
class HomeConfiguration {

	/** 사이드 내비 항목 (순서가 작을수록 위) */
	@Bean
	CozyNavItem homeNavItem() {
		return new CozyNavItem("홈", "/", 0, "home");
	}
}
