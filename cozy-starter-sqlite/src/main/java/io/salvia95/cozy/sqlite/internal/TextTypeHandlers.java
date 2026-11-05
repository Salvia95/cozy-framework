package io.salvia95.cozy.sqlite.internal;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * SQLite 저장 형식을 고정하는 MyBatis TypeHandler (PRD §6.3).
 *
 * <ul>
 * <li>{@link Instant}: UTC ISO-8601 TEXT, 소수점 9자리 고정 ({@code 2026-11-06T01:02:03.000000000Z})</li>
 * <li>{@link LocalDateTime}: ISO-8601 TEXT, 소수점 9자리 고정 ({@code 2026-11-06T10:02:03.000000000})</li>
 * <li>{@link LocalDate}: {@code 2026-11-06}</li>
 * <li>{@link UUID}: 소문자 TEXT</li>
 * <li>{@code boolean}/{@link Boolean}: INTEGER 0/1</li>
 * </ul>
 * 자릿수를 고정하므로 문자열 정렬이 시간 순서와 같다. 읽을 때는 SQLite의 {@code CURRENT_TIMESTAMP}
 * 형식({@code 2026-11-06 01:02:03}, UTC)과 소수점 자릿수가 다른 값도 받아들인다.
 */
public final class TextTypeHandlers {

	static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter
		.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", Locale.ROOT)
		.withZone(ZoneOffset.UTC);

	static final DateTimeFormatter LOCAL_DATE_TIME_FORMAT = DateTimeFormatter
		.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS", Locale.ROOT);

	private TextTypeHandlers() {
	}

	public static void registerAll(TypeHandlerRegistry registry) {
		registry.register(Instant.class, new TextHandler<>(INSTANT_FORMAT::format, TextTypeHandlers::parseInstant));
		registry.register(LocalDateTime.class,
				new TextHandler<>(LOCAL_DATE_TIME_FORMAT::format, TextTypeHandlers::parseLocalDateTime));
		registry.register(LocalDate.class, new TextHandler<>(LocalDate::toString, LocalDate::parse));
		registry.register(UUID.class, new TextHandler<>((uuid) -> uuid.toString().toLowerCase(Locale.ROOT), UUID::fromString));
		BooleanIntegerHandler booleans = new BooleanIntegerHandler();
		registry.register(Boolean.class, booleans);
		registry.register(boolean.class, booleans);
	}

	static Instant parseInstant(String text) {
		String value = text.trim().replace(' ', 'T');
		if (!(value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$"))) {
			value = value + "Z";
		}
		return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value, Instant::from);
	}

	static LocalDateTime parseLocalDateTime(String text) {
		return LocalDateTime.parse(text.trim().replace(' ', 'T'));
	}

	static final class TextHandler<T> extends BaseTypeHandler<T> {

		private final Function<T, String> format;

		private final Function<String, T> parse;

		TextHandler(Function<T, String> format, Function<String, T> parse) {
			this.format = format;
			this.parse = parse;
		}

		@Override
		public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
			ps.setString(i, this.format.apply(parameter));
		}

		@Override
		public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
			return parse(rs.getString(columnName));
		}

		@Override
		public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
			return parse(rs.getString(columnIndex));
		}

		@Override
		public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
			return parse(cs.getString(columnIndex));
		}

		private T parse(String text) throws SQLException {
			if (text == null) {
				return null;
			}
			try {
				return this.parse.apply(text);
			}
			catch (RuntimeException ex) {
				throw new SQLException("[cozy] 저장된 값 '" + text + "'를 읽을 수 없습니다", ex);
			}
		}

	}

	static final class BooleanIntegerHandler extends BaseTypeHandler<Boolean> {

		@Override
		public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType)
				throws SQLException {
			ps.setInt(i, parameter ? 1 : 0);
		}

		@Override
		public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
			int value = rs.getInt(columnName);
			return rs.wasNull() ? null : value != 0;
		}

		@Override
		public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
			int value = rs.getInt(columnIndex);
			return rs.wasNull() ? null : value != 0;
		}

		@Override
		public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
			int value = cs.getInt(columnIndex);
			return cs.wasNull() ? null : value != 0;
		}

	}
}
