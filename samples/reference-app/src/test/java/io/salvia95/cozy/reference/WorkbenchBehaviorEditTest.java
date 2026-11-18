package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.salvia95.cozy.cli.CozyCli;
import io.salvia95.cozy.test.CozyHtmlValidator;
import io.salvia95.cozy.ui.catalog.FragmentManifest;

/**
 * 워크벤치에서 컴포넌트를 고친다: 화면 동작 폼을 지금 항목으로 채워 열고, 바꾼 행은 {@code cozy add behavior}, 지운 행은
 * {@code cozy remove behavior}의 한 계획으로 보이고 적용한다. 같은 명령을 터미널에서 차례로 실행한 것과 파일이 글자까지 같다. 이름 바꾸기와 지우기,
 * 오래된 폼의 거절, 낱말 표와 제안도 본다.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-behavior-edit-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchBehaviorEditTest {

	static final String URL = "/cozy/dev/workbench/commands";

	static final String EDIT = URL + "?recipe=behavior&edit=memoBodyCount";

	static final String SPEC = "src/main/resources/behaviors/memo.yml";

	static final String SCRIPT = "src/main/resources/static/memo/memo.js";

	static final String FIELDS = "src/main/resources/templates/memo/internal/fields.html";

	/** 계산 text를 지우고, label이 text 대신 같은 글을 바로 만든다 */
	static final String LABEL = "if(over, format('{length} / {limit}자 · 한도를 넘었습니다', 'ko-KR'), format('{length} / {limit}자', 'ko-KR'))";

	static Path project;

	@DynamicPropertySource
	static void project(DynamicPropertyRegistry registry) throws IOException {
		project = WorkbenchCommandTest.copyOfThisProject("workbench-behavior-edit-");
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

	String page(String url) throws Exception {
		return this.mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	String body(MockHttpServletRequestBuilder request) throws Exception {
		return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	static String hashOf(String html) {
		Matcher m = Pattern.compile("name=\"specHash\" value=\"([0-9a-f]{64})\"").matcher(html);
		assertThat(m.find()).as("폼에 명세의 지문이 있다").isTrue();
		return m.group(1);
	}

	/** 지금 항목 그대로인 폼에서 계산 text를 지우고 label을 바꾼 것 */
	MockHttpServletRequestBuilder edited(String step, String hash) {
		return post(URL).with(csrf())
			.param("recipe", "behavior")
			.param("edit", "memoBodyCount")
			.param("specHash", hash)
			.param("step", step)
			.param("arg.모듈", "memo")
			.param("arg.동작", "body-count")
			.param("opt.purpose", "본문을 쓰는 동안 글자 수와 한도를 보인다")
			.param("opt.state.0.name", "length")
			.param("opt.state.0.declaration", "number@data-length")
			.param("opt.state.1.name", "limit")
			.param("opt.state.1.declaration", "number@data-limit")
			.param("opt.computed.0.name", "over")
			.param("opt.computed.0.expression", "and(gt(limit, 0), gt(length, limit))")
			.param("opt.computed.2.name", "label")
			.param("opt.computed.2.expression", LABEL)
			.param("opt.action.0.name", "count")
			.param("opt.action.0.steps", "within('textarea', set(length, length(event.value)))");
	}

	String terminal(List<List<String>> commands) throws Exception {
		Path terminal = WorkbenchCommandTest.copyOfThisProject("workbench-behavior-terminal-");
		String ui = Path.of(FragmentManifest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
		for (List<String> command : commands) {
			ByteArrayOutputStream err = new ByteArrayOutputStream();
			String[] args = Stream.concat(command.stream(), Stream.of("--project", terminal.toString(), "--ui", ui)).toArray(String[]::new);
			int code = CozyCli.run(args, terminal, null, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true, StandardCharsets.UTF_8));
			assertThat(code).as(err.toString(StandardCharsets.UTF_8)).isZero();
		}
		return terminal.toString();
	}

	@Test
	void theFormOpensFilledWithTheComponentAndItsVocabulary() throws Exception {
		String html = page(EDIT);

		assertThat(html).contains("동작 고치기", "memoBodyCount", "memo 모듈 · 동작 이름 body-count")
			.contains("value=\"본문을 쓰는 동안 글자 수와 한도를 보인다\"", "value=\"number@data-limit\"",
					"value=\"and(gt(limit, 0), gt(length, limit))\"", "value=\"within(&#39;textarea&#39;, set(length, length(event.value)))\"")
			.as("모듈과 동작은 고치는 컴포넌트가 정한다")
			.doesNotContain("name=\"arg.동작\" value=\"body-count\" placeholder")
			.contains("name=\"edit\" value=\"memoBodyCount\"", "name=\"specHash\"")
			.as("계산·동작 칸 옆의 낱말 표")
			.contains("낱말 표: 상태의 종류", "낱말 표: 계산 함수", "낱말 표: 동작 단계", "낱말 표: 이벤트 값", "<code>gt(a, b)</code>",
					"<code>within(&#39;선택자&#39;, 단계 …)</code>")
			.as("식 칸은 이 컴포넌트의 이름과 낱말을 제안한다")
			.contains("list=\"command-p4-0-expression-list\"", "label=\"limit — 상태 number@data-limit\"",
					"label=\"gt(a, b) — a가 b보다 큰가 (&gt;)\"")
			.as("컴포넌트 전체: 이름 바꾸기와 지우기(확인 창)")
			.contains("새 동작 이름", "컴포넌트 지우기", "id=\"behavior-delete-confirm\"", "지울 컴포넌트: <strong>memoBodyCount</strong>");
		assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		assertThat(page("/cozy/dev/workbench/map?module=memo")).contains("href=\"/cozy/dev/workbench/commands?recipe=behavior&amp;edit=memoBodyCount\"");
		assertThat(page("/cozy/dev/workbench?spec=memo-form&node=10")).contains("/cozy/dev/workbench/commands?recipe=behavior&amp;edit=memoBodyCount");
		this.mvc.perform(get(URL + "?recipe=behavior&edit=memoNothing")).andExpect(status().isNotFound());
		this.mvc.perform(get(URL + "?recipe=crud&edit=memoBodyCount")).andExpect(status().isBadRequest());
	}

	@Test
	void changedAndRemovedRowsAreOnePlanAndApplyLikeTheTerminalInTheSameOrder() throws Exception {
		String hash = hashOf(page(EDIT));
		String add = "cozy add behavior memo body-count --computed &quot;label:" + LABEL.replace("'", "&#39;") + "&quot;";
		String remove = "cozy remove behavior memo body-count --computed text";

		String plan = body(edited("plan", hash));

		assertThat(plan).contains("명령 2개를 차례로 한 계획에 넣습니다", add, remove)
			.as("label이 text를 읽지 않게 고친 뒤 text를 지운다")
			.containsSubsequence(add, remove)
			.contains("적용 전입니다", "src/main/resources/behaviors/memo.yml", "계산 바꿈 label; 컴포넌트 memoBodyCount: 계산 text 지움")
			.contains("form=\"command-form\">적용</button>");
		assertThat(WorkbenchCommandTest.sources(project)).as("계획은 파일을 바꾸지 않는다").isEqualTo(this.original);

		String applied = body(edited("apply", hash));

		assertThat(applied).contains("적용했습니다");
		String terminal = terminal(List.of(
				List.of("add", "behavior", "memo", "body-count", "--computed", "label:" + LABEL),
				List.of("remove", "behavior", "memo", "body-count", "--computed", "text")));
		for (String file : List.of(SPEC, SCRIPT, FIELDS)) {
			assertThat(Files.readString(project.resolve(file), StandardCharsets.UTF_8)).as(file + ": 워크벤치와 터미널이 같다")
				.isEqualTo(Files.readString(Path.of(terminal).resolve(file), StandardCharsets.UTF_8));
		}
		assertThat(Files.readString(project.resolve(SPEC), StandardCharsets.UTF_8)).doesNotContain("text:");
		assertThat(applied).as("적용한 뒤에는 바뀐 명세로 폼을 다시 채운다")
			.contains("name=\"opt.computed.1.name\" value=\"label\"", "name=\"specHash\" value=\"" + hashOf(page(EDIT)) + "\"");
	}

	@Test
	void theUnchangedFormRunsNothingAndARefusalWritesNothing() throws Exception {
		String hash = hashOf(page(EDIT));

		String same = body(edited("plan", hash).param("opt.computed.1.name", "text")
			.param("opt.computed.1.expression", "format('{length} / {limit}자', 'ko-KR')")
			.param("opt.computed.2.expression", "if(over, format('{text} · 한도를 넘었습니다'), text)"));
		assertThat(same).contains("바뀐 항목이 없습니다").doesNotContain("적용 전입니다");

		String refused = body(edited("apply", hash).param("opt.computed.2.name", "").param("opt.computed.2.expression", "")
			.param("opt.computed.1.name", "text")
			.param("opt.computed.1.expression", "format('{length} / {limit}자', 'ko-KR')"));
		assertThat(refused).contains("템플릿이 부르는 이름입니다: label (memo/internal/fields.html:9)", "파일은 변경되지 않았습니다");
		assertThat(WorkbenchCommandTest.sources(project)).isEqualTo(this.original);
	}

	@Test
	void aFormOpenedBeforeTheSpecChangedIsNotApplied() throws Exception {
		String hash = hashOf(page(EDIT));
		Files.writeString(project.resolve(SPEC), Files.readString(project.resolve(SPEC), StandardCharsets.UTF_8) + "# 다른 곳에서 고침\n",
				StandardCharsets.UTF_8);
		Map<String, String> before = WorkbenchCommandTest.sources(project);

		String stale = body(edited("apply", hash));

		assertThat(stale).contains("폼을 연 뒤 동작 명세가 바뀌었습니다", "지금 내용으로 다시 열기").doesNotContain("적용했습니다");
		assertThat(WorkbenchCommandTest.sources(project)).isEqualTo(before);
	}

	@Test
	void renamingAndDeletingPlanTheWholeComponentAndItsAttachedPlaces() throws Exception {
		String hash = hashOf(page(EDIT));

		String rename = body(post(URL).with(csrf()).param("recipe", "behavior").param("edit", "memoBodyCount").param("specHash", hash)
			.param("change", "rename").param("newName", "body-length").param("step", "plan"));
		assertThat(rename).contains("cozy rename behavior memo body-count body-length", "x-data memoBodyCount → memoBodyLength",
				"form=\"behavior-rename\">적용</button>");

		String delete = body(post(URL).with(csrf()).param("recipe", "behavior").param("edit", "memoBodyCount").param("specHash", hash)
			.param("change", "delete").param("step", "plan"));
		assertThat(delete).contains("cozy remove behavior memo body-count", "뗌 memoBodyCount", "form=\"behavior-delete\">적용</button>");
		assertThat(WorkbenchCommandTest.sources(project)).isEqualTo(this.original);

		String renamed = body(post(URL).with(csrf()).param("recipe", "behavior").param("edit", "memoBodyCount").param("specHash", hash)
			.param("change", "rename").param("newName", "body-length").param("step", "apply"));
		assertThat(renamed).contains("적용했습니다", "name=\"edit\" value=\"memoBodyLength\"");
		assertThat(Files.readString(project.resolve(FIELDS), StandardCharsets.UTF_8)).contains("x-data=\"memoBodyLength\"");

		String deleted = body(post(URL).with(csrf()).param("recipe", "behavior").param("edit", "memoBodyLength")
			.param("specHash", hashOf(page(URL + "?recipe=behavior&edit=memoBodyLength"))).param("change", "delete").param("step", "apply"));
		assertThat(deleted).contains("적용했습니다", "컴포넌트를 지웠습니다: memoBodyLength").doesNotContain("name=\"edit\"");
		assertThat(Files.exists(project.resolve(SPEC))).isFalse();
		assertThat(Files.readString(project.resolve(FIELDS), StandardCharsets.UTF_8)).doesNotContain("x-data");
	}

	@Test
	void writesNeedAKnownChangeAndTheEditTarget() throws Exception {
		this.mvc.perform(post(URL).with(csrf()).param("recipe", "behavior").param("edit", "memoBodyCount").param("change", "drop")
			.param("step", "plan")).andExpect(status().isBadRequest());
		this.mvc.perform(post(URL).with(csrf()).param("recipe", "behavior").param("edit", "memoNothing").param("step", "plan"))
			.andExpect(status().isNotFound());
		this.mvc.perform(post(URL).param("recipe", "behavior").param("edit", "memoBodyCount").param("step", "plan"))
			.andExpect(status().isForbidden());
	}
}
