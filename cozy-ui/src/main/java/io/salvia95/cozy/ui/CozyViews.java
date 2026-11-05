package io.salvia95.cozy.ui;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 화면 컨트롤러의 반환값 도우미 (PRD §6.4, §10).
 *
 * <pre>{@code
 * @GetMapping
 * String list(...) {
 *     return CozyViews.pageOrFragment("memo/list", "result");   // htmx면 "memo/list :: result"
 * }
 *
 * @PostMapping
 * String create(@Valid @ModelAttribute("form") MemoForm form, BindingResult errors, RedirectAttributes flash) {
 *     if (errors.hasErrors()) return CozyViews.pageOrFragment("memo/form", "form");
 *     flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("저장했습니다"));
 *     return CozyViews.redirect("/memo/" + id);                   // htmx면 HX-Redirect
 * }
 * }</pre>
 */
public final class CozyViews {

	/** 본문 없이 응답하는 뷰 이름. htmx 리다이렉트에 쓴다. */
	public static final String EMPTY = "cozyEmpty";

	private CozyViews() {
	}

	/** 일반 요청이면 전체 페이지, htmx 부분 갱신 요청이면 그 페이지의 fragment 하나. */
	public static String pageOrFragment(String view, String fragment) {
		return isPartial() ? view + " :: " + fragment : view;
	}

	/**
	 * POST 뒤 리다이렉트(PRG). {@code "redirect:" + path}와 같다. htmx 요청이면 {@code HtmxRedirectInterceptor}가
	 * {@code HX-Redirect} 헤더로 바꿔 브라우저가 전체 페이지를 다시 읽게 한다. 플래시 속성은 두 경우 모두 다음 화면에 전달된다.
	 * @param path 컨텍스트 경로를 뺀 앱 안 경로 ({@code /memo/1})
	 */
	public static String redirect(String path) {
		return "redirect:" + path;
	}

	private static boolean isPartial() {
		return HtmxRequest.from(currentAttributes().getRequest()).partial();
	}

	private static ServletRequestAttributes currentAttributes() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes;
		}
		throw new IllegalStateException("[cozy] CozyViews는 웹 요청을 처리하는 스레드에서만 쓸 수 있습니다");
	}
}
