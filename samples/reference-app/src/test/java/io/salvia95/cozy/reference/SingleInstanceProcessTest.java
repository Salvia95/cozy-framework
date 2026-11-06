package io.salvia95.cozy.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * M1 완료 기준을 실제 프로세스 두 개로 검증한다.
 *
 * <ul>
 * <li>reference-app이 127.0.0.1의 빈 포트로 뜬다. {@code --server.address=0.0.0.0}을 줘도 바뀌지 않는다.</li>
 * <li>두 번째 실행은 기존 인스턴스 주소를 알리고 종료 코드 0으로 끝난다.</li>
 * <li>다른 Host 헤더 요청은 403이다.</li>
 * <li>Linux에서는 연결할 수 없는 디스플레이로 띄운다. 트레이를 올릴 수 없어도 앱은 계속 돈다
 * (디스플레이 서버가 없는 PC나 CI. AWT가 {@code AWTError}를 던지는 경로).</li>
 * </ul>
 * 브라우저가 실제로 열리는지는 Windows에서 수동으로 확인한다(docs/conventions/windows-verification.md).
 */
class SingleInstanceProcessTest {

	private static final Duration STARTUP = Duration.ofSeconds(60);

	private final Path dataDir = Path.of("target/test-data/process-" + UUID.randomUUID()).toAbsolutePath();

	private final List<Process> processes = new ArrayList<>();

	@AfterEach
	void stopAll() {
		this.processes.forEach(Process::destroyForcibly);
	}

	@Test
	void secondLaunchHandsOverAndExitsWithZero() throws Exception {
		Process first = launch("first.log");
		int port = waitForPort(first);

		assertThat(status(port, "127.0.0.1:" + port)).isNotEqualTo(403);
		assertThat(status(port, "localhost:" + port)).isNotEqualTo(403);
		assertThat(status(port, "evil.example:" + port)).isEqualTo(403);
		assertThat(status(port, "127.0.0.1:" + (port + 1))).isEqualTo(403);
		for (InetAddress address : nonLoopbackAddresses()) {
			assertThatThrownBy(() -> new Socket().connect(new InetSocketAddress(address, port), 2000))
				.as("127.0.0.1 밖의 주소 %s에서 접속되면 안 된다", address)
				.isInstanceOf(ConnectException.class);
		}

		Process second = launch("second.log");
		assertThat(second.waitFor(STARTUP.toSeconds(), TimeUnit.SECONDS)).as("두 번째 실행이 끝나지 않았다").isTrue();
		assertThat(second.exitValue()).isZero();
		String secondLog = Files.readString(this.dataDir.resolveSibling(this.dataDir.getFileName() + "-second.log"));
		assertThat(secondLog).contains("이미 실행 중인 인스턴스가 있어 그 화면을 엽니다: http://127.0.0.1:" + port + "/")
			.doesNotContain("Tomcat started");
		assertThat(first.isAlive()).isTrue();
		if (!isWindows()) {
			assertThat(Files.readString(this.dataDir.resolveSibling(this.dataDir.getFileName() + "-first.log")))
				.contains("트레이 아이콘을 올릴 수 없습니다");

			// Windows의 destroy()는 종료 훅 없이 프로세스를 끊는다. 정상 종료 경로(M6 종료 메뉴)는 Windows에서 따로 검증한다.
			first.destroy();
			assertThat(first.waitFor(30, TimeUnit.SECONDS)).isTrue();
			assertThat(this.dataDir.resolve("app.port")).doesNotExist();
		}
	}

	private Process launch(String logName) throws IOException {
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		ProcessBuilder builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
				ReferenceApp.class.getName(), "--cozy.data-dir=" + this.dataDir, "--cozy.runtime.open-browser=false",
				"--server.address=0.0.0.0")
			.redirectErrorStream(true)
			.redirectOutput(this.dataDir.resolveSibling(this.dataDir.getFileName() + "-" + logName).toFile());
		if (!isWindows()) {
			// 연결할 수 없는 디스플레이: headless가 아니라서 AWT가 디스플레이에 연결하려다 AWTError를 던진다.
			// ":987"처럼 형식이 맞는 값은 환경에 따라 연결 시도가 오래 멈추므로, 바로 실패하는 값을 쓴다
			builder.environment().put("DISPLAY", "cozy-no-display");
			builder.environment().remove("WAYLAND_DISPLAY");
			builder.command().add(1, "-Djava.awt.headless=false");
		}
		Files.createDirectories(this.dataDir.getParent());
		Process process = builder.start();
		this.processes.add(process);
		return process;
	}

	private int waitForPort(Process process) throws Exception {
		Path portFile = this.dataDir.resolve("app.port");
		long deadline = System.nanoTime() + STARTUP.toNanos();
		while (System.nanoTime() < deadline) {
			assertThat(process.isAlive()).as("첫 번째 실행이 기동 중에 끝났다").isTrue();
			Optional<Integer> port = readPort(portFile);
			if (port.isPresent()) {
				return port.get();
			}
			Thread.sleep(200);
		}
		throw new AssertionError("app.port가 " + STARTUP + " 안에 생기지 않았다");
	}

	private static Optional<Integer> readPort(Path file) {
		try {
			return Optional.of(Integer.parseInt(Files.readString(file).trim()));
		}
		catch (IOException | NumberFormatException ex) {
			return Optional.empty();
		}
	}

	private static int status(int port, String host) throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
			socket.setSoTimeout(10000);
			OutputStream out = socket.getOutputStream();
			out.write(("GET / HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			String line = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
				.readLine();
			return Integer.parseInt(line.split(" ")[1]);
		}
	}

	private static List<InetAddress> nonLoopbackAddresses() throws IOException {
		List<InetAddress> addresses = new ArrayList<>();
		for (NetworkInterface nic : NetworkInterface.networkInterfaces().toList()) {
			if (!nic.isUp() || nic.isLoopback()) {
				continue;
			}
			nic.inetAddresses().filter((a) -> a instanceof Inet4Address && !a.isLoopbackAddress()).forEach(addresses::add);
		}
		return addresses;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}
}
