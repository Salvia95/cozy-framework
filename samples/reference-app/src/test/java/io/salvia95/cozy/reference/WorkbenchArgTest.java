package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.salvia95.cozy.test.CozyHtmlValidator;

/**
 * 화면 보기의 속성 패널에서 fragment 호출의 문자열 인자를 고친다. 파일은 {@code cozy set arg}가 쓰고, 변경 파일을 확인한 뒤 적용한다.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-arg-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchArgTest {

	static final String LIST = "src/main/resources/templates/memo/api/list.html";

	/** list.html의 고정 배지가 있는 줄 */
	static final int BADGE = 47;

	static Path project;

	@DynamicPropertySource
	static void project(DynamicPropertyRegistry registry) throws IOException {
		project = WorkbenchCommandTest.copyOfThisProject("workbench-arg-");
		registry.add("cozy.dev.workbench.project-dir", project::toString);
	}

	@Autowired
	MockMvc mvc;

	String list() throws IOException {
		return Files.readString(project.resolve(LIST), StandardCharsets.UTF_8);
	}

	MockHttpServletRequestBuilder arg(String step, int line, String fragment, int index, String from, String value) {
		return post("/cozy/dev/workbench/arg").with(csrf())
			.param("spec", "memo-list")
			.param("state", "default")
			.param("w", "full")
			.param("line", String.valueOf(line))
			.param("fragment", fragment)
			.param("index", String.valueOf(index))
			.param("from", from)
			.param("value", value)
			.param("name", "배지 · 색상")
			.param("step", step);
	}

	/** 고친 뒤 돌아온 화면 (플래시를 실어 다시 읽는다) */
	String after(MockHttpServletRequestBuilder request) throws Exception {
		MvcResult posted = this.mvc.perform(request)
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/cozy/dev/workbench?spec=memo-list&state=default&w=full&node=" + BADGE))
			.andReturn();
		return this.mvc.perform(get(posted.getResponse().getRedirectedUrl()).flashAttrs(posted.getFlashMap()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	@Test
	void stringArgumentsHaveAnInputAndExpressionsStayReadOnly() throws Exception {
		String html = this.mvc.perform(get("/cozy/dev/workbench").param("spec", "memo-list"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(html).contains("action=\"/cozy/dev/workbench/arg\"")
			.contains("name=\"line\" value=\"" + BADGE + "\"")
			.contains("name=\"fragment\" value=\"badge\"")
			.as("값을 정한 파라미터는 선택 칸")
			.contains("<option value=\"neutral\" selected=\"selected\">중립 (neutral)</option>")
			.contains("<option value=\"critical\">위험 (critical)</option>")
			.as("정하지 않은 문자열은 글자 칸")
			.contains("name=\"value\" value=\"고정\"")
			.as("선택자는 읽기 전용")
			.contains("<code>~{::#filter}</code>");
		assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
	}

	@Test
	void thePlanShowsTheChangeAndApplyingChangesOnlyThatArgument() throws Exception {
		String before = list();
		String plan = after(arg("plan", BADGE, "badge", 2, "neutral", "info"));
		assertThat(plan).contains("매개변수 변경 파일")
			.contains("배지 · 색상")
			.contains("적용 전입니다")
			.contains("badge(…) 2번째 인자 tone: &#39;neutral&#39; → &#39;info&#39;")
			.contains("cozy set arg memo/api/list --line 47 --fragment badge --index 2 --from neutral --value info")
			.contains("name=\"step\" value=\"apply\"");
		assertThat(list()).as("확인만 하면 파일은 그대로").isEqualTo(before);
		assertThat(new CozyHtmlValidator().validate(plan).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();

		String applied = after(arg("apply", BADGE, "badge", 2, "neutral", "info"));
		assertThat(applied).contains("매개변수 적용 결과").contains("적용했습니다").contains("앱을 다시 빌드하고 실행한 뒤");
		assertThat(list()).as("그 인자만 바뀐다").isEqualTo(before.replace("badge('고정', 'neutral')", "badge('고정', 'info')"));
		assertThat(applied).as("구조와 속성은 소스를 읽으므로 바로 바뀐다").contains("<option value=\"info\" selected=\"selected\">정보 (info)</option>");

		after(arg("apply", BADGE, "badge", 2, "info", "neutral"));
		assertThat(list()).as("되돌리면 처음과 같다").isEqualTo(before);
	}

	@Test
	void staleOrDisallowedValuesAreRefusedAndWriteNothing() throws Exception {
		String before = list();
		String stale = after(arg("apply", BADGE, "badge", 2, "info", "success"));
		assertThat(stale).contains("지금 &#39;neutral&#39;입니다").contains("파일은 변경되지 않았습니다");
		String outside = after(arg("apply", BADGE, "badge", 2, "neutral", "purple"));
		assertThat(outside).contains("component/badge의 tone는 neutral, info, success, warning, critical 가운데 하나입니다");
		assertThat(list()).isEqualTo(before);
	}

	@Test
	void writesNeedTheCsrfTokenTheSameOriginAndAKnownStep() throws Exception {
		this.mvc.perform(post("/cozy/dev/workbench/arg").param("spec", "memo-list").param("state", "default").param("line", "47")
			.param("fragment", "badge").param("index", "2").param("from", "neutral").param("value", "info").param("step", "apply"))
			.andExpect(status().isForbidden());
		this.mvc.perform(arg("apply", BADGE, "badge", 2, "neutral", "info").header("Origin", "http://evil.example"))
			.andExpect(status().isForbidden());
		this.mvc.perform(arg("write", BADGE, "badge", 2, "neutral", "info")).andExpect(status().isBadRequest());
		this.mvc.perform(arg("plan", BADGE, "badge", 2, "neutral", "info").param("spec", "none")).andExpect(status().isNotFound());
		assertThat(list()).contains("badge('고정', 'neutral')");
	}
}
