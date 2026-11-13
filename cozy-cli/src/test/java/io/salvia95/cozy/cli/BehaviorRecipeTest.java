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
 * {@code cozy add behavior <모듈> <이름>}: 모듈의 Alpine 컴포넌트 파일({@code static/{모듈}/{모듈}.js})에 컴포넌트 등록 하나와 빈칸을
 * 만든다. 파일이 있으면 끝에 더한다. 상태와 메서드는 에이전트가 채운다(C9).
 */
class BehaviorRecipeTest {

	static final String FILE = "src/main/resources/static/memo/memo.js";

	@TempDir
	Path dir;

	@BeforeEach
	void app() throws IOException {
		App.of(this.dir).module("memo", "{}").module("wiki", "{}");
	}

	Result run(String... args) {
		String[] all = new String[args.length + 2];
		System.arraycopy(args, 0, all, 0, args.length);
		all[args.length] = "--project";
		all[args.length + 1] = this.dir.toString();
		return CozyCliTest.run(all);
	}

	String read(String file) throws IOException {
		return Files.readString(this.dir.resolve(file), StandardCharsets.UTF_8);
	}

	@Test
	void theFirstBehaviorCreatesTheModuleFileWithASlot() throws IOException {
		Result result = run("add", "behavior", "memo", "char-count");
		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("만듦  " + FILE).contains("빈칸 1곳").contains("x-data=\"memoCharCount\"");
		String js = read(FILE);
		assertThat(js).startsWith("/*")
			.contains("document.addEventListener('alpine:init', function () {")
			.contains("window.Alpine.data('memoCharCount', function () {")
			.contains("root = this.$root;")
			.contains("cozy:slot behavior-memoCharCount")
			.endsWith("});\n");

		// 빈칸만 남고 등록 규칙(R16)은 지킨다
		Result check = run("check");
		assertThat(check.code()).isEqualTo(CozyCli.VIOLATIONS);
		assertThat(check.out()).contains("[C9]").contains("behavior-memoCharCount").doesNotContain("[R16]");
	}

	@Test
	void moreBehaviorsAreAddedAtTheEndAndRunningAgainChangesNothing() throws IOException {
		run("add", "behavior", "memo", "char-count");
		String first = read(FILE);
		Result second = run("add", "behavior", "memo", "pin-toggle");
		assertThat(second.code()).as(second.err()).isZero();
		assertThat(second.out()).contains("바꿈  " + FILE);
		String js = read(FILE);
		assertThat(js.indexOf("'memoCharCount'")).isLessThan(js.indexOf("'memoPinToggle'"));
		assertThat(js).as("앞의 등록은 그대로다").startsWith(first.substring(0, first.lastIndexOf("});")));
		assertThat(js).endsWith("});\n");

		String once = read(FILE);
		Result again = run("add", "behavior", "memo", "pin-toggle");
		assertThat(again.code()).isZero();
		assertThat(again.out()).contains("바뀐 것 없음");
		assertThat(read(FILE)).isEqualTo(once);
	}

	@Test
	void aFileTheCommandDidNotWriteIsLeftAlone() throws IOException {
		Path file = this.dir.resolve(FILE);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "window.Alpine.data('memoOld', function () { return {}; });\n", StandardCharsets.UTF_8);
		Result result = run("add", "behavior", "memo", "char-count");
		assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains("alpine:init");
		assertThat(read(FILE)).doesNotContain("memoCharCount");
	}

	@Test
	void argumentsAreChecked() {
		assertThat(run("add", "behavior", "memo", "CharCount").err()).contains("소문자와 하이픈");
		assertThat(run("add", "behavior", "nope", "count").err()).contains("모듈 'nope'가 없습니다");
		assertThat(run("add", "behavior", "memo").code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(this.dir.resolve(FILE)).doesNotExist();
	}
}
