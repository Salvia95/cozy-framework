package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
	void aHandWrittenScriptWithoutASpecIsNotThisChecksBusiness() throws IOException {
		write(BehaviorRules.scriptPath("memo"), BODY);

		assertThat(check()).isEmpty();
	}

	@Test
	void onlyModuleSpecsBelongInTheSpecFolder() throws IOException {
		write("behaviors/Memo.yml", SPEC);
		write("behaviors/notes.txt", "x");

		assertThat(check()).hasSize(2).allSatisfy((v) -> assertThat(v.message()).contains("모듈 이름의 .yml"));
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
