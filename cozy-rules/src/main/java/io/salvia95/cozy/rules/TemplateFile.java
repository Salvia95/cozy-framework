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
 * 검사할 템플릿 파일 하나.
 *
 * @param path {@code templates/} 기준 상대 경로, 구분자는 {@code /} ({@code memo/api/list.html})
 * @param origin 앱 또는 프레임워크
 * @param location 위반 메시지에 보일 위치 ({@code templates/memo/api/list.html})
 * @param content 파일 내용
 */
public record TemplateFile(String path, Origin origin, String location, String content) {

	public TemplateFile {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(content, "content");
		if (path.startsWith("/") || path.contains("\\")) {
			throw new IllegalArgumentException("path는 templates/ 기준 상대 경로이고 구분자는 '/'입니다: " + path);
		}
	}

	/** 템플릿 이름 (확장자를 뺀 경로, {@code memo/api/list}) */
	public String templateName() {
		return this.path.endsWith(".html") ? this.path.substring(0, this.path.length() - 5) : this.path;
	}

	/**
	 * 디렉터리 아래의 모든 {@code .html}을 읽는다.
	 * @param root 템플릿 루트 ({@code src/main/resources/templates}). 없으면 빈 목록
	 * @param origin 앱 또는 프레임워크
	 * @param locationPrefix 메시지에 보일 위치 앞부분 ({@code templates/})
	 */
	public static List<TemplateFile> fromDirectory(Path root, Origin origin, String locationPrefix) {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter((p) -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".html"))
				.sorted()
				.map((p) -> {
					String relative = root.relativize(p).toString().replace('\\', '/');
					return new TemplateFile(relative, origin, locationPrefix + relative, read(p));
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
