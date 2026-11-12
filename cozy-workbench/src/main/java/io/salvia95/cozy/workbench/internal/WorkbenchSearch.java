package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 워크벤치 검색. 화면, 컴포넌트, 생성 항목, 모듈을 한글 이름과 식별자로 찾는다.
 *
 * <p>
 * 검색어는 공백으로 나눈 낱말이고, 모든 낱말이 한 항목 안에 있어야 찾는다. 대소문자와 띄어쓰기, 식별자의 구분 글자({@code - _ / . :})는 보지
 * 않는다: {@code 메모목록}은 {@code 메모 목록}을, {@code memolist}는 {@code memo-list}를 찾는다. 이름과 식별자에서 찾은 항목이 설명에서만
 * 찾은 항목보다 앞에 온다.
 */
final class WorkbenchSearch {

	private WorkbenchSearch() {
	}

	/** 검색어의 최대 길이. 더 길면 자른다 */
	static final int MAX_QUERY = 100;

	/**
	 * 찾을 항목 하나.
	 *
	 * @param kind 묶음 ({@code 화면}, {@code 컴포넌트}, {@code 생성 항목}, {@code 모듈})
	 * @param label 사람이 읽는 이름 ({@code 메모 목록})
	 * @param id 식별자 ({@code memo-list})
	 * @param type 묶음 안의 종류 ({@code 패턴}). 없으면 빈 문자열
	 * @param context 이름 아래 한 줄 ({@code memo · memo/api/list}). 없으면 빈 문자열
	 * @param href 여는 주소
	 * @param more 이름과 식별자 말고 더 찾아볼 글 (설명, 경로)
	 */
	record Entry(String kind, String label, String id, String type, String context, String href, List<String> more) {
	}

	/** 찾은 항목과 그 점수 */
	record Hit(Entry entry, int score) {
	}

	/** 묶음 하나의 결과 */
	record Group(String kind, List<Hit> hits) {
	}

	/** 묶음의 순서 */
	static final List<String> KINDS = List.of("화면", "컴포넌트", "생성 항목", "모듈");

	/** 비교할 글: 소문자로, 띄어쓰기와 구분 글자를 뺀다 */
	static String normalize(String text) {
		StringBuilder out = new StringBuilder(text.length());
		text.toLowerCase(Locale.ROOT).codePoints().forEach((c) -> {
			if (!Character.isWhitespace(c) && "-_/.:·()".indexOf(c) < 0) {
				out.appendCodePoint(c);
			}
		});
		return out.toString();
	}

	/** 검색어의 낱말들. 비교할 글로 바꾼 뒤 빈 것은 뺀다 */
	static List<String> terms(String query) {
		String trimmed = (query == null) ? "" : query.strip();
		if (trimmed.length() > MAX_QUERY) {
			trimmed = trimmed.substring(0, MAX_QUERY);
		}
		List<String> terms = new ArrayList<>();
		for (String word : trimmed.split("\\s+")) {
			String term = normalize(word);
			if (!term.isEmpty() && !terms.contains(term)) {
				terms.add(term);
			}
		}
		return terms;
	}

	/**
	 * 낱말 하나의 점수. 이름이나 식별자와 같으면 6, 그것으로 시작하면 4, 그 안에 있으면 3, 설명에만 있으면 1, 없으면 0.
	 */
	private static int score(Entry entry, String term) {
		int best = 0;
		for (String field : List.of(entry.label(), entry.id())) {
			String text = normalize(field);
			if (text.equals(term)) {
				return 6;
			}
			if (text.startsWith(term)) {
				best = Math.max(best, 4);
			}
			else if (text.contains(term)) {
				best = Math.max(best, 3);
			}
		}
		if (best > 0) {
			return best;
		}
		for (String field : entry.more()) {
			if (normalize(field).contains(term)) {
				return 1;
			}
		}
		return 0;
	}

	/**
	 * @return 묶음별 결과 ({@link #KINDS} 순서, 찾은 것이 있는 묶음만). 묶음 안은 점수가 높은 것부터, 같으면 받은 순서
	 */
	static List<Group> search(String query, List<Entry> entries) {
		List<String> terms = terms(query);
		if (terms.isEmpty()) {
			return List.of();
		}
		Map<String, List<Hit>> byKind = new LinkedHashMap<>();
		KINDS.forEach((kind) -> byKind.put(kind, new ArrayList<>()));
		for (Entry entry : entries) {
			int total = 0;
			for (String term : terms) {
				int score = score(entry, term);
				if (score == 0) {
					total = 0;
					break;
				}
				total += score;
			}
			if (total > 0) {
				byKind.computeIfAbsent(entry.kind(), (k) -> new ArrayList<>()).add(new Hit(entry, total));
			}
		}
		List<Group> groups = new ArrayList<>();
		byKind.forEach((kind, hits) -> {
			if (!hits.isEmpty()) {
				hits.sort(Comparator.comparingInt(Hit::score).reversed());
				groups.add(new Group(kind, List.copyOf(hits)));
			}
		});
		return groups;
	}
}
