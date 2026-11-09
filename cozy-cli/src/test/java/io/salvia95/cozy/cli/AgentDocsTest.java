package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.cli.internal.Recipes;

/**
 * 앱의 에이전트 문서: 가벼운 AGENTS.md, cozy-helper 스킬, 명령마다 references/&lt;명령&gt;/PLAYBOOK.md. 원본은 docs/agents/이고 아키타입이
 * 앱에 복사한다. 작성 규칙: 영어로만(명령이 내는 한국어 메시지와 예시 값은 코드 안에), 불릿은 마침표로 끝나는 문장, 절은 정해진 순서.
 */
class AgentDocsTest {

	static final Path ROOT = Path.of("../docs/agents");

	static final Path SKILL = ROOT.resolve("skills/cozy-helper");

	static final List<String> SECTIONS = List.of("## When to use", "## Syntax", "## Creates", "## Slots", "## Example", "## Failures",
			"## Next");

	static String read(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	/** 레시피와 조회·검사 명령, 주제: 플레이북 폴더 → 형식(주제는 null) */
	static Map<String, String> expectedPlaybooks() {
		Map<String, String> expected = new TreeMap<>();
		Recipes.ALL.forEach((r) -> expected.put(r.playbook(), r.usage()));
		Recipes.OTHER_PLAYBOOKS.forEach(expected::put);
		Recipes.TOPIC_PLAYBOOKS.forEach((t) -> expected.put(t, null));
		return expected;
	}

	static List<Path> playbooks() throws IOException {
		try (Stream<Path> dirs = Files.list(SKILL.resolve("references"))) {
			return dirs.map((d) -> d.resolve("PLAYBOOK.md")).sorted().toList();
		}
	}

	static List<Path> prompts() throws IOException {
		List<Path> files = new ArrayList<>(List.of(ROOT.resolve("AGENTS.md"), SKILL.resolve("SKILL.md")));
		files.addAll(playbooks());
		return files;
	}

	/** 코드 블록과 인라인 코드를 뺀 글 */
	static String prose(String text) {
		return text.replaceAll("(?s)```.*?```", "").replaceAll("`[^`\n]*`", "");
	}

	@Test
	void everyCommandHasAPlaybookWithItsExactSyntax() throws IOException {
		Map<String, String> expected = expectedPlaybooks();
		List<String> found = playbooks().stream().map((p) -> p.getParent().getFileName().toString()).toList();
		assertThat(found).as("references/의 폴더는 명령(레시피, describe, check)과 주제뿐이다").containsExactlyInAnyOrderElementsOf(expected.keySet());
		for (Map.Entry<String, String> e : expected.entrySet()) {
			Path playbook = SKILL.resolve("references").resolve(e.getKey()).resolve("PLAYBOOK.md");
			assertThat(playbook).as(e.getKey()).isRegularFile();
			if (e.getValue() != null) {
				assertThat(read(playbook)).as(e.getKey() + "의 ## Syntax는 cozy recipes의 형식과 글자까지 같다")
					.contains("## Syntax\n\n```text\n" + e.getValue() + "\n```\n");
			}
		}
	}

	@Test
	void everyPlaybookHasTheSameSectionsInOrder() throws IOException {
		for (Path playbook : playbooks()) {
			String text = read(playbook);
			assertThat(text).as(playbook + "는 제목 한 줄로 시작하고 frontmatter가 없다").startsWith("# ").doesNotStartWith("---");
			int at = -1;
			for (String section : SECTIONS) {
				int next = text.indexOf("\n" + section + "\n");
				assertThat(next).as(playbook + "의 " + section).isGreaterThan(at);
				at = next;
			}
		}
	}

	@Test
	void promptsAreEnglishWithCompleteBullets() throws IOException {
		Pattern hangul = Pattern.compile("[\\uAC00-\\uD7A3\\u3131-\\u318E]");
		for (Path file : prompts()) {
			String prose = prose(read(file));
			for (String line : prose.split("\n")) {
				assertThat(hangul.matcher(line).find()).as(file + ": 한국어는 코드(`…`) 안에만 쓴다: " + line).isFalse();
				if (line.matches("\\s*(- |\\d+\\. ).*")) {
					assertThat(line.stripTrailing()).as(file + ": 불릿은 마침표로 끝나는 문장이다: " + line).endsWith(".");
				}
			}
		}
	}

	@Test
	void theSkillFollowsTheAgentSkillsFormatAndLinksEveryPlaybook() throws IOException {
		String skill = read(SKILL.resolve("SKILL.md"));
		Matcher front = Pattern.compile("(?s)\\A---\nname: (\\S+)\ndescription: ([^\n]+)\n---\n").matcher(skill);
		assertThat(front.find()).as("frontmatter는 name과 description 한 줄").isTrue();
		assertThat(front.group(1)).isEqualTo(SKILL.getFileName().toString());
		assertThat(front.group(2).length()).isBetween(1, 1024);
		assertThat(skill.lines().count()).as("SKILL.md는 가볍게").isLessThanOrEqualTo(100);
		for (String name : expectedPlaybooks().keySet()) {
			assertThat(skill).as("SKILL.md가 " + name + " 플레이북에 링크한다").contains("](references/" + name + "/PLAYBOOK.md)");
		}
		for (String section : List.of("## When to use", "## When NOT to use", "## Steps", "## Constraints")) {
			assertThat(skill).contains("\n" + section + "\n");
		}
		Pattern link = Pattern.compile("\\]\\(([^)#:]+\\.md)\\)");
		for (Path file : prompts()) {
			Matcher m = link.matcher(read(file));
			while (m.find()) {
				assertThat(file.getParent().resolve(m.group(1)).normalize()).as(file + "의 링크 " + m.group(1)).isRegularFile();
			}
		}
	}

	@Test
	void agentsMdStaysSmallAndPointsToTheSkill() {
		String agents = read(ROOT.resolve("AGENTS.md"));
		assertThat(agents.lines().count()).as("AGENTS.md는 늘 읽히므로 40줄 이하").isLessThanOrEqualTo(40);
		assertThat(agents).contains(".agents/skills/cozy-helper/SKILL.md").contains("Cozy framework").contains("mvn -o verify");
		for (Recipes.Recipe recipe : Recipes.ALL) {
			assertThat(agents).as("명령 형식은 플레이북에만 둔다").doesNotContain(recipe.usage());
		}
		assertThat(agents.stripTrailing()).endsWith("Respond in Korean unless the user message is in English.");
	}

	@Test
	void cozyRecipesPointsToThePlaybooks() {
		assertThat(Recipes.text()).contains(".agents/skills/cozy-helper/references/");
	}
}
