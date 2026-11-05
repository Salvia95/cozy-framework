package io.salvia95.cozy.runtime.internal;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * DNS 리바인딩 차단 (PRD §9.3).
 *
 * <p>Host 헤더가 {@code 127.0.0.1:{port}} 또는 {@code localhost:{port}}가 아니면 403으로 막는다.
 * {@code port}는 요청을 받은 커넥터의 실제 포트다. 포트를 생략한 Host는 80으로 본다.
 */
public class HostHeaderFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (isAllowed(request.getHeader(HttpHeaders.HOST), request.getServerName(), request.getServerPort(),
				request.getLocalPort())) {
			chain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.TEXT_PLAIN_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("Forbidden: unexpected Host header");
	}

	static boolean isAllowed(String hostHeader, String serverName, int serverPort, int localPort) {
		String host;
		int port;
		if (hostHeader == null || hostHeader.isBlank()) {
			host = serverName;
			port = serverPort;
		}
		else {
			String value = hostHeader.trim().toLowerCase(Locale.ROOT);
			if (value.startsWith("[")) {
				// IPv6 리터럴. 127.0.0.1에만 바인딩하므로 허용하지 않는다.
				return false;
			}
			int colon = value.lastIndexOf(':');
			if (colon < 0) {
				host = value;
				port = 80;
			}
			else {
				host = value.substring(0, colon);
				try {
					port = Integer.parseInt(value.substring(colon + 1));
				}
				catch (NumberFormatException ex) {
					return false;
				}
			}
		}
		if (host == null) {
			return false;
		}
		host = host.toLowerCase(Locale.ROOT);
		return port == localPort && (host.equals("127.0.0.1") || host.equals("localhost"));
	}
}
