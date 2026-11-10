package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code cozy sync agents}: 앱의 AGENTS.md와 cozy-helper 스킬을 이 cozy 명령에 든 것과 같게 맞춘다. 스킬 폴더는 cozy의 것이라 늘 같게
 * 만들고(남는 파일은 지운다), AGENTS.md는 앱의 것이라 내용이 다르면 {@code --force} 없이는 바꾸지 않는다.
 */
class SyncAgentsTest {

	static final Path SOURCE = Path.of("../docs/agents");

	static final String SKILL = ".agents/skills/cozy-helper/";

	@TempDir
	Path app;

	record Result(int code, String out, String err) {
	}

	Result run(String... args) {
		String[] all = new String[args.length + 2];
		System.arraycopy(args, 0, all, 0, args.length);
		all[args.length] = "--project";
		all[args.length + 1] = this.app.toString();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(all, Path.of(""), null, new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	/** 원본이 앱에 놓일 자리 → 내용 */
	static Map<String, String> expected() throws IOException {
		Map<String, String> files = new TreeMap<>();
		files.put("AGENTS.md", Files.readString(SOURCE.resolve("AGENTS.md"), StandardCharsets.UTF_8));
		Path skill = SOURCE.resolve("skills/cozy-helper");
		try (Stream<Path> walk = Files.walk(skill)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				files.put(SKILL + skill.relativize(file).toString().replace('\\', '/'),
						Files.readString(file, StandardCharsets.UTF_8));
			}
		}
		return files;
	}

	/** 앱의 AGENTS.md와 .agents 아래 파일 */
	Map<String, String> actual() throws IOException {
		Map<String, String> files = new TreeMap<>();
		if (Files.isRegularFile(this.app.resolve("AGENTS.md"))) {
			files.put("AGENTS.md", Files.readString(this.app.resolve("AGENTS.md"), StandardCharsets.UTF_8));
		}
		Path agents = this.app.resolve(".agents");
		if (Files.isDirectory(agents)) {
			try (Stream<Path> walk = Files.walk(agents)) {
				for (Path file : walk.filter(Files::isRegularFile).toList()) {
					files.put(this.app.relativize(file).toString().replace('\\', '/'),
							Files.readString(file, StandardCharsets.UTF_8));
				}
			}
		}
		return files;
	}

	void write(String file, String content) throws IOException {
		Path path = this.app.resolve(file);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	@BeforeEach
	void emptyApp() throws IOException {
		write("pom.xml", "<project><modelVersion>4.0.0</modelVersion><groupId>it</groupId><artifactId>app</artifactId>"
				+ "<version>1</version></project>\n");
		Files.createDirectories(this.app.resolve("src/main/java"));
	}

	@Test
	void anAppWithoutAgentDocsGetsTheSameFilesAsANewApp() throws IOException {
		Result result = run("sync", "agents");
		assertThat(result.err()).isEmpty();
		assertThat(result.code()).isZero();
		assertThat(actual()).isEqualTo(expected());
		assertThat(result.out()).contains("만듦  AGENTS.md")
			.contains("만듦  " + SKILL + "SKILL.md")
			.contains("만듦  " + SKILL + "references/add-crud/PLAYBOOK.md")
			.contains("[cozy] 적용했습니다")
			.doesNotContain("빈칸");
	}

	@Test
	void runningAgainChangesNothing() throws IOException {
		run("sync", "agents");
		Result again = run("sync", "agents");
		assertThat(again.code()).isZero();
		assertThat(again.out()).contains("[cozy] 바뀐 것 없음").doesNotContain("만듦").doesNotContain("바꿈");
		assertThat(actual()).isEqualTo(expected());
	}

	@Test
	void theSkillFolderIsMadeIdenticalIncludingRemovingLeftovers() throws IOException {
		run("sync", "agents");
		write(SKILL + "SKILL.md", "old skill\n");
		write(SKILL + "references/add-field/PLAYBOOK.md", "a playbook this cozy does not have\n");
		Files.delete(this.app.resolve(SKILL + "references/check/PLAYBOOK.md"));
		Result result = run("sync", "agents");
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("바꿈  " + SKILL + "SKILL.md")
			.contains("지움  " + SKILL + "references/add-field/PLAYBOOK.md")
			.contains("만듦  " + SKILL + "references/check/PLAYBOOK.md")
			.contains("[cozy] 적용했습니다");
		assertThat(actual()).isEqualTo(expected());
		assertThat(this.app.resolve(SKILL + "references/add-field")).doesNotExist();
	}

	@Test
	void otherSkillsAreNotTouched() throws IOException {
		write(".agents/skills/team-notes/SKILL.md", "ours\n");
		run("sync", "agents");
		assertThat(this.app.resolve(".agents/skills/team-notes/SKILL.md")).hasContent("ours\n");
	}

	@Test
	void aDifferentAgentsMdIsKeptAndTheSkillIsStillSynced() throws IOException {
		write("AGENTS.md", "# our own notes\n");
		Result result = run("sync", "agents");
		assertThat(result.code()).isZero();
		assertThat(this.app.resolve("AGENTS.md")).hasContent("# our own notes\n");
		assertThat(this.app.resolve(SKILL + "SKILL.md")).isRegularFile();
		assertThat(result.out()).contains("그대로  AGENTS.md").contains("--force").contains("[cozy] 적용했습니다");

		Result again = run("sync", "agents");
		assertThat(again.out()).contains("[cozy] 바뀐 것 없음").contains("--force").doesNotContain("이미 같은 내용이 있습니다");
	}

	@Test
	void forceReplacesADifferentAgentsMd() throws IOException {
		write("AGENTS.md", "# our own notes\n");
		Result result = run("sync", "agents", "--force");
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("바꿈  AGENTS.md");
		assertThat(actual()).isEqualTo(expected());
	}

	@Test
	void dryRunWritesNothing() throws IOException {
		Result result = run("sync", "agents", "--dry-run");
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("만듦  AGENTS.md").contains("계획만 보였습니다(--dry-run)");
		assertThat(actual()).isEmpty();
	}

	@Test
	void jsonListsThePlan() {
		Result result = run("sync", "agents", "--json");
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("\"recipe\": \"sync agents\"")
			.contains("\"applied\": true")
			.contains("\"action\": \"create\"")
			.contains("\"file\": \"AGENTS.md\"");
	}

	@Test
	void wrongUsageIsRefused() throws IOException {
		Result missing = run("sync");
		assertThat(missing.code()).isEqualTo(2);
		assertThat(missing.err()).contains("cozy sync agents");
		Result unknown = run("sync", "skills");
		assertThat(unknown.code()).isEqualTo(2);
		Result extra = run("sync", "agents", "--label", "x");
		assertThat(extra.code()).isEqualTo(2);
		assertThat(actual()).isEmpty();
	}

	@Test
	void forceBelongsToSyncOnly() {
		Result result = run("recipes", "--force");
		assertThat(result.code()).isEqualTo(2);
		assertThat(result.err()).contains("--force");
	}
}
