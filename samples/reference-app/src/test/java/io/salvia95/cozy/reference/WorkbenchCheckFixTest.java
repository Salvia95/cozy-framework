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
import java.util.Comparator;
import java.util.stream.Stream;

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
 * 검사에서 고치기: C12의 고치는 명령({@code cozy sync behaviors}, {@code cozy migrate behaviors})은 검사 화면의 계획 버튼으로 변경 파일과 남은 일을
 * 보이고 적용한다. C13 항목은 고치는 화면으로 이어진다. 위반 픽스처(C12, C13)를 워크벤치만으로 고치면 {@code cozy check}가 통과한다.
 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-check-fix-${random.uuid}", "cozy.runtime.open-browser=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchCheckFixTest {

	static final String CHECK = "/cozy/dev/workbench/check";

	static final String COMMANDS = "/cozy/dev/workbench/commands";

	static final Path FIXTURES = Path.of("../violations/checks");

	static final String SPEC = "src/main/resources/behaviors/memo.yml";

	static final String SCRIPT = "src/main/resources/static/memo/memo.js";

	/** 테스트마다 위반 픽스처 하나를 복사해 둔다 */
	static Path project;

	@DynamicPropertySource
	static void project(DynamicPropertyRegistry registry) throws IOException {
		project = Files.createTempDirectory(Path.of("target"), "workbench-check-fix-").toAbsolutePath();
		// 워크벤치는 시작할 때 앱 프로젝트 폴더인지 본다
		use("C12-behaviors");
		registry.add("cozy.dev.workbench.project-dir", project::toString);
	}

	@Autowired
	MockMvc mvc;

	/** 픽스처를 복사한다 (빌드 결과는 빼고) */
	static void use(String fixture) throws IOException {
		try (Stream<Path> old = Files.walk(project)) {
			for (Path p : old.sorted(Comparator.reverseOrder()).toList()) {
				if (!p.equals(project)) {
					Files.delete(p);
				}
			}
		}
		Path from = FIXTURES.resolve(fixture);
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path file : walk.toList()) {
				Path relative = from.relativize(file);
				if (relative.startsWith("target") || relative.toString().equals("build.log")) {
					continue;
				}
				Path to = project.resolve(relative.toString());
				if (Files.isDirectory(file)) {
					Files.createDirectories(to);
				}
				else {
					Files.copy(file, to);
				}
			}
		}
	}

	String page(String url) throws Exception {
		return this.mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	String body(MockHttpServletRequestBuilder request) throws Exception {
		return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 계획 버튼이나 적용을 보내고 돌아간 검사 화면 */
	String fix(String fix, String step) throws Exception {
		MvcResult posted = this.mvc.perform(post(CHECK).with(csrf()).param("fix", fix).param("step", step))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(CHECK))
			.andReturn();
		return this.mvc.perform(get(CHECK).flashAttrs(posted.getFlashMap())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 터미널의 cozy check */
	static int cozyCheck() throws Exception {
		String ui = Path.of(FragmentManifest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
		return CozyCli.run(new String[] { "check", "--project", project.toString(), "--ui", ui }, project, null,
				new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
	}

	static void valid(String html) {
		assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
	}

	@Test
	void theC12FixtureIsFixedByThePlanButtonOfTheCheck() throws Exception {
		use("C12-behaviors");
		assertThat(cozyCheck()).isNotZero();
		String before = Files.readString(project.resolve(SCRIPT), StandardCharsets.UTF_8);

		String check = page(CHECK);
		assertThat(check).contains("동작 명세에서 만든 파일을 손으로 고쳤습니다", "name=\"fix\" value=\"sync-behaviors\"",
				"aria-label=\"cozy sync behaviors 변경 파일 확인\"");
		valid(check);
		assertThat(page("/cozy/dev/workbench")).as("개요의 위반에도 계획 버튼").contains("name=\"fix\" value=\"sync-behaviors\"");

		String plan = fix("sync-behaviors", "plan");
		assertThat(plan).contains("동작 명세에서 JS 다시 만들기 · 변경 파일", "적용 전입니다", SCRIPT, "바뀐 줄 보기", "cozy sync behaviors",
				"name=\"step\" value=\"apply\"");
		valid(plan);
		assertThat(Files.readString(project.resolve(SCRIPT), StandardCharsets.UTF_8)).as("계획은 쓰지 않는다").isEqualTo(before);

		String applied = fix("sync-behaviors", "apply");
		assertThat(applied).contains("동작 명세에서 JS 다시 만들기 · 적용 결과", "적용했습니다").doesNotContain("오류 C12")
			.as("다시 만든 뒤에는 붙이지 않은 컴포넌트(C13)가 보이고, 고치는 화면으로 이어진다")
			.contains("어느 템플릿에도 붙이지 않은 컴포넌트입니다: memoPinToggle", "edit=memoPinToggle");

		// 픽스처에는 화면이 없으므로 쓰지 않는 컴포넌트를 지운다 (동작 고치기의 컴포넌트 지우기)
		String form = page(COMMANDS + "?recipe=behavior&edit=memoPinToggle");
		assertThat(body(post(COMMANDS).with(csrf()).param("recipe", "behavior").param("edit", "memoPinToggle")
			.param("specHash", WorkbenchBehaviorEditTest.hashOf(form)).param("change", "delete").param("step", "apply"))).contains("적용했습니다");
		assertThat(page(CHECK)).contains("위반 없음");
		assertThat(cozyCheck()).as("터미널의 cozy check가 통과한다").isZero();
	}

	@Test
	void aHandWrittenScriptIsMovedToASpecAndWhatCannotMoveIsShown() throws Exception {
		use("C12-behaviors");
		Files.delete(project.resolve(SPEC));
		String legacy = """
				document.addEventListener('alpine:init', function () {
				  // 패널을 펼치고 접는다
				  window.Alpine.data('memoPanel', function () {
				    return {
				      open: false,
				      toggle: function () {
				        if (this.open) {
				          this.open = false;
				        } else {
				          this.open = true;
				        }
				      }
				    };
				  });
				});
				""";
		Files.writeString(project.resolve(SCRIPT), legacy, StandardCharsets.UTF_8);

		assertThat(page(CHECK)).contains("손으로 쓴 모듈 JS입니다", "name=\"fix\" value=\"migrate-behaviors\"");
		String stuck = fix("migrate-behaviors", "plan");
		assertThat(stuck).as("낱말 밖의 JS는 남은 일로 그 자리와 이유를 보인다")
			.contains("손으로 쓴 모듈 JS를 동작 명세로 옮기기 · 변경 파일", "남은 일 1곳", "else는 옮기지 않습니다");

		Files.writeString(project.resolve(SCRIPT), legacy.replace("""
				        if (this.open) {
				          this.open = false;
				        } else {
				          this.open = true;
				        }
				""", """
				        this.open = !this.open;
				"""), StandardCharsets.UTF_8);
		assertThat(fix("migrate-behaviors", "plan")).contains("적용 전입니다", SPEC, "바뀐 줄 보기");
		String applied = fix("migrate-behaviors", "apply");
		assertThat(applied).contains("적용했습니다").doesNotContain("손으로 쓴 모듈 JS입니다");
		assertThat(Files.readString(project.resolve(SPEC), StandardCharsets.UTF_8)).contains("panel:", "toggle(open)");
	}

	@Test
	void theC13FixtureLinksToTheScreensThatFixItAndTheWorkbenchAloneMakesTheCheckPass() throws Exception {
		use("C13-behaviors");
		assertThat(cozyCheck()).isNotZero();

		String check = page(CHECK);
		assertThat(check).as("붙이지 않은 컴포넌트: 붙일 화면과 동작 고치기(지우기)")
			.contains("href=\"/cozy/dev/workbench/map?module=memo#module-screens\"", "href=\"/cozy/dev/workbench/commands?recipe=behavior&amp;edit=memoPanel\"")
			.as("부르지 않는 동작: 붙인 곳")
			.contains("href=\"/cozy/dev/workbench/map?module=memo#behavior-memoSelection\"")
			.as("화면 이벤트: 모듈 화면의 그 이벤트")
			.contains("href=\"/cozy/dev/workbench/map?module=memo#ui-event-memo-picked\"", "href=\"/cozy/dev/workbench/map?module=memo#ui-event-memo-archived\"",
					"href=\"/cozy/dev/workbench/commands?recipe=behavior&amp;edit=memoSelection\"")
			.doesNotContain("직접 수정");
		valid(check);
		assertThat(page("/cozy/dev/workbench/map?module=memo")).contains("id=\"module-screens\"", "id=\"behavior-memoSelection\"",
				"id=\"ui-event-memo-picked\"");

		// 1. 쓰지 않는 컴포넌트를 지운다 (동작 고치기의 컴포넌트 지우기)
		String panel = page(COMMANDS + "?recipe=behavior&edit=memoPanel");
		assertThat(body(post(COMMANDS).with(csrf()).param("recipe", "behavior").param("edit", "memoPanel")
			.param("specHash", WorkbenchBehaviorEditTest.hashOf(panel)).param("change", "delete").param("step", "apply"))).contains("적용했습니다");

		// 2. 고른 행을 세는 컴포넌트: 부르지 않는 동작(drop), 받는 곳이 없는 emit, 내는 곳이 없는 받기와 그것만 부르던 clear를 한 폼에서 지운다
		String selection = page(COMMANDS + "?recipe=behavior&edit=memoSelection");
		String applied = body(post(COMMANDS).with(csrf())
			.param("recipe", "behavior")
			.param("edit", "memoSelection")
			.param("specHash", WorkbenchBehaviorEditTest.hashOf(selection))
			.param("step", "apply")
			.param("arg.모듈", "memo")
			.param("arg.동작", "selection")
			.param("opt.purpose", "고른 행을 센다")
			.param("opt.state.0.name", "ids")
			.param("opt.state.0.declaration", "list")
			.param("opt.action.0.name", "pick")
			.param("opt.action.0.steps", "toggleItem(ids, event.data.id)"));
		assertThat(applied).contains("명령 2개를 차례로 한 계획에 넣습니다", "적용했습니다");

		assertThat(page(CHECK)).contains("위반 없음");
		assertThat(cozyCheck()).as("터미널의 cozy check가 통과한다").isZero();
	}

	@Test
	void writesNeedAKnownFixAStepAndTheSameOrigin() throws Exception {
		use("C12-behaviors");
		this.mvc.perform(post(CHECK).with(csrf()).param("fix", "rm").param("step", "plan")).andExpect(status().isBadRequest());
		this.mvc.perform(post(CHECK).with(csrf()).param("fix", "sync-behaviors").param("step", "write")).andExpect(status().isBadRequest());
		this.mvc.perform(post(CHECK).param("fix", "sync-behaviors").param("step", "apply")).andExpect(status().isForbidden());
		this.mvc.perform(post(CHECK).with(csrf()).param("fix", "sync-behaviors").param("step", "apply").header("Origin", "http://evil.example"))
			.andExpect(status().isForbidden());
	}
}
