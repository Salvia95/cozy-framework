package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;
import io.salvia95.cozy.cli.internal.Describe;

/**
 * describe의 화면 안 동작: 모듈의 동작 명세(behaviors[]와 붙인 곳), 화면의 요소마다 붙은 Alpine과 htmx 속성(screens[].bindings[]), 화면
 * 이벤트를 내는 곳과 받는 곳(uiEvents[]).
 */
class DescribeBehaviorsTest {

	static final String T = "src/main/resources/templates/";

	@TempDir
	Path dir;

	App app;

	@BeforeEach
	void app() throws IOException {
		this.app = App.of(this.dir).module("memo", "{}");
		this.app.file("src/main/resources/behaviors/memo.yml", """
				components:
				  bodyCount:
				    purpose: "본문을 쓰는 동안 글자 수를 보인다"
				    state:
				      length: "number@data-length"
				      limit: "number=5000"
				    computed:
				      label: "format('{length}자')"
				    actions:
				      count: "within('textarea', set(length, length(event.value)))"
				  selection:
				    purpose: "고른 행을 센다"
				    state:
				      ids: "list"
				    actions:
				      pick: "toggleItem(ids, event.data.id); when(gt(count(ids), 0), emit('memo:picked', ids))"
				      clear: "reset(ids)"
				    listens:
				      "memo:archived": "clear"
				""")
			.file(T + "memo/api/form.html", """
				<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
				<main>
				  <form id="f" th:hx-post="@{/memo}" hx-target="this">
				    <div th:replace="~{memo/internal/fields :: memoFields()}"></div>
				    <button type="button" class="btn" x-data="cozyCopy" data-copy-text="a" x-on:click="copy" x-cloak>복사</button>
				  </form>
				</main>
				</html>
				""")
			.file(T + "memo/internal/fields.html", """
				<div th:fragment="memoFields()">
				  <div class="mb-3" x-data="memoBodyCount" th:data-length="${bodyLength}" x-on:input="count">
				    <textarea name="body"></textarea>
				    <p class="form-hint" x-text="label" x-cloak>0자</p>
				  </div>
				</div>
				""")
			.file(T + "memo/api/list.html", """
				<main>
				  <section id="result" hx-get="/memo/rows" hx-trigger="memo:archived from:window, load">
				    <table x-data="memoSelection">
				      <tr x-on:click="pick"></tr>
				    </table>
				  </section>
				</main>
				""")
			.java("memo/api/MemoController.java", """
				package it.cozy.app.memo.api;

				class MemoController {
					void archive(jakarta.servlet.http.HttpServletResponse response) {
						response.setHeader("HX-Trigger", "{\\"memo:archived\\": true}");
					}
				}
				""");
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> describe() {
		Result result = CozyCliTest.run("describe", "--json", "--project", this.dir.toString());
		assertThat(result.code()).as(result.err()).isZero();
		// JSON은 YAML로도 읽힌다
		return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(result.out());
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> module(Map<String, Object> model) {
		return (Map<String, Object>) ((List<Object>) model.get("modules")).getFirst();
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> screen(Map<String, Object> model, String template) {
		return ((List<Map<String, Object>>) module(model).get("screens")).stream()
			.filter((s) -> template.equals(s.get("template")))
			.findFirst()
			.orElseThrow();
	}

	static Map<String, Object> attr(String attribute, Object value) {
		return Describe.ordered("attribute", attribute, "value", value);
	}

	@Test
	@SuppressWarnings("unchecked")
	void behaviorsListTheSpecAndWhereEachComponentIsAttached() {
		List<Map<String, Object>> behaviors = (List<Map<String, Object>>) module(describe()).get("behaviors");

		assertThat(behaviors).extracting((b) -> b.get("name")).containsExactly("memoBodyCount", "memoSelection");
		Map<String, Object> count = behaviors.get(0);
		assertThat(count.get("purpose")).isEqualTo("본문을 쓰는 동안 글자 수를 보인다");
		assertThat(count.get("state")).isEqualTo(List.of(
				Describe.ordered("name", "length", "type", "number", "initial", null, "from", "data-length"),
				Describe.ordered("name", "limit", "type", "number", "initial", "5000", "from", null)));
		assertThat(count.get("computed")).isEqualTo(List.of(Describe.ordered("name", "label", "expression", "format('{length}자')")));
		assertThat(count.get("actions")).isEqualTo(
				List.of(Describe.ordered("name", "count", "steps", List.of("within('textarea', set(length, length(event.value)))"))));
		assertThat(count.get("emits")).isEqualTo(List.of());
		// 붙인 곳: 모듈의 모든 템플릿에서, 그 컴포넌트가 감싸는 요소
		assertThat((List<Map<String, Object>>) count.get("attached")).extracting((a) -> a.get("template") + ":" + a.get("line"))
			.containsExactly("memo/internal/fields:2", "memo/internal/fields:4");

		Map<String, Object> selection = behaviors.get(1);
		// emit은 when 안에 있어도 찾는다
		assertThat(selection.get("emits")).isEqualTo(List.of(Describe.ordered("event", "memo:picked", "action", "pick")));
		assertThat(selection.get("listens")).isEqualTo(List.of(Describe.ordered("event", "memo:archived", "action", "clear")));
	}

	@Test
	@SuppressWarnings("unchecked")
	void screenBindingsShowEveryBoundElementWithTheScreenLineItComesThrough() {
		List<Map<String, Object>> bindings = (List<Map<String, Object>>) screen(describe(), "memo/api/form").get("bindings");

		// 문서 순서: 화면의 form, 넣은 fragment의 두 요소(화면에서는 넣은 줄 4), 화면의 버튼
		assertThat(bindings).extracting((b) -> b.get("template") + ":" + b.get("line") + "@" + b.get("screenLine"))
			.containsExactly("memo/api/form:3@3", "memo/internal/fields:2@4", "memo/internal/fields:4@4", "memo/api/form:5@5");

		Map<String, Object> form = bindings.get(0);
		assertThat(form.get("element")).isEqualTo("<form id=\"f\">");
		assertThat(form.get("component")).isNull();
		assertThat(form.get("alpine")).isEqualTo(List.of());
		assertThat(form.get("htmx")).isEqualTo(List.of(attr("th:hx-post", "@{/memo}"), attr("hx-target", "this")));

		Map<String, Object> root = bindings.get(1);
		assertThat(root.get("element")).isEqualTo("<div class=\"mb-3\">");
		assertThat(root.get("component")).isEqualTo("memoBodyCount");
		assertThat(root.get("root")).isEqualTo(true);
		assertThat(root.get("alpine")).isEqualTo(List.of(attr("x-data", "memoBodyCount"), attr("th:data-length", "${bodyLength}"),
				attr("x-on:input", "count")));

		Map<String, Object> hint = bindings.get(2);
		assertThat(hint.get("component")).isEqualTo("memoBodyCount");
		assertThat(hint.get("root")).isEqualTo(false);
		assertThat(hint.get("alpine")).isEqualTo(List.of(attr("x-text", "label"), attr("x-cloak", null)));

		// 프레임워크 컴포넌트도 같은 모양이다. data-*는 컴포넌트의 자리에서만 싣는다
		Map<String, Object> copy = bindings.get(3);
		assertThat(copy.get("element")).isEqualTo("<button class=\"btn\">");
		assertThat(copy.get("alpine")).isEqualTo(List.of(attr("x-data", "cozyCopy"), attr("data-copy-text", "a"), attr("x-on:click", "copy"),
				attr("x-cloak", null)));
	}

	@Test
	@SuppressWarnings("unchecked")
	void uiEventsJoinWhereEachScreenEventIsSentAndReceived() {
		List<Map<String, Object>> events = (List<Map<String, Object>>) describe().get("uiEvents");

		assertThat(events).extracting((e) -> e.get("event")).containsExactly("memo:archived", "memo:picked");
		Map<String, Object> archived = events.get(0);
		assertThat(archived.get("module")).isEqualTo("memo");
		// 서버의 HX-Trigger: 그 머리를 쓰는 Java 파일의 글자에서 이름을 찾는다
		assertThat(archived.get("emittedBy")).isEqualTo(
				List.of(Describe.ordered("kind", "server", "file", "src/main/java/it/cozy/app/memo/api/MemoController.java")));
		assertThat(archived.get("receivedBy")).isEqualTo(List.of(
				Describe.ordered("kind", "behavior", "module", "memo", "component", "memoSelection", "action", "clear"),
				Describe.ordered("kind", "htmx", "template", "memo/api/list", "line", 2)));

		Map<String, Object> picked = events.get(1);
		assertThat(picked.get("emittedBy")).isEqualTo(
				List.of(Describe.ordered("kind", "behavior", "module", "memo", "component", "memoSelection", "action", "pick")));
		assertThat(picked.get("receivedBy")).isEqualTo(List.of());
	}

	@Test
	void textOutputListsBehaviorsAndScreenEvents() {
		Result result = CozyCliTest.run("describe", "--project", this.dir.toString());

		assertThat(result.out())
			.contains("  화면 안의 동작 memoBodyCount — 본문을 쓰는 동안 글자 수를 보인다 · 붙인 곳 memo/internal/fields:2, memo/internal/fields:4\n")
			.contains("  화면 안의 동작 memoSelection — 고른 행을 센다 · 붙인 곳 memo/api/list:3, memo/api/list:4\n")
			.contains("화면 이벤트 memo:archived 내는 곳: src/main/java/it/cozy/app/memo/api/MemoController.java → 받는 곳: memoSelection.clear, "
					+ "memo/api/list:2(htmx)\n")
			.contains("화면 이벤트 memo:picked 내는 곳: memoSelection.pick → 받는 곳: 없음\n");
	}

	@Test
	@SuppressWarnings("unchecked")
	void aSpecThatCannotBeReadIsAProblemNotAFailure() throws IOException {
		this.app.file("src/main/resources/behaviors/memo.yml", "components:\n  bodyCount:\n    state: {}\n");
		Map<String, Object> model = describe();

		assertThat(module(model).get("behaviors")).isEqualTo(List.of());
		assertThat((List<Object>) model.get("problems")).anySatisfy((p) -> assertThat(String.valueOf(p))
			.contains("src/main/resources/behaviors/memo.yml").contains("purpose"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void unwiredBehaviorsAreLeftWorkInSlots() throws IOException {
		// 만든 JS가 있어야 C13이 컴포넌트를 안다. 붙인 곳은 있지만 memo:picked를 받는 곳이 없다
		Result sync = CozyCliTest.run("sync", "behaviors", "--project", this.dir.toString());
		assertThat(sync.code()).as(sync.err()).isZero();

		List<Map<String, Object>> slots = (List<Map<String, Object>>) describe().get("slots");

		assertThat(slots).singleElement().satisfies((slot) -> {
			assertThat(slot).containsEntry("check", "C13")
				.containsEntry("file", "src/main/resources/behaviors/memo.yml")
				.containsEntry("line", 12)
				.containsEntry("slot", "memoSelection → memo:picked");
			assertThat(String.valueOf(slot.get("what"))).startsWith("받는 곳이 없는 화면 이벤트입니다: memo:picked");
		});
	}
}
