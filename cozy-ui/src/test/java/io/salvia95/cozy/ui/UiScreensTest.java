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
 * 레이아웃, 내비, htmx 부분 갱신, PRG, 로컬 자산.
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
		Matcher m = Pattern.compile("(?:href|src)=\"(/cozy/[^\"#?]+)(\\?[^\"#]*)?(?:#[^\"]*)?\"").matcher(html);
		java.util.Set<String> assets = new java.util.TreeSet<>();
		while (m.find()) {
			assets.add(m.group(1));
			this.mvc.perform(get(m.group(1) + ((m.group(2) != null) ? m.group(2) : ""))).andExpect(status().isOk());
		}
		assertThat(assets).containsExactlyInAnyOrder("/cozy/vendor/pretendard/1.3.9/pretendardvariable.css",
				"/cozy/vendor/tabler/1.7.5/css/tabler.min.css", "/cozy/cozy.css", "/cozy/vendor/tabler/1.7.5/js/tabler.min.js",
				"/cozy/vendor/htmx/2.0.11/htmx.min.js", "/cozy/cozy.js", "/cozy/vendor/alpinejs-csp/3.17.5/cdn.min.js",
				"/cozy/vendor/tabler-icons/3.51.0/tabler-sprite.svg", "/cozy/favicon.svg", "/cozy/modules.js");
		// Alpine은 컴포넌트를 등록하는 cozy.js 뒤에 시작한다 (모두 defer라 문서 순서대로 실행된다)
		assertThat(html.indexOf("/cozy/cozy.js")).isLessThan(html.indexOf("/cozy/vendor/alpinejs-csp/3.17.5/cdn.min.js"));
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
	void htmxRequestThatFollowsRedirectsKeepsThePostRedirectGet() throws Exception {
		// 받은 화면에서 영역을 고르는 요소(hx-select)는 리다이렉트를 따라가 다음 화면을 받는다
		MockHttpSession session = new MockHttpSession();
		this.mvc.perform(post("/uitest").with(csrf()).header("HX-Request", "true").header(CozyViews.FOLLOW_REDIRECT_HEADER, "follow")
			.session(session))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/uitest"))
			.andExpect(header().doesNotExist("HX-Redirect"));
		assertThat(body(this.mvc.perform(get("/uitest").session(session)).andReturn())).contains("저장했습니다");
	}

	@Test
	void moduleScriptsLoadAsOneBundleBeforeAlpine() throws Exception {
		// 모듈마다 static/{모듈}/{모듈}.js 하나. 레이아웃은 그것들을 이은 묶음 하나를 싣는다
		String html = body(this.mvc.perform(get("/uitest")).andExpect(status().isOk()).andReturn());
		Matcher src = Pattern
			.compile("<script src=\"(/cozy/modules\\.js\\?v=([0-9a-f]{12}))\" data-cozy-parts=\"static/uitest/uitest.js:2\" defer></script>")
			.matcher(html);
		assertThat(src.find()).as(html).isTrue();
		assertThat(src.find()).as("묶음은 한 번만 싣는다").isFalse();
		assertThat(html).doesNotContain("/cozy/modules/").doesNotContain("uitest.js\"");
		assertThat(html.indexOf("/cozy/cozy.js")).isLessThan(html.indexOf("/cozy/modules.js"));
		assertThat(html.indexOf("/cozy/modules.js")).isLessThan(html.indexOf("alpinejs-csp"));
		src.reset().find();

		// 주소의 해시가 지금 내용과 같으면 오래 캐시한다. 내용이 바뀌면 주소도 바뀐다
		MvcResult current = this.mvc.perform(get(src.group(1)))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("javascript")))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
			.andReturn();
		assertThat(body(current)).contains("/* static/uitest/uitest.js */").contains("uitestSample").doesNotContain("extra");

		// 옛 주소나 해시 없는 주소는 지금 내용을 캐시하지 않고 준다
		for (String stale : java.util.List.of("/cozy/modules.js?v=000000000000", "/cozy/modules.js")) {
			MvcResult result = this.mvc.perform(get(stale))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-cache"))
				.andReturn();
			assertThat(body(result)).contains("uitestSample");
		}
		// 모듈마다 따로 주던 주소는 없다
		this.mvc.perform(get("/cozy/modules/uitest/uitest.js")).andExpect(status().isNotFound());
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
