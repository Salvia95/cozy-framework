package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 검사 C12: 동작 명세와 그 명세에서 만든 JS가 맞는가.
 */
class BehaviorRulesTest {

	static final String SPEC = "components:\n  bodyCount:\n    purpose: \"글자 수를 보인다\"\n";

	static final String BODY = "document.addEventListener('alpine:init', function () {\n});\n";

	@TempDir
	Path resources;

	@Test
	void aScriptMadeFromTheSpecPasses() throws IOException {
		write(BehaviorRules.specPath("memo"), SPEC);
		write(BehaviorRules.scriptPath("memo"), generated(SPEC, BODY));

		assertThat(check()).isEmpty();
	}

	@Test
	void windowsLineEndingsAndABomDoNotChangeTheHashes() throws IOException {
		write(BehaviorRules.specPath("memo"), "﻿" + SPEC.replace("\n", "\r\n"));
		write(BehaviorRules.scriptPath("memo"), generated(SPEC, BODY).replace("\n", "\r\n"));

		assertThat(check()).isEmpty();
	}

	@Test
	void aHandEditedScriptFailsWithTheFixCommand() throws IOException {
		write(BehaviorRules.specPath("memo"), SPEC);
		write(BehaviorRules.scriptPath("memo"), generated(SPEC, BODY).replace("});", "  console.log('x');\n});"));

		assertThat(check()).singleElement().satisfies((v) -> {
			assertThat(v.ruleId()).isEqualTo("C12");
			assertThat(v.isError()).isTrue();
			assertThat(v.format()).startsWith("[C12] src/main/resources/static/memo/memo.js → memo — 동작 명세에서 만든 파일을 손으로 고쳤습니다")
				.endsWith("고치는 명령: cozy sync behaviors");
		});
	}

	@Test
	void anEditedSpecThatWasNotRegeneratedFails() throws IOException {
		write(BehaviorRules.specPath("memo"), SPEC + "    state:\n      length: \"number\"\n");
		write(BehaviorRules.scriptPath("memo"), generated(SPEC, BODY));

		assertThat(check()).singleElement()
			.satisfies((v) -> assertThat(v.format()).startsWith("[C12] src/main/resources/behaviors/memo.yml → memo — 동작 명세를 고친 뒤")
				.endsWith("고치는 명령: cozy sync behaviors"));
	}

	@Test
	void aSpecWithoutItsScriptFails() throws IOException {
		write(BehaviorRules.specPath("memo"), SPEC);

		assertThat(check()).singleElement()
			.satisfies((v) -> assertThat(v.format()).contains("behaviors/memo.yml").contains("만든 파일(static/memo/memo.js)이 없습니다"));
	}

	@Test
	void aGeneratedScriptWithoutItsSpecFails() throws IOException {
		write(BehaviorRules.scriptPath("memo"), generated(SPEC, BODY));

		assertThat(check()).singleElement()
			.satisfies((v) -> assertThat(v.format()).contains("static/memo/memo.js").contains("명세가 없습니다"));
	}

	@Test
	void aHandWrittenScriptNextToASpecFailsWithoutAFixCommand() throws IOException {
		write(BehaviorRules.specPath("memo"), SPEC);
		write(BehaviorRules.scriptPath("memo"), BODY);

		assertThat(check()).singleElement().satisfies((v) -> {
			assertThat(v.format()).contains("손으로 썼습니다");
			assertThat(v.command()).isNull();
		});
	}

	@Test
	void aHandWrittenScriptWithoutASpecIsAWarningWithTheMigrateCommand() throws IOException {
		write(BehaviorRules.scriptPath("memo"), BODY);
		write("static/vendor/other.js", BODY);

		assertThat(check()).singleElement().satisfies((v) -> {
			assertThat(v.isError()).isFalse();
			assertThat(v.format()).startsWith("[C12 경고] src/main/resources/static/memo/memo.js → memo — 손으로 쓴 모듈 JS입니다(직접 쓴 코드)")
				.endsWith("고치는 명령: cozy migrate behaviors");
		});
	}

	@Test
	void onlyModuleSpecsBelongInTheSpecFolder() throws IOException {
		write("behaviors/Memo.yml", SPEC);
		write("behaviors/notes.txt", "x");

		assertThat(check()).hasSize(2).allSatisfy((v) -> assertThat(v.message()).contains("모듈 이름의 .yml"));
	}

	@Test
	void aScriptMadeByAnEarlierCozyWithoutComponentLinesFails() throws IOException {
		// 템플릿이 부르는 이름(R16)과 붙인 곳·화면 이벤트(C13)는 cozy:component 줄로 확인한다. 그 줄이 없거나 동작과 이벤트가 없으면
		// 확인할 수 없으므로 다시 만들게 한다
		String register = "  window.Alpine.data('memoBodyCount', function () { return {}; });\n});\n";
		String head = "document.addEventListener('alpine:init', function () {\n";
		String none = head + register;
		String members = head + "  " + ComponentContract.line("memoBodyCount", List.of("count"), List.of()) + "\n" + register;
		String current = head + "  "
				+ ComponentContract.line("memoBodyCount", List.of("count"), List.of(), List.of("count"), List.of(), Map.of()) + "\n"
				+ register;
		write(BehaviorRules.specPath("memo"), SPEC);
		for (String old : List.of(none, members)) {
			write(BehaviorRules.scriptPath("memo"), generated(SPEC, old));
			assertThat(check()).singleElement()
				.satisfies((v) -> assertThat(v.format()).contains("cozy:component 줄)가 없습니다").endsWith("고치는 명령: cozy sync behaviors"));
		}
		write(BehaviorRules.scriptPath("memo"), generated(SPEC, current));
		assertThat(check()).isEmpty();
	}

	@Test
	void componentLinesCarryActionsAndScreenEvents() {
		String line = ComponentContract.line("memoSelection", List.of("ids", "pick", "clear"), List.of("ids"),
				List.of("pick", "clear"), List.of("memo:selection-changed"), Map.of("memo:archived", "clear"));

		assertThat(line).isEqualTo("// cozy:component memoSelection members=ids,pick,clear data=ids actions=pick,clear "
				+ "emits=memo:selection-changed listens=memo:archived:clear");
		ComponentContract contract = ComponentContract.parse(line).get("memoSelection");
		assertThat(contract.members()).containsExactlyInAnyOrder("ids", "pick", "clear");
		assertThat(contract.wiring().actions()).containsExactlyInAnyOrder("pick", "clear");
		assertThat(contract.wiring().emits()).containsExactly("memo:selection-changed");
		assertThat(contract.wiring().listens()).containsExactlyEntriesOf(Map.of("memo:archived", "clear"));
		assertThat(ComponentContract.parse(ComponentContract.line("cozyCopy", List.of("copy"), List.of("copy-text")))
			.get("cozyCopy")
			.wiring()).isNull();
	}

	@Test
	void anAppWithoutBehaviorsHasNothingToReport() {
		assertThat(check()).isEmpty();
	}

	static String generated(String spec, String body) {
		return "/* cozy가 만든 파일\n   " + BehaviorRules.stampLine(spec, body) + body;
	}

	List<Violation> check() {
		return BehaviorRules.check(this.resources, "src/main/resources/");
	}

	void write(String path, String content) throws IOException {
		Path file = this.resources.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}
}
