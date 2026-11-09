package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import io.salvia95.cozy.ui.testapp.UiTestApp;

/**
 * 디자인 원칙이 컴포넌트에 들어 있다 (docs/conventions/design.md): 색은 tone으로, 주 버튼은 오른쪽 끝, 폼은 가운데 좁게,
 * 삭제는 확인 창을 거친다.
 */
@SpringBootTest(classes = UiTestApp.class, properties = "cozy.data-dir=target/test-data/ui-design")
@AutoConfigureMockMvc
class DesignPrinciplesTest {

	@Autowired
	MockMvc mvc;

	String render(String id, String state) throws Exception {
		return UiScreensTest.body(this.mvc.perform(get("/cozy/dev/catalog/render").param("id", id).param("state", state))
			.andExpect(status().isOk())
			.andReturn());
	}

	@Test
	void badgesAndAlertsUseTones() throws Exception {
		assertThat(render("component/badge", "default")).contains("class=\"badge cozy-tone-neutral\"");
		assertThat(render("component/badge", "critical")).contains("class=\"badge cozy-tone-critical\"");
		String critical = render("component/alert", "critical");
		assertThat(critical).contains("alert-danger").contains("#tabler-alert-circle");
		assertThat(render("component/alert", "success")).contains("alert-success").contains("#tabler-circle-check");
	}

	@Test
	void statCardsCarryNoColor() throws Exception {
		String stat = render("component/stat", "default");
		assertThat(stat).contains("class=\"avatar\"").doesNotContainPattern("class=\"[^\"]*\\bbg-");
	}

	@Test
	void formPageIsNarrowWithBreadcrumbAndPrimaryLast() throws Exception {
		String html = render("pattern/form-page", "default");
		assertThat(html).contains("class=\"cozy-width-form\"").contains("class=\"breadcrumb")
			.containsPattern("aria-current=\"page\">메모 만들기<");
		// 오른쪽 아래 [취소] [메모 만들기]
		assertThat(html).containsPattern(Pattern.compile(
				"btn-list justify-content-end\">\\s*<a class=\"btn\"[^>]*>취소</a>\\s*<button class=\"btn btn-primary\"", Pattern.DOTALL));
	}

	@Test
	void deleteGoesThroughAConfirmDialog() throws Exception {
		String html = render("component/delete-confirm", "default");
		assertThat(html).contains("data-bs-toggle=\"modal\"").contains("data-bs-target=\"#sample-delete\"")
			.contains(">메모 삭제<").contains("<strong>회의록 정리</strong>").contains("되돌릴 수 없습니다")
			.contains("action=\"/memo/1/delete\"");
		assertThat(html.indexOf("data-bs-dismiss=\"modal\">취소")).isPositive()
			.isLessThan(html.indexOf("cozy-btn-critical-fill"));
	}

	@Test
	void detailsHeaderPutsTheEditActionLast() throws Exception {
		String html = render("pattern/details", "default");
		assertThat(html.indexOf("cozy-btn-critical\"")).isPositive().isLessThan(html.indexOf("btn btn-primary"));
	}

	@Test
	void flashKindsAreTones() {
		assertThat(CozyFlash.critical("x").kind()).isEqualTo("critical");
		assertThatIllegalArgumentException().isThrownBy(() -> new CozyFlash("danger", "x")).withMessageContaining("critical");
	}
}
