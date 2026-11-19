package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.salvia95.cozy.test.CozyHtmlValidator;

/** {@code cozy.dev.workbench.live-resources=false}: 워크벤치로 적용해도 미리보기는 빌드 결과를 그리고, 화면이 빌드가 필요하다고 알린다 */
@SpringBootTest(properties = { "cozy.data-dir=target/test-data/workbench-live-off-${random.uuid}", "cozy.runtime.open-browser=false",
		"cozy.dev.workbench.live-resources=false" })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext
class WorkbenchLiveOffTest extends WorkbenchLiveBase {

	static Path project;

	@DynamicPropertySource
	static void project(DynamicPropertyRegistry registry) throws IOException {
		project = WorkbenchCommandTest.copyOfThisProject("workbench-live-off-");
		registry.add("cozy.dev.workbench.project-dir", project::toString);
	}

	@Override
	Path project() {
		return project;
	}

	@Test
	void anAppliedTemplateNeedsABuildAndTheScreenSaysSo() throws Exception {
		String applied = after(showOver("apply"));

		assertThat(Files.readString(project.resolve(FIELDS), StandardCharsets.UTF_8)).contains("x-show=\"over\"");
		assertThat(applied).contains("빌드 필요", "바뀐 파일은 앱을 다시 빌드하고 실행한 뒤 보입니다.");
		assertThat(body(PREVIEW)).as("미리보기는 빌드 결과의 템플릿이다").doesNotContain("x-show=\"over\"");
	}
}
