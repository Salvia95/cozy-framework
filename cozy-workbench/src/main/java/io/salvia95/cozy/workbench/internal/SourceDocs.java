package io.salvia95.cozy.workbench.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 화면 DTO의 속성 설명을 소스의 Javadoc에서 읽는다. 샘플 데이터 화면이 속성 이름 앞에 보일 사람의 이름이다.
 *
 * <ul>
 * <li>record는 타입 Javadoc의 {@code @param 이름 설명}이다.</li>
 * <li>폼 클래스는 필드 바로 위 Javadoc의 첫 문장이다({@code /** 제목 *}{@code /}).</li>
 * <li>프레임워크 타입({@code CozyPage})은 소스가 앱에 없으므로 여기에 적은 이름을 쓴다. 이름은 그 타입의 Javadoc과 같다.</li>
 * </ul>
 *
 * <p>javac를 거치지 않고 글자로 찾는다. 찾지 못하면 빈 맵이고, 화면은 식별자만 보인다.
 */
final class SourceDocs {

	private SourceDocs() {
	}

	/** 소스가 앱에 없는 프레임워크 타입: 타입 이름 → 속성 → 설명 */
	static final Map<String, Map<String, String>> FRAMEWORK = Map.of("io.salvia95.cozy.ui.CozyPage",
			Map.of("number", "현재 페이지", "size", "페이지 크기", "totalElements", "전체 건수"), "io.salvia95.cozy.ui.CozyOptions",
			Map.of("items", "선택지"), "io.salvia95.cozy.ui.CozyOptions$Option", Map.of("value", "값", "label", "이름"));

	private static final Pattern PARAM = Pattern.compile("@param\\s+(\\w+)\\s+([^@]*)");

	/** 필드 위의 Javadoc: 설명, 애너테이션, 수식어, 타입, 이름 */
	private static final Pattern FIELD = Pattern.compile(
			"/\\*\\*((?:(?!\\*/).)*)\\*/\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*(?:(?:private|protected|public|final|transient|volatile)\\s+)*"
					+ "[\\w.<>,?\\[\\] ]+?\\s+(\\w+)\\s*(?:=[^;]*)?;",
			Pattern.DOTALL);

	/** 앱 소스 폴더 기준 파일 ({@code src/main/java/io/x/MemoViews.java}). 중첩 타입은 바깥 타입의 파일이다 */
	static String file(Class<?> type) {
		Class<?> outer = type;
		while (outer.getEnclosingClass() != null) {
			outer = outer.getEnclosingClass();
		}
		return "src/main/java/" + outer.getName().replace('.', '/') + ".java";
	}

	/**
	 * @param source 타입이 든 파일의 내용
	 * @param type 찾을 타입 (중첩 타입이면 그 짧은 이름으로 찾는다)
	 * @return 속성 이름 → 설명 (선언 순서). 없으면 빈 맵
	 */
	static Map<String, String> of(String source, Class<?> type) {
		Map<String, String> docs = new LinkedHashMap<>();
		String text = stripLineComments(source);
		Matcher declaration = Pattern.compile("\\b(record|class)\\s+" + Pattern.quote(type.getSimpleName()) + "\\b").matcher(text);
		if (!declaration.find()) {
			return docs;
		}
		if (declaration.group(1).equals("record")) {
			String doc = docBefore(text, declaration.start());
			if (doc != null) {
				Matcher param = PARAM.matcher(doc);
				while (param.find()) {
					docs.put(param.group(1), oneLine(param.group(2)));
				}
			}
			return docs;
		}
		int open = text.indexOf('{', declaration.end());
		int close = (open < 0) ? -1 : matching(text, open);
		if (close < 0) {
			return docs;
		}
		String body = text.substring(open + 1, close);
		Matcher field = FIELD.matcher(body);
		while (field.find()) {
			if (depth(body, field.start()) == 0) {
				docs.putIfAbsent(field.group(2), firstSentence(field.group(1)));
			}
		}
		return docs;
	}

	/** 짧은 이름: 설명의 괄호와 쉼표 앞까지 ({@code 언제 (yyyy-MM-dd HH:mm)} → {@code 언제}) */
	static String shortName(String description) {
		String text = description.strip();
		for (String stop : new String[] { "(", ",", ". " }) {
			int at = text.indexOf(stop);
			if (at > 0) {
				text = text.substring(0, at).strip();
			}
		}
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}

	/** 선언 바로 앞의 Javadoc 본문. 사이에 애너테이션과 수식어만 있어야 한다 */
	private static String docBefore(String text, int declaration) {
		int close = text.lastIndexOf("*/", declaration);
		int open = (close < 0) ? -1 : text.lastIndexOf("/**", close);
		if (open < 0) {
			return null;
		}
		String between = text.substring(close + 2, declaration);
		return between.matches("(?s)\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*(?:(?:public|protected|private|static|final|sealed|abstract)\\s+)*")
				? text.substring(open + 3, close) : null;
	}

	private static String oneLine(String doc) {
		return doc.replaceAll("(?m)^\\s*\\*\\s?", " ").replaceAll("\\s+", " ").strip();
	}

	private static String firstSentence(String doc) {
		String line = oneLine(doc);
		int tag = line.indexOf(" @");
		if (tag >= 0) {
			line = line.substring(0, tag).strip();
		}
		int end = line.indexOf(". ");
		return (end > 0) ? line.substring(0, end + 1) : line;
	}

	/** 한 줄 주석은 중괄호 세기를 흐린다: 지운다 (글자 수는 그대로) */
	private static String stripLineComments(String source) {
		return Pattern.compile("(?m)(?<![:\"])//[^\\n]*").matcher(source).replaceAll((m) -> " ".repeat(m.group().length()));
	}

	private static int matching(String text, int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return i;
			}
		}
		return -1;
	}

	private static int depth(String text, int end) {
		int depth = 0;
		for (int i = 0; i < end; i++) {
			char c = text.charAt(i);
			depth += (c == '{') ? 1 : (c == '}') ? -1 : 0;
		}
		return depth;
	}
}
