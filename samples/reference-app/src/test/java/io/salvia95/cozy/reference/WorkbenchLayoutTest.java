package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 워크벤치 화면 보기의 세 칸: 왼쪽에 모듈별 화면 목록과 구조 트리, 가운데에 미리보기나 소스, 오른쪽에 속성이나 샘플 데이터.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-layout-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchLayoutTest {

	@Autowired
	MockMvc mvc;

	String page(String query) throws Exception {
		return this.mvc.perform(get("/cozy/dev/workbench" + query)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	static int count(String html, String part) {
		return html.split(java.util.regex.Pattern.quote(part), -1).length - 1;
	}

	@Test
	void threePanesWithTheMenuOnOneLine() throws Exception {
		String html = page("?spec=memo-list");
		assertThat(html).contains("cozy-workbench-bar")
			.contains("cozy-workbench-panes")
			.contains("cozy-workbench-side")
			.contains("cozy-workbench-main")
			.contains("cozy-workbench-inspector");
		assertThat(html.indexOf("cozy-workbench-side")).isLessThan(html.indexOf("cozy-workbench-main"));
		assertThat(html.indexOf("cozy-workbench-main")).isLessThan(html.indexOf("cozy-workbench-inspector"));
	}

	@Test
	void screensAreGroupedByModuleAndNamedByTheirTitle() throws Exception {
		String html = page("?spec=memo-list");
		assertThat(html.indexOf(">dashboard<")).as("모듈 머리").isPositive().isLessThan(html.indexOf(">대시보드<"));
		assertThat(html.indexOf(">memo<")).isPositive().isLessThan(html.indexOf(">메모 목록<"));
		assertThat(html).contains(">메모 만들기<").contains(">메모 상세<").contains(">메모 수정<").contains(">memo-list<");
	}

	@Test
	void theStructureIsANestedTreeThatFolds() throws Exception {
		String html = page("?spec=memo-list");
		assertThat(count(html, "<ul class=\"cozy-workbench-nodes\">")).as("마디가 있는 줄마다 안쪽 목록").isGreaterThan(4);
		assertThat(count(html, "<details open")).isGreaterThan(4);
		int pattern = html.indexOf(">목록 탐색<");
		int badge = html.indexOf(">배지<");
		assertThat(pattern).isPositive().isLessThan(badge);
		assertThat(html.substring(html.indexOf(">페이지네이션<") - 600, html.indexOf(">페이지네이션<"))).as("안에 마디가 없는 줄은 접는 틀이 없다")
			.doesNotContain("<summary");
	}

	@Test
	void everyNodeCarriesItsProperties() throws Exception {
		String html = page("?spec=memo-list");
		assertThat(count(html, "data-cozy-workbench-node-props")).isEqualTo(count(html, "data-cozy-line="));
		assertThat(html).contains(">기본 정보<")
			.contains(">매개변수<")
			.contains("<code>pattern/list-with-filter</code>")
			.contains("<code>component/badge</code>")
			.contains("상태나 분류를 짧게 표시한다")
			.contains("src/main/resources/templates/memo/api/list.html:7–58")
			.contains(">필터<")
			.contains(">색상<")
			.contains("<code>&#39;neutral&#39;</code>");
	}

	@Test
	void theInspectorShowsTheScreenUntilSomethingIsSelected() throws Exception {
		String html = page("?spec=memo-list");
		String box = html.substring(html.indexOf("data-cozy-workbench-props"));
		assertThat(box).contains(">메모 목록<")
			.contains("<code>memo-list</code>")
			.contains("src/main/resources/specs/memo-list.yml")
			.contains("4개 · 기본, 데이터 없음, 검색 결과 없음, 여러 페이지")
			.contains(">경로 복사<");
	}

	@Test
	void sampleDataSitsBesideThePreview() throws Exception {
		String html = page("?spec=memo-list&view=fixture");
		assertThat(html).contains("data-cozy-workbench-frame").contains("name=\"v.filter/q\"").contains("view=fixture");
		assertThat(html.indexOf("data-cozy-workbench-frame")).isLessThan(html.indexOf("name=\"v.filter/q\""));
		assertThat(page("?spec=memo-list&view=template")).as("소스는 가운데에 보인다").contains("id=\"L7\"").doesNotContain("data-cozy-workbench-frame");
	}
}
