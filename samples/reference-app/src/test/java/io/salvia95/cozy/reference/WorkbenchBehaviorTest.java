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
 * 워크벤치의 화면 안 동작: 구조 트리의 줄에 붙은 동작의 수, 속성 표의 "화면 안의 동작" 구획(컴포넌트와 그 명세, 이벤트, 묶인 값, htmx 요청),
 * 속성 표에서 붙이고 떼기({@code cozy attach}, 변경 파일 확인 → 적용), 모듈 화면의 동작 목록과 화면 이벤트.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-behavior-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchBehaviorTest {

	static final String FIELDS = "src/main/resources/templates/memo/internal/fields.html";

	/** memo/api/form.html에서 memoFields를 넣는 줄 */
	static final int FIELDS_ROW = 10;

	static Path project;

	@DynamicPropertySource
	static void project(DynamicPropertyRegistry registry) throws IOException {
		project = WorkbenchCommandTest.copyOfThisProject("workbench-behavior-");
		registry.add("cozy.dev.workbench.project-dir", project::toString);
	}

	@Autowired
	MockMvc mvc;

	String fields() throws IOException {
		return Files.readString(project.resolve(FIELDS), StandardCharsets.UTF_8);
	}

	String page(String url) throws Exception {
		return this.mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	MockHttpServletRequestBuilder attach(String step, String option, String key, String value, boolean detach) {
		return post("/cozy/dev/workbench/attach").with(csrf())
			.param("spec", "memo-form")
			.param("state", "default")
			.param("w", "full")
			.param("node", String.valueOf(FIELDS_ROW))
			.param("component", "memoBodyCount")
			.param("template", "memo/internal/fields")
			.param("line", "9")
			.param("option", option)
			.param("key", key)
			.param("value", value)
			.param("detach", String.valueOf(detach))
			.param("step", step);
	}

	String after(MockHttpServletRequestBuilder request) throws Exception {
		MvcResult posted = this.mvc.perform(request)
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/cozy/dev/workbench?spec=memo-form&state=default&w=full&node=" + FIELDS_ROW))
			.andReturn();
		return this.mvc.perform(get(posted.getResponse().getRedirectedUrl()).flashAttrs(posted.getFlashMap()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	@Test
	void theTreeRowShowsHowManyBoundElementsItHasAndThePropertiesListThem() throws Exception {
		String html = page("/cozy/dev/workbench?spec=memo-form");
		// 넣은 fragment(memoFields)의 두 요소는 화면에서 넣은 줄의 마디에 붙는다
		assertThat(html).contains("title=\"화면 안의 동작이 붙은 요소 2개\"")
			.contains("<span class=\"cozy-workbench-behaviors-count\">동작 2</span>")
			.as("속성 표의 구획과 요소")
			.contains("화면 안의 동작")
			.contains("<code>templates/memo/internal/fields.html:7</code>")
			.contains("<code>&lt;div class=&quot;form-hint mt-n2 mb-3&quot;&gt;</code>")
			.as("이벤트, 묶인 값, 넘기는 값")
			.contains("<code>input → count</code>")
			.contains("<code>label</code>")
			.contains("<code>data-limit = 5000</code>")
			.contains("<code>th:data-length = ${bodyLength}</code>")
			.as("htmx 요청도 읽기 전용으로 싣는다")
			.contains("<code>th:hx-post = @{/memo}</code>")
			.as("컴포넌트를 누르면 명세가 보인다")
			.contains("본문을 쓰는 동안 글자 수와 한도를 보인다")
			.contains("<code>if(over, format(&#39;{text} · 한도를 넘었습니다&#39;), text)</code>")
			.as("붙이기 폼")
			.contains("action=\"/cozy/dev/workbench/attach\"");
		// 구획은 요소가 있는 줄(memoFields, form)과 화면의 속성에만 있다
		assertThat(html.split("<th colspan=\"2\" scope=\"colgroup\">화면 안의 동작 ", -1)).hasSize(4);
		assertThat(html.split("cozy-workbench-behaviors-count", -1)).hasSize(3);
		assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
	}

	@Test
	void frameworkComponentsShowTheirSpecFromCozyUi() throws Exception {
		String html = page("/cozy/dev/workbench?spec=memo-detail");
		assertThat(html).contains("<code>click → copy</code>")
			.contains("<code>cozyCopy</code>")
			.as("cozy-ui의 cozy/behaviors.yml")
			.contains("글자를 복사하고 &quot;복사했습니다&quot; 토스트를 띄운다")
			.as("안내는 템플릿에 적는 모양이다")
			.contains("이벤트가 부를 이름: copy · 묶을 이름: copy");
	}

	@Test
	void attachingShowsThePlanThenAppliesAndDetachingRestoresTheTemplate() throws Exception {
		String before = fields();
		String plan = after(attach("plan", "show", "", "over", false));
		assertThat(plan).contains("동작 변경 파일")
			.contains("적용 전입니다")
			.contains("cozy attach memoBodyCount --at memo/internal/fields:9 --show over")
			.contains("name=\"step\" value=\"apply\"");
		assertThat(fields()).as("확인만 하면 파일은 그대로").isEqualTo(before);
		assertThat(new CozyHtmlValidator().validate(plan).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();

		String applied = after(attach("apply", "show", "", "over", false));
		assertThat(applied).contains("동작 적용 결과").contains("적용했습니다").as("속성은 소스를 읽으므로 바로 바뀐다").contains("<code>over</code>");
		assertThat(fields()).isEqualTo(before.replace("x-text=\"label\" x-cloak>", "x-text=\"label\" x-cloak x-show=\"over\">"));

		after(attach("apply", "show", "", "over", true));
		assertThat(fields()).as("떼면 처음과 같다").isEqualTo(before);
	}

	@Test
	void namesTheComponentDoesNotHaveAreRefusedAndWriteNothing() throws Exception {
		String before = fields();
		String refused = after(attach("apply", "text", "", "nothing", false));
		assertThat(refused).contains("memoBodyCount에 없는 상태나 계산입니다: nothing").contains("파일은 변경되지 않았습니다");
		assertThat(fields()).isEqualTo(before);
	}

	@Test
	void writesNeedTheCsrfTokenTheSameOriginAKnownStepAndAKnownOption() throws Exception {
		this.mvc.perform(post("/cozy/dev/workbench/attach").param("spec", "memo-form").param("state", "default")
			.param("component", "memoBodyCount").param("template", "memo/internal/fields").param("line", "9").param("option", "show")
			.param("value", "over").param("step", "apply")).andExpect(status().isForbidden());
		this.mvc.perform(attach("apply", "show", "", "over", false).header("Origin", "http://evil.example")).andExpect(status().isForbidden());
		this.mvc.perform(attach("write", "show", "", "over", false)).andExpect(status().isBadRequest());
		this.mvc.perform(attach("plan", "effect", "", "over", false)).andExpect(status().isBadRequest());
		this.mvc.perform(attach("plan", "show", "", "over", false).param("spec", "none")).andExpect(status().isNotFound());
		assertThat(fields()).doesNotContain("x-show");
	}

	@Test
	void theModulePageListsBehaviorsAndWhereTheyAreAttached() throws Exception {
		String html = page("/cozy/dev/workbench/map?module=memo");
		assertThat(html).contains("화면 안의 동작")
			.contains("<code class=\"d-block fw-medium\">memoBodyCount</code>")
			.contains("본문을 쓰는 동안 글자 수와 한도를 보인다")
			.as("붙인 화면: 만들기와 수정")
			.contains("붙인 화면 2개")
			.contains("화면 이벤트")
			.contains("화면 이벤트가 없습니다");
		assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
	}

	@Test
	void theScreenOpensTheBehaviorSpecTheMadeScriptAndTheIncludedTemplates() throws Exception {
		String preview = page("/cozy/dev/workbench?spec=memo-form");
		assertThat(preview).as("화면이 앱 컴포넌트를 쓰면 소스 탭이 둘 더 있다")
			.contains(">동작 명세</a>")
			.contains(">만든 JS</a>")
			.as("요소의 위치는 넣은 템플릿의 그 줄로 간다")
			.contains("href=\"/cozy/dev/workbench?spec=memo-form&amp;state=default&amp;w=full&amp;view=template&amp;template=memo/internal/fields#L7\"")
			.as("구조 트리의 표시가 무엇을 세는지 알린다")
			.contains("title=\"화면 안의 동작이 붙은 요소 2개\"");

		String spec = page("/cozy/dev/workbench?spec=memo-form&view=behavior");
		assertThat(spec).contains("src/main/resources/behaviors/memo.yml")
			.contains("동작 명세가 원본입니다")
			.contains("<pre class=\"cozy-workbench-source-text\">  bodyCount:</pre>");
		String script = page("/cozy/dev/workbench?spec=memo-form&view=script");
		assertThat(script).contains("src/main/resources/static/memo/memo.js")
			.contains("cozy가 동작 명세에서 만든 파일입니다")
			.contains("window.Alpine.data(&#39;memoBodyCount&#39;");
		String fields = page("/cozy/dev/workbench?spec=memo-form&view=template&template=memo/internal/fields");
		assertThat(fields).contains("<code class=\"small\">src/main/resources/templates/memo/internal/fields.html</code>")
			.as("화면이 넣는 앱 템플릿을 고른다")
			.contains("aria-label=\"볼 템플릿\"")
			.contains(">memo/api/form</a>");
		for (String html : java.util.List.of(spec, script, fields)) {
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		}
	}

	@Test
	void sourcesOutsideTheScreenAreRefused() throws Exception {
		assertThat(page("/cozy/dev/workbench?spec=memo-detail")).as("앱 컴포넌트를 쓰지 않는 화면").doesNotContain(">동작 명세</a>");
		this.mvc.perform(get("/cozy/dev/workbench?spec=memo-detail&view=behavior")).andExpect(status().isNotFound());
		this.mvc.perform(get("/cozy/dev/workbench?spec=memo-form&view=template&template=dashboard/api/home")).andExpect(status().isNotFound());
		this.mvc.perform(get("/cozy/dev/workbench?spec=memo-form&view=template&template=../../../pom")).andExpect(status().isNotFound());
		this.mvc.perform(get("/cozy/dev/workbench/map?module=memo&source=nothing")).andExpect(status().isBadRequest());
		this.mvc.perform(get("/cozy/dev/workbench/map?module=dashboard&source=behavior")).andExpect(status().isNotFound());
	}

	@Test
	void theModulePageOpensTheSpecAtTheComponentAndTheScript() throws Exception {
		String module = page("/cozy/dev/workbench/map?module=memo");
		assertThat(module).contains("id=\"behavior-memoBodyCount\"")
			.contains("href=\"/cozy/dev/workbench/map?module=memo&amp;source=behavior#L3\"")
			.contains("href=\"/cozy/dev/workbench/map?module=memo&amp;source=script\"")
			.doesNotContain("aria-label=\"동작 소스\"");
		String spec = page("/cozy/dev/workbench/map?module=memo&source=behavior");
		assertThat(spec).contains("aria-label=\"동작 소스\"").contains("src/main/resources/behaviors/memo.yml").contains("<tr id=\"L3\">");
		String script = page("/cozy/dev/workbench/map?module=memo&source=script");
		assertThat(script).contains("src/main/resources/static/memo/memo.js").contains("손으로 고치지 않습니다(C12)");
		assertThat(new CozyHtmlValidator().validate(spec).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
	}

	@Test
	void searchFindsTheComponentByNameAndByPurpose() throws Exception {
		String byName = page("/cozy/dev/workbench/search?q=memoBodyCount");
		assertThat(byName).contains("화면 안의 동작").contains("href=\"/cozy/dev/workbench/map?module=memo#behavior-memoBodyCount\"");
		String byPurpose = this.mvc.perform(get("/cozy/dev/workbench/search").param("q", "글자 수"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(byPurpose)
			.contains("memoBodyCount")
			.contains("본문을 쓰는 동안 글자 수와 한도를 보인다");
	}
}
