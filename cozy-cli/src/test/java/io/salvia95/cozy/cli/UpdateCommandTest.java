package io.salvia95.cozy.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.channel.ChannelClient;
import io.salvia95.cozy.cli.internal.channel.ChannelSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code cozy update}: 채널 색인을 읽어 지금 판과 새 판을 비교해 보인다. 아무것도 바꾸지 않는다. 채널은 파일(경로, {@code file:})과
 * 로컬 HTTP 서버로 확인한다. 하루에 한 번 하는 새 판 알림은 명령의 결과를 바꾸지 않는다.
 */
class UpdateCommandTest {

	static final String SHA = "a".repeat(64);

	@TempDir
	Path temp;

	Map<String, String> env;

	final List<HttpServer> servers = new ArrayList<>();

	@BeforeEach
	void env() throws IOException {
		this.env = new HashMap<>();
		this.env.put("XDG_CONFIG_HOME", this.temp.resolve("config").toString());
		this.env.put("HOME", this.temp.resolve("home").toString());
		installed("2.0.0");
	}

	@AfterEach
	void stop() {
		this.servers.forEach(server -> server.stop(0));
	}

	record Result(int code, String out, String err) {
	}

	Result run(String... args) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.runWith(args, this.temp, this.env, new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	/** 설치 폴더: COZY_VERSION이 있다. 번들의 cozy 명령이 COZY_HOME으로 알려 준다 */
	void installed(String version) throws IOException {
		Path home = Files.createDirectories(this.temp.resolve("install"));
		Files.writeString(home.resolve("COZY_VERSION"), version + "\n");
		this.env.put("COZY_HOME", home.toString());
	}

	static String release(String version, String date, String summary, String... appSteps) {
		boolean breaking = version.endsWith(".0.0");
		StringBuilder steps = new StringBuilder();
		for (String step : appSteps) {
			steps.append(steps.isEmpty() ? "" : ", ").append('"').append(step).append('"');
		}
		return """
				{"version": "%s", "date": "%s", "breaking": %s, "summary": "%s", "appSteps": [%s],
				 "files": [{"kind": "msi", "path": "../%s/cozy-%s-windows-x64.msi", "sha256": "%s", "size": 10}]}
				""".formatted(version, date, breaking, summary, steps, version, version, SHA);
	}

	static String index(String... releases) {
		return "{\"schema\": 1, \"releases\": [" + String.join(",", releases) + "]}";
	}

	Path channelFolder(String json) throws IOException {
		Path file = Files.createDirectories(this.temp.resolve("channel/index")).resolve("cozy-channel.json");
		Files.writeString(file, json);
		return file;
	}

	void useChannel(String address) {
		this.env.put("COZY_CHANNEL", address);
	}

	/** 요청을 기록하고 정한 응답을 주는 로컬 HTTP 서버 */
	static final class Recorded {

		final List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();

		final AtomicInteger hits = new AtomicInteger();
	}

	interface Handler {

		void handle(HttpExchange exchange) throws IOException;
	}

	String serve(Recorded recorded, Handler handler) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			recorded.hits.incrementAndGet();
			recorded.headers.add(Map.copyOf(exchange.getRequestHeaders()));
			try (exchange) {
				handler.handle(exchange);
			}
		});
		server.start();
		this.servers.add(server);
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
	}

	@Nested
	class FromAFileChannel {

		@Test
		void newerReleasesAreListedWithTheirSummariesAndNothingChanges() throws IOException {
			Path channel = channelFolder(index(release("2.2.0", "2026-12-20", "둘째 판"), release("2.1.0", "2026-12-01", "첫째 판"),
					release("1.9.0", "2026-10-01", "옛 판")));
			useChannel(channel.toString());

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("지금 판:  2.0.0")
				.contains("최신 판:  2.2.0")
				.contains("2.2.0 (2026-12-20)  둘째 판")
				.contains("2.1.0 (2026-12-01)  첫째 판")
				.doesNotContain("옛 판")
				.contains("cozy upgrade로 2.2.0까지 올립니다");
			assertThat(UpdateCommandTest.this.temp.resolve("install")).isDirectoryContaining(path -> path.getFileName().toString().equals("COZY_VERSION"));
			assertThat(Files.readString(UpdateCommandTest.this.temp.resolve("install/COZY_VERSION"))).isEqualTo("2.0.0\n");
		}

		@Test
		void aFileUriIsTheSameChannel() throws IOException {
			Path channel = channelFolder(index(release("2.1.0", "2026-12-01", "첫째 판")));
			useChannel(channel.toUri().toString());

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("최신 판:  2.1.0");
		}

		@Test
		void theJsonFormHasEveryNewerRelease() throws IOException {
			Path channel = channelFolder(index(release("3.0.0", "2027-01-10", "새 주 버전", "cozy migrate events"),
					release("2.1.0", "2026-12-01", "첫째 판")));
			useChannel(channel.toString());

			Result result = run("update", "--json");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("\"current\": \"2.0.0\"")
				.contains("\"latest\": \"3.0.0\"")
				.contains("\"upgrade\": \"2.1.0\"")
				.contains("\"channel\": \"" + channel.toString().replace("\\", "\\\\") + "\"")
				.contains("\"breaking\": true")
				.contains("\"appSteps\": [\n        \"cozy migrate events\"\n      ]")
				.doesNotContain("지금 판");
		}

		@Test
		void anUpToDateInstallSaysSo() throws IOException {
			useChannel(channelFolder(index(release("2.0.0", "2026-11-17", "지금 판"))).toString());

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("최신 판입니다").doesNotContain("cozy upgrade로");
		}

		@Test
		void aBreakingReleaseIsMarkedAndNeedsItsVersionWritten() throws IOException {
			useChannel(channelFolder(index(release("3.0.0", "2027-01-10", "새 주 버전", "cozy migrate events"),
					release("2.1.0", "2026-12-01", "첫째 판"))).toString());

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("3.0.0 (2027-01-10)  [호환 깨짐] 새 주 버전")
				.contains("앱에서 돌릴 명령: cozy migrate events")
				.contains("cozy upgrade로 2.1.0까지 올립니다")
				.contains("cozy upgrade --to 3.0.0");
		}

		@Test
		void onlyABreakingReleaseMeansNothingIsUpgradedWithoutTo() throws IOException {
			useChannel(channelFolder(index(release("3.0.0", "2027-01-10", "새 주 버전", "cozy migrate events"))).toString());

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).doesNotContain("까지 올립니다").contains("cozy upgrade --to 3.0.0");
		}

		@Test
		void aChannelBehindTheInstallIsReportedAndNotFollowed() throws IOException {
			useChannel(channelFolder(index(release("1.9.0", "2026-10-01", "옛 판"))).toString());

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("채널의 최신 판(1.9.0)이 지금 판(2.0.0)보다 낮습니다").contains("낮은 판으로는 올리지 않습니다");
		}

		@Test
		void anIndexThatCannotBeReadFailsClosed() throws IOException {
			useChannel(channelFolder("{\"schema\": 2, \"releases\": []}").toString());

			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("schema가 1이 아닙니다");
		}

		@Test
		void aMissingIndexFailsClosed() {
			useChannel(UpdateCommandTest.this.temp.resolve("nowhere/index/cozy-channel.json").toString());

			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("채널 색인").contains("nowhere");
		}
	}

	@Nested
	class WhenItCannotCompare {

		@Test
		void withoutAChannelItSaysHowToSetOne() {
			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("채널을 정하지 않았습니다").contains("cozy channel set");
		}

		@Test
		void outsideAnInstalledCozyTheVersionIsUnknown() throws IOException {
			UpdateCommandTest.this.env.remove("COZY_HOME");
			useChannel(channelFolder(index(release("2.1.0", "2026-12-01", "첫째 판"))).toString());

			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("설치한 cozy 명령");
		}

		@Test
		void aDevelopmentBuildIsNotCompared() throws IOException {
			installed("2.1.0-SNAPSHOT");
			useChannel(channelFolder(index(release("2.1.0", "2026-12-01", "첫째 판"))).toString());

			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("2.1.0-SNAPSHOT").contains("릴리스 판이 아니라");
		}
	}

	@Nested
	class FromAWebServer {

		@Test
		void theTokenGoesInTheAuthorizationHeaderByDefault() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판"))));
			useChannel(base + "/cozy/index/cozy-channel.json");
			UpdateCommandTest.this.env.put("COZY_CHANNEL_TOKEN", "s3cret-token");

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(result.out()).contains("최신 판:  2.1.0").doesNotContain("s3cret-token");
			assertThat(recorded.headers.getFirst().get("Authorization")).containsExactly("Bearer s3cret-token");
		}

		@Test
		void aNamedHeaderCarriesTheTokenAsIs() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판"))));
			assertThat(run("channel", "set", base + "/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json", "--header",
					"PRIVATE-TOKEN").code()).isZero();
			Files.writeString(UpdateCommandTest.this.temp.resolve("config/cozy/cozy.properties"), "channel.token=glpat-123\n",
					java.nio.file.StandardOpenOption.APPEND);

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(recorded.headers.getFirst().get("Private-token")).containsExactly("glpat-123");
			assertThat(recorded.headers.getFirst()).doesNotContainKey("Authorization");
		}

		@Test
		void aRefusedRequestSaysWhatToCheckWithoutShowingTheToken() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 401, ""));
			useChannel(base + "/cozy/index/cozy-channel.json");
			UpdateCommandTest.this.env.put("COZY_CHANNEL_TOKEN", "s3cret-token");

			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("401").contains("COZY_CHANNEL_TOKEN").doesNotContain("s3cret-token");
		}

		@Test
		void aMissingIndexIsNotFound() throws IOException {
			String base = serve(new Recorded(), exchange -> respond(exchange, 404, "not found"));
			useChannel(base + "/cozy/index/cozy-channel.json");

			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("404").contains("채널 주소");
		}

		@Test
		void aRedirectToTheSameServerKeepsTheTokenAndToAnotherServerDropsIt() throws IOException {
			Recorded other = new Recorded();
			String otherBase = serve(other, exchange -> respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판"))));
			Recorded first = new Recorded();
			String firstBase = serve(first, exchange -> {
				String path = exchange.getRequestURI().getPath();
				exchange.getResponseHeaders().add("Location", path.startsWith("/latest/") ? "/download/cozy-channel.json"
						: otherBase + "/storage/cozy-channel.json");
				respond(exchange, 302, "");
			});
			useChannel(firstBase + "/latest/cozy-channel.json");
			UpdateCommandTest.this.env.put("COZY_CHANNEL_TOKEN", "s3cret-token");

			Result result = run("update");

			assertThat(result.code()).as(result.err()).isZero();
			assertThat(first.headers).hasSize(2);
			assertThat(first.headers).allSatisfy(headers -> assertThat(headers.get("Authorization")).containsExactly("Bearer s3cret-token"));
			assertThat(other.headers).hasSize(1);
			assertThat(other.headers.getFirst()).doesNotContainKey("Authorization");
		}

		@Test
		void endlessRedirectsStop() throws IOException {
			String base = serve(new Recorded(), exchange -> {
				exchange.getResponseHeaders().add("Location", "/again");
				respond(exchange, 302, "");
			});
			useChannel(base + "/cozy-channel.json");

			Result result = run("update");

			assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
			assertThat(result.err()).contains("넘겨주기");
		}
	}

	@Nested
	class FileLocations {

		@Test
		void filesAreRelativeToTheIndexAddress() {
			assertThat(ChannelClient.resolve("https://gitlab.example/api/v4/projects/42/packages/generic/cozy/index/cozy-channel.json",
					"../2.1.0/cozy-2.1.0-windows-x64.msi"))
				.isEqualTo("https://gitlab.example/api/v4/projects/42/packages/generic/cozy/2.1.0/cozy-2.1.0-windows-x64.msi");
			assertThat(ChannelClient.resolve("https://github.com/o/r/releases/latest/download/cozy-channel.json", "cozy-2.1.0.zip"))
				.isEqualTo("https://github.com/o/r/releases/latest/download/cozy-2.1.0.zip");
			assertThat(ChannelClient.resolve("file:///mnt/share/cozy/index/cozy-channel.json", "../2.1.0/a.zip"))
				.isEqualTo("file:///mnt/share/cozy/2.1.0/a.zip");
			assertThat(ChannelClient.resolve("/mnt/share/cozy/index/cozy-channel.json", "../2.1.0/a.zip"))
				.isEqualTo(Path.of("/mnt/share/cozy/2.1.0/a.zip").toString());
		}
	}

	@Nested
	class TrustedCertificates {

		@Test
		void aCertificateFromAnExtraTrustStoreIsTrustedNextToTheJdkOnes() throws Exception {
			Path keystore = UpdateCommandTest.this.temp.resolve("server.p12");
			Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair",
					"-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=intra.example", "-ext", "SAN=ip:127.0.0.1",
					"-validity", "2", "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", "changeit")
				.redirectErrorStream(true)
				.start();
			keytool.getInputStream().readAllBytes();
			assertThat(keytool.waitFor(60, TimeUnit.SECONDS)).isTrue();
			assertThat(keytool.exitValue()).isZero();
			KeyStore store = KeyStore.getInstance("PKCS12");
			try (InputStream in = Files.newInputStream(keystore)) {
				store.load(in, "changeit".toCharArray());
			}
			KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keys.init(store, "changeit".toCharArray());
			SSLContext serverContext = SSLContext.getInstance("TLS");
			serverContext.init(keys.getKeyManagers(), null, null);
			HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
			server.createContext("/", exchange -> {
				try (exchange) {
					respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판")));
				}
			});
			server.start();
			UpdateCommandTest.this.servers.add(server);
			String address = "https://127.0.0.1:" + server.getAddress().getPort() + "/cozy/index/cozy-channel.json";
			KeyStore company = KeyStore.getInstance("PKCS12");
			company.load(null, null);
			company.setCertificateEntry("company-ca", store.getCertificate("server"));

			assertThatThrownBy(() -> new ChannelClient(ChannelClient.trust(List.of()), Duration.ofSeconds(5), Duration.ofSeconds(5))
				.index(new ChannelSettings(address, "test", null, null)))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("인증서");
			assertThat(new ChannelClient(ChannelClient.trust(List.of(company)), Duration.ofSeconds(5), Duration.ofSeconds(5))
				.index(new ChannelSettings(address, "test", null, null))
				.releases()).hasSize(1);
		}
	}

	@Nested
	class DailyNotice {

		final LocalDate today = LocalDate.of(2026, 12, 2);

		String notice(String... args) {
			ByteArrayOutputStream err = new ByteArrayOutputStream();
			CozyCli.notice(args, UpdateCommandTest.this.env, false, this.today,
					() -> new ChannelClient(ChannelClient.trust(List.of()), Duration.ofSeconds(2), Duration.ofSeconds(2)),
					new PrintStream(err, true, StandardCharsets.UTF_8));
			return err.toString(StandardCharsets.UTF_8);
		}

		@Test
		void theFirstCommandOfTheDayChecksAndTellsAboutANewRelease() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판"))));
			useChannel(base + "/cozy-channel.json");

			assertThat(notice("check")).isEqualTo("[cozy] 새 버전이 있습니다: 2.1.0. cozy upgrade로 올립니다 (바뀌는 것: cozy update)\n"
					.replace("\n", System.lineSeparator()));
			assertThat(notice("describe")).isEmpty();
			assertThat(recorded.hits.get()).isEqualTo(1);
			assertThat(notice("check")).isEmpty();
		}

		@Test
		void theNextDayChecksAgain() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 200, index(release("2.0.0", "2026-11-17", "지금 판"))));
			useChannel(base + "/cozy-channel.json");
			Path state = UpdateCommandTest.this.temp.resolve("config/cozy/update-check.properties");
			Files.createDirectories(state.getParent());
			Files.writeString(state, "checked=2026-12-01\n");

			assertThat(notice("check")).isEmpty();
			assertThat(recorded.hits.get()).isEqualTo(1);
			assertThat(Files.readString(state)).contains("checked=2026-12-02");
		}

		@Test
		void aBreakingReleaseIsAnnouncedWithoutAnUpgradeCommand() throws IOException {
			useChannel(channelFolder(index(release("3.0.0", "2027-01-10", "새 주 버전"))).toString());

			assertThat(notice("check")).contains("호환이 깨지는 새 버전이 있습니다: 3.0.0").contains("cozy update");
		}

		@Test
		void aFailedCheckSaysNothingAndWaitsForTheNextDay() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 500, "broken"));
			useChannel(base + "/cozy-channel.json");

			assertThat(notice("check")).isEmpty();
			assertThat(notice("check")).isEmpty();
			assertThat(recorded.hits.get()).isEqualTo(1);
		}

		@Test
		void machineOutputUpdateChannelAndHelpAreLeftAlone() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판"))));
			useChannel(base + "/cozy-channel.json");

			assertThat(notice("check", "--json")).isEmpty();
			assertThat(notice("--project", "check", "describe", "--json")).isEmpty();
			assertThat(notice("update")).isEmpty();
			assertThat(notice("channel", "show")).isEmpty();
			assertThat(notice("help")).isEmpty();
			assertThat(notice()).isEmpty();
			assertThat(recorded.hits.get()).isZero();
		}

		@Test
		void withoutAChannelOrAReleaseVersionNothingIsChecked() throws IOException {
			assertThat(notice("check")).isEmpty();
			installed("2.1.0-SNAPSHOT");
			Recorded recorded = new Recorded();
			useChannel(serve(recorded, exchange -> respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판"))))
					+ "/cozy-channel.json");
			assertThat(notice("check")).isEmpty();
			UpdateCommandTest.this.env.remove("COZY_HOME");
			assertThat(notice("check")).isEmpty();
			assertThat(recorded.hits.get()).isZero();
		}

		@Test
		void cozyUpdateRecordsTheCheckSoTheNoticeWaitsForTomorrow() throws IOException {
			Recorded recorded = new Recorded();
			String base = serve(recorded, exchange -> respond(exchange, 200, index(release("2.1.0", "2026-12-01", "첫째 판"))));
			useChannel(base + "/cozy-channel.json");
			System.setProperty("cozy.cli.now", "202612020900");
			try {
				assertThat(run("update").code()).isZero();
			}
			finally {
				System.clearProperty("cozy.cli.now");
			}

			assertThat(notice("check")).isEmpty();
			assertThat(recorded.hits.get()).isEqualTo(1);
		}
	}
}
