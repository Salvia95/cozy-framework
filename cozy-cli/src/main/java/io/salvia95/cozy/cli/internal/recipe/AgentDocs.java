package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Recipes;

/**
 * 앱의 에이전트 문서: {@code AGENTS.md}와 {@code .agents/skills/cozy-helper/}. 이 jar에 든 것이 이 cozy 명령을 설명하는 판이다.
 *
 * <ul>
 * <li>스킬 폴더는 cozy의 것이다. jar의 것과 같게 만든다: 없으면 만들고, 다르면 바꾸고, jar에 없는 파일은 지운다.</li>
 * <li>{@code AGENTS.md}는 앱의 것이다. 없으면 만든다. 내용이 다르면 {@code --force}일 때만 바꾼다.</li>
 * </ul>
 */
final class AgentDocs {

	static final String AGENTS_MD = "AGENTS.md";

	static final String SKILL_DIR = ".agents/skills/cozy-helper";

	private AgentDocs() {
	}

	/** 스킬 폴더 안의 경로 → 내용. 플레이북은 명령(레시피, describe, check 등)과 주제마다 하나다 */
	static Map<String, String> skillFiles() {
		Map<String, String> files = new LinkedHashMap<>();
		files.put("SKILL.md", resource("skills/cozy-helper/SKILL.md"));
		TreeSet<String> playbooks = new TreeSet<>(Recipes.OTHER_PLAYBOOKS.keySet());
		Recipes.ALL.forEach((r) -> playbooks.add(r.playbook()));
		playbooks.addAll(Recipes.TOPIC_PLAYBOOKS);
		for (String playbook : playbooks) {
			String file = "references/" + playbook + "/PLAYBOOK.md";
			files.put(file, resource("skills/cozy-helper/" + file));
		}
		return files;
	}

	static String agentsMd() {
		return resource(AGENTS_MD);
	}

	/** @return 다음 할 일 */
	static String sync(Plan plan, boolean force) {
		String next = "에이전트는 AGENTS.md를 늘 읽고, 구조를 바꿀 때 " + SKILL_DIR + "/SKILL.md를 읽습니다.";
		String agents = agentsMd();
		String current = plan.current(AGENTS_MD);
		if (current == null || current.equals(agents) || force) {
			plan.replace(AGENTS_MD, agents, "이 cozy 명령의 AGENTS.md로 바꿈(--force)");
		}
		else {
			plan.keep(AGENTS_MD, "내용이 달라 바꾸지 않음");
			next = "AGENTS.md는 이 cozy 명령의 것과 달라 그대로 두었습니다. 앱에서 고친 내용이 없으면 cozy sync agents --force로 "
					+ "바꾸고, 있으면 고친 내용을 따로 적어 둔 뒤 --force로 바꾸고 다시 더하세요. " + next;
		}
		Map<String, String> skill = skillFiles();
		skill.forEach((file, content) -> plan.replace(SKILL_DIR + "/" + file, content, "이 cozy 명령의 내용으로 바꿈"));
		Path dir = plan.project().resolve(SKILL_DIR);
		if (Files.isDirectory(dir)) {
			try (Stream<Path> walk = Files.walk(dir)) {
				for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
					String file = dir.relativize(path).toString().replace('\\', '/');
					if (!skill.containsKey(file)) {
						plan.delete(SKILL_DIR + "/" + file, "이 cozy 명령에 없는 파일");
					}
				}
			}
			catch (IOException ex) {
				throw new CliException(SKILL_DIR + "를 읽지 못했습니다: " + ex.getMessage());
			}
		}
		return next;
	}

	private static String resource(String name) {
		try (InputStream in = AgentDocs.class.getResourceAsStream("/agents/" + name)) {
			if (in == null) {
				throw new CliException("cozy 명령에 에이전트 문서가 들어 있지 않습니다: agents/" + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new CliException("에이전트 문서를 읽지 못했습니다: agents/" + name + ": " + ex.getMessage());
		}
	}
}
