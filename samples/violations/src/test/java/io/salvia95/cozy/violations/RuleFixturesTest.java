package io.salvia95.cozy.violations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.salvia95.cozy.rules.ModuleModel;
import io.salvia95.cozy.rules.RulesInput;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.TemplateRules;
import io.salvia95.cozy.rules.Violation;
import io.salvia95.cozy.test.CozyProject;

/**
 * PRD §6.2 수용 기준: rules/의 규칙별 픽스처가 각각 정확히 해당 규칙 ID로 검출된다.
 *
 * <p>픽스처 폴더 이름은 {@code R{n}-설명}이다. 폴더 안에 {@code templates/}, 모듈 구조 {@code modules.properties}
 * ({@code 모듈=허용 의존, ...}, 선언하지 않은 모듈은 {@code open}), 앱 매니페스트 {@code fragments.yml}(선택)을 둔다.
 * 프레임워크 템플릿과 매니페스트는 실제 cozy-ui jar의 것을 쓴다.
 */
class RuleFixturesTest {

	static final Path RULES = Path.of("rules");

	@TestFactory
	Stream<DynamicTest> eachFixtureFailsWithExactlyItsRule() throws IOException {
		List<Path> fixtures;
		try (Stream<Path> dirs = Files.list(RULES)) {
			fixtures = dirs.filter(Files::isDirectory).sorted().toList();
		}
		assertThat(fixtures).extracting((p) -> p.getFileName().toString().substring(0, 2))
			.containsExactly("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8");
		return fixtures.stream().map((fixture) -> DynamicTest.dynamicTest(fixture.getFileName().toString(), () -> {
			String rule = fixture.getFileName().toString().substring(0, 2);
			List<Violation> violations = check(fixture);
			violations.forEach(System.out::println);
			assertThat(violations).as(fixture + "는 위반이 있어야 합니다").isNotEmpty();
			assertThat(violations).as(fixture + "의 위반은 모두 " + rule + " 오류여야 합니다")
				.allSatisfy((v) -> {
					assertThat(v.ruleId()).isEqualTo(rule);
					assertThat(v.isError()).isTrue();
				});
		}));
	}

	static List<Violation> check(Path fixture) throws IOException {
		List<TemplateFile> templates = new ArrayList<>(CozyProject.appTemplates(fixture.resolve("templates")));
		templates.addAll(CozyProject.frameworkTemplates());
		return TemplateRules.check(new RulesInput(templates, modules(fixture.resolve("modules.properties")),
				CozyProject.manifest(fixture.resolve("fragments.yml")), false));
	}

	static ModuleModel modules(Path file) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file)) {
			properties.load(reader);
		}
		Map<String, ModuleModel.Module> modules = new LinkedHashMap<>();
		for (String name : properties.stringPropertyNames()) {
			String value = properties.getProperty(name).strip();
			List<String> deps = value.equals("open") ? null
					: value.isEmpty() ? List.of() : List.of(value.split("\\s*,\\s*"));
			modules.put(name, new ModuleModel.Module(name, deps, Set.of()));
		}
		return new ModuleModel(modules);
	}
}
