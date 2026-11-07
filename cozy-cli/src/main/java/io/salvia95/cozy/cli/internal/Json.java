package io.salvia95.cozy.cli.internal;

import java.util.List;
import java.util.Map;

/**
 * {@code --json} 출력. 맵(순서 유지), 목록, 문자열, 숫자, 참/거짓, null만 쓴다. 들여쓰기 2칸, 키 순서는 넣은 순서 그대로라 같은 입력이면
 * 같은 글자가 나온다(테스트가 기대 파일과 비교한다).
 */
public final class Json {

	private Json() {
	}

	public static String write(Object value) {
		StringBuilder out = new StringBuilder();
		write(out, value, 0);
		return out.append('\n').toString();
	}

	private static void write(StringBuilder out, Object value, int depth) {
		switch (value) {
			case null -> out.append("null");
			case String s -> string(out, s);
			case Boolean b -> out.append(b);
			case Number n -> out.append(n);
			case Map<?, ?> map -> {
				if (map.isEmpty()) {
					out.append("{}");
					return;
				}
				out.append("{\n");
				int i = 0;
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					indent(out, depth + 1);
					string(out, String.valueOf(entry.getKey()));
					out.append(": ");
					write(out, entry.getValue(), depth + 1);
					out.append((++i < map.size()) ? ",\n" : "\n");
				}
				indent(out, depth);
				out.append('}');
			}
			case List<?> list -> {
				if (list.isEmpty()) {
					out.append("[]");
					return;
				}
				out.append("[\n");
				for (int i = 0; i < list.size(); i++) {
					indent(out, depth + 1);
					write(out, list.get(i), depth + 1);
					out.append((i + 1 < list.size()) ? ",\n" : "\n");
				}
				indent(out, depth);
				out.append(']');
			}
			default -> string(out, String.valueOf(value));
		}
	}

	private static void indent(StringBuilder out, int depth) {
		out.append("  ".repeat(depth));
	}

	private static void string(StringBuilder out, String s) {
		out.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
	}
}
