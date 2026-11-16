package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * {@code cozy migrate alpine}: 앱 템플릿의 없앤 동작 속성을 같은 뜻의 cozy 컴포넌트로 바꾼다. 다른 명령처럼 계획을 보이고 원자적으로
 * 적용하며, 두 번 실행해도 결과가 같다. 바꿀 수 없는 것은 손으로 할 일로 알린다.
 */
class MigrateAlpineTest {

	static final String LIST = "src/main/resources/templates/memo/api/list.html";

	static final String DETAIL = "src/main/resources/templates/memo/api/detail.html";

	@TempDir
	Path dir;

	@BeforeEach
	void app() throws IOException {
		App.of(this.dir).module("memo", "{}");
	}

	void template(String file, String content) throws IOException {
		Path path = this.dir.resolve(file);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	String read(String file) throws IOException {
		return Files.readString(this.dir.resolve(file), StandardCharsets.UTF_8);
	}

	Result migrate(String... options) {
		String[] args = new String[options.length + 4];
		args[0] = "migrate";
		args[1] = "alpine";
		System.arraycopy(options, 0, args, 2, options.length);
		args[options.length + 2] = "--project";
		args[options.length + 3] = this.dir.toString();
		return CozyCliTest.run(args);
	}

	@Test
	void confirmAndCopyBecomeCozyComponentsAndTheRestIsKept() throws IOException {
		template(LIST, """
				<form method="post" th:action="@{/memo/{id}/delete(id=${memo.id})}" class="d-inline" data-cozy-confirm="메모를 삭제할까요?">
				  <button type="submit" class="btn" th:if="${a > b}">삭제</button>
				</form>
				<form method='post' th:data-cozy-confirm='${message}'></form>
				<button type="button" class="btn btn-sm" th:data-cozy-copy="${memo.title}">복사</button>
				""");
		Result result = migrate();
		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("바꿈  " + LIST).contains("적용했습니다");
		assertThat(read(LIST)).isEqualTo("""
				<form method="post" th:action="@{/memo/{id}/delete(id=${memo.id})}" class="d-inline" x-data="cozyConfirm" x-on:submit="ask" data-confirm-message="메모를 삭제할까요?">
				  <button type="submit" class="btn" th:if="${a > b}">삭제</button>
				</form>
				<form method='post' x-data='cozyConfirm' x-on:submit='ask' th:data-confirm-message='${message}'></form>
				<button type="button" class="btn btn-sm" x-data="cozyCopy" x-on:click="copy" th:data-copy-text="${memo.title}">복사</button>
				""");

		String once = read(LIST);
		Result again = migrate();
		assertThat(again.out()).as("두 번째 실행").contains("바뀐 것 없음");
		assertThat(read(LIST)).isEqualTo(once);
	}

	@Test
	void dryRunShowsThePlanAndWritesNothing() throws IOException {
		template(LIST, "<form method=\"post\" data-cozy-confirm=\"지울까요?\"></form>\n");
		template(DETAIL, "<p>바꿀 것 없음</p>\n");
		Result result = migrate("--dry-run", "--json");
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("\"recipe\": \"migrate alpine\"")
			.contains("\"applied\": false")
			.contains("\"file\": \"" + LIST + "\"")
			.doesNotContain(DETAIL);
		assertThat(read(LIST)).contains("data-cozy-confirm");
	}

	@Test
	void whatTheCommandCannotChangeIsLeftAsWorkToDoByHand() throws IOException {
		template(LIST, """
				<form method="post" x-data="memoForm" data-cozy-confirm="지울까요?"></form>
				<div><iframe title="미리보기" src="/memo/1" data-cozy-autosize></iframe></div>
				<form method="post" data-cozy-confirm="저장할까요?"></form>
				""");
		Result result = migrate();
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("남은 일 2곳")
			.contains(LIST + ":1")
			.contains("x-data가 이미 있어")
			.contains(LIST + ":2")
			.contains("cozyAutoHeight");
		assertThat(read(LIST)).as("바꿀 수 있는 것은 바꾼다")
			.contains("<form method=\"post\" x-data=\"memoForm\" data-cozy-confirm=\"지울까요?\"></form>")
			.contains("<form method=\"post\" x-data=\"cozyConfirm\" x-on:submit=\"ask\" data-confirm-message=\"저장할까요?\"></form>");
	}

	@Test
	void anAppWithoutOldAttributesChangesNothing() throws IOException {
		template(LIST, "<p x-data=\"cozyCopy\" data-copy-text=\"a\">a</p>\n");
		Result result = migrate();
		assertThat(result.code()).isZero();
		assertThat(result.out()).contains("바뀐 것 없음");
	}

	@Test
	void onlyAlpineAndEventsCanBeMigrated() throws IOException {
		Result result = CozyCliTest.run("migrate", "--project", this.dir.toString());
		assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains("cozy migrate alpine").contains("cozy migrate events");
	}
}
