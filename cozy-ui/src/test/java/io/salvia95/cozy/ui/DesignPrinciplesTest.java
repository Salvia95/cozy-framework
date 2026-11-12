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
	void dialogsShareOneTitleStructure() throws Exception {
		// 확인 창은 모두 같은 제목 구조: role=dialog, 제목 h2.modal-title, aria-labelledby로 제목을 가리킨다
		for (String[] dialog : new String[][] { { "component/modal", "sample-modal" },
				{ "component/delete-confirm", "sample-delete" } }) {
			String html = render(dialog[0], "default").replaceAll("\\s+", " ");
			String id = dialog[1];
			assertThat(html).as(dialog[0])
				.containsPattern("<div class=\"modal modal-blur fade\" tabindex=\"-1\" role=\"dialog\" aria-hidden=\"true\" id=\""
						+ id + "\" aria-labelledby=\"" + id + "-title\">")
				.contains("<h2 class=\"modal-title\" id=\"" + id + "-title\"")
				.doesNotContainPattern("<h[13-6] class=\"modal-title\"");
		}
	}

	@Test
	void everyFormInputMeetsNonTextContrast() throws Exception {
		// 모든 입력(글자 칸, 셀렉트, 체크박스, 스위치)의 테두리와 포커스 표시는 모든 화면에서 3:1 이상이고 같은 값이다
		// (Tabler 기본은 흰 바탕에서 테두리 1.26:1, 포커스 2.1:1)
		String css = this.mvc.perform(get("/cozy/cozy.css")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		java.util.regex.Matcher token = Pattern.compile("--cozy-control-border: #([0-9a-f]{6});").matcher(css);
		assertThat(token.find()).as("입력 테두리 토큰").isTrue();
		assertThat(contrastOnWhite(token.group(1))).as("입력 테두리 대비").isGreaterThanOrEqualTo(3.0);
		assertThat(css)
			.containsPattern("(?m)^:is\\(\\.form-control, \\.form-select, \\.form-check-input, \\.input-group-text\\) "
					+ "\\{ border-color: var\\(--cozy-control-border\\); \\}")
			.containsPattern("(?m)^:is\\(\\.form-control, \\.form-select, \\.form-check-input\\):focus-visible \\{ "
					+ "border-color: var\\(--tblr-primary\\); box-shadow: 0 0 0 2px var\\(--tblr-primary\\); outline: none; \\}")
			// Tabler가 꺼진 스위치의 테두리를 투명으로 두는 규칙(0,3,0)보다 같거나 높아야 한다
			.containsPattern("(?m)^\\.form-switch \\.form-check-input:not\\(:checked, :disabled\\) \\{ border-color: var\\(--cozy-control-border\\); \\}")
			// 오류 칸에 포커스가 가도 링은 하나 (Tabler의 오류 outline을 끈다)
			.containsPattern("(?m)^:is\\(\\.form-control, \\.form-select, \\.form-check-input\\)\\.is-invalid:focus-visible \\{ outline: none; \\}")
			// 오류인 체크박스·라디오의 글자: Tabler 빨강은 옅은 회색 바탕에서 4.47:1이라 칸 오류 글자와 같은 진한 critical로
			.containsPattern("(?m)^\\.form-check-input\\.is-invalid ~ \\.form-check-label \\{ color: var\\(--cozy-critical-fg\\); \\}");
		assertThat(css).as("입력의 포커스와 체크박스 테두리를 화면마다 따로 고치지 않는다")
			.doesNotContainPattern("\\.cozy-(?!grid )[a-z-]+ [^{]*\\.form-(?:check-input|control|select)\\)?:focus")
			.doesNotContainPattern("\\.cozy-(?!grid )[a-z-]+ [^{]*\\.form-(?:check-input|control|select)(?::not\\([^)]*\\))? \\{[^}]*border-color");
	}

	static double contrastOnWhite(String hex) {
		double luminance = 0;
		double[] weights = { 0.2126, 0.7152, 0.0722 };
		for (int i = 0; i < 3; i++) {
			double c = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16) / 255.0;
			luminance += weights[i] * (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
		}
		return 1.05 / (luminance + 0.05);
	}

	@Test
	void selectPicksOneValueFromOptions() throws Exception {
		String html = render("component/select", "default").replaceAll("\\s+", " ");
		assertThat(html).contains("<label class=\"form-label\" for=\"status\">상태</label>")
			.containsPattern("<select class=\"form-select\" id=\"status\" name=\"status\">")
			.contains("<option value=\"\">선택하세요</option>")
			.contains("<option value=\"DOING\" selected=\"selected\">진행 중</option>")
			.contains("<option value=\"TODO\">할 일</option>");
		String error = render("component/select", "error").replaceAll("\\s+", " ");
		assertThat(error).contains("class=\"form-select is-invalid\"").contains("class=\"invalid-feedback d-block\">상태를 고르세요<");
	}

	@Test
	void checkboxGroupPicksSeveralAndRadioGroupPicksOne() throws Exception {
		String checkboxes = render("component/checkbox-group", "default").replaceAll("\\s+", " ");
		assertThat(checkboxes).contains("<fieldset class=\"mb-3\">").contains("<legend class=\"form-label\">알림 받을 곳</legend>")
			.containsPattern("<input type=\"checkbox\" class=\"form-check-input\" value=\"MAIL\" id=\"channels1\" name=\"channels\" checked=\"checked\">")
			.containsPattern("<input type=\"checkbox\" class=\"form-check-input\" value=\"SMS\" id=\"channels2\" name=\"channels\">");
		String radios = render("component/radio-group", "default").replaceAll("\\s+", " ");
		assertThat(radios).contains("<legend class=\"form-label\">중요도</legend>")
			.containsPattern("<input type=\"radio\" class=\"form-check-input\" value=\"LOW\" id=\"priority1\" name=\"priority\">")
			.containsPattern("<input type=\"radio\" class=\"form-check-input\" value=\"HIGH\" id=\"priority2\" name=\"priority\" checked=\"checked\">");
		assertThat(render("component/radio-group", "error")).contains("is-invalid").contains("중요도를 고르세요");
	}

	@Test
	void tableHeadersAreCentered() throws Exception {
		// 열 머리, 행 머리, 행 번호처럼 항목을 나타내는 칸은 가운데, 값은 왼쪽
		String css = this.mvc.perform(get("/cozy/cozy.css")).andReturn().getResponse()
			.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(css).containsPattern("(?m)^\\.table th \\{ text-align: center; \\}")
			.doesNotContainPattern("(?s)\\bth[^{}]*\\{[^}]*text-align: (?:left|right|start|end)");
	}

	@Test
	void inputGridDeclaresAnInputPerColumn() throws Exception {
		String html = render("component/input-grid", "default").replaceAll("\\s+", " ");
		assertThat(html).as("머리")
			.contains("<table class=\"table table-bordered cozy-grid\" aria-label=\"필드\">")
			.contains("<th scope=\"col\" class=\"cozy-grid-number\">행</th>")
			.contains("<th scope=\"col\"><span>이름</span><span class=\"cozy-grid-required\" aria-hidden=\"true\">*</span></th>")
			.contains("<th scope=\"row\" class=\"cozy-grid-number\">1</th>");
		assertThat(html).as("글자 칸")
			.contains("<input class=\"form-control\" type=\"text\" name=\"items[0].title\" value=\"회의록\" autocomplete=\"off\" aria-label=\"이름 1행\">");
		assertThat(html).as("셀렉트")
			.contains("<select class=\"form-select\" name=\"items[0].kind\" aria-label=\"종류 1행\">")
			.contains("<option value=\"\">선택하세요</option>")
			.contains("<option value=\"text\" selected=\"selected\">글자</option>");
		assertThat(html).as("라디오 버튼")
			.contains("<div class=\"cozy-grid-radios\" role=\"radiogroup\" aria-label=\"중요도 1행\">")
			.contains("<input class=\"form-check-input\" type=\"radio\" name=\"items[0].priority\" value=\"HIGH\" checked=\"checked\">");
		assertThat(html).as("체크박스 (Spring의 꺼짐 표시와 함께)")
			.contains("<input type=\"hidden\" name=\"_items[0].required\" value=\"on\">")
			.contains("<input class=\"form-check-input\" type=\"checkbox\" name=\"items[0].required\" value=\"true\" aria-label=\"필수 1행\" checked=\"checked\">");
		assertThat(html).as("날짜").contains("type=\"date\" name=\"items[0].due\" value=\"2026-11-20\"");
		assertThat(html).as("행 작업")
			.contains("name=\"row\" value=\"duplicate:items/0\" aria-label=\"1행 복제\"")
			.contains("name=\"row\" value=\"delete:items/1\" aria-label=\"2행 삭제\"")
			.contains("name=\"row\" value=\"add:items\" aria-label=\"필드에 행 추가\"");
		String error = render("component/input-grid", "error").replaceAll("\\s+", " ");
		assertThat(error).contains("class=\"form-control is-invalid\" type=\"text\" name=\"items[0].title\"")
			.contains("aria-invalid=\"true\"")
			.contains("<div class=\"invalid-feedback d-block\">이름을 입력하세요</div>");
		assertThat(render("component/input-grid", "empty")).contains("행이 없습니다.");
		assertThat(render("component/input-grid", "fixed")).as("행 수가 정해진 그리드").doesNotContain("name=\"row\"");
	}

	@Test
	void gridCellsAreTheInputs() throws Exception {
		// 칸 전체가 입력: 입력의 테두리는 없고 칸의 선(입력 테두리 토큰)이 경계, 포커스는 칸 안쪽 링
		String css = this.mvc.perform(get("/cozy/cozy.css")).andReturn().getResponse()
			.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(css)
			.containsPattern("(?m)^\\.table\\.cozy-grid > :not\\(caption\\) > \\* > \\* \\{ border-color: var\\(--cozy-control-border\\); \\}")
			.containsPattern("(?m)^\\.cozy-grid td > :is\\(\\.form-control, \\.form-select\\) \\{[^}]*border: 0;[^}]*width: 100%;")
			.containsPattern("(?m)^\\.cozy-grid td > :is\\(\\.form-control, \\.form-select\\):focus-visible \\{ box-shadow: inset 0 0 0 2px var\\(--tblr-primary\\); \\}");
	}

	@Test
	void flashKindsAreTones() {
		assertThat(CozyFlash.critical("x").kind()).isEqualTo("critical");
		assertThatIllegalArgumentException().isThrownBy(() -> new CozyFlash("danger", "x")).withMessageContaining("critical");
	}
}
