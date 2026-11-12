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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
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
import io.salvia95.cozy.cli.internal.Recipes;
import io.salvia95.cozy.test.CozyHtmlValidator;
import io.salvia95.cozy.ui.catalog.FragmentManifest;

/**
 * 워크벤치의 만들기({@code /cozy/dev/workbench/commands}): 폼으로 cozy 명령을 만들고, 계획을 본 뒤 적용한다. 결과는 같은 명령을 터미널에서 실행한
 * 것과 같다.
 */
class WorkbenchCommandTest {

	static final String URL = "/cozy/dev/workbench/commands";

	/** 이 앱의 소스를 그대로 복사한 프로젝트 */
	static Path copyOfThisProject(String prefix) throws IOException {
		Path dir = Files.createTempDirectory(Path.of("target"), prefix).toAbsolutePath();
		Files.copy(Path.of("pom.xml"), dir.resolve("pom.xml"));
		try (Stream<Path> walk = Files.walk(Path.of("src"))) {
			for (Path file : walk.toList()) {
				Path target = dir.resolve(file.toString());
				if (Files.isDirectory(file)) {
					Files.createDirectories(target);
				}
				else {
					Files.copy(file, target);
				}
			}
		}
		return dir;
	}

	/** 프로젝트의 src 아래 파일 → 내용 */
	static Map<String, String> sources(Path project) throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> walk = Files.walk(project.resolve("src"))) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				files.put(project.relativize(file).toString().replace('\\', '/'), Files.readString(file, StandardCharsets.UTF_8));
			}
		}
		files.put("pom.xml", Files.readString(project.resolve("pom.xml"), StandardCharsets.UTF_8));
		return files;
	}

	@BeforeAll
	static void fixedTime() {
		// 마이그레이션 파일 이름에 시각이 들어간다. 워크벤치와 터미널이 같은 시각을 쓰게 한다
		System.setProperty("cozy.cli.now", "202611100900");
	}

	@AfterAll
	static void systemTime() {
		System.clearProperty("cozy.cli.now");
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-command-${random.uuid}",
			"cozy.runtime.open-browser=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class InAProject {

		static Path project;

		@DynamicPropertySource
		static void project(DynamicPropertyRegistry registry) throws IOException {
			project = copyOfThisProject("workbench-commands-");
			registry.add("cozy.dev.workbench.project-dir", project::toString);
		}

		@Autowired
		MockMvc mvc;

		String body(MockHttpServletRequestBuilder request) throws Exception {
			return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		}

		MockHttpServletRequestBuilder command(String recipe, String step) {
			return post(URL).with(csrf()).param("recipe", recipe).param("step", step);
		}

		@Test
		void everyRecipeIsListedWithAForm() throws Exception {
			String list = body(get(URL));
			for (Recipes.Recipe recipe : Recipes.ALL) {
				assertThat(list).as(recipe.name()).contains("recipe=" + recipe.name().replace(" ", "%20"));
				String form = body(get(URL).param("recipe", recipe.name()));
				assertThat(form).as(recipe.name())
					.contains(recipe.usage().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"));
				for (Recipes.Param param : recipe.params()) {
					String field = (param.option() ? "opt." : "arg.") + param.name();
					assertThat(form).as(recipe.name() + " " + param.name())
						.contains("name=\"" + (param.parts().isEmpty() ? field : field + ".0." + param.parts().getFirst().name()) + "\"");
				}
				assertThat(new CozyHtmlValidator().validate(form).stream().filter(CozyHtmlValidator.Message::isError)).as(recipe.name())
					.isEmpty();
			}
			String screen = body(get(URL).param("recipe", "screen"));
			assertThat(screen).as("고를 값은 select로, 이름 뒤에 값").contains("<option value=\"overview\">대시보드 (overview)</option>")
				.contains("<option value=\"details\">상세 정보 확인 (details)</option>");
			assertThat(screen).as("모듈은 지금 있는 모듈을 제안하고 새 이름도 받는다").contains("list=\"command-p0-list\"")
				.contains("<option value=\"memo\"></option>");
			assertThat(body(get(URL).param("recipe", "migration"))).as("있어야 하는 모듈은 고르기")
				.contains("<option value=\"memo\">memo</option>");
			String crud = body(get(URL).param("recipe", "crud"));
			assertThat(crud).as("여러 번 쓰는 옵션은 그리드, 칸마다 구성 요소 (입력 그리드와 같은 모양)")
				.contains("class=\"table table-bordered cozy-grid cozy-workbench-grid\"")
				.contains("class=\"cozy-grid-ops\"")
				.contains("name=\"opt.field.0.name\"")
				.contains("name=\"opt.field.0.kind\"")
				.contains("type=\"checkbox\" value=\"!\"")
				.contains("<option value=\"longtext\">여러 줄 텍스트 (longtext)</option>")
				.contains("value=\"add:opt.field\"")
				.doesNotContain("<textarea");
			assertThat(crud).as("여럿 고르는 값은 체크박스").contains("name=\"opt.publish\" value=\"created\"")
				.contains("생성 (created)");
			assertThat(crud).as("아이콘은 이름 목록에서").contains("list=\"command-icons\"").contains("<option value=\"notes\">");
			assertThat(crud.indexOf("name=\"step\" value=\"plan\"")).as("Enter는 변경 파일 확인이다: 행 버튼보다 앞에 있다")
				.isLessThan(crud.indexOf("name=\"row\""));
		}

		@Test
		void choicesComeFromTheApp() throws Exception {
			assertThat(body(get(URL).param("recipe", "field"))).as("add field의 엔티티는 crud 한 벌")
				.contains("<optgroup label=\"memo\" data-cozy-module=\"memo\">")
				.contains("<option value=\"Memo\">Memo</option>");
			assertThat(body(get(URL).param("recipe", "listener"))).as("받을 이벤트는 모든 모듈의 이벤트")
				.contains("<option value=\"memo.MemoCreated\">memo.MemoCreated</option>");
			assertThat(body(get(URL).param("recipe", "event-version"))).contains("<option value=\"MemoCreated\">MemoCreated</option>");
			assertThat(body(get(URL).param("recipe", "query"))).as("조회 인터페이스와 돌려줄 DTO")
				.contains("<option value=\"ActivityFinder\">ActivityFinder</option>")
				.contains("<option value=\"list:ActivityItem\">ActivityItem 목록 (list:ActivityItem)</option>")
				.contains("<option value=\"long\">64비트 정수 (long)</option>");
			assertThat(body(get(URL).param("recipe", "fragment"))).as("파라미터 타입은 종류와 DTO")
				.contains("<optgroup label=\"memo 모듈의 DTO\" data-cozy-module=\"memo\">")
				.contains("<option value=\"list:MemoItem\">MemoItem 목록 (list:MemoItem)</option>");
			assertThat(body(get(URL).param("recipe", "link"))).as("쓰려는 모듈은 여럿 고르기")
				.contains("name=\"opt.uses\" value=\"activity\"");
		}

		@Test
		void rowButtonsEditTheGridWithoutRunningTheCommand() throws Exception {
			Map<String, String> before = sources(project);
			String added = body(post(URL).with(csrf())
				.param("recipe", "crud")
				.param("row", "add:opt.field")
				.param("arg.모듈", "task")
				.param("opt.field.0.name", "title")
				.param("opt.field.0.kind", "text")
				.param("opt.field.0.required", "!")
				.param("opt.field.0.label", "제목"));
			assertThat(added).as("적던 값은 그대로, 행이 하나 늘었다")
				.contains("name=\"opt.field.0.name\" value=\"title\"")
				.contains("name=\"opt.field.1.name\" value=\"\"")
				.contains("2행")
				.contains("name=\"arg.모듈\" value=\"task\"")
				.doesNotContain("필수 항목을 입력하세요")
				.doesNotContain("적용 전입니다");
			assertThat(added).as("행 버튼은 그 그리드로 돌아온다").contains("formaction=\"/cozy/dev/workbench/commands?recipe=crud#command-opt-field\"")
				.as("다시 읽어도 같은 생성 항목이다: 주소에 recipe가 있다")
				.contains("action=\"/cozy/dev/workbench/commands?recipe=crud#command-result\"")
				.contains("id=\"command-opt-field\"");
			assertThat(new CozyHtmlValidator().validate(added).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
			String deleted = body(post(URL).with(csrf())
				.param("recipe", "crud")
				.param("row", "delete:opt.field/0")
				.param("opt.field.0.name", "title")
				.param("opt.field.1.name", "body"));
			assertThat(deleted).contains("name=\"opt.field.0.name\" value=\"body\"").doesNotContain("value=\"title\"");
			this.mvc.perform(post(URL).with(csrf()).param("recipe", "crud").param("row", "delete:opt.field/3"))
				.andExpect(status().isBadRequest());
			this.mvc.perform(post(URL).with(csrf()).param("recipe", "screen").param("row", "add:opt.nav"))
				.andExpect(status().isBadRequest());
			assertThat(sources(project)).isEqualTo(before);
		}

		@Test
		void theChangedFilesAreATreeByModuleAndLayer() throws Exception {
			String html = body(command("crud", "plan").param("arg.모듈", "task")
				.param("opt.entity", "Task")
				.param("opt.label", "할 일")
				.param("opt.icon", "checklist")
				.param("opt.field.0.name", "title")
				.param("opt.field.0.kind", "text")
				.param("opt.field.0.required", "!")
				.param("opt.field.0.label", "제목")
				.param("opt.publish", "created"));
			assertThat(html).contains("적용 전입니다").contains("task 모듈").contains("cozy-workbench-changes");
			int tree = html.indexOf("cozy-workbench-changes");
			assertThat(html.substring(tree)).as("계층은 정한 순서")
				.containsSubsequence(">공개 API<", ">내부 구현<", ">화면<", ">SQL<", ">화면 명세<", ">테스트<");
			assertThat(html).contains(">TaskFacade.java<").contains(">spi/dto<").contains(">task-list.yml<");
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		}

		@Test
		void aPlanShowsTheCommandAndTheFilesWithoutWritingAnything() throws Exception {
			Map<String, String> before = sources(project);
			String html = body(command("module", "plan").param("arg.모듈", "voc").param("opt.label", "고객의 소리"));
			assertThat(html).contains("cozy add module voc --label &quot;고객의 소리&quot;")
				.contains("생성")
				.contains("src/main/java/io/salvia95/cozy/reference/voc/package-info.java")
				.contains("적용 전입니다")
				.contains("적용하면 파일 1개가 생성되거나 수정됩니다.");
			assertThat(html.split("name=\"step\" value=\"apply\"", -1)).as("적용 버튼은 하나다").hasSize(2);
			assertThat(html).as("폼은 적은 값을 그대로 가진다").contains("value=\"voc\"").contains("value=\"고객의 소리\"");
			assertThat(sources(project)).isEqualTo(before);
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		}

		@Test
		void aRecipeWithSlotsListsThemAfterApplying() throws Exception {
			String html = body(command("migration", "apply").param("arg.모듈", "memo").param("arg.설명", "add_due_date"));
			assertThat(html).contains("적용했습니다")
				.contains("미구현 항목 1건")
				.contains("src/main/resources/db/migration/V202611100900__memo_add_due_date.sql")
				.contains("migration-sql");
		}

		@Test
		void workTheCommandLeavesToDoByHandIsShownWithWhatToInsert() throws Exception {
			Map<String, String> before = sources(project);
			String html = body(command("field", "plan").param("arg.모듈", "memo")
				.param("opt.entity", "Memo")
				.param("opt.field.0.name", "dueOn")
				.param("opt.field.0.kind", "date")
				.param("opt.field.0.label", "마감일"));
			assertThat(html).contains("cozy add field memo --entity Memo --field dueOn:date:마감일")
				.contains("수동 수정 항목 1건")
				.contains("적용 후 수동 수정 항목")
				.contains("src/main/resources/templates/memo/api/detail.html")
				.contains("상세의 datagrid에 마감일 항목을 더하세요")
				.contains("&lt;div class=&quot;datagrid-title&quot;&gt;마감일&lt;/div&gt;");
			assertThat(sources(project)).isEqualTo(before);
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		}

		@Test
		void aRefusedCommandShowsTheReasonAndWritesNothing() throws Exception {
			Map<String, String> before = sources(project);
			String html = body(command("screen", "apply").param("arg.모듈", "memo")
				.param("arg.화면", "stats")
				.param("opt.pattern", "details")
				.param("opt.title", "통계")
				.param("opt.nav.0.label", "통계")
				.param("opt.nav.0.icon", "no-such-icon"));
			assertThat(html).contains("no-such-icon").contains("파일은 변경되지 않았습니다").doesNotContain("적용했습니다");
			assertThat(sources(project)).isEqualTo(before);
		}

		@Test
		void requiredFieldsAreCheckedBeforeCallingTheCommand() throws Exception {
			String html = body(command("crud", "plan").param("arg.모듈", "task").param("opt.entity", " "));
			assertThat(html).contains("엔티티 이름 (PascalCase)")
				.contains("필수 항목을 입력하세요")
				.doesNotContain("적용 전입니다");
		}

		@Test
		void writesNeedTheCsrfTokenAndTheSameOrigin() throws Exception {
			this.mvc.perform(post(URL).param("recipe", "module").param("step", "apply").param("arg.모듈", "nope"))
				.andExpect(status().isForbidden());
			this.mvc
				.perform(command("module", "apply").param("arg.모듈", "nope").header("Origin", "http://evil.example"))
				.andExpect(status().isForbidden());
			this.mvc.perform(command("module", "plan").param("arg.모듈", "nope").header("Origin", "http://localhost"))
				.andExpect(status().isOk());
			assertThat(project.resolve("src/main/java/io/salvia95/cozy/reference/nope")).doesNotExist();
		}

		@Test
		void unknownRecipesAndStepsAreRefused() throws Exception {
			this.mvc.perform(get(URL).param("recipe", "no-such")).andExpect(status().isNotFound());
			this.mvc.perform(command("module", "delete").param("arg.모듈", "x")).andExpect(status().isBadRequest());
		}
	}

	/** 이 프로젝트에는 이 테스트만 쓴다: 터미널에서 같은 명령을 실행한 사본과 통째로 비교한다 */
	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-command-${random.uuid}",
			"cozy.runtime.open-browser=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class ComparedWithTheTerminal {

		static Path project;

		@DynamicPropertySource
		static void project(DynamicPropertyRegistry registry) throws IOException {
			project = copyOfThisProject("workbench-compared-");
			registry.add("cozy.dev.workbench.project-dir", project::toString);
		}

		@Autowired
		MockMvc mvc;

		String body(MockHttpServletRequestBuilder request) throws Exception {
			return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		}

		MockHttpServletRequestBuilder command(String recipe, String step) {
			return post(URL).with(csrf()).param("recipe", recipe).param("step", step);
		}

		@Test
		void applyingGivesTheSameFilesAsTheTerminal() throws Exception {
			String[] args = { "add", "crud", "task", "--entity", "Task", "--label", "할 일", "--icon", "checklist", "--field",
					"title:text!:제목", "--field", "dueOn:date:마감일", "--field", "done:bool:완료", "--publish", "created" };
			Path terminal = copyOfThisProject("workbench-terminal-");
			ByteArrayOutputStream err = new ByteArrayOutputStream();
			String[] withProject = Stream
				.concat(Stream.of(args), Stream.of("--project", terminal.toString(), "--ui",
						Path.of(FragmentManifest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString()))
				.toArray(String[]::new);
			int code = CozyCli.run(withProject, terminal, null, new PrintStream(new ByteArrayOutputStream()),
					new PrintStream(err, true, StandardCharsets.UTF_8));
			assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
			assertThat(code).isZero();

			String html = body(command("crud", "apply").param("arg.모듈", "task")
				.param("opt.entity", "Task")
				.param("opt.label", "할 일")
				.param("opt.icon", "checklist")
				.param("opt.field.0.name", "title")
				.param("opt.field.0.kind", "text")
				.param("opt.field.0.required", "!")
				.param("opt.field.0.label", "제목")
				.param("opt.field.1.name", "dueOn")
				.param("opt.field.1.kind", "date")
				.param("opt.field.1.label", "마감일")
				.param("opt.field.2.name", "")
				.param("opt.field.2.kind", "text")
				.param("opt.field.3.name", "done")
				.param("opt.field.3.kind", "bool")
				.param("opt.field.3.label", "완료")
				.param("opt.publish", "created"));
			assertThat(html).contains("적용했습니다")
				.contains("cozy add crud task --entity Task --label &quot;할 일&quot; --icon checklist --field title:text!:제목")
				.contains("src/main/java/io/salvia95/cozy/reference/task/api/TaskController.java")
				.as("Java가 바뀌면 다시 빌드하고 실행해야 보인다고 알린다")
				.contains("다시 빌드하고 실행");
			assertThat(sources(project)).as("워크벤치로 만든 것과 터미널로 만든 것이 글자까지 같다").isEqualTo(sources(terminal));

			String again = body(command("crud", "apply").param("arg.모듈", "task")
				.param("opt.entity", "Task")
				.param("opt.label", "할 일")
				.param("opt.icon", "checklist")
				.param("opt.field.0.name", "title")
				.param("opt.field.0.kind", "text")
				.param("opt.field.0.required", "!")
				.param("opt.field.0.label", "제목")
				.param("opt.field.1.name", "dueOn")
				.param("opt.field.1.kind", "date")
				.param("opt.field.1.label", "마감일")
				.param("opt.field.2.name", "done")
				.param("opt.field.2.kind", "bool")
				.param("opt.field.2.label", "완료")
				.param("opt.publish", "created"));
			assertThat(again).contains("변경 사항 없음");
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		}
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-command-${random.uuid}",
			"cozy.runtime.open-browser=false", "cozy.dev.workbench.project-dir=target/no-such-project" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class WithoutAProject {

		@Autowired
		MockMvc mvc;

		@Test
		void nothingCanBePlannedOrApplied() throws Exception {
			String html = this.mvc.perform(get(URL)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(html).contains("앱 프로젝트 폴더");
			this.mvc.perform(post(URL).with(csrf()).param("recipe", "module").param("step", "apply").param("arg.모듈", "voc"))
				.andExpect(status().isConflict());
		}
	}
}
