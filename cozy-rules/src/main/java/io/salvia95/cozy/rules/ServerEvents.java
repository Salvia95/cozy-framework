package io.salvia95.cozy.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 서버가 응답 머리 {@code HX-Trigger}로 내는 화면 이벤트. 소스만 읽고 판단한다: 글자 {@code "HX-Trigger"}(또는
 * {@code "HX-Trigger-After-Swap"}, {@code "HX-Trigger-After-Settle"})를 요청 머리 읽기({@code getHeader(…)}) 밖에서 쓰는 Java 파일의
 * 글자 안에 있는 {@code 모듈:이름}이 그 파일이 내는 이벤트다.
 *
 * <pre>
 * response.setHeader("HX-Trigger", "memo:archived");
 * return ResponseEntity.ok().header("HX-Trigger", "{\"memo:archived\": {}}").body(…);
 * </pre>
 *
 * <p>이름을 이어 붙여 만들거나(상수와 {@code +}) 다른 파일의 상수로 넘기면 찾지 못한다. 그런 이름을 받는 동작은 C13이 실패로 닫는다. 이름은 글자 그대로 적는다.
 */
public final class ServerEvents {

	/** 화면 이벤트의 이름: {@code 모듈:소문자-하이픈} */
	public static final Pattern EVENT = Pattern
		.compile("(?<![\\w:-])([a-z][a-z0-9]*):([a-z][a-z0-9]*(?:-[a-z0-9]+)*)(?![\\w:-])");

	private static final Pattern HEADER = Pattern.compile("\"HX-Trigger(?:-After-(?:Swap|Settle))?\"");

	private static final Pattern READ = Pattern.compile("getHeaders?\\s*\\(\\s*$");

	private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

	private ServerEvents() {
	}

	/** 응답 머리 {@code HX-Trigger}를 쓰는 파일인가 */
	public static boolean sendsTriggers(String source) {
		Matcher header = HEADER.matcher(source);
		while (header.find()) {
			if (!READ.matcher(source.substring(Math.max(0, header.start() - 40), header.start())).find()) {
				return true;
			}
		}
		return false;
	}

	/** 파일 하나가 내는 이벤트 (나온 순서). {@code HX-Trigger}를 쓰지 않는 파일이면 비어 있다 */
	public static Set<String> events(String source) {
		Set<String> names = new LinkedHashSet<>();
		if (!sendsTriggers(source)) {
			return names;
		}
		Matcher literal = STRING.matcher(source);
		while (literal.find()) {
			Matcher event = EVENT.matcher(literal.group(1).replace("\\\"", "\""));
			while (event.find()) {
				names.add(event.group());
			}
		}
		return names;
	}

	/**
	 * 폴더 아래의 모든 Java 파일.
	 *
	 * @param javaRoot 앱의 {@code src/main/java}. 없으면 빈 결과
	 * @param displayPrefix 위치 앞에 붙일 경로 ({@code src/main/java/})
	 * @return 이벤트 → 내는 파일 (이벤트 이름 순서, 파일은 경로 순서)
	 */
	public static Map<String, List<String>> scan(Path javaRoot, String displayPrefix) {
		Map<String, List<String>> events = new TreeMap<>();
		if (!Files.isDirectory(javaRoot)) {
			return events;
		}
		try (Stream<Path> files = Files.walk(javaRoot)) {
			for (Path file : files.filter((p) -> Files.isRegularFile(p) && p.toString().endsWith(".java")).sorted().toList()) {
				String location = displayPrefix + javaRoot.relativize(file).toString().replace('\\', '/');
				for (String event : events(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))) {
					events.computeIfAbsent(event, (k) -> new ArrayList<>()).add(location);
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[C13] " + javaRoot + "를 읽지 못했습니다", ex);
		}
		return events;
	}
}
