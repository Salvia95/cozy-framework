package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** workbench.md가 코드와 같다: 주소, 화면 보기의 파라미터, 설정 속성, 검사 화면이 부르는 명령 */
class WorkbenchDocsTest {

	static final Path DOC = Path.of("../docs/conventions/workbench.md");

	static final Path CONTROLLER = Path.of("src/main/java/io/salvia95/cozy/workbench/internal/WorkbenchController.java");

	static String doc() throws IOException {
		return Files.readString(DOC, StandardCharsets.UTF_8);
	}

	static List<String> all(Pattern pattern, String text) {
		List<String> found = new ArrayList<>();
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			found.add(m.group(1));
		}
		return found;
	}

	@Test
	void everyAddressOfTheControllerIsDocumented() throws IOException {
		String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
		List<String> paths = all(Pattern.compile("@(?:Get|Post)Mapping\\(\"(/[a-z]+)\"\\)"), source);

		assertThat(paths).contains("/check", "/attach", "/commands", "/map");
		assertThat(doc()).contains(paths.stream().map((p) -> "/cozy/dev/workbench" + p).toArray(String[]::new));
	}

	@Test
	void everyParameterOfTheScreenViewIsInTheAddressTable() throws IOException {
		String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
		int start = source.indexOf("String workbench(");
		String signature = source.substring(start, source.indexOf("Model model", start));
		List<String> params = all(Pattern.compile("@RequestParam\\([^)]*\\) (?:String|boolean|Integer) ([a-z]+)"), signature);

		assertThat(params).contains("spec", "state", "w", "view", "at", "component");
		assertThat(doc()).contains(params.stream().map((p) -> "| `" + p + "` |").toArray(String[]::new));
	}

	@Test
	void everySettingIsInTheTableAndEveryCheckFixIsNamed() throws IOException {
		List<String> settings = new ArrayList<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path file : files.filter((f) -> f.toString().endsWith(".java")).toList()) {
				settings.addAll(all(Pattern.compile("\"(cozy\\.dev\\.workbench\\.[a-z-]+)\""), Files.readString(file, StandardCharsets.UTF_8)));
			}
		}

		assertThat(settings).contains("cozy.dev.workbench.enabled", "cozy.dev.workbench.project-dir", "cozy.dev.workbench.live-resources");
		assertThat(doc()).contains(settings.stream().distinct().map((s) -> "| `" + s + "` |").toArray(String[]::new));
		for (var fix : WorkbenchController.CHECK_FIXES.entrySet()) {
			assertThat(doc()).contains("`" + fix.getKey() + "`", "`cozy " + String.join(" ", fix.getValue()) + "`");
		}
	}
}
