package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.rules.TemplateOutline.Node;

/** 템플릿 하나의 구조: fragment 선언과 삽입(th:replace, th:insert)을 겹친 깊이와 줄 번호로 읽는다. */
class TemplateOutlineTest {

	static List<Node> outline(String content) {
		return TemplateOutline.of(new TemplateFile("memo/api/list.html", Origin.APP, "templates/memo/api/list.html", content));
	}

	@Test
	void readsInsertionsWithDepthLineAndArguments() {
		List<Node> nodes = outline("""
				<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
				<main>
				  <section th:replace="~{cozy/pattern/list-with-filter :: listWithFilter('메모', ~{::#actions}, ~{::#filter}, ~{::#result})}">
				    <a id="actions" th:href="@{/memo/new}"><svg th:replace="~{cozy/icon/tabler :: icon('plus')}"></svg>만들기</a>
				    <form id="filter"><input th:field="*{q}"></form>
				    <div id="result" th:fragment="result">
				      <div th:insert="~{cozy/component/pagination :: pagination(${result.page}, '/memo', '#result', '#filter')}"></div>
				    </div>
				  </section>
				</main>
				</html>
				""");
		assertThat(nodes).extracting(Node::kind, Node::target, Node::fragment, Node::line, Node::depth)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("replace", "cozy/layout/base", "appShell", 1, 0),
					org.assertj.core.groups.Tuple.tuple("replace", "cozy/pattern/list-with-filter", "listWithFilter", 3, 1),
					org.assertj.core.groups.Tuple.tuple("slot", "", null, 4, 2), org.assertj.core.groups.Tuple.tuple("replace", "cozy/icon/tabler", "icon", 4, 3),
					org.assertj.core.groups.Tuple.tuple("slot", "", null, 5, 2), org.assertj.core.groups.Tuple.tuple("slot", "", null, 6, 2),
					org.assertj.core.groups.Tuple.tuple("fragment", "", "result", 6, 3),
					org.assertj.core.groups.Tuple.tuple("insert", "cozy/component/pagination", "pagination", 7, 4));
		assertThat(nodes.get(1).args()).containsExactly("'메모'", "~{::#actions}", "~{::#filter}", "~{::#result}");
		assertThat(nodes).filteredOn((n) -> n.kind().equals("slot")).extracting(Node::id)
			.containsExactly("actions", "filter", "result");
		assertThat(nodes.get(7).args()).containsExactly("${result.page}", "'/memo'", "'#result'", "'#filter'");
	}

	@Test
	void anElementThatDeclaresAndInsertsGivesBothNodes() {
		List<Node> nodes = outline("""
				<div th:fragment="card(summary)" th:replace="~{cozy/component/stat :: stat(${summary.label}, ${summary.value})}"></div>
				""");
		assertThat(nodes).extracting(Node::kind, Node::fragment, Node::depth)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("fragment", "card", 0),
					org.assertj.core.groups.Tuple.tuple("replace", "stat", 1));
		assertThat(nodes.get(0).args()).containsExactly("summary");
	}

	@Test
	void sameTemplateAndUnreadableExpressions() {
		List<Node> nodes = outline("""
				<div th:replace="~{:: row}"></div>
				<div th:replace="~{this :: row(${x})}"></div>
				<div data-th-replace="memo/spi/card :: card"></div>
				<div th:replace="${dynamic}"></div>
				<div th:replace="~{cozy/layout/base}"></div>
				""");
		assertThat(nodes).extracting(Node::target, Node::fragment)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("", "row"), org.assertj.core.groups.Tuple.tuple("", "row"),
					org.assertj.core.groups.Tuple.tuple("memo/spi/card", "card"),
					org.assertj.core.groups.Tuple.tuple("${dynamic}", null),
					org.assertj.core.groups.Tuple.tuple("cozy/layout/base", null));
	}

	@Test
	void siblingsAfterAClosedElementReturnToTheOuterDepth() {
		List<Node> nodes = outline("""
				<section th:replace="~{cozy/pattern/details :: details('제목', ~{::#body})}">
				  <div id="body"><br><img src="/x.png"><span th:replace="~{cozy/component/badge :: badge('a', 'neutral')}"></span></div>
				</section>
				<div th:replace="~{cozy/component/alert :: alert('info', '끝')}"></div>
				""");
		assertThat(nodes).extracting(Node::fragment, Node::depth)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("details", 0), org.assertj.core.groups.Tuple.tuple(null, 1), org.assertj.core.groups.Tuple.tuple("badge", 2),
					org.assertj.core.groups.Tuple.tuple("alert", 0));
	}

	@Test
	void everyNodeKnowsTheLineWhereItsElementEnds() {
		List<Node> nodes = outline("""
				<section th:replace="~{cozy/pattern/details :: details('제목', ~{::#body})}">
				  <div id="body">
				    <span th:replace="~{cozy/component/badge :: badge('a', 'neutral')}"></span>
				    <input th:insert="~{memo/spi/x :: x}">
				  </div>
				</section>
				""");
		assertThat(nodes).extracting(Node::fragment, Node::line, Node::endLine)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("details", 1, 6), org.assertj.core.groups.Tuple.tuple(null, 2, 5),
					org.assertj.core.groups.Tuple.tuple("badge", 3, 3), org.assertj.core.groups.Tuple.tuple("x", 4, 4));
	}

	@Test
	void argumentsSplitOnlyAtTheTopLevel() {
		assertThat(TemplateOutline.splitArguments("'a, b', ~{::#x}, ${m.get('k', 1)}, #{msg(${a}, 'b')}"))
			.containsExactly("'a, b'", "~{::#x}", "${m.get('k', 1)}", "#{msg(${a}, 'b')}");
		assertThat(TemplateOutline.splitArguments("  ")).isEmpty();
	}
}
