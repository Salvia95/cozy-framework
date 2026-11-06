package io.salvia95.cozy.ui.internal;

import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import io.salvia95.cozy.runtime.update.CozyUpdates;
import io.salvia95.cozy.ui.CozyNavItem;

/**
 * 모든 화면의 모델에 레이아웃이 쓰는 값을 넣는다.
 *
 * <ul>
 * <li>{@code cozyApp}: 앱 이름과 버전, Cozy 버전 (푸터, 제목)</li>
 * <li>{@code cozyNav}: 정렬된 내비 항목과 활성 여부 (app-shell)</li>
 * <li>{@code cozyUpdate}: 설치할 수 있는 새 버전 (업데이트 배너, 없으면 null)</li>
 * <li>{@code cozyUpdateOutcome}: 직전 업데이트 결과 (업데이트 뒤 첫 실행에서만)</li>
 * </ul>
 */
@ControllerAdvice
public class CozyLayoutAdvice {

	private final CozyAppInfo appInfo;

	private final List<CozyNavItem> navItems;

	private final @Nullable CozyUpdates updates;

	public CozyLayoutAdvice(CozyAppInfo appInfo, List<CozyNavItem> navItems, @Nullable CozyUpdates updates) {
		this.appInfo = appInfo;
		this.updates = updates;
		this.navItems = navItems.stream()
			.sorted(Comparator.comparingInt(CozyNavItem::order).thenComparing(CozyNavItem::label))
			.toList();
	}

	@ModelAttribute("cozyApp")
	CozyAppInfo cozyApp() {
		return this.appInfo;
	}

	@ModelAttribute("cozyUpdate")
	CozyUpdates.@Nullable Offer cozyUpdate() {
		return (this.updates != null) ? this.updates.offer().orElse(null) : null;
	}

	@ModelAttribute("cozyUpdateOutcome")
	CozyUpdates.@Nullable Outcome cozyUpdateOutcome() {
		return (this.updates != null) ? this.updates.lastOutcome().orElse(null) : null;
	}

	@ModelAttribute("cozyNav")
	List<NavLink> cozyNav(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return this.navItems.stream()
			.map((item) -> new NavLink(item.label(), item.path(), item.icon(), isActive(path, item.path())))
			.toList();
	}

	static boolean isActive(String requestPath, String itemPath) {
		if (itemPath.equals("/")) {
			return requestPath.equals("/");
		}
		return requestPath.equals(itemPath) || requestPath.startsWith(itemPath + "/");
	}

	/** 화면에 그리는 내비 링크. */
	public record NavLink(String label, String path, String icon, boolean active) {
	}

	/** 푸터와 제목에 쓰는 앱 정보. */
	public record CozyAppInfo(String name, String version, String cozyVersion) {
	}
}
