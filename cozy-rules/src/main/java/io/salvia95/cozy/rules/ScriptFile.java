package io.salvia95.cozy.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Alpine 컴포넌트를 등록하는 JS 파일 하나 (R16). 규칙은 {@code Alpine.data('이름', …)}의 글자 이름만 읽는다.
 *
 * @param path 정적 자원 루트 기준 상대 경로, 구분자는 {@code /}. 앱은 {@code static/} 기준({@code memo/memo.js}),
 * 프레임워크는 {@code META-INF/resources/} 기준({@code cozy/cozy.js})
 * @param origin 앱 또는 프레임워크
 * @param location 위반 메시지에 보일 위치 ({@code static/memo/memo.js})
 * @param content 파일 내용
 */
public record ScriptFile(String path, Origin origin, String location, String content) {

	public ScriptFile {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(content, "content");
		if (path.startsWith("/") || path.contains("\\")) {
			throw new IllegalArgumentException("path는 정적 자원 루트 기준 상대 경로이고 구분자는 '/'입니다: " + path);
		}
	}

	/**
	 * 디렉터리 아래의 모든 {@code .js}를 읽는다.
	 * @param root 정적 자원 루트 ({@code src/main/resources/static}). 없으면 빈 목록
	 * @param origin 앱 또는 프레임워크
	 * @param locationPrefix 메시지에 보일 위치 앞부분 ({@code static/})
	 */
	public static List<ScriptFile> fromDirectory(Path root, Origin origin, String locationPrefix) {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter((p) -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".js"))
				.sorted()
				.map((p) -> {
					String relative = root.relativize(p).toString().replace('\\', '/');
					return new ScriptFile(relative, origin, locationPrefix + relative, read(p));
				})
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
