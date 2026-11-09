package io.salvia95.cozy.reference.memo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.salvia95.cozy.reference.memo.spi.MemoFacade;
import io.salvia95.cozy.reference.memo.spi.MemoFinder;

/**
 * memo 화면이 패턴만으로 구성되고, 외부 자원 없이 그대로 그려진다.
 */
@SpringBootTest(properties = "cozy.data-dir=target/test-data/memo-screen-${random.uuid}")
@AutoConfigureMockMvc
@DirtiesContext
class MemoScreenTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemoFacade memos;

	@Autowired
	MemoFinder finder;

	@Test
	void createWithValidationErrorThenSuccessWithPrg() throws Exception {
		String invalid = body(this.mvc.perform(post("/memo").with(csrf()).param("title", " ")).andExpect(status().isOk()).andReturn());
		assertThat(invalid).contains("<html").contains("is-invalid").contains("제목을 입력하세요");

		String invalidHtmx = body(this.mvc.perform(post("/memo").with(csrf()).header("HX-Request", "true").param("title", ""))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(invalidHtmx).doesNotContain("<html").startsWith("<form").contains("제목을 입력하세요");

		MockHttpSession session = new MockHttpSession();
		MvcResult created = this.mvc
			.perform(post("/memo").with(csrf()).session(session).param("title", "화면 메모").param("body", "첫 줄\n둘째 줄")
				.param("pinned", "true"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrlPattern("/memo/*"))
			.andReturn();
		String detail = body(this.mvc.perform(get(created.getResponse().getRedirectedUrl()).session(session)).andReturn());
		assertThat(detail).contains("메모를 만들었습니다").contains("화면 메모").contains(">첫 줄<").contains(">둘째 줄<")
			.contains(">고정<");
	}

	@Test
	void htmxCreateRespondsWithHxRedirect() throws Exception {
		MvcResult result = this.mvc
			.perform(post("/memo").with(csrf()).header("HX-Request", "true").param("title", "htmx 메모"))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(result.getResponse().getHeader("HX-Redirect")).matches("/memo/\\d+");
		assertThat(result.getResponse().getContentAsString()).isEmpty();
	}

	@Test
	void filterAndPaginationUpdateOnlyTheResult() throws Exception {
		for (int i = 1; i <= 12; i++) {
			this.memos.create("페이지 메모 " + i, "본문 " + i, false);
		}
		this.memos.create("다른 100% 메모", "", false);

		String page2 = body(this.mvc.perform(get("/memo").param("q", "페이지 메모").param("page", "2").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(page2).doesNotContain("<html").contains("id=\"result\"").contains("전체 <span class=\"fw-medium\">12</span>건")
			.containsPattern("aria-current=\"page\"[^>]*>2</a>");
		assertThat(Pattern.compile("class=\"memo-row\"").matcher(page2).results().count()).isEqualTo(2);

		// LIKE 특수문자는 글자 그대로 찾는다
		String percent = body(this.mvc.perform(get("/memo").param("q", "100%").header("HX-Request", "true")).andReturn());
		assertThat(percent).contains("다른 100% 메모").doesNotContain("페이지 메모");

		// 조건에 맞는 것이 없으면 "조건 지우기", 아직 아무것도 없을 때(메모가 없습니다)와 구분한다
		String nothing = body(this.mvc.perform(get("/memo").param("q", "없는 단어").header("HX-Request", "true")).andReturn());
		assertThat(nothing).contains("조건에 맞는 결과가 없습니다").contains(">조건 지우기<").doesNotContain("메모가 없습니다");
	}

	@Test
	void detailIsReadOnlyWithDeleteThenEditInTheHeader() throws Exception {
		long id = this.memos.create("읽기 메모", "본문", false);
		String detail = body(this.mvc.perform(get("/memo/" + id)).andExpect(status().isOk()).andReturn());
		assertThat(detail).doesNotContain("name=\"title\"").contains("aria-current=\"page\">읽기 메모<")
			.contains(">메모 삭제<").contains("<strong>읽기 메모</strong>");
		// 머리 오른쪽: [삭제] [수정(주 버튼)]
		assertThat(detail.indexOf("cozy-btn-critical\"")).isPositive()
			.isLessThan(detail.indexOf("href=\"/memo/" + id + "/edit\""));
	}

	@Test
	void editOnItsOwnPageThenSaveOrNoChange() throws Exception {
		long id = this.memos.create("고칠 메모", "", false);
		String edit = body(this.mvc.perform(get("/memo/" + id + "/edit")).andExpect(status().isOk()).andReturn());
		assertThat(edit).contains("cozy-width-form").contains("value=\"고칠 메모\"").contains(">메모 수정<")
			.contains("href=\"/memo/" + id + "\"");

		this.mvc.perform(post("/memo/" + id).with(csrf()).param("title", "")).andExpect(status().isOk());
		// htmx 검증 오류: 수정 폼만 돌아온다
		String invalidHtmx = body(this.mvc.perform(post("/memo/" + id).with(csrf()).header("HX-Request", "true").param("title", ""))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(invalidHtmx).startsWith("<form").contains("제목을 입력하세요").doesNotContain("/delete");
		assertThat(invalidHtmx.split("<form", -1)).hasSize(2);

		MockHttpSession session = new MockHttpSession();
		this.mvc.perform(post("/memo/" + id).with(csrf()).session(session).param("title", "고칠 메모").param("body", ""))
			.andExpect(redirectedUrl("/memo/" + id));
		assertThat(body(this.mvc.perform(get("/memo/" + id).session(session)).andReturn())).contains("바뀐 내용이 없습니다");

		this.mvc.perform(post("/memo/" + id).with(csrf()).session(session).param("title", "고친 메모").param("body", "새 본문"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/memo/" + id));
		assertThat(body(this.mvc.perform(get("/memo/" + id).session(session)).andReturn())).contains("메모를 수정했습니다");
		assertThat(this.finder.find(id).orElseThrow().title()).isEqualTo("고친 메모");
	}

	@Test
	void deleteReturnsToTheListWithAFlash() throws Exception {
		long id = this.memos.create("지울 메모", "", false);
		this.mvc.perform(post("/memo/" + id + "/delete").with(csrf()).header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(header().string("HX-Redirect", "/memo"));
		assertThat(this.finder.find(id)).isEmpty();
		this.mvc.perform(get("/memo/" + id)).andExpect(status().isNotFound());
		this.mvc.perform(get("/memo/" + id + "/edit")).andExpect(status().isNotFound());

		long other = this.memos.create("남길 메모", "", false);
		MockHttpSession session = new MockHttpSession();
		this.mvc.perform(post("/memo/" + other + "/delete").with(csrf()).session(session)).andExpect(redirectedUrl("/memo"));
		assertThat(body(this.mvc.perform(get("/memo").session(session)).andReturn())).contains("메모를 삭제했습니다");
	}

	@Test
	void pagesUseOnlyLocalAssetsThatExist() throws Exception {
		long id = this.memos.create("자산 확인", "", false);
		for (String path : List.of("/memo", "/memo/new", "/memo/" + id, "/memo/" + id + "/edit")) {
			String html = body(this.mvc.perform(get(path)).andExpect(status().isOk()).andReturn());
			assertThat(html).doesNotContainPattern("(?:href|src|action)=\"(?:https?:)?//");
			Matcher assets = Pattern.compile("(?:href|src)=\"(/cozy/[^\"#]+)(?:#[^\"]*)?\"").matcher(html);
			java.util.Set<String> distinct = new java.util.TreeSet<>();
			while (assets.find()) {
				distinct.add(assets.group(1));
			}
			for (String asset : distinct) {
				this.mvc.perform(get(asset)).andExpect(status().isOk());
			}
			assertThat(distinct).as(path).hasSize(8).contains("/cozy/vendor/tabler-icons/3.50.0/tabler-sprite.svg");
		}
	}

	@Test
	void memoTemplatesAreBuiltFromCozyPatterns() throws Exception {
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/templates/memo/api"))) {
			for (Path template : files.filter(Files::isRegularFile).toList()) {
				String html = Files.readString(template);
				assertThat(html).as(template.toString())
					.contains("~{cozy/layout/base :: appShell(")
					.contains("~{cozy/pattern/")
					.doesNotContain("<link")
					.doesNotContain("<script")
					.doesNotContain("style=");
			}
		}
	}

	private static String body(MvcResult result) throws Exception {
		result.getResponse().setCharacterEncoding("UTF-8");
		return result.getResponse().getContentAsString();
	}
}
