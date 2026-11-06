package io.salvia95.cozy.reference.dashboard.api;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.salvia95.cozy.reference.activity.spi.ActivityFinder;
import io.salvia95.cozy.reference.dashboard.api.dto.DashboardView;
import io.salvia95.cozy.reference.memo.spi.MemoFinder;

/**
 * 첫 화면. memo·activity의 Finder로 값을 구하고, 그 모듈의 공개 fragment(templates/{모듈}/spi/)에 넘긴다. 최근 활동은
 * polling-panel이 {@code /dashboard/activity}로 주기 갱신한다.
 */
@Controller
class DashboardController {

	static final int RECENT = 10;

	private final MemoFinder memos;

	private final ActivityFinder activities;

	DashboardController(MemoFinder memos, ActivityFinder activities) {
		this.memos = memos;
		this.activities = activities;
	}

	@GetMapping("/")
	String root() {
		return "redirect:/dashboard";
	}

	@GetMapping("/dashboard")
	String index(Model model) {
		model.addAttribute("dashboard", view());
		return "dashboard/api/index";
	}

	/** polling-panel 본문 (htmx가 innerHTML로 바꾼다) */
	@GetMapping("/dashboard/activity")
	String activity(Model model) {
		model.addAttribute("dashboard", view());
		return "dashboard/api/index :: activity";
	}

	private DashboardView view() {
		return new DashboardView(this.memos.summary(), this.activities.recent(RECENT));
	}
}
