package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.Result;

/** {@code cozy set arg}: fragment 호출의 문자열 인자 하나만 바꾼다. 표현식, 바뀐 템플릿, 정해진 값 밖의 값은 거절한다 */
class SetArgCommandTest {

	@TempDir
	Path dir;

	static final String LIST = "src/main/resources/templates/memo/api/list.html";

	static final String TEMPLATE = """
			<!DOCTYPE html>
			<html th:replace="~{cozy/layout/app-shell :: appShell('메모', ~{::main})}">
			<body>
			<main>
			  <span th:replace="~{cozy/component/badge :: badge('고정', 'neutral')}"></span>
			  <div th:replace="~{cozy/component/empty-state :: emptyStateAction('notes', '메모가 없습니다',
			      '첫 메모를 만들어 보세요.', ~{::#action})}"><a id="action">만들기</a></div>
			  <a th:href="@{/memo}" th:replace="~{cozy/component/delete-confirm :: deleteConfirm('memo-delete', '메모', ${memo.title}, '/memo/' + ${memo.id})}"></a>
			  <div th:replace="~{memo/spi/card :: memoCard('제목', 'compact')}"></div>
			</main>
			</body>
			</html>
			""";

	@BeforeEach
	void app() throws IOException {
		write("pom.xml", """
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <parent>
				    <groupId>io.salvia95.cozy</groupId>
				    <artifactId>cozy-parent</artifactId>
				    <version>%s</version>
				  </parent>
				  <artifactId>test-app</artifactId>
				  <dependencies>
				    <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-ui</artifactId></dependency>
				  </dependencies>
				</project>
				""".formatted(CozyCliTest.VERSION));
		write("src/main/java/it/cozy/app/App.java", "package it.cozy.app;\n\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
				+ "@SpringBootApplication\npublic class App {\n}\n");
		write("src/main/resources/cozy/fragments.yml", """
				fragments:
				  - id: memo/card
				    template: memo/spi/card
				    signature: memoCard(title, size)
				    purpose: 메모 카드
				    params:
				      - { name: title, type: String, required: true, description: 제목 }
				      - { name: size, type: String, required: true, description: 크기, choices: [compact, wide] }
				    samples:
				      default: { title: 제목, size: compact }
				""");
		write(LIST, TEMPLATE);
	}

	Result cozy(String... args) {
		List<String> all = new ArrayList<>(List.of(args));
		all.addAll(List.of("--project", this.dir.toString()));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(all.toArray(String[]::new), Path.of(""), CozyCliTest.REPOSITORY,
				new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	Result set(String line, String fragment, String index, String from, String value, String... more) {
		List<String> args = new ArrayList<>(List.of("set", "arg", "memo/api/list", "--line", line, "--fragment", fragment, "--index", index,
				"--from", from, "--value", value));
		args.addAll(List.of(more));
		return cozy(args.toArray(String[]::new));
	}

	String list() {
		try {
			return Files.readString(this.dir.resolve(LIST), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	void write(String path, String content) throws IOException {
		Path file = this.dir.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	void refused(Result result, String message) {
		assertThat(result.code()).as(result.out()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains(message);
		assertThat(list()).as("거절하면 템플릿은 그대로다").isEqualTo(TEMPLATE);
	}

	@Test
	void onlyTheLiteralChangesAndTheRestOfTheFileStaysByteForByte() {
		Result plan = set("5", "badge", "2", "neutral", "info", "--dry-run");
		assertThat(plan.code()).as(plan.err()).isZero();
		assertThat(plan.out()).contains("바꿈  " + LIST + "    badge(…) 2번째 인자 tone: 'neutral' → 'info'")
			.contains("계획만 보였습니다(--dry-run)");
		assertThat(list()).isEqualTo(TEMPLATE);

		Result applied = set("5", "badge", "2", "neutral", "info", "--json");
		assertThat(applied.code()).as(applied.err()).isZero();
		assertThat(applied.out()).contains("\"recipe\": \"set arg\"").contains("\"applied\": true").contains("\"action\": \"edit\"");
		assertThat(list()).isEqualTo(TEMPLATE.replace("badge('고정', 'neutral')", "badge('고정', 'info')"));
		assertThat(set("5", "badge", "2", "info", "info").out()).as("같은 값이면 바뀐 것 없음").contains("바뀐 것 없음");
	}

	@Test
	void anArgumentOnTheNextLineOfAMultiLineCallIsFoundFromTheElementLine() {
		Result result = set("6", "emptyStateAction", "3", "첫 메모를 만들어 보세요.", "메모를 \"먼저\" 만들고 & 고정하세요 \\ 끝");
		assertThat(result.code()).as(result.err()).isZero();
		assertThat(list()).as("큰따옴표와 &는 문자 참조로, 역슬래시는 두 번")
			.contains("'메모를 &quot;먼저&quot; 만들고 &amp; 고정하세요 \\\\ 끝', ~{::#action})")
			.contains("emptyStateAction('notes', '메모가 없습니다',\n");
		Result back = set("6", "emptyStateAction", "3", "메모를 \"먼저\" 만들고 & 고정하세요 \\ 끝", "첫 메모를 만들어 보세요.");
		assertThat(back.code()).as(back.err()).isZero();
		assertThat(list()).as("되돌리면 처음과 같다").isEqualTo(TEMPLATE);
	}

	@Test
	void expressionsSelectorsAndStaleValuesAreRefused() {
		refused(set("8", "deleteConfirm", "3", "", "x"), "글자 그대로의 문자열이 아닙니다: ${memo.title}");
		refused(set("8", "deleteConfirm", "4", "", "x"), "글자 그대로의 문자열이 아닙니다: '/memo/' + ${memo.id}");
		refused(set("6", "emptyStateAction", "4", "", "x"), "글자 그대로의 문자열이 아닙니다: ~{::#action}");
		refused(set("5", "badge", "1", "다른 값", "새 값"), "지금 '고정'입니다(--from '다른 값')");
		refused(set("5", "badge", "3", "x", "y"), "인자는 2개입니다");
		refused(set("4", "badge", "1", "고정", "y"), "호출을 찾지 못했습니다");
		refused(set("5", "pill", "1", "고정", "y"), "호출을 찾지 못했습니다");
		refused(set("5", "badge", "1", "고정", "it's"), "작은따옴표(')를 넣을 수 없습니다");
		refused(set("5", "badge", "1", "고정", "두\n줄"), "줄바꿈");
		refused(cozy("set", "arg", "../secret", "--line", "1", "--fragment", "x", "--index", "1", "--from", "", "--value", ""),
				"뷰 이름");
		refused(cozy("set", "arg", "memo/api/none", "--line", "1", "--fragment", "x", "--index", "1", "--from", "", "--value", ""),
				"가 없습니다");
		refused(cozy("set", "value"), "cozy set 뒤에는 arg만");
	}

	/** cozy-ui의 FragmentManifest와 같은 형식 검사 */
	@Test
	void wrongChoicesInTheAppManifestFailTheSameWayAsCozyUi() {
		for (String[] bad : new String[][] { { "type: int, required: true, choices: [1, 2]", "memo/card의 size: choices는 String 파라미터에만 씁니다" },
				{ "type: String, required: true, choices: []", "choices가 비어 있습니다" },
				{ "type: String, required: true, choices: wide", "choices는 값의 목록이나 값 → 이름의 맵입니다" },
				{ "type: String, required: true, choices: [\"it's\"]", "따옴표와 역슬래시가 없는 글자" } }) {
			String yaml = """
					fragments:
					  - id: memo/card
					    template: memo/spi/card
					    signature: memoCard(size)
					    purpose: 메모 카드
					    params:
					      - { name: size, %s }
					    samples:
					      default: { size: compact }
					""".formatted(bad[0]);
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> io.salvia95.cozy.cli.internal.AppResources.manifest(yaml, "fragments.yml"))
				.as(bad[0])
				.hasMessageContaining(bad[1]);
		}
		assertThat(io.salvia95.cozy.cli.internal.AppResources
			.manifest("fragments:\n  - { id: a/b, template: a/b, signature: b(x), purpose: p, params: [{ name: x, type: String, choices: { s: 작게, l: 크게 } }], samples: { default: { x: s } } }\n", "f")
			.getFirst()
			.choices()).containsEntry("x", List.of("s", "l"));
	}

	@Test
	void choicesInTheManifestLimitTheValue() {
		refused(set("5", "badge", "2", "neutral", "purple"), "component/badge의 tone는 neutral, info, success, warning, critical 가운데 하나입니다");
		refused(set("9", "memoCard", "2", "compact", "huge"), "memo/card의 size는 compact, wide 가운데 하나입니다");
		Result wide = set("9", "memoCard", "2", "compact", "wide");
		assertThat(wide.code()).as(wide.err()).isZero();
		Result title = set("9", "memoCard", "1", "제목", "아무 글자");
		assertThat(title.code()).as("값을 정하지 않은 파라미터는 무엇이든 받는다: " + title.err()).isZero();
		assertThat(list()).contains("memoCard('아무 글자', 'wide')");
	}
}
