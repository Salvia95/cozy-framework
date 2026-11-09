package io.salvia95.cozy.ui;

import jakarta.servlet.http.HttpServletRequest;

/**
 * htmx 요청 헤더. 컨트롤러 메서드 인자로 받는다.
 *
 * @param htmx {@code HX-Request: true}
 * @param boosted {@code HX-Boosted}
 * @param target {@code HX-Target} (대상 요소 id)
 * @param trigger {@code HX-Trigger} (요청을 일으킨 요소 id)
 * @param triggerName {@code HX-Trigger-Name}
 * @param currentUrl {@code HX-Current-URL}
 */
public record HtmxRequest(boolean htmx, boolean boosted, String target, String trigger, String triggerName,
		String currentUrl) {

	public static HtmxRequest from(HttpServletRequest request) {
		return new HtmxRequest("true".equals(request.getHeader("HX-Request")),
				"true".equals(request.getHeader("HX-Boosted")), request.getHeader("HX-Target"),
				request.getHeader("HX-Trigger"), request.getHeader("HX-Trigger-Name"),
				request.getHeader("HX-Current-URL"));
	}

	/** 부분 갱신 요청인가. boost된 요청은 전체 페이지를 원하므로 false. */
	public boolean partial() {
		return this.htmx && !this.boosted;
	}
}
