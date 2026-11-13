package io.salvia95.cozy.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URLClassLoader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 모듈의 {@code static/{모듈}/{모듈}.js}를 모듈 이름 순서로 이은 묶음 하나. 주소에 내용의 해시를 붙여 오래 캐시해도 바뀐 내용을 받는다.
 */
class CozyModuleScriptsTest {

	@TempDir
	Path dir;

	void write(String file, String content) throws IOException {
		Path path = this.dir.resolve(file);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	CozyModuleScripts scripts() throws IOException {
		URLClassLoader loader = new URLClassLoader(new URL[] { this.dir.toUri().toURL() }, null);
		return new CozyModuleScripts(new PathMatchingResourcePatternResolver(loader));
	}

	@Test
	void moduleFilesAreJoinedInModuleOrderWithTheirNames() throws IOException {
		write("static/wiki/wiki.js", "wikiPart();\n");
		write("static/memo/memo.js", "memoPart();");
		write("static/memo/extra.js", "extraPart();\n");
		write("static/img/sprite.svg", "<svg/>");
		CozyModuleScripts.Bundle bundle = scripts().bundle();
		assertThat(bundle.files()).containsExactly("static/memo/memo.js", "static/wiki/wiki.js");
		assertThat(bundle.content()).contains("/* static/memo/memo.js */\nmemoPart();\n")
			.contains("/* static/wiki/wiki.js */\nwikiPart();\n")
			.doesNotContain("extraPart");
		assertThat(bundle.content().indexOf("memoPart")).isLessThan(bundle.content().indexOf("wikiPart"));
		assertThat(bundle.version()).matches("[0-9a-f]{12}");
		assertThat(scripts().src()).isEqualTo("/cozy/modules.js?v=" + bundle.version());
	}

	@Test
	void anAppWithoutModuleFilesLoadsNothing() throws IOException {
		write("static/img/logo.svg", "<svg/>");
		assertThat(scripts().src()).isNull();
		assertThat(scripts().bundle().files()).isEmpty();
	}

	@Test
	void filesInABuildFolderAreReadAgainSoARebuildShowsWithoutARestart() throws IOException {
		write("static/memo/memo.js", "first();\n");
		CozyModuleScripts scripts = scripts();
		String before = scripts.bundle().version();
		write("static/memo/memo.js", "second();\n");
		write("static/wiki/wiki.js", "wiki();\n");
		assertThat(scripts.bundle().content()).contains("second()").contains("wiki()").doesNotContain("first()");
		assertThat(scripts.bundle().version()).isNotEqualTo(before);
		assertThat(scripts.src()).endsWith(scripts.bundle().version());
	}
}
