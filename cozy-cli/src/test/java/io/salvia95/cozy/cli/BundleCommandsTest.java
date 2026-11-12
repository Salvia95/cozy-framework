package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 오프라인 번들의 cozy 명령(bash의 cozy, Windows의 cozy.ps1)은 cozy-cli가 아는 동작을 모두 cozy-cli로 넘긴다. 하나라도 빠지면 문서에 있는
 * 명령을 번들에서 쓸 수 없다.
 */
class BundleCommandsTest {

	static final Path BUNDLE = Path.of("../distribution/offline-bundle");

	/** 사용법에 적힌 동작: {@code cozy <동작>} */
	static Set<String> cliActions() {
		Set<String> actions = new TreeSet<>();
		Matcher m = Pattern.compile("(?m)^\\s+cozy ([a-z]+)").matcher(CozyCli.USAGE);
		while (m.find()) {
			actions.add(m.group(1));
		}
		return actions;
	}

	@Test
	void theBashCommandPassesEveryCliActionToCozyCli() throws IOException {
		String script = Files.readString(BUNDLE.resolve("cozy"), StandardCharsets.UTF_8);
		Matcher line = Pattern.compile("(?m)^\\s*([a-z|]+)\\) cli \"\\$ACTION\" \"\\$@\" ;;$").matcher(script);
		assertThat(line.find()).as("cozy의 cozy-cli 줄").isTrue();
		assertThat(Set.of(line.group(1).split("\\|"))).containsExactlyInAnyOrderElementsOf(cliActions());
	}

	@Test
	void theWindowsCommandPassesEveryCliActionToCozyCli() throws IOException {
		String script = Files.readString(BUNDLE.resolve("cozy.ps1"), StandardCharsets.UTF_8);
		Matcher line = Pattern.compile("\\{ \\$_ -in @\\(([^)]*)\\) \\} \\{ Invoke-CozyCli").matcher(script);
		assertThat(line.find()).as("cozy.ps1의 cozy-cli 줄").isTrue();
		Set<String> passed = new TreeSet<>();
		Matcher name = Pattern.compile("'([a-z]+)'").matcher(line.group(1));
		while (name.find()) {
			passed.add(name.group(1));
		}
		assertThat(passed).containsExactlyInAnyOrderElementsOf(cliActions());
	}
}
