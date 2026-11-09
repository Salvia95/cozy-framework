package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 규칙마다 통과 예와 위반 예: 규칙마다 통과·위반 픽스처가 각각 하나 이상 있다.
 */
class TemplateRulesTest {

	static final String BADGE = """
			<span th:fragment="badge(text, tone)" class="badge" th:text="${text}">x</span>""";

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
		f.manifest("component/badge", "cozy/component/badge", "badge(text, tone)");
		f.modules = ModuleModel.of(new ModuleModel.Module("memo", List.of(), Set.of()),
				new ModuleModel.Module("wiki", List.of(), Set.of("spi")),
				new ModuleModel.Module("voc", List.of("wiki :: spi"), Set.of()),
				new ModuleModel.Module("open", null, Set.of()));
		return f;
	}

	static final String SCREEN = """
			<!DOCTYPE html>
			<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
			<body><main>
			  <section th:replace="~{cozy/pattern/form-page :: formPage('새 메모', ~{::#form})}">
			    <form id="form"><span th:replace="~{cozy/component/badge :: badge('고정', 'neutral')}"></span></form>
			  </section>
			  <div class="row" th:replace="~{this :: .row}"></div>
			</main></body>
			</html>""";

	@Test
	void frameworkAndScreenPass() {
		Fixture f = fixture();
		f.app("memo/api/list.html", SCREEN);
		assertThat(f.check()).isEmpty();
	}

	@Test
	void messageHasRuleLocationReferenceAndFix() {
		Fixture f = fixture();
		f.app("memo/api/list.html", "<p>\n<span th:replace=\"~{cozy/component/badge :: badg(x)}\"></span></p>");
		assertThat(f.check()).singleElement()
			.extracting(Violation::format)
			.asString()
			.startsWith("[R6] templates/memo/api/list.html:2 → cozy/component/badge :: badg(1개 인자) — ")
			.contains("있는 fragment: badge(text, tone)");
	}

	@Nested
	class R1 {

		@Test
		void moduleAndReservedDirectoriesPass() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<p>x</p>");
			f.app("memo/api/sub/detail.html", "<p>x</p>");
			f.app("memo/internal/row.html", "<tr th:fragment=\"row()\"><td>x</td></tr>");
			f.app("error/404.html", "<p>x</p>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void moduleTemplatesLiveInApiSpiOrInternal() {
			Fixture f = fixture();
			f.app("memo/list.html", "<p>x</p>");
			f.app("memo/views/detail.html", "<p>x</p>");
			assertThat(f.rules()).containsExactly("R1", "R1");
			assertThat(f.check().get(0).message()).contains("memo/api/").contains("memo/spi/").contains("memo/internal/");
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
		void allowedSpiReferencePasses() {
			Fixture f = fixture();
			f.app("wiki/spi/card.html", "<div th:fragment=\"card(title)\" th:text=\"${title}\">c</div>");
			f.app("voc/api/list.html", "<div th:replace=\"~{wiki/spi/card :: card('x')}\"></div>");
			f.app("voc/spi/panel.html", "<div th:fragment=\"panel()\"><div th:replace=\"~{wiki/spi/card :: card('x')}\"></div></div>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void apiAndInternalTemplatesOfOtherModule() {
			Fixture f = fixture();
			f.app("wiki/api/detail.html", "<div th:fragment=\"body\">b</div>");
			f.app("wiki/internal/row.html", "<div th:fragment=\"row()\">b</div>");
			f.app("voc/api/list.html", "<div th:replace=\"~{wiki/api/detail :: body}\"></div><div th:replace=\"~{wiki/internal/row :: row()}\"></div>");
			assertThat(f.rules()).containsExactly("R3", "R3");
			assertThat(f.check().get(0).message()).startsWith("wiki/api/는 wiki 안에서만 씁니다. 다른 모듈에는 wiki/spi/ 아래 fragment만 공개됩니다");
		}

		@Test
		void spiWithoutAllowedDependency() {
			Fixture f = fixture();
			f.app("wiki/spi/card.html", "<div th:fragment=\"card(title)\">c</div>");
			f.app("memo/api/list.html", "<div th:replace=\"~{wiki/spi/card :: card('x')}\"></div>");
			f.app("open/api/list.html", "<div th:replace=\"~{wiki/spi/card :: card('x')}\"></div>");
			assertThat(f.rules()).containsExactly("R3", "R3");
			assertThat(f.check()).extracting(Violation::message)
				.anyMatch((m) -> m.startsWith("open는 allowedDependencies를 선언하지 않았습니다"))
				.anyMatch((m) -> m.contains("\"wiki :: spi\""));
		}

		@Test
		void aMissingDependencyComesWithTheCommandThatAddsIt() {
			Fixture f = fixture();
			f.app("wiki/spi/card.html", "<div th:fragment=\"card(title)\">c</div>");
			f.app("memo/api/list.html", "<div th:replace=\"~{wiki/spi/card :: card('x')}\"></div>");
			Violation v = f.check().getFirst();
			assertThat(v.command()).isEqualTo("cozy link memo --uses wiki");
			assertThat(v.format()).endsWith("에 추가하세요. 고치는 명령: cozy link memo --uses wiki");
		}

		@Test
		void otherModulesInternalTemplatesHaveNoCommand() {
			Fixture f = fixture();
			f.app("wiki/api/detail.html", "<div th:fragment=\"body\">b</div>");
			f.app("voc/api/list.html", "<div th:replace=\"~{wiki/api/detail :: body}\"></div>");
			assertThat(f.check()).extracting(Violation::command).containsOnlyNulls();
		}

		@Test
		void oldApiDependencyDoesNotOpenSpi() {
			Fixture f = fixture();
			f.modules = ModuleModel.of(new ModuleModel.Module("wiki", List.of(), Set.of("spi")),
					new ModuleModel.Module("voc", List.of("wiki::api"), Set.of()));
			f.app("wiki/spi/card.html", "<div th:fragment=\"card(title)\">c</div>");
			f.app("voc/api/list.html", "<div th:replace=\"~{wiki/spi/card :: card('x')}\"></div>");
			assertThat(f.rules()).containsExactly("R3");
		}
	}

	@Nested
	class R4 {

		@Test
		void patternMayUseComponent() {
			Fixture f = fixture();
			f.app("fragments/pattern/card.html", "<div th:fragment=\"card()\"><span th:replace=\"~{cozy/component/badge :: badge('a', 'neutral')}\"></span></div>");
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
			f.app("fragments/component/chip.html", "<div th:fragment=\"chip()\"><span th:replace=\"~{cozy/component/badge :: badge('a', 'neutral')}\"></span></div>");
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
			f.app("memo/spi/y.html", "<div th:fragment=\"y()\">y</div>");
			f.app("fragments/pattern/x.html", "<div th:fragment=\"x()\"><div th:replace=\"~{memo/spi/y :: y()}\"></div></div>");
			f.manifest("pattern/x", "fragments/pattern/x", "x()");
			f.manifest("memo/y", "memo/spi/y", "y()");
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
			f.app("memo/api/list.html", """
					<div th:replace="~{cozy/component/nope :: x()}"></div>
					<div th:replace="~{cozy/component/badge :: badg('a', 'b')}"></div>
					<div th:replace="~{cozy/component/badge :: badge('a')}"></div>
					<div th:replace="~{:: #missing}"></div>""");
			assertThat(f.rules()).containsExactly("R6", "R6", "R6", "R6");
		}

		@Test
		void elementSelectorsResolve() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<main id=\"m\" class=\"c\"><i th:replace=\"~{:: main}\"></i><i th:replace=\"~{:: #m}\"></i><i th:replace=\"~{:: .c}\"></i></main>");
			assertThat(f.check()).isEmpty();
		}
	}

	@Nested
	class R7 {

		@Test
		void dynamicReferences() {
			Fixture f = fixture();
			f.app("memo/api/list.html", """
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
			f.app("memo/api/list.html", "<section th:fragment=\"part(body)\"><div><th:block th:replace=\"${body}\"></th:block></div></section>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void includeIsWarningOnly() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<div th:include=\"~{cozy/component/badge :: badge('a', 'neutral')}\"></div>");
			assertThat(f.check()).singleElement()
				.satisfies((v) -> assertThat(v.severity()).isEqualTo(Severity.WARNING))
				.satisfies((v) -> assertThat(v.ruleId()).isEqualTo("R7"));
		}

		@Test
		void unwrappedFragmentExpressionIsChecked() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<div th:replace=\"cozy/component/badge :: badge('a', 'neutral')\"></div><div th:replace=\"cozy/component/badge :: badge\"></div>");
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
			f.manifest("screen", "memo/api/list", "list()");
			assertThat(f.rules()).containsExactly("R8", "R8", "R8", "R8");
		}
	}

	@Nested
	class R8Spi {

		@Test
		void moduleSpiFragmentsMayBeListedButScreensMayNot() {
			Fixture f = fixture();
			f.app("memo/spi/card.html", "<div th:fragment=\"card(memo)\">c</div>");
			f.app("memo/internal/row.html", "<div th:fragment=\"row()\">r</div>");
			f.manifest("memo/card", "memo/spi/card", "card(memo)");
			assertThat(f.check()).isEmpty();
			f.manifest("memo/row", "memo/internal/row", "row()");
			assertThat(f.rules()).containsExactly("R8");
		}
	}

	@Nested
	class R9 {

		static final String ROW = "<tr th:fragment=\"row(m)\"><td th:text=\"${m}\">x</td></tr>";

		@Test
		void apiComposesOwnSpiAndInternalAndSpiComposesInternal() {
			Fixture f = fixture();
			f.app("memo/internal/row.html", ROW);
			f.app("memo/internal/table.html", """
					<table th:fragment="memoTable(items)"><th:block th:each="i : ${items}"><tr th:replace="~{memo/internal/row :: row(${i})}"></tr></th:block></table>""");
			f.app("memo/spi/board.html", """
					<section th:fragment="board(items)"><table th:replace="~{memo/internal/table :: memoTable(${items})}"></table></section>""");
			f.app("memo/api/list.html", """
					<main><section th:replace="~{memo/spi/board :: board(${memos})}"></section>
					<table th:replace="~{memo/internal/table :: memoTable(${memos})}"></table>
					<div th:replace="~{memo/api/detail :: body}"></div></main>""");
			f.app("memo/api/detail.html", "<div th:fragment=\"body\">d</div>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void spiMustNotUseApi() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<div th:fragment=\"result\">r</div>");
			f.app("memo/spi/board.html", "<section th:fragment=\"board()\"><div th:replace=\"~{memo/api/list :: result}\"></div></section>");
			assertThat(f.rules()).containsExactly("R9");
			assertThat(f.check().get(0).message()).contains("spi는 같은 모듈의 api를 참조하지 않습니다");
		}

		@Test
		void internalMustNotUseSpiApiOrOtherModules() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<div th:fragment=\"result\">r</div>");
			f.app("memo/spi/card.html", "<div th:fragment=\"card()\">c</div>");
			f.app("wiki/spi/card.html", "<div th:fragment=\"card(title)\">c</div>");
			f.app("voc/internal/row.html", "<div th:fragment=\"row()\"><div th:replace=\"~{wiki/spi/card :: card('x')}\"></div></div>");
			f.app("memo/internal/row.html", """
					<div th:fragment="row()"><div th:replace="~{memo/spi/card :: card()}"></div><div th:replace="~{memo/api/list :: result}"></div></div>""");
			assertThat(f.rules()).containsExactly("R9", "R9", "R9");
			assertThat(f.check()).extracting(Violation::message)
				.anyMatch((m) -> m.startsWith("internal은 다른 모듈의 템플릿을 쓰지 않습니다"));
		}
	}

	@Nested
	class R10 {

		@Test
		void paramsLocalsAndUtilitiesPass() {
			Fixture f = fixture();
			f.app("memo/spi/card.html", """
					<div th:fragment="card(memos, form, cozyActions)" th:object="${form}">
					  <p th:text="${#lists.size(memos)} + ' / ' + ${memos.?[pinned].size()}">n</p>
					  <a th:href="@{/memo/{id}(id=${memos[0].id})}" th:text="${'제목: ' + memos[0].title}">t</a>
					  <th:block th:each="m, st : ${memos}"><span th:text="${m.title} + ${st.index}">x</span></th:block>
					  <th:block th:each="m : ${memos}"><span th:text="${mStat.count}">x</span></th:block>
					  <p th:with="first=${memos[0]}, title=${first.title}" th:text="${title}">x</p>
					  <input th:field="*{title}">
					  <p th:text="${T(java.lang.Math).max(1, #lists.size(memos))} ?: ${cozyApp}">x</p>
					  <p>[[${memos.size()}]] [(${form.title})]</p>
					  <th:block th:replace="${cozyActions}"></th:block>
					  <p th:text="${memos.size() > 0 ? 'yes' : 'no'}">x</p>
					</div>""");
			f.manifest("memo/card", "memo/spi/card", "card(memos, form, cozyActions)");
			f.app("memo/api/list.html", "<main th:text=\"${anything.from.model}\">x</main>");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void modelReadsInSpiAndInternalFail() {
			Fixture f = fixture();
			f.app("memo/spi/card.html", """
					<div th:fragment="card(summary)">
					  <p th:text="${summary.total} + ${memos.size()}">x</p>
					  <input th:field="*{title}">
					  <p th:text="${#vars.memos}">x</p>
					  <p th:text="${@memoService.count()}">x</p>
					  <p th:text="${param.q}">x</p>
					</div>""");
			f.app("memo/internal/row.html", """
					<p th:text="${outside}">x</p>
					<div th:each="x : ${list}"><span th:fragment="row()">[[${x}]]</span></div>""");
			List<Violation> violations = f.check();
			violations.forEach(System.out::println);
			assertThat(violations).extracting(Violation::ruleId).containsOnly("R10");
			assertThat(violations).extracting(Violation::reference)
				.containsExactlyInAnyOrder("${memos.size()} → memos", "*{title}", "${#vars.memos} → #vars",
						"${@memoService.count()} → @memoService", "${param.q} → param", "${outside} → outside",
						"${list} → list", "${x} → x");
			assertThat(violations).extracting(Violation::message)
				.anyMatch((m) -> m.startsWith("'memos'는 card(summary)의 파라미터나 지역 변수가 아닙니다"));
		}
	}

	@Nested
	class R11 {

		@Test
		void eachWithInsertionOnSameElementFails() {
			Fixture f = fixture();
			f.app("memo/internal/row.html", "<tr th:fragment=\"row(m)\"><td th:text=\"${m}\">x</td></tr>");
			f.app("memo/api/list.html", """
					<table><tr th:each="m : ${memos}" th:replace="~{memo/internal/row :: row(${m})}"></tr>
					<th:block th:each="m : ${memos}"><tr th:replace="~{memo/internal/row :: row(${m})}"></tr></th:block></table>""");
			assertThat(f.rules()).containsExactly("R11");
			assertThat(f.check().get(0).message()).contains("th:block th:each");
		}
	}

	@Nested
	class R12 {

		@Test
		void fragmentNamedLikeAnotherElementInTheFileFails() {
			Fixture f = fixture();
			f.app("memo/api/detail.html", """
					<div><form action="/memo/1/delete" method="post"><button>삭제</button></form>
					<form id="edit" th:fragment="form"><input name="title"></form></div>""");
			assertThat(f.rules()).containsExactly("R12");
			assertThat(f.check().get(0).format()).contains("th:fragment=\"form\"").contains("<form>");
		}

		@Test
		void fragmentNameThatIsNoElementOfTheFilePasses() {
			Fixture f = fixture();
			f.app("memo/api/detail.html", "<div><form id=\"edit\" th:fragment=\"memoForm\"><input name=\"title\"></form></div>");
			assertThat(f.check()).isEmpty();
		}
	}

	@Nested
	class R14 {

		@Test
		void appTemplateMustNotPickColorsDirectly() {
			Fixture f = fixture();
			f.app("memo/api/list.html", """
					<div><span class="badge bg-blue-lt">고정</span>
					<span class="avatar text-white" th:classappend="${ok} ? 'bg-green'">A</span>
					<a class="btn btn-outline-danger" href="/x">삭제</a></div>""");
			assertThat(f.rules()).containsExactly("R14", "R14", "R14", "R14");
			assertThat(f.check().get(0).format()).contains("bg-blue-lt").contains("tone");
		}

		@Test
		void unknownToneLiteralFails() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<p><span th:replace=\"~{cozy/component/badge :: badge('고정', 'blue')}\"></span>"
					+ "<span th:replace=\"~{cozy/component/badge :: badge('고정', 'neutral')}\"></span></p>");
			assertThat(f.rules()).containsExactly("R14");
			assertThat(f.check().get(0).format()).contains("'blue'").contains("neutral, info, success, warning, critical");
		}

		@Test
		void colorBuiltFromAnExpressionFailsClosed() {
			Fixture f = fixture();
			f.app("memo/api/list.html", "<span class=\"badge\" th:classappend=\"|bg-${color}-lt|\">x</span>");
			assertThat(f.rules()).containsExactly("R14");
		}

		@Test
		void neutralAndPrimaryClassesPass() {
			Fixture f = fixture();
			f.app("memo/api/list.html", """
					<div class="card"><p class="text-secondary">설명</p><a class="text-reset" href="/m">메모</a>
					<a class="btn btn-primary" href="/memo/new">메모 만들기</a><a class="btn" href="/memo">목록</a>
					<input class="form-control" th:classappend="${bad} ? 'is-invalid'"></div>""");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void frameworkTemplatesImplementTonesAndAreExempt() {
			Fixture f = fixture();
			f.framework("cozy/component/tone.html", "<span class=\"badge bg-green-lt\" th:fragment=\"tone(text, tone)\">x</span>");
			f.manifest("component/tone", "cozy/component/tone", "tone(text, tone)");
			assertThat(f.check()).isEmpty();
		}
	}

	@Nested
	class R15 {

		@Test
		void primaryActionMustBeLastInItsGroup() {
			Fixture f = fixture();
			f.app("memo/internal/fields.html", """
					<div class="btn-list"><button class="btn btn-primary" type="submit">저장</button>
					<a class="btn" href="/memo">취소</a></div>""");
			assertThat(f.rules()).containsExactly("R15");
			assertThat(f.check().get(0).format()).contains("오른쪽");
		}

		@Test
		void onlyOnePrimaryActionPerGroup() {
			Fixture f = fixture();
			f.app("memo/api/detail.html", """
					<div class="btn-list"><a class="btn btn-primary" href="/a">하나</a>
					<form class="m-0" method="post" action="/b"><button class="btn btn-primary">둘</button></form></div>""");
			assertThat(f.rules()).containsExactly("R15");
			assertThat(f.check().get(0).format()).contains("하나만");
		}

		@Test
		void insertedActionAfterThePrimaryCountsAsAnAction() {
			Fixture f = fixture();
			f.framework("cozy/component/x.html", "<a class=\"btn\" th:fragment=\"x()\">x</a>");
			f.manifest("component/x", "cozy/component/x", "x()");
			f.app("memo/api/detail.html", """
					<div class="btn-list"><a class="btn btn-primary" href="/a">수정</a><a th:replace="~{cozy/component/x :: x()}">x</a></div>""");
			assertThat(f.rules()).containsExactly("R15");
		}

		@Test
		void primaryLastAndNestedFormsPass() {
			Fixture f = fixture();
			f.app("memo/api/detail.html", """
					<div class="btn-list justify-content-end"><a class="btn" href="/memo">취소</a>
					<form class="m-0" method="post" action="/x"><button class="btn">보관</button></form>
					<button class="btn btn-primary" type="submit">저장</button></div>""");
			assertThat(f.check()).isEmpty();
		}
	}

	@Nested
	class IconLayer {

		static final String ICON = """
				<svg class="icon" th:fragment="icon(name)"><use th:href="@{/x.svg} + '#tabler-' + ${name}"></use></svg>
				<svg class="icon" th:fragment="iconFilled(name)"><use th:href="@{/y.svg} + '#tabler-filled-' + ${name}"></use></svg>""";

		Fixture withIcons() {
			Fixture f = fixture();
			f.framework("cozy/icon/tabler.html", ICON);
			f.manifest("icon/tabler", "cozy/icon/tabler", "icon(name)");
			f.manifest("icon/tabler-filled", "cozy/icon/tabler", "iconFilled(name)");
			f.icons = Set.of("plus", "home");
			f.filledIcons = Set.of("star");
			return f;
		}

		@Test
		void componentMayUseIconAndKnownNamesPass() {
			Fixture f = withIcons();
			f.app("fragments/component/chip.html", """
					<span th:fragment="chip(icon)"><svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>
					<svg th:replace="~{cozy/icon/tabler :: iconFilled('star')}"></svg>
					<svg th:replace="~{cozy/icon/tabler :: icon(${icon})}"></svg></span>""");
			f.manifest("component/chip", "fragments/component/chip", "chip(icon)");
			assertThat(f.check()).isEmpty();
		}

		@Test
		void iconMustNotUseComponent() {
			Fixture f = withIcons();
			f.app("fragments/icon/dot.html", "<span th:fragment=\"dot()\"><span th:replace=\"~{cozy/component/badge :: badge('a', 'neutral')}\"></span></span>");
			f.manifest("icon/dot", "fragments/icon/dot", "dot()");
			assertThat(f.rules()).containsExactly("R4");
		}

		@Test
		void unknownIconNameFails() {
			Fixture f = withIcons();
			f.app("memo/api/list.html", """
					<p><svg th:replace="~{cozy/icon/tabler :: icon('pluss')}"></svg>
					<svg th:replace="~{cozy/icon/tabler :: iconFilled('home')}"></svg></p>""");
			assertThat(f.rules()).containsExactly("R13", "R13");
			assertThat(f.check().get(0).format()).contains("'pluss'").contains("비슷한 이름: plus");
			assertThat(f.check().get(1).format()).contains("filled");
		}

		@Test
		void iconNamesMissingFailClosed() {
			Fixture f = withIcons();
			f.icons = Set.of();
			f.app("memo/api/list.html", "<p><svg th:replace=\"~{cozy/icon/tabler :: icon('plus')}\"></svg></p>");
			assertThat(f.rules()).containsExactly("R13");
		}
	}

	/** 검사 입력을 조립한다 */
	static final class Fixture {

		final List<TemplateFile> templates = new ArrayList<>();

		final List<FragmentDeclaration> manifest = new ArrayList<>();

		ModuleModel modules = ModuleModel.of();

		boolean allowSameLayer;

		Set<String> icons = Set.of();

		Set<String> filledIcons = Set.of();

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
			return TemplateRules.check(new RulesInput(this.templates, this.modules, this.manifest, this.allowSameLayer,
					this.icons, this.filledIcons));
		}

		List<String> rules() {
			List<Violation> violations = check();
			violations.forEach(System.out::println);
			return violations.stream().map(Violation::ruleId).toList();
		}
	}
}
