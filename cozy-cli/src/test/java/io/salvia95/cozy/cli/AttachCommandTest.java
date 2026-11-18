package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * {@code cozy attach}: 템플릿의 요소 하나에 컴포넌트를 붙이고 뗀다. 붙일 이름은 컴포넌트가 내놓는 이름인지 확인한다.
 * {@code cozy remove behavior}, {@code cozy rename behavior}: 명세를 고치고 붙인 곳도 함께 고친다.
 */
class AttachCommandTest {

	static final String TEMPLATE = "src/main/resources/templates/memo/api/edit.html";

	static final String SPEC = "src/main/resources/behaviors/memo.yml";

	static final String SCRIPT = "src/main/resources/static/memo/memo.js";

	static final String EDIT = """
			<!DOCTYPE html>
			<html xmlns:th="http://www.thymeleaf.org">
			<body>
			<div class="card-body">
			  <div>
			    <textarea class="form-control" name="body" aria-label="본문"></textarea>
			    <div class="form-hint"></div>
			  </div>
			  <button type="button" class="btn"
			          th:text="${title}">제목</button>
			</div>
			</body>
			</html>
			""";

	@TempDir
	Path dir;

	@BeforeEach
	void app() throws IOException {
		App.of(this.dir).module("memo", "{}").module("wiki", "{}");
		write(TEMPLATE, EDIT);
		Result added = run("add", "behavior", "memo", "body-count", "--purpose", "본문을 쓰는 동안 글자 수를 보인다", "--state",
				"length:number@data-length", "--state", "limit:number=5000@data-limit", "--state", "draft:text", "--computed",
				"label:format('{length} / {limit}자', 'ko-KR')", "--computed", "over:gt(length, limit)", "--action",
				"count:within('textarea', set(length, length(event.value)))");
		assertThat(added.code()).as(added.err()).isZero();
	}

	Result run(String... args) {
		String[] all = new String[args.length + 2];
		System.arraycopy(args, 0, all, 0, args.length);
		all[args.length] = "--project";
		all[args.length + 1] = this.dir.toString();
		return CozyCliTest.run(all);
	}

	String read(String file) throws IOException {
		return Files.readString(this.dir.resolve(file), StandardCharsets.UTF_8);
	}

	void write(String file, String content) throws IOException {
		Path path = this.dir.resolve(file);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	Result attachRoot() {
		return run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--data", "length=${bodyLength}", "--data", "limit=5000",
				"--on", "input:count");
	}

	@Nested
	class Attach {

		@Test
		void theRootTakesXDataAndDataAndInnerElementsTakeBindings() throws IOException {
			Result root = attachRoot();
			Result hint = run("attach", "memoBodyCount", "--at", "templates/memo/api/edit.html:7", "--text", "label", "--bind",
					"class:over", "--cloak");

			assertThat(root.code()).as(root.err()).isZero();
			assertThat(root.out()).contains("바꿈  " + TEMPLATE, "x-data=\"memoBodyCount\"", "th:data-length=\"${bodyLength}\"");
			assertThat(hint.code()).as(hint.err()).isZero();
			assertThat(read(TEMPLATE)).contains("""
					  <div x-data="memoBodyCount" th:data-length="${bodyLength}" data-limit="5000" x-on:input="count">
					    <textarea class="form-control" name="body" aria-label="본문"></textarea>
					    <div class="form-hint" x-bind:class="over" x-text="label" x-cloak></div>
					""");
			Result check = run("check");
			assertThat(check.code()).as(check.out()).isZero();
		}

		@Test
		void runningTheSameAttachAgainChangesNothing() throws IOException {
			attachRoot();
			String once = read(TEMPLATE);

			Result again = attachRoot();

			assertThat(again.code()).as(again.err()).isZero();
			assertThat(again.out()).contains("그대로", "같은 것을 붙였음");
			assertThat(read(TEMPLATE)).isEqualTo(once);
		}

		@Test
		void aChangedValueReplacesTheOldOneInPlace() throws IOException {
			attachRoot();

			Result result = run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--data", "limit=200");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("바꿈 data-limit: \"5000\" → \"200\"");
			assertThat(read(TEMPLATE)).contains("th:data-length=\"${bodyLength}\" data-limit=\"200\" x-on:input=\"count\"");
		}

		@Test
		void aDryRunWritesNothing() throws IOException {
			Result result = run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--on", "input:count", "--dry-run");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("x-on:input=\"count\"");
			assertThat(read(TEMPLATE)).isEqualTo(EDIT);
		}

		@Test
		void namesTheComponentDoesNotHaveAreRefused() throws IOException {
			attachRoot();

			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--text", "lable").err())
				.contains("memoBodyCount에 없는 상태나 계산입니다: lable", "있는 것: length, limit, draft, label, over");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--on", "click:label").err())
				.contains("memoBodyCount에 없는 동작입니다: label", "있는 것: count");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:6", "--model", "length").err())
				.contains("x-model에 쓸 상태(text, bool)가 아닙니다: length", "있는 것: draft");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--data", "size=1").err())
				.contains("data-size를 읽지 않습니다", "data-length, data-limit");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--text", "label.length").err())
				.contains("값은 이름이나 이름('글자')입니다");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--data", "limit=1").err())
				.contains("--data는 컴포넌트를 붙인 요소");
			assertThat(read(TEMPLATE)).doesNotContain("lable", "size");
		}

		@Test
		void theLineMustStartExactlyOneElementThatSurvivesRendering() throws IOException {
			write(TEMPLATE, EDIT.replace("<div class=\"form-hint\"></div>",
					"<div th:replace=\"~{cozy/component/field :: field('a', 'b', 'text')}\"></div><span></span>"));

			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:10", "--on", "input:count").err())
				.contains("memo/api/edit:10에서 시작하는 요소가 0개입니다");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--on", "input:count").err())
				.contains("시작하는 요소가 2개입니다");
			write(TEMPLATE, EDIT.replace("<div class=\"form-hint\"></div>", "<div th:replace=\"~{x :: y}\"></div>"));
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--on", "input:count").err())
				.contains("그려질 때 다른 것으로 바뀌어");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/nope:1", "--on", "input:count").err())
				.contains("templates/memo/api/nope.html가 없습니다");
		}

		@Test
		void anElementCarriesOneComponentAndAModuleComponentStaysInItsModule() throws IOException {
			attachRoot();
			write("src/main/resources/templates/wiki/api/list.html", "<div></div>\n");

			assertThat(run("attach", "cozyCopy", "--at", "memo/api/edit:5", "--on", "click:copy").err())
				.contains("이미 memoBodyCount를 붙였습니다", "--detach");
			assertThat(run("attach", "memoBodyCount", "--at", "wiki/api/list:1", "--on", "input:count").err())
				.contains("memo 템플릿(templates/memo/…)에만 붙입니다");
			assertThat(run("attach", "memoNothing", "--at", "memo/api/edit:5").err()).contains("컴포넌트 memoNothing가 없습니다",
					"cozy add behavior");
		}

		@Test
		void frameworkComponentsAreCheckedAgainstTheFrameworkSpec() throws IOException {
			Result copy = run("attach", "cozyCopy", "--at", "memo/api/edit:9", "--data", "copy-text=${title}", "--on", "click:copy",
					"--cloak");

			assertThat(copy.code()).as(copy.err()).isZero();
			assertThat(read(TEMPLATE)).contains("""
					  <button type="button" class="btn"
					          th:text="${title}" x-data="cozyCopy" th:data-copy-text="${title}" x-on:click="copy" x-cloak>제목</button>
					""");
			assertThat(run("attach", "cozyDisclosure", "--at", "memo/api/edit:4", "--on", "click:toggle").err())
				.contains("toggle는 글자 인자 하나를 받습니다");
			Result toggle = run("attach", "cozyDisclosure", "--at", "memo/api/edit:4", "--data", "disclosure-closed=filter", "--on",
					"click:toggle('filter')", "--bind", "aria-expanded:isOpen('filter')");
			assertThat(toggle.code()).as(toggle.err()).isZero();
			assertThat(run("attach", "cozyCopy", "--at", "memo/api/edit:9", "--on", "click:copy('x')").err())
				.contains("copy는 인자를 받지 않습니다");
			assertThat(run("attach", "cozyNothing", "--at", "memo/api/edit:4").err()).contains("프레임워크 컴포넌트 cozyNothing가 없습니다",
					"cozyCopy");
			// 이 앱의 memoBodyCount는 아직 붙이지 않았다(C13). 프레임워크 컴포넌트의 이름은 R16이 보고 통과한다
			Result check = run("check");
			assertThat(check.out()).doesNotContain("[R16]").contains("[C13] src/main/resources/behaviors/memo.yml:3 → memoBodyCount — ");
			attachRoot();
			assertThat(run("check").code()).as(run("check").out()).isZero();
		}

		@Test
		void theCheckFailsWhenAHandEditedBindingNamesSomethingTheComponentLacks() throws IOException {
			attachRoot();
			write(TEMPLATE, read(TEMPLATE).replace("<div class=\"form-hint\">", "<div class=\"form-hint\" x-text=\"lable\">"));

			Result check = run("check");

			assertThat(check.code()).isNotZero();
			assertThat(check.out()).contains("R16", "'lable'는 감싸는 컴포넌트 memoBodyCount에 없습니다");
		}
	}

	@Nested
	class Detach {

		@Test
		void detachingNamedBindingsRemovesOnlyThose() throws IOException {
			attachRoot();

			Result result = run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--data", "limit=x", "--detach");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(read(TEMPLATE)).contains("<div x-data=\"memoBodyCount\" th:data-length=\"${bodyLength}\" x-on:input=\"count\">");
		}

		@Test
		void theRootIsDetachedLastAndTheTemplateReturnsToWhereItWas() throws IOException {
			attachRoot();
			run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--text", "label", "--cloak");
			run("attach", "cozyCopy", "--at", "memo/api/edit:9", "--data", "copy-text=${title}", "--on", "click:copy", "--cloak");

			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--detach").err())
				.contains("안쪽 요소에 붙인 것이 남아 있습니다", "[7]");
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--detach").code()).isZero();
			assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--detach").code()).isZero();
			assertThat(run("attach", "cozyCopy", "--at", "memo/api/edit:9", "--detach").code()).isZero();
			assertThat(read(TEMPLATE)).isEqualTo(EDIT);
		}
	}

	@Nested
	class RemoveAndRename {

		@Test
		void removingAComponentDetachesItEverywhereAndDropsTheEmptySpec() throws IOException {
			attachRoot();
			run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--text", "label", "--cloak");

			Result result = run("remove", "behavior", "memo", "body-count");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("지움  " + SPEC, "지움  " + SCRIPT, "뗌 memoBodyCount");
			assertThat(read(TEMPLATE)).isEqualTo(EDIT);
			assertThat(Files.exists(dir.resolve(SPEC))).isFalse();
			Result check = run("check");
			assertThat(check.code()).as(check.out()).isZero();
		}

		@Test
		void anEntryTheTemplateStillUsesIsNotRemoved() throws IOException {
			attachRoot();
			run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--text", "label");

			Result used = run("remove", "behavior", "memo", "body-count", "--computed", "label");
			Result unused = run("remove", "behavior", "memo", "body-count", "--state", "draft", "--computed", "over");

			assertThat(used.err()).contains("label를 템플릿이 부릅니다(memo/api/edit.html:7)", "--detach");
			assertThat(unused.code()).as(unused.err()).isZero();
			assertThat(unused.out()).contains("상태 draft 지움", "계산 over 지움");
			assertThat(read(SPEC)).contains("label:").doesNotContain("draft:", "over:");
			assertThat(read(SCRIPT)).doesNotContain("get over()");
			assertThat(run("remove", "behavior", "memo", "body-count", "--action", "nope").err()).contains("동작 'nope'이 없습니다");
			assertThat(run("remove", "behavior", "memo", "body-count", "--state", "limit").code()).as("계산 label이 limit을 쓴다").isEqualTo(2);
			assertThat(read(SPEC)).contains("limit:");
		}

		@Test
		void renamingUpdatesTheSpecTheScriptAndEveryXData() throws IOException {
			attachRoot();

			Result result = run("rename", "behavior", "memo", "body-count", "body-length");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(read(SPEC)).contains("  bodyLength:").doesNotContain("bodyCount");
			assertThat(read(SCRIPT)).contains("window.Alpine.data('memoBodyLength'").doesNotContain("memoBodyCount");
			assertThat(read(TEMPLATE)).contains("x-data=\"memoBodyLength\"").doesNotContain("memoBodyCount");
			Result check = run("check");
			assertThat(check.code()).as(check.out()).isZero();
			assertThat(run("rename", "behavior", "memo", "nope", "x").err()).contains("nope가 없습니다");
		}
	}

	@Nested
	class ReferenceApp {

		/**
		 * reference-app의 화면 안 동작은 명령만으로 만들 수 있다: 모두 떼어 낸 템플릿에 명령을 다시 실행하면 지금 파일과 글자까지 같다.
		 */
		@Test
		void itsBindingsAreReachedByCommandsOnly(@TempDir Path copy) throws IOException {
			Path source = Path.of("../samples/reference-app");
			try (Stream<Path> files = Files.walk(source.resolve("src"))) {
				for (Path file : files.filter(Files::isRegularFile).toList()) {
					Path target = copy.resolve(source.relativize(file));
					Files.createDirectories(target.getParent());
					Files.copy(file, target);
				}
			}
			Files.copy(source.resolve("pom.xml"), copy.resolve("pom.xml"));
			String fields = "src/main/resources/templates/memo/internal/fields.html";
			String detail = "src/main/resources/templates/memo/api/detail.html";
			String[][] steps = {
					{ "attach", "memoBodyCount", "--at", "memo/internal/fields:9", "--detach" },
					{ "attach", "memoBodyCount", "--at", "memo/internal/fields:7", "--detach" },
					{ "attach", "cozyCopy", "--at", "memo/api/detail:21", "--detach" },
					{ "attach", "memoBodyCount", "--at", "memo/internal/fields:7", "--data", "length=${bodyLength}", "--data",
							"limit=5000", "--on", "input:count" },
					{ "attach", "memoBodyCount", "--at", "memo/internal/fields:9", "--text", "label", "--cloak" },
					{ "attach", "cozyCopy", "--at", "memo/api/detail:21", "--data",
							"copy-text=${#strings.listJoin(memo.bodyLines, '&#10;')}", "--on", "click:copy", "--cloak" } };
			for (int i = 0; i < steps.length; i++) {
				String[] args = Stream.concat(Stream.of(steps[i]), Stream.of("--project", copy.toString())).toArray(String[]::new);
				Result result = CozyCliTest.run(args);
				assertThat(result.code()).as(String.join(" ", steps[i]) + "\n" + result.err()).isZero();
				if (i == 2) {
					assertThat(Files.readString(copy.resolve(fields))).doesNotContain("x-data", "x-on", "x-text", "data-limit");
					assertThat(Files.readString(copy.resolve(detail))).doesNotContain("cozyCopy", "x-cloak");
				}
			}

			assertThat(Files.readString(copy.resolve(fields))).isEqualTo(Files.readString(source.resolve(fields)));
			assertThat(Files.readString(copy.resolve(detail))).isEqualTo(Files.readString(source.resolve(detail)));
		}
	}
}
