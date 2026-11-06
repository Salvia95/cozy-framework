package io.salvia95.cozy.runtime.testapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 런타임 스타터 테스트용 최소 앱. */
@SpringBootApplication
public class TestApp {

	@RestController
	static class HelloController {

		@Value("${cozy.test.greeting:none}")
		private String greeting;

		@GetMapping("/hello")
		String hello() {
			return this.greeting;
		}

		@PostMapping("/hello")
		String post() {
			return "posted";
		}

		/** CSRF 토큰 (실제 HTTP로 POST하는 테스트용) */
		@GetMapping("/csrf")
		String csrf(CsrfToken token) {
			return token.getHeaderName() + "=" + token.getToken();
		}

	}
}
