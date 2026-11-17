package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.FrameworkBehaviors;
import io.salvia95.cozy.rules.ComponentContract;

/**
 * 프레임워크 컴포넌트의 명세 {@code cozy/behaviors.yml}이 {@code cozy.js}의 등록·메서드·{@code cozy:component} 줄과 같고,
 * docs/conventions/ui.md의 표가 그 명세에서 만든 표와 같다.
 */
class FrameworkBehaviorsTest {

	static final Path UI = Path.of("../cozy-ui/src/main/resources");

	static final String TABLE_START = "<!-- cozy:framework-behaviors (cozy-ui의 cozy/behaviors.yml에서 만든 표) -->\n";

	static final String TABLE_END = "<!-- /cozy:framework-behaviors -->";

	static FrameworkBehaviors spec() throws IOException {
		return FrameworkBehaviors.read(Files.readString(UI.resolve("cozy/behaviors.yml"), StandardCharsets.UTF_8), "cozy/behaviors.yml");
	}

	static String script() throws IOException {
		return Files.readString(UI.resolve("META-INF/resources/cozy/cozy.js"), StandardCharsets.UTF_8);
	}

	@Test
	void theSpecNamesEveryComponentCozyJsRegisters() throws IOException {
		Set<String> registered = new LinkedHashSet<>();
		Matcher m = Pattern.compile("Alpine\\.data\\('([A-Za-z]+)'").matcher(script());
		while (m.find()) {
			registered.add(m.group(1));
		}

		assertThat(spec().components().keySet()).containsExactlyInAnyOrderElementsOf(registered);
	}

	@Test
	void contractLinesInCozyJsMatchTheSpec() throws IOException {
		Map<String, ComponentContract> contracts = ComponentContract.parse(script());

		assertThat(contracts.keySet()).containsExactlyInAnyOrderElementsOf(spec().components().keySet());
		spec().components().values().forEach((c) -> {
			assertThat(contracts.get(c.name()).members()).as(c.name()).containsExactlyInAnyOrderElementsOf(c.methods().keySet());
			assertThat(contracts.get(c.name()).data()).as(c.name()).containsExactlyInAnyOrderElementsOf(c.data().keySet());
		});
	}

	@Test
	void everyMethodInTheSpecIsDefinedInCozyJs() throws IOException {
		String script = script();
		spec().components().values().forEach((c) -> c.methods().keySet().forEach((name) -> assertThat(script)
			.as(c.name() + "." + name).containsPattern("\\n\\s+" + name + ": function \\(")));
	}

	@Test
	void theUiDocTableIsGeneratedFromTheSpec() throws IOException {
		String doc = Files.readString(Path.of("../docs/conventions/ui.md"), StandardCharsets.UTF_8);
		int start = doc.indexOf(TABLE_START);
		int end = doc.indexOf(TABLE_END);

		assertThat(start).as("ui.md의 표 시작 표지").isNotNegative();
		assertThat(doc.substring(start + TABLE_START.length(), end)).isEqualTo(spec().table());
	}

	@Test
	void methodKeysAreNamesOrNamesWithOneArgument() {
		FrameworkBehaviors spec = FrameworkBehaviors.read("""
				components:
				  cozyX:
				    purpose: "p"
				    methods:
				      toggle(이름): "a"
				      copy: "b"
				""", "x.yml");

		assertThat(spec.components().get("cozyX").methods().get("toggle").argument()).isEqualTo("이름");
		assertThat(spec.components().get("cozyX").methods().get("copy").signature()).isEqualTo("copy()");
		assertThatThrownBy(() -> FrameworkBehaviors.read("components:\n  cozyX:\n    purpose: \"p\"\n    methods:\n      'a b': \"c\"\n",
				"x.yml")).isInstanceOf(CliException.class).hasMessageContaining("이름(인자)");
		assertThatThrownBy(() -> FrameworkBehaviors.read("components:\n  cozyX: {}\n", "x.yml")).isInstanceOf(CliException.class)
			.hasMessageContaining("purpose");
	}
}
