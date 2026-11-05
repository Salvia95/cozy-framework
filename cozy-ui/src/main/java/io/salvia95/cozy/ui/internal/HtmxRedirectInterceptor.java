package io.salvia95.cozy.ui.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import io.salvia95.cozy.ui.CozyViews;
import io.salvia95.cozy.ui.HtmxRequest;

/**
 * htmx 요청에 대한 {@code redirect:}를 {@code HX-Redirect} 응답으로 바꾼다 (PRD §6.4).
 *
 * <p>htmx는 XHR로 302를 따라가 리다이렉트된 페이지 전체를 대상 요소에 끼워 넣는다. 그래서 200 빈 응답에
 * {@code HX-Redirect}를 실어 브라우저가 전체 페이지를 다시 읽게 한다. Spring이 {@code redirect:}일 때 출력 플래시 맵에
 * 옮겨 둔 플래시 속성은 여기서 저장하므로 다음 화면에 그대로 전달된다.
 */
public class HtmxRedirectInterceptor implements HandlerInterceptor {

	private static final String REDIRECT = "redirect:";

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) {
		if (modelAndView == null || modelAndView.getViewName() == null
				|| !modelAndView.getViewName().startsWith(REDIRECT) || !HtmxRequest.from(request).htmx()) {
			return;
		}
		String target = modelAndView.getViewName().substring(REDIRECT.length());
		String location = target.startsWith("/") ? request.getContextPath() + target : target;
		RequestContextUtils.saveOutputFlashMap(location, request, response);
		response.setHeader("HX-Redirect", location);
		modelAndView.clear();
		modelAndView.setViewName(CozyViews.EMPTY);
	}
}
