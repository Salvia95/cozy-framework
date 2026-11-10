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
		void thePropertyTurnsItOff() throws Exception {
			this.mvc.perform(get("/cozy/dev/workbench")).andExpect(status().isNotFound());
			this.mvc.perform(get("/cozy/dev/catalog")).andExpect(status().isOk());
		}
	}
}
