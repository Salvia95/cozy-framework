package io.salvia95.cozy.cli.internal.recipe;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 레시피의 필드 {@code 이름:종류[!][:라벨]} (ADR-0032 부록 §1). 예: {@code title:text!:제목}, {@code pinned:bool:고정}.
 *
 * @param name camelCase 이름
 * @param kind 종류
 * @param required {@code !}
 * @param label 화면에 보이는 이름. 빼면 이름 그대로
 */
public record Field(String name, Kind kind, boolean required, String label) {

	private static final Pattern FORMAT = Pattern.compile("([a-z][A-Za-z0-9]*):([a-z]+)(!?)(?::(.+))?");

	/** 레시피가 직접 넣는 이름이라 필드로 쓸 수 없다 */
	private static final Set<String> RESERVED = Set.of("id", "createdAt", "updatedAt", "eventId", "schemaVersion", "page", "q");

	/** 필드 종류 */
	public enum Kind {

		TEXT("text"), LONGTEXT("longtext"), INT("int"), LONG("long"), BOOL("bool"), DATE("date"), INSTANT("instant");

		private final String word;

		Kind(String word) {
			this.word = word;
		}

		public String word() {
			return this.word;
		}

		static Kind of(String word) {
			for (Kind kind : values()) {
				if (kind.word.equals(word)) {
					return kind;
				}
			}
			throw new CliException("필드 종류 '" + word + "'를 모릅니다. text, longtext, int, long, bool, date, instant 중 하나를 씁니다.");
		}
	}

	public static Field parse(String text) {
		Matcher m = FORMAT.matcher(text);
		if (!m.matches()) {
			throw new CliException("필드 '" + text + "'는 이름:종류[!][:라벨] 형식이어야 합니다 (예: title:text!:제목).");
		}
		String name = m.group(1);
		if (RESERVED.contains(name)) {
			throw new CliException("필드 이름 '" + name + "'는 명령이 직접 넣는 이름이라 쓸 수 없습니다. 다른 이름을 쓰세요.");
		}
		return new Field(name, Kind.of(m.group(2)), !m.group(3).isEmpty(),
				(m.group(4) != null) ? Names.label(m.group(4), "필드 라벨") : name);
	}

	public static List<Field> parseAll(List<String> texts) {
		List<Field> fields = texts.stream().map(Field::parse).toList();
		if (fields.stream().map(Field::name).distinct().count() != fields.size()) {
			throw new CliException("같은 이름의 필드가 둘 이상입니다.");
		}
		return fields;
	}

	/** 열 이름 ({@code createdAt} → {@code created_at}) */
	public String column() {
		return Names.snakeOf(this.name);
	}

	public String pascal() {
		return Names.capitalize(this.name);
	}

	/** 도메인·spi DTO·폼·Facade 파라미터의 타입 (값이 없을 수 있는 것은 감싼 타입) */
	public String javaType() {
		return switch (this.kind) {
			case TEXT, LONGTEXT -> "String";
			case INT -> "Integer";
			case LONG -> "Long";
			case BOOL -> "boolean";
			case DATE -> "LocalDate";
			case INSTANT -> "Instant";
		};
	}

	/** 이벤트 필드의 타입 */
	public String eventType() {
		return switch (this.kind) {
			case TEXT, LONGTEXT -> "String";
			case INT -> "int";
			case LONG -> "long";
			case BOOL -> "boolean";
			case DATE -> "LocalDate";
			case INSTANT -> "Instant";
		};
	}

	/** 매니페스트 파라미터 타입 (카탈로그가 그대로 쓰는 이름) */
	public String manifestType() {
		return switch (this.kind) {
			case TEXT, LONGTEXT, DATE, INSTANT -> "String";
			case INT -> "int";
			case LONG -> "long";
			case BOOL -> "boolean";
		};
	}

	/** {@code java.time.*} import가 필요하면 그 이름 */
	public String importName() {
		return switch (this.kind) {
			case DATE -> "java.time.LocalDate";
			case INSTANT -> "java.time.Instant";
			default -> null;
		};
	}

	public String getter() {
		return ((this.kind == Kind.BOOL) ? "is" : "get") + pascal();
	}

	/** SQLite 열 정의 */
	public String sql() {
		String c = column();
		return switch (this.kind) {
			case TEXT -> this.required ? "TEXT    NOT NULL CHECK (length(" + c + ") > 0)" : "TEXT";
			case LONGTEXT -> this.required ? "TEXT    NOT NULL CHECK (length(" + c + ") > 0)" : "TEXT    NOT NULL DEFAULT ''";
			case INT, LONG -> this.required ? "INTEGER NOT NULL" : "INTEGER";
			case BOOL -> "INTEGER NOT NULL DEFAULT 0 CHECK (" + c + " IN (0, 1))";
			case DATE, INSTANT -> this.required ? "TEXT    NOT NULL" : "TEXT";
		};
	}

	/** cozy-ui {@code field(name, label, type)}의 type */
	public String input() {
		return switch (this.kind) {
			case TEXT -> "text";
			case LONGTEXT -> "textarea";
			case INT, LONG -> "number";
			case BOOL -> "checkbox";
			case DATE -> "date";
			case INSTANT -> throw new IllegalStateException("instant는 폼에 두지 않는다");
		};
	}

	/** 샘플 값(YAML·JSON 표기) */
	public String sample() {
		return switch (this.kind) {
			case TEXT -> quote("예시 " + this.label);
			case LONGTEXT -> "\"첫 줄\\n둘째 줄\"";
			case INT -> "3";
			case LONG -> "1";
			case BOOL -> "true";
			case DATE -> "\"2026-11-06\"";
			case INSTANT -> "\"2026-11-06T01:00:00Z\"";
		};
	}

	/** 샘플 값(Java 식) */
	public String javaSample() {
		return switch (this.kind) {
			case TEXT -> quote("예시 " + this.label);
			case LONGTEXT -> "\"첫 줄\\n둘째 줄\"";
			case INT -> "3";
			case LONG -> "1L";
			case BOOL -> "true";
			case DATE -> "LocalDate.of(2026, 11, 6)";
			case INSTANT -> "Instant.parse(\"2026-11-06T01:00:00Z\")";
		};
	}

	/** 빈 상태의 샘플 값(YAML) */
	public String emptySample() {
		return switch (this.kind) {
			case TEXT -> this.required ? quote(this.label) : "null";
			case LONGTEXT -> "\"\"";
			case INT, LONG, DATE, INSTANT -> "null";
			case BOOL -> "false";
		};
	}

	static String quote(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
