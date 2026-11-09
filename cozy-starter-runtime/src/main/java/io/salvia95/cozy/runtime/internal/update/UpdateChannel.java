package io.salvia95.cozy.runtime.internal.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 업데이트 채널: 매니페스트와 패키지를 읽는 곳.
 *
 * <p>{@code file:}(공유 폴더 UNC 경로 포함)과 {@code http(s):}만 지원한다. 설정값이 경로({@code \\server\share\…},
 * {@code C:\…}, {@code /srv/…})이면 {@code file:} 주소로 바꾼다. 다른 형식은 기동할 때 실패한다.
 * 패키지는 매니페스트와 같은 폴더에서 읽는다.
 */
public final class UpdateChannel {

	private static final Pattern WINDOWS_DRIVE = Pattern.compile("[A-Za-z]:[\\\\/].*");

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration MANIFEST_TIMEOUT = Duration.ofSeconds(30);

	private static final Duration PACKAGE_TIMEOUT = Duration.ofMinutes(30);

	private final URI manifest;

	private UpdateChannel(URI manifest) {
		this.manifest = manifest;
	}

	/** {@code cozy.update.manifest-url} 값으로 채널을 만든다. */
	public static UpdateChannel of(String manifestUrl) {
		String value = manifestUrl.strip();
		URI uri;
		if (value.startsWith("\\\\") || WINDOWS_DRIVE.matcher(value).matches() || value.startsWith("/")) {
			uri = Path.of(value).toUri();
		}
		else {
			try {
				uri = new URI(value);
			}
			catch (URISyntaxException ex) {
				throw invalid(manifestUrl, ex.getMessage());
			}
		}
		String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
		if (!scheme.equals("file") && !scheme.equals("http") && !scheme.equals("https")) {
			throw invalid(manifestUrl, "file:, http:, https:만 지원합니다");
		}
		return new UpdateChannel(uri);
	}

	public URI manifestUri() {
		return this.manifest;
	}

	public byte[] readManifest() throws IOException {
		try (InputStream in = open(this.manifest, MANIFEST_TIMEOUT)) {
			return in.readNBytes(1024 * 1024);
		}
	}

	/** 매니페스트와 같은 폴더의 패키지를 연다. */
	public InputStream openPackage(String packageName) throws IOException {
		return open(this.manifest.resolve(packageName), PACKAGE_TIMEOUT);
	}

	public URI packageUri(String packageName) {
		return this.manifest.resolve(packageName);
	}

	private static InputStream open(URI uri, Duration timeout) throws IOException {
		if ("file".equalsIgnoreCase(uri.getScheme())) {
			return Files.newInputStream(Path.of(uri));
		}
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IOException("HTTP " + response.statusCode() + ": " + uri);
			}
			return response.body();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("중단됨: " + uri, ex);
		}
	}

	private static IllegalStateException invalid(String value, String reason) {
		return new IllegalStateException("[cozy] cozy.update.manifest-url '" + value + "'를 쓸 수 없습니다: " + reason
				+ ". 예: file://server/share/my-tool/my-tool-update.json, https://intra.example/my-tool/my-tool-update.json");
	}

	@Override
	public String toString() {
		return this.manifest.toString();
	}
}
