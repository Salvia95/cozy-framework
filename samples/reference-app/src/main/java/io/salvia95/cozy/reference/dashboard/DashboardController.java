package io.salvia95.cozy.reference.dashboard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.salvia95.cozy.reference.activity.api.RecentActivities;
import io.salvia95.cozy.reference.dashboard.view.DashboardView;
import io.salvia95.cozy.reference.memo.api.MemoSummaries;
import io.salvia95.cozy.ui.CozyNavItem;

/** 첫 화면. 최근 활동은 polling-panel이 {@code /dashboard/activity}로 주기 갱신한다. */
@Controller
class DashboardController {

	static final int RECENT = 10;

	private final MemoSummaries memos;

	private final RecentActivities activities;

	DashboardController(MemoSummaries memos, RecentActivities activities) {
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
		return "dashboard/index";
	}

	/** polling-panel 본문 (htmx가 innerHTML로 바꾼다) */
	@GetMapping("/dashboard/activity")
	String activity(Model model) {
		model.addAttribute("dashboard", view());
		return "dashboard/index :: activity";
	}

	private DashboardView view() {
		return new DashboardView(this.memos.summary(), this.activities.recent(RECENT));
	}

	@Configuration(proxyBeanMethods = false)
	static class Navigation {

		@Bean
		CozyNavItem dashboardNavItem() {
			return new CozyNavItem("대시보드", "/dashboard", 0);
		}

	}
}
