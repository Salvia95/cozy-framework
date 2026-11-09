package io.salvia95.cozy.runtime.internal.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 배포 zip 만들기와 풀기.
 *
 * <p>Linux app-image의 런처와 런타임은 실행 권한이 있어야 하므로 POSIX 권한을 zip에 담고 풀 때 되살린다.
 * Windows에서는 권한을 무시한다. 파일의 수정 시각도 보존한다(AOT 캐시가 jar의 시각을 대조한다). 풀 때는 모든 항목이 하나의 최상위 폴더 아래에 있어야 하고,
 * 그 폴더 밖으로 나가는 경로(zip slip)가 하나라도 있으면 아무것도 풀지 않고 실패한다.
 */
public final class ZipArchives {

	private ZipArchives() {
	}

	/** {@code source} 폴더를 {@code {source 이름}/...} 구조의 zip으로 만든다. */
	public static void create(Path source, Path zip) throws IOException {
		Files.deleteIfExists(zip);
		Files.createDirectories(zip.toAbsolutePath().getParent());
		boolean posix = supportsPosix(source);
		Map<String, Object> env = Map.of("create", "true", "enablePosixFileAttributes", Boolean.toString(posix));
		try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toAbsolutePath().toUri()), env);
				Stream<Path> walk = Files.walk(source)) {
			Path root = fs.getPath(source.getFileName().toString());
			for (Path file : (Iterable<Path>) walk.sorted()::iterator) {
				Path target = root.resolve(source.relativize(file).toString().replace('\\', '/'));
				if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
					Files.createDirectories(target);
				}
				else {
					Files.copy(file, target);
					// AOT 캐시는 jar의 수정 시각을 대조하므로 시각을 그대로 담는다
					Files.setLastModifiedTime(target, Files.getLastModifiedTime(file));
				}
				if (posix) {
					Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS));
				}
			}
		}
	}

	/**
	 * zip의 최상위 폴더 하나의 내용을 {@code target} 폴더로 푼다. {@code target}은 없어야 한다.
	 * @return 최상위 폴더 이름
	 */
	public static String extractSingleRoot(Path zip, Path target) throws IOException {
		if (Files.exists(target)) {
			throw new IOException("풀 위치가 이미 있습니다: " + target);
		}
		boolean posix = supportsPosix(target.toAbsolutePath().getParent());
		Map<String, Object> env = Map.of("enablePosixFileAttributes", Boolean.toString(posix));
		try (FileSystem fs = FileSystems.newFileSystem(zip, env)) {
			Path zipRoot = fs.getRootDirectories().iterator().next();
			List<Path> tops;
			try (Stream<Path> list = Files.list(zipRoot)) {
				tops = list.toList();
			}
			if (tops.size() != 1 || !Files.isDirectory(tops.get(0))) {
				throw new IOException("zip에는 최상위 폴더가 하나만 있어야 합니다 (" + tops.size() + "개 항목): " + zip);
			}
			Path top = tops.get(0);
			String topName = top.getFileName().toString().replace("/", "");
			Path base = target.toAbsolutePath().normalize();
			List<Path[]> plan = new ArrayList<>();
			try (Stream<Path> walk = Files.walk(top)) {
				for (Path entry : (Iterable<Path>) walk::iterator) {
					String relative = top.relativize(entry).toString();
					Path out = base.resolve(relative).normalize();
					if (!out.startsWith(base)) {
						throw new IOException("zip 항목이 풀 위치 밖을 가리킵니다: " + entry);
					}
					plan.add(new Path[] { entry, out });
				}
			}
			try {
				for (Path[] step : plan) {
					if (Files.isDirectory(step[0])) {
						Files.createDirectories(step[1]);
					}
					else {
						Files.createDirectories(step[1].getParent());
						Files.copy(step[0], step[1]);
						Files.setLastModifiedTime(step[1], Files.getLastModifiedTime(step[0]));
					}
					if (posix) {
						restorePermissions(step[0], step[1]);
					}
				}
			}
			catch (IOException | RuntimeException ex) {
				deleteRecursively(base);
				throw ex;
			}
			return topName;
		}
	}

	/** 파일을 복사하면서 SHA-256을 계산한다. */
	public static String copyWithSha256(InputStream in, OutputStream out) throws IOException {
		MessageDigest digest = sha256();
		try (DigestInputStream digesting = new DigestInputStream(in, digest)) {
			digesting.transferTo(out);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	public static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return copyWithSha256(in, OutputStream.nullOutputStream());
		}
	}

	public static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(path)) {
			for (Path p : (Iterable<Path>) walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())::iterator) {
				Files.deleteIfExists(p);
			}
		}
	}

	private static void restorePermissions(Path entry, Path out) throws IOException {
		PosixFileAttributeView view = Files.getFileAttributeView(entry, PosixFileAttributeView.class);
		if (view == null) {
			return;
		}
		PosixFileAttributes attributes = view.readAttributes();
		Set<PosixFilePermission> permissions = attributes.permissions();
		if (permissions != null && !permissions.isEmpty()) {
			Files.setPosixFilePermissions(out, permissions);
		}
	}

	private static boolean supportsPosix(Path path) {
		return path != null && path.getFileSystem().supportedFileAttributeViews().contains("posix");
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
