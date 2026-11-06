package io.salvia95.cozy.reference.dashboard.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.salvia95.cozy.ui.CozyNavItem;

@Configuration(proxyBeanMethods = false)
class DashboardConfiguration {

	@Bean
	CozyNavItem dashboardNavItem() {
		return new CozyNavItem("대시보드", "/dashboard", 0, "layout-dashboard");
	}
}
