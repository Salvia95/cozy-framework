package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/**
 * 워크벤치의 htmx: 서버를 거치는 동작은 받은 화면에서 바뀐 영역만 골라 바꾼다. 서버는 htmx 요청에도 같은 응답(리다이렉트, 전체 화면)을
 * 돌려주고, 같은 폼과 링크가 스크립트 없이 전체 페이지로 동작한다.
 */
class WorkbenchHtmxTest {

	static final String URL = "/cozy/dev/workbench";

	/** 요소 하나의 이름과 속성 */
	record Tag(String name, Map<String, String> attributes) {

		String get(String attribute) {
			return this.attributes.get(attribute);
		}

		boolean has(String attribute) {
			return this.attributes.containsKey(attribute);
		}
	}

	private static final Pattern TAG = Pattern.compile("<([a-z]+)(\\s[^>]*)?>");

	private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:=\"([^\"]*)\"|='([^']*)')?");

	static List<Tag> tags(String html) {
		List<Tag> tags = new ArrayList<>();
		Matcher tag = TAG.matcher(html);
		while (tag.find()) {
			Map<String, String> attributes = new LinkedHashMap<>();
			if (tag.group(2) != null) {
				Matcher attribute = ATTRIBUTE.matcher(tag.group(2));
				while (attribute.find()) {
					String value = (attribute.group(2) != null) ? attribute.group(2) : attribute.group(3);
					attributes.put(attribute.group(1), (value != null) ? value : "");
				}
			}
			tags.add(new Tag(tag.group(1), attributes));
		}
		return tags;
	}

	/** {@code #a, #b} → [a, b] */
	static List<String> ids(String selectors) {
		List<String> ids = new ArrayList<>();
		for (String selector : selectors.split(",")) {
			String id = selector.strip();
			assertThat(id).as("htmx 선택자는 id 하나다: " + selectors).matches("#[a-z][a-z0-9-]*");
			ids.add(id.substring(1));
		}
		return ids;
	}

	/** htmx 요소의 요청 주소에서 조각(#)을 뗀다 */
	static String withoutAnchor(String url) {
		int hash = url.indexOf('#');
		return (hash < 0) ? url : url.substring(0, hash);
	}

	/**
	 * 화면 하나의 htmx 요소를 확인하고 돌려준다: 같은 주소의 href·action(스크립트 없을 때의 길)이 있고, 바꿀 영역의 id가 화면에 하나씩 있다.
	 */
	static List<Tag> htmx(String page, String html) {
		List<Tag> tags = tags(html);
		Map<String, Integer> idCounts = new LinkedHashMap<>();
		tags.stream().filter((t) -> t.has("id")).forEach((t) -> idCounts.merge(t.get("id"), 1, Integer::sum));
		List<Tag> requests = new ArrayList<>();
		for (Tag tag : tags) {
			if (tag.has("hx-get")) {
				requests.add(tag);
				String fallback = tag.name().equals("a") ? tag.get("href") : tag.get("action");
				assertThat(fallback).as(page + ": " + tag + "는 스크립트 없이도 같은 주소로 간다").isNotNull();
				assertThat(withoutAnchor(fallback)).as(page + ": " + tag).isEqualTo(tag.get("hx-get"));
			}
			if (tag.has("hx-post")) {
				requests.add(tag);
				assertThat(tag.name()).as(page + ": " + tag + "는 폼이다").isEqualTo("form");
				assertThat(tag.get("method")).as(page + ": " + tag).isEqualTo("post");
				assertThat(withoutAnchor(tag.get("action"))).as(page + ": " + tag).isEqualTo(tag.get("hx-post"));
				if (tag.has("hx-push-url")) {
					// 리다이렉트(PRG)하는 폼: 리다이렉트를 따라가 다음 화면에서 영역을 고른다
					assertThat(tag.get("hx-headers")).as(page + ": " + tag).isEqualTo("{\"X-Cozy-Redirect\": \"follow\"}");
				}
			}
			for (String attribute : List.of("hx-target", "hx-select", "hx-select-oob")) {
				if (tag.has(attribute)) {
					for (String id : ids(tag.get(attribute))) {
						assertThat(idCounts.getOrDefault(id, 0)).as(page + ": " + attribute + "의 #" + id + "가 화면에 하나 있다")
							.isEqualTo(1);
					}
				}
			}
			if (tag.has("hx-get") || tag.has("hx-post")) {
				assertThat(tag.get("hx-target")).as(page + ": " + tag + "는 바꿀 영역을 정한다").isNotNull();
				assertThat(tag.get("hx-select")).as(page + ": " + tag + "는 받은 화면에서 같은 영역을 고른다").isEqualTo(tag.get("hx-target"));
				assertThat(tag.get("hx-swap")).as(page + ": " + tag).isEqualTo("outerHTML");
			}
		}
		return requests;
	}

	@Nested
	@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-htmx-${random.uuid}", "cozy.runtime.open-browser=false" })
	@ActiveProfiles("dev")
	@AutoConfigureMockMvc
	@DirtiesContext
	class InAProject {

		static Path project;

		@DynamicPropertySource
		static void project(DynamicPropertyRegistry registry) throws IOException {
			project = WorkbenchCommandTest.copyOfThisProject("workbench-htmx-");
			registry.add("cozy.dev.workbench.project-dir", project::toString);
		}

		@Autowired
		MockMvc mvc;

		String body(MockHttpServletRequestBuilder request) throws Exception {
			return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		}

		@Test
		void everyHtmxRequestHasAPlainFallbackAndItsRegionsExist() throws Exception {
			for (String page : List.of(URL + "?spec=memo-list", URL + "?spec=memo-list&state=empty&view=fixture",
					URL + "?spec=memo-detail&view=template", URL + "/commands?recipe=crud", URL + "/search?q=메모")) {
				String html = body(get(page));
				assertThat(htmx(page, html)).as(page + "에 htmx 요청이 있다").isNotEmpty();
			}
		}

		@Test
		void stateAndWidthSwapThePreviewAndInspectorButKeepTheTree() throws Exception {
			List<Tag> requests = htmx("memo-list", body(get(URL).param("spec", "memo-list")));
			List<Tag> toolbar = requests.stream().filter((t) -> t.get("hx-get") != null && t.get("hx-get").contains("state=")).toList();
			assertThat(toolbar).as("상태 4개와 화면 너비 4개").hasSizeGreaterThanOrEqualTo(8).allSatisfy((t) -> {
				assertThat(t.get("hx-target")).isEqualTo("#workbench-center");
				assertThat(ids(t.get("hx-select-oob"))).containsExactlyInAnyOrder("workbench-inspector", "workbench-flash",
						"workbench-compare");
				assertThat(t.get("hx-push-url")).isEqualTo("true");
			});
		}

		@Test
		void fixtureFormsSwapTheInspectorAndRefreshThePreview() throws Exception {
			List<Tag> forms = htmx("fixture", body(get(URL).param("spec", "memo-list").param("view", "fixture")))
				.stream()
				.filter((t) -> t.has("hx-post") && !t.get("hx-post").endsWith("/arg"))
				.toList();
			assertThat(forms).extracting((t) -> t.get("hx-post"))
				.containsExactlyInAnyOrder(URL + "/fixture", URL + "/state");
			assertThat(forms).allSatisfy((t) -> {
				assertThat(t.get("hx-target")).isEqualTo("#workbench-inspector");
				assertThat(ids(t.get("hx-select-oob"))).containsExactlyInAnyOrder("workbench-center", "workbench-flash",
						"workbench-compare");
				assertThat(t.get("hx-push-url")).isEqualTo("true");
			});
		}

		@Test
		void argumentFormsAlsoSwapTheTreeAndTheErrorCount() throws Exception {
			String html = body(get(URL).param("spec", "memo-detail"));
			List<Tag> forms = htmx("memo-detail", html).stream().filter((t) -> (URL + "/arg").equals(t.get("hx-post"))).toList();
			assertThat(forms).as("문자열 매개변수가 있다").isNotEmpty().allSatisfy((t) -> {
				assertThat(t.get("hx-target")).isEqualTo("#workbench-inspector");
				// 미리보기는 다시 빌드해야 바뀌므로 가운데 칸은 그대로 둔다
				assertThat(ids(t.get("hx-select-oob"))).containsExactlyInAnyOrder("workbench-side", "workbench-flash",
						"workbench-errors");
			});
		}

		@Test
		void commandFormsSwapTheFormAndResultWithoutChangingTheAddress() throws Exception {
			List<Tag> forms = htmx("commands", body(get(URL + "/commands").param("recipe", "crud")))
				.stream()
				.filter((t) -> t.has("hx-post"))
				.toList();
			assertThat(forms).singleElement().satisfies((t) -> {
				assertThat(t.get("hx-target")).isEqualTo("#workbench-command");
				assertThat(t.has("hx-push-url")).isFalse();
			});
		}

		@Test
		void searchUpdatesOnlyTheResultsWhileTyping() throws Exception {
			List<Tag> forms = htmx("search", body(get(URL + "/search").param("q", "메모")))
				.stream()
				.filter((t) -> t.name().equals("form") && t.has("hx-get"))
				.toList();
			assertThat(forms).singleElement().satisfies((t) -> {
				assertThat(t.get("hx-target")).isEqualTo("#workbench-results");
				assertThat(t.get("hx-trigger")).contains("submit").contains("input");
				assertThat(t.get("hx-push-url")).isEqualTo("true");
			});
		}

		@Test
		void htmxPostsKeepTheRedirectSoTheAddressAndMessageAreTheSame() throws Exception {
			String page = body(get(URL).param("spec", "memo-list").param("view", "fixture"));
			Matcher hash = Pattern.compile("name=\"hash\" value=\"([0-9a-f]+)\"").matcher(page);
			assertThat(hash.find()).isTrue();
			for (boolean htmx : List.of(false, true)) {
				MockHttpServletRequestBuilder request = post(URL + "/fixture").with(csrf())
					.param("spec", "memo-list")
					.param("state", "default")
					.param("hash", hash.group(1));
				if (htmx) {
					request.header("HX-Request", "true").header("X-Cozy-Redirect", "follow");
				}
				String location = this.mvc.perform(request)
					.andExpect(status().is3xxRedirection())
					.andReturn()
					.getResponse()
					.getRedirectedUrl();
				assertThat(location).as("htmx=" + htmx).isEqualTo(URL + "?spec=memo-list&state=default&w=full&view=fixture");
			}
		}
	}
}
