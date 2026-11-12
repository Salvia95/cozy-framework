package io.salvia95.cozy.cli.internal.recipe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.AppResources;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.FrameworkJar;

/**
 * {@code cozy set arg}: 템플릿의 fragment 호출({@code th:replace}, {@code th:insert}) 인자 가운데 글자 그대로의 문자열({@code '고정'}) 하나를
 * 바꾼다. 표현식({@code ${…}}, {@code '/memo/' + ${id}})과 선택자({@code ~{::#id}})는 바꾸지 않는다. 그 인자만 바뀌고 다른 글자는 그대로다.
 *
 * <ul>
 * <li>지금 값({@code --from})이 템플릿과 다르면 실패한다. 화면을 본 뒤 템플릿이 바뀌었으면 덮어쓰지 않는다.</li>
 * <li>매니페스트가 그 파라미터의 값을 정했으면({@code choices}) 그 가운데 하나여야 한다.</li>
 * </ul>
 */
final class SetArg {

	private SetArg() {
	}

	/** 쓰는 법 */
	static final String USAGE = "cozy set arg <템플릿> --line <줄> --fragment <이름> --index <번째> --from <지금 값> --value <새 값>";

	/** 뷰 이름: 소문자, 숫자, -, _, / */
	private static final Pattern VIEW = Pattern.compile("[a-z0-9_-]+(?:/[a-z0-9_-]+)*");

	/** fragment를 넣는 속성. 값은 큰따옴표로 감싼다 */
	private static final Pattern ATTRIBUTE = Pattern.compile("(?<=\\s)(?:th:|data-th-)(?:replace|insert)\\s*=\\s*(\"|')");

	/** {@code 템플릿 :: 이름(} */
	private static final Pattern CALL = Pattern.compile("\\s*(?:~\\{)?\\s*([^:\\s(]*)\\s*::\\s*([A-Za-z_][A-Za-z0-9_\\-]*)\\s*\\(");

	/** 글자 그대로의 문자열 하나 (속성 안의 원문) */
	private static final Pattern LITERAL = Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'");

	/** 인자 하나의 원문 위치 (앞뒤 공백 제외) */
	record Span(int start, int end) {
	}

	/** 한 줄에서 찾은 fragment 호출 */
	record Call(String target, String fragment, List<Span> args) {
	}

	static String run(Plan plan, AppProject project, Supplier<FrameworkJar> framework, Args args) {
		args.only("set arg", 1, "line", "fragment", "index", "from", "value");
		String view = args.positional(0, "템플릿의 뷰 이름(예: memo/api/list)");
		if (!VIEW.matcher(view).matches()) {
			throw new CliException("템플릿은 뷰 이름으로 적습니다(예: memo/api/list): " + view);
		}
		String file = "src/main/resources/templates/" + view + ".html";
		String text = plan.current(file);
		if (text == null) {
			throw new CliException(file + "가 없습니다. 템플릿은 앱의 뷰 이름으로 적습니다(예: memo/api/list).");
		}
		int line = number(args.required("line"), "--line");
		String fragment = args.required("fragment");
		int index = number(args.required("index"), "--index");
		String from = args.option("from");
		String value = args.option("value");
		if (from == null || value == null) {
			throw new CliException("--from(지금 값)과 --value(새 값)가 필요합니다. 빈 글자는 --value \"\"로 적습니다.");
		}
		if (value.contains("\n") || value.contains("\r")) {
			throw new CliException("--value에 줄바꿈을 넣을 수 없습니다.");
		}
		if (value.contains("'")) {
			throw new CliException("--value에 작은따옴표(')를 넣을 수 없습니다. Thymeleaf는 fragment 인자의 문자열에서 \\'를 읽지 못합니다. "
					+ "’ 같은 다른 글자를 쓰거나 템플릿에서 직접 고칩니다.");
		}

		List<Call> calls = calls(text, line).stream().filter((c) -> c.fragment().equals(fragment)).toList();
		if (calls.isEmpty()) {
			throw new CliException(file + ":" + line + "에서 " + fragment + "(…) 호출을 찾지 못했습니다. 템플릿이 바뀌었으면 줄 번호를 다시 확인하세요.");
		}
		if (calls.size() > 1) {
			throw new CliException(file + ":" + line + "에 " + fragment + "(…) 호출이 둘 이상입니다. 한 줄에 하나만 있을 때 고칩니다.");
		}
		Call call = calls.getFirst();
		if (index < 1 || index > call.args().size()) {
			throw new CliException(fragment + "(…)의 인자는 " + call.args().size() + "개입니다. --index는 1부터 " + call.args().size()
					+ "까지입니다: " + index);
		}
		Span span = call.args().get(index - 1);
		String raw = text.substring(span.start(), span.end());
		Matcher literal = LITERAL.matcher(raw);
		if (!literal.matches()) {
			throw new CliException(fragment + "(…)의 " + index + "번째 인자는 글자 그대로의 문자열이 아닙니다: " + raw
					+ ". 표현식과 선택자는 템플릿에서 직접 고칩니다.");
		}
		String current = decode(literal.group(1));
		if (!current.equals(from)) {
			throw new CliException(fragment + "(…)의 " + index + "번째 인자는 지금 '" + current + "'입니다(--from '" + from
					+ "'). 템플릿이 바뀌었으니 다시 읽고 고치세요.");
		}
		AppResources.ManifestEntry entry = entry(project, framework, call);
		String param = (entry != null && index <= entry.params().size()) ? entry.params().get(index - 1) : null;
		List<String> choices = (param != null) ? entry.choices().getOrDefault(param, List.of()) : List.of();
		if (!choices.isEmpty() && !choices.contains(value)) {
			throw new CliException(entry.id() + "의 " + param + "는 " + String.join(", ", choices) + " 가운데 하나입니다: " + value);
		}
		String changed = text.substring(0, span.start()) + encode(value) + text.substring(span.end());
		plan.edit(file, changed, fragment + "(…) " + index + "번째 인자" + ((param != null) ? " " + param : "") + ": '" + current
				+ "' → '" + value + "'");
		return "앱을 다시 빌드하면 화면에 보입니다. 확인은 cozy check, 그다음 mvn verify";
	}

	private static int number(String text, String option) {
		if (!text.matches("\\d{1,6}") || Integer.parseInt(text) < 1) {
			throw new CliException(option + "는 1 이상의 수입니다: " + text);
		}
		return Integer.parseInt(text);
	}

	/** {@code line}에서 시작하는 요소의 fragment 호출 */
	static List<Call> calls(String text, int line) {
		List<Call> calls = new ArrayList<>();
		Matcher attribute = ATTRIBUTE.matcher(text);
		while (attribute.find()) {
			int tag = text.lastIndexOf('<', attribute.start());
			if (tag < 0 || lineOf(text, tag) != line) {
				continue;
			}
			if (attribute.group(1).equals("'")) {
				throw new CliException("줄 " + line + "의 속성은 작은따옴표로 감쌌습니다. 큰따옴표로 감싼 속성만 고칩니다.");
			}
			int start = attribute.end();
			int end = text.indexOf('"', start);
			if (end < 0) {
				continue;
			}
			Matcher call = CALL.matcher(text).region(start, end);
			if (!call.lookingAt()) {
				continue;
			}
			int open = call.end() - 1;
			int close = closing(text, open, end);
			if (close < 0) {
				continue;
			}
			calls.add(new Call(call.group(1), call.group(2), split(text, open + 1, close)));
		}
		return calls;
	}

	/** {@code open}의 괄호와 짝인 닫는 괄호. 따옴표 안은 건너뛴다 */
	private static int closing(String text, int open, int limit) {
		int depth = 0;
		boolean quoted = false;
		for (int i = open; i < limit; i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '\\') {
					i++;
				}
				else if (c == '\'') {
					quoted = false;
				}
			}
			else if (c == '\'') {
				quoted = true;
			}
			else if (c == '(' || c == '{' || c == '[') {
				depth++;
			}
			else if (c == ')' || c == '}' || c == ']') {
				depth--;
				if (depth == 0) {
					return (c == ')') ? i : -1;
				}
			}
		}
		return -1;
	}

	/** 맨 바깥 쉼표로 나눈 인자의 위치 */
	private static List<Span> split(String text, int start, int end) {
		List<Span> spans = new ArrayList<>();
		int depth = 0;
		boolean quoted = false;
		int from = start;
		for (int i = start; i <= end; i++) {
			char c = (i < end) ? text.charAt(i) : ',';
			if (quoted) {
				if (c == '\\') {
					i++;
				}
				else if (c == '\'') {
					quoted = false;
				}
				continue;
			}
			if (c == '\'') {
				quoted = true;
			}
			else if (c == '(' || c == '{' || c == '[') {
				depth++;
			}
			else if (c == ')' || c == '}' || c == ']') {
				depth--;
			}
			else if (c == ',' && depth == 0) {
				int s = from;
				int e = i;
				while (s < e && Character.isWhitespace(text.charAt(s))) {
					s++;
				}
				while (e > s && Character.isWhitespace(text.charAt(e - 1))) {
					e--;
				}
				if (s < e || i < end) {
					spans.add(new Span(s, e));
				}
				from = i + 1;
			}
		}
		return spans;
	}

	private static int lineOf(String text, int offset) {
		int line = 1;
		for (int i = 0; i < offset; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	/** 속성 안 문자열의 원문 → 글자: HTML 문자 참조, 그다음 {@code \'}와 {@code \\} */
	static String decode(String raw) {
		String html = raw.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < html.length(); i++) {
			char c = html.charAt(i);
			if (c == '\\' && i + 1 < html.length()) {
				c = html.charAt(++i);
			}
			out.append(c);
		}
		return out.toString();
	}

	/** 글자 → 큰따옴표 속성 안의 문자열 원문. 작은따옴표는 받지 않는다({@link #run}) */
	static String encode(String value) {
		String escaped = value.replace("\\", "\\\\");
		return "'" + escaped.replace("&", "&amp;").replace("\"", "&quot;") + "'";
	}

	/** 호출한 fragment의 매니페스트 항목. 프레임워크 템플릿이면 cozy-ui의 매니페스트, 아니면 앱의 것. 없으면 {@code null} */
	private static AppResources.ManifestEntry entry(AppProject project, Supplier<FrameworkJar> framework, Call call) {
		List<AppResources.ManifestEntry> entries;
		if (call.target().startsWith("cozy/")) {
			String manifest = framework.get().manifest();
			entries = (manifest != null) ? AppResources.manifest(manifest, "[cozy-ui] cozy/fragments.yml") : List.of();
		}
		else {
			Path file = project.dir().resolve("src/main/resources/cozy/fragments.yml");
			try {
				entries = Files.isRegularFile(file)
						? AppResources.manifest(Files.readString(file, StandardCharsets.UTF_8), "src/main/resources/cozy/fragments.yml")
						: List.of();
			}
			catch (java.io.IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			}
		}
		return entries.stream()
			.filter((e) -> e.template().equals(call.target()) && e.fragmentName().equals(call.fragment()))
			.findFirst()
			.orElse(null);
	}
}
