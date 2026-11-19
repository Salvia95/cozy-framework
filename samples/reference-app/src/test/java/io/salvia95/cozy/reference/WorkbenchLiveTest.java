package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 워크벤치가 켜진 dev 실행은 앱의 템플릿과 {@code static/}을 소스 폴더에서 먼저 읽는다. 워크벤치로 적용한 것이 다시 빌드하지 않고 새로고침만으로
 * 미리보기와 만든 JS에 보인다. 계획은 파일마다 바뀐 줄과 "바로 보임/빌드 필요"를 보인다. {@code cozy.dev.workbench.live-resources=false}면 빌드
 * 결과만 읽는다.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-live-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchLiveTest extends WorkbenchLiveBase {

	static Path project;

	@DynamicPropertySource
	static void project(DynamicPropertyRegistry registry) throws IOException {
		project = WorkbenchCommandTest.copyOfThisProject("workbench-live-");
		registry.add("cozy.dev.workbench.project-dir", project::toString);
	}

	@Override
	Path project() {
		return project;
	}

	@Test
	void thePlanShowsTheChangedLinesAndThatTheyShowOnRefresh() throws Exception {
		String plan = after(showOver("plan"));

		assertThat(plan).contains("바뀐 줄 보기 · +1 −1", "바로 보임")
			.as("지운 줄과 더한 줄, 앞뒤의 같은 줄")
			.contains("<pre class=\"cozy-workbench-diff-text\">    &lt;div class=&quot;form-hint mt-n2 mb-3&quot; x-text=&quot;label&quot; x-cloak&gt;&lt;/div&gt;</pre>",
					"<pre class=\"cozy-workbench-diff-text\">    &lt;div class=&quot;form-hint mt-n2 mb-3&quot; x-text=&quot;label&quot; x-cloak x-show=&quot;over&quot;&gt;&lt;/div&gt;</pre>",
					"<pre class=\"cozy-workbench-diff-text\">  &lt;div x-data=&quot;memoBodyCount&quot; th:data-length=&quot;${bodyLength}&quot; data-limit=&quot;5000&quot; x-on:input=&quot;count&quot;&gt;</pre>",
					"더한 줄: ", "지운 줄: ")
			.contains("바뀐 파일은 모두 새로고침하면 미리보기와 화면에 보입니다.");
		assertThat(new CozyHtmlValidator().validate(plan).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();

		String sql = this.mvc.perform(post("/cozy/dev/workbench/commands").with(csrf())
			.param("recipe", "migration")
			.param("step", "plan")
			.param("arg.모듈", "memo")
			.param("arg.설명", "add_due_date"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(sql).as("SQL은 다시 빌드하고 실행해야 보인다").contains("빌드 필요", "앱을 다시 빌드하고 실행한 뒤 보입니다");
	}

	@Test
	void anAppliedTemplateAndScriptShowOnRefreshWithoutABuild() throws Exception {
		assertThat(body(PREVIEW)).doesNotContain("x-show=\"over\"");
		String script = body("/cozy/modules.js");
		assertThat(script).doesNotContain("under");

		after(showOver("apply"));
		assertThat(body(PREVIEW)).as("미리보기가 소스의 템플릿을 그린다").contains("x-show=\"over\"");
		assertThat(body(PREVIEW + "&inspect=1")).as("소스 위치도 소스의 줄이다").contains("data-cozy-src=\"memo/internal/fields:9\"");

		String hash = WorkbenchBehaviorEditTest.hashOf(body("/cozy/dev/workbench/commands?recipe=behavior&edit=memoBodyCount"));
		String applied = this.mvc.perform(post("/cozy/dev/workbench/commands").with(csrf())
			.param("recipe", "behavior")
			.param("edit", "memoBodyCount")
			.param("specHash", hash)
			.param("step", "apply")
			.param("arg.모듈", "memo")
			.param("arg.동작", "body-count")
			.param("opt.purpose", "본문을 쓰는 동안 글자 수와 한도를 보인다")
			.param("opt.state.0.name", "length")
			.param("opt.state.0.declaration", "number@data-length")
			.param("opt.state.1.name", "limit")
			.param("opt.state.1.declaration", "number@data-limit")
			.param("opt.computed.0.name", "over")
			.param("opt.computed.0.expression", "and(gt(limit, 0), gt(length, limit))")
			.param("opt.computed.1.name", "text")
			.param("opt.computed.1.expression", "format('{length} / {limit}자', 'ko-KR')")
			.param("opt.computed.2.name", "label")
			.param("opt.computed.2.expression", "if(over, format('{text} · 한도를 넘었습니다'), text)")
			.param("opt.computed.3.name", "under")
			.param("opt.computed.3.expression", "not(over)")
			.param("opt.action.0.name", "count")
			.param("opt.action.0.steps", "within('textarea', set(length, length(event.value)))"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(applied).contains("적용했습니다", "바뀐 파일은 모두 새로고침하면 미리보기와 화면에 보입니다.");
		assertThat(body("/cozy/modules.js")).as("만든 JS의 묶음이 소스의 static/을 읽는다").contains("under").isNotEqualTo(script);
		Path extra = project.resolve("src/main/resources/static/live-check.txt");
		Files.writeString(extra, "live from source", StandardCharsets.UTF_8);
		try {
			assertThat(body("/live-check.txt")).as("다른 정적 파일도 소스에서").isEqualTo("live from source");
		}
		finally {
			Files.delete(extra);
		}
	}
}
