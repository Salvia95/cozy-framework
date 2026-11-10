package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.salvia95.cozy.test.CozyHtmlValidator;
import io.salvia95.cozy.ui.catalog.ScreenSpec;

/**
 * 워크벤치 {@code /cozy/dev/workbench}: dev에서만 뜨고, 앱의 모든 화면과 상태를 구조·미리보기·소스로 보인다.
 */
class WorkbenchTest {

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-${random.uuid}",
			"cozy.runtime.open-browser=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class InDev {

		@Autowired
		MockMvc mvc;

		String page(String query) throws Exception {
			return this.mvc.perform(get("/cozy/dev/workbench" + query))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		}

		@Test
		void everyScreenAndStateIsShown() throws Exception {
			for (ScreenSpec spec : ScreenSpec.load(getClass().getClassLoader())) {
				for (String state : spec.states().keySet()) {
					String html = page("?spec=" + spec.screen() + "&state=" + state);
					assertThat(html).as(spec.screen() + " " + state)
						.contains("/cozy/dev/catalog/screen?spec=" + spec.screen() + "&amp;state=" + state)
						.contains(spec.template());
				}
			}
		}

		@Test
		void theFirstScreenAndItsDefaultStateOpenWithoutParameters() throws Exception {
			String html = page("");
			assertThat(html).contains("워크벤치").contains("dashboard-index").contains("memo-list").contains("state=default");
		}

		@Test
		void theStructureShowsPatternSlotsAndFragmentsWithLines() throws Exception {
			String html = page("?spec=memo-list");
			assertThat(html).contains("list-with-filter")
				.contains("cozyFilter")
				.contains("#filter")
				.contains("cozyResult")
				.contains("data-table")
				.contains("pagination")
				.contains("templates/memo/api/list.html:7")
				.as("줄 번호는 템플릿 소스의 그 줄로 간다")
				.contains("view=template#L7");
			assertThat(html).as("아이콘은 구조에서 뺀다").doesNotContain("cozy/icon/tabler");
		}

		@Test
		void viewportsNarrowTheFrame() throws Exception {
			assertThat(page("?spec=memo-list&w=768")).contains("cozy-workbench-frame-768");
			assertThat(page("?spec=memo-list")).doesNotContain("cozy-workbench-frame-768");
		}

		@Test
		void sourceViewsShowTheTemplateAndTheSpecWithLineNumbers() throws Exception {
			String template = page("?spec=memo-list&view=template");
			assertThat(template).contains("src/main/resources/templates/memo/api/list.html")
				.contains("listWithFilter(&#39;메모&#39;");
			String spec = page("?spec=memo-list&view=spec");
			assertThat(spec).contains("src/main/resources/specs/memo-list.yml").contains("screen: memo-list");
		}

		String body(String url) throws Exception {
			return this.mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		}

		@Test
		void thePreviewCarriesSourceLocationsOnlyWhenTheWorkbenchAsks() throws Exception {
			String plain = body("/cozy/dev/catalog/screen?spec=memo-list&state=default");
			String inspected = body("/cozy/dev/catalog/screen?spec=memo-list&state=default&inspect=1");
			assertThat(plain).doesNotContain("data-cozy-src");
			assertThat(inspected).as("앱 템플릿의 요소: 필터 폼(10번 줄), 결과 영역(28번 줄)")
				.contains("data-cozy-src=\"memo/api/list:10\"")
				.contains("data-cozy-src=\"memo/api/list:28\"")
				.as("프레임워크 템플릿의 요소도 자기 위치를 가진다")
				.contains("data-cozy-src=\"cozy/pattern/list-with-filter:")
				.contains("data-cozy-src=\"cozy/layout/base:");
			// CSRF 토큰은 요청마다 다르게 가려져 나온다
			String token = "(name=\"_csrf\" (?:content|value)=\")[^\"]*";
			assertThat(inspected.replaceAll(" data-cozy-src=\"[^\"]*\"", "").replaceAll(token, "$1"))
				.as("위치 표시 말고는 한 글자도 다르지 않다")
				.isEqualTo(plain.replaceAll(token, "$1"));
		}

		@Test
		void appPagesAreNeverStamped() throws Exception {
			assertThat(body("/memo?inspect=1")).doesNotContain("data-cozy-src");
			assertThat(body("/cozy/dev/catalog/render?id=component/badge&state=default&inspect=1"))
				.doesNotContain("data-cozy-src");
		}

		@Test
		void theWorkbenchAsksForLocationsAndGivesTheScriptWhatItNeeds() throws Exception {
			String html = page("?spec=memo-list&state=paged");
			assertThat(html).contains("/cozy/dev/catalog/screen?spec=memo-list&amp;state=paged&amp;inspect=1")
				.contains("src=\"/cozy/workbench.js\"")
				.contains("요소 선택")
				.contains("에이전트용 복사")
				.contains("data-template=\"memo/api/list\"")
				.contains("data-spec-file=\"src/main/resources/specs/memo-list.yml\"")
				.contains("data-playbook=\".agents/skills/cozy-helper/references/screens/PLAYBOOK.md\"");
			assertThat(html).as("구조의 줄마다 시작·끝 줄과 삽입 대상")
				.contains("data-cozy-line=\"7\"")
				.contains("data-cozy-end=\"58\"")
				.contains("data-cozy-target=\"cozy/pattern/list-with-filter\"");
			assertThat(body("/cozy/workbench.js")).contains("data-cozy-src");
		}

		@Test
		void everyViewIsValidHtml() throws Exception {
			CozyHtmlValidator validator = new CozyHtmlValidator();
			for (String query : java.util.List.of("?spec=memo-list", "?spec=memo-list&w=768", "?spec=memo-list&view=template",
					"?spec=dashboard-index&view=spec")) {
				assertThat(validator.validate(page(query)).stream().filter(CozyHtmlValidator.Message::isError))
					.as(query)
					.isEmpty();
			}
		}

		@Test
		void unknownValuesAreRefused() throws Exception {
			this.mvc.perform(get("/cozy/dev/workbench?spec=no-such")).andExpect(status().isNotFound());
			this.mvc.perform(get("/cozy/dev/workbench?spec=memo-list&state=no-such")).andExpect(status().isNotFound());
			this.mvc.perform(get("/cozy/dev/workbench?spec=memo-list&w=123")).andExpect(status().isBadRequest());
			this.mvc.perform(get("/cozy/dev/workbench?spec=memo-list&view=x")).andExpect(status().isBadRequest());
		}
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-off-${random.uuid}",
			"cozy.runtime.open-browser=false" })
	@AutoConfigureMockMvc
	@DirtiesContext
	class OutsideDev {

		@Autowired
		MockMvc mvc;

		@Test
		void theWorkbenchIsNotRegistered() throws Exception {
			this.mvc.perform(get("/cozy/dev/workbench")).andExpect(status().isNotFound());
		}
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-disabled-${random.uuid}",
			"cozy.runtime.open-browser=false", "cozy.dev.workbench.enabled=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class DisabledInDev {

		@Autowired
		MockMvc mvc;

		@Test
		void previewsAreNotStampedWithoutTheWorkbench() throws Exception {
			assertThat(this.mvc.perform(get("/cozy/dev/catalog/screen?spec=memo-list&state=default&inspect=1"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString()).doesNotContain("data-cozy-src");
		}

		@Test
		void thePropertyTurnsItOff() throws Exception {
			this.mvc.perform(get("/cozy/dev/workbench")).andExpect(status().isNotFound());
			this.mvc.perform(get("/cozy/dev/catalog")).andExpect(status().isOk());
		}
	}
}
