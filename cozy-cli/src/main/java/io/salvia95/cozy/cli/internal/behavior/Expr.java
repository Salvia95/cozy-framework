package io.salvia95.cozy.cli.internal.behavior;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 동작 명세의 식 하나. 함수 호출 모양만 있다: {@code and(gt(limit, 0), gt(length, limit))}. 값은 이름(상태, 계산), 숫자, {@code true}·
 * {@code false}, 작은따옴표 글자({@code 'textarea'}), 이벤트 값({@code event.value}, {@code event.checked},
 * {@code event.data.이름}, {@code event.detail.이름})이다. 동작의 단계는 {@code ;}로 잇는다. 같은 식은 같은 글자로 다시 적는다
 * ({@link #print()}).
 */
public sealed interface Expr {

	/** 상태나 계산의 이름 */
	record Name(String name) implements Expr {
	}

	/** 숫자. 글자는 정리한 모양({@code 1.50} → {@code 1.5}) */
	record Num(String text) implements Expr {
	}

	record Bool(boolean value) implements Expr {
	}

	/** 작은따옴표 글자 */
	record Text(String value) implements Expr {
	}

	/**
	 * 이벤트 값.
	 *
	 * @param kind {@code value}, {@code checked}, {@code data}, {@code detail}
	 * @param name {@code data}·{@code detail}의 이름. 나머지는 {@code null}
	 */
	record EventRef(String kind, String name) implements Expr {
	}

	record Call(String function, List<Expr> args) implements Expr {
	}

	/** 같은 식을 같은 글자로 */
	default String print() {
		return switch (this) {
			case Name n -> n.name();
			case Num n -> n.text();
			case Bool b -> Boolean.toString(b.value());
			case Text t -> "'" + t.value().replace("\\", "\\\\").replace("'", "\\'") + "'";
			case EventRef e -> "event." + e.kind() + ((e.name() != null) ? "." + e.name() : "");
			case Call c -> c.function() + "(" + String.join(", ", c.args().stream().map(Expr::print).toList()) + ")";
		};
	}

	static String print(List<Expr> steps) {
		return String.join("; ", steps.stream().map(Expr::print).toList());
	}

	/** 식 하나 */
	static Expr parse(String source, String where) {
		Parser parser = new Parser(source, where);
		Expr expr = parser.expression();
		parser.end();
		return expr;
	}

	/** {@code ;}로 이은 단계들 */
	static List<Expr> parseSteps(String source, String where) {
		Parser parser = new Parser(source, where);
		List<Expr> steps = new ArrayList<>();
		steps.add(parser.expression());
		while (parser.accept(';')) {
			steps.add(parser.expression());
		}
		parser.end();
		return steps;
	}

	/** 손으로 쓴 재귀 하강 파서. 글자 하나씩 읽는다 */
	final class Parser {

		private static final Pattern NAME = Pattern.compile("[a-z][A-Za-z0-9]*");

		private static final Pattern NUMBER = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

		private final String source;

		private final String where;

		private int at;

		Parser(String source, String where) {
			this.source = source;
			this.where = where;
		}

		Expr expression() {
			skip();
			if (this.at >= this.source.length()) {
				throw fail("식이 끝났습니다. 값이나 함수를 적습니다");
			}
			char c = this.source.charAt(this.at);
			if (c == '\'') {
				return text();
			}
			if (c == '-' || Character.isDigit(c)) {
				return number();
			}
			if (Character.isLetter(c)) {
				int start = this.at;
				while (this.at < this.source.length()
						&& (Character.isLetterOrDigit(this.source.charAt(this.at)) || this.source.charAt(this.at) == '.')) {
					this.at++;
				}
				String word = this.source.substring(start, this.at);
				skip();
				if (accept('(')) {
					if (!NAME.matcher(word).matches()) {
						throw fail("읽을 수 없는 함수 이름입니다: " + word);
					}
					List<Expr> args = new ArrayList<>();
					skip();
					if (!accept(')')) {
						do {
							args.add(expression());
						}
						while (accept(','));
						expect(')');
					}
					return new Call(word, List.copyOf(args));
				}
				return word(word, start);
			}
			throw fail("읽을 수 없는 글자입니다: " + c + ". 값은 이름, 숫자, true·false, '글자', event.value 같은 이벤트 값입니다");
		}

		private Expr word(String word, int start) {
			if (word.equals("true") || word.equals("false")) {
				return new Bool(word.equals("true"));
			}
			if (word.startsWith("event.")) {
				String[] parts = word.split("\\.", -1);
				if (parts.length == 2 && (parts[1].equals("value") || parts[1].equals("checked"))) {
					return new EventRef(parts[1], null);
				}
				if (parts.length == 3 && (parts[1].equals("data") || parts[1].equals("detail")) && NAME.matcher(parts[2]).matches()) {
					return new EventRef(parts[1], parts[2]);
				}
				this.at = start;
				throw fail("모르는 이벤트 값입니다: " + word + ". event.value, event.checked, event.data.이름, event.detail.이름을 씁니다");
			}
			if (!NAME.matcher(word).matches()) {
				this.at = start;
				throw fail("읽을 수 없는 이름입니다: " + word + ". 이름은 영문 소문자로 시작하는 카멜 표기입니다");
			}
			return new Name(word);
		}

		private Expr text() {
			StringBuilder value = new StringBuilder();
			this.at++;
			while (this.at < this.source.length()) {
				char c = this.source.charAt(this.at++);
				if (c == '\\' && this.at < this.source.length()) {
					value.append(this.source.charAt(this.at++));
				}
				else if (c == '\'') {
					return new Text(value.toString());
				}
				else if (c == '\n' || c == '\r') {
					break;
				}
				else {
					value.append(c);
				}
			}
			throw fail("글자를 닫는 작은따옴표(')가 없습니다");
		}

		private Expr number() {
			int start = this.at;
			if (this.source.charAt(this.at) == '-') {
				this.at++;
			}
			while (this.at < this.source.length()
					&& (Character.isDigit(this.source.charAt(this.at)) || this.source.charAt(this.at) == '.')) {
				this.at++;
			}
			String text = this.source.substring(start, this.at);
			if (!NUMBER.matcher(text).matches()) {
				this.at = start;
				throw fail("읽을 수 없는 숫자입니다: " + text);
			}
			return new Num(canonicalNumber(text));
		}

		boolean accept(char c) {
			skip();
			if (this.at < this.source.length() && this.source.charAt(this.at) == c) {
				this.at++;
				return true;
			}
			return false;
		}

		private void expect(char c) {
			if (!accept(c)) {
				throw fail("여기에 와야 하는 글자: " + c);
			}
		}

		void end() {
			skip();
			if (this.at < this.source.length()) {
				throw fail("식 뒤에 남은 글자가 있습니다");
			}
		}

		private void skip() {
			while (this.at < this.source.length() && Character.isWhitespace(this.source.charAt(this.at))) {
				this.at++;
			}
		}

		private CliException fail(String why) {
			return new CliException(this.where + ": " + why + " (" + (this.at + 1) + "번째 글자): " + this.source);
		}
	}

	/** 숫자를 정리한 글자. {@code 1.50} → {@code 1.5}, {@code 2.0} → {@code 2}, {@code -0} → {@code 0} */
	static String canonicalNumber(String text) {
		BigDecimal value = new BigDecimal(text).stripTrailingZeros();
		return (value.signum() == 0) ? "0" : value.toPlainString();
	}
}
