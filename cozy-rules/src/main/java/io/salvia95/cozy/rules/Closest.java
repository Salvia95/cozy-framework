package io.salvia95.cozy.rules;

import java.util.Collection;
import java.util.Optional;

/**
 * 없는 이름을 적었을 때 가까운 이름 하나. 글자 하나를 넣고, 빼고, 바꾸고, 이웃한 두 글자를 맞바꾸는 것을 한 번으로 센다. 이름 길이의 3분의 1(적어도 1)
 * 안에서 가장 가까운 것이고, 같으면 앞의 것이다. 대소문자만 다르면 가장 가깝다.
 */
public final class Closest {

	private Closest() {
	}

	public static Optional<String> to(String word, Collection<String> names) {
		if (word == null || word.isEmpty()) {
			return Optional.empty();
		}
		int limit = Math.max(1, word.length() / 3);
		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (String name : names) {
			if (name.equals(word)) {
				continue;
			}
			int distance = name.equalsIgnoreCase(word) ? 0 : distance(word, name);
			if (distance <= limit && distance < bestDistance) {
				best = name;
				bestDistance = distance;
			}
		}
		return Optional.ofNullable(best);
	}

	/** 오류 문장 뒤에 붙일 제안 ({@code " label인가요?"}). 가까운 이름이 없으면 빈 글자 */
	public static String hint(String word, Collection<String> names) {
		return to(word, names).map((name) -> " " + name + "인가요?").orElse("");
	}

	/** 맞바꾸기를 하나로 세는 편집 거리 */
	static int distance(String a, String b) {
		int[][] d = new int[a.length() + 1][b.length() + 1];
		for (int i = 0; i <= a.length(); i++) {
			d[i][0] = i;
		}
		for (int j = 0; j <= b.length(); j++) {
			d[0][j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			for (int j = 1; j <= b.length(); j++) {
				int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				}
			}
		}
		return d[a.length()][b.length()];
	}
}
