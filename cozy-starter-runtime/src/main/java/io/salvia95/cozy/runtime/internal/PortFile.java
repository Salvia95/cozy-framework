package io.salvia95.cozy.runtime.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * {@code {dataDir}/app.port}: 실행 중인 인스턴스의 포트 번호 한 줄 (PRD §9.2).
 *
 * <p>두 번째 실행이 읽는 도중에 반쯤 쓴 파일을 보지 않도록 임시 파일에 쓰고 원자적으로 옮긴다.
 */
public final class PortFile {

	private PortFile() {
	}

	public static void write(Path file, int port) {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.writeString(tmp, Integer.toString(port), StandardCharsets.US_ASCII);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 포트 파일을 쓸 수 없습니다: " + file, ex);
		}
	}

	/** 포트를 읽는다. 파일이 없거나 내용이 포트가 아니면 빈 값. */
	public static Optional<Integer> read(Path file) {
		try {
			String text = Files.readString(file, StandardCharsets.US_ASCII).trim();
			int port = Integer.parseInt(text);
			return (port > 0 && port <= 65535) ? Optional.of(port) : Optional.empty();
		}
		catch (IOException | NumberFormatException ex) {
			return Optional.empty();
		}
	}

	public static void delete(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			// 다음 인스턴스가 덮어쓴다
		}
	}
}
