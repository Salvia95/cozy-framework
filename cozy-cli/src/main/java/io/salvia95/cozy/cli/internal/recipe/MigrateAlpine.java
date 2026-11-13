package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.rules.AlpineAttributes;

/**
 * {@code cozy migrate alpine}: 앱 템플릿의 없앤 동작 속성을 같은 뜻의 cozy 컴포넌트로 바꾼다.
 *
 * <ul>
 * <li>{@code data-cozy-confirm="…"} → {@code x-data="cozyConfirm" x-on:submit="ask" data-confirm-message="…"}</li>
 * <li>{@code data-cozy-copy="…"} → {@code x-data="cozyCopy" x-on:click="copy" data-copy-text="…"}</li>
 * </ul>
 * {@code th:}·{@code data-th-} 접두와 따옴표는 그대로 둔다. 요소에 {@code x-data}가 이미 있거나 값이 없거나, 바꾸는 법이 정해지지 않은
 * 속성(AlpineAttributes가 아는 그 밖의 것)은 손으로 할 일로 알린다. 주석 안은 보지 않는다.
 */
final class MigrateAlpine {

	static final String USAGE = "cozy migrate alpine [--dry-run] [--json]";

	private static final String TEMPLATES = "src/main/resources/templates";

	/** 요소 하나의 속성 가운데 없앤 동작 속성: 앞 공백, 접두, 이름, 값(따옴표 포함, 없으면 null) */
	private static final Pattern OLD = Pattern.compile("(\\s)((?:th:|data-th-)?)(data-cozy-[a-z-]+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'))?");

	private static final Pattern X_DATA = Pattern.compile("\\sx-data(?:\\s*=|[\\s/>]|$)");

	/** 바꾸는 속성 → (컴포넌트, 이벤트와 메서드, 값의 새 이름) */
	private static final Map<String, String[]> SHAPES = Map.of("data-cozy-confirm",
			new String[] { "cozyConfirm", "x-on:submit", "ask", "data-confirm-message" }, "data-cozy-copy",
			new String[] { "cozyCopy", "x-on:click", "copy", "data-copy-text" });

	private MigrateAlpine() {
	}

	/** @return 다음 할 일 */
	static String run(Plan plan, List<Context.Manual> manual, AppProject project, List<String> rest) {
		if (rest.size() != 1 || !rest.getFirst().equals("alpine")) {
			throw new CliException("cozy migrate 뒤에는 alpine만 적습니다 (" + USAGE + ").");
		}
		Path root = project.dir().resolve(TEMPLATES);
		if (Files.isDirectory(root)) {
			for (Path file : templates(root)) {
				String relative = project.dir().relativize(file).toString().replace('\\', '/');
				migrate(plan, manual, relative, plan.current(relative));
			}
		}
		return manual.isEmpty() ? "cozy check로 남은 속성이 없는지 확인하고, 그다음 mvn verify."
				: "남은 일을 고치고 cozy check, 그다음 mvn verify.";
	}

	private static List<Path> templates(Path root) {
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter((p) -> Files.isRegularFile(p) && p.toString().endsWith(".html")).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void migrate(Plan plan, List<Context.Manual> manual, String file, String text) {
		StringBuilder out = new StringBuilder(text.length() + 64);
		Map<String, Integer> counts = new LinkedHashMap<>();
		int i = 0;
		while (i < text.length()) {
			int open = text.indexOf('<', i);
			if (open < 0) {
				out.append(text, i, text.length());
				break;
			}
			out.append(text, i, open);
			if (text.startsWith("<!--", open)) {
				int close = text.indexOf("-->", open + 4);
				int end = (close < 0) ? text.length() : close + 3;
				out.append(text, open, end);
				i = end;
				continue;
			}
			if (open + 1 >= text.length() || !Character.isLetter(text.charAt(open + 1))) {
				out.append('<');
				i = open + 1;
				continue;
			}
			int end = tagEnd(text, open);
			String tag = text.substring(open, end);
			out.append(tag(manual, file, line(text, open), tag, counts));
			i = end;
			String name = tag.substring(1).split("[\\s/>]", 2)[0].toLowerCase(Locale.ROOT);
			if (name.equals("script") || name.equals("style")) {
				int close = text.toLowerCase(Locale.ROOT).indexOf("</" + name, i);
				int stop = (close < 0) ? text.length() : close;
				out.append(text, i, stop);
				i = stop;
			}
		}
		if (counts.isEmpty()) {
			return;
		}
		StringBuilder detail = new StringBuilder();
		counts.forEach((attribute, n) -> detail.append((detail.length() > 0) ? ", " : "")
			.append(attribute)
			.append(" → ")
			.append(SHAPES.get(attribute)[0])
			.append(' ')
			.append(n)
			.append("곳"));
		plan.edit(file, out.toString(), detail.toString());
	}

	/** 요소 하나: 바꿀 수 있으면 바꾼 글, 아니면 그대로 두고 손으로 할 일을 남긴다 */
	private static String tag(List<Context.Manual> manual, String file, int line, String tag, Map<String, Integer> counts) {
		Matcher m = OLD.matcher(tag);
		StringBuilder out = new StringBuilder();
		int last = 0;
		int migratable = 0;
		while (m.find()) {
			if (SHAPES.containsKey(m.group(3))) {
				migratable++;
			}
		}
		m.reset();
		boolean hasData = X_DATA.matcher(tag).find();
		while (m.find()) {
			String attribute = m.group(3);
			if (AlpineAttributes.removed(attribute).isEmpty()) {
				continue;
			}
			String[] shape = SHAPES.get(attribute);
			String where = file + ":" + line;
			if (shape == null) {
				manual.add(new Context.Manual(where, AlpineAttributes.removed(attribute).get(), tag.strip()));
				continue;
			}
			if (hasData || migratable > 1) {
				manual.add(new Context.Manual(where, "x-data가 이미 있어 " + attribute + "를 " + shape[0]
						+ "로 바꾸지 못했습니다. 요소를 감싸는 요소를 하나 더 두고 그곳에 " + shape[0] + "를 다세요.", tag.strip()));
				continue;
			}
			String value = m.group(4);
			if (value == null) {
				manual.add(new Context.Manual(where, attribute + "에 값이 없습니다. " + AlpineAttributes.removed(attribute).get(), tag.strip()));
				continue;
			}
			char quote = value.charAt(0);
			out.append(tag, last, m.start())
				.append(m.group(1))
				.append("x-data=").append(quote).append(shape[0]).append(quote).append(' ')
				.append(shape[1]).append('=').append(quote).append(shape[2]).append(quote).append(' ')
				.append(m.group(2)).append(shape[3]).append('=').append(value);
			last = m.end();
			counts.merge(attribute, 1, Integer::sum);
		}
		out.append(tag, last, tag.length());
		return out.toString();
	}

	/** {@code <}에서 시작하는 시작 태그의 끝(따옴표 안의 {@code >}는 건너뛴다) */
	private static int tagEnd(String text, int open) {
		char quote = 0;
		for (int i = open + 1; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
			}
			else if (c == '"' || c == '\'') {
				quote = c;
			}
			else if (c == '>') {
				return i + 1;
			}
		}
		return text.length();
	}

	private static int line(String text, int index) {
		int line = 1;
		for (int i = 0; i < index; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}
}
