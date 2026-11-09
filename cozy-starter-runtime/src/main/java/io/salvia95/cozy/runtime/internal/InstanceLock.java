package io.salvia95.cozy.runtime.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * {@code {dataDir}/app.lock}에 대한 단일 인스턴스 잠금.
 *
 * <p>OS 파일 잠금이라 프로세스가 비정상 종료해도 OS가 풀어 준다. 같은 JVM 안에서 이미 잡은 잠금은
 * {@link OverlappingFileLockException}으로 드러나므로 이것도 "다른 인스턴스가 실행 중"으로 본다.
 */
public final class InstanceLock implements AutoCloseable {

	private final Path file;

	private final FileChannel channel;

	private final FileLock lock;

	private InstanceLock(Path file, FileChannel channel, FileLock lock) {
		this.file = file;
		this.channel = channel;
		this.lock = lock;
	}

	/**
	 * 잠금을 시도한다.
	 * @return 잡았으면 잠금, 다른 인스턴스가 쥐고 있으면 빈 값
	 * @throws UncheckedIOException 파일을 열 수 없을 때 (권한 등). 실행 중으로 오판하지 않고 실패한다.
	 */
	public static Optional<InstanceLock> tryAcquire(Path file) {
		FileChannel channel;
		try {
			channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 잠금 파일을 열 수 없습니다: " + file, ex);
		}
		try {
			FileLock lock = channel.tryLock();
			if (lock == null) {
				closeQuietly(channel);
				return Optional.empty();
			}
			return Optional.of(new InstanceLock(file, channel, lock));
		}
		catch (OverlappingFileLockException ex) {
			closeQuietly(channel);
			return Optional.empty();
		}
		catch (IOException ex) {
			closeQuietly(channel);
			throw new UncheckedIOException("[cozy] 잠금을 시도할 수 없습니다: " + file, ex);
		}
	}

	public Path file() {
		return this.file;
	}

	public boolean isHeld() {
		return this.lock.isValid();
	}

	@Override
	public void close() {
		try {
			if (this.lock.isValid()) {
				this.lock.release();
			}
		}
		catch (IOException ex) {
			// 채널을 닫으면 잠금도 풀린다
		}
		finally {
			closeQuietly(this.channel);
		}
	}

	private static void closeQuietly(FileChannel channel) {
		try {
			channel.close();
		}
		catch (IOException ex) {
			// 무시
		}
	}
}
