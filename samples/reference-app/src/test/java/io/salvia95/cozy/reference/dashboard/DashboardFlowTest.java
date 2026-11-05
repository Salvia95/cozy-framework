package io.salvia95.cozy.reference.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.salvia95.cozy.reference.activity.api.RecentActivities;
import io.salvia95.cozy.reference.memo.MemoService;

/**
 * M5 흐름: memo가 MemoCreated를 발행 → activity가 받아 기록 → dashboard가 memo::api, activity::api fragment로 보여 준다.
 */
@SpringBootTest(properties = "cozy.data-dir=target/test-data/dashboard-${random.uuid}")
@AutoConfigureMockMvc
@DirtiesContext
class DashboardFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemoService memos;

	@Autowired
	RecentActivities activities;

	@Test
	void rootGoesToDashboard() throws Exception {
		this.mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/dashboard"));
	}

	@Test
	void createdMemoAppearsInRecentActivityAndDashboard() throws Exception {
		this.memos.create("이벤트 메모", "본문", true);
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (this.activities.recent(10).isEmpty() && System.nanoTime() < deadline) {
			Thread.sleep(100);
		}
		assertThat(this.activities.recent(10)).extracting((a) -> a.message())
			.containsExactly("메모를 만들었습니다: 이벤트 메모");

		String page = body(this.mvc.perform(get("/dashboard")).andExpect(status().isOk()).andReturn());
		assertThat(page).contains("<html").contains("전체 메모").contains(">이벤트 메모<")
			.contains("메모를 만들었습니다: 이벤트 메모")
			.contains("hx-get=\"/dashboard/activity\"")
			.contains("hx-trigger=\"every 10s\"");

		String panel = body(this.mvc.perform(get("/dashboard/activity").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(panel).doesNotContain("<html").startsWith("<div id=\"activity\"")
			.contains("메모를 만들었습니다: 이벤트 메모");
	}

	static String body(MvcResult result) throws Exception {
		result.getResponse().setCharacterEncoding("UTF-8");
		return result.getResponse().getContentAsString();
	}
}
