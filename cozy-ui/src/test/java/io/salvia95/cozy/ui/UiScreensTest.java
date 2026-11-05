package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.salvia95.cozy.ui.testapp.UiTestApp;

/**
 * 레이아웃, 내비, htmx 부분 갱신, PRG, 로컬 자산 (PRD §6.4, M3 완료 기준).
 */
@SpringBootTest(classes = UiTestApp.class, properties = "cozy.data-dir=target/test-data/ui-screens")
@AutoConfigureMockMvc
class UiScreensTest {

	@Autowired
	MockMvc mvc;

	@Test
	void fullPageUsesAppShellWithSortedNavAndLocalAssets() throws Exception {
		String html = body(this.mvc.perform(get("/uitest")).andExpect(status().isOk()).andReturn());
		assertThat(html).startsWith("<!DOCTYPE html>")
			.contains("<title>항목 · UI 테스트</title>")
			.contains("name=\"_csrf\"")
			.contains("navbar-vertical")
			.contains("id=\"cozy-toasts\"");
		assertThat(html.indexOf(">항목<")).isLessThan(html.indexOf(">둘째<"));
		assertThat(html).containsPattern("class=\"nav-item active\"[\\s\\S]*?항목");
		// 모든 자산이 로컬이고 실제로 내려온다 (네트워크를 끊어도 같게 그려진다)
		Matcher m = Pattern.compile("(?:href|src)=\"(/cozy/[^\"]+)\"").matcher(html);
		int count = 0;
		while (m.find()) {
			this.mvc.perform(get(m.group(1))).andExpect(status().isOk());
			count++;
		}
		assertThat(count).isEqualTo(6);
		assertThat(html).doesNotContainPattern("(?:href|src)=\"(?:https?:)?//");
	}

	@Test
	void htmxRequestGetsOnlyTheResultFragment() throws Exception {
		String html = body(this.mvc.perform(get("/uitest").param("q", "나").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(html).doesNotContain("<html").doesNotContain("navbar").contains("id=\"result\"").contains(">나<")
			.doesNotContain(">가<");
		assertThat(html).contains("hx-get=\"/uitest?page=1\"").contains("hx-include=\"#filter\"");
	}

	@Test
	void boostedRequestGetsTheFullPage() throws Exception {
		String html = body(this.mvc.perform(get("/uitest").header("HX-Request", "true").header("HX-Boosted", "true"))
			.andReturn());
		assertThat(html).contains("<html");
	}

	@Test
	void postRedirectGetForPlainAndHtmxRequests() throws Exception {
		this.mvc.perform(post("/uitest").with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/uitest"))
			.andExpect(flash().attribute(CozyFlash.ATTRIBUTE, CozyFlash.success("저장했습니다")));

		MockHttpSession session = new MockHttpSession();
		MvcResult htmx = this.mvc.perform(post("/uitest").with(csrf()).header("HX-Request", "true").session(session))
			.andExpect(status().isOk())
			.andExpect(header().string("HX-Redirect", "/uitest"))
			.andReturn();
		assertThat(htmx.getResponse().getContentAsString()).isEmpty();
		// HX-Redirect로 이동한 다음 화면에 플래시가 보인다
		String next = body(this.mvc.perform(get("/uitest").session(session)).andReturn());
		assertThat(next).contains("alert-success").contains("저장했습니다");
	}

	@Test
	void htmxHeadersAreResolved() throws Exception {
		String text = this.mvc
			.perform(get("/uitest/htmx").header("HX-Request", "true").header("HX-Target", "result").header("HX-Trigger", "filter"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(text).isEqualTo("true|true|result|filter");
	}

	@Test
	void errorPagesUseCozyLayout() throws Exception {
		// MockMvc는 컨테이너의 오류 디스패치를 하지 않으므로 /error를 직접 부른다
		String html = body(this.mvc
			.perform(get("/error").requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404)
				.requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/uitest/missing")
				.accept(org.springframework.http.MediaType.TEXT_HTML))
			.andExpect(status().isNotFound())
			.andReturn());
		assertThat(html).contains("요청을 처리할 수 없습니다").contains("/cozy/cozy.css");
	}

	static String body(MvcResult result) throws Exception {
		result.getResponse().setCharacterEncoding("UTF-8");
		return result.getResponse().getContentAsString();
	}
}
