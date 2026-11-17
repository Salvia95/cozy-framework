package io.salvia95.cozy.cli.internal.channel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 채널에 두는 릴리스 판. {@code 주.부.수}의 숫자 세 개이고, SNAPSHOT 같은 꼬리는 받지 않는다(릴리스한 판만 채널에 둔다).
 *
 * @param major 주 버전
 * @param minor 부 버전
 * @param patch 수 버전
 */
public record Version(int major, int minor, int patch) implements Comparable<Version> {

	private static final Pattern FORM = Pattern.compile("(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})");

	/**
	 * @param where 메시지에 보일 출처
	 */
	public static Version parse(String text, String where) {
		Matcher m = FORM.matcher(text == null ? "" : text);
		if (!m.matches()) {
			throw new CliException(where + "의 판 '" + text + "'은 릴리스 판(숫자 세 개, 예: 2.1.0)이 아닙니다. 채널에는 릴리스한 판만 둡니다.");
		}
		return new Version(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
	}

	/** 주 버전이 바뀐 판(x.0.0)이다. 앱을 올릴 때 손이 간다 */
	public boolean breaking() {
		return this.minor == 0 && this.patch == 0;
	}

	@Override
	public int compareTo(Version other) {
		if (this.major != other.major) {
			return Integer.compare(this.major, other.major);
		}
		if (this.minor != other.minor) {
			return Integer.compare(this.minor, other.minor);
		}
		return Integer.compare(this.patch, other.patch);
	}

	@Override
	public String toString() {
		return this.major + "." + this.minor + "." + this.patch;
	}
}
