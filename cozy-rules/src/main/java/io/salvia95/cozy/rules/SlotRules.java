package io.salvia95.cozy.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 검사 C9: 채우지 않은 빈칸 (ADR-0032 §3). cozy 명령이 만든 파일에서 에이전트가 채울 자리에는 {@value #MARKER} 표시가 있다. 표시가
 * 남아 있으면 오류다. 다 채웠으면 표시를 지운다.
 *
 * <pre>
 * // cozy:slot listener-body — MemoCreated를 받았을 때 할 일을 쓰세요
 * throw new UnsupportedOperationException("cozy:slot listener-body");
 * &lt;!--/* cozy:slot fragment-body — 파라미터로 받은 값만으로 그리세요 *&#47;--&gt;
 * -- cozy:slot migration-sql — DDL을 쓰세요
 * </pre>
 *
 * <p>표시는 {@code cozy:slot {id} — {무엇을}}이다. 한 파일에서 같은 id는 처음 나온 곳 한 번만 보고한다(Java의 주석과 {@code throw}가 같은
 * 빈칸이다). id가 없는 표시는 나올 때마다 보고한다. 파일 종류는 가리지 않는다(실패로 닫는다).
 */
public final class SlotRules {

	public static final String MARKER = "cozy:slot";

	/** 표시 뒤의 id와 설명. 설명은 대시(—, -) 뒤부터 주석 끝(*&#47;, --&gt;, ", ;)까지 */
	private static final Pattern SLOT = Pattern.compile(Pattern.quote(MARKER) + "(?:\\s+([A-Za-z0-9_.-]+))?"
			+ "(?:\\s+[—-]+\\s*(.*?))?\\s*(?:\\*/|-->|\"|;|$)");

	private SlotRules() {
	}

	/**
	 * 폴더 아래의 모든 파일. 폴더가 없으면 빈칸도 없다.
	 *
	 * @param root 앱의 {@code src/main}
	 * @param displayPrefix 위치 앞에 붙일 경로 ({@code src/main/})
	 * @return 경로 순서, 파일 안에서는 줄 순서
	 */
	public static List<Violation> check(Path root, String displayPrefix) {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		List<Violation> slots = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				String location = displayPrefix + root.relativize(file).toString().replace('\\', '/');
				slots.addAll(scan(location, new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[C9] " + root + "를 읽지 못했습니다", ex);
		}
		return slots;
	}

	/** 파일 하나의 내용 */
	public static List<Violation> scan(String location, String content) {
		List<Violation> slots = new ArrayList<>();
		if (!content.contains(MARKER)) {
			return slots;
		}
		Set<String> seen = new HashSet<>();
		String[] lines = content.split("\\R", -1);
		for (int i = 0; i < lines.length; i++) {
			int from = lines[i].indexOf(MARKER);
			while (from >= 0) {
				Matcher matcher = SLOT.matcher(lines[i]);
				String id = null;
				String what = "";
				if (matcher.find(from) && matcher.start() == from) {
					id = matcher.group(1);
					what = (matcher.group(2) != null) ? matcher.group(2).strip() : "";
				}
				if (id == null || seen.add(id)) {
					slots.add(new Violation(location, i + 1, "C9", Severity.ERROR,
							(id != null) ? MARKER + " " + id : MARKER, message(what)));
				}
				from = lines[i].indexOf(MARKER, from + MARKER.length());
			}
		}
		return slots;
	}

	private static String message(String what) {
		String text = what.isEmpty() ? "빈칸이 남아 있습니다" : what;
		return text + (text.endsWith(".") ? "" : ".") + " 다 채우면 이 " + MARKER + " 표시를 지웁니다.";
	}
}
