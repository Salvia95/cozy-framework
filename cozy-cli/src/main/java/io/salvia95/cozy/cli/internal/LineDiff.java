package io.salvia95.cozy.cli.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 두 글의 줄 단위 비교. 같은 앞과 뒤를 걸러 낸 가운데를 가장 긴 공통 줄(LCS)로 맞춘다. 가운데가 너무 크면 가운데 전체를 바뀐 것으로 본다.
 * 바뀐 줄마다 앞뒤 {@value #CONTEXT}줄을 붙여 덩어리로 묶는다.
 */
public final class LineDiff {

	/** 바뀐 줄 앞뒤에 붙이는 줄 수 */
	public static final int CONTEXT = 3;

	/** 가운데를 줄마다 맞추는 표의 최대 칸 수 (넘으면 가운데 전체를 바꾼 것으로) */
	static final long LIMIT = 4_000_000L;

	/**
	 * 한 줄.
	 *
	 * @param kind {@code ' '} 같음, {@code '-'} 지운 줄, {@code '+'} 더한 줄
	 * @param before 바뀌기 전 글의 줄 번호. 더한 줄이면 0
	 * @param after 바뀐 뒤 글의 줄 번호. 지운 줄이면 0
	 */
	public record Line(char kind, int before, int after, String text) {

		public boolean added() {
			return this.kind == '+';
		}

		public boolean removed() {
			return this.kind == '-';
		}
	}

	/** 바뀐 줄과 그 앞뒤 줄의 덩어리 하나 */
	public record Hunk(int before, int after, List<Line> lines) {
	}

	private LineDiff() {
	}

	/** 줄로 나눈다. 끝의 줄바꿈 하나는 빈 줄로 세지 않는다 */
	static List<String> lines(String text) {
		if (text == null || text.isEmpty()) {
			return List.of();
		}
		List<String> lines = new ArrayList<>(List.of(text.split("\\R", -1)));
		if (lines.getLast().isEmpty()) {
			lines.removeLast();
		}
		return lines;
	}

	/**
	 * 바뀐 덩어리들.
	 *
	 * @param before 바뀌기 전 글 (없던 파일이면 {@code null}이나 빈 문자열)
	 * @param after 바뀐 뒤 글
	 * @return 같은 글이면 빈 목록
	 */
	public static List<Hunk> of(String before, String after) {
		return hunks(compare(lines(before), lines(after)));
	}

	/** 모든 줄 */
	static List<Line> compare(List<String> a, List<String> b) {
		int start = 0;
		while (start < a.size() && start < b.size() && a.get(start).equals(b.get(start))) {
			start++;
		}
		int endA = a.size();
		int endB = b.size();
		while (endA > start && endB > start && a.get(endA - 1).equals(b.get(endB - 1))) {
			endA--;
			endB--;
		}
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < start; i++) {
			lines.add(new Line(' ', i + 1, i + 1, a.get(i)));
		}
		middle(a, b, start, endA, endB, lines);
		for (int i = endA, j = endB; i < a.size(); i++, j++) {
			lines.add(new Line(' ', i + 1, j + 1, a.get(i)));
		}
		return lines;
	}

	private static void middle(List<String> a, List<String> b, int start, int endA, int endB, List<Line> lines) {
		int n = endA - start;
		int m = endB - start;
		if ((long) n * m > LIMIT || n == 0 || m == 0) {
			for (int i = start; i < endA; i++) {
				lines.add(new Line('-', i + 1, 0, a.get(i)));
			}
			for (int j = start; j < endB; j++) {
				lines.add(new Line('+', 0, j + 1, b.get(j)));
			}
			return;
		}
		// common[i][j]: a[start+i..], b[start+j..]의 가장 긴 공통 줄 수
		int[][] common = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				common[i][j] = a.get(start + i).equals(b.get(start + j)) ? common[i + 1][j + 1] + 1
						: Math.max(common[i + 1][j], common[i][j + 1]);
			}
		}
		int i = 0;
		int j = 0;
		while (i < n || j < m) {
			if (i < n && j < m && a.get(start + i).equals(b.get(start + j))) {
				lines.add(new Line(' ', start + i + 1, start + j + 1, a.get(start + i)));
				i++;
				j++;
			}
			else if (j < m && (i == n || common[i][j + 1] > common[i + 1][j])) {
				lines.add(new Line('+', 0, start + j + 1, b.get(start + j)));
				j++;
			}
			else {
				lines.add(new Line('-', start + i + 1, 0, a.get(start + i)));
				i++;
			}
		}
	}

	private static List<Hunk> hunks(List<Line> lines) {
		List<Hunk> hunks = new ArrayList<>();
		int i = 0;
		while (i < lines.size()) {
			if (lines.get(i).kind() == ' ') {
				i++;
				continue;
			}
			int from = Math.max(0, i - CONTEXT);
			int to = i;
			// 바뀐 줄 사이의 같은 줄이 앞뒤 문맥을 합친 것보다 짧으면 한 덩어리로
			while (to < lines.size()) {
				if (lines.get(to).kind() != ' ') {
					to++;
					continue;
				}
				int same = to;
				while (same < lines.size() && lines.get(same).kind() == ' ') {
					same++;
				}
				if (same < lines.size() && same - to <= 2 * CONTEXT) {
					to = same;
					continue;
				}
				to = Math.min(lines.size(), to + CONTEXT);
				break;
			}
			List<Line> part = List.copyOf(lines.subList(from, to));
			hunks.add(new Hunk(firstNumber(part, true), firstNumber(part, false), part));
			i = to;
		}
		return List.copyOf(hunks);
	}

	private static int firstNumber(List<Line> part, boolean before) {
		for (Line line : part) {
			int number = before ? line.before() : line.after();
			if (number > 0) {
				return number;
			}
		}
		return 0;
	}
}
