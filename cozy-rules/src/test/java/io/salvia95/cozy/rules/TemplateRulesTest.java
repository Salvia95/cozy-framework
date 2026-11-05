package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 규칙마다 통과 예와 위반 예 (PRD §11 테스트: 규칙마다 통과·위반 픽스처 각 1개 이상).
 */
class TemplateRulesTest {

	static final String BADGE = """
			<span th:fragment="badge(text, color)" class="badge" th:text="${text}">x</span>""";

	static final String FORM_PAGE = """
			<section th:fragment="formPage(cozyHeading, cozyForm)">
			  <h2 th:text="${cozyHeading}">h</h2>
			  <th:block th:replace="${cozyForm}"></th:block>
			</section>""";

	static final String BASE = """
			<html th:fragment="base(cozyTitle, cozyContent)"><body>
			<th:block th:replace="${cozyContent}"></th:block>
			</body></html>
			<th:block th:fragment="appShell(cozyTitle, cozyContent)"
			  th:replace="~{cozy/layout/base :: base(${cozyTitle}, ~{cozy/layout/base :: shellBody(${cozyContent})})}"></th:block>
			<div th:fragment="shellBody(cozyShellContent)"><th:block th:replace="${cozyShellContent}"></th:block></div>""";

	/** 프레임워크 템플릿 세 개와 그 매니페스트 */
	static Fixture fixture() {
		Fixture f = new Fixture();
		f.framework("cozy/layout/base.html", BASE);
		f.framework("cozy/pattern/form-page.html", FORM_PAGE);
		f.framework("cozy/component/badge.html", BADGE);
		f.manifest("layout/base", "cozy/layout/base", "base(cozyTitle, cozyContent)");
		f.manifest("layout/app-shell", "cozy/layout/base", "appShell(cozyTitle, cozyContent)");
		f.manifest("layout/shell-body", "cozy/layout/base", "shellBody(cozyShellContent)");
		f.manifest("pattern/form-page", "cozy/pattern/form-page", "formPage(cozyHeading, cozyForm)");
		f.manifest("component/badge", "cozy/component/badge", "badge(text, color)");
		f.modules = ModuleModel.of(new ModuleModel.Module("memo", List.of(), Set.of()),
				new ModuleModel.Module("wiki", List.of(), Set.of("api")),
				new ModuleModel.Module("voc", List.of("wiki::api"), Set.of()),
				new ModuleModel.Module("open", null, Set.of()));
		return f;
	}

	static final String SCREEN = """
			<!DOCTYPE html>
			<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
			<body><main>
			  <section th:replace="~{cozy/pattern/form-page :: formPage('새 메모', ~{::#form})}">
			    <form id="form"><span th:replace="~{cozy/component/badge :: badge('고정', 'blue')}"></span></form>
			  </section>
			  <div class="row" th:replace="~{this :: .row}"></div>
			</main></body>
			</html>""";

	@Test
	void frameworkAndScreenPass() {
		Fixture f = fixture();
		f.app("memo/list.html", SCREEN);
		assertThat(f.check()).isEmpty();
	}

	@Test
	void messageHasRuleLocationReferenceAndFix() {
		Fixture f = fixture();
		f.app("memo/list.html", "<p>\n<span th:replace=\"~{cozy/component/badge :: badg(x)}\"></span></p>");
		assertThat(f.check()).singleElement()
			.extracting(Violation::format)
			.asString()
			.startsWith("[R6] templates/memo/list.html:2 → cozy/component/badge :: badg(1개 인자) — ")
			.contains("있는 fragment: badge(text, color)");
	}

	@Nested
	class R1 {

		@Test
		void moduleAndReservedDirectoriesPass() {
			Fixture f = fixture();
			f.app("memo/list.html", "<p>x</p>");
			f.app("error/404.html", "<p>x</p>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void unknownTopDirectory() {
			Fixture f = fixture();
			f.app("vocc/list.html", "<p>x</p>");
			assertThat(f.rules()).containsExactly("R1");
			assertThat(f.check().get(0).message()).contains("memo, open, voc, wiki");
		}

		@Test
		void appMustNotWriteIntoCozy() {
			Fixture f = fixture();
			f.app("cozy/component/chip.html", "<p>x</p>");
			assertThat(f.rules()).containsExactly("R1");
		}

		@Test
		void fragmentsNeedLayerFolder() {
			Fixture f = fixture();
			f.app("fragments/widgets/chip.html", "<p>x</p>");
			assertThat(f.rules()).containsExactly("R1");
		}
	}

	@Nested
	class R2 {

		@Test
		void rootTemplateFails() {
			Fixture f = fixture();
			f.app("index.html", "<p>x</p>");
			assertThat(f.rules()).containsExactly("R2");
		}
	}

	@Nested
	class R3 {

		@Test
		void allowedApiReferencePasses() {
			Fixture f = fixture();
			f.app("wiki/api/card.html", "<div th:fragment=\"card(title)\" th:text=\"${title}\">c</div>");
			f.app("voc/list.html", "<div th:replace=\"~{wiki/api/card :: card('x')}\"></div>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void internalTemplateOfOtherModule() {
			Fixture f = fixture();
			f.app("wiki/detail.html", "<div th:fragment=\"body\">b</div>");
			f.app("voc/list.html", "<div th:replace=\"~{wiki/detail :: body}\"></div>");
			assertThat(f.rules()).containsExactly("R3");
			assertThat(f.check().get(0).message()).startsWith("wiki의 내부 템플릿입니다. wiki/api/ 아래 fragment를 쓰거나");
		}

		@Test
		void apiWithoutAllowedDependency() {
			Fixture f = fixture();
			f.app("wiki/api/card.html", "<div th:fragment=\"card(title)\">c</div>");
			f.app("memo/list.html", "<div th:replace=\"~{wiki/api/card :: card('x')}\"></div>");
			f.app("open/list.html", "<div th:replace=\"~{wiki/api/card :: card('x')}\"></div>");
			assertThat(f.rules()).containsExactly("R3", "R3");
			assertThat(f.check()).extracting(Violation::message)
				.anyMatch((m) -> m.startsWith("open는 allowedDependencies를 선언하지 않았습니다"))
				.anyMatch((m) -> m.contains("\"wiki::api\""));
		}
	}

	@Nested
	class R4 {

		@Test
		void patternMayUseComponent() {
			Fixture f = fixture();
			f.app("fragments/pattern/card.html", "<div th:fragment=\"card()\"><span th:replace=\"~{cozy/component/badge :: badge('a', 'b')}\"></span></div>");
			f.manifest("pattern/card", "fragments/pattern/card", "card()");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void componentMustNotUsePattern() {
			Fixture f = fixture();
			f.app("fragments/component/chip.html", "<div th:fragment=\"chip()\"><section th:replace=\"~{cozy/pattern/form-page :: formPage('a', ~{})}\"></section></div>");
			f.manifest("component/chip", "fragments/component/chip", "chip()");
			assertThat(f.rules()).containsExactly("R4");
		}

		@Test
		void sameLayerIsForbiddenUnlessAllowed() {
			Fixture f = fixture();
			f.app("fragments/component/chip.html", "<div th:fragment=\"chip()\"><span th:replace=\"~{cozy/component/badge :: badge('a', 'b')}\"></span></div>");
			f.manifest("component/chip", "fragments/component/chip", "chip()");
			assertThat(f.rules()).containsExactly("R4");
			f.allowSameLayer = true;
			assertThat(f.check()).isEmpty();
		}
	}

	@Nested
	class R5 {

		@Test
		void fragmentsMustNotUseModuleTemplates() {
			Fixture f = fixture();
			f.app("memo/api/y.html", "<div th:fragment=\"y()\">y</div>");
			f.app("fragments/pattern/x.html", "<div th:fragment=\"x()\"><div th:replace=\"~{memo/api/y :: y()}\"></div></div>");
			f.manifest("pattern/x", "fragments/pattern/x", "x()");
			f.manifest("memo/y", "memo/api/y", "y()");
			assertThat(f.rules()).containsExactly("R5");
		}

		@Test
		void cozyMustNotUseAppTemplates() {
			Fixture f = fixture();
			f.framework("cozy/component/bad.html", "<div th:fragment=\"bad()\"><div th:replace=\"~{fragments/component/x :: x()}\"></div></div>");
			f.manifest("component/bad", "cozy/component/bad", "bad()");
			assertThat(f.rules()).containsExactly("R5");
		}
	}

	@Nested
	class R6 {

		@Test
		void missingTemplateFragmentAndWrongArgCount() {
			Fixture f = fixture();
			f.app("memo/list.html", """
					<div th:replace="~{cozy/component/nope :: x()}"></div>
					<div th:replace="~{cozy/component/badge :: badg('a', 'b')}"></div>
					<div th:replace="~{cozy/component/badge :: badge('a')}"></div>
					<div th:replace="~{:: #missing}"></div>""");
			assertThat(f.rules()).containsExactly("R6", "R6", "R6", "R6");
		}

		@Test
		void elementSelectorsResolve() {
			Fixture f = fixture();
			f.app("memo/list.html", "<main id=\"m\" class=\"c\"><i th:replace=\"~{:: main}\"></i><i th:replace=\"~{:: #m}\"></i><i th:replace=\"~{:: .c}\"></i></main>");
			assertThat(f.check()).isEmpty();
		}
	}

	@Nested
	class R7 {

		@Test
		void dynamicReferences() {
			Fixture f = fixture();
			f.app("memo/list.html", """
					<div th:replace="~{${view} :: body}"></div>
					<div th:replace="~{cozy/component/badge :: ${name}}"></div>
					<div th:replace="${view}"></div>
					<div th:insert="~{|memo/${x}| :: a}"></div>
					<div th:replace="~{cozy/component/badge :: span[@id='x']}"></div>
					<p>[[~{cozy/component/badge}]]</p>""");
			assertThat(f.rules()).containsExactly("R7", "R7", "R7", "R7", "R7", "R7");
		}

		@Test
		void slotParameterIsNotDynamic() {
			Fixture f = fixture();
			f.app("memo/list.html", "<section th:fragment=\"part(body)\"><div><th:block th:replace=\"${body}\"></th:block></div></section>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void includeIsWarningOnly() {
			Fixture f = fixture();
			f.app("memo/list.html", "<div th:include=\"~{cozy/component/badge :: badge('a', 'b')}\"></div>");
			assertThat(f.check()).singleElement()
				.satisfies((v) -> assertThat(v.severity()).isEqualTo(Severity.WARNING))
				.satisfies((v) -> assertThat(v.ruleId()).isEqualTo("R7"));
		}

		@Test
		void unwrappedFragmentExpressionIsChecked() {
			Fixture f = fixture();
			f.app("memo/list.html", "<div th:replace=\"cozy/component/badge :: badge('a', 'b')\"></div><div th:replace=\"cozy/component/badge :: badge\"></div>");
			assertThat(f.rules()).containsExactly("R6");
		}
	}

	@Nested
	class R8 {

		@Test
		void undeclaredFragment() {
			Fixture f = fixture();
			f.app("fragments/component/chip.html", "<span th:fragment=\"chip(text)\">c</span>");
			assertThat(f.rules()).containsExactly("R8");
		}

		@Test
		void manifestMustMatchTemplate() {
			Fixture f = fixture();
			f.app("fragments/component/chip.html", "<span th:fragment=\"chip(text)\">c</span>");
			f.manifest("component/chip", "fragments/component/chip", "chip(label)");
			f.manifest("component/gone", "fragments/component/gone", "gone()");
			f.manifest("component/nope", "fragments/component/chip", "nope()");
			f.manifest("screen", "memo/list", "list()");
			assertThat(f.rules()).containsExactly("R8", "R8", "R8", "R8");
		}
	}

	/** 검사 입력을 조립한다 */
	static final class Fixture {

		final List<TemplateFile> templates = new ArrayList<>();

		final List<FragmentDeclaration> manifest = new ArrayList<>();

		ModuleModel modules = ModuleModel.of();

		boolean allowSameLayer;

		void app(String path, String content) {
			this.templates.add(new TemplateFile(path, Origin.APP, "templates/" + path, content));
		}

		void framework(String path, String content) {
			this.templates.add(new TemplateFile(path, Origin.FRAMEWORK, "[cozy-ui] templates/" + path, content));
		}

		void manifest(String id, String template, String signature) {
			this.manifest.add(new FragmentDeclaration(id, template, signature, "cozy/fragments.yml", this.manifest.size() + 1));
		}

		List<Violation> check() {
			return TemplateRules.check(new RulesInput(this.templates, this.modules, this.manifest, this.allowSameLayer));
		}

		List<String> rules() {
			List<Violation> violations = check();
			violations.forEach(System.out::println);
			return violations.stream().map(Violation::ruleId).toList();
		}
	}
}
