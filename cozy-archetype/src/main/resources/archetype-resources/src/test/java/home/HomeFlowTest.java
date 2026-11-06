package ${package}.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 홈 화면 흐름: 등록(PRG), 검증 오류, htmx 부분 갱신, 최근 활동. */
@SpringBootTest(properties = "cozy.data-dir=target/test-data/home-flow")
@AutoConfigureMockMvc
class HomeFlowTest {

	@Autowired
	MockMvc mvc;

	@Test
	void addNoteThenSeeItInListAndActivity() throws Exception {
		this.mvc.perform(post("/notes").with(csrf()).param("title", "첫 메모"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/"));
		assertThat(page("/")).contains("첫 메모");
		// events 옵션이면 리스너가 비동기로 기록하므로 잠시 기다린다
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (!page("/").contains("메모를 추가했습니다: 첫 메모") && System.nanoTime() < deadline) {
			Thread.sleep(100);
		}
		assertThat(page("/")).contains("메모를 추가했습니다: 첫 메모");
	}

	@Test
	void blankTitleShowsErrorInFormFragment() throws Exception {
		String html = this.mvc.perform(post("/notes").with(csrf()).param("title", " ").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(html).doesNotContain("<html").contains("id=\"noteForm\"").contains("제목을 입력하세요");
	}

	@Test
	void htmxFilterReturnsOnlyResultAndSuccessUsesHxRedirect() throws Exception {
		this.mvc.perform(post("/notes").with(csrf()).param("title", "검색 대상").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(header().string("HX-Redirect", "/"));
		String html = this.mvc.perform(get("/").param("q", "검색").header("HX-Request", "true"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(html).doesNotContain("<html").contains("id=\"result\"").contains("검색 대상");
	}

	private String page(String path) throws Exception {
		return this.mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
	}
}
