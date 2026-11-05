package io.salvia95.cozy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import io.salvia95.cozy.runtime.internal.PortFile;
import io.salvia95.cozy.runtime.testapp.RawHttp;
import io.salvia95.cozy.runtime.testapp.TestApp;

/**
 * 실제 웹 서버로 띄우는 CozyApplication.run (PRD §9.2, M1 완료 기준).
 */
class CozyApplicationTest {

	private static String[] args(Path dataDir, String... extra) {
		String[] base = { "--cozy.data-dir=" + dataDir, "--cozy.runtime.open-browser=false" };
		String[] all = new String[base.length + extra.length];
		System.arraycopy(base, 0, all, 0, base.length);
		System.arraycopy(extra, 0, all, base.length, extra.length);
		return all;
	}

	@Test
	void startsOnFreeLoopbackPortAndHandsOverToRunningInstance() throws Exception {
		Path dataDir = Path.of("target/test-data/app-" + UUID.randomUUID());
		ConfigurableApplicationContext first = CozyApplication.run(TestApp.class,
				args(dataDir, "--server.address=0.0.0.0"));
		assertThat(first).isNotNull();
		try {
			int port = ((WebServerApplicationContext) first).getWebServer().getPort();
			assertThat(port).isPositive();
			assertThat(PortFile.read(dataDir.resolve("app.port"))).contains(port);

			assertThat(RawHttp.get("127.0.0.1", port, "/hello", "127.0.0.1:" + port)).isEqualTo(200);
			assertThat(RawHttp.get("127.0.0.1", port, "/hello", "localhost:" + port)).isEqualTo(200);
			assertThat(RawHttp.get("127.0.0.1", port, "/hello", "evil.example:" + port)).isEqualTo(403);
			assertThat(RawHttp.get("127.0.0.1", port, "/hello", "evil.example")).isEqualTo(403);

			// 같은 데이터 폴더로 두 번째 실행: 컨텍스트를 만들지 않고 끝난다
			ConfigurableApplicationContext second = CozyApplication.run(TestApp.class, args(dataDir));
			assertThat(second).isNull();
			assertThat(first.isActive()).isTrue();
			assertThat(PortFile.read(dataDir.resolve("app.port"))).contains(port);
		}
		finally {
			first.close();
		}

		// 종료하면 포트 파일이 지워지고 잠금이 풀려 다시 실행할 수 있다
		assertThat(dataDir.resolve("app.port")).doesNotExist();
		ConfigurableApplicationContext third = CozyApplication.run(TestApp.class, args(dataDir));
		assertThat(third).isNotNull();
		third.close();
	}

	@Test
	void missingOrInvalidAppIdFailsWithFix() {
		Path dataDir = Path.of("target/test-data/app-" + UUID.randomUUID());
		assertThatThrownBy(() -> CozyApplication.run(TestApp.class, args(dataDir, "--cozy.app.id=")))
			.hasMessageContaining("[cozy] cozy.app.id가 없습니다");
		assertThatThrownBy(() -> CozyApplication.run(TestApp.class, args(dataDir, "--cozy.app.id=Bad_Id")))
			.hasMessageContaining("형식이 맞지 않습니다");
	}
}
