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

/** 앱 복사본에서 소스를 읽는 워크벤치: 테스트마다 같은 앱에서 시작하고, 미리보기와 붙이기를 부른다 */
abstract class WorkbenchLiveBase {

	static final String PREVIEW = "/cozy/dev/catalog/screen?spec=memo-form&state=default";

	static final String FIELDS = "src/main/resources/templates/memo/internal/fields.html";

	@Autowired
	MockMvc mvc;

	Map<String, String> original;

	abstract Path project();

	@BeforeEach
	void remember() throws IOException {
		this.original = WorkbenchCommandTest.sources(project());
	}

	@AfterEach
	void restore() throws IOException {
		for (Map.Entry<String, String> file : this.original.entrySet()) {
			Files.writeString(project().resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
		}
	}

	String body(String url) throws Exception {
		return this.mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	MockHttpServletRequestBuilder showOver(String step) {
		return post("/cozy/dev/workbench/attach").with(csrf())
			.param("spec", "memo-form")
			.param("state", "default")
			.param("w", "full")
			.param("component", "memoBodyCount")
			.param("template", "memo/internal/fields")
			.param("line", "9")
			.param("option", "show")
			.param("value", "over")
			.param("step", step);
	}

	String after(MockHttpServletRequestBuilder request) throws Exception {
		MvcResult posted = this.mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
		return this.mvc.perform(get(posted.getResponse().getRedirectedUrl()).flashAttrs(posted.getFlashMap()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}
}
