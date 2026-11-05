package io.salvia95.cozy.runtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceFilesTest {

	@TempDir
	Path temp;

	@Test
	void secondLockInSameJvmIsRefusedUntilReleased() {
		Path file = this.temp.resolve("app.lock");
		try (InstanceLock first = InstanceLock.tryAcquire(file).orElseThrow()) {
			assertThat(first.isHeld()).isTrue();
			assertThat(InstanceLock.tryAcquire(file)).isEmpty();
		}
		try (InstanceLock again = InstanceLock.tryAcquire(file).orElseThrow()) {
			assertThat(again.isHeld()).isTrue();
		}
	}

	@Test
	void portFileRoundTrip() {
		Path file = this.temp.resolve("app.port");
		assertThat(PortFile.read(file)).isEmpty();
		PortFile.write(file, 51234);
		assertThat(PortFile.read(file)).contains(51234);
		PortFile.write(file, 51235);
		assertThat(PortFile.read(file)).contains(51235);
		assertThat(this.temp.resolve("app.port.tmp")).doesNotExist();
		PortFile.delete(file);
		assertThat(file).doesNotExist();
	}

	@Test
	void portFileWithGarbageIsIgnored() throws Exception {
		Path file = this.temp.resolve("app.port");
		Files.writeString(file, "not-a-port");
		assertThat(PortFile.read(file)).isEmpty();
		Files.writeString(file, "70000");
		assertThat(PortFile.read(file)).isEmpty();
	}

	@Test
	void waitForPortReturnsWhenFileAppears() throws Exception {
		Path file = this.temp.resolve("app.port");
		Thread writer = Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(300);
			}
			catch (InterruptedException ex) {
				return;
			}
			PortFile.write(file, 40000);
		});
		assertThat(DesktopLauncher.waitForPort(file, Duration.ofSeconds(5))).contains(40000);
		writer.join();
		assertThat(DesktopLauncher.waitForPort(this.temp.resolve("missing"), Duration.ofMillis(300))).isEmpty();
	}

	@Test
	void fallbackBrowserCommands() {
		URI uri = URI.create("http://127.0.0.1:1234/");
		assertThat(BrowserOpener.fallbackCommand("Windows 11", uri))
			.containsExactly("rundll32", "url.dll,FileProtocolHandler", "http://127.0.0.1:1234/");
		assertThat(BrowserOpener.fallbackCommand("Linux", uri)).containsExactly("xdg-open", "http://127.0.0.1:1234/");
		assertThat(BrowserOpener.fallbackCommand("Mac OS X", uri)).containsExactly("open", "http://127.0.0.1:1234/");
	}

	@Test
	void hostHeaderRules() {
		int port = 51234;
		assertThat(HostHeaderFilter.isAllowed("127.0.0.1:51234", "x", 0, port)).isTrue();
		assertThat(HostHeaderFilter.isAllowed("localhost:51234", "x", 0, port)).isTrue();
		assertThat(HostHeaderFilter.isAllowed("LOCALHOST:51234", "x", 0, port)).isTrue();
		assertThat(HostHeaderFilter.isAllowed("evil.example:51234", "x", 0, port)).isFalse();
		assertThat(HostHeaderFilter.isAllowed("127.0.0.1:51235", "x", 0, port)).isFalse();
		assertThat(HostHeaderFilter.isAllowed("127.0.0.1", "x", 0, port)).isFalse();
		assertThat(HostHeaderFilter.isAllowed("127.0.0.1", "x", 0, 80)).isTrue();
		assertThat(HostHeaderFilter.isAllowed("[::1]:51234", "x", 0, port)).isFalse();
		assertThat(HostHeaderFilter.isAllowed("127.0.0.1:abc", "x", 0, port)).isFalse();
		assertThat(HostHeaderFilter.isAllowed("127.0.0.1.evil.example:51234", "x", 0, port)).isFalse();
		// Host 헤더가 없으면 컨테이너가 정한 서버 이름과 포트로 판단한다
		assertThat(HostHeaderFilter.isAllowed(null, "localhost", 80, 80)).isTrue();
		assertThat(HostHeaderFilter.isAllowed(null, "evil.example", 80, 80)).isFalse();
	}
}
