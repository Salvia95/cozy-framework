package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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

import io.salvia95.cozy.test.CozyHtmlValidator;

/**
 * 워크벤치의 검사({@code /cozy/dev/workbench/check})와 지도({@code /cozy/dev/workbench/map}). 둘 다 cozy 명령({@code check},
 * {@code describe})의 결과를 그대로 보인다.
 */
class WorkbenchCheckTest {

	/** 이 앱의 소스를 복사해 위반 셋을 넣은 프로젝트: 선언 없는 모듈 사용(R3 두 건), 색 클래스(R14), 채우지 않은 빈칸(C9) */
	static Path projectWithViolations() throws IOException {
		Path dir = Files.createTempDirectory(Path.of("target"), "workbench-violations-").toAbsolutePath();
		Files.copy(Path.of("pom.xml"), dir.resolve("pom.xml"));
		Path src = Path.of("src");
		try (Stream<Path> walk = Files.walk(src)) {
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
		edit(dir.resolve("src/main/resources/templates/memo/api/list.html"),
				"class=\"text-secondary text-nowrap\" th:text=\"${row.updatedAt}\"",
				"class=\"text-danger text-nowrap\" th:text=\"${row.updatedAt}\"");
		edit(dir.resolve("src/main/java/io/salvia95/cozy/reference/dashboard/package-info.java"),
				"allowedDependencies = { \"memo :: spi\", \"activity :: spi\" }", "allowedDependencies = {}");
		Path summary = dir.resolve("src/main/resources/templates/memo/spi/summary.html");
		Files.writeString(summary, Files.readString(summary, StandardCharsets.UTF_8)
				+ "<!-- cozy:slot fragment-body — 본문을 채우세요 -->\n", StandardCharsets.UTF_8);
		return dir;
	}

	static void edit(Path file, String from, String to) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertThat(text).as(file.toString()).contains(from);
		Files.writeString(file, text.replace(from, to), StandardCharsets.UTF_8);
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-check-${random.uuid}",
			"cozy.runtime.open-browser=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class CleanApp {

		@Autowired
		MockMvc mvc;

		String page(String path) throws Exception {
			return this.mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		}

		@Test
		void theCheckPageSaysNothingIsWrong() throws Exception {
			String html = page("/cozy/dev/workbench/check");
			assertThat(html).contains("위반 없음").contains("mvn -o verify").doesNotContain("수정 명령");
		}

		@Test
		void everyPageLinksToTheOthers() throws Exception {
			for (String path : new String[] { "/cozy/dev/workbench", "/cozy/dev/workbench/check", "/cozy/dev/workbench/map" }) {
				assertThat(page(path)).as(path)
					.contains("href=\"/cozy/dev/workbench\"")
					.contains("href=\"/cozy/dev/workbench/check\"")
					.contains("href=\"/cozy/dev/workbench/map\"");
			}
		}

		@Test
		void theMapDrawsModulesAsADiagramWithDependenciesAndEvents() throws Exception {
			String html = page("/cozy/dev/workbench/map");
			assertThat(html).contains("<svg class=\"cozy-workbench-graph\"")
				.contains("href=\"/cozy/dev/workbench/map?module=memo\"")
				.contains("href=\"/cozy/dev/workbench/map?module=activity\"")
				.contains("href=\"/cozy/dev/workbench/map?module=dashboard\"")
				.contains(">MemoCreated</text>")
				.contains("cozy-workbench-graph-event")
				.contains("의존: 화살표가 가리키는 모듈을 참조")
				.as("선택한 모듈이 없으면 앱의 속성")
				.contains("io.salvia95.cozy.reference")
				.contains(">기본 패키지<")
				.contains("미구현 항목이 없습니다")
				.doesNotContain("cozy-workbench-graph-selected");
			assertThat(html.split("cozy-workbench-graph-node\"", -1).length - 1).as("모듈마다 마디 하나").isEqualTo(3);
			assertThat(html.split("class=\"cozy-workbench-graph-edge", -1).length - 1).as("의존 3 + 이벤트 1 + 범례 2").isEqualTo(6);
		}

		@Test
		void selectingAModuleShowsItsContractsEventsAndScreens() throws Exception {
			String memo = page("/cozy/dev/workbench/map?module=memo");
			assertThat(memo).contains("cozy-workbench-graph-selected")
				.contains("cozy-workbench-graph-on")
				.contains(">참조하는 모듈<")
				.contains("href=\"/cozy/dev/workbench/map?module=activity\" >activity<".replace("\" >", "\">"))
				.contains("MemoFacade")
				.contains("long create(String title, String body, boolean pinned)")
				.contains("MemoFinder")
				.contains("MemoItem(")
				.contains("발행 위치")
				.contains("src/main/java/io/salvia95/cozy/reference/memo/internal/app/command/MemoFacadeImpl.java")
				.contains("activity.MemoCreatedListener")
				.contains("memo/summary :: memoSummary(summary)")
				.as("화면은 워크벤치의 그 화면으로 이어진다")
				.contains("/cozy/dev/workbench?spec=memo-list");
			String activity = page("/cozy/dev/workbench/map?module=activity");
			assertThat(activity).contains("MemoCreatedListener").contains("memo.MemoCreated").contains("cozy-workbench-graph-dim");
		}

		@Test
		void theTableViewListsDependenciesAndReceivedEvents() throws Exception {
			String html = page("/cozy/dev/workbench/map?view=table&module=dashboard");
			assertThat(html).contains("cozy-workbench-matrix")
				.contains("cozy-workbench-matrix-selected")
				.contains(">의존<")
				.contains("이벤트 수신: MemoCreated")
				.doesNotContain("<svg class=\"cozy-workbench-graph\"");
			this.mvc.perform(get("/cozy/dev/workbench/map?module=nothing")).andExpect(status().isNotFound());
			this.mvc.perform(get("/cozy/dev/workbench/map?view=chart")).andExpect(status().isBadRequest());
		}

		@Test
		void thePagesAreValidHtml() throws Exception {
			CozyHtmlValidator validator = new CozyHtmlValidator();
			for (String path : new String[] { "/cozy/dev/workbench/check", "/cozy/dev/workbench/map" }) {
				assertThat(validator.validate(page(path)).stream().filter(CozyHtmlValidator.Message::isError)).as(path)
					.isEmpty();
			}
		}
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-check-${random.uuid}",
			"cozy.runtime.open-browser=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class AppWithViolations {

		@DynamicPropertySource
		static void project(DynamicPropertyRegistry registry) throws IOException {
			Path dir = projectWithViolations();
			registry.add("cozy.dev.workbench.project-dir", dir::toString);
		}

		@Autowired
		MockMvc mvc;

		String page(String path) throws Exception {
			return this.mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		}

		@Test
		void theCheckPageShowsEveryViolationWithItsFixCommand() throws Exception {
			String html = page("/cozy/dev/workbench/check");
			assertThat(html).contains("오류 4건")
				.contains("오류 R3")
				.contains("다른 모듈 템플릿 참조")
				.contains("dashboard의 allowedDependencies에 memo가 없습니다")
				.contains("cozy link dashboard --uses memo")
				.contains("cozy link dashboard --uses activity")
				.contains("R14")
				.contains("앱 화면은 색을 직접 고르지 않습니다")
				.contains("C9")
				.contains("본문을 채우세요")
				.contains("src/main/resources/templates/memo/spi/summary.html");
			assertThat(html).as("화면 템플릿의 위반은 워크벤치의 소스 보기 그 줄로 이어진다")
				.contains("/cozy/dev/workbench?spec=memo-list&amp;view=template#L50")
				.contains("/cozy/dev/workbench?spec=dashboard-index&amp;view=template#L9");
			assertThat(new CozyHtmlValidator().validate(html).stream().filter(CozyHtmlValidator.Message::isError)).isEmpty();
		}

		@Test
		void theScreenListCountsViolationsPerScreen() throws Exception {
			String html = page("/cozy/dev/workbench?spec=memo-list");
			assertThat(html).contains("data-cozy-violations=\"memo-list:1\"")
				.contains("data-cozy-violations=\"dashboard-index:2\"")
				.doesNotContain("data-cozy-violations=\"memo-form:")
				.as("검사 탭에 전체 오류 수")
				.contains("data-cozy-errors=\"4\"");
		}

		@Test
		void theMapListsOpenSlots() throws Exception {
			String html = page("/cozy/dev/workbench/map");
			assertThat(html).contains("미구현 항목 1건")
				.contains("src/main/resources/templates/memo/spi/summary.html")
				.contains("fragment-body")
				.contains("본문을 채우세요");
		}
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-check-${random.uuid}",
			"cozy.runtime.open-browser=false", "cozy.dev.workbench.project-dir=target/no-such-project" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class WithoutAProject {

		@Autowired
		MockMvc mvc;

		@Test
		void thePagesSayWhyTheyCannotCheck() throws Exception {
			for (String path : new String[] { "/cozy/dev/workbench/check", "/cozy/dev/workbench/map" }) {
				String html = this.mvc.perform(get(path))
					.andExpect(status().isOk())
					.andReturn()
					.getResponse()
					.getContentAsString();
				assertThat(html).as(path).contains("앱 프로젝트 폴더").doesNotContain("위반 없음");
			}
		}
	}
}
