package io.salvia95.cozy.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.attoparser.AbstractMarkupHandler;
import org.attoparser.MarkupParser;
import org.attoparser.ParseException;
import org.attoparser.config.ParseConfiguration;

/**
 * 템플릿 하나의 구조. fragment 선언({@code th:fragment})과 삽입({@code th:replace}, {@code th:insert})을 문서 순서대로, 서로 겹친
 * 깊이와 줄 번호와 함께 읽는다. 삽입이 {@code ~{::#id}}로 넘기는 요소는 그 삽입의 자리({@code slot})로 읽는다. 화면이 어떤 패턴을 쓰고 그 안에
 * 무엇을 넣었는지 보이는 데 쓴다.
 */
public final class TemplateOutline {

	private TemplateOutline() {
	}

	/**
	 * 구조의 마디 하나.
	 *
	 * @param line 요소가 시작하는 줄 (1부터)
	 * @param depth 이 마디를 감싸는 마디의 수
	 * @param kind {@code fragment}(선언), {@code replace}, {@code insert}, {@code slot}(감싸는 삽입이 {@code ~{::#id}}로 넘기는 요소)
	 * @param target 삽입하는 템플릿 이름. 같은 템플릿이면 빈 문자열. 읽을 수 없는 표현식이면 원문
	 * @param fragment fragment 이름. 템플릿 전체를 넣거나 읽을 수 없으면 {@code null}
	 * @param id 요소의 {@code id} (없으면 {@code null})
	 * @param args 선언이면 파라미터 이름, 삽입이면 넘기는 인자의 원문
	 */
	public record Node(int line, int depth, String kind, String target, String fragment, String id, List<String> args) {
	}

	private static final MarkupParser PARSER = new MarkupParser(ParseConfiguration.htmlConfiguration());

	/** {@code 템플릿 :: 이름(인자)} 또는 {@code 템플릿} */
	private static final Pattern REFERENCE = Pattern
		.compile("\\s*([^:\\s(]*)\\s*(?:::\\s*([A-Za-z_][A-Za-z0-9_\\-]*)\\s*(?:\\((.*)\\))?\\s*)?", Pattern.DOTALL);

	/** 인자로 넘기는 같은 템플릿의 요소: {@code ~{::#id}} */
	private static final Pattern SLOT_ARGUMENT = Pattern.compile("~\\{\\s*(?:this)?\\s*::\\s*#([A-Za-z_][A-Za-z0-9_\\-]*)\\s*}");

	private static final Pattern SIGNATURE = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_\\-]*)\\s*(?:\\(([^)]*)\\))?\\s*");

	public static List<Node> of(TemplateFile file) {
		Handler handler = new Handler();
		try {
			PARSER.parse(file.content(), handler);
		}
		catch (ParseException ex) {
			throw new IllegalArgumentException(file.location() + "를 읽지 못했습니다: " + ex.getMessage(), ex);
		}
		return List.copyOf(handler.nodes);
	}

	/** 인자 목록을 맨 바깥의 쉼표에서 나눈다. 따옴표와 괄호 안의 쉼표는 나누지 않는다 */
	public static List<String> splitArguments(String text) {
		List<String> args = new ArrayList<>();
		int depth = 0;
		char quote = 0;
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
			}
			else if (c == '\'' || c == '"') {
				quote = c;
			}
			else if (c == '(' || c == '{' || c == '[') {
				depth++;
			}
			else if (c == ')' || c == '}' || c == ']') {
				depth--;
			}
			else if (c == ',' && depth == 0) {
				args.add(current.toString().strip());
				current.setLength(0);
				continue;
			}
			current.append(c);
		}
		if (!current.toString().isBlank()) {
			args.add(current.toString().strip());
		}
		return args;
	}

	private static final class Handler extends AbstractMarkupHandler {

		final List<Node> nodes = new ArrayList<>();

		/** 열린 요소마다 이 요소가 더한 깊이 */
		private final Deque<Integer> open = new ArrayDeque<>();

		private final Map<String, String> attributes = new LinkedHashMap<>();

		/** 감싸는 삽입들이 {@code ~{::#id}}로 넘기는 id */
		private final Deque<String> slotIds = new ArrayDeque<>();

		/** 열린 요소마다 이 요소가 더한 slot id의 수 */
		private final Deque<Integer> openSlots = new ArrayDeque<>();

		private int addedSlots;

		private int depth;

		private int line;

		@Override
		public void handleOpenElementStart(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			start(line);
		}

		@Override
		public void handleOpenElementEnd(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			int added = element();
			this.open.push(added);
			this.openSlots.push(this.addedSlots);
			this.depth += added;
		}

		@Override
		public void handleStandaloneElementStart(char[] buffer, int nameOffset, int nameLen, boolean minimized, int line,
				int col) {
			start(line);
		}

		@Override
		public void handleStandaloneElementEnd(char[] buffer, int nameOffset, int nameLen, boolean minimized, int line,
				int col) {
			element();
			dropSlots(this.addedSlots);
		}

		@Override
		public void handleCloseElementStart(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			close();
		}

		@Override
		public void handleAutoCloseElementStart(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			close();
		}

		@Override
		public void handleAttribute(char[] buffer, int nameOffset, int nameLen, int nameLine, int nameCol, int operatorOffset,
				int operatorLen, int operatorLine, int operatorCol, int valueContentOffset, int valueContentLen,
				int valueOuterOffset, int valueOuterLen, int valueLine, int valueCol) {
			this.attributes.putIfAbsent(new String(buffer, nameOffset, nameLen).toLowerCase(),
					new String(buffer, valueContentOffset, valueContentLen));
		}

		private void start(int line) {
			this.attributes.clear();
			this.line = line;
		}

		private void close() {
			if (!this.open.isEmpty()) {
				this.depth -= this.open.pop();
				dropSlots(this.openSlots.pop());
			}
		}

		private void dropSlots(int count) {
			for (int i = 0; i < count; i++) {
				this.slotIds.pop();
			}
		}

		/** 지금 요소의 마디를 더한다. @return 이 요소가 자식에게 더하는 깊이 */
		private int element() {
			String id = this.attributes.get("id");
			int added = 0;
			this.addedSlots = 0;
			if (id != null && this.slotIds.contains(id)) {
				this.nodes.add(new Node(this.line, this.depth, "slot", "", null, id, List.of()));
				added++;
			}
			String declared = th("fragment");
			if (declared != null) {
				Matcher m = SIGNATURE.matcher(declared);
				if (m.matches()) {
					this.nodes.add(new Node(this.line, this.depth + added, "fragment", "", m.group(1), id,
							(m.group(2) != null) ? splitArguments(m.group(2)) : List.of()));
					added++;
				}
			}
			for (String kind : List.of("replace", "insert")) {
				String value = th(kind);
				if (value != null) {
					Node node = reference(kind, unescape(value), id, this.depth + added);
					this.nodes.add(node);
					for (String arg : node.args()) {
						Matcher slot = SLOT_ARGUMENT.matcher(arg);
						if (slot.matches()) {
							this.slotIds.push(slot.group(1));
							this.addedSlots++;
						}
					}
					added++;
					break;
				}
			}
			return added;
		}

		private Node reference(String kind, String value, String id, int depth) {
			String text = value.strip();
			if (text.startsWith("~{") && text.endsWith("}")) {
				text = text.substring(2, text.length() - 1);
			}
			Matcher m = REFERENCE.matcher(text);
			if (text.startsWith("${") || text.startsWith("*{") || !m.matches()) {
				return new Node(this.line, depth, kind, value.strip(), null, id, List.of());
			}
			String target = m.group(1).equals("this") ? "" : m.group(1);
			return new Node(this.line, depth, kind, target, m.group(2), id,
					(m.group(3) != null) ? splitArguments(m.group(3)) : List.of());
		}

		private String th(String name) {
			String value = this.attributes.get("th:" + name);
			return (value != null) ? value : this.attributes.get("data-th-" + name);
		}

		private static String unescape(String value) {
			return value.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
				.replace("&amp;", "&");
		}
	}
}
