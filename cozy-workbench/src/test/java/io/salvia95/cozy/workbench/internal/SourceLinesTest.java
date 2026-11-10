package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Thymeleaf는 파서 주석({@code <!--/* … *}{@code /-->})을 읽기 전에 지우고, 그 안의 줄바꿈도 함께 지운다. 그래서 여러 줄짜리 주석 뒤의 요소는 줄 번호가
 * 주석의 줄 수만큼 당겨진다. 템플릿 원문으로 실제 줄을 되찾는다.
 */
class SourceLinesTest {

	static final String TEMPLATE = """
			<!DOCTYPE html>
			<!--/* 설명 첫 줄
			     둘째 줄
			     셋째 줄 */-->
			<html>
			<body>
			<!--/* 한 줄 주석 */-->
			<main>
			  <!--/*
			    또 여러 줄
			  */-->
			  <p>끝</p>
			</main>
			</body>
			</html>
			""";

	@Test
	void linesAfterAMultiLineParserCommentShiftBack() {
		SourceLines lines = SourceLines.of(TEMPLATE);
		assertThat(lines.actual(1)).as("주석 앞").isEqualTo(1);
		assertThat(lines.actual(3)).as("<html>: 엔진은 3, 실제는 5").isEqualTo(5);
		assertThat(lines.actual(4)).as("<body>").isEqualTo(6);
		assertThat(lines.actual(6)).as("<main>: 한 줄 주석은 줄을 당기지 않는다").isEqualTo(8);
		assertThat(lines.actual(8)).as("<p>: 두 번째 여러 줄 주석 뒤").isEqualTo(12);
		assertThat(lines.actual(9)).as("</main>").isEqualTo(13);
	}

	@Test
	void aTemplateWithoutParserCommentsKeepsItsLines() {
		SourceLines lines = SourceLines.of("<html>\n<!-- 보통 주석\n 둘째 줄 -->\n<body></body>\n</html>\n");
		assertThat(lines.actual(4)).isEqualTo(4);
		assertThat(SourceLines.of("").actual(7)).isEqualTo(7);
	}

	@Test
	void prototypeOnlyBlocksAreNotParserComments() {
		SourceLines lines = SourceLines.of("<div>\n<!--/*/\n<p th:text=\"${x}\">a</p>\n/*/-->\n<span></span>\n</div>\n");
		assertThat(lines.actual(5)).as("<!--/*/ … /*/-->는 내용을 남기므로 줄이 그대로다").isEqualTo(5);
	}
}
