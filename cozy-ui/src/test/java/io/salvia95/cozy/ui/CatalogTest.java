package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.testapp.UiTestApp;

/**
 * 매니페스트의 모든 fragment가 모든 상태에서 오류 없이 그려진다.
 */
@SpringBootTest(classes = UiTestApp.class, properties = "cozy.data-dir=target/test-data/ui-catalog")
@AutoConfigureMockMvc
class CatalogTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	FragmentManifest manifest;

	@Test
	void manifestCoversV1FragmentList() {
		assertThat(this.manifest.fragments()).extracting(FragmentManifest.Entry::id)
			.contains("layout/base", "layout/app-shell", "pattern/list-with-filter", "pattern/details",
					"pattern/master-detail", "pattern/form-page", "pattern/polling-panel", "component/field",
					"component/data-table", "component/pagination", "component/badge", "component/alert",
					"component/empty-state", "component/modal", "component/toast", "component/loading");
	}

	/**
	 * fragment를 받는 fragment 안에서는 파라미터가 같은 이름의 모델 속성을 가린다. 예를 들어 페이지의 ${result}가
	 * 패턴 파라미터 result로 바뀐다. 그래서 이런 fragment의 파라미터는 모두 cozy로 시작해야 한다.
	 */
	@Test
	void slotTakingFragmentsUseReservedParameterNames() {
		for (FragmentManifest.Entry entry : this.manifest.fragments()) {
			if (entry.params().stream().anyMatch(FragmentManifest.Param::isFragment)) {
				assertThat(entry.params()).as(entry.id())
					.allSatisfy((p) -> assertThat(p.name()).startsWith("cozy"));
			}
		}
	}

	@Test
	void catalogListsEveryFragmentAndState() throws Exception {
		String html = UiScreensTest.body(this.mvc.perform(get("/cozy/dev/catalog")).andExpect(status().isOk()).andReturn());
		for (FragmentManifest.Entry entry : this.manifest.fragments()) {
			assertThat(html).contains(entry.template() + " :: " + entry.signature());
			for (String state : entry.samples().keySet()) {
				assertThat(html).contains("state=" + state);
			}
		}
	}

	@Test
	void everySampleRendersWithoutError() throws Exception {
		List<String> rendered = new ArrayList<>();
		for (FragmentManifest.Entry entry : this.manifest.fragments()) {
			for (Map.Entry<String, Map<String, Object>> state : entry.samples().entrySet()) {
				String html = UiScreensTest.body(this.mvc
					.perform(get("/cozy/dev/catalog/render").param("id", entry.id()).param("state", state.getKey()))
					.andExpect(status().isOk())
					.andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
					.andReturn());
				assertThat(html).as(entry.id() + " " + state.getKey()).startsWith("<!DOCTYPE html>").contains("</html>");
				rendered.add(entry.id() + "|" + state.getKey());
			}
		}
		assertThat(rendered).hasSizeGreaterThan(20);
	}

	@Test
	void fieldSampleShowsValidationError() throws Exception {
		String html = UiScreensTest.body(this.mvc
			.perform(get("/cozy/dev/catalog/render").param("id", "component/field").param("state", "error"))
			.andReturn());
		assertThat(html).contains("is-invalid").contains("제목을 입력하세요");
	}
}
