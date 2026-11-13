package io.salvia95.cozy.workbench.internal;

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
 * 워크벤치의 화면 안 동작은 Alpine 컴포넌트다: 템플릿이 {@code x-data}와 {@code x-on}으로 붙이고, 스크립트는 컴포넌트를 등록만 한다.
 */
class WorkbenchScriptTest {

	static final Path SCRIPT = Path.of("src/main/resources/META-INF/resources/cozy/workbench.js");

	static final Path TEMPLATES = Path.of("src/main/resources/templates/cozy/dev");

	/** 스크립트가 이벤트를 직접 다는 곳: 컴포넌트 등록, 그리고 Alpine이 닿지 않는 미리보기(iframe) 안의 문서와 창 */
	static final Pattern LISTENER = Pattern.compile("([\\w.]+)\\.addEventListener\\(\\s*'([^']+)'");

	static String script() throws IOException {
		return Files.readString(SCRIPT, StandardCharsets.UTF_8);
	}

	@Test
	void theScriptRegistersComponentsInsteadOfBindingElements() throws IOException {
		String script = script();
		List<String> listeners = new ArrayList<>();
		Matcher m = LISTENER.matcher(script);
		while (m.find()) {
			listeners.add(m.group(1) + " " + m.group(2));
		}
		assertThat(listeners).as("이벤트는 템플릿의 x-on이 단다")
			.allMatch((l) -> l.equals("document alpine:init") || l.startsWith("doc ") || l.startsWith("view "));
		assertThat(listeners).contains("document alpine:init");
		assertThat(script).doesNotContainPattern("querySelectorAll\\([^)]*\\)\\s*\\.forEach");
		assertThat(script).doesNotContain("htmx:").doesNotContain("DOMContentLoaded");
	}

	@Test
	void componentsAreWorkbenchOnly() throws IOException {
		Matcher m = Pattern.compile("Alpine\\.data\\('([A-Za-z]+)'").matcher(script());
		List<String> names = new ArrayList<>();
		while (m.find()) {
			names.add(m.group(1));
		}
		// 앱 화면에도 쓰는 동작(접기, 복사, 단축키)은 cozy-ui의 cozy 컴포넌트다
		assertThat(names).isNotEmpty().allMatch((n) -> n.startsWith("workbench"));
	}

	@Test
	void templatesHaveNoBehaviourHooks() throws IOException {
		Pattern hook = Pattern.compile("data-cozy-(?:copy|workbench-(?:fold|pick|frame|hint|chain|context|context-details|copy|hover|selected|"
				+ "selection|props|thumb|search|node)|compare(?:-[a-z]+)?|command-form|command-module|icon-input|icon-preview|arg-result)\\b"
				+ "(?!-)");
		try (Stream<Path> files = Files.list(TEMPLATES)) {
			for (Path file : files.filter((f) -> f.toString().endsWith(".html")).toList()) {
				String html = Files.readString(file, StandardCharsets.UTF_8);
				Matcher m = hook.matcher(html);
				assertThat(m.find()).as(file + ": " + (m.hitEnd() ? "" : html.substring(m.start(), Math.min(html.length(), m.start() + 40))))
					.isFalse();
				assertThat(html).as(file + ": 스크립트는 레이아웃이 Alpine보다 먼저 싣는다").doesNotContain("workbench.js");
			}
		}
	}
}
