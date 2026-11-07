package it.cozy.app.dashboard.api;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import it.cozy.app.activity.spi.ActivityFinder;
import it.cozy.app.dashboard.api.dto.DashboardView;
import it.cozy.app.memo.spi.MemoFinder;

/**
 * 대시보드 화면 (cozy add screen). 값은 spi의 Finder로 읽어 {@link DashboardView}로 바꾼 뒤 모델 view로 넘긴다. 뷰는 dashboard/api/index이다.
 * 다른 모듈의 Facade(쓰기)는 부르지 않는다(C8).
 */
@Controller
@RequestMapping("/dashboard")
class DashboardController {

	static final int RECENT = 10;

	private final MemoFinder memos;

	private final ActivityFinder activities;

	DashboardController(MemoFinder memos, ActivityFinder activities) {
		this.memos = memos;
		this.activities = activities;
	}

	@GetMapping
	String index(Model model) {
		model.addAttribute("view", new DashboardView(this.memos.search(null, 0, 1).total(), this.activities.recent(RECENT)));
		return "dashboard/api/index";
	}
}
