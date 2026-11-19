package io.salvia95.cozy.cli.internal.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 화면 안의 동작 문서가 코드와 같은가. 낱말(계산 함수, 동작 단계, 이벤트 값, 상태의 종류)은 JS를 만드는 코드의 목록이 원본이고, 규약 문서와 에이전트
 * 플레이북이 같은 목록을 보인다. 명령, 검사, 올리는 절차도 문서마다 빠지지 않았는지 본다.
 */
class BehaviorDocsTest {

	static final Path DOCS = Path.of("../docs");

	static final List<String> EVENT_VALUES = List.of("event.value", "event.checked", "event.data.", "event.detail.");

	static final List<String> KINDS = List.of("bool", "number", "text", "list");

	@Test
	void theConventionListsEveryWordOfTheVocabulary() throws IOException {
		String section = section("conventions/ui.md", "### 동작 명세");

		assertThat(missing(section, words())).as("ui.md의 동작 명세에 없는 낱말").isEmpty();
		assertThat(section).contains(EVENT_VALUES).contains(KINDS.stream().map((k) -> "`" + k + "`").toList());
		assertThat(section).contains("C12", "C13", "cozy attach", "cozy migrate behaviors");
	}

	@Test
	void theVocabularyIsWhatTheSpecReadsAndTheScriptChecks() {
		assertThat(BehaviorVocabulary.names(BehaviorVocabulary.TYPE)).isEqualTo(KINDS);
		assertThat(KINDS).allSatisfy((k) -> assertThat(BehaviorSpec.Type.of(k)).as(k).isNotNull());
		assertThat(BehaviorVocabulary.of(BehaviorVocabulary.EVENT).stream().map((w) -> w.shape().replace("이름", ""))).isEqualTo(EVENT_VALUES);
		for (BehaviorVocabulary.Word w : BehaviorVocabulary.of(BehaviorVocabulary.EVENT)) {
			assertThat(Expr.parse(w.shape().replace("이름", "id"), "낱말")).as(w.shape()).isInstanceOf(Expr.EventRef.class);
		}
		assertThat(BehaviorScript.VALUES.keySet()).containsExactlyElementsOf(BehaviorVocabulary.names(BehaviorVocabulary.FUNCTION));
		assertThat(BehaviorScript.STEPS).containsExactlyElementsOf(BehaviorVocabulary.names(BehaviorVocabulary.STEP));
		assertThat(BehaviorVocabulary.WORDS).allSatisfy((w) -> {
			assertThat(w.shape()).as(w.name()).startsWith(w.name());
			assertThat(w.text()).as(w.name()).isNotBlank();
		});
	}

	@Test
	void theAddBehaviorPlaybookListsTheSameVocabulary() throws IOException {
		String section = section("agents/skills/cozy-helper/references/add-behavior/PLAYBOOK.md", "## Syntax");

		assertThat(missing(section, words())).as("add-behavior 플레이북의 Syntax에 없는 낱말").isEmpty();
		assertThat(section).contains(EVENT_VALUES).contains(KINDS.stream().map((k) -> "`" + k + "`").toList());
	}

	@Test
	void everyBehaviorCommandHasItsSectionInTheCliReference() throws IOException {
		String cli = read("conventions/cli.md");
		String usage = section("conventions/cli.md", "## 형식");

		for (String command : List.of("sync behaviors", "attach", "remove behavior", "rename behavior", "migrate behaviors")) {
			assertThat(usage).as("cli.md의 형식에 " + command).contains(command);
		}
		for (String heading : List.of("## sync behaviors", "## attach", "## remove behavior", "## rename behavior", "## migrate behaviors")) {
			assertThat(cli).as("cli.md의 절 " + heading).containsPattern("(?m)^" + Pattern.quote(heading) + "$");
		}
		assertThat(section("conventions/cli.md", "## describe")).contains("behaviors[]", "bindings[]", "uiEvents[]", "`C13`");
	}

	@Test
	void theBehaviorChecksAreDocumentedWhereTheyRun() throws IOException {
		String testing = read("conventions/testing.md");
		assertThat(testing).contains("| C12 |", "| C13 |", "behaviorScriptsMatchTheirSpecs", "behaviorsAreWired", "[C12 경고]");
		assertThat(read("conventions/rules.md")).contains("BehaviorRules.check", "BehaviorWiring.check", "ServerEvents", "ComponentContract");
		assertThat(read("conventions/workbench.md")).contains("C13");
		assertThat(section("agents/skills/cozy-helper/references/check/PLAYBOOK.md", "## Failures")).contains("[C13]", "[C12 경고]");
	}

	@Test
	void theUpgradeTo220SaysWhatToRunInTheApp() throws IOException {
		String upgrade = section("conventions/release.md", "## 앱을 2.2.0으로 올리기");

		assertThat(upgrade).contains("cozy sync agents", "cozy migrate behaviors", "cozy check", "mvn -o verify", "남은 일", "C13", "R16");
		assertThat(read("agents/skills/cozy-helper/references/upgrade/PLAYBOOK.md")).contains("cozy migrate behaviors");
	}

	@Test
	void theAgentRulesKeepScriptsAndAttributesWithTheCommands() throws IOException {
		String agents = read("agents/AGENTS.md");

		assertThat(agents).contains("cozy add behavior", "cozy attach", "static/{module}/{module}.js", "cozy migrate behaviors", "[C13]");
	}

	/** 계산 함수와 동작 단계 */
	static List<String> words() {
		List<String> words = new ArrayList<>(BehaviorScript.VALUES.keySet());
		words.addAll(BehaviorScript.STEPS);
		return words;
	}

	/** 백틱 안에 그 이름으로 시작하는 글자({@code `gt`}, {@code `gt(a, b)`})가 없는 낱말 */
	static List<String> missing(String text, List<String> words) {
		return words.stream().filter((w) -> !Pattern.compile("`" + Pattern.quote(w) + "[(`]").matcher(text).find()).toList();
	}

	static String read(String file) throws IOException {
		return Files.readString(DOCS.resolve(file), StandardCharsets.UTF_8);
	}

	/** 그 머리부터 같거나 높은 단계의 다음 머리 앞까지. 코드 블록 안의 {@code #}는 머리가 아니다 */
	static String section(String file, String heading) throws IOException {
		List<String> lines = read(file).lines().toList();
		int start = -1;
		for (int i = 0; i < lines.size() && start < 0; i++) {
			if (lines.get(i).equals(heading) || lines.get(i).startsWith(heading + " ")) {
				start = i;
			}
		}
		assertThat(start).as(file + "에 " + heading + "이 있다").isNotNegative();
		int level = heading.indexOf(' ');
		StringBuilder out = new StringBuilder(heading).append('\n');
		boolean fenced = false;
		for (String line : lines.subList(start + 1, lines.size())) {
			if (line.startsWith("```")) {
				fenced = !fenced;
			}
			int hashes = line.indexOf(' ');
			if (!fenced && hashes > 0 && hashes <= level && line.substring(0, hashes).chars().allMatch((c) -> c == '#')) {
				break;
			}
			out.append(line).append('\n');
		}
		return out.toString();
	}
}
