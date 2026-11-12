package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.salvia95.cozy.cli.internal.Recipes;
import io.salvia95.cozy.ui.catalog.ScreenSpec;

/**
 * 워크벤치의 용어: 사람이 읽는 이름이 앞에 오고 식별자는 뒤에 따로 남는다. 용어집은 docs/conventions/workbench.md에 있다.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-terms-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchTermsTest {

	static final String URL = "/cozy/dev/workbench";

	static final Pattern HANGUL = Pattern.compile("[가-힣]");

	@Autowired
	MockMvc mvc;

	String page(String path) throws Exception {
		return this.mvc.perform(get(URL + path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 태그와 주석을 뺀, 화면에 보이는 글 */
	static String visible(String html) {
		return html.replaceAll("(?s)<!--.*?-->", " ").replaceAll("(?s)<script.*?</script>", " ").replaceAll("<[^>]+>", " ");
	}

	static List<String> texts(String html, String regex) {
		List<String> found = new ArrayList<>();
		Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(html);
		while (m.find()) {
			found.add(m.group(1).strip());
		}
		return found;
	}

	@Test
	void theMenuUsesTheGlossaryAndCarriesTheWordmark() throws Exception {
		String html = page("?spec=memo-list");
		assertThat(texts(html, "<a class=\"nav-link[^\"]*\"[^>]*>\\s*([^<]+)<")).as("메뉴와 보기 탭")
			.containsExactly("개요", "화면", "모듈", "코드 생성", "검사", "컴포넌트", "미리보기", "템플릿", "화면 명세", "속성", "샘플 데이터");
		assertThat(html).contains("class=\"cozy-workbench-wordmark\"").contains("aria-label=\"Cozy Workbench\"");
	}

	@Test
	void retiredWordsAreGone() throws Exception {
		for (String path : List.of("", "?spec=memo-list&view=fixture", "?spec=memo-list&view=states", "/map", "/check", "/commands",
				"/commands?recipe=listener", "/search?q=memo")) {
			assertThat(visible(page(path))).as(path)
				.doesNotContain("지도")
				.doesNotContain("빈칸")
				.doesNotContain("손으로 할 일")
				.doesNotContain("레시피")
				.doesNotContain("fragment 선언")
				.doesNotContain("계획 보기")
				.doesNotContain("요소 고르기")
				.doesNotContain("고치는 명령");
		}
	}

	@Test
	void statesAreNamedForPeopleAndKeepTheirIdentifier() throws Exception {
		String html = page("?spec=memo-list");
		assertThat(html).contains("title=\"noResults\"").contains(">검색 결과 없음<").contains(">여러 페이지<").doesNotContain(">noResults<");
		assertThat(page("?spec=memo-form")).contains("title=\"invalid\"").contains(">입력 오류<");
	}

	@Test
	void everyStructureNodeHasAReadableNameBeforeItsIdentifier() throws Exception {
		for (ScreenSpec spec : ScreenSpec.load(getClass().getClassLoader())) {
			String html = page("?spec=" + spec.screen());
			List<String> labels = texts(html, "data-cozy-workbench-node(?:=\"\")?>([^<]+)<");
			assertThat(labels).as(spec.screen()).isNotEmpty().allMatch((label) -> HANGUL.matcher(label).find());
		}
		String list = page("?spec=memo-list");
		assertThat(list).contains(">목록 탐색<")
			.contains(">list-with-filter<")
			.contains(">필터 영역<")
			.contains(">페이지네이션<")
			.contains("data-cozy-name=\"목록 탐색\"")
			.contains("data-cozy-code=\"list-with-filter (패턴)\"");
	}

	@Test
	void recipesAreListedByTitleInGroups() throws Exception {
		String html = page("/commands");
		for (Recipes.Recipe recipe : Recipes.ALL) {
			assertThat(html).as(recipe.name()).contains(">" + recipe.title() + "<").contains(">cozy " + String.join(" ", recipe.command()) + "<");
		}
		assertThat(texts(html, "<div class=\"list-group-header\">([^<]+)<")).containsExactlyElementsOf(Recipes.GROUPS);
		assertThat(page("/commands?recipe=crud")).contains(">변경 파일 확인<");
	}
}
