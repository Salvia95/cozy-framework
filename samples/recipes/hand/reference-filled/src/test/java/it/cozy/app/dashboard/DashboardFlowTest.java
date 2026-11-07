package it.cozy.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import it.cozy.app.activity.spi.ActivityFinder;
import it.cozy.app.memo.spi.MemoFacade;

/** 레시피로 만든 구조가 이어진다: memo가 MemoCreated를 발행 → activity 리스너가 기록 → dashboard가 보여 준다. */
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

	@Test
	void createdMemoAppearsInRecentActivityAndDashboard() throws Exception {
		this.memos.create("이벤트 메모", "본문", true);
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (this.activities.recent(10).isEmpty() && System.nanoTime() < deadline) {
			Thread.sleep(100);
		}
		assertThat(this.activities.recent(10)).extracting((a) -> a.message()).containsExactly("메모를 만들었습니다: 이벤트 메모");
		var result = this.mvc.perform(get("/dashboard")).andExpect(status().isOk()).andReturn();
		result.getResponse().setCharacterEncoding("UTF-8");
		assertThat(result.getResponse().getContentAsString()).contains("전체 메모").contains("메모를 만들었습니다: 이벤트 메모");
	}
}
