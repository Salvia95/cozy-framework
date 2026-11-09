package io.salvia95.cozy.runtime.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 최소선 (PRD §9.3).
 *
 * <p>로그인은 없다. 모든 요청을 허용하고, CSRF와 보안 헤더만 켠다.
 * CSP는 자기 출처만 허용하고 인라인 스크립트를 막는다. Tabler CSS가 쓰는 data: 이미지는 허용한다.
 */
@AutoConfiguration(before = { ServletWebSecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class CozySecurityAutoConfiguration {

	public static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
			+ "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
			+ "form-action 'self'; frame-ancestors 'none'";

	@Bean
	SecurityFilterChain cozySecurityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.headers((headers) -> headers.frameOptions((frame) -> frame.deny())
				.contentSecurityPolicy((csp) -> csp.policyDirectives(CONTENT_SECURITY_POLICY)));
		// CSRF는 기본값(켜짐, 세션 저장소)을 그대로 쓴다. htmx는 layout의 meta 태그에서 토큰을 읽는다(M3).
		return http.build();
	}

	/** 로그인이 없으므로 Boot가 임시 사용자와 비밀번호를 만들지 않게 한다. */
	@Bean
	@ConditionalOnMissingBean
	UserDetailsService cozyNoUsers() {
		return (username) -> {
			throw new UsernameNotFoundException("Cozy 앱에는 로그인이 없습니다");
		};
	}
}
