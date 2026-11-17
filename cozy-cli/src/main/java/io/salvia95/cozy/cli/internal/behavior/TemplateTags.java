package io.salvia95.cozy.cli.internal.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 템플릿의 여는 태그와 그 속성의 위치. 글자를 그대로 두고 태그 하나만 고치기 위해 읽는다. 주석, {@code <!DOCTYPE>}, {@code script}·
 * {@code style}의 내용은 건너뛴다. 닫는 태그는 같은 이름의 가장 가까운 열린 요소를 닫는다.
 */
public final class TemplateTags {

	private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
			"source", "track", "wbr");

	/**
	 * 속성 하나.
	 *
	 * @param start 앞의 공백을 뺀 이름의 첫 글자
	 * @param end 값의 닫는 따옴표 다음(값이 없으면 이름 다음)
	 * @param value 따옴표 안의 글자. 값이 없으면 {@code null}
	 */
	public record Attr(String name, String value, int start, int end) {
	}

	/**
	 * 여는 태그 하나.
	 *
	 * @param start {@code <}의 위치
	 * @param close 태그를 닫는 {@code >}(혼자 닫히면 {@code />}의 {@code /})의 위치
	 * @param parent 감싸는 요소의 번호. 없으면 -1
	 * @param endOfElement 요소가 끝나는 곳(닫는 태그 다음). 혼자 닫히면 태그 끝
	 */
	public record Tag(int index, String name, int start, int close, int line, List<Attr> attrs, int parent, int[] endOfElement) {

		public Attr attr(String name) {
			for (Attr a : this.attrs) {
				if (a.name().equals(name)) {
					return a;
				}
			}
			return null;
		}

		/** 이 요소 안(this 포함)에 있는가 */
		public boolean contains(Tag other) {
			return other.start() >= this.start && other.start() < this.endOfElement[0];
		}
	}

	private TemplateTags() {
	}

	public static List<Tag> read(String text) {
		List<Tag> tags = new ArrayList<>();
		List<Integer> open = new ArrayList<>();
		int i = 0;
		int line = 1;
		int n = text.length();
		while (i < n) {
			char c = text.charAt(i);
			if (c == '\n') {
				line++;
				i++;
				continue;
			}
			if (c != '<') {
				i++;
				continue;
			}
			if (text.startsWith("<!--", i)) {
				int end = text.indexOf("-->", i + 4);
				end = (end < 0) ? n : end + 3;
				line += count(text, i, end);
				i = end;
				continue;
			}
			if (text.startsWith("<!", i) || text.startsWith("<?", i)) {
				int end = text.indexOf('>', i);
				end = (end < 0) ? n : end + 1;
				line += count(text, i, end);
				i = end;
				continue;
			}
			if (text.startsWith("</", i)) {
				int end = text.indexOf('>', i);
				end = (end < 0) ? n : end + 1;
				String name = text.substring(i + 2, Math.max(i + 2, end - 1)).strip().toLowerCase();
				for (int k = open.size() - 1; k >= 0; k--) {
					Tag tag = tags.get(open.get(k));
					if (tag.name().equals(name)) {
						tag.endOfElement()[0] = end;
						open.subList(k, open.size()).clear();
						break;
					}
				}
				line += count(text, i, end);
				i = end;
				continue;
			}
			int nameStart = i + 1;
			int j = nameStart;
			while (j < n && !Character.isWhitespace(text.charAt(j)) && text.charAt(j) != '>' && text.charAt(j) != '/') {
				j++;
			}
			if (j == nameStart) {
				i++;
				continue;
			}
			String name = text.substring(nameStart, j).toLowerCase();
			int tagLine = line;
			List<Attr> attrs = new ArrayList<>();
			boolean selfClosing = false;
			int close = n;
			while (j < n) {
				char ch = text.charAt(j);
				if (Character.isWhitespace(ch)) {
					j++;
					continue;
				}
				if (ch == '>') {
					close = j;
					break;
				}
				if (ch == '/' && j + 1 < n && text.charAt(j + 1) == '>') {
					selfClosing = true;
					close = j;
					break;
				}
				int attrStart = j;
				while (j < n && !Character.isWhitespace(text.charAt(j)) && text.charAt(j) != '=' && text.charAt(j) != '>'
						&& !(text.charAt(j) == '/' && j + 1 < n && text.charAt(j + 1) == '>')) {
					j++;
				}
				String attrName = text.substring(attrStart, j).toLowerCase();
				int k = j;
				while (k < n && Character.isWhitespace(text.charAt(k))) {
					k++;
				}
				String value = null;
				if (k < n && text.charAt(k) == '=') {
					k++;
					while (k < n && Character.isWhitespace(text.charAt(k))) {
						k++;
					}
					if (k < n && (text.charAt(k) == '"' || text.charAt(k) == '\'')) {
						char quote = text.charAt(k);
						int endQuote = text.indexOf(quote, k + 1);
						endQuote = (endQuote < 0) ? n - 1 : endQuote;
						value = text.substring(k + 1, endQuote);
						j = endQuote + 1;
					}
					else {
						int v = k;
						while (v < n && !Character.isWhitespace(text.charAt(v)) && text.charAt(v) != '>') {
							v++;
						}
						value = text.substring(k, v);
						j = v;
					}
				}
				attrs.add(new Attr(attrName, value, attrStart, j));
			}
			int parent = open.isEmpty() ? -1 : open.get(open.size() - 1);
			int tagEnd = selfClosing ? close + 2 : Math.min(n, close + 1);
			Tag tag = new Tag(tags.size(), name, i, close, tagLine, List.copyOf(attrs), parent, new int[] { tagEnd });
			tags.add(tag);
			line += count(text, i, tagEnd);
			i = tagEnd;
			if (selfClosing || VOID.contains(name)) {
				continue;
			}
			if (name.equals("script") || name.equals("style")) {
				int end = text.toLowerCase().indexOf("</" + name, i);
				end = (end < 0) ? n : end;
				line += count(text, i, end);
				i = end;
			}
			open.add(tag.index());
		}
		for (int k : open) {
			tags.get(k).endOfElement()[0] = n;
		}
		return tags;
	}

	private static int count(String text, int from, int to) {
		int lines = 0;
		for (int k = from; k < to && k < text.length(); k++) {
			if (text.charAt(k) == '\n') {
				lines++;
			}
		}
		return lines;
	}
}
