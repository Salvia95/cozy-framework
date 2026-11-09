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

import io.salvia95.cozy.reference.activity.spi.ActivityFinder;
import io.salvia95.cozy.reference.memo.spi.MemoFacade;

/**
 * 이벤트 흐름: memo가 MemoCreated를 발행 → activity가 받아 기록 → dashboard가 memo·activity의 spi fragment로 보여 준다.
 */
@SpringBootTest(properties = "cozy.data-dir=target/test-data/dashboard-${random.uuid}")
@AutoConfigureMockMvc
@DirtiesContext
class DashboardFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemoFacade memos;

	@Autowired
	ActivityFinder activities;

	@Autowired
	io.salvia95.cozy.reference.memo.spi.MemoFinder finder;

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

	/** "가장 최근에 만든 메모"는 만든 시각 기준이다. 고정이나 나중에 고친 것이 앞서지 않는다 */
	@Test
	void latestCardShowsTheMostRecentlyCreatedMemo() throws Exception {
		long first = this.memos.create("먼저 만든 고정 메모", "", true);
		this.memos.create("나중에 만든 메모", "", false);
		this.memos.update(first, "먼저 만든 고정 메모 (수정)", "", true);
		assertThat(this.finder.summary().latestTitle()).isEqualTo("나중에 만든 메모");
	}

	static String body(MvcResult result) throws Exception {
		result.getResponse().setCharacterEncoding("UTF-8");
		return result.getResponse().getContentAsString();
	}
}
