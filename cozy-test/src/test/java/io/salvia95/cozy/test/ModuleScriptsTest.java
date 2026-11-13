package io.salvia95.cozy.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * C10: 레이아웃이 모든 모듈의 {@code static/{모듈}/{모듈}.js}를 한 묶음으로 싣으므로 한 파일의 문법 오류는 모든 모듈의 컴포넌트를 멈춘다.
 * 빌드에서 각 파일을 읽어 본다.
 */
class ModuleScriptsTest {

	@TempDir
	Path dir;

	void write(String file, String content) throws IOException {
		Path path = this.dir.resolve(file);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	@Test
	void modernSyntaxThatBrowsersRunIsAccepted() throws IOException {
		write("memo/memo.js", """
				document.addEventListener('alpine:init', function () {
				  window.Alpine.data('memoBodyCount', () => {
				    let root = null;
				    const limit = 10;
				    return {
				      length: 0,
				      init() { root = this.$root; this.length = root?.dataset.length ?? 0; },
				      get label() { return `${this.length} / ${limit}자`; },
				      async later() { await Promise.resolve(); },
				      ...{ extra: true }
				    };
				  });
				});
				""");
		assertThat(ModuleScripts.problems(this.dir)).isEmpty();
	}

	@Test
	void aSyntaxErrorIsReportedWithFileAndLine() throws IOException {
		write("memo/memo.js", """
				document.addEventListener('alpine:init', function () {
				  window.Alpine.data('memoBodyCount', function () {
				    return {
				      count: function () {}
				      label: function () {}
				    };
				  });
				});
				""");
		write("wiki/wiki.js", "document.addEventListener('alpine:init', function () {\n");
		assertThat(ModuleScripts.problems(this.dir)).hasSize(2)
			.anySatisfy((p) -> assertThat(p).startsWith("[C10] src/main/resources/static/memo/memo.js:5 — "))
			.anySatisfy((p) -> assertThat(p).startsWith("[C10] src/main/resources/static/wiki/wiki.js:"))
			.allSatisfy((p) -> assertThat(p).contains("모든 모듈의 컴포넌트"));
	}

	@Test
	void onlyTheFileTheLayoutLoadsIsRead() throws IOException {
		write("memo/extra.js", "this is not javascript");
		write("img/sprite.js", "nor this");
		assertThat(ModuleScripts.problems(this.dir)).isEmpty();
		assertThat(ModuleScripts.problems(this.dir.resolve("missing"))).isEmpty();
	}
}
