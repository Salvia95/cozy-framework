package io.salvia95.cozy.cli.internal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.salvia95.cozy.rules.FragmentDeclaration;
import io.salvia95.cozy.rules.LayerRules;
import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.RulesInput;
import io.salvia95.cozy.rules.Severity;
import io.salvia95.cozy.rules.SlotRules;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.TemplateRules;
import io.salvia95.cozy.rules.Violation;

/**
 * {@code cozy check}: 컴파일 없이 소스로 판단할 수 있는 검사. 템플릿 규칙 R1~R15(C2), 모듈 안 구조 C8, 빈칸 C9. 판단은 cozy-test와 같은
 * cozy-rules가 한다. C1, C3~C7은 앱을 컴파일하고 띄워야 하므로 {@code mvn verify}가 한다.
 */
public final class Checks {

	/** 같은 계층 fragment 참조 허용 (R4). cozy-test의 {@code CozyArchitectureTest.ALLOW_SAME_LAYER}와 같다 */
	static final String ALLOW_SAME_LAYER = "cozy.rules.allow-same-layer";

	private Checks() {
	}

	/**
	 * 위반 하나와 그것을 고칠 파일.
	 *
	 * @param file 프로젝트 기준 파일 경로. 프레임워크 jar 안이면 {@code null}
	 */
	public record Finding(Violation violation, String file) {
	}

	public static List<Finding> run(AppProject project, FrameworkJar framework) {
		List<Violation> violations = new ArrayList<>(project.problems());
		if (project.mainClass().isPresent()) {
			violations.addAll(templateRules(project, framework, violations));
			violations.addAll(LayerRules.check(project.modulePackages(), project.sources().javaTypes(),
					project.sources().namedInterfacePackages()));
		}
		violations.addAll(SlotRules.check(project.dir().resolve("src/main"), "src/main/"));
		Map<String, String> typeFiles = new LinkedHashMap<>();
		project.sources().types().values().forEach((t) -> typeFiles.put(t.name(), t.unit().relative()));
		project.sources().packageInfos().values().forEach((p) -> typeFiles.put(p.pkg(), p.unit().relative()));
		return violations.stream().map((v) -> new Finding(v, fileOf(project, v, typeFiles))).toList();
	}

	private static List<Violation> templateRules(AppProject project, FrameworkJar framework, List<Violation> problems) {
		List<TemplateFile> templates = new ArrayList<>(
				TemplateFile.fromDirectory(project.dir().resolve("src/main/resources/templates"), Origin.APP, "templates/"));
		templates.addAll(framework.templates());
		List<FragmentDeclaration> manifest = new ArrayList<>();
		if (framework.manifest() != null) {
			manifest.addAll(manifest(framework.manifest(), "[cozy-ui] cozy/fragments.yml", problems));
		}
		Path appManifest = project.dir().resolve("src/main/resources/cozy/fragments.yml");
		if (Files.isRegularFile(appManifest)) {
			manifest.addAll(manifest(read(appManifest), "src/main/resources/cozy/fragments.yml", problems));
		}
		boolean allowSameLayer = Boolean.parseBoolean(AppResources.appProperty(project.dir(), ALLOW_SAME_LAYER));
		return TemplateRules.check(new RulesInput(templates, project.moduleModel(), manifest, allowSameLayer,
				framework.icons(), framework.filledIcons()));
	}

	private static List<FragmentDeclaration> manifest(String text, String source, List<Violation> problems) {
		try {
			return AppResources.manifest(text, source).stream().map(AppResources.ManifestEntry::declaration).toList();
		}
		catch (RuntimeException ex) {
			problems.add(new Violation(source, 0, "R8", Severity.ERROR, null,
					ex.getMessage().replace("[cozy] ", "") + ". 매니페스트 형식은 docs/conventions/ui.md를 보세요."));
			return List.of();
		}
	}

	/** 메시지의 위치를 프로젝트 파일로: 템플릿, 소스, 타입 이름(C8), 패키지 이름(C8) */
	static String fileOf(AppProject project, Violation v, Map<String, String> typeFiles) {
		String location = v.location();
		if (location.startsWith("[")) {
			return null;
		}
		if (location.startsWith("templates/")) {
			return "src/main/resources/" + location;
		}
		if (location.startsWith("src/") || location.startsWith("cozy/")) {
			return location.startsWith("cozy/") ? "src/main/resources/" + location : location;
		}
		if (typeFiles.containsKey(location)) {
			return typeFiles.get(location);
		}
		String dir = "src/main/java/" + location.replace('.', '/');
		return Files.isDirectory(project.dir().resolve(dir)) ? dir : null;
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}
}
