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
		String html = page("");
		assertThat(html).as("개요 메뉴가 선택된다")
			.contains("aria-current=\"page\" href=\"/cozy/dev/workbench\">개요</a>")
			.contains("href=\"/cozy/dev/workbench/screens\">화면</a>")
			.as("숫자 카드: 화면, 모듈, 규칙 위반, 미구현 항목")
			.contains(">5개<")
			.contains(">3개<")
			.contains(">규칙 위반<")
			.contains(">미구현 항목<")
			.contains("io.salvia95.cozy.reference");
		assertThat(html).as("화면 목록은 모듈별로, 화면 보기와 전체 상태 보기로 이어진다")
			.contains("href=\"/cozy/dev/workbench?spec=memo-list\">메모 목록</a>")
			.contains("기본, 데이터 없음, 검색 결과 없음, 여러 페이지")
			.contains("href=\"/cozy/dev/workbench?spec=memo-list&amp;view=states\" aria-label=\"메모 목록 전체 상태 보기\"");
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
		assertThat(crud).contains(">생성 항목<").contains("href=\"/cozy/dev/workbench/commands?recipe=crud\"").contains(">CRUD 화면<");

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
	void theStatesViewListsEveryStateAsAThumbnail() throws Exception {
		String html = page("?spec=memo-list&view=states&w=768").replaceAll("\\s+", " ");
		for (String state : List.of("default", "empty", "noResults", "paged")) {
			assertThat(html).as(state)
				.contains("src=\"/cozy/dev/catalog/screen?spec=memo-list&amp;state=" + state + "\"")
				.contains("href=\"/cozy/dev/workbench?spec=memo-list&amp;state=" + state + "&amp;w=768\"");
		}
		assertThat(html).contains("cozy-workbench-thumb-768")
			.contains("data-cozy-workbench-thumb")
			.as("선택한 상태가 표시된다")
			.contains("선택한 상태")
			.as("다시 누르면 미리보기로")
			.contains("<a class=\"btn btn-sm active\" aria-current=\"true\" href=\"/cozy/dev/workbench?spec=memo-list&amp;state=default&amp;w=768\">")
			.as("요소 선택은 미리보기에서만")
			.doesNotContain("data-cozy-workbench-pick")
			.doesNotContain("data-cozy-workbench-frame");
		assertThat(html).as("너비와 화면을 바꿔도 전체 상태 보기에 남는다")
			.contains("href=\"/cozy/dev/workbench?spec=memo-list&amp;state=default&amp;w=390&amp;view=states\"")
			.contains("href=\"/cozy/dev/workbench?spec=memo-detail&amp;w=768&amp;view=states\"");
		assertThat(page("?spec=memo-list")).contains("href=\"/cozy/dev/workbench?spec=memo-list&amp;state=default&amp;w=full&amp;view=states\"")
			.contains(">전체 상태 보기</a>");
		valid(html, "전체 상태 보기");
	}
}
