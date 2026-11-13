package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * {@code cozy.js}: htmx 전역 설정만 문서 전체에 이벤트를 단다. 화면 안의 동작은 {@code cozy} 컴포넌트이고 템플릿이 {@code x-data}로 붙인다.
 */
class CozyScriptTest {

	static final Path SCRIPT = Path.of("src/main/resources/META-INF/resources/cozy/cozy.js");

	static final Path TEMPLATES = Path.of("src/main/resources/templates");

	static final Pattern LISTENER = Pattern.compile("([\\w.]+)\\.addEventListener\\(\\s*'([^']+)'");

	@Test
	void onlyHtmxSettingsListenOnTheWholeDocument() throws IOException {
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
		List<String> listeners = new ArrayList<>();
		Matcher m = LISTENER.matcher(script);
		while (m.find()) {
			listeners.add(m.group(1) + " " + m.group(2));
		}
		// CSRF, 오류 토스트, 진행 막대, 서버가 보내는 토스트, 검증 오류 칸 포커스, 그리고 컴포넌트 등록
		assertThat(listeners).containsExactlyInAnyOrder("document htmx:configRequest", "document htmx:beforeRequest",
				"document htmx:afterRequest", "document htmx:responseError", "document htmx:sendError", "document cozy:toast",
				"document htmx:afterSettle", "document DOMContentLoaded", "document alpine:init", "window error");
		assertThat(script).doesNotContainPattern("querySelectorAll\\([^)]*\\)\\s*\\.forEach");
	}

	@Test
	void componentsAreNamedCozy() throws IOException {
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
		Matcher m = Pattern.compile("Alpine\\.data\\('([A-Za-z]+)'").matcher(script);
		List<String> names = new ArrayList<>();
		while (m.find()) {
			names.add(m.group(1));
		}
		assertThat(names).containsExactlyInAnyOrder("cozyCopy", "cozyDisclosure", "cozyFocusKey", "cozyConfirm", "cozyDirtyForm",
				"cozyAutoHeight", "cozyIconList");
	}

	@Test
	void anErrorInTheModuleBundleNamesTheOriginalFileAndLine() throws IOException {
		// 묶음의 문법 오류는 묶음 전체를 멈춘다. 브라우저는 묶음의 줄을 알리므로 data-cozy-parts로 원래 파일의 줄로 바꾼다
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
		String handler = script.substring(script.indexOf("window.addEventListener('error'"));
		handler = handler.substring(0, handler.indexOf("\n  });") + 6);
		assertThat(handler).contains("/cozy/modules.js")
			.contains("data-cozy-parts")
			.contains("SyntaxError")
			.contains("console.error(")
			.contains("toast(");
	}

	@Test
	void anAutoHeightFrameIsFittedToItsContentNotToItself() throws IOException {
		// 미리보기의 body는 Tabler가 높이 100%로 둔다. body를 지켜보며 틀 높이를 바꾸면 그 크기가 다시 바뀌어 끝없이 돈다.
		// 틀 안의 문서는 내용 높이를 갖게 하고(cozy-fit), 그 크기는 틀 높이와 상관없으므로 문서 요소를 지켜본다
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
		String fit = script.substring(script.indexOf("function fitFrame"), script.indexOf("function copyText"));
		assertThat(fit).contains("classList.add('cozy-fit')")
			.contains(".observe(doc.documentElement)")
			.doesNotContain("observe(doc.body)")
			.doesNotContain("scrollHeight")
			.contains("frame.offsetHeight - frame.clientHeight");
		// 틀의 처음 빈 문서는 곧 바뀐다. 그 문서를 지켜보면 바뀐 뒤에도 남아 브라우저가 그림마다 오류를 낸다. 문서가 바뀌면 앞의 것을 끊는다
		assertThat(fit).contains("about:blank").contains("frame.cozyObserver.disconnect()");
		String css = Files.readString(Path.of("src/main/resources/META-INF/resources/cozy/cozy.css"), StandardCharsets.UTF_8);
		assertThat(css).contains("html.cozy-fit, html.cozy-fit body, html.cozy-fit .page { height: auto; min-height: 0; }");
	}

	@Test
	void templatesUseNoRemovedBehaviourAttributes() throws IOException {
		Pattern removed = Pattern.compile("data-cozy-(?:confirm|copy|autosize|icon-filter)\\b|th:attr=\"[^\"]*data-copy=");
		try (Stream<Path> files = Files.walk(TEMPLATES)) {
			for (Path file : files.filter((f) -> f.toString().endsWith(".html")).toList()) {
				assertThat(Files.readString(file, StandardCharsets.UTF_8)).as(file.toString()).doesNotContainPattern(removed);
			}
		}
	}
}
