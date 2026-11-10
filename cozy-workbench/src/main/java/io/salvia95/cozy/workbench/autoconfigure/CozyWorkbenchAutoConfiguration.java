package io.salvia95.cozy.workbench.autoconfigure;

import java.nio.file.Path;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import io.salvia95.cozy.ui.autoconfigure.CozyUiAutoConfiguration;
import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.ScreenSpec;
import io.salvia95.cozy.workbench.internal.SourceLocationDialect;
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

	/** 미리보기의 요소에 소스 위치를 싣는다. Thymeleaf 자동 설정이 이 빈을 템플릿 엔진에 더한다 */
	@Bean
	SourceLocationDialect cozyWorkbenchSourceLocationDialect() {
		return new SourceLocationDialect();
	}

	@Bean
	WorkbenchController cozyWorkbenchController(FragmentManifest manifest, WorkbenchEngine engine) {
		return new WorkbenchController(manifest, ScreenSpec.load(CozyWorkbenchAutoConfiguration.class.getClassLoader()),
				engine);
	}
}
