package io.salvia95.cozy.runtime.internal.update;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 앱 버전 비교 (PRD §9.4). 형식은 {@code 1.2.3} 또는 {@code 1.2.3-qualifier}다.
 *
 * <p>숫자 부분을 차례로 비교하고, 빠진 자리는 0으로 본다({@code 1.1 = 1.1.0}).
 * 숫자 부분이 같으면 qualifier가 없는 쪽이 높다({@code 1.1.0-SNAPSHOT < 1.1.0}).
 */
public final class Versions {

	public static final Pattern FORMAT = Pattern.compile("(\\d+(?:\\.\\d+){0,3})(?:-([0-9A-Za-z.-]+))?");

	public static final Comparator<String> ORDER = Versions::compare;

	private Versions() {
	}

	public static boolean isValid(String version) {
		return version != null && FORMAT.matcher(version).matches();
	}

	/** jpackage {@code --app-version}에 쓰는 숫자 부분 ({@code 1.1.0-SNAPSHOT → 1.1.0}). */
	public static String numericPart(String version) {
		return parse(version).group(1);
	}

	public static int compare(String a, String b) {
		Matcher ma = parse(a);
		Matcher mb = parse(b);
		String[] na = ma.group(1).split("\\.");
		String[] nb = mb.group(1).split("\\.");
		for (int i = 0; i < Math.max(na.length, nb.length); i++) {
			long x = (i < na.length) ? Long.parseLong(na[i]) : 0;
			long y = (i < nb.length) ? Long.parseLong(nb[i]) : 0;
			if (x != y) {
				return Long.compare(x, y);
			}
		}
		String qa = ma.group(2);
		String qb = mb.group(2);
		if (qa == null || qb == null) {
			return (qa == null) ? ((qb == null) ? 0 : 1) : -1;
		}
		return qa.compareTo(qb);
	}

	private static Matcher parse(String version) {
		Matcher matcher = FORMAT.matcher((version != null) ? version : "");
		if (!matcher.matches()) {
			throw new IllegalArgumentException("버전 형식이 아닙니다: '" + version + "' (예: 1.2.0)");
		}
		return matcher;
	}
}
