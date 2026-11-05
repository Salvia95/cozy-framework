package io.salvia95.cozy.ui.internal;

import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import io.salvia95.cozy.ui.CozyNavItem;

/**
 * 모든 화면의 모델에 레이아웃이 쓰는 값을 넣는다.
 *
 * <ul>
 * <li>{@code cozyApp}: 앱 이름과 버전, Cozy 버전 (푸터, 제목)</li>
 * <li>{@code cozyNav}: 정렬된 내비 항목과 활성 여부 (app-shell)</li>
 * </ul>
 */
@ControllerAdvice
public class CozyLayoutAdvice {

	private final CozyAppInfo appInfo;

	private final List<CozyNavItem> navItems;

	public CozyLayoutAdvice(CozyAppInfo appInfo, List<CozyNavItem> navItems) {
		this.appInfo = appInfo;
		this.navItems = navItems.stream()
			.sorted(Comparator.comparingInt(CozyNavItem::order).thenComparing(CozyNavItem::label))
			.toList();
	}

	@ModelAttribute("cozyApp")
	CozyAppInfo cozyApp() {
		return this.appInfo;
	}

	@ModelAttribute("cozyNav")
	List<NavLink> cozyNav(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return this.navItems.stream()
			.map((item) -> new NavLink(item.label(), item.path(), isActive(path, item.path())))
			.toList();
	}

	static boolean isActive(String requestPath, String itemPath) {
		if (itemPath.equals("/")) {
			return requestPath.equals("/");
		}
		return requestPath.equals(itemPath) || requestPath.startsWith(itemPath + "/");
	}

	/** 화면에 그리는 내비 링크. */
	public record NavLink(String label, String path, boolean active) {
	}

	/** 푸터와 제목에 쓰는 앱 정보. */
	public record CozyAppInfo(String name, String version, String cozyVersion) {
	}
}
