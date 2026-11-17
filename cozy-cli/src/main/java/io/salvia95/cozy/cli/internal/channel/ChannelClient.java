package io.salvia95.cozy.cli.internal.channel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 채널에서 읽는다. 파일 서버(UNC, 연결한 드라이브, 마운트, {@code file:})는 파일로, 웹 서버({@code http}, {@code https})는 HTTP로 읽는다.
 *
 * <ul>
 * <li>토큰은 채널 주소와 같은 서버(scheme, 호스트, 포트)에만 보낸다. 다른 서버로 넘겨주면(예: GitHub의 자산 저장소) 토큰 없이 따른다.</li>
 * <li>{@code https}에서 {@code http}로 넘겨주면 따르지 않는다.</li>
 * <li>JDK의 인증서에 더해 받은 인증서 저장소(Windows는 {@code Windows-ROOT})를 믿는다. 사내 CA로 서명한 서버도 따로 설정하지 않는다.</li>
 * <li>프록시는 JDK의 기본 선택기를 따른다. cozy 명령은 시스템 프록시 설정({@code java.net.useSystemProxies})을 켜고 시작한다.</li>
 * </ul>
 */
public final class ChannelClient {

	/** 색인의 최대 크기 */
	static final long MAX_INDEX = 4L * 1024 * 1024;

	static final int MAX_REDIRECTS = 5;

	private final SSLContext ssl;

	private final Duration connectTimeout;

	private final Duration requestTimeout;

	public ChannelClient(SSLContext ssl, Duration connectTimeout, Duration requestTimeout) {
		this.ssl = ssl;
		this.connectTimeout = connectTimeout;
		this.requestTimeout = requestTimeout;
	}

	/** 이 PC가 믿는 인증서로 읽는다. Windows는 Windows 인증서 저장소를 더한다 */
	public static ChannelClient forThisPc(boolean windows, Duration connectTimeout, Duration requestTimeout) {
		List<KeyStore> extra = new ArrayList<>();
		if (windows) {
			try {
				KeyStore store = KeyStore.getInstance("Windows-ROOT");
				store.load(null, null);
				extra.add(store);
			}
			catch (GeneralSecurityException | IOException ex) {
				// Windows 인증서 저장소를 읽지 못하면 JDK의 인증서만 믿는다. 사내 CA 서버는 인증서 오류로 알린다
			}
		}
		return new ChannelClient(trust(extra), connectTimeout, requestTimeout);
	}

	public Duration requestTimeout() {
		return this.requestTimeout;
	}

	/** JDK의 인증서와 {@code extra}의 인증서를 믿는 TLS 문맥 */
	public static SSLContext trust(List<KeyStore> extra) {
		try {
			List<X509ExtendedTrustManager> managers = new ArrayList<>();
			managers.add(manager(null));
			for (KeyStore store : extra) {
				managers.add(manager(store));
			}
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new AnyOf(managers) }, null);
			return context;
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static X509ExtendedTrustManager manager(KeyStore store) throws GeneralSecurityException {
		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(store);
		return Arrays.stream(factory.getTrustManagers())
			.filter(X509ExtendedTrustManager.class::isInstance)
			.map(X509ExtendedTrustManager.class::cast)
			.findFirst()
			.orElseThrow(() -> new GeneralSecurityException("X509 인증서 관리자가 없습니다"));
	}

	/** 채널 색인을 읽는다 */
	public ChannelIndex index(ChannelSettings settings) {
		byte[] bytes = read(settings.address(), settings, MAX_INDEX);
		return ChannelIndex.parse(new String(bytes, StandardCharsets.UTF_8), settings.address());
	}

	/** 색인 주소 기준의 상대 경로를 주소로 바꾼다 */
	public static String resolve(String address, String relative) {
		if (web(address)) {
			return URI.create(address).resolve(relative).toString();
		}
		if (address.startsWith("file:")) {
			return Path.of(URI.create(address)).resolveSibling(relative).normalize().toUri().toString();
		}
		return Path.of(address).resolveSibling(relative.replace('/', java.io.File.separatorChar)).normalize().toString();
	}

	private static boolean web(String address) {
		String lower = address.toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	private byte[] read(String location, ChannelSettings settings, long max) {
		if (web(location)) {
			return get(location, settings, max);
		}
		Path file;
		try {
			file = location.startsWith("file:") ? Path.of(URI.create(location)) : Path.of(location);
		}
		catch (IllegalArgumentException ex) {
			throw new CliException("채널 주소 " + location + "를 파일 경로로 읽을 수 없습니다: " + ex.getMessage());
		}
		try {
			if (Files.size(file) > max) {
				throw new CliException(location + "이 너무 큽니다(" + max + "바이트 넘음). 채널 색인이 맞는지 확인합니다.");
			}
			return Files.readAllBytes(file);
		}
		catch (NoSuchFileException ex) {
			throw new CliException("채널 색인 " + location + "이 없습니다. 채널 주소가 맞는지(cozy channel show), 공유 폴더에 연결했는지 "
					+ "확인합니다.");
		}
		catch (IOException ex) {
			throw new CliException("채널 색인 " + location + "을 읽지 못했습니다: " + ex.getMessage());
		}
	}

	private byte[] get(String location, ChannelSettings settings, long max) {
		URI origin = URI.create(settings.address());
		URI uri = URI.create(location);
		HttpClient client = HttpClient.newBuilder()
			.sslContext(this.ssl)
			.connectTimeout(this.connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(ProxySelector.getDefault())
			.build();
		try (client) {
			for (int redirects = 0;; redirects++) {
				HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(this.requestTimeout).GET();
				if (settings.token() != null && sameServer(origin, uri)) {
					try {
						request.header(settings.headerName(), settings.headerValue());
					}
					catch (IllegalArgumentException ex) {
						throw new CliException("인증 머리 이름 '" + settings.headerName() + "'을 쓸 수 없습니다. cozy channel set "
								+ "<주소> --header <이름>으로 다시 정합니다.");
					}
				}
				HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
				int status = response.statusCode();
				if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
					response.body().close();
					if (redirects >= MAX_REDIRECTS) {
						throw new CliException(location + "이 넘겨주기를 " + MAX_REDIRECTS + "번 넘게 했습니다. 채널 주소를 확인합니다.");
					}
					String next = response.headers().firstValue("Location").orElse(null);
					if (next == null) {
						throw new CliException(uri + "이 넘겨주는 곳(Location)을 알리지 않았습니다.");
					}
					URI target = uri.resolve(next);
					if ("https".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) {
						throw new CliException(uri + "이 https가 아닌 곳(" + target + ")으로 넘겨줍니다. 따르지 않습니다.");
					}
					uri = target;
					continue;
				}
				try (InputStream body = response.body()) {
					if (status != 200) {
						throw new CliException(failure(status, location, settings));
					}
					return limited(body, max, location);
				}
			}
		}
		catch (HttpConnectTimeoutException ex) {
			throw new CliException(uri.getHost() + "에 " + this.connectTimeout.toSeconds() + "초 안에 연결하지 못했습니다(" + location
					+ "). 이 PC에서 채널 서버에 닿는지, 프록시가 필요한지 확인합니다.");
		}
		catch (HttpTimeoutException ex) {
			throw new CliException(location + "이 " + this.requestTimeout.toSeconds() + "초 안에 응답하지 않았습니다.");
		}
		catch (SSLHandshakeException ex) {
			throw new CliException(uri.getHost() + "의 인증서를 믿을 수 없습니다: " + ex.getMessage() + ". Windows는 Windows 인증서 "
					+ "저장소의 인증서를, 그 밖은 JDK의 인증서(cacerts)를 믿습니다. 사내 CA라면 그 인증서를 저장소에 넣습니다.");
		}
		catch (ConnectException | UnknownHostException ex) {
			throw new CliException(uri.getHost() + "에 연결하지 못했습니다(" + location + "). 채널 주소(cozy channel show)와 이 PC의 "
					+ "네트워크를 확인합니다.");
		}
		catch (IOException ex) {
			throw new CliException(location + "을 받지 못했습니다: " + ex.getMessage());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new CliException(location + "을 받는 중에 멈췄습니다.");
		}
	}

	private static String failure(int status, String location, ChannelSettings settings) {
		return switch (status) {
			case 401, 403 -> location + "이 요청을 거부했습니다(HTTP " + status + "). "
					+ ((settings.token() == null) ? "인증이 필요하면 토큰을 환경 변수 " + ChannelCommands.TOKEN_ENV + "에 둡니다"
							: "토큰(" + ChannelCommands.TOKEN_ENV + "나 설정 파일의 " + UserConfig.TOKEN + ")과 머리 이름("
									+ settings.headerName() + ")이 맞는지 확인합니다")
					+ ". GitLab의 개인 토큰은 cozy channel set <주소> --header PRIVATE-TOKEN입니다.";
			case 404 -> location + "이 없습니다(HTTP 404). 채널 주소가 맞는지 cozy channel show로 확인합니다.";
			default -> location + "이 HTTP " + status + "로 답했습니다. 잠시 뒤에 다시 하거나 채널 서버를 확인합니다.";
		};
	}

	private static boolean sameServer(URI a, URI b) {
		return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost())
				&& port(a) == port(b);
	}

	private static int port(URI uri) {
		if (uri.getPort() >= 0) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static byte[] limited(InputStream in, long max, String location) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1 << 16];
		long total = 0;
		for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
			total += n;
			if (total > max) {
				throw new CliException(location + "이 너무 큽니다(" + max + "바이트 넘음). 채널 색인이 맞는지 확인합니다.");
			}
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}

	/** 인증서 관리자 가운데 하나가 믿으면 믿는다. 이름 확인은 각 관리자가 한다 */
	private static final class AnyOf extends X509ExtendedTrustManager {

		private final List<X509ExtendedTrustManager> managers;

		AnyOf(List<X509ExtendedTrustManager> managers) {
			this.managers = List.copyOf(managers);
		}

		private interface Check {

			void check(X509ExtendedTrustManager manager) throws CertificateException;
		}

		private void any(Check check) throws CertificateException {
			CertificateException first = null;
			for (X509ExtendedTrustManager manager : this.managers) {
				try {
					check.check(manager);
					return;
				}
				catch (CertificateException ex) {
					first = Objects.requireNonNullElse(first, ex);
				}
			}
			throw first;
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
			any(manager -> manager.checkServerTrusted(chain, authType, socket));
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
			any(manager -> manager.checkServerTrusted(chain, authType, engine));
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			any(manager -> manager.checkServerTrusted(chain, authType));
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
			this.managers.getFirst().checkClientTrusted(chain, authType, socket);
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
			this.managers.getFirst().checkClientTrusted(chain, authType, engine);
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			this.managers.getFirst().checkClientTrusted(chain, authType);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return this.managers.stream().flatMap(manager -> Arrays.stream(manager.getAcceptedIssuers())).toArray(X509Certificate[]::new);
		}
	}
}
