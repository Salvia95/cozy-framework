package io.salvia95.cozy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasToString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
@Import(RuntimeContextTest.Contributors.class)
class RuntimeContextTest {

	static final String DATA_DIR = "target/test-data/runtime-context";

	static {
		// 컨텍스트가 뜨기 전에 외부 설정과 비밀 값 파일을 준비한다
		Path root = Path.of(DATA_DIR);
		try {
			FileSystemUtils.deleteRecursively(root);
			Files.createDirectories(root.resolve("config"));
			Files.writeString(root.resolve("config/application.yml"), """
					cozy:
					  test:
					    greeting: from-external
					    api-token: abc123
					    endpoint: https://scott:tiger@intra.example/api
					""");
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
	void diagnosticsZipHasSummaryMaskedConfigLogsAndContributions() throws Exception {
		this.mvc.perform(post("/cozy/diagnostics").header("Referer", "http://localhost/hello?x=1"))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/cozy/diagnostics").with(csrf()).header("Referer", "http://localhost/hello?x=1"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/hello?x=1"))
			.andExpect(flash().attribute("cozyFlash", hasToString(containsString("kind=success"))));
		Path zip;
		try (var list = Files.list(this.dataDirectory.diagnostics())) {
			zip = list.filter((p) -> p.getFileName().toString().endsWith(".zip")).findFirst().orElseThrow();
		}
		assertThat(zip.getFileName().toString()).matches("diagnostics-cozy-runtime-test-\\d{8}-\\d{6}\\.zip");
		Map<String, String> entries = new LinkedHashMap<>();
		try (ZipFile file = new ZipFile(zip.toFile())) {
			file.stream().forEach((entry) -> {
				try {
					entries.put(entry.getName(), new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
		}
		assertThat(entries).containsKeys("summary.txt", "config.txt", "test-ok.txt", "test-fail.txt")
			.doesNotContainKey("Bad Name.txt");
		assertThat(entries.keySet()).anyMatch((name) -> name.startsWith("logs/app.log"));
		assertThat(entries.get("summary.txt")).contains("앱: 런타임 테스트 (cozy-runtime-test)").contains("Java: 25");
		assertThat(entries.get("config.txt")).contains("cozy.test.greeting = from-external")
			.contains("cozy.test.api-token = ******")
			.contains("cozy.test.endpoint = https://******@intra.example/api")
			.doesNotContain("abc123")
			.doesNotContain("tiger");
		assertThat(entries.get("test-ok.txt")).isEqualTo("대기 작업 3");
		assertThat(entries.get("test-fail.txt")).contains("내용을 만들 수 없습니다").contains("일부러");
		// 비밀 값 파일의 값은 어디에도 없다
		assertThat(entries.values()).noneMatch((content) -> content.contains("s3cr3t"));
	}

	@Test
	void updateWithoutOfferGoesBackWithMessage() throws Exception {
		this.mvc
			.perform(post("/cozy/update").with(csrf()).param("version", "9.9.9").header("Referer", "http://evil.example//x"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/"))
			.andExpect(flash().attribute("cozyFlash",
					hasToString(containsString("kind=danger, message=설치할 업데이트가 없습니다. 화면을 새로 고치세요."))));
	}

	@Test
	void noGeneratedLoginUser() {
		assertThat(this.userDetailsService.getClass().getName()).contains("CozySecurityAutoConfiguration");
	}

	@TestConfiguration
	static class Contributors {

		@Bean
		CozyDiagnosticsContributor okContributor() {
			return CozyDiagnosticsContributor.of("test-ok.txt", () -> "대기 작업 3");
		}

		@Bean
		CozyDiagnosticsContributor failingContributor() {
			return CozyDiagnosticsContributor.of("test-fail.txt", () -> {
				throw new IllegalStateException("일부러");
			});
		}

		@Bean
		CozyDiagnosticsContributor badNameContributor() {
			return CozyDiagnosticsContributor.of("Bad Name.txt", () -> "x");
		}

	}
}
