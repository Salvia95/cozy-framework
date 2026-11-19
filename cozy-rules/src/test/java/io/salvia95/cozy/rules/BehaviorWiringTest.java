package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 검사 C13: 동작 명세의 컴포넌트가 화면에 붙었고, 동작을 부르는 곳과 화면 이벤트를 내고 받는 곳이 있는가.
 */
class BehaviorWiringTest {

	static final String SPEC = """
			components:
			  bodyCount:
			    purpose: "글자 수를 보인다"
			  selection:
			    purpose: "고른 행 수"
			""";

	@TempDir
	Path main;

	final List<TemplateFile> templates = new ArrayList<>();

	final List<ScriptFile> scripts = new ArrayList<>();

	@Test
	void anAttachedComponentWhoseActionsAreCalledPasses() {
		script("memo", line("memoBodyCount", List.of("count"), List.of(), Map.of()));
		template("memo/internal/fields.html", """
				<div x-data="memoBodyCount" x-on:input="count">
				  <p x-text="label"></p>
				</div>
				""");

		assertThat(check()).isEmpty();
	}

	@Test
	void aComponentThatNoTemplateAttachesFailsAtItsSpecLine() throws IOException {
		write("resources/behaviors/memo.yml", SPEC);
		script("memo", line("memoBodyCount", List.of("count"), List.of(), Map.of()) + "\n"
				+ line("memoSelection", List.of("pick"), List.of(), Map.of()));
		template("memo/api/list.html", "<div x-data=\"memoSelection\" x-on:click=\"pick\"></div>\n");
		// 다른 모듈의 템플릿도 붙인 곳으로 센다(R16이 따로 막는다). 프레임워크 템플릿은 세지 않는다
		this.templates.add(new TemplateFile("cozy/x.html", Origin.FRAMEWORK, "[cozy-ui] templates/cozy/x.html",
				"<div x-data=\"memoBodyCount\" x-on:input=\"count\"></div>"));

		assertThat(check()).singleElement().satisfies((v) -> {
			assertThat(v.ruleId()).isEqualTo("C13");
			assertThat(v.isError()).isTrue();
			assertThat(v.format()).startsWith("[C13] src/main/resources/behaviors/memo.yml:2 → memoBodyCount — 어느 템플릿에도 붙이지 않은 컴포넌트입니다: memoBodyCount")
				.contains("cozy attach memoBodyCount --at <템플릿>:<줄>")
				.contains("cozy remove behavior memo body-count");
			assertThat(v.command()).isNull();
		});
	}

	@Test
	void anActionThatNoElementCallsFailsUnlessAScreenEventCallsIt() {
		script("memo", line("memoSelection", List.of("pick", "clear", "reset"), List.of("memo:picked"), Map.of("memo:archived", "reset")));
		template("memo/api/list.html", """
				<div x-data="memoSelection" x-on:memo:picked.window="pick">
				  <button x-on:click="pick">고르기</button>
				  <span x-text="'clear'"></span>
				  <span x-text="other.clear"></span>
				</div>
				""");
		java("it/cozy/app/memo/api/MemoController.java", "response.setHeader(\"HX-Trigger\", \"memo:archived\");");

		assertThat(check()).singleElement().satisfies((v) -> {
			assertThat(v.reference()).isEqualTo("memoSelection.clear");
			assertThat(v.message()).startsWith("부르는 요소가 없는 동작입니다: clear")
				.contains("--on <이벤트>:clear")
				.contains("cozy add behavior memo selection --listen clear:<모듈>:<이벤트>")
				.contains("cozy remove behavior memo selection --action clear");
		});
	}

	@Test
	void anActionCalledOnlyOutsideItsComponentIsNotCalled() {
		script("memo", line("memoSelection", List.of("pick"), List.of(), Map.of()));
		template("memo/api/list.html", """
				<div x-data="memoSelection"></div>
				<button x-on:click="pick">고르기</button>
				""");

		assertThat(check()).singleElement().satisfies((v) -> assertThat(v.reference()).isEqualTo("memoSelection.pick"));
	}

	@Test
	void anEmittedEventNeedsAReceiver() {
		script("memo", line("memoSelection", List.of("pick"), List.of("memo:selection-changed", "memo:picked", "memo:cleared"), Map.of()));
		template("memo/api/list.html", """
				<div x-data="memoSelection" x-on:click="pick">
				  <section hx-get="/memo" th:hx-trigger="'memo:picked from:window, load'"></section>
				  <section data-hx-trigger="memo:cleared[detail.ids] from:window"></section>
				</div>
				""");

		assertThat(check()).singleElement().satisfies((v) -> {
			assertThat(v.reference()).isEqualTo("memoSelection → memo:selection-changed");
			assertThat(v.message()).startsWith("받는 곳이 없는 화면 이벤트입니다: memo:selection-changed")
				.contains("hx-trigger=\"memo:selection-changed from:window\"");
		});
	}

	@Test
	void anotherComponentsListenReceivesAnEmittedEvent() {
		script("memo", line("memoSelection", List.of("pick"), List.of("memo:picked"), Map.of()) + "\n"
				+ line("memoCounter", List.of("bump"), List.of(), Map.of("memo:picked", "bump")));
		template("memo/api/list.html", """
				<div x-data="memoSelection" x-on:click="pick"></div>
				<div x-data="memoCounter"></div>
				""");

		assertThat(check()).isEmpty();
	}

	@Test
	void aListenedEventNeedsAnEmitterAndTheServerMustNameItLiterally() {
		script("memo", line("memoSelection", List.of("clear"), List.of(), Map.of("memo:archived", "clear", "memo:saved", "clear")));
		template("memo/api/list.html", "<div x-data=\"memoSelection\"></div>\n");
		java("it/cozy/app/memo/api/MemoController.java", """
				class MemoController {
				  ResponseEntity<Void> archive() {
				    return ResponseEntity.ok().header("HX-Trigger", "{\\"memo:archived\\": {}}").build();
				  }
				  ResponseEntity<Void> save() {
				    return ResponseEntity.ok().header("HX-Trigger", "memo:" + "saved").build();
				  }
				}
				""");
		// 요청 머리를 읽기만 하는 파일의 글자는 내는 곳이 아니다
		java("it/cozy/app/memo/api/Reader.java", "String id = request.getHeader(\"HX-Trigger\"); String x = \"memo:saved\";");

		assertThat(check()).singleElement().satisfies((v) -> {
			assertThat(v.reference()).isEqualTo("memoSelection ← memo:saved");
			assertThat(v.message()).startsWith("내는 곳이 없는 화면 이벤트입니다: memo:saved")
				.contains("HX-Trigger의 값에 이름을 글자 그대로(\"memo:saved\")")
				.contains("cozy remove behavior memo selection --listen memo:saved");
		});
	}

	@Test
	void handWrittenAndFrameworkComponentsAreNotChecked() {
		this.scripts.add(new ScriptFile("memo/memo.js", Origin.APP, "static/memo/memo.js",
				"window.Alpine.data('memoOld', function () { return {}; });"));
		this.scripts.add(new ScriptFile("cozy/cozy.js", Origin.FRAMEWORK, "[cozy-ui] cozy/cozy.js",
				ComponentContract.line("cozyCopy", List.of("copy"), List.of("copy-text"))));

		assertThat(check()).isEmpty();
	}

	@Test
	void serverEventsAreTheNamesInStringsOfFilesThatSendTheHeader() {
		assertThat(ServerEvents.events("""
				response.addHeader("HX-Trigger-After-Settle", "memo:archived, memo:list-changed");
				String other = "wiki:saved"; // memo:not-a-string
				""")).containsExactly("memo:archived", "memo:list-changed", "wiki:saved");
		assertThat(ServerEvents.events("String id = request.getHeader(\"HX-Trigger\"); String x = \"memo:saved\";")).isEmpty();
		assertThat(ServerEvents.events("String x = \"memo:saved\";")).isEmpty();
	}

	static String line(String name, List<String> actions, List<String> emits, Map<String, String> listens) {
		return ComponentContract.line(name, actions, List.of(), actions, emits, listens);
	}

	void script(String module, String lines) {
		this.scripts.add(new ScriptFile(module + "/" + module + ".js", Origin.APP, "static/" + module + "/" + module + ".js", lines));
	}

	void template(String path, String content) {
		this.templates.add(new TemplateFile(path, Origin.APP, "templates/" + path, content));
	}

	void java(String path, String content) {
		try {
			write("java/" + path, content);
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	List<Violation> check() {
		return BehaviorWiring.check(this.templates, this.scripts, this.main, "src/main/");
	}

	void write(String path, String content) throws IOException {
		Path file = this.main.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}
}
