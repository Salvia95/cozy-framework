package io.salvia95.cozy.reference.memo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.salvia95.cozy.ui.CozyNavItem;

@Configuration(proxyBeanMethods = false)
class MemoConfiguration {

	@Bean
	CozyNavItem memoNavItem() {
		return new CozyNavItem("메모", "/memo", 10);
	}
}
