package io.salvia95.cozy.workbench.autoconfigure;

import java.nio.file.Path;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import io.salvia95.cozy.ui.autoconfigure.CozyUiAutoConfiguration;
import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.ScreenSpec;
import io.salvia95.cozy.workbench.internal.SourceLocationDialect;
import io.salvia95.cozy.workbench.internal.SourceScreenSpecs;
import io.salvia95.cozy.workbench.internal.SourceTemplateResolver;
import io.salvia95.cozy.workbench.internal.WorkbenchController;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine;
import io.salvia95.cozy.workbench.internal.WorkbenchEnvironmentPostProcessor;

/**
 * 워크벤치 자동 설정. dev 프로필이면 켜진다({@code cozy.dev.workbench.enabled}). 패턴 카탈로그({@code cozy.dev.catalog.enabled})가 켜져
 * 있어야 한다: 매니페스트와 화면 미리보기를 카탈로그가 준다.
 */
@AutoConfiguration(after = CozyUiAutoConfiguration.class, before = ThymeleafAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = WorkbenchEnvironmentPostProcessor.ENABLED, havingValue = "true")
@ConditionalOnBean(FragmentManifest.class)
public class CozyWorkbenchAutoConfiguration {

	/** 앱 프로젝트 폴더를 직접 정하는 속성 */
	public static final String PROJECT_DIR = "cozy.dev.workbench.project-dir";

	/**
	 * 앱 프로젝트: {@code cozy.dev.workbench.project-dir}이 있으면 그 폴더, 없으면 실행한 폴더에서 위로 올라가며 찾는다. cozy 명령에는 실행 중인
	 * cozy-ui의 위치를 넘겨, Maven 저장소를 찾지 않고 같은 프레임워크 템플릿으로 검사하게 한다.
	 */
	@Bean
	WorkbenchEngine cozyWorkbenchEngine(Environment environment) {
		String projectDir = environment.getProperty(PROJECT_DIR, "");
		Path ui = uiLocation();
		return projectDir.isBlank() ? new WorkbenchEngine(Path.of(""), ui) : WorkbenchEngine.at(Path.of(projectDir), ui);
	}

	/** cozy-ui의 jar나 클래스 폴더. 파일 시스템의 경로가 아니면 {@code null} */
	private static Path uiLocation() {
		try {
			java.security.CodeSource source = FragmentManifest.class.getProtectionDomain().getCodeSource();
			return (source != null && "file".equals(source.getLocation().getProtocol())) ? Path.of(source.getLocation().toURI())
					: null;
		}
		catch (java.net.URISyntaxException | RuntimeException ex) {
			return null;
		}
	}

	/**
	 * 미리보기의 요소에 소스 위치를 싣는다. Thymeleaf 자동 설정이 이 빈을 템플릿 엔진에 더한다. 템플릿을 소스에서 읽으면 줄도 소스의 글로 맞춘다
	 */
	@Bean
	SourceLocationDialect cozyWorkbenchSourceLocationDialect(WorkbenchEngine engine, Environment environment) {
		if (!live(engine, environment)) {
			return new SourceLocationDialect();
		}
		// 소스 해석기와 같이: 프레임워크 템플릿과 이름이 아닌 것(cozy-catalog:…)은 클래스패스에서 읽는다
		return new SourceLocationDialect((template) -> (template.startsWith("cozy/") || template.contains(":") || template.contains(".."))
				? java.util.Optional.empty() : engine.source("src/main/resources/templates/" + template + ".html"));
	}

	/**
	 * 앱의 템플릿을 소스 폴더에서 먼저 읽는다(캐시하지 않는다). {@code cozy.dev.workbench.live-resources=false}면 없다. 정적 자원은
	 * {@link WorkbenchEnvironmentPostProcessor}가 맡는다
	 */
	@Bean
	@ConditionalOnProperty(name = WorkbenchEnvironmentPostProcessor.LIVE_RESOURCES, havingValue = "true", matchIfMissing = true)
	SourceTemplateResolver cozyWorkbenchSourceTemplateResolver(WorkbenchEngine engine) {
		return new SourceTemplateResolver(engine.projectDir().map((dir) -> dir.resolve("src/main/resources/templates")).orElse(null));
	}

	/** 앱이 템플릿과 {@code static/}을 소스 폴더에서 읽는가 */
	static boolean live(WorkbenchEngine engine, Environment environment) {
		return engine.available() && environment.getProperty(WorkbenchEnvironmentPostProcessor.LIVE_RESOURCES, Boolean.class, true);
	}

	/** 화면 스펙을 소스 폴더에서 읽는다. 패턴 카탈로그의 미리보기도 이것을 쓴다 */
	@Bean
	@Primary
	SourceScreenSpecs cozyWorkbenchScreenSpecs(WorkbenchEngine engine) {
		return new SourceScreenSpecs(engine, ScreenSpec.load(CozyWorkbenchAutoConfiguration.class.getClassLoader()));
	}

	@Bean
	WorkbenchController cozyWorkbenchController(FragmentManifest manifest, SourceScreenSpecs specs, WorkbenchEngine engine,
			Environment environment) {
		return new WorkbenchController(manifest, specs, engine, live(engine, environment));
	}
}
