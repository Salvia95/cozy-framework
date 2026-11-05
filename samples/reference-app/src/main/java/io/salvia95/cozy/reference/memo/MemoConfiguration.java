package io.salvia95.cozy.reference.memo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import io.salvia95.cozy.ui.CozyNavItem;

@Configuration(proxyBeanMethods = false)
class MemoConfiguration {

	@Bean
	CozyNavItem memoNavItem() {
		return new CozyNavItem("메모", "/memo", 10);
	}

	/** 첫 화면은 메모 목록이다. dashboard 모듈이 생기면(M5) 그쪽으로 옮긴다. */
	@Controller
	static class RootRedirect {

		@GetMapping("/")
		String root() {
			return "redirect:/memo";
		}

	}
}
