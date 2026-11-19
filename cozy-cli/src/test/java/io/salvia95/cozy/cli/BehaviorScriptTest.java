package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.BehaviorScript;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec;
import io.salvia95.cozy.cli.internal.behavior.Expr;
import io.salvia95.cozy.rules.BehaviorRules;

/**
 * 동작 명세에서 JS 만들기. {@code behaviors/demo.yml}은 낱말을 모두 쓰는 예시이고, {@code demo.js}는 그 명세에서 만든 결과다. 같은 명세는 같은
 * JS가 되고, 명세는 같은 글자로 다시 쓴다. 낱말 밖이나 형식이 틀린 명세는 실패로 닫는다. 만든 JS가 브라우저에서 동작하는지는
 * {@code behaviors/demo.html}을 열어 본다(docs/conventions/testing.md).
 */
class BehaviorScriptTest {

	static final Path DIR = Path.of("src/test/resources/behaviors");

	static String read(String name) throws IOException {
		return Files.readString(DIR.resolve(name), StandardCharsets.UTF_8);
	}

	@Test
	void theDemoSpecBecomesTheDemoScript() throws IOException {
		String text = read("demo.yml");
		BehaviorSpec spec = BehaviorSpec.read("demo", text, "behaviors/demo.yml");

		assertThat(spec.write()).as("demo.yml은 정리한 모양 그대로다").isEqualTo(text);
		assertThat(BehaviorScript.generate(spec, text)).isEqualTo(read("demo.js"));
	}

	@Test
	void theSameSpecWrittenDifferentlyBecomesTheSameFiles() throws IOException {
		BehaviorSpec loose = BehaviorSpec.read("demo", read("demo-input.yml"), "demo-input.yml");

		assertThat(loose.write()).isEqualTo(read("demo.yml"));
		assertThat(BehaviorScript.generate(loose, loose.write())).isEqualTo(read("demo.js"));
	}

	@Test
	void theScriptCarriesTheStampThatTheC12CheckReads() throws IOException {
		String js = read("demo.js");

		assertThat(BehaviorRules.isGenerated(js)).isTrue();
		assertThat(js).contains(BehaviorRules.stampLine(read("demo.yml"), js.substring(js.indexOf("*/\n") + 3)));
		assertThat(js).as("서버에 요청하지 않는다").doesNotContain("fetch(", "XMLHttpRequest", "htmx.ajax", "eval(", "Function(");
		assertThat(js).as("등록 이름은 글자다(R16이 읽는다)")
			.contains("window.Alpine.data('demoBodyCount', function () {", "window.Alpine.data('demoSelection', function () {",
					"window.Alpine.data('demoPanel', function () {");
	}

	@Test
	void expressionsArePrintedTheSameWayTheyAreRead() {
		assertThat(Expr.parse("and( gt(limit,0) ,gt(length , limit))", "x").print()).isEqualTo("and(gt(limit, 0), gt(length, limit))");
		assertThat(Expr.parse("format('it\\'s {n}', 'ko-KR')", "x").print()).isEqualTo("format('it\\'s {n}', 'ko-KR')");
		assertThat(Expr.parse("gt(n, 1.50)", "x").print()).isEqualTo("gt(n, 1.5)");
		assertThat(Expr.print(Expr.parseSteps("reset(ids);emit('demo:x', ids)", "x"))).isEqualTo("reset(ids); emit('demo:x', ids)");
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
			모르는 함수            | computed: { x: "sum(a, b)" }                                | 모르는 함수입니다: sum(…)
			없는 이름              | computed: { x: "not(missing)" }                             | 없는 이름입니다: missing
			종류가 다름            | state: { n: "number" }, computed: { x: "not(n)" }           | 받는 종류: bool, 받은 종류: number
			계산이 돈다            | computed: { a: "not(b)", b: "not(a)" }                      | 계산이 돌고 돌아 자기 자신을 씁니다
			계산의 이벤트 값       | computed: { x: "empty(event.value)" }                       | 이벤트 값(event.value)은 동작에서만 씁니다
			계산은 바꿀 수 없음    | computed: { x: "not(true)" }, actions: { a: "toggle(x)" }   | 계산은 바꿀 수 없습니다: x
			단계가 아닌 것         | state: { n: "number" }, actions: { a: "gt(n, 1)" }          | 단계는 set, toggle
			값 자리의 단계         | state: { n: "bool" }, computed: { x: "toggle(n)" }          | 동작의 단계는 값으로 쓸 수 없습니다: toggle(…)
			set의 종류             | state: { n: "number" }, actions: { a: "set(n, event.value)" } | number(…)를 씁니다
			다른 모듈의 이벤트     | state: { n: "bool" }, actions: { a: "emit('memo:x')" }      | 이 모듈의 이름으로 시작
			없는 동작을 받음       | listens: { "demo:x": "missing" }                            | 없는 동작입니다: missing
			format의 모르는 이름   | computed: { x: "format('{nope}')" }                         | 없는 이름입니다: nope
			format의 홀로 중괄호   | computed: { x: "format('a { b')" }                          | 중괄호 글자는 {{, }}로 적습니다
			쓸 수 없는 이름        | state: { init: "bool" }                                     | 쓸 수 없는 이름입니다(Alpine과 JavaScript가 쓰는 이름): init
			두 칸의 같은 이름      | state: { x: "bool" }, computed: { x: "not(x)" }             | 같은 이름이 상태, 계산, 동작 가운데 둘 이상에 있습니다
			모르는 종류            | state: { x: "date" }                                        | 모르는 종류입니다: date
			닫히지 않은 글자       | computed: { x: "format('a)" }                               | 작은따옴표(')가 없습니다
			모르는 이벤트 값       | state: { x: "text" }, actions: { a: "set(x, event.key)" }   | 모르는 이벤트 값입니다: event.key
			가까운 이름           | state: { length: "number" }, computed: { x: "gt(lenght, 1)" } | 없는 이름입니다: lenght. length인가요?
			가까운 함수           | computed: { x: "lenght('a')" }                            | 모르는 함수입니다: lenght(…). length인가요?
			가까운 단계           | state: { n: "bool" }, actions: { a: "toogle(n)" }         | toggle인가요?
			가까운 상태           | state: { open: "bool" }, actions: { a: "toggle(opne)" }   | 없는 상태입니다: opne. open인가요?
			""")
	void specsOutsideTheVocabularyAreRefused(String what, String body, String message) {
		String yaml = "components:\n  thing:\n    purpose: \"" + what + "\"\n    " + body.strip().replace("}, ", "}\n    ") + "\n";

		assertThatThrownBy(() -> BehaviorScript.generate(BehaviorSpec.read("demo", yaml, "behaviors/demo.yml"), yaml))
			.isInstanceOf(CliException.class)
			.hasMessageContaining(message);
	}

	@Test
	void theFileFormatIsChecked() {
		for (List<String> c : List.of(List.of("other: 1\n", "맨 위에는 components만 둡니다"),
				List.of("components:\n  thing:\n    state: {}\n", "purpose(목적 한 문장)가 없습니다"),
				List.of("components:\n  thing:\n    purpose: x\n    methods: {}\n", "모르는 키 methods"),
				List.of("components:\n  Thing:\n    purpose: x\n", "카멜 표기"),
				List.of("components:\n  thing:\n    purpose: x\n    state: { n: 1 }\n", "값은 글자로 적습니다"),
				List.of("components:\n  thing:\n    purpose: x\n  thing:\n    purpose: y\n", "YAML로 읽을 수 없습니다"))) {
			assertThatThrownBy(() -> BehaviorSpec.read("demo", c.get(0), "behaviors/demo.yml")).as(c.get(0))
				.isInstanceOf(CliException.class)
				.hasMessageContaining(c.get(1));
		}
	}
}
