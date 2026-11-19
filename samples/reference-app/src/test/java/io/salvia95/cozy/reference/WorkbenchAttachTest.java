package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

import io.salvia95.cozy.cli.CozyCli;
import io.salvia95.cozy.test.CozyHtmlValidator;
import io.salvia95.cozy.ui.catalog.FragmentManifest;

/**
 * 미리보기에서 고른 요소에 붙이기({@code ?at=템플릿:줄}): 컴포넌트가 없는 요소는 그 모듈의 컴포넌트와 프레임워크 컴포넌트에서 골라 자리로 만들고
 * ({@code data-*} 칸), 컴포넌트 안의 요소는 그 컴포넌트의 이름을 선택 칸에서 고른다. {@code cozy attach}가 거절하는 요소는 그 이유를 보인다.
 * memoBodyCount를 지운 앱에서 워크벤치만으로 다시 선언하고 붙이면 처음과 같은 파일이 된다.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-attach-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchAttachTest {

	static final String SCREEN = "/cozy/dev/workbench?spec=memo-form";

	static final String BACK = "/cozy/dev/workbench?spec=memo-form&state=default&w=full";

	static final String SPEC = "src/main/resources/behaviors/memo.yml";

	static final String SCRIPT = "src/main/resources/static/memo/memo.js";

	static final String FIELDS = "src/main/resources/templates/memo/internal/fields.html";

	static Path project;

	@DynamicPropertySource
	static void project(DynamicPropertyRegistry registry) throws IOException {
		project = WorkbenchCommandTest.copyOfThisProject("workbench-attach-");
		registry.add("cozy.dev.workbench.project-dir", project::toString);
	}

	@Autowired
	MockMvc mvc;

	Map<String, String> original;

	@BeforeEach
	void remember() throws IOException {
		this.original = WorkbenchCommandTest.sources(project);
	}

	/** 테스트마다 같은 앱에서 시작한다 */
	@AfterEach
	void restore() throws IOException {
		for (Map.Entry<String, String> file : this.original.entrySet()) {
			Path path = project.resolve(file.getKey());
			Files.createDirectories(path.getParent());
			Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
		}
	}

	String read(String file) throws IOException {
		return Files.readString(project.resolve(file), StandardCharsets.UTF_8);
	}

	String page(String url) throws Exception {
		return this.mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	static void valid(String html) {
		assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
	}

	MockHttpServletRequestBuilder attach(int line, String step) {
		return post("/cozy/dev/workbench/attach").with(csrf())
			.param("spec", "memo-form")
			.param("state", "default")
			.param("w", "full")
			.param("component", "memoBodyCount")
			.param("template", "memo/internal/fields")
			.param("line", String.valueOf(line))
			.param("fromPlace", "true")
			.param("step", step);
	}

	/** 붙이기 카드 (속성 표의 붙이기 폼은 빼고) */
	static String card(String html) {
		int start = html.indexOf("id=\"attach-place-title\"");
		assertThat(start).as("붙이기 카드가 있다").isPositive();
		return html.substring(start, html.indexOf("x-ref=\"props\"", start));
	}

	/** 붙이기를 보내고 돌아간 화면 */
	String after(MockHttpServletRequestBuilder request) throws Exception {
		return after(request, BACK);
	}

	String after(MockHttpServletRequestBuilder request, String back) throws Exception {
		MvcResult posted = this.mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back)).andReturn();
		return this.mvc.perform(get(posted.getResponse().getRedirectedUrl()).flashAttrs(posted.getFlashMap()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	@Test
	void anElementOutsideEveryComponentChoosesAComponentOfItsModuleOrTheFramework() throws Exception {
		assertThat(page(SCREEN)).as("미리보기에서 앱 템플릿의 요소를 고르면 보이는 링크").contains("x-ref=\"attachHere\"", "요소에 붙이기");

		String choose = page(SCREEN + "&at=memo/internal/fields:5");
		assertThat(card(choose)).contains("요소에 붙이기", "<code>&lt;div class=&quot;card-body&quot; th:fragment=&quot;memoFields(bodyLength)&quot;&gt;</code>",
				"templates/memo/internal/fields.html:5")
			.contains("붙일 컴포넌트", "<optgroup label=\"memo 모듈의 컴포넌트\">", "<option value=\"memoBodyCount\">memoBodyCount</option>",
					"<optgroup label=\"프레임워크 컴포넌트\">", "<option value=\"cozyCopy\">cozyCopy</option>")
			.as("고르기 전에는 붙이기 폼이 없다")
			.doesNotContain("name=\"data.limit\"");
		valid(choose);

		String chosen = page(SCREEN + "&at=memo/internal/fields:5&component=memoBodyCount");
		assertThat(chosen).contains("<option value=\"memoBodyCount\" selected=\"selected\">memoBodyCount</option>",
				"이 요소가 memoBodyCount의 자리가 됩니다(x-data)", "본문을 쓰는 동안 글자 수와 한도를 보인다")
			.as("컴포넌트가 읽는 data-*마다 칸")
			.contains("<code>data-length</code>", "상태 length · number@data-length", "name=\"data.length\"", "name=\"data.limit\"")
			.as("이름은 선택 칸이다")
			.contains("<optgroup label=\"동작 (이벤트가 부른다)\">", "<option value=\"count\">count</option>",
					"<optgroup label=\"상태와 계산 (글자, 보임, 속성, 입력값)\">", "<option value=\"label\">label</option>")
			.contains("<option value=\"\">붙일 것 없음</option>", "name=\"fromPlace\" value=\"true\"");
		valid(chosen);

		String framework = page(SCREEN + "&at=memo/internal/fields:5&component=cozyCopy");
		assertThat(card(framework)).as("프레임워크 컴포넌트는 인자를 받는 메서드가 있어 글 칸과 안내")
			.contains("name=\"data.copy-text\"", "이벤트가 부를 이름: copy")
			.doesNotContain("<option value=\"count\">count</option>");
	}

	@Test
	void anElementInsideAComponentTakesItsNamesAndThePropertyTableSelectsThemToo() throws Exception {
		String inner = page(SCREEN + "&at=memo/internal/fields:9");
		assertThat(card(inner)).contains("memoBodyCount의 안쪽 요소입니다", "<option value=\"label\">label</option>")
			.as("안쪽 요소는 data-*를 받지 않는다")
			.doesNotContain("name=\"data.limit\"", "붙일 컴포넌트");
		valid(inner);

		String root = page(SCREEN + "&at=memo/internal/fields:7");
		assertThat(root).contains("memoBodyCount의 자리(x-data)입니다", "name=\"data.limit\"");

		String table = page(SCREEN);
		assertThat(table).as("속성 표의 붙이기 폼: 이름의 선택 칸, 자리의 data-*는 지금 값으로 채운 칸")
			.contains("<optgroup label=\"동작 (이벤트가 부른다)\">", "name=\"data.limit\" value=\"5000\"", "name=\"was.limit\" value=\"5000\"",
					"name=\"data.length\" value=\"${bodyLength}\"");
		valid(table);
	}

	@Test
	void elementsCozyAttachRefusesShowTheReasonAndBadPlacesAreRejected() throws Exception {
		assertThat(page(SCREEN + "&at=memo/internal/fields:6")).contains("memo/internal/fields:6의 요소는 그려질 때 다른 것으로 바뀌어 속성이 남지 않습니다",
				"이 요소에는 붙일 수 없습니다").doesNotContain("붙일 컴포넌트");
		assertThat(page(SCREEN + "&at=memo/internal/fields:1")).contains("memo/internal/fields:1에서 시작하는 요소가 0개입니다");
		this.mvc.perform(get(SCREEN + "&at=cozy/component/field:1")).andExpect(status().isBadRequest());
		this.mvc.perform(get(SCREEN + "&at=../pom:1")).andExpect(status().isBadRequest());
		this.mvc.perform(get(SCREEN + "&at=memo/internal/nope:1")).andExpect(status().isNotFound());

		String refused = after(attach(9, "plan").param("option", "text").param("value", "lable"),
				BACK + "&at=memo/internal/fields:9&component=memoBodyCount");
		assertThat(refused).contains("memoBodyCount에 없는 상태나 계산입니다: lable. label인가요?", "파일은 변경되지 않았습니다")
			.as("거절되면 붙이기 카드가 다시 열려 고쳐 보낸다")
			.contains("memoBodyCount의 안쪽 요소입니다");
		this.mvc.perform(attach(9, "plan").param("data.Limit", "1")).andExpect(status().isBadRequest());
		assertThat(WorkbenchCommandTest.sources(project)).isEqualTo(this.original);
	}

	@Test
	void theRemovedComponentIsDeclaredAndAttachedAgainFromTheWorkbenchAloneIntoTheSameFiles() throws Exception {
		String spec = read(SPEC);
		String script = read(SCRIPT);
		String fields = read(FIELDS);
		removeWithTheTerminal();
		assertThat(project.resolve(SPEC)).doesNotExist();
		assertThat(read(FIELDS)).doesNotContain("x-data", "x-text");

		// 1. 코드 생성의 화면 동작으로 다시 선언한다
		String declared = this.mvc.perform(post("/cozy/dev/workbench/commands").with(csrf())
			.param("recipe", "behavior")
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
			.param("opt.action.0.name", "count")
			.param("opt.action.0.steps", "within('textarea', set(length, length(event.value)))"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(declared).contains("적용했습니다");

		// 2. 미리보기에서 본문을 감싸는 요소를 고르고 memoBodyCount의 자리로 만든다
		assertThat(page(SCREEN + "&at=memo/internal/fields:7&component=memoBodyCount")).contains("name=\"data.length\"");
		MockHttpServletRequestBuilder root = attach(7, "plan").param("data.length", "${bodyLength}")
			.param("data.limit", "5000")
			.param("option", "on")
			.param("key", "input")
			.param("value", "count");
		String plan = after(root);
		assertThat(plan).contains("cozy attach memoBodyCount --at memo/internal/fields:7 --data &#39;length=${bodyLength}&#39; --data limit=5000 --on input:count",
				"memoBodyCount · 넘기는 값 length, limit · 이벤트 input count", "적용 전입니다", "name=\"data.length\" value=\"${bodyLength}\"");
		valid(plan);
		after(attach(7, "apply").param("data.length", "${bodyLength}").param("data.limit", "5000").param("option", "on").param("key", "input")
			.param("value", "count"));

		// 3. 글자 수를 보일 안쪽 요소를 고르고 이름을 고른다
		assertThat(page(SCREEN + "&at=memo/internal/fields:9")).contains("memoBodyCount의 안쪽 요소입니다");
		after(attach(9, "apply").param("option", "text").param("value", "label"));
		String last = after(attach(9, "apply").param("option", "cloak"));
		assertThat(last).contains("적용했습니다");

		assertThat(read(SPEC)).isEqualTo(spec);
		assertThat(read(SCRIPT)).isEqualTo(script);
		assertThat(read(FIELDS)).isEqualTo(fields);
	}

	/** 지운 상태에서 시작한다: cozy remove behavior memo body-count */
	void removeWithTheTerminal() throws Exception {
		String ui = Path.of(FragmentManifest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(new String[] { "remove", "behavior", "memo", "body-count", "--project", project.toString(), "--ui", ui }, project,
				null, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true, StandardCharsets.UTF_8));
		assertThat(code).as(err.toString(StandardCharsets.UTF_8)).isZero();
	}
}
