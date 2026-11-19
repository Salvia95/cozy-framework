package io.salvia95.cozy.cli.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** 줄 단위 비교: 바뀐 줄과 앞뒤 세 줄의 덩어리 */
class LineDiffTest {

	static String numbered(int from, int to) {
		return IntStream.rangeClosed(from, to).mapToObj((i) -> "line " + i).collect(Collectors.joining("\n", "", "\n"));
	}

	static String show(List<LineDiff.Hunk> hunks) {
		return hunks.stream()
			.map((h) -> "@@ " + h.before() + " " + h.after() + "\n"
					+ h.lines().stream().map((l) -> l.kind() + l.text()).collect(Collectors.joining("\n")))
			.collect(Collectors.joining("\n"));
	}

	@Test
	void theSameTextHasNoHunks() {
		assertThat(LineDiff.of("a\nb\n", "a\nb\n")).isEmpty();
		assertThat(LineDiff.of("a\r\nb\r\n", "a\nb\n")).as("줄바꿈 글자는 보지 않는다").isEmpty();
	}

	@Test
	void aChangedLineCarriesThreeLinesAroundIt() {
		String before = numbered(1, 10);
		String after = before.replace("line 5\n", "line five\n");

		assertThat(show(LineDiff.of(before, after))).isEqualTo("""
				@@ 2 2
				 line 2
				 line 3
				 line 4
				-line 5
				+line five
				 line 6
				 line 7
				 line 8""");
	}

	@Test
	void farChangesAreSeparateHunksAndNearOnesOne() {
		String before = numbered(1, 30);
		String far = before.replace("line 2\n", "two\n").replace("line 25\n", "");
		List<LineDiff.Hunk> hunks = LineDiff.of(before, far);
		assertThat(hunks).hasSize(2);
		assertThat(hunks.get(1).before()).isEqualTo(22);
		assertThat(hunks.get(1).after()).isEqualTo(22);
		assertThat(hunks.get(1).lines()).filteredOn(LineDiff.Line::removed).extracting(LineDiff.Line::text).containsExactly("line 25");

		String near = before.replace("line 10\n", "ten\n").replace("line 15\n", "fifteen\n");
		assertThat(LineDiff.of(before, near)).hasSize(1);
	}

	@Test
	void insertedLinesKeepTheLinesAroundThemAndNumbersFollowEachText() {
		String before = "<div>\n  <p>a</p>\n</div>\n";
		String after = "<div x-data=\"memoBodyCount\">\n  <p>a</p>\n  <p x-text=\"label\"></p>\n</div>\n";

		List<LineDiff.Line> lines = LineDiff.of(before, after).getFirst().lines();

		assertThat(lines).extracting(LineDiff.Line::kind).containsExactly('-', '+', ' ', '+', ' ');
		assertThat(lines.get(3)).isEqualTo(new LineDiff.Line('+', 0, 3, "  <p x-text=\"label\"></p>"));
		assertThat(lines.get(4)).isEqualTo(new LineDiff.Line(' ', 3, 4, "</div>"));
	}

	@Test
	void aNewFileIsAllAddedLines() {
		assertThat(LineDiff.of(null, "a\nb\n").getFirst().lines()).extracting(LineDiff.Line::kind).containsExactly('+', '+');
	}
}
