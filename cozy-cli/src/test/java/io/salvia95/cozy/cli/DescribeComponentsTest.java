package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * describe의 화면별 Alpine 컴포넌트: 화면 템플릿에 직접 쓴 것과, 넣은 앱 fragment 안의 것을 쓴 곳(템플릿, 줄)과 함께 보인다.
 */
class DescribeComponentsTest {

	static final String T = "src/main/resources/templates/";

	@TempDir
	Path dir;

	App app;

	@BeforeEach
	void app() throws IOException {
		this.app = App.of(this.dir).module("memo", "{}");
	}

	String json() {
		Result result = CozyCliTest.run("describe", "--json", "--project", this.dir.toString());
		assertThat(result.code()).as(result.err()).isZero();
		return result.out().replaceAll("\\s+", " ");
	}

	String text() {
		Result result = CozyCliTest.run("describe", "--project", this.dir.toString());
		assertThat(result.code()).as(result.err()).isZero();
		return result.out();
	}

	static String component(String name, String template, int line) {
		return "{ \"name\": \"" + name + "\", \"template\": \"" + template + "\", \"line\": " + line + " }";
	}

	@Test
	void componentsInIncludedAppFragmentsAreListedWhereTheyAreWritten() throws IOException {
		this.app.file(T + "memo/api/form.html", """
				<html th:replace="~{cozy/layout/base :: appShell('메모', ~{::main})}">
				<main>
				  <!--/* x-data="memoInComment" 주석은 보지 않는다 */-->
				  <form th:replace="~{cozy/pattern/form-page :: formPage('메모', '메모', '/memo', ~{::#f})}">
				    <form id="f">
				      <div th:replace="~{memo/internal/fields :: memoFields()}"></div>
				      <button type="button" x-data="cozyCopy" x-on:click="copy" data-copy-text="a">복사</button>
				      <div th:replace="~{memo/internal/fields :: memoFields()}"></div>
				    </form>
				  </form>
				</main>
				</html>
				""")
			.file(T + "memo/internal/fields.html", """
				<div th:fragment="memoFields()">
				  <div x-data="memoBodyCount" data-limit="5000">
				    <div th:replace="~{::memoHint}"></div>
				  </div>
				</div>
				<p th:fragment="memoOther()" x-data="memoUnused"></p>
				<p th:fragment="memoHint" x-data="memoHint"></p>
				""");
		String json = json();
		// 문서 순서. 두 번 넣은 fragment는 한 번만. 같은 파일 안의 fragment(~{::memoHint})도 따라간다. 넣지 않은 fragment는 빠진다
		assertThat(json).contains("\"components\": [ " + component("memoBodyCount", "memo/internal/fields", 2) + ", "
				+ component("memoHint", "memo/internal/fields", 7) + ", " + component("cozyCopy", "memo/api/form", 7) + " ]")
			.doesNotContain("memoUnused")
			.doesNotContain("memoInComment");
		assertThat(text()).contains("· 컴포넌트 memoBodyCount(memo/internal/fields:2), memoHint(memo/internal/fields:7), cozyCopy(memo/api/form:7)");
	}

	@Test
	void aWholeTemplateIsReadWhenNoFragmentIsNamedAndCyclesStop() throws IOException {
		this.app.file(T + "memo/api/list.html", """
				<main>
				  <div th:insert="~{memo/internal/panel}"></div>
				</main>
				""")
			.file(T + "memo/internal/panel.html", """
				<section x-data="memoPanel">
				  <div th:replace="~{memo/internal/panel :: memoLoop}"></div>
				  <div th:fragment="memoLoop"><div th:replace="~{memo/internal/panel}"></div></div>
				</section>
				""");
		assertThat(json()).contains("\"components\": [ " + component("memoPanel", "memo/internal/panel", 1) + " ]");
	}

	@Test
	void frameworkFragmentsAndMissingTemplatesAreNotFollowed() throws IOException {
		this.app.file(T + "memo/api/detail.html", """
				<main th:replace="~{cozy/pattern/details :: details('메모', ~{::section})}">
				  <section><div th:replace="~{memo/internal/missing :: nothing}"></div></section>
				</main>
				""");
		String json = json();
		assertThat(json).contains("\"template\": \"memo/api/detail\"").contains("\"components\": []");
		assertThat(text()).contains("화면 memo/api/detail — 스펙 없음\n");
	}
}
