package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.salvia95.cozy.runtime.update.CozyUpdates;
import io.salvia95.cozy.ui.testapp.UiTestApp;

/**
 * app-shell의 앱 메뉴(진단 내보내기, 종료)와 업데이트 배너.
 */
@SpringBootTest(classes = UiTestApp.class, properties = "cozy.data-dir=target/test-data/ui-app-menu")
@AutoConfigureMockMvc
@Import(AppMenuAndUpdateBannerTest.FakeUpdates.class)
class AppMenuAndUpdateBannerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void appShellHasDiagnosticsAndShutdownForms() throws Exception {
		String html = body(this.mvc.perform(get("/uitest")).andExpect(status().isOk()).andReturn());
		assertThat(html).containsPattern("<form class=\"m-0\" method=\"post\" action=\"/cozy/diagnostics\">\\s*<input type=\"hidden\" name=\"_csrf\"")
			.containsPattern("<form class=\"m-0\" method=\"post\" action=\"/cozy/shutdown\" data-cozy-confirm=\"앱을 종료할까요\\?\">\\s*<input type=\"hidden\" name=\"_csrf\"")
			.contains("#tabler-file-download").contains("#tabler-power");
	}

	@Test
	void updateBannerOffersInstallAndShowsLastOutcome() throws Exception {
		String html = body(this.mvc.perform(get("/uitest")).andExpect(status().isOk()).andReturn());
		assertThat(html).contains("alert-danger")
			.contains("필수 업데이트입니다.")
			.contains("새 버전 2.0.0이 있습니다.")
			.contains("검색 개선")
			.containsPattern("<form method=\"post\" action=\"/cozy/update\"[^>]*data-cozy-confirm=")
			.contains("<input type=\"hidden\" name=\"version\" value=\"2.0.0\">")
			.contains("v1.0.1 업데이트를 시작하지 못해 v1.0.0으로 되돌렸습니다.");
	}

	@Test
	void diagnosticsFlashIsShownByAppShell() throws Exception {
		MvcResult posted = this.mvc.perform(post("/cozy/diagnostics").with(csrf()).header("Referer", "http://localhost/uitest"))
			.andExpect(status().isFound())
			.andReturn();
		String html = body(this.mvc.perform(get("/uitest").flashAttrs(posted.getFlashMap())).andReturn());
		assertThat(html).contains("alert-success").contains("진단 파일을 만들었습니다");
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	@TestConfiguration
	static class FakeUpdates {

		@Bean
		@Primary
		CozyUpdates fakeUpdates() {
			return new CozyUpdates() {
				@Override
				public Optional<Offer> offer() {
					return Optional.of(new Offer("2.0.0", true, "검색 개선", true, ""));
				}

				@Override
				public Optional<Outcome> lastOutcome() {
					return Optional.of(new Outcome(false, "v1.0.1 업데이트를 시작하지 못해 v1.0.0으로 되돌렸습니다."));
				}
			};
		}

	}
}
