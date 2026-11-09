package io.salvia95.cozy.test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.TemplateRules;
import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.ScreenSpec;

/**
 * 렌더링 검사 (C3, C4, C5). 앱은 한 줄만 쓴다.
 *
 * <pre>
 * class RenderingTest extends CozyRenderingTest {
 * }
 * </pre>
 *
 * <ul>
 * <li>C3: {@code specs/*.yml}의 모든 화면·상태와 매니페스트의 모든 fragment·상태를 패턴 카탈로그 경로로 렌더링한다.
 * fixture는 선언된 화면 DTO 타입으로 바꾸며, 타입과 어긋나면 실패한다.</li>
 * <li>C4: 렌더링 결과를 vnu로 검사한다. 오류는 실패, 경고는 표준 출력에 보고한다.</li>
 * <li>C5: 스펙이 없는 화면 템플릿({@code {모듈}/api/}), 매니페스트에 없는 공개 fragment({@code {모듈}/spi/})가 있으면
 * 실패한다. 공개 fragment의 파라미터 타입은 spi DTO나 기본 타입이어야 한다(api·internal 타입 금지). {@code {모듈}/internal/}은
 * 그것을 조합하는 화면과 공개 fragment의 렌더링으로 검사된다.</li>
 * </ul>
 * 문서: docs/conventions/testing.md
 */
@SpringBootTest(properties = { "cozy.dev.catalog.enabled=true", "cozy.data-dir=target/cozy-test/rendering",
		"cozy.runtime.open-browser=false" })
@AutoConfigureMockMvc
public abstract class CozyRenderingTest {

	private static final String CATALOG = "/cozy/dev/catalog";

	@Autowired
	private MockMvc mvc;

	private final CozyHtmlValidator validator = new CozyHtmlValidator();

	/** 앱 템플릿 루트 */
	protected Path templateRoot() {
		return CozyProject.TEMPLATES;
	}

	@Test
	public final void everyScreenStateRendersAsValidHtml() {
		Report report = new Report();
		List<ScreenSpec> specs = specs(report);
		for (ScreenSpec spec : specs) {
			for (String state : spec.states().keySet()) {
				String where = "specs/" + spec.screen() + ".yml 상태 " + state;
				render(report, where, get(CATALOG + "/screen").param("spec", spec.screen()).param("state", state), false);
				for (String fragment : spec.fragments()) {
					render(report, where + " fragment " + fragment, get(CATALOG + "/screen").param("spec", spec.screen())
						.param("state", state)
						.param("fragment", fragment), true);
				}
			}
		}
		report.assertClean();
	}

	@Test
	public final void everyManifestFragmentRendersAsValidHtml() {
		Report report = new Report();
		for (FragmentManifest.Entry entry : FragmentManifest.load(getClass().getClassLoader()).fragments()) {
			for (String state : entry.samples().keySet()) {
				render(report, entry.source() + " " + entry.id() + " 상태 " + state,
						get(CATALOG + "/render").param("id", entry.id()).param("state", state), false);
			}
		}
		report.assertClean();
	}

	@Test
	public final void everyScreenTemplateHasSpec() {
		Report report = new Report();
		Set<String> specified = specs(report).stream().map(ScreenSpec::template).collect(Collectors.toSet());
		Set<String> manifest = FragmentManifest.load(getClass().getClassLoader())
			.fragments()
			.stream()
			.map((e) -> e.template() + " :: " + e.fragmentName())
			.collect(Collectors.toSet());
		for (TemplateFile file : CozyProject.appTemplates(templateRoot())) {
			String[] parts = file.path().split("/");
			if (parts.length < 2 || Set.of("cozy", "fragments", "error").contains(parts[0])) {
				continue;
			}
			String name = file.templateName();
			if (parts.length >= 3 && parts[1].equals("spi")) {
				for (String fragment : TemplateRules.declaredFragments(file)) {
					if (!manifest.contains(name + " :: " + fragment)) {
						report.fail("[C5] " + file.location() + " → " + name + " :: " + fragment + " — 모듈의 공개 fragment는 "
								+ "src/main/resources/cozy/fragments.yml에 적어야 합니다 (docs/conventions/ui.md).");
					}
				}
			}
			else if (parts.length >= 3 && parts[1].equals("api") && !specified.contains(name)) {
				String screen = ScreenSpec.screenName(name);
				report.fail("[C5] " + file.location() + " → " + name + " — 화면 스펙이 없습니다. src/main/resources/specs/"
						+ screen + ".yml을 만들고 default, empty 상태를 적으세요 (docs/conventions/testing.md).");
			}
		}
		String base = CozyProject.applicationClass(getClass()).getPackageName() + ".";
		for (FragmentManifest.Entry entry : FragmentManifest.load(getClass().getClassLoader()).fragments()) {
			String[] parts = entry.template().split("/");
			if (entry.isFramework() || parts.length < 3 || !parts[1].equals("spi")) {
				continue;
			}
			for (FragmentManifest.Param param : entry.params()) {
				String type = param.type();
				if (type.startsWith(base) && (type.contains(".api.") || type.contains(".internal."))) {
					report.fail("[C5] " + entry.source() + " " + entry.id() + " 파라미터 " + param.name() + ": " + type
							+ " — 공개 fragment는 다른 모듈이 부르므로 파라미터 타입은 spi DTO(" + parts[0]
							+ ".spi.dto)나 기본 타입이어야 합니다.");
				}
			}
		}
		report.assertClean();
	}

	private List<ScreenSpec> specs(Report report) {
		try {
			return ScreenSpec.load(getClass().getClassLoader());
		}
		catch (RuntimeException ex) {
			report.fail("[C3] " + ex.getMessage());
			report.assertClean();
			return List.of();
		}
	}

	private void render(Report report, String where, MockHttpServletRequestBuilder request, boolean fragment) {
		String html;
		try {
			MockHttpServletResponse response = this.mvc.perform(request).andReturn().getResponse();
			response.setCharacterEncoding("UTF-8");
			if (response.getStatus() != 200) {
				report.fail("[C3] " + where + " — 렌더링 응답이 " + response.getStatus() + "입니다.");
				return;
			}
			html = response.getContentAsString();
		}
		catch (Exception ex) {
			report.fail("[C3] " + where + " — 렌더링하지 못했습니다: " + rootMessage(ex));
			return;
		}
		List<CozyHtmlValidator.Message> messages = fragment ? this.validator.validateFragment(html)
				: this.validator.validate(html);
		for (CozyHtmlValidator.Message message : messages) {
			if (message.isError()) {
				report.fail("[C4] " + where + " — " + message.format());
			}
			else {
				report.warn("[C4 경고] " + where + " — " + message.format());
			}
		}
	}

	private static String rootMessage(Throwable ex) {
		List<String> messages = new ArrayList<>();
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t.getMessage() != null && (t.getMessage().contains("[cozy]") || t.getCause() == null)) {
				messages.add(t.getMessage());
			}
		}
		return messages.isEmpty() ? ex.toString() : String.join(" / ", new LinkedHashSet<>(messages));
	}

	/** 실패를 모두 모아 한 번에 낸다 */
	private static final class Report {

		private final List<String> failures = new ArrayList<>();

		void fail(String message) {
			this.failures.add(message);
		}

		void warn(String message) {
			System.out.println(message);
		}

		void assertClean() {
			if (!this.failures.isEmpty()) {
				throw new AssertionFailedError(this.failures.size() + "건 실패 (docs/conventions/testing.md)\n"
						+ String.join("\n", this.failures));
			}
		}
	}
}
