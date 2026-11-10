package io.salvia95.cozy.workbench.autoconfigure;

import java.nio.file.Path;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.Bean;

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

	/** 실행한 폴더에서 위로 올라가며 앱 프로젝트를 찾는다 */
	@Bean
	WorkbenchEngine cozyWorkbenchEngine() {
		return new WorkbenchEngine(Path.of(""));
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
