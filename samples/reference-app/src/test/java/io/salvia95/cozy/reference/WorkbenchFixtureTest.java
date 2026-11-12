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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.salvia95.cozy.test.CozyHtmlValidator;
import io.salvia95.cozy.ui.catalog.ScreenSpec;

/**
 * 워크벤치의 값 편집({@code view=fixture}): 화면 스펙의 상태 값을 고쳐 저장하면 그 값의 자리만 바뀌고, 다시 빌드하지 않아도 미리보기에 보인다.
 */
class WorkbenchFixtureTest {

	static final String URL = "/cozy/dev/workbench";

	/** enum과 날짜가 있는 화면 DTO. 앱에는 없어서 복사한 프로젝트의 화면 명세에서만 쓴다 */
	public enum Priority {
		LOW, NORMAL, HIGH
	}

	public record Task(long id, String title, Priority priority, java.time.LocalDate due, java.time.LocalDateTime remindAt, boolean done) {
	}

	public record TaskList(java.util.List<Task> rows) {
	}

	static final String TASKS = """
			# 할 일 (테스트 전용)
			screen: memo-tasks
			template: memo/api/tasks
			title: 할 일
			model:
			  tasks: io.salvia95.cozy.reference.WorkbenchFixtureTest.TaskList
			states:
			  default:
			    tasks:
			      rows:
			        - { id: 1, title: 보고서, priority: HIGH, due: "2026-11-30", remindAt: "2026-11-29T09:00", done: false }
			  empty:
			    tasks:
			      rows: []
			""";

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-fixture-${random.uuid}",
			"cozy.runtime.open-browser=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class InAProject {

		static Path project;

		static Path memoList;

		static String original;

		static Path tasks;

		@DynamicPropertySource
		static void project(DynamicPropertyRegistry registry) throws IOException {
			project = WorkbenchCommandTest.copyOfThisProject("workbench-fixture-");
			memoList = project.resolve("src/main/resources/specs/memo-list.yml");
			original = Files.readString(memoList, StandardCharsets.UTF_8);
			tasks = project.resolve("src/main/resources/specs/memo-tasks.yml");
			registry.add("cozy.dev.workbench.project-dir", project::toString);
		}

		@Autowired
		MockMvc mvc;

		@BeforeEach
		void restore() throws IOException {
			Files.writeString(memoList, original, StandardCharsets.UTF_8);
			Files.writeString(tasks, TASKS, StandardCharsets.UTF_8);
			Files.deleteIfExists(project.resolve("src/main/resources/specs/memo-list.yml.cozy-tmp"));
		}

		String body(MockHttpServletRequestBuilder request) throws Exception {
			return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		}

		String fixturePage(String state) throws Exception {
			return body(get(URL).param("spec", "memo-list").param("state", state).param("view", "fixture"));
		}

		/** 화면에 실린 파일 지문 */
		String hash(String html) {
			Matcher m = Pattern.compile("name=\"hash\" value=\"([0-9a-f]+)\"").matcher(html);
			assertThat(m.find()).as("폼에 hash가 있다").isTrue();
			return m.group(1);
		}

		/** 저장하고, 이어지는 화면(플래시 포함)을 돌려준다 */
		String save(String state, String hash, String... pathAndValue) throws Exception {
			MockHttpServletRequestBuilder request = post(URL + "/fixture").with(csrf())
				.param("spec", "memo-list")
				.param("state", state)
				.param("hash", hash);
			for (int i = 0; i < pathAndValue.length; i += 2) {
				request.param("v." + pathAndValue[i], pathAndValue[i + 1]);
			}
			MockHttpSession session = new MockHttpSession();
			MvcResult result = this.mvc.perform(request.session(session)).andExpect(status().is3xxRedirection()).andReturn();
			return body(get(result.getResponse().getRedirectedUrl()).session(session));
		}

		@Test
		void theFixtureViewListsEveryValueOfTheState() throws Exception {
			String html = fixturePage("default");
			assertThat(html).contains("name=\"v.result/rows/0/title\"")
				.contains("value=\"회의록 정리\"")
				.contains("name=\"v.result/rows/1/updatedAt\"")
				.contains("value=\"2026-11-05 17:20\"")
				.contains("name=\"v.filter/pinned\"")
				.contains("src/main/resources/specs/memo-list.yml")
				.contains("저장")
				.contains("상태 복제");
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
			assertThat(fixturePage("empty")).contains("name=\"v.result/page/totalElements\"").doesNotContain("v.result/rows/0");
		}

		@Test
		void objectsArePropertyTablesAndListsOfObjectsAreGridsWithInputsThatFitTheModelType() throws Exception {
			String html = fixturePage("default");
			assertThat(html).as("모델 구획: 이름과 타입").containsPattern("<code>result</code>\\s*<code class=\"cozy-workbench-id\"[^>]*>ListResult</code>");
			String grid = html.substring(html.indexOf("<code>rows</code>"), html.indexOf("<code>page</code>"));
			assertThat(grid).as("목록은 속성마다 열이 있는 그리드").contains("cozy-workbench-grid").contains("List&lt;Row&gt;").contains("2행");
			assertThat(grid).containsSubsequence("id</code>", "정수", "title</code>", "텍스트", "pinned</code>", "true/false",
					"updatedAt</code>");
			assertThat(grid).as("정수는 숫자 입력").containsPattern("type=\"number\" step=\"1\"[^>]*name=\"v.result/rows/0/id\" value=\"1\"");
			assertThat(grid).as("참·거짓은 체크박스. 끄면 숨은 false가 간다")
				.containsPattern("<input type=\"hidden\" name=\"v.result/rows/0/pinned\" value=\"false\">\\s*<input class=\"form-check-input m-0\" type=\"checkbox\" value=\"true\"[^>]*name=\"v.result/rows/0/pinned\"[^>]*checked")
				.contains("aria-label=\"rows 2행 제목\"");
			String page = html.substring(html.indexOf("<code>page</code>"));
			assertThat(page).as("객체는 속성 표").contains("cozy-workbench-fixture-props").contains("CozyPage").contains(">totalElements</code>");
			String filter = html.substring(html.indexOf("aria-label=\"모델 filter\""), html.indexOf("aria-label=\"모델 result\""));
			assertThat(filter).as("타입에는 있고 명세에는 없는 값").containsSubsequence("페이지", ">page</code>", ">없음</span>");
			assertThat(html).as("목록이 비면 열만 보인다").doesNotContain("행이 없습니다");
			assertThat(fixturePage("empty")).contains("행이 없습니다").contains(">updatedAt</code>");
		}

		@Test
		void nestedObjectsListsOfTextAndNullValuesAreShownByTheirType() throws Exception {
			String dashboard = body(get(URL).param("spec", "dashboard-index").param("state", "empty").param("view", "fixture"));
			assertThat(dashboard).containsSubsequence("<code>dashboard</code>", "DashboardView", "<code>memo</code>", "MemoSummary",
					"<code>activities</code>", "List&lt;ActivityItem&gt;");
			assertThat(dashboard).as("null은 빈 칸").containsPattern("name=\"v.dashboard/memo/latestTitle\" value=\"\" placeholder=\"null\"");
			String detail = body(get(URL).param("spec", "memo-detail").param("view", "fixture"));
			assertThat(detail).contains("List&lt;String&gt;").contains("name=\"v.memo/bodyLines/1\"").contains("value=\"둘째 줄\"");
			String form = body(get(URL).param("spec", "memo-form").param("state", "invalid").param("view", "fixture"));
			assertThat(form).as("검증 오류").containsSubsequence("검증 오류", "<code>_errors</code>", "name=\"v._errors/form/title\"");
			for (String html : java.util.List.of(dashboard, detail, form)) {
				assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
			}
		}

		@Test
		void aCheckboxSavesTrueWhenCheckedAndFalseWhenNot() throws Exception {
			String hash = hash(fixturePage("default"));
			MockHttpSession session = new MockHttpSession();
			this.mvc
				.perform(post(URL + "/fixture").with(csrf())
					.session(session)
					.param("spec", "memo-list")
					.param("state", "default")
					.param("hash", hash)
					.param("v.result/rows/0/pinned", "false")
					.param("v.result/rows/1/pinned", "false", "true"))
				.andExpect(status().is3xxRedirection());
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(original
				.replace("title: 회의록 정리, pinned: true", "title: 회의록 정리, pinned: false")
				.replace("title: 주간 보고, pinned: false", "title: 주간 보고, pinned: true"));
		}

		@Test
		void savingChangesOnlyThatValueAndThePreviewShowsItWithoutARebuild() throws Exception {
			String html = save("default", hash(fixturePage("default")), "result/rows/0/title", "분기 계획", "result/rows/0/pinned", "true",
					"filter/q", "");
			assertThat(html).contains("저장했습니다").contains("value=\"분기 계획\"");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8))
				.as("그 값의 자리만 바뀐다. 주석, 순서, 다른 값은 그대로다")
				.isEqualTo(original.replace("title: 회의록 정리", "title: 분기 계획"));

			String preview = body(get("/cozy/dev/catalog/screen").param("spec", "memo-list").param("state", "default"));
			assertThat(preview).contains("분기 계획").doesNotContain("회의록 정리");
			assertThat(body(get(URL).param("spec", "memo-list").param("view", "spec"))).contains("title: 분기 계획");

			// 저장한 스펙은 렌더링 검사가 읽는 것과 같은 방법으로 읽히고, 값이 모델 타입에 맞는다
			try (var in = Files.newInputStream(memoList)) {
				ScreenSpec saved = ScreenSpec.parse(in, "memo-list.yml", "specs/memo-list.yml");
				saved.verify();
				for (String state : saved.states().keySet()) {
					this.mvc.perform(get("/cozy/dev/catalog/screen").param("spec", "memo-list").param("state", state))
						.andExpect(status().isOk());
				}
			}
		}

		@Test
		void aValueThatDoesNotFitTheModelTypeIsRefusedAndNothingIsWritten() throws Exception {
			String html = save("default", hash(fixturePage("default")), "result/rows/0/pinned", "아마도", "result/rows/0/title",
					"적던 제목");
			assertThat(html).contains("저장하지 않았습니다. result/rows/0/pinned: true나 false만 씁니다 (적은 값: 아마도)")
				.doesNotContain("저장했습니다")
				.as("적은 값은 그대로 남는다")
				.contains("value=\"적던 제목\"")
				.contains("value=\"아마도\"");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(original);

			// 종류는 맞지만 모델 타입과 맞지 않는 값: 변환기의 메시지를 짧게 보인다
			String wrongType = save("default", hash(fixturePage("default")), "result/page/number", "99999999999999999999");
			assertThat(wrongType).contains("저장하지 않았습니다")
				.contains("모델 &#39;result&#39;")
				.doesNotContain("through reference chain")
				.doesNotContain("[cozy]");
			assertThat(wrongType).as("알림의 클래스 이름은 짧게")
				.contains("모델 &#39;result&#39;의 값이 ListResult와 맞지 않습니다")
				.doesNotContain("MemoViews$ListResult");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(original);
		}

		@Test
		void aFileChangedSinceTheFormWasShownIsNotOverwritten() throws Exception {
			String hash = hash(fixturePage("default"));
			String edited = original.replace("title: 주간 보고", "title: 손으로 고침");
			Files.writeString(memoList, edited, StandardCharsets.UTF_8);
			String html = save("default", hash, "result/rows/0/title", "분기 계획");
			assertThat(html).contains("저장하지 않았습니다").contains("파일이 바뀌었습니다").contains("value=\"분기 계획\"");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(edited);
		}

		@Test
		void savingWithoutChangesWritesNothing() throws Exception {
			String html = save("default", hash(fixturePage("default")), "result/rows/0/title", "회의록 정리");
			assertThat(html).contains("바뀐 값이 없습니다");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(original);
		}

		@Test
		void aStateCanBeDuplicatedUnderANewName() throws Exception {
			MockHttpSession session = new MockHttpSession();
			MvcResult result = this.mvc
				.perform(post(URL + "/state").with(csrf())
					.session(session)
					.param("spec", "memo-list")
					.param("from", "default")
					.param("name", "many")
					.param("hash", hash(fixturePage("default"))))
				.andExpect(status().is3xxRedirection())
				.andReturn();
			assertThat(result.getResponse().getRedirectedUrl()).contains("state=many").contains("view=fixture");
			String html = body(get(result.getResponse().getRedirectedUrl()).session(session));
			assertThat(html).contains("상태 many를 만들었습니다").contains("value=\"회의록 정리\"");
			String saved = Files.readString(memoList, StandardCharsets.UTF_8);
			assertThat(saved).startsWith(original.substring(0, original.indexOf("  empty:"))).contains("\n  many:\n");
			assertThat(saved.replaceFirst("(?s)  many:\\n.*?(?=  empty:)", "")).as("다른 곳은 그대로다").isEqualTo(original);
			this.mvc.perform(get("/cozy/dev/catalog/screen").param("spec", "memo-list").param("state", "many"))
				.andExpect(status().isOk());

			MvcResult again = this.mvc
				.perform(post(URL + "/state").with(csrf())
					.session(session)
					.param("spec", "memo-list")
					.param("from", "default")
					.param("name", "empty")
					.param("hash", hash(fixturePage("default"))))
				.andExpect(status().is3xxRedirection())
				.andReturn();
			assertThat(body(get(again.getResponse().getRedirectedUrl()).session(session))).contains("이미 있습니다");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(saved);
		}

		@Test
		void propertiesShowTheNameFromTheDtoDocsBeforeTheIdentifier() throws Exception {
			String html = fixturePage("default");
			String grid = html.substring(html.indexOf("id=\"fixture-result-rows\""));
			assertThat(grid).as("열 머리: DTO의 @param").containsSubsequence("번호", "<code class=\"cozy-workbench-id\">id</code>", "제목",
					"<code class=\"cozy-workbench-id\">title</code>", "고정", "수정한 시각");
			assertThat(grid).as("칸의 이름도 한글").contains("aria-label=\"rows 1행 제목\"");
			String filter = html.substring(html.indexOf("aria-label=\"모델 filter\""), html.indexOf("aria-label=\"모델 result\""));
			assertThat(filter).as("폼 클래스: 필드의 Javadoc").containsSubsequence("검색어", "<code class=\"cozy-workbench-id\">q</code>");
			assertThat(html).as("프레임워크 타입").containsSubsequence("전체 건수", "<code class=\"cozy-workbench-id\">totalElements</code>");
			String dashboard = body(get(URL).param("spec", "dashboard-index").param("view", "fixture"));
			assertThat(dashboard).containsSubsequence("가장 최근에 만든 메모의 제목", "latestTitle");
		}

		/** 행 버튼을 누른다: 적던 값과 함께 보낸다 */
		MvcResult row(String state, String row, String... pathAndValue) throws Exception {
			MockHttpServletRequestBuilder request = post(URL + "/fixture").with(csrf())
				.param("spec", "memo-list")
				.param("state", state)
				.param("hash", hash(fixturePage(state)))
				.param("row", row);
			for (int i = 0; i < pathAndValue.length; i += 2) {
				request.param("v." + pathAndValue[i], pathAndValue[i + 1]);
			}
			return this.mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
		}

		@Test
		void rowsAreAddedDuplicatedAndDeletedAndTheEditsTypedSoFarAreKept() throws Exception {
			String html = fixturePage("default");
			assertThat(html).contains("value=\"duplicate:result/rows/0\"").contains("value=\"delete:result/rows/1\"").contains("value=\"add:result/rows\"");
			String form = html.substring(html.indexOf("action=\"/cozy/dev/workbench/fixture\""));
			assertThat(form.substring(form.indexOf("<button"))).as("Enter는 저장: 폼의 첫 제출 버튼에는 행 작업이 없다")
				.startsWith("<button type=\"submit\" class=\"d-none\"");

			MvcResult added = row("default", "add:result/rows", "result/rows/0/title", "분기 계획");
			assertThat(added.getResponse().getRedirectedUrl()).endsWith("&w=full&view=fixture#fixture-result-rows").doesNotContain("&amp;");
			assertThat(fixturePage("default")).as("앵커는 그리드의 제목").containsPattern("<h5 class=\"cozy-workbench-fixture-subtitle\" id=\"fixture-result-rows\">");
			String saved = Files.readString(memoList, StandardCharsets.UTF_8);
			assertThat(saved).as("적던 값도 반영된다").contains("title: 분기 계획")
				.contains("        - { id: 2, title: 주간 보고, pinned: false, updatedAt: \"2026-11-05 17:20\" }\n        - { id: 0, title: \"\", pinned: false, updatedAt: \"\" }\n      page:");

			row("default", "delete:result/rows/2");
			row("default", "duplicate:result/rows/0");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).contains(
					"        - { id: 1, title: 분기 계획, pinned: true, updatedAt: \"2026-11-06 10:00\" }\n        - { id: 1, title: 분기 계획, pinned: true, updatedAt: \"2026-11-06 10:00\" }\n");

			row("empty", "add:result/rows");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).as("빈 목록에 첫 행").contains(
					"  empty:\n    filter: { q: \"\", pinned: false }\n    result:\n      rows:\n        - { id: 0, title: \"\", pinned: false, updatedAt: \"\" }\n");
			for (String state : java.util.List.of("default", "empty")) {
				this.mvc.perform(get("/cozy/dev/catalog/screen").param("spec", "memo-list").param("state", state)).andExpect(status().isOk());
			}
		}

		@Test
		void aRowChangeThatBreaksTheModelIsRefusedAndBadRowValuesAreRejected() throws Exception {
			// CozyPage는 page가 1 이상이어야 한다: 목록이 아니어서 행이 없다
			MockHttpSession session = new MockHttpSession();
			MvcResult result = this.mvc
				.perform(post(URL + "/fixture").with(csrf())
					.session(session)
					.param("spec", "memo-list")
					.param("state", "default")
					.param("hash", hash(fixturePage("default")))
					.param("row", "add:result/page"))
				.andExpect(status().is3xxRedirection())
				.andReturn();
			assertThat(body(get(result.getResponse().getRedirectedUrl()).session(session))).contains("저장하지 않았습니다").contains("목록이 아닙니다");
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(original);
			this.mvc
				.perform(post(URL + "/fixture").with(csrf())
					.param("spec", "memo-list")
					.param("state", "default")
					.param("hash", hash(fixturePage("default")))
					.param("row", "drop:result/rows/0"))
				.andExpect(status().isBadRequest());
		}

		@Test
		void enumsAreDropdownsAndDatesAreDateInputs() throws Exception {
			String html = body(get(URL).param("spec", "memo-tasks").param("view", "fixture"));
			assertThat(html).containsPattern("<select[^>]*name=\"v.tasks/rows/0/priority\"")
				.contains("<option value=\"LOW\">LOW</option>")
				.contains("<option value=\"HIGH\" selected=\"selected\">HIGH</option>")
				.containsPattern("type=\"date\"[^>]*name=\"v.tasks/rows/0/due\" value=\"2026-11-30\"")
				.containsPattern("type=\"datetime-local\" step=\"1\"[^>]*name=\"v.tasks/rows/0/remindAt\" value=\"2026-11-29T09:00\"")
				.containsSubsequence("<code>priority</code>", "Priority", "<code>due</code>", "날짜", "<code>remindAt</code>", "날짜와 시각");
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();

			MockHttpServletRequestBuilder request = post(URL + "/fixture").with(csrf())
				.param("spec", "memo-tasks")
				.param("state", "default")
				.param("hash", hash(html))
				.param("v.tasks/rows/0/priority", "LOW")
				.param("v.tasks/rows/0/due", "2026-12-01")
				.param("v.tasks/rows/0/remindAt", "2026-11-29T09:00:00");
			this.mvc.perform(request).andExpect(status().is3xxRedirection());
			assertThat(Files.readString(tasks, StandardCharsets.UTF_8)).as("같은 시각은 바꾸지 않는다")
				.isEqualTo(TASKS.replace("priority: HIGH", "priority: LOW").replace("due: \"2026-11-30\"", "due: \"2026-12-01\""));

			MockHttpSession session = new MockHttpSession();
			MvcResult refused = this.mvc
				.perform(post(URL + "/fixture").with(csrf())
					.session(session)
					.param("spec", "memo-tasks")
					.param("state", "default")
					.param("hash", hash(body(get(URL).param("spec", "memo-tasks").param("view", "fixture"))))
					.param("v.tasks/rows/0/priority", "LATER"))
				.andExpect(status().is3xxRedirection())
				.andReturn();
			assertThat(body(get(refused.getResponse().getRedirectedUrl()).session(session)))
				.contains("tasks/rows/0/priority: 다음 가운데 하나만 씁니다: LOW, NORMAL, HIGH (적은 값: LATER)")
				.as("적던 값은 글자 칸으로 남는다")
				.containsPattern("type=\"text\"[^>]*name=\"v.tasks/rows/0/priority\" value=\"LATER\"");

			this.mvc
				.perform(post(URL + "/fixture").with(csrf())
					.param("spec", "memo-tasks")
					.param("state", "empty")
					.param("hash", hash(body(get(URL).param("spec", "memo-tasks").param("state", "empty").param("view", "fixture"))))
					.param("row", "add:tasks/rows"))
				.andExpect(status().is3xxRedirection());
			assertThat(Files.readString(tasks, StandardCharsets.UTF_8)).as("타입의 기본값으로 첫 행")
				.containsPattern("      rows:\n        - \\{ id: 0, title: \"\", priority: LOW, due: \"\\d{4}-\\d{2}-\\d{2}\", remindAt: \"[^\"]+T09:00\", done: false \\}\n");
		}


		@Test
		void writesNeedTheCsrfTokenTheSameOriginAndAKnownSpec() throws Exception {
			String hash = hash(fixturePage("default"));
			this.mvc.perform(post(URL + "/fixture").param("spec", "memo-list").param("state", "default").param("hash", hash))
				.andExpect(status().isForbidden());
			this.mvc
				.perform(post(URL + "/fixture").with(csrf())
					.header("Origin", "http://evil.example")
					.param("spec", "memo-list")
					.param("state", "default")
					.param("hash", hash)
					.param("v.result/rows/0/title", "x"))
				.andExpect(status().isForbidden());
			this.mvc
				.perform(post(URL + "/fixture").with(csrf())
					.param("spec", "../../pom")
					.param("state", "default")
					.param("hash", hash)
					.param("v.result/rows/0/title", "x"))
				.andExpect(status().isNotFound());
			assertThat(Files.readString(memoList, StandardCharsets.UTF_8)).isEqualTo(original);
		}
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-fixture-${random.uuid}",
			"cozy.runtime.open-browser=false", "cozy.dev.workbench.project-dir=target/no-such-project" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class WithoutAProject {

		@Autowired
		MockMvc mvc;

		@Test
		void valuesCannotBeEdited() throws Exception {
			String html = this.mvc.perform(get(URL).param("spec", "memo-list").param("view", "fixture"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(html).contains("앱 프로젝트 폴더").doesNotContain("name=\"hash\"");
			this.mvc
				.perform(post(URL + "/fixture").with(csrf()).param("spec", "memo-list").param("state", "default").param("hash", "x"))
				.andExpect(status().isConflict());
		}
	}
}
