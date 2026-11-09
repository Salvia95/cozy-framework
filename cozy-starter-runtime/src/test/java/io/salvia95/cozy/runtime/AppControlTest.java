package io.salvia95.cozy.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import io.salvia95.cozy.runtime.internal.InstanceLock;
import io.salvia95.cozy.runtime.testapp.TestApp;

/**
 * 실제로 띄운 앱의 종료, 준비 신호, 훈련 실행.
 * JVM 종료는 테스트 JVM을 끝내므로 {@code cozy.runtime.exit-jvm=false}로 컨텍스트 종료까지만 본다.
 */
class AppControlTest {

	@Test
	void shutdownFromScreenMenuClosesAppAndReleasesInstance() throws Exception {
		Path dataDir = Path.of("target/test-data/control-" + UUID.randomUUID());
		ConfigurableApplicationContext context = CozyApplication.run(TestApp.class, "--cozy.data-dir=" + dataDir,
				"--cozy.runtime.open-browser=false", "--cozy.runtime.tray=false", "--cozy.runtime.exit-jvm=false");
		assertThat(context).isNotNull();
		int port = ((WebServerApplicationContext) context).getWebServer().getPort();
		// 기동을 마친 앱은 업데이트 준비 신호에 자기 버전을 쓴다 (build-info가 없으면 dev)
		assertThat(dataDir.resolve("update/ready")).hasContent("dev");

		HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
		URI base = URI.create("http://127.0.0.1:" + port);
		HttpResponse<String> withoutToken = client.send(HttpRequest.newBuilder(base.resolve("/cozy/shutdown"))
			.POST(HttpRequest.BodyPublishers.noBody())
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(withoutToken.statusCode()).isEqualTo(403);
		assertThat(context.isActive()).isTrue();

		String[] csrf = client.send(HttpRequest.newBuilder(base.resolve("/csrf")).build(),
				HttpResponse.BodyHandlers.ofString()).body().split("=", 2);
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(base.resolve("/cozy/shutdown"))
			.header(csrf[0], csrf[1])
			.POST(HttpRequest.BodyPublishers.noBody())
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("앱을 종료했습니다");

		await(() -> !context.isActive(), Duration.ofSeconds(10));
		assertThat(dataDir.resolve("app.port")).doesNotExist();
		try (InstanceLock lock = InstanceLock.tryAcquire(dataDir.resolve("app.lock")).orElseThrow()) {
			assertThat(lock).isNotNull();
		}
	}

	@Test
	void trainingRunRequestsPathsAndExitsWithoutReadySignal() throws Exception {
		Path dataDir = Path.of("target/test-data/training-" + UUID.randomUUID());
		ConfigurableApplicationContext context = CozyApplication.run(TestApp.class, "--cozy.data-dir=" + dataDir,
				"--cozy.runtime.open-browser=false", "--cozy.runtime.exit-jvm=false", "--cozy.runtime.training=true",
				"--cozy.runtime.training-paths=/hello,/csrf");
		assertThat(context).isNotNull();
		await(() -> !context.isActive(), Duration.ofSeconds(20));
		assertThat(dataDir.resolve("update/ready")).doesNotExist();
		assertThat(Files.readString(dataDir.resolve("logs/app.log"))).contains("훈련 요청 http://127.0.0.1:")
			.contains("/hello → 200")
			.contains("/csrf → 200");
	}

	private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError(timeout + " 안에 조건이 맞지 않았습니다");
			}
			Thread.sleep(100);
		}
	}
}
