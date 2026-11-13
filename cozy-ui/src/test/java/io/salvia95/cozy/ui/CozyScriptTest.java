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
				"document htmx:afterSettle", "document DOMContentLoaded", "document alpine:init");
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
	void templatesUseNoRemovedBehaviourAttributes() throws IOException {
		Pattern removed = Pattern.compile("data-cozy-(?:confirm|copy|autosize|icon-filter)\\b|th:attr=\"[^\"]*data-copy=");
		try (Stream<Path> files = Files.walk(TEMPLATES)) {
			for (Path file : files.filter((f) -> f.toString().endsWith(".html")).toList()) {
				assertThat(Files.readString(file, StandardCharsets.UTF_8)).as(file.toString()).doesNotContainPattern(removed);
			}
		}
	}
}
