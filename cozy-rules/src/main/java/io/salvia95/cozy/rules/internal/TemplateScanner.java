package io.salvia95.cozy.rules.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.attoparser.AbstractMarkupHandler;
import org.attoparser.MarkupParser;
import org.attoparser.ParseException;
import org.attoparser.config.ParseConfiguration;

import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.internal.FragmentExpressions.Ref;
import io.salvia95.cozy.rules.internal.ParsedTemplate.FragmentDef;
import io.salvia95.cozy.rules.internal.ParsedTemplate.Problem;
import io.salvia95.cozy.rules.internal.ParsedTemplate.Reference;

/**
 * AttoParser로 요소와 속성을 순회하며 fragment 선언과 참조를 모은다 (PRD §6.2: 정규식만으로 HTML을 훑지 않는다).
 *
 * <ul>
 * <li>추출 대상: {@code th:replace}, {@code th:insert}, {@code th:include}(경고), 모든 속성 값 안의 {@code ~{...}}.
 * {@code data-th-*} 형식도 같다.</li>
 * <li>{@code th:replace="${p}"}는 감싸는 {@code th:fragment}의 파라미터 {@code p}일 때만 허용한다(슬롯). 그 밖의 변수는
 * 동적 참조다.</li>
 * <li>본문 텍스트의 {@code ~{}}와 프로토타입 전용 주석({@code <!--/*}{@code /-->})은 검사할 수 없으므로 실패로 닫는다.</li>
 * </ul>
 */
final class TemplateScanner {

	private static final Set<String> INSERTION = Set.of("replace", "insert", "include", "substituteby");

	private static final Pattern SLOT = Pattern.compile("\\$\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}");

	private static final Pattern SIGNATURE = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_\\-]*)\\s*(?:\\(([^)]*)\\))?\\s*");

	private static final MarkupParser PARSER = new MarkupParser(ParseConfiguration.htmlConfiguration());

	private TemplateScanner() {
	}

	static ParsedTemplate scan(TemplateFile file) {
		ParsedTemplate parsed = new ParsedTemplate(file);
		try {
			PARSER.parse(file.content(), new Handler(parsed));
		}
		catch (ParseException ex) {
			parsed.problems.add(new Problem(file.path(), "HTML로 읽을 수 없습니다: " + ex.getMessage(),
					(ex.getLine() != null) ? ex.getLine() : 0));
		}
		return parsed;
	}

	private static final class Handler extends AbstractMarkupHandler {

		private final ParsedTemplate parsed;

		/** 열린 요소와 그 요소가 선언한 fragment 파라미터 */
		private final Deque<Frame> stack = new ArrayDeque<>();

		private final Map<String, Attribute> attributes = new LinkedHashMap<>();

		private String elementName;

		Handler(ParsedTemplate parsed) {
			this.parsed = parsed;
		}

		record Frame(String element, List<String> params) {
		}

		record Attribute(String name, String value, int line) {
		}

		@Override
		public void handleOpenElementStart(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			startElement(new String(buffer, nameOffset, nameLen));
		}

		@Override
		public void handleOpenElementEnd(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			Frame frame = endElement();
			this.stack.push(frame);
			processAttributes();
		}

		@Override
		public void handleStandaloneElementStart(char[] buffer, int nameOffset, int nameLen, boolean minimized,
				int line, int col) {
			startElement(new String(buffer, nameOffset, nameLen));
		}

		@Override
		public void handleStandaloneElementEnd(char[] buffer, int nameOffset, int nameLen, boolean minimized,
				int line, int col) {
			Frame frame = endElement();
			this.stack.push(frame);
			processAttributes();
			this.stack.pop();
		}

		@Override
		public void handleCloseElementStart(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			close(new String(buffer, nameOffset, nameLen));
		}

		@Override
		public void handleAutoCloseElementStart(char[] buffer, int nameOffset, int nameLen, int line, int col) {
			close(new String(buffer, nameOffset, nameLen));
		}

		@Override
		public void handleAttribute(char[] buffer, int nameOffset, int nameLen, int nameLine, int nameCol,
				int operatorOffset, int operatorLen, int operatorLine, int operatorCol, int valueContentOffset,
				int valueContentLen, int valueOuterOffset, int valueOuterLen, int valueLine, int valueCol) {
			String name = new String(buffer, nameOffset, nameLen).toLowerCase();
			String value = unescape(new String(buffer, valueContentOffset, valueContentLen));
			this.attributes.putIfAbsent(name, new Attribute(name, value, nameLine));
		}

		@Override
		public void handleText(char[] buffer, int offset, int len, int line, int col) {
			String text = new String(buffer, offset, len);
			if (text.contains("~{")) {
				this.parsed.problems.add(new Problem(text.strip(), "본문 텍스트 안의 fragment 표현식은 검사할 수 없습니다",
						line + leadingLines(text, text.indexOf("~{"))));
			}
		}

		@Override
		public void handleComment(char[] buffer, int contentOffset, int contentLen, int outerOffset, int outerLen,
				int line, int col) {
			String content = new String(buffer, contentOffset, contentLen);
			if (content.startsWith("/*/")) {
				this.parsed.problems.add(new Problem("<!--/*/ ... /*/-->",
						"프로토타입 전용 주석 안의 마크업은 검사할 수 없습니다. 주석을 풀거나 th:block을 쓰세요", line));
			}
		}

		private void startElement(String name) {
			this.elementName = name.toLowerCase();
			this.attributes.clear();
			this.parsed.elements.add(this.elementName);
		}

		/** 이 요소의 th:fragment를 읽어 프레임을 만든다 */
		private Frame endElement() {
			List<String> params = List.of();
			Attribute fragment = thAttribute("fragment");
			if (fragment != null) {
				Matcher m = SIGNATURE.matcher(fragment.value());
				if (m.matches()) {
					params = (m.group(2) == null || m.group(2).isBlank()) ? List.of()
							: List.of(m.group(2).split(",")).stream().map(String::strip).toList();
					FragmentDef def = new FragmentDef(m.group(1), params, fragment.line());
					if (this.parsed.fragments.putIfAbsent(def.name(), def) != null) {
						this.parsed.duplicateFragments.add(def);
					}
				}
				else {
					this.parsed.problems.add(new Problem("th:fragment=\"" + fragment.value() + "\"",
							"fragment 선언은 name 또는 name(a, b) 형식이어야 합니다", fragment.line()));
				}
			}
			Attribute id = this.attributes.get("id");
			if (id != null && !id.value().isBlank()) {
				this.parsed.ids.add(id.value().strip());
			}
			Attribute cls = this.attributes.get("class");
			if (cls != null) {
				for (String token : cls.value().strip().split("\\s+")) {
					if (!token.isEmpty()) {
						this.parsed.classes.add(token);
					}
				}
			}
			return new Frame(this.elementName, params);
		}

		private void processAttributes() {
			for (Attribute attribute : this.attributes.values()) {
				String thName = thName(attribute.name());
				if (thName != null && INSERTION.contains(thName)) {
					insertion(thName, attribute);
				}
				else {
					for (Ref ref : FragmentExpressions.findAll(attribute.value())) {
						add(ref, attribute.line());
					}
				}
			}
		}

		private void insertion(String thName, Attribute attribute) {
			if (thName.equals("include") || thName.equals("substituteby")) {
				this.parsed.includes.add(new Problem("th:" + thName + "=\"" + attribute.value() + "\"",
						"th:" + thName + "는 Thymeleaf 3에서 권장하지 않습니다. th:replace나 th:insert를 쓰세요",
						attribute.line()));
			}
			String value = attribute.value().strip();
			if (value.contains("~{")) {
				for (Ref ref : FragmentExpressions.findAll(value)) {
					add(ref, attribute.line());
				}
				return;
			}
			Matcher slot = SLOT.matcher(value);
			if (slot.matches()) {
				if (!isEnclosingParam(slot.group(1))) {
					this.parsed.problems.add(new Problem("th:" + thName + "=\"" + value + "\"", "'" + slot.group(1)
							+ "'는 감싸는 th:fragment의 파라미터가 아닙니다. 변수로 고른 템플릿을 넣는 동적 참조는 금지합니다",
							attribute.line()));
				}
				return;
			}
			if (value.isEmpty()) {
				return;
			}
			Ref ref = FragmentExpressions.parseUnwrapped(value);
			if (ref != null) {
				add(ref, attribute.line());
			}
		}

		private void add(Ref ref, int line) {
			if (ref.problem() != null) {
				this.parsed.problems.add(new Problem(ref.raw(), ref.problem(), line));
			}
			else {
				this.parsed.references.add(new Reference(ref, line));
			}
		}

		private boolean isEnclosingParam(String name) {
			for (Frame frame : this.stack) {
				if (frame.params().contains(name)) {
					return true;
				}
			}
			return false;
		}

		private void close(String name) {
			String lower = name.toLowerCase();
			if (this.stack.stream().noneMatch((f) -> f.element().equals(lower))) {
				return;
			}
			while (!this.stack.isEmpty()) {
				if (this.stack.pop().element().equals(lower)) {
					return;
				}
			}
		}

		private Attribute thAttribute(String name) {
			Attribute a = this.attributes.get("th:" + name);
			return (a != null) ? a : this.attributes.get("data-th-" + name);
		}

		private static String thName(String attribute) {
			if (attribute.startsWith("th:")) {
				return attribute.substring(3);
			}
			if (attribute.startsWith("data-th-")) {
				return attribute.substring(8);
			}
			return null;
		}

		private static int leadingLines(String text, int index) {
			int lines = 0;
			for (int i = 0; i < index; i++) {
				if (text.charAt(i) == '\n') {
					lines++;
				}
			}
			return lines;
		}
	}

	/** Thymeleaf가 속성 값을 해석하기 전에 하는 것과 같이 기본 엔티티를 푼다 */
	static String unescape(String value) {
		if (value.indexOf('&') < 0) {
			return value;
		}
		List<String[]> entities = new ArrayList<>(List.of(new String[] { "&quot;", "\"" }, new String[] { "&#39;", "'" },
				new String[] { "&apos;", "'" }, new String[] { "&lt;", "<" }, new String[] { "&gt;", ">" },
				new String[] { "&amp;", "&" }));
		String result = value;
		for (String[] e : entities) {
			result = result.replace(e[0], e[1]);
		}
		return result;
	}
}
