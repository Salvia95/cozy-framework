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
					assertThat(form).as(recipe.name() + " " + param.name())
						.contains("name=\"" + (param.option() ? "opt." : "arg.") + param.name() + "\"");
				}
			}
			String screen = body(get(URL).param("recipe", "screen"));
			assertThat(screen).as("고를 값은 select로").contains("<option value=\"overview\"").contains("<option value=\"details\"");
			assertThat(screen).as("모듈은 지금 있는 모듈에서 고를 수 있다").contains("<option value=\"memo\"");
			assertThat(body(get(URL).param("recipe", "crud"))).as("여러 번 쓰는 옵션은 한 줄에 하나").contains("<textarea");
		}

		@Test
		void aPlanShowsTheCommandAndTheFilesWithoutWritingAnything() throws Exception {
			Map<String, String> before = sources(project);
			String html = body(command("module", "plan").param("arg.모듈", "voc").param("opt.label", "고객의 소리"));
			assertThat(html).contains("cozy add module voc --label &quot;고객의 소리&quot;")
				.contains("만듦")
				.contains("src/main/java/io/salvia95/cozy/reference/voc/package-info.java")
				.contains("계획만 보였습니다")
				.contains("적용하면 위의 파일 1개를 만들거나 바꿉니다.");
			assertThat(html.split("name=\"step\" value=\"apply\"", -1)).as("적용 버튼은 하나다").hasSize(2);
			assertThat(html).as("폼은 적은 값을 그대로 가진다").contains("value=\"voc\"").contains("value=\"고객의 소리\"");
			assertThat(sources(project)).isEqualTo(before);
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		}

		@Test
		void aRecipeWithSlotsListsThemAfterApplying() throws Exception {
			String html = body(command("migration", "apply").param("arg.모듈", "memo").param("arg.설명", "add_due_date"));
			assertThat(html).contains("적용했습니다")
				.contains("빈칸 1곳")
				.contains("src/main/resources/db/migration/V202611100900__memo_add_due_date.sql")
				.contains("migration-sql");
		}

		@Test
		void workTheCommandLeavesToDoByHandIsShownWithWhatToInsert() throws Exception {
			Map<String, String> before = sources(project);
			String html = body(command("field", "plan").param("arg.모듈", "memo")
				.param("opt.entity", "Memo")
				.param("opt.field", "dueOn:date:마감일"));
			assertThat(html).contains("cozy add field memo --entity Memo --field dueOn:date:마감일")
				.contains("남은 일 1곳")
				.contains("적용한 뒤 손으로 할 일")
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
				.param("opt.nav", "통계:no-such-icon"));
			assertThat(html).contains("no-such-icon").contains("아무 파일도 바꾸지 않았습니다").doesNotContain("적용했습니다");
			assertThat(sources(project)).isEqualTo(before);
		}

		@Test
		void requiredFieldsAreCheckedBeforeCallingTheCommand() throws Exception {
			String html = body(command("crud", "plan").param("arg.모듈", "task").param("opt.entity", " "));
			assertThat(html).contains("엔티티 이름 (PascalCase)")
				.contains("값을 적습니다")
				.doesNotContain("계획만 보였습니다");
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
				.param("opt.field", "title:text!:제목\r\ndueOn:date:마감일\r\n\r\ndone:bool:완료\r\n")
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
				.param("opt.field", "title:text!:제목\ndueOn:date:마감일\ndone:bool:완료")
				.param("opt.publish", "created"));
			assertThat(again).contains("바뀐 것 없음");
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
