package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 템플릿 엔진이 알려 주는 줄 번호를 파일의 실제 줄로 바꾼다.
 *
 * <p>
 * Thymeleaf는 파서 주석({@code <!--/* … *}{@code /-->})을 읽기 전에 지우는데, 그 안의 줄바꿈도 함께 지운다. 그래서 여러 줄짜리 파서 주석 뒤의
 * 요소는 줄 번호가 주석 안의 줄바꿈 수만큼 작게 나온다. 프로토타입 전용 블록({@code <!--/*}{@code / … /*}{@code /-->})은 내용이 남으므로 줄이
 * 그대로다.
 */
final class SourceLines {

	/** 여러 줄짜리 파서 주석 하나: 시작하는 실제 줄과 안의 줄바꿈 수 */
	private record Comment(int line, int newlines) {
	}

	private final List<Comment> comments;

	private SourceLines(List<Comment> comments) {
		this.comments = comments;
	}

	static SourceLines of(String content) {
		List<Comment> comments = new ArrayList<>();
		int line = 1;
		int index = 0;
		while (index < content.length()) {
			if (content.startsWith("<!--/*", index) && !content.startsWith("<!--/*/", index)) {
				int end = content.indexOf("*/-->", index + 6);
				if (end < 0) {
					break;
				}
				int newlines = 0;
				for (int i = index; i < end; i++) {
					if (content.charAt(i) == '\n') {
						newlines++;
					}
				}
				if (newlines > 0) {
					comments.add(new Comment(line, newlines));
				}
				line += newlines;
				index = end + 5;
			}
			else {
				if (content.charAt(index) == '\n') {
					line++;
				}
				index++;
			}
		}
		return new SourceLines(List.copyOf(comments));
	}

	/**
	 * @param reported 템플릿 엔진이 알려 준 줄
	 * @return 파일의 실제 줄. 주석과 같은 줄에서 시작하는 요소는 주석 앞에 있다고 본다
	 */
	int actual(int reported) {
		int shift = 0;
		for (Comment comment : this.comments) {
			if (comment.line() - shift < reported) {
				shift += comment.newlines();
			}
		}
		return reported + shift;
	}
}
