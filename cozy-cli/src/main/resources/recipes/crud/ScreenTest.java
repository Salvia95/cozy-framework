package {{base}};

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

{{testImports}}import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import {{spiPkg}}.{{E}}Facade;
import {{spiPkg}}.{{E}}Finder;

/** {{L}} 화면 흐름: 목록 → 만들기(검증, PRG) → 상세 → 수정 → 삭제 (cozy add crud, docs/conventions/design.md §3). */
@SpringBootTest(properties = "cozy.data-dir=target/test-data/{{module}}-screen-${random.uuid}")
@AutoConfigureMockMvc
@DirtiesContext
class {{E}}ScreenTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	{{E}}Facade facade;

	@Autowired
	{{E}}Finder finder;

	@Test
	void listShowsTheEmptyStateThenRows() throws Exception {
		// 같은 컨텍스트의 다른 테스트가 만든 것을 지운다(테스트 순서와 무관하게)
		this.finder.list().forEach((item) -> this.facade.delete(item.id()));
		assertThat(body(this.mvc.perform(get("/{{module}}")).andExpect(status().isOk()).andReturn())).contains("{{Ls}} 없습니다");
		this.facade.create({{createSample}});
		assertThat(body(this.mvc.perform(get("/{{module}}")).andExpect(status().isOk()).andReturn())).contains({{nameSample}});
		String nothing = body(this.mvc.perform(get("/{{module}}").param("q", "없는 단어").header("HX-Request", "true")).andReturn());
		assertThat(nothing).doesNotContain("<html").contains(">조건 지우기<");
	}

	@Test
	void createWithAValidationErrorThenRedirectToTheDetail() throws Exception {
		String invalid = body(this.mvc.perform(post("/{{module}}").with(csrf()).param("{{name}}", ""))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(invalid).contains("is-invalid").contains("{{nameRequired}}");

		MockHttpSession session = new MockHttpSession();
		MvcResult created = this.mvc.perform(post("/{{module}}").with(csrf()).session(session){{postParams}})
			.andExpect(status().isFound())
			.andExpect(redirectedUrlPattern("/{{module}}/*"))
			.andReturn();
		String detail = body(this.mvc.perform(get(created.getResponse().getRedirectedUrl()).session(session)).andReturn());
		assertThat(detail).contains("{{Lo}} 만들었습니다").contains({{nameSample}});
	}

	@Test
	void editWithoutChangesThenSaveThenDelete() throws Exception {
		long id = this.facade.create({{createSample}});
		this.mvc.perform(get("/{{module}}/" + id + "/edit")).andExpect(status().isOk());

		MockHttpSession session = new MockHttpSession();
		this.mvc.perform(post("/{{module}}/" + id).with(csrf()).session(session){{postParams}})
			.andExpect(redirectedUrl("/{{module}}/" + id));
		assertThat(body(this.mvc.perform(get("/{{module}}/" + id).session(session)).andReturn())).contains("바뀐 내용이 없습니다");

		this.mvc.perform(post("/{{module}}/" + id).with(csrf()).session(session){{postRenamed}})
			.andExpect(redirectedUrl("/{{module}}/" + id));
		assertThat(body(this.mvc.perform(get("/{{module}}/" + id).session(session)).andReturn())).contains("{{Lo}} 수정했습니다");
		assertThat(this.finder.find(id).orElseThrow().{{name}}()).isEqualTo("바꾼 {{L}}");

		this.mvc.perform(post("/{{module}}/" + id + "/delete").with(csrf()).session(session)).andExpect(redirectedUrl("/{{module}}"));
		assertThat(body(this.mvc.perform(get("/{{module}}").session(session)).andReturn())).contains("{{Lo}} 삭제했습니다");
		this.mvc.perform(get("/{{module}}/" + id)).andExpect(status().isNotFound());
	}

	private static String body(MvcResult result) throws Exception {
		result.getResponse().setCharacterEncoding("UTF-8");
		return result.getResponse().getContentAsString();
	}
}
