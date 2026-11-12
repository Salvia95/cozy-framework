package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.salvia95.cozy.test.CozyHtmlValidator;

/**
 * 워크벤치의 개요(첫 화면), 검색, 전체 상태 보기.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-overview-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchOverviewTest {

	static final String URL = "/cozy/dev/workbench";

	@Autowired
	MockMvc mvc;

	String page(String path) throws Exception {
		return this.mvc.perform(get(URL + path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	static void valid(String html, String path) {
		assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).as(path).isEmpty();
	}

	@Test
	void theWorkbenchOpensOnTheOverview() throws Exception {
		String html = page("").replaceAll("\\s+", " ");
		assertThat(html).as("개요 메뉴가 선택된다")
			.contains("aria-current=\"page\" href=\"/cozy/dev/workbench\">개요</a>")
			.contains("href=\"/cozy/dev/workbench/screens\">화면</a>")
			.as("숫자 카드: 화면, 모듈, 규칙 위반, 미구현 항목")
			.contains(">5개<")
			.contains(">3개<")
			.contains(">규칙 위반<")
			.contains(">미구현 항목<")
			.contains("io.salvia95.cozy.reference");
		assertThat(html).as("화면 목록은 모듈별로, 화면 보기와 상태 비교로 이어진다")
			.contains("href=\"/cozy/dev/workbench?spec=memo-list\">메모 목록</a>")
			.contains("기본, 데이터 없음, 검색 결과 없음, 여러 페이지")
			.contains("href=\"/cozy/dev/workbench?spec=memo-list&amp;compare=true\" aria-label=\"메모 목록 상태 비교\"")
			.as("화면이 적으면 모듈 묶음을 모두 펼친다")
			.contains("<details class=\"cozy-workbench-overview-module\" open=\"open\">")
			.doesNotContain("모듈만 펼쳤습니다");
		assertThat(html).as("모듈의 의존 방향")
			.contains("href=\"/cozy/dev/workbench/map?module=memo\">memo</a>")
			.contains("activity, dashboard")
			.as("검사와 미구현 항목")
			.contains("위반이 없습니다.")
			.contains("미구현 항목이 없습니다.");
		valid(html, "개요");
	}

	@Test
	void theScreensMenuOpensTheFirstScreen() throws Exception {
		this.mvc.perform(get(URL + "/screens")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(URL + "?spec=dashboard-index"));
		assertThat(page("?spec=dashboard-index")).contains("aria-current=\"page\" href=\"/cozy/dev/workbench/screens\">화면</a>");
	}

	@Test
	void everyPageHasTheSearchFieldInTheHeader() throws Exception {
		for (String path : List.of("", "?spec=memo-list", "/map", "/check", "/commands")) {
			assertThat(page(path)).as(path)
				.contains("action=\"/cozy/dev/workbench/search\"")
				.contains("aria-label=\"화면, 컴포넌트, 생성 항목, 모듈 검색\"")
				.contains("aria-keyshortcuts=\"/\"");
		}
	}

	@Test
	void searchFindsScreensComponentsRecipesAndModules() throws Exception {
		String screen = page("/search?q=메모목록");
		assertThat(screen).contains("&#39;메모목록&#39; 검색 결과 1건").contains("href=\"/cozy/dev/workbench?spec=memo-list\"");

		String badge = page("/search?q=badge");
		assertThat(badge).contains(">컴포넌트<")
			.contains("href=\"/cozy/dev/catalog#fragment-component-badge\"")
			.contains(">배지<");

		String crud = page("/search?q=crud");
		assertThat(crud).contains(">생성 항목<").contains("href=\"/cozy/dev/workbench/commands?recipe=crud\"").contains(">CRUD 화면<")
			.as("이름에서 찾았으니 설명에 crud가 있는 항목은 뺀다")
			.contains("검색 결과 1건")
			.doesNotContain("설명에서 찾았습니다");
		assertThat(page("/search?q=짧게")).as("이름에 없으면 설명에서").contains("설명에서 찾았습니다").contains(">배지<");

		String memo = page("/search?q=memo");
		assertThat(memo).as("모듈과 화면이 함께").contains(">모듈<").contains("href=\"/cozy/dev/workbench/map?module=memo\"").contains(">화면<");
		assertThat(memo.indexOf(">화면</h3>")).isLessThan(memo.indexOf(">모듈</h3>"));
		valid(memo, "검색");

		String none = page("/search?q=zzzz-없음");
		assertThat(none).contains("검색 결과 없음").contains("&#39;zzzz-없음&#39;와 일치하는 항목이 없습니다");
		String empty = page("/search");
		assertThat(empty).doesNotContain("검색 결과").contains("한글 이름이나 식별자로 찾습니다");
		valid(empty, "빈 검색");
	}

	@Test
	void stateComparisonIsAModalAndThePreviewShowsOneState() throws Exception {
		String html = page("?spec=memo-list&w=768").replaceAll("\\s+", " ");
		assertThat(html).as("미리보기는 상태 하나")
			.contains("data-cozy-workbench-frame")
			.contains("src=\"/cozy/dev/catalog/screen?spec=memo-list&amp;state=default&amp;inspect=1\"")
			.as("상태 비교 버튼이 창을 연다")
			.contains("data-bs-toggle=\"modal\" data-bs-target=\"#state-compare\" aria-haspopup=\"dialog\" data-cozy-compare-button")
			.contains("id=\"state-compare\"")
			.contains("data-cozy-compare-open=\"false\" data-cozy-compare-limit=\"4\"");
		String modal = html.substring(html.indexOf("id=\"state-compare\""));
		for (String state : List.of("default", "empty", "noResults", "paged")) {
			assertThat(modal).as(state)
				.contains("value=\"" + state + "\" data-cozy-compare-pick checked=\"checked\"")
				.as("미리보기는 창을 열 때 불러온다")
				.contains("data-src=\"/cozy/dev/catalog/screen?spec=memo-list&amp;state=" + state + "\"")
				.contains("href=\"/cozy/dev/workbench?spec=memo-list&amp;state=" + state + "&amp;w=768\"");
		}
		assertThat(modal).doesNotContain(" src=\"/cozy/dev/catalog/screen")
			.contains("cozy-workbench-thumb-768")
			.contains("지금 보는 상태")
			.contains("id=\"compare-width-768\" value=\"768\" data-cozy-compare-width checked=\"checked\"");
		assertThat(page("?spec=memo-list&compare=true")).contains("data-cozy-compare-open=\"true\"");
		assertThat(page("?spec=memo-detail")).as("상태가 둘이면 둘 다").contains("value=\"empty\" data-cozy-compare-pick checked=\"checked\"");
		this.mvc.perform(get(URL + "?spec=memo-list&view=states")).andExpect(status().isBadRequest());
		valid(html, "상태 비교");
	}
}
