package io.salvia95.cozy.rules.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
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
import io.salvia95.cozy.rules.internal.ExpressionReads.Expression;
import io.salvia95.cozy.rules.internal.ParsedTemplate.FragmentDef;
import io.salvia95.cozy.rules.internal.ParsedTemplate.ModelRead;
import io.salvia95.cozy.rules.internal.ParsedTemplate.Problem;
import io.salvia95.cozy.rules.internal.ParsedTemplate.Reference;

/**
 * AttoParser로 요소와 속성을 순회하며 fragment 선언과 참조를 모은다 (정규식만으로 HTML을 훑지 않는다).
 *
 * <ul>
 * <li>추출 대상: {@code th:replace}, {@code th:insert}, {@code th:include}(경고), 모든 속성 값 안의 {@code ~{...}}.
 * {@code data-th-*} 형식도 같다.</li>
 * <li>{@code th:replace="${p}"}는 감싸는 {@code th:fragment}의 파라미터 {@code p}일 때만 허용한다(슬롯). 그 밖의 변수는
 * 동적 참조다.</li>
 * <li>본문 텍스트의 {@code ~{}}와 프로토타입 전용 주석({@code <!--/*}{@code /-->})은 검사할 수 없으므로 실패로 닫는다.</li>
 * <li>표현식이 읽는 이름을 지역 범위와 비교한다(R10). 지역 범위는 가장 가까운 {@code th:fragment}부터 안쪽으로
 * fragment 파라미터, {@code th:each}(반복 변수와 상태 변수), {@code th:with}, {@code th:object}다. 한 요소 안에서는
 * Thymeleaf의 속성 실행 순서(삽입 → th:each → th:if 등 → th:object·th:with → 나머지)를 따른다. fragment 위쪽 요소의
 * 지역 변수는 selector로 fragment만 부를 때 없으므로 범위에 넣지 않는다.</li>
 * </ul>
 */
final class TemplateScanner {

	private static final Set<String> INSERTION = Set.of("replace", "insert", "include", "substituteby");

	private static final Set<String> CONDITIONS = Set.of("if", "unless", "switch", "case");

	/** 모델이나 요청 전체를 읽는 표현식 유틸리티 (R10) */
	private static final Set<String> FORBIDDEN_UTILITIES = Set.of("#vars", "#ctx", "#root", "#request", "#session",
			"#response", "#servletContext", "#application");

	private static final Pattern EACH = Pattern
		.compile("\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?:,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*)?:(.*)", Pattern.DOTALL);

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

		/**
		 * @param fragment 이 요소가 th:fragment를 선언했으면 그 선언 ({@code card(summary)}), 아니면 null
		 * @param locals 이 요소가 더한 지역 이름 (fragment 파라미터 포함)
		 * @param object 이 요소가 th:object를 두었는가
		 */
		record Frame(String element, List<String> params, String fragment, Set<String> locals, boolean[] object,
				ActionGroup group, boolean action) {
		}

		/** btn-list 하나. items: 동작마다 주 버튼(btn-primary, btn-danger, cozy-btn-critical-fill)인가 */
		record ActionGroup(int line, List<Boolean> items) {
		}

		/** 지금 위치의 지역 범위 */
		record Scope(String fragment, Set<String> names, boolean object) {
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
			if (text.contains("[[") || text.contains("[(")) {
				reads(text, scope(), line);
			}
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
			String declaration = null;
			Attribute fragment = thAttribute("fragment");
			if (fragment != null) {
				Matcher m = SIGNATURE.matcher(fragment.value());
				if (m.matches()) {
					params = (m.group(2) == null || m.group(2).isBlank()) ? List.of()
							: List.of(m.group(2).split(",")).stream().map(String::strip).toList();
					FragmentDef def = new FragmentDef(m.group(1), params, fragment.line());
					declaration = def.signature();
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
			Set<String> tokens = classTokens(cls);
			colorClasses(cls);
			boolean action = actionItem(tokens);
			if (action) {
				ActionGroup group = enclosingGroup();
				if (group != null) {
					group.items().add(tokens.contains("btn-primary") || tokens.contains("btn-danger")
							|| tokens.contains("cozy-btn-critical-fill"));
				}
			}
			ActionGroup group = tokens.contains("btn-list") ? new ActionGroup(line(cls), new ArrayList<>()) : null;
			return new Frame(this.elementName, params, declaration, new HashSet<>(params), new boolean[1], group, action);
		}

		private static Set<String> classTokens(Attribute cls) {
			Set<String> tokens = new HashSet<>();
			if (cls != null) {
				for (String token : cls.value().strip().split("\\s+")) {
					if (!token.isEmpty()) {
						tokens.add(token);
					}
				}
			}
			return tokens;
		}

		private int line(Attribute cls) {
			if (cls != null) {
				return cls.line();
			}
			return this.attributes.values().stream().mapToInt(Attribute::line).min().orElse(0);
		}

		/** R15: 버튼이거나, 내용을 바꿔 끼우는 요소(무엇이 들어올지 모르므로 동작 하나로 센다) */
		private boolean actionItem(Set<String> tokens) {
			return tokens.contains("btn") || thAttribute("replace") != null || thAttribute("insert") != null;
		}

		/** 가장 가까운 btn-list. 그 사이에 이미 동작으로 센 요소가 있으면(버튼 안, 바꿔 끼울 요소 안) 세지 않는다 */
		private ActionGroup enclosingGroup() {
			for (Frame frame : this.stack) {
				if (frame.action()) {
					return null;
				}
				if (frame.group() != null) {
					return frame.group();
				}
			}
			return null;
		}

		/** R14: class와 th:classappend·th:class의 글자 그대로인 클래스, 식으로 만든 색 클래스 */
		private void colorClasses(Attribute cls) {
			if (cls != null) {
				for (String token : cls.value().strip().split("\\s+")) {
					if (ColorClasses.forbidden(token)) {
						this.parsed.colorClasses.add(new Problem(token, "class", cls.line()));
					}
				}
			}
			for (String name : List.of("classappend", "class")) {
				Attribute expression = thAttribute(name);
				if (expression == null) {
					continue;
				}
				for (String token : ColorClasses.tokens(expression.value())) {
					if (ColorClasses.forbidden(token)) {
						this.parsed.colorClasses.add(new Problem(token, "th:" + name, expression.line()));
					}
				}
			}
		}

		private void processAttributes() {
			checkScope();
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

		/** 속성 실행 순서대로 표현식을 지역 범위와 비교하고, 이 요소가 더하는 지역 이름을 프레임에 넣는다 (R10, R11) */
		private void checkScope() {
			Frame frame = this.stack.peek();
			Attribute each = thAttribute("each");
			List<Attribute> insertions = new ArrayList<>();
			List<Attribute> conditions = new ArrayList<>();
			List<Attribute> rest = new ArrayList<>();
			for (Attribute attribute : this.attributes.values()) {
				String thName = thName(attribute.name());
				if (thName == null || thName.equals("fragment") || thName.equals("each") || thName.equals("with")
						|| thName.equals("object")) {
					continue;
				}
				if (INSERTION.contains(thName)) {
					insertions.add(attribute);
				}
				else if (CONDITIONS.contains(thName)) {
					conditions.add(attribute);
				}
				else {
					rest.add(attribute);
				}
			}
			boolean eachWithInsertion = each != null && !insertions.isEmpty();
			if (eachWithInsertion) {
				// R11로 보고한다. 같은 실수를 R10(반복 변수가 범위 밖)으로 한 번 더 보고하지 않도록 반복 변수를 먼저 넣는다
				this.parsed.eachWithInsertion.add(new Problem(each.name() + " + " + insertions.get(0).name(),
						"같은 요소의 th:each와 th:replace", each.line()));
			}
			else {
				insertions.forEach((a) -> reads(a.value(), scope(), a.line()));
			}
			if (each != null) {
				Matcher m = EACH.matcher(each.value());
				if (m.matches()) {
					reads(m.group(3), scope(), each.line());
					frame.locals().add(m.group(1));
					frame.locals().add((m.group(2) != null) ? m.group(2) : m.group(1) + "Stat");
				}
				else {
					reads(each.value(), scope(), each.line());
				}
			}
			if (eachWithInsertion) {
				insertions.forEach((a) -> reads(a.value(), scope(), a.line()));
			}
			conditions.forEach((a) -> reads(a.value(), scope(), a.line()));
			Attribute object = thAttribute("object");
			if (object != null) {
				reads(object.value(), scope(), object.line());
				frame.object()[0] = true;
			}
			Attribute with = thAttribute("with");
			if (with != null) {
				for (String[] assignment : ExpressionReads.assignments(with.value())) {
					reads(assignment[1], scope(), with.line());
					frame.locals().add(assignment[0]);
				}
			}
			rest.forEach((a) -> reads(a.value(), scope(), a.line()));
		}

		/** 가장 가까운 th:fragment까지의 지역 이름 */
		private Scope scope() {
			Set<String> names = new HashSet<>();
			boolean object = false;
			for (Frame frame : this.stack) {
				names.addAll(frame.locals());
				object |= frame.object()[0];
				if (frame.fragment() != null) {
					return new Scope(frame.fragment(), names, object);
				}
			}
			return new Scope(null, names, object);
		}

		private void reads(String value, Scope scope, int line) {
			for (Expression expression : ExpressionReads.find(value)) {
				if (expression.selection()) {
					if (!scope.object()) {
						this.parsed.modelReads.add(new ModelRead(expression.text(), null, scope.fragment(), line));
					}
					continue;
				}
				for (String name : ExpressionReads.roots(expression.body())) {
					boolean allowed = name.startsWith("#") ? !FORBIDDEN_UTILITIES.contains(name)
							: !name.startsWith("@") && (scope.names().contains(name) || name.startsWith("cozy")
									|| name.equals("_csrf"));
					if (!allowed) {
						this.parsed.modelReads.add(new ModelRead(expression.text(), name, scope.fragment(), line));
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
				Frame frame = this.stack.pop();
				finish(frame);
				if (frame.element().equals(lower)) {
					return;
				}
			}
		}

		@Override
		public void handleDocumentEnd(long endTimeNanos, long totalTimeNanos, int line, int col) {
			while (!this.stack.isEmpty()) {
				finish(this.stack.pop());
			}
		}

		/** R15: 주 버튼은 묶음에 하나, 맨 오른쪽(마지막) */
		private void finish(Frame frame) {
			ActionGroup group = frame.group();
			if (group == null) {
				return;
			}
			long primaries = group.items().stream().filter(Boolean::booleanValue).count();
			if (primaries > 1) {
				this.parsed.actionOrder.add(new Problem("class=\"btn-list\"", "many", group.line()));
			}
			else if (primaries == 1 && !group.items().get(group.items().size() - 1)) {
				this.parsed.actionOrder.add(new Problem("class=\"btn-list\"", "last", group.line()));
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
