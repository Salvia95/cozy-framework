package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.ui.catalog.FragmentManifest;

/**
 * 레포의 화면 작업 스킬(.claude/skills/ui-design-criteria)이 원문과 어긋나지 않는다. 스킬은 원문(docs/conventions, 매니페스트,
 * cozy.css, 검사)을 행동 순서로 다시 엮은 것이라, 원문이 바뀌고 스킬이 남으면 에이전트가 낡은 기준으로 화면을 만든다.
 */
class DesignCriteriaSkillTest {

	static final Path REPO = Path.of("..");

	static final Path SKILL = REPO.resolve(".claude/skills/ui-design-criteria");

	static final Path REFERENCES = SKILL.resolve("references");

	static String read(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static List<Path> documents() throws IOException {
		List<Path> files = new ArrayList<>(List.of(SKILL.resolve("SKILL.md")));
		try (Stream<Path> refs = Files.list(REFERENCES)) {
			refs.filter((p) -> p.toString().endsWith(".md")).sorted().forEach(files::add);
		}
		return files;
	}

	static String all() throws IOException {
		StringBuilder text = new StringBuilder();
		for (Path file : documents()) {
			text.append(read(file)).append('\n');
		}
		return text.toString();
	}

	@Test
	void everyReferenceIsLinkedWithWhenToReadItFirst() throws IOException {
		String skill = read(SKILL.resolve("SKILL.md"));
		assertThat(skill).startsWith("---\nname: ui-design-criteria\ndescription:");
		Set<String> linked = new TreeSet<>();
		Matcher link = Pattern.compile("\\]\\((references/[^)]+\\.md)\\)").matcher(skill);
		while (link.find()) {
			linked.add(link.group(1));
			assertThat(SKILL.resolve(link.group(1))).as("링크 " + link.group(1)).isRegularFile();
		}
		Set<String> files = new TreeSet<>();
		try (Stream<Path> refs = Files.list(REFERENCES)) {
			refs.forEach((p) -> files.add("references/" + p.getFileName()));
		}
		assertThat(linked).as("references의 문서는 모두 SKILL.md에서 읽을 때를 밝혀 링크한다").isEqualTo(files);
		for (String line : skill.split("\n")) {
			if (line.contains("](references/")) {
				String before = line.substring(0, line.indexOf('['));
				assertThat(before).as("링크 앞에 어떤 상황·동작에서 읽는지 한 줄로 쓴다: " + line).contains("때").hasSizeGreaterThan(30);
			}
		}
	}

	@Test
	void repositoryPathsExist() throws IOException {
		Pattern path = Pattern.compile("`((?:docs|cozy-[a-z]+|samples|distribution|\\.claude)/[^`\\s]+)`");
		for (Path file : documents()) {
			Matcher m = path.matcher(read(file));
			while (m.find()) {
				String target = m.group(1).replaceAll("(:\\d+|#.*)$", "");
				if (target.matches(".*[{}*<>…].*") || target.contains("...")) {
					continue;
				}
				assertThat(REPO.resolve(target)).as(file.getFileName() + "의 경로 " + target).exists();
			}
		}
	}

	@Test
	void everyManifestFragmentIsInTheComponentList() throws IOException {
		String components = read(REFERENCES.resolve("components.md"));
		Path manifest = Path.of("src/main/resources/cozy/fragments.yml");
		for (FragmentManifest.Entry entry : FragmentManifest.parse(Files.newInputStream(manifest), manifest.toString())) {
			String function = entry.signature().substring(0, entry.signature().indexOf('('));
			assertThat(components).as(entry.id() + "가 components.md에 있다").contains(function + "(");
		}
	}

	@Test
	void tokenValuesMatchTheStyleSheet() {
		String css = read(Path.of("src/main/resources/META-INF/resources/cozy/cozy.css"));
		String colors = read(REFERENCES.resolve("color-type-spacing.md"));
		for (String tone : List.of("neutral", "info", "success", "warning", "critical")) {
			String fg = value(css, "--cozy-" + tone + "-fg");
			String bg = value(css, "--cozy-" + tone + "-bg");
			assertThat(colors).as(tone).containsPattern("\\| `" + tone + "` \\|[^\\n]*`" + fg + "` / `" + bg + "`");
		}
		assertThat(colors).as("입력 테두리 토큰").contains("`--cozy-control-border` " + value(css, "--cozy-control-border"));
	}

	@Test
	void checkIdsExistInTheirSources() throws IOException {
		String text = all();
		String rules = read(REPO.resolve("docs/conventions/rules.md"));
		String testing = read(REPO.resolve("docs/conventions/testing.md"));
		String verifier = read(REPO.resolve(".claude/agents/runtime-verifier.md"));
		Matcher id = Pattern.compile("\\b([RCV])(\\d{1,2})\\b").matcher(text);
		while (id.find()) {
			String name = id.group(1) + id.group(2);
			String source = switch (id.group(1)) {
				case "R" -> rules;
				case "C" -> testing;
				default -> verifier;
			};
			assertThat(source).as(name + "가 원문에 있다").contains("| " + name + " |");
		}
	}

	static String value(String css, String token) {
		Matcher m = Pattern.compile(Pattern.quote(token) + ":\\s*(#[0-9a-f]{6})").matcher(css);
		assertThat(m.find()).as(token).isTrue();
		return m.group(1);
	}
}
