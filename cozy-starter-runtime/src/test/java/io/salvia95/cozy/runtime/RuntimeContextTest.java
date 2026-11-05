package io.salvia95.cozy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import io.salvia95.cozy.runtime.autoconfigure.CozySecurityAutoConfiguration;
import io.salvia95.cozy.runtime.testapp.TestApp;

/**
 * 자동 설정이 테스트 컨텍스트에도 그대로 적용되는지 (PRD §9.1, §9.3, §9.5).
 */
@SpringBootTest(classes = TestApp.class,
		properties = { "cozy.data-dir=" + RuntimeContextTest.DATA_DIR, "server.address=0.0.0.0"})
@AutoConfigureMockMvc
class RuntimeContextTest {

	static final String DATA_DIR = "target/test-data/runtime-context";

	static {
		// 컨텍스트가 뜨기 전에 외부 설정과 비밀 값 파일을 준비한다
		Path root = Path.of(DATA_DIR);
		try {
			FileSystemUtils.deleteRecursively(root);
			Files.createDirectories(root.resolve("config"));
			Files.writeString(root.resolve("config/application.yml"), "cozy:\n  test:\n    greeting: from-external\n");
			Files.writeString(root.resolve("config/secrets.properties"), "api.token=s3cr3t\n");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	Environment environment;

	@Autowired
	CozyDataDirectory dataDirectory;

	@Autowired
	CozySecretStore secretStore;

	@Autowired
	UserDetailsService userDetailsService;

	@Test
	void dataDirectoryIsResolvedAndCreated() {
		assertThat(this.dataDirectory.root()).isEqualTo(Path.of(DATA_DIR).toAbsolutePath().normalize());
		assertThat(CozyDataDirectory.SUBDIRECTORIES)
			.allSatisfy((name) -> assertThat(this.dataDirectory.root().resolve(name)).isDirectory());
	}

	@Test
	void externalConfigOverridesPackagedConfig() throws Exception {
		this.mvc.perform(get("/hello")).andExpect(status().isOk()).andExpect(content().string("from-external"));
	}

	@Test
	void loopbackAddressCannotBeOverridden() {
		assertThat(this.environment.getProperty("server.address")).isEqualTo("127.0.0.1");
	}

	@Test
	void logFileGoesToDataDirectory() {
		assertThat(this.environment.getProperty("logging.file.name"))
			.isEqualTo(this.dataDirectory.logs().resolve("app.log").toString());
		assertThat(this.environment.getProperty("logging.logback.rollingpolicy.max-file-size")).isEqualTo("10MB");
		assertThat(this.environment.getProperty("logging.logback.rollingpolicy.max-history")).isEqualTo("14");
	}

	@Test
	void secretsComeFromDataDirectory() {
		assertThat(this.secretStore.find("api.token")).contains("s3cr3t");
		assertThat(this.secretStore.find("missing")).isEmpty();
		assertThat(this.secretStore.toString()).doesNotContain("s3cr3t");
	}

	@Test
	void foreignHostIsForbidden() throws Exception {
		this.mvc.perform(get("/hello").header("Host", "evil.example")).andExpect(status().isForbidden());
		this.mvc.perform(get("/hello").header("Host", "localhost")).andExpect(status().isOk());
	}

	@Test
	void csrfIsRequiredForPost() throws Exception {
		this.mvc.perform(post("/hello")).andExpect(status().isForbidden());
		this.mvc.perform(post("/hello").with(csrf())).andExpect(status().isOk()).andExpect(content().string("posted"));
	}

	@Test
	void securityHeaders() throws Exception {
		this.mvc.perform(get("/hello"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Content-Security-Policy", CozySecurityAutoConfiguration.CONTENT_SECURITY_POLICY));
	}

	@Test
	void noGeneratedLoginUser() {
		assertThat(this.userDetailsService.getClass().getName()).contains("CozySecurityAutoConfiguration");
	}
}
