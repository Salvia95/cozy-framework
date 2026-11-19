package io.salvia95.cozy.cli.internal.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Component;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.State;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Type;
import io.salvia95.cozy.rules.BehaviorRules;

/**
 * 손으로 쓴 모듈 JS를 읽어 동작 명세의 컴포넌트로 옮긴다. 읽는 것은 옛 {@code cozy add behavior}가 만든 모양이다.
 *
 * <pre>
 * document.addEventListener('alpine:init', function () {
 *   // 목적 한 문장
 *   window.Alpine.data('memoPanel', function () {
 *     var root = null;
 *     return {
 *       open: false,                                   // 상태 (글자 그대로의 처음 값)
 *       limit: 0,
 *       init: function () {
 *         root = this.$root;
 *         this.limit = parseInt(root.dataset.limit, 10) || 0;   // data-limit에서 읽는 상태
 *         var self = this;
 *         window.addEventListener('memo:archived', function () { self.close(); });   // 받는 화면 이벤트
 *       },
 *       get label() { return this.open ? '닫기' : '열기'; },   // 계산
 *       toggle: function () { this.open = !this.open; },      // 동작
 *       close: function () { this.open = false; this.$dispatch('memo:closed'); }
 *     };
 *   });
 * });
 * </pre>
 *
 * <p>계산과 동작의 JS는 낱말로 옮길 수 있는 식과 문장만 읽는다: 상태와 계산의 이름({@code this.이름}), 글자 그대로의 값, {@code !}, {@code &&},
 * {@code ||}, 비교, {@code ? :}, 글자 잇기({@code +}, {@code toLocaleString('ko-KR')}), {@code .length}, {@code .includes(…)},
 * {@code Number(…)}, 이벤트 값({@code event.target.value}, {@code .checked}, {@code .dataset.이름}, {@code event.detail.이름}), 대입,
 * {@code !} 뒤집기, 더하기와 빼기, {@code if}, {@code this.$dispatch('모듈:이름', {…})}, 한 번 쓰는 지역 변수. 그 밖의 것은 그 컴포넌트를 옮기지 않고
 * 어디가 낱말 밖인지 알린다(판단할 수 없는 것은 옮기지 않는다).
 */
public final class LegacyScript {

	/**
	 * 옮기지 못한 곳.
	 *
	 * @param component 컴포넌트의 등록 이름. 파일의 모양이 문제면 {@code null}
	 * @param line 줄 (1부터)
	 * @param what 무엇이 낱말 밖인지
	 * @param code 그 자리의 JS
	 */
	public record Problem(String component, int line, String what, String code) {
	}

	/** @param components 옮긴 컴포넌트 (등록 순서). 문제가 있는 컴포넌트는 없다 */
	public record Result(List<Component> components, List<Problem> problems) {
	}

	private LegacyScript() {
	}

	/**
	 * @param purposes 등록 이름 → 목적. 등록 위의 주석보다 앞선다
	 */
	public static Result read(String module, String source, Map<String, String> purposes) {
		List<Component> components = new ArrayList<>();
		List<Problem> problems = new ArrayList<>();
		List<Token> tokens;
		try {
			tokens = new Lexer(source).tokens();
		}
		catch (Unsupported ex) {
			problems.add(new Problem(null, ex.line, ex.what, ex.code));
			return new Result(components, problems);
		}
		new FileReader(module, source, tokens, purposes, components, problems).read();
		return new Result(List.copyOf(components), List.copyOf(problems));
	}

	// ── 낱말 나누기 ─────────────────────────────────────────────

	private enum Kind {
		NAME, NUMBER, STRING, PUNCT, END
	}

	/**
	 * @param comment 바로 앞의 주석 (없으면 {@code null})
	 */
	private record Token(Kind kind, String text, int line, int start, int end, String comment) {

		boolean is(String value) {
			return (this.kind == Kind.PUNCT || this.kind == Kind.NAME) && this.text.equals(value);
		}
	}

	private static final List<String> PUNCTS = List.of("===", "!==", "...", "=>", "==", "!=", ">=", "<=", "&&", "||", "++", "--",
			"+=", "-=", "*=", "/=", "?.", "??", "{", "}", "(", ")", "[", "]", ";", ",", ".", ":", "?", "!", "<", ">", "=", "+", "-",
			"*", "/", "%", "&", "|", "^", "~");

	private static final class Lexer {

		private final String source;

		private int at;

		private int line = 1;

		Lexer(String source) {
			this.source = source.startsWith("﻿") ? " " + source.substring(1) : source;
		}

		List<Token> tokens() {
			List<Token> tokens = new ArrayList<>();
			String comment = null;
			while (true) {
				String found = skip();
				if (found != null) {
					comment = found;
				}
				if (this.at >= this.source.length()) {
					tokens.add(new Token(Kind.END, "", this.line, this.at, this.at, comment));
					return tokens;
				}
				int start = this.at;
				int startLine = this.line;
				char c = this.source.charAt(this.at);
				Kind kind;
				String text;
				if (Character.isJavaIdentifierStart(c)) {
					while (this.at < this.source.length() && Character.isJavaIdentifierPart(this.source.charAt(this.at))) {
						this.at++;
					}
					kind = Kind.NAME;
					text = this.source.substring(start, this.at);
				}
				else if (Character.isDigit(c) || (c == '.' && this.at + 1 < this.source.length()
						&& Character.isDigit(this.source.charAt(this.at + 1)))) {
					while (this.at < this.source.length()
							&& (Character.isLetterOrDigit(this.source.charAt(this.at)) || this.source.charAt(this.at) == '.')) {
						this.at++;
					}
					kind = Kind.NUMBER;
					text = this.source.substring(start, this.at);
				}
				else if (c == '\'' || c == '"') {
					kind = Kind.STRING;
					text = string(c);
				}
				else if (c == '`') {
					throw new Unsupported(this.line, "템플릿 문자열(`…`)", code(start));
				}
				else {
					text = null;
					for (String p : PUNCTS) {
						if (this.source.startsWith(p, this.at)) {
							text = p;
							break;
						}
					}
					if (text == null) {
						throw new Unsupported(this.line, "읽을 수 없는 글자 '" + c + "'", code(start));
					}
					this.at += text.length();
					kind = Kind.PUNCT;
				}
				tokens.add(new Token(kind, text, startLine, start, this.at, comment));
				comment = null;
			}
		}

		/** 빈칸과 주석. 마지막 주석의 글을 돌려준다 */
		private String skip() {
			String comment = null;
			while (this.at < this.source.length()) {
				char c = this.source.charAt(this.at);
				if (c == '\n') {
					this.line++;
					this.at++;
				}
				else if (Character.isWhitespace(c)) {
					this.at++;
				}
				else if (this.source.startsWith("//", this.at)) {
					int end = this.source.indexOf('\n', this.at);
					end = (end < 0) ? this.source.length() : end;
					comment = this.source.substring(this.at + 2, end);
					this.at = end;
				}
				else if (this.source.startsWith("/*", this.at)) {
					int end = this.source.indexOf("*/", this.at + 2);
					if (end < 0) {
						throw new Unsupported(this.line, "닫지 않은 주석", code(this.at));
					}
					String text = this.source.substring(this.at + 2, end);
					this.line += (int) text.chars().filter((ch) -> ch == '\n').count();
					comment = text;
					this.at = end + 2;
				}
				else {
					break;
				}
			}
			return comment;
		}

		private String string(char quote) {
			StringBuilder value = new StringBuilder();
			int start = this.at;
			this.at++;
			while (this.at < this.source.length()) {
				char c = this.source.charAt(this.at++);
				if (c == quote) {
					return value.toString();
				}
				if (c == '\n') {
					break;
				}
				if (c == '\\' && this.at < this.source.length()) {
					char next = this.source.charAt(this.at++);
					switch (next) {
						case 'n' -> value.append('\n');
						case 't' -> value.append('\t');
						case '\\', '\'', '"' -> value.append(next);
						default -> throw new Unsupported(this.line, "글자 안의 \\" + next, code(start));
					}
				}
				else {
					value.append(c);
				}
			}
			throw new Unsupported(this.line, "닫지 않은 글자", code(start));
		}

		private String code(int start) {
			int end = this.source.indexOf('\n', start);
			return this.source.substring(start, (end < 0) ? this.source.length() : end).strip();
		}
	}

	/** 낱말 밖. 그 컴포넌트(또는 파일)를 옮기지 않는다 */
	private static final class Unsupported extends RuntimeException {

		private static final long serialVersionUID = 1L;

		final int line;

		final String what;

		final String code;

		Unsupported(int line, String what, String code) {
			super(what, null, false, false);
			this.line = line;
			this.what = what;
			this.code = code;
		}
	}

	// ── JS의 식과 문장 ─────────────────────────────────────────────

	private sealed interface Js {

		int start();

		int end();

		int line();
	}

	private record JName(String name, int line, int start, int end) implements Js {
	}

	private record JThis(int line, int start, int end) implements Js {
	}

	private record JNumber(String text, int line, int start, int end) implements Js {
	}

	private record JString(String value, int line, int start, int end) implements Js {
	}

	/** {@code true}, {@code false}, {@code null}, {@code undefined} */
	private record JWord(String word, int line, int start, int end) implements Js {
	}

	private record JArray(List<Js> items, int line, int start, int end) implements Js {
	}

	/** 객체 글자. 키 → 값 */
	private record JObject(Map<String, Js> entries, int line, int start, int end) implements Js {
	}

	private record JMember(Js object, String name, int line, int start, int end) implements Js {
	}

	private record JCall(Js callee, List<Js> args, int line, int start, int end) implements Js {
	}

	private record JUnary(String op, Js operand, int line, int start, int end) implements Js {
	}

	private record JBinary(String op, Js left, Js right, int line, int start, int end) implements Js {
	}

	private record JCond(Js test, Js then, Js otherwise, int line, int start, int end) implements Js {
	}

	/** {@code a = b}, {@code a += 1}, {@code a++} (값은 {@code null}) */
	private record JAssign(String op, Js target, Js value, int line, int start, int end) implements Js {
	}

	/** {@code function (p) { … }}, {@code (p) => …} */
	private record JFunction(List<String> params, List<Stmt> body, Js expression, int line, int start, int end) implements Js {
	}

	private sealed interface Stmt {

		Js at();
	}

	private record SExpr(Js expr) implements Stmt {

		public Js at() {
			return this.expr;
		}
	}

	private record SVar(String name, Js value, Js at) implements Stmt {
	}

	private record SIf(Js test, List<Stmt> body, Js at) implements Stmt {
	}

	private record SReturn(Js value, Js at) implements Stmt {
	}

	// ── 읽기 ─────────────────────────────────────────────

	/** 파일 하나: {@code alpine:init} 안의 등록들 */
	private static final class FileReader {

		private final String module;

		private final String source;

		private final List<Token> tokens;

		private final Map<String, String> purposes;

		private final List<Component> components;

		private final List<Problem> problems;

		private int at;

		FileReader(String module, String source, List<Token> tokens, Map<String, String> purposes, List<Component> components,
				List<Problem> problems) {
			this.module = module;
			this.source = source;
			this.tokens = tokens;
			this.purposes = purposes;
			this.components = components;
			this.problems = problems;
		}

		void read() {
			try {
				int listeners = 0;
				while (!peek().kind().equals(Kind.END)) {
					listener();
					listeners++;
				}
				if (listeners == 0) {
					throw new Unsupported(1, "document.addEventListener('alpine:init', …) 안의 Alpine.data 등록이 없습니다", "");
				}
			}
			catch (Unsupported ex) {
				this.problems.add(new Problem(null, ex.line, "파일의 모양: " + ex.what, ex.code));
			}
		}

		private void listener() {
			Token first = peek();
			expectWords("document", ".", "addEventListener", "(");
			Token event = next();
			if (event.kind() != Kind.STRING || !event.text().equals("alpine:init")) {
				throw unsupported(first, "맨 위에는 document.addEventListener('alpine:init', function () { … })만 둡니다");
			}
			expect(",");
			if (peek().is("function")) {
				next();
				expect("(");
				expect(")");
			}
			else {
				expect("(");
				expect(")");
				expect("=>");
			}
			expect("{");
			while (!peek().is("}")) {
				registration();
			}
			expect("}");
			expect(")");
			accept(";");
		}

		private void registration() {
			Token first = peek();
			if (first.is("window")) {
				next();
				expect(".");
			}
			expectWords("Alpine", ".", "data", "(");
			int open = this.at - 1;
			int close = matching(open);
			Token name = next();
			if (name.kind() != Kind.STRING) {
				throw unsupported(name, "등록 이름은 글자로 적습니다");
			}
			String component = name.text();
			try {
				expect(",");
				Component c = new ComponentReader(this.module, component, this.source, this.tokens.subList(this.at, close),
						purpose(component, first)).read();
				this.components.add(c);
			}
			catch (Unsupported ex) {
				this.problems.add(new Problem(component, ex.line, ex.what, ex.code));
			}
			this.at = close + 1;
			accept(";");
		}

		private String purpose(String component, Token first) {
			String given = this.purposes.get(component);
			if (given != null) {
				return given;
			}
			if (first.comment() == null) {
				return null;
			}
			for (String line : first.comment().split("\n")) {
				String text = line.strip().replaceFirst("^\\*+", "").strip();
				if (!text.isEmpty()) {
					return text;
				}
			}
			return null;
		}

		/** 짝이 맞는 닫는 괄호의 자리 */
		private int matching(int open) {
			int depth = 0;
			for (int i = open; i < this.tokens.size(); i++) {
				Token t = this.tokens.get(i);
				if (t.is("(") || t.is("{") || t.is("[")) {
					depth++;
				}
				else if (t.is(")") || t.is("}") || t.is("]")) {
					depth--;
					if (depth == 0) {
						return i;
					}
				}
			}
			throw unsupported(this.tokens.get(open), "닫는 괄호가 없습니다");
		}

		private Token peek() {
			return this.tokens.get(this.at);
		}

		private Token next() {
			Token t = this.tokens.get(this.at);
			if (t.kind() != Kind.END) {
				this.at++;
			}
			return t;
		}

		private boolean accept(String value) {
			if (peek().is(value)) {
				next();
				return true;
			}
			return false;
		}

		private void expect(String value) {
			Token t = next();
			if (!t.is(value)) {
				throw unsupported(t, "여기에 와야 하는 글자: " + value);
			}
		}

		private void expectWords(String... values) {
			for (String value : values) {
				Token t = next();
				if (!t.is(value)) {
					throw unsupported(t, "맨 위에는 document.addEventListener('alpine:init', …)와 그 안의 window.Alpine.data('이름', …)만 둡니다");
				}
			}
		}

		private Unsupported unsupported(Token t, String what) {
			return new Unsupported(t.line(), what, LegacyScript.code(this.source, t.start()));
		}
	}

	/** 컴포넌트 하나: {@code function () { var root = null; return { … }; }} */
	private static final class ComponentReader {

		private final String module;

		private final String name;

		private final String source;

		private final Parser parser;

		private final String purpose;

		/** 상태 → 글자 그대로의 처음 값 */
		private final Map<String, Js> literals = new LinkedHashMap<>();

		private final Map<String, JFunction> getters = new LinkedHashMap<>();

		private final Map<String, JFunction> methods = new LinkedHashMap<>();

		private JFunction init;

		private Token start;

		ComponentReader(String module, String name, String source, List<Token> tokens, String purpose) {
			this.module = module;
			this.name = name;
			this.source = source;
			List<Token> body = new ArrayList<>(tokens);
			Token last = body.isEmpty() ? null : body.getLast();
			body.add(new Token(Kind.END, "", (last != null) ? last.line() : 1, (last != null) ? last.end() : 0,
					(last != null) ? last.end() : 0, null));
			this.parser = new Parser(source, body);
			this.purpose = purpose;
		}

		Component read() {
			this.start = this.parser.peek();
			if (!this.name.startsWith(this.module) || this.name.length() <= this.module.length()
					|| !Character.isUpperCase(this.name.charAt(this.module.length()))) {
				throw new Unsupported(this.start.line(), this.module + " 모듈의 컴포넌트 이름은 모듈 이름으로 시작합니다: " + this.name,
						code(this.source, this.start.start()));
			}
			String key = Character.toLowerCase(this.name.charAt(this.module.length())) + this.name.substring(this.module.length() + 1);
			if (!BehaviorSpec.NAME.matcher(key).matches()) {
				throw new Unsupported(this.start.line(), "명세의 이름으로 쓸 수 없는 컴포넌트 이름입니다(" + key + ")", code(this.source, this.start.start()));
			}
			if (this.purpose == null || this.purpose.isBlank()) {
				throw new Unsupported(this.start.line(), "목적 한 문장이 없습니다. 등록 바로 위에 한 줄 주석으로 적거나 --purpose " + this.name + "=문장을 줍니다",
						code(this.source, this.start.start()));
			}
			factory();
			Translator translator = new Translator(this.module, this.name, this.source, this.literals, this.getters, this.methods,
					this.init);
			Component c = translator.component(key, this.purpose.strip());
			try {
				BehaviorScript.check(new BehaviorSpec(this.module, Map.of(key, c)), BehaviorRules.specPath(this.module));
			}
			catch (CliException ex) {
				// 옮긴 명세가 낱말의 종류와 이름 규칙에 맞지 않는다(글자를 숫자 상태에 넣는 등)
				throw new Unsupported(this.start.line(), ex.getMessage(), code(this.source, this.start.start()));
			}
			return c;
		}

		private void factory() {
			Parser p = this.parser;
			boolean arrow;
			if (p.accept("function")) {
				p.expect("(");
				p.expect(")");
				arrow = false;
			}
			else {
				p.expect("(");
				p.expect(")");
				p.expect("=>");
				arrow = true;
			}
			if (arrow && p.accept("(")) {
				object();
				p.expect(")");
			}
			else {
				p.expect("{");
				while (true) {
					Token t = p.peek();
					if (t.is("var") || t.is("let") || t.is("const")) {
						p.next();
						Token root = p.next();
						if (!root.is("root")) {
							throw p.unsupported(t, "등록 함수 안의 변수는 var root = null 하나만 둡니다");
						}
						if (p.accept("=")) {
							Token value = p.next();
							if (!value.is("null")) {
								throw p.unsupported(value, "var root = null로 둡니다");
							}
						}
						p.accept(";");
						continue;
					}
					if (t.is("return")) {
						p.next();
						object();
						p.accept(";");
						break;
					}
					throw p.unsupported(t, "등록 함수에는 var root = null과 return { … }만 둡니다");
				}
				p.expect("}");
			}
			if (p.peek().kind() != Kind.END) {
				throw p.unsupported(p.peek(), "등록의 둘째 인자는 컴포넌트 객체를 돌려주는 함수 하나입니다");
			}
		}

		private void object() {
			Parser p = this.parser;
			p.expect("{");
			Set<String> seen = new HashSet<>();
			while (!p.peek().is("}")) {
				Token key = p.next();
				boolean getter = false;
				if (key.is("get") && (p.peek().kind() == Kind.NAME || p.peek().kind() == Kind.STRING)) {
					getter = true;
					key = p.next();
				}
				if (key.kind() != Kind.NAME && key.kind() != Kind.STRING) {
					throw p.unsupported(key, "객체의 키는 이름입니다");
				}
				String name = key.text();
				if (!seen.add(name)) {
					throw p.unsupported(key, "같은 이름이 두 번 있습니다: " + name);
				}
				if (getter) {
					JFunction f = p.functionRest(key);
					if (!f.params().isEmpty()) {
						throw p.unsupported(key, "getter는 인자를 받지 않습니다");
					}
					this.getters.put(name, f);
				}
				else if (p.peek().is("(")) {
					member(name, p.functionRest(key), key);
				}
				else {
					p.expect(":");
					Js value = p.expression();
					if (value instanceof JFunction f) {
						member(name, f, key);
					}
					else {
						this.literals.put(name, value);
					}
				}
				if (!p.accept(",")) {
					break;
				}
			}
			p.expect("}");
		}

		private void member(String name, JFunction f, Token key) {
			if (name.equals("init")) {
				this.init = f;
			}
			else if (name.equals("destroy")) {
				throw this.parser.unsupported(key, "destroy는 옮기지 않습니다. 받는 화면 이벤트는 init의 window.addEventListener로 읽고, 떼는 일은 만든 JS가 합니다");
			}
			else {
				this.methods.put(name, f);
			}
		}
	}

	/** 낱말 하나씩 읽는 식과 문장의 파서 */
	private static final class Parser {

		private final String source;

		private final List<Token> tokens;

		private int at;

		Parser(String source, List<Token> tokens) {
			this.source = source;
			this.tokens = tokens;
		}

		Token peek() {
			return this.tokens.get(this.at);
		}

		Token next() {
			Token t = this.tokens.get(this.at);
			if (t.kind() != Kind.END) {
				this.at++;
			}
			return t;
		}

		boolean accept(String value) {
			if (peek().is(value) && peek().kind() != Kind.STRING) {
				next();
				return true;
			}
			return false;
		}

		void expect(String value) {
			Token t = next();
			if (!t.is(value) || t.kind() == Kind.STRING) {
				throw unsupported(t, "여기에 와야 하는 글자: " + value);
			}
		}

		Unsupported unsupported(Token t, String what) {
			return new Unsupported(t.line(), what, code(this.source, t.start()));
		}

		/** {@code (p) { … }}: 메서드나 getter의 인자와 몸 */
		JFunction functionRest(Token from) {
			List<String> params = params();
			List<Stmt> body = block();
			return new JFunction(params, body, null, from.line(), from.start(), this.tokens.get(this.at - 1).end());
		}

		private List<String> params() {
			expect("(");
			List<String> params = new ArrayList<>();
			if (!accept(")")) {
				do {
					Token p = next();
					if (p.kind() != Kind.NAME) {
						throw unsupported(p, "인자는 이름입니다");
					}
					params.add(p.text());
				}
				while (accept(","));
				expect(")");
			}
			return params;
		}

		List<Stmt> block() {
			expect("{");
			List<Stmt> body = new ArrayList<>();
			while (!peek().is("}")) {
				if (peek().kind() == Kind.END) {
					throw unsupported(peek(), "여기에 와야 하는 글자: }");
				}
				body.add(statement());
			}
			expect("}");
			return body;
		}

		private Stmt statement() {
			Token t = peek();
			if (t.is("var") || t.is("let") || t.is("const")) {
				next();
				Token name = next();
				if (name.kind() != Kind.NAME) {
					throw unsupported(name, "변수 이름이 와야 합니다");
				}
				expect("=");
				Js value = expression();
				accept(";");
				return new SVar(name.text(), value, at(t, value));
			}
			if (t.is("if")) {
				next();
				expect("(");
				Js test = expression();
				Token close = peek();
				expect(")");
				List<Stmt> body = peek().is("{") ? block() : List.of(statement());
				if (peek().is("else")) {
					throw unsupported(peek(), "else는 옮기지 않습니다. 조건마다 if를 따로 씁니다(앞의 if가 상태를 바꾸면 뜻이 달라지므로 cozy가 나누지 않습니다)");
				}
				return new SIf(test, body, new JName("if", t.line(), t.start(), close.end()));
			}
			if (t.is("return")) {
				next();
				Js value = (peek().is(";") || peek().is("}")) ? null : expression();
				accept(";");
				return new SReturn(value, (value != null) ? at(t, value) : new JName("return", t.line(), t.start(), t.end()));
			}
			for (String word : List.of("for", "while", "do", "switch", "try", "throw", "function", "class", "new", "delete")) {
				if (t.is(word)) {
					throw unsupported(t, word + " 문장");
				}
			}
			Js expr = expression();
			accept(";");
			return new SExpr(expr);
		}

		/** 문장의 자리: 첫 낱말부터 식 끝까지 */
		private Js at(Token t, Js last) {
			return new JName(t.text(), t.line(), t.start(), last.end());
		}

		Js expression() {
			return assignment();
		}

		private Js assignment() {
			Js left = conditional();
			Token t = peek();
			if (t.kind() == Kind.PUNCT && (t.is("=") || t.is("+=") || t.is("-=") || t.is("*=") || t.is("/="))) {
				next();
				Js value = assignment();
				return new JAssign(t.text(), left, value, left.line(), left.start(), value.end());
			}
			return left;
		}

		private Js conditional() {
			Js test = binary(0);
			if (accept("?")) {
				Js then = assignment();
				expect(":");
				Js otherwise = assignment();
				return new JCond(test, then, otherwise, test.line(), test.start(), otherwise.end());
			}
			return test;
		}

		private static final List<List<String>> LEVELS = List.of(List.of("??"), List.of("||"), List.of("&&"), List.of("|"),
				List.of("^"), List.of("&"), List.of("===", "!==", "==", "!="), List.of("<", ">", "<=", ">=", "instanceof", "in"),
				List.of("+", "-"), List.of("*", "/", "%"));

		private Js binary(int level) {
			if (level >= LEVELS.size()) {
				return unary();
			}
			Js left = binary(level + 1);
			while (true) {
				Token t = peek();
				if (t.kind() == Kind.STRING || !LEVELS.get(level).contains(t.text())) {
					return left;
				}
				next();
				Js right = binary(level + 1);
				left = new JBinary(t.text(), left, right, left.line(), left.start(), right.end());
			}
		}

		private Js unary() {
			Token t = peek();
			if (t.kind() == Kind.PUNCT && (t.is("!") || t.is("-") || t.is("+") || t.is("~"))
					|| t.is("typeof") || t.is("void")) {
				next();
				Js operand = unary();
				return new JUnary(t.text(), operand, t.line(), t.start(), operand.end());
			}
			if (t.kind() == Kind.PUNCT && (t.is("++") || t.is("--"))) {
				next();
				Js operand = unary();
				return new JAssign(t.text(), operand, null, t.line(), t.start(), operand.end());
			}
			Js expr = postfix();
			Token after = peek();
			if (after.kind() == Kind.PUNCT && (after.is("++") || after.is("--"))) {
				next();
				return new JAssign(after.text(), expr, null, expr.line(), expr.start(), after.end());
			}
			return expr;
		}

		private Js postfix() {
			Js expr = primary();
			while (true) {
				Token t = peek();
				if (t.is(".") && t.kind() == Kind.PUNCT) {
					next();
					Token name = next();
					if (name.kind() != Kind.NAME) {
						throw unsupported(name, "'.' 뒤에는 이름이 옵니다");
					}
					expr = new JMember(expr, name.text(), expr.line(), expr.start(), name.end());
				}
				else if (t.is("?.")) {
					throw unsupported(t, "선택적 연결(?.)");
				}
				else if (t.is("[") && t.kind() == Kind.PUNCT) {
					next();
					Js index = expression();
					Token close = next();
					if (!close.is("]") || !(index instanceof JString s)) {
						throw unsupported(t, "[…]로 읽는 값은 글자 이름 하나만 씁니다");
					}
					expr = new JMember(expr, s.value(), expr.line(), expr.start(), close.end());
				}
				else if (t.is("(") && t.kind() == Kind.PUNCT) {
					next();
					List<Js> args = new ArrayList<>();
					if (!accept(")")) {
						do {
							args.add(expression());
						}
						while (accept(","));
						expect(")");
					}
					expr = new JCall(expr, List.copyOf(args), expr.line(), expr.start(), this.tokens.get(this.at - 1).end());
				}
				else {
					return expr;
				}
			}
		}

		private Js primary() {
			Token t = next();
			switch (t.kind()) {
				case NUMBER -> {
					if (!t.text().matches("[0-9]+(\\.[0-9]+)?")) {
						throw unsupported(t, "숫자 " + t.text());
					}
					return new JNumber(t.text(), t.line(), t.start(), t.end());
				}
				case STRING -> {
					return new JString(t.text(), t.line(), t.start(), t.end());
				}
				case NAME -> {
					return switch (t.text()) {
						case "this" -> new JThis(t.line(), t.start(), t.end());
						case "true", "false", "null", "undefined" -> new JWord(t.text(), t.line(), t.start(), t.end());
						case "function" -> {
							List<String> params = params();
							List<Stmt> body = block();
							yield new JFunction(params, body, null, t.line(), t.start(), this.tokens.get(this.at - 1).end());
						}
						default -> {
							if (peek().is("=>")) {
								next();
								yield arrowBody(List.of(t.text()), t);
							}
							yield new JName(t.text(), t.line(), t.start(), t.end());
						}
					};
				}
				case PUNCT -> {
					if (t.is("(")) {
						int mark = this.at;
						if (arrowAhead()) {
							this.at = mark - 1;
							List<String> params = params();
							expect("=>");
							return arrowBody(params, t);
						}
						Js inner = expression();
						expect(")");
						return inner;
					}
					if (t.is("[")) {
						List<Js> items = new ArrayList<>();
						if (!accept("]")) {
							do {
								items.add(expression());
							}
							while (accept(","));
							expect("]");
						}
						return new JArray(List.copyOf(items), t.line(), t.start(), this.tokens.get(this.at - 1).end());
					}
					if (t.is("{")) {
						Map<String, Js> entries = new LinkedHashMap<>();
						while (!peek().is("}")) {
							Token key = next();
							if (key.kind() != Kind.NAME && key.kind() != Kind.STRING) {
								throw unsupported(key, "객체의 키는 이름입니다");
							}
							Js value;
							if (accept(":")) {
								value = expression();
							}
							else {
								throw unsupported(key, "객체의 줄임 표기({ " + key.text() + " })");
							}
							entries.put(key.text(), value);
							if (!accept(",")) {
								break;
							}
						}
						expect("}");
						return new JObject(entries, t.line(), t.start(), this.tokens.get(this.at - 1).end());
					}
					throw unsupported(t, "'" + t.text() + "'");
				}
				default -> throw unsupported(t, "식이 끝났습니다");
			}
		}

		/** {@code (} 다음이 화살표 함수의 인자인가 */
		private boolean arrowAhead() {
			int depth = 1;
			for (int i = this.at; i < this.tokens.size(); i++) {
				Token t = this.tokens.get(i);
				if (t.is("(")) {
					depth++;
				}
				else if (t.is(")")) {
					depth--;
					if (depth == 0) {
						return i + 1 < this.tokens.size() && this.tokens.get(i + 1).is("=>");
					}
				}
			}
			return false;
		}

		private Js arrowBody(List<String> params, Token from) {
			if (peek().is("{")) {
				List<Stmt> body = block();
				return new JFunction(params, body, null, from.line(), from.start(), this.tokens.get(this.at - 1).end());
			}
			Js expr = assignment();
			return new JFunction(params, List.of(), expr, from.line(), from.start(), expr.end());
		}
	}

	/** 읽은 JS를 명세의 낱말로 */
	private static final class Translator {

		private final String module;

		private final String name;

		private final String source;

		private final Map<String, Js> literals;

		private final Map<String, JFunction> getters;

		private final Map<String, JFunction> methods;

		private final JFunction init;

		private final Map<String, State> state = new LinkedHashMap<>();

		private final Map<String, Expr> computed = new LinkedHashMap<>();

		private final Set<String> visiting = new HashSet<>();

		Translator(String module, String name, String source, Map<String, Js> literals, Map<String, JFunction> getters,
				Map<String, JFunction> methods, JFunction init) {
			this.module = module;
			this.name = name;
			this.source = source;
			this.literals = literals;
			this.getters = getters;
			this.methods = methods;
			this.init = init;
		}

		Component component(String key, String purpose) {
			Map<String, String> listens = new LinkedHashMap<>();
			Map<String, Js> reads = new LinkedHashMap<>();
			if (this.init != null) {
				init(reads, listens);
			}
			Set<String> names = new LinkedHashSet<>(this.literals.keySet());
			names.addAll(reads.keySet());
			for (String n : names) {
				this.state.put(n, state(n, this.literals.get(n), reads.get(n)));
			}
			for (String n : this.getters.keySet()) {
				computed(n);
			}
			Map<String, List<Expr>> actions = new LinkedHashMap<>();
			this.methods.forEach((n, f) -> actions.put(n, action(f)));
			for (Map.Entry<String, String> listen : listens.entrySet()) {
				if (!actions.containsKey(listen.getValue())) {
					throw new Unsupported(this.init.line(), "받는 화면 이벤트가 부르는 이름이 동작이 아닙니다: " + listen.getKey() + " → " + listen.getValue(),
							code(this.init));
				}
			}
			return new Component(key, purpose, this.state, this.computed, actions, listens);
		}

		// init: 읽는 data-*와 받는 화면 이벤트

		private void init(Map<String, Js> reads, Map<String, String> listens) {
			if (!this.init.params().isEmpty()) {
				throw fail(this.init, "init은 인자를 받지 않습니다");
			}
			Set<String> self = new HashSet<>();
			for (Stmt s : this.init.body()) {
				if (s instanceof SVar v && v.value() instanceof JThis) {
					self.add(v.name());
					continue;
				}
				if (s instanceof SExpr e && e.expr() instanceof JAssign a && a.op().equals("=") && a.target() instanceof JName n
						&& n.name().equals("root") && a.value() instanceof JMember m && m.object() instanceof JThis
						&& (m.name().equals("$root") || m.name().equals("$el"))) {
					continue;
				}
				if (s instanceof SExpr e && e.expr() instanceof JAssign a && a.op().equals("=") && a.target() instanceof JMember m
						&& m.object() instanceof JThis) {
					if (reads.put(m.name(), a.value()) != null) {
						throw fail(a, "init에서 두 번 정하는 상태입니다: " + m.name());
					}
					continue;
				}
				if (s instanceof SExpr e && e.expr() instanceof JCall call && listen(call, self, listens)) {
					continue;
				}
				throw fail(s.at(), "init에서는 root = this.$root, this.상태 = root.dataset.이름(Number, parseInt, === 'true', || 처음 값), "
						+ "window.addEventListener('모듈:이름', …)만 옮깁니다");
			}
		}

		/** {@code window.addEventListener('memo:archived', function () { self.clear(); })} */
		private boolean listen(JCall call, Set<String> self, Map<String, String> listens) {
			if (!(call.callee() instanceof JMember m && m.name().equals("addEventListener") && m.object() instanceof JName w
					&& w.name().equals("window") && call.args().size() == 2 && call.args().get(0) instanceof JString event
					&& call.args().get(1) instanceof JFunction handler)) {
				return false;
			}
			if (!BehaviorSpec.EVENT.matcher(event.value()).matches()) {
				throw fail(call, "받는 화면 이벤트의 이름은 모듈:이름 모양입니다: " + event.value());
			}
			Js only = (handler.expression() != null) ? handler.expression()
					: (handler.body().size() == 1 && handler.body().getFirst() instanceof SExpr e) ? e.expr() : null;
			if (handler.params().size() > 1 || !(only instanceof JCall target) || !(target.callee() instanceof JMember method)
					|| !(method.object() instanceof JThis || (method.object() instanceof JName n && self.contains(n.name())))
					|| target.args().size() > 1 || (target.args().size() == 1 && !(target.args().getFirst() instanceof JName arg
							&& handler.params().size() == 1 && arg.name().equals(handler.params().getFirst())))) {
				throw fail(call, "받는 화면 이벤트에서는 동작 하나만 부릅니다: function (event) { self.동작(event); }");
			}
			if (listens.put(event.value(), method.name()) != null) {
				throw fail(call, "두 번 받는 화면 이벤트입니다: " + event.value());
			}
			return true;
		}

		private State state(String n, Js literal, Js read) {
			Type type = null;
			String initial = null;
			if (literal != null) {
				Object[] lit = literal(literal);
				type = (Type) lit[0];
				initial = (String) lit[1];
			}
			String data = null;
			if (read != null) {
				Object[] r = reader(read);
				Type readType = (Type) r[0];
				if (type != null && type != readType) {
					throw fail(read, "처음 값과 읽는 종류가 다른 상태입니다: " + n + " (처음 값 " + type.word() + ", 읽는 종류 " + readType.word() + ")");
				}
				type = readType;
				data = (String) r[1];
				if (Boolean.TRUE.equals(r[3])) {
					// data-*가 없을 때 옛 JS는 || 뒤의 값을 썼다. 만든 JS는 처음 값을 쓴다
					initial = (String) r[2];
				}
			}
			String print = type.word() + ((initial != null) ? "=" + initial : "") + ((data != null) ? "@" + data : "");
			try {
				return State.parse(print, "상태 " + n);
			}
			catch (CliException ex) {
				throw fail((literal != null) ? literal : read, ex.getMessage());
			}
		}

		/** 글자 그대로의 처음 값: 종류와 정리한 값(빈 값이면 null) */
		private Object[] literal(Js js) {
			return switch (js) {
				case JNumber n -> new Object[] { Type.NUMBER, zero(Expr.canonicalNumber(n.text())) };
				case JUnary u when u.op().equals("-") && u.operand() instanceof JNumber n ->
					new Object[] { Type.NUMBER, zero(Expr.canonicalNumber("-" + n.text())) };
				case JString s -> new Object[] { Type.TEXT, s.value().isEmpty() ? null : s.value() };
				case JWord w when w.word().equals("true") || w.word().equals("false") ->
					new Object[] { Type.BOOL, w.word().equals("true") ? "true" : null };
				case JArray a -> {
					List<String> items = new ArrayList<>();
					for (Js item : a.items()) {
						if (!(item instanceof JString s)) {
							throw fail(item, "목록 상태의 처음 값은 글자 목록입니다");
						}
						items.add(s.value());
					}
					yield new Object[] { Type.LIST, items.isEmpty() ? null : String.join(",", items) };
				}
				default -> throw fail(js, "상태의 처음 값은 숫자, 글자, true·false, 글자 목록입니다(null과 객체는 낱말 밖)");
			};
		}

		private static String zero(String number) {
			return number.equals("0") ? null : number;
		}

		/** init의 읽기: 종류, data-이름, 없을 때의 값(종류의 빈 값이면 null), 없을 때의 값을 적었는가 */
		private Object[] reader(Js js) {
			if (js instanceof JBinary b && b.op().equals("||")) {
				Object[] inner = reader(b.left());
				Object[] fallback = literal(b.right());
				if (fallback[0] != inner[0]) {
					throw fail(b.right(), "|| 뒤의 값은 " + ((Type) inner[0]).word() + "입니다");
				}
				return new Object[] { inner[0], inner[1], fallback[1], true };
			}
			if (js instanceof JBinary b && b.op().equals("===") && b.right() instanceof JString s && s.value().equals("true")) {
				return new Object[] { Type.BOOL, dataset(b.left()), null, false };
			}
			if (js instanceof JCall c && c.callee() instanceof JName f
					&& (f.name().equals("Number") || f.name().equals("parseFloat") || f.name().equals("parseInt")) && !c.args().isEmpty()
					&& (c.args().size() == 1 || (f.name().equals("parseInt") && c.args().size() == 2
							&& c.args().get(1) instanceof JNumber radix && radix.text().equals("10")))) {
				return new Object[] { Type.NUMBER, dataset(c.args().getFirst()), null, false };
			}
			return new Object[] { Type.TEXT, dataset(js), null, false };
		}

		/** {@code root.dataset.limit} → {@code data-limit} */
		private String dataset(Js js) {
			if (js instanceof JMember m && m.object() instanceof JMember d && d.name().equals("dataset")
					&& (d.object() instanceof JName r && r.name().equals("root")
							|| d.object() instanceof JMember el && el.object() instanceof JThis
									&& (el.name().equals("$root") || el.name().equals("$el")))) {
				if (!m.name().matches("[a-z][A-Za-z0-9]*")) {
					throw fail(js, "data-* 이름으로 바꿀 수 없는 dataset 이름: " + m.name());
				}
				return "data-" + m.name().replaceAll("([A-Z])", "-$1").toLowerCase(Locale.ROOT);
			}
			throw fail(js, "init에서는 x-data 요소의 data-*만 읽습니다(root.dataset.이름). 화면의 다른 요소(querySelector 등)는 읽지 않습니다. 서버 값은 "
					+ "data-*로 넘깁니다");
		}

		// 계산

		private Expr computed(String n) {
			Expr known = this.computed.get(n);
			if (known != null) {
				return known;
			}
			JFunction f = this.getters.get(n);
			if (!this.visiting.add(n)) {
				throw fail(f, "계산이 돌고 돌아 자기 자신을 씁니다");
			}
			Map<String, Js> locals = new HashMap<>();
			Js result = null;
			for (Stmt s : f.body()) {
				if (result != null) {
					throw fail(s.at(), "return 뒤의 문장");
				}
				if (s instanceof SVar v) {
					locals.put(v.name(), v.value());
				}
				else if (s instanceof SReturn r && r.value() != null) {
					result = r.value();
				}
				else {
					throw fail(s.at(), "계산(getter)에는 지역 변수와 return 식 하나만 둡니다");
				}
			}
			if (result == null) {
				throw fail(f, "계산(getter)이 값을 돌려주지 않습니다");
			}
			Expr expr = value(result, locals, null);
			this.visiting.remove(n);
			this.computed.put(n, expr);
			return expr;
		}

		// 동작

		private List<Expr> action(JFunction f) {
			if (f.params().size() > 1) {
				throw fail(f, "동작은 이벤트 하나만 받습니다");
			}
			String event = f.params().isEmpty() ? null : f.params().getFirst();
			List<Expr> steps = steps(f.body(), new HashMap<>(), event);
			if (steps.isEmpty()) {
				throw fail(f, "하는 일이 없는 동작");
			}
			return steps;
		}

		private List<Expr> steps(List<Stmt> body, Map<String, Js> locals, String event) {
			List<Expr> steps = new ArrayList<>();
			for (Stmt s : body) {
				switch (s) {
					case SVar v -> locals.put(v.name(), v.value());
					case SIf i -> {
						List<Expr> inner = steps(i.body(), new HashMap<>(locals), event);
						if (inner.isEmpty()) {
							throw fail(i.at(), "하는 일이 없는 if");
						}
						List<Expr> args = new ArrayList<>();
						args.add(value(i.test(), locals, event));
						args.addAll(inner);
						steps.add(call("when", args));
					}
					case SExpr e -> steps.add(step(e.expr(), locals, event));
					case SReturn r -> throw fail(r.at(), "동작 안의 return");
				}
			}
			return steps;
		}

		private Expr step(Js js, Map<String, Js> locals, String event) {
			if (js instanceof JAssign a) {
				if (!(a.target() instanceof JMember m && m.object() instanceof JThis)) {
					throw fail(a, "바꿀 수 있는 것은 this.상태입니다");
				}
				String target = m.name();
				State s = this.state.get(target);
				if (s == null) {
					throw fail(a, "'" + target + "'은 상태가 아닙니다. 바꿀 수 있는 것은 상태입니다");
				}
				Expr name = new Expr.Name(target);
				switch (a.op()) {
					case "++" -> {
						return call("increment", List.of(name));
					}
					case "--" -> {
						return call("decrement", List.of(name));
					}
					case "+=", "-=" -> {
						if (!(a.value() instanceof JNumber n)) {
							throw fail(a, a.op() + " 뒤에는 숫자를 씁니다");
						}
						return counter(a.op().equals("+=") ? "increment" : "decrement", name, n);
					}
					case "=" -> {
						Js v = a.value();
						if (v instanceof JUnary u && u.op().equals("!") && same(u.operand(), target)) {
							return call("toggle", List.of(name));
						}
						if (v instanceof JBinary b && (b.op().equals("+") || b.op().equals("-")) && same(b.left(), target)
								&& b.right() instanceof JNumber n && s.type() == Type.NUMBER) {
							return counter(b.op().equals("+") ? "increment" : "decrement", name, n);
						}
						if (v instanceof JArray) {
							Object[] lit = literal(v);
							if (s.type() == Type.LIST && java.util.Objects.equals(lit[1], s.initial())) {
								return call("reset", List.of(name));
							}
							throw fail(a, "목록에는 처음 값으로 되돌리기(reset)만 대입합니다. 더하고 빼기는 addItem·removeItem입니다");
						}
						return call("set", List.of(name, value(v, locals, event)));
					}
					default -> throw fail(a, a.op() + " 대입");
				}
			}
			if (js instanceof JCall c && c.callee() instanceof JMember m && m.object() instanceof JThis && m.name().equals("$dispatch")) {
				return emit(c);
			}
			throw fail(js, "동작의 문장은 this.상태 대입, if, this.$dispatch('모듈:이름', { … })입니다");
		}

		private Expr counter(String function, Expr name, JNumber n) {
			String amount = Expr.canonicalNumber(n.text());
			return amount.equals("1") ? call(function, List.of(name)) : call(function, List.of(name, new Expr.Num(amount)));
		}

		private Expr emit(JCall c) {
			if (c.args().isEmpty() || c.args().size() > 2 || !(c.args().getFirst() instanceof JString event)) {
				throw fail(c, "this.$dispatch('" + this.module + ":이름', { 상태: this.상태 })입니다");
			}
			List<Expr> args = new ArrayList<>();
			args.add(new Expr.Text(event.value()));
			if (c.args().size() == 2) {
				if (!(c.args().get(1) instanceof JObject detail)) {
					throw fail(c, "$dispatch가 함께 보내는 값은 { 이름: this.이름 } 모양입니다");
				}
				for (Map.Entry<String, Js> e : detail.entries().entrySet()) {
					if (!same(e.getValue(), e.getKey())) {
						throw fail(e.getValue(), "$dispatch가 함께 보내는 값은 같은 이름의 상태나 계산입니다({ " + e.getKey() + ": this." + e.getKey() + " })");
					}
					args.add(new Expr.Name(e.getKey()));
				}
			}
			return call("emit", args);
		}

		// 값

		private Expr value(Js js, Map<String, Js> locals, String event) {
			return switch (js) {
				case JNumber n -> new Expr.Num(Expr.canonicalNumber(n.text()));
				case JUnary u when u.op().equals("-") && u.operand() instanceof JNumber n -> new Expr.Num(Expr.canonicalNumber("-" + n.text()));
				case JUnary u when u.op().equals("!") -> call("not", List.of(value(u.operand(), locals, event)));
				case JString s -> new Expr.Text(s.value());
				case JWord w when w.word().equals("true") || w.word().equals("false") -> new Expr.Bool(w.word().equals("true"));
				case JName n when locals.containsKey(n.name()) -> {
					Js bound = locals.remove(n.name());
					try {
						yield value(bound, locals, event);
					}
					finally {
						locals.put(n.name(), bound);
					}
				}
				case JMember m when m.object() instanceof JThis -> name(m);
				case JMember m when m.name().equals("length") -> {
					Expr inner = value(m.object(), locals, event);
					yield call((type(inner) == Type.LIST) ? "count" : "length", List.of(inner));
				}
				case JMember m when eventRef(m, event) != null -> eventRef(m, event);
				case JCall c when c.callee() instanceof JMember m && m.name().equals("includes") && c.args().size() == 1 -> {
					Expr list = value(m.object(), locals, event);
					if (type(list) != Type.LIST) {
						throw fail(c, "includes는 목록 상태에만 씁니다");
					}
					yield call("includes", List.of(list, value(c.args().getFirst(), locals, event)));
				}
				case JCall c when c.callee() instanceof JName f && (f.name().equals("Number") || f.name().equals("parseFloat"))
						&& c.args().size() == 1 -> call("number", List.of(value(c.args().getFirst(), locals, event)));
				case JBinary b when b.op().equals("&&") || b.op().equals("||") -> {
					String function = b.op().equals("&&") ? "and" : "or";
					List<Expr> args = new ArrayList<>();
					flatten(b, b.op()).forEach((part) -> args.add(value(part, locals, event)));
					yield call(function, args);
				}
				case JBinary b when b.op().equals("+") -> format(b, locals, event);
				case JBinary b when b.op().equals("==") || b.op().equals("!=") ->
					throw fail(b, "느슨한 비교(" + b.op() + ")는 옮기지 않습니다. 종류까지 같은지 보는 " + b.op() + "=로 바꾼 뒤 다시 실행합니다");
				case JBinary b when COMPARE.containsKey(b.op()) ->
					call(COMPARE.get(b.op()), List.of(value(b.left(), locals, event), value(b.right(), locals, event)));
				case JCond c -> call("if", List.of(value(c.test(), locals, event), value(c.then(), locals, event),
						value(c.otherwise(), locals, event)));
				default -> throw fail(js, "낱말로 적을 수 없는 식");
			};
		}

		private static final Map<String, String> COMPARE = Map.of("===", "eq", "!==", "ne", ">", "gt", "<", "lt", ">=", "ge", "<=",
				"le");

		/** {@code this.이름}: 상태나 계산 */
		private Expr name(JMember m) {
			String n = m.name();
			if (this.state.containsKey(n)) {
				return new Expr.Name(n);
			}
			if (this.getters.containsKey(n)) {
				computed(n);
				return new Expr.Name(n);
			}
			if (this.methods.containsKey(n)) {
				throw fail(m, "'" + n + "'은 동작이라 값으로 읽을 수 없습니다");
			}
			throw fail(m, "this." + n + "은 상태나 계산이 아닙니다");
		}

		/** 이벤트 값. 아니면 null */
		private Expr eventRef(JMember m, String event) {
			if (event == null) {
				return null;
			}
			if (m.object() instanceof JMember t && t.name().equals("target") && t.object() instanceof JName e && e.name().equals(event)) {
				return switch (m.name()) {
					case "value" -> new Expr.EventRef("value", null);
					case "checked" -> new Expr.EventRef("checked", null);
					default -> null;
				};
			}
			if (m.object() instanceof JMember d && d.name().equals("detail") && d.object() instanceof JName e && e.name().equals(event)
					&& BehaviorSpec.NAME.matcher(m.name()).matches()) {
				return new Expr.EventRef("detail", m.name());
			}
			if (m.object() instanceof JMember d && d.name().equals("dataset") && d.object() instanceof JMember t
					&& t.name().equals("target") && t.object() instanceof JName e && e.name().equals(event)
					&& BehaviorSpec.NAME.matcher(m.name()).matches()) {
				return new Expr.EventRef("data", m.name());
			}
			return null;
		}

		/** 글자 잇기 → {@code format('…{이름}…'[, 'ko-KR'])} */
		private Expr format(JBinary b, Map<String, Js> locals, String event) {
			List<Js> parts = flatten(b, "+");
			// JS는 왼쪽부터 더한다. 앞의 둘이 모두 글자가 아니면 숫자 더하기다
			if (!textual(parts.get(0), locals) && !textual(parts.get(1), locals)) {
				throw fail(b, "숫자 더하기는 낱말 밖입니다(+는 글자를 잇는 데만 씁니다)");
			}
			StringBuilder template = new StringBuilder();
			Set<String> localesUsed = new LinkedHashSet<>();
			List<String> plainNumbers = new ArrayList<>();
			for (Js part : parts) {
				Js resolved = resolve(part, locals);
				if (resolved instanceof JString s) {
					template.append(s.value().replace("{", "{{").replace("}", "}}"));
				}
				else if (resolved instanceof JNumber n) {
					template.append(Expr.canonicalNumber(n.text()));
				}
				else if (resolved instanceof JCall c && c.callee() instanceof JMember m && m.name().equals("toLocaleString")
						&& m.object() instanceof JMember target && target.object() instanceof JThis && c.args().size() == 1
						&& c.args().getFirst() instanceof JString locale) {
					Expr n = name(target);
					if (type(n) != Type.NUMBER) {
						throw fail(c, "toLocaleString은 숫자에만 씁니다");
					}
					localesUsed.add(locale.value());
					template.append('{').append(target.name()).append('}');
				}
				else if (resolved instanceof JMember m && m.object() instanceof JThis) {
					Expr n = name(m);
					Type t = type(n);
					if (t == Type.LIST) {
						throw fail(m, "목록을 글자로 잇는 모양은 낱말 밖입니다");
					}
					if (t == Type.NUMBER) {
						plainNumbers.add(m.name());
					}
					template.append('{').append(m.name()).append('}');
				}
				else {
					throw fail(part, "글자 잇기(format)에는 글자와 상태·계산의 이름만 씁니다. 식은 계산으로 따로 둡니다");
				}
			}
			if (localesUsed.size() > 1) {
				throw fail(b, "숫자 형식이 둘 이상입니다: " + localesUsed);
			}
			if (!localesUsed.isEmpty() && !plainNumbers.isEmpty()) {
				throw fail(b, "숫자 형식(toLocaleString)을 쓰지 않은 숫자가 함께 있습니다: " + plainNumbers);
			}
			List<Expr> args = new ArrayList<>();
			args.add(new Expr.Text(template.toString()));
			if (!localesUsed.isEmpty()) {
				args.add(new Expr.Text(localesUsed.iterator().next()));
			}
			return call("format", args);
		}

		private Js resolve(Js js, Map<String, Js> locals) {
			Js current = js;
			Set<String> seen = new HashSet<>();
			while (current instanceof JName n && locals.containsKey(n.name()) && seen.add(n.name())) {
				current = locals.get(n.name());
			}
			return current;
		}

		/** 글자인가 (글자 그대로, toLocaleString, text 상태와 계산) */
		private boolean textual(Js js, Map<String, Js> locals) {
			Js resolved = resolve(js, locals);
			if (resolved instanceof JString) {
				return true;
			}
			if (resolved instanceof JCall c && c.callee() instanceof JMember m && m.name().equals("toLocaleString")) {
				return true;
			}
			if (resolved instanceof JBinary b && b.op().equals("+")) {
				return true;
			}
			if (resolved instanceof JMember m && m.object() instanceof JThis
					&& (this.state.containsKey(m.name()) || this.getters.containsKey(m.name()))) {
				return type(name(m)) == Type.TEXT;
			}
			return false;
		}

		private List<Js> flatten(Js js, String op) {
			List<Js> parts = new ArrayList<>();
			if (js instanceof JBinary b && b.op().equals(op)) {
				parts.addAll(flatten(b.left(), op));
				if (op.equals("+") && b.right() instanceof JBinary r && r.op().equals("+")) {
					// 오른쪽에 괄호로 묶은 더하기: 그 자리는 따로 계산된다
					throw fail(r, "괄호로 묶은 더하기");
				}
				parts.addAll(flatten(b.right(), op));
			}
			else {
				parts.add(js);
			}
			return parts;
		}

		private boolean same(Js js, String name) {
			return js instanceof JMember m && m.object() instanceof JThis && m.name().equals(name);
		}

		/** 값의 종류 */
		private Type type(Expr expr) {
			return switch (expr) {
				case Expr.Num n -> Type.NUMBER;
				case Expr.Bool b -> Type.BOOL;
				case Expr.Text t -> Type.TEXT;
				case Expr.EventRef e -> e.kind().equals("checked") ? Type.BOOL : e.kind().equals("detail") ? Type.ANY : Type.TEXT;
				case Expr.Name n -> this.state.containsKey(n.name()) ? this.state.get(n.name()).type() : type(computed(n.name()));
				case Expr.Call c -> switch (c.function()) {
					case "count", "length", "number" -> Type.NUMBER;
					case "format" -> Type.TEXT;
					case "if" -> type(c.args().get(1));
					default -> Type.BOOL;
				};
			};
		}

		private static Expr call(String function, List<Expr> args) {
			return new Expr.Call(function, List.copyOf(args));
		}

		private Unsupported fail(Js js, String what) {
			return new Unsupported(js.line(), what, code(js));
		}

		private String code(Js js) {
			String text = this.source.substring(js.start(), Math.min(js.end(), this.source.length())).strip();
			text = text.replaceAll("\\s*\\n\\s*", " ");
			return (text.length() > 120) ? text.substring(0, 117) + "…" : text;
		}
	}

	private static String code(String source, int start) {
		int end = source.indexOf('\n', start);
		String text = source.substring(start, (end < 0) ? source.length() : end).strip();
		return (text.length() > 120) ? text.substring(0, 117) + "…" : text;
	}
}
