package io.salvia95.cozy.runtime.internal;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import io.salvia95.cozy.runtime.CozyDataDirectory;
import io.salvia95.cozy.runtime.internal.update.UpdateService;

/**
 * {@code CozyApplication.run}으로 띄운 앱의 데스크톱 동작 (PRD §9.2).
 *
 * <ul>
 * <li>환경이 준비되면, 컨텍스트를 만들기 전에 {@code app.lock}을 잡는다. 못 잡으면 기존 인스턴스 주소를 브라우저로 열고 조용히 끝낸다.</li>
 * <li>웹 서버가 뜨면 실제 포트를 {@code app.port}에 쓴다.</li>
 * <li>기동이 끝나면 업데이트 준비 신호({@code update/ready})를 쓰고, 브라우저를 열고({@code cozy.runtime.open-browser}),
 * 트레이 아이콘을 올린다({@code cozy.runtime.tray}).</li>
 * <li>훈련 실행({@code cozy.runtime.training})이면 위 대신 정해진 경로를 요청하고 종료한다(PRD §9.6).</li>
 * <li>종료하면 트레이 아이콘을 내리고, 포트 파일을 지우고, 잠금을 푼다.</li>
 * </ul>
 */
public class DesktopLauncher implements ApplicationListener<ApplicationEvent>, Ordered {

	/** 설정 확정(EnvironmentPostProcessor) 뒤, 로깅 초기화 앞. 두 번째 인스턴스가 실행 중인 앱의 로그 파일을 건드리지 않게 한다. */
	public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 15;

	static final Duration PORT_WAIT = Duration.ofSeconds(15);

	private @Nullable InstanceLock lock;

	private @Nullable CozyDataDirectory dataDir;

	private final CozyTray tray = new CozyTray();

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ApplicationEnvironmentPreparedEvent prepared) {
			acquireOrHandOver(prepared.getEnvironment());
		}
		else if (event instanceof WebServerInitializedEvent initialized
				&& initialized.getApplicationContext().getServerNamespace() == null && this.dataDir != null) {
			PortFile.write(this.dataDir.portFile(), initialized.getWebServer().getPort());
		}
		else if (event instanceof ApplicationReadyEvent ready) {
			onReady(ready);
		}
		else if (event instanceof ContextClosedEvent || event instanceof ApplicationFailedEvent) {
			this.tray.remove();
			release();
		}
	}

	private void acquireOrHandOver(Environment environment) {
		CozyDataDirectory dir = CozyDataDirectory.of(Path.of(environment.getRequiredProperty("cozy.data-dir")));
		Optional<InstanceLock> acquired = InstanceLock.tryAcquire(dir.lockFile());
		if (acquired.isPresent()) {
			this.lock = acquired.get();
			this.dataDir = dir;
			return;
		}
		boolean openBrowser = openBrowser(environment);
		Optional<Integer> port = waitForPort(dir.portFile(), PORT_WAIT);
		if (port.isPresent()) {
			URI uri = localUri(port.get());
			System.err.println("[cozy] 이미 실행 중인 인스턴스가 있어 그 화면을 엽니다: " + uri);
			if (openBrowser) {
				BrowserOpener.open(uri);
			}
		}
		else {
			System.err.println("[cozy] 이미 실행 중인 인스턴스가 있지만 주소를 알 수 없습니다(" + dir.portFile() + "). 잠시 뒤 다시 실행하세요.");
		}
		throw new AlreadyRunningException();
	}

	private void onReady(ApplicationReadyEvent ready) {
		if (!(ready.getApplicationContext() instanceof WebServerApplicationContext webContext)
				|| webContext.getWebServer() == null) {
			return;
		}
		ConfigurableApplicationContext context = ready.getApplicationContext();
		Environment environment = context.getEnvironment();
		URI uri = localUri(webContext.getWebServer().getPort());
		CozyShutdown shutdown = context.getBean(CozyShutdown.class);
		if (environment.getProperty("cozy.runtime.training", Boolean.class, false)) {
			List<String> paths = Binder.get(environment)
				.bind("cozy.runtime.training-paths", Bindable.listOf(String.class))
				.orElse(List.of("/"));
			LogFactory.getLog(DesktopLauncher.class).info("[cozy] 훈련 실행: " + paths);
			TrainingRun.start(uri, paths, shutdown);
			return;
		}
		context.getBeanProvider(UpdateService.class).ifAvailable(UpdateService::signalReady);
		LogFactory.getLog(DesktopLauncher.class).info("[cozy] 실행 중: " + uri);
		boolean browser = openBrowser(environment);
		boolean tray = environment.getProperty("cozy.runtime.tray", Boolean.class, true);
		String name = environment.getProperty("cozy.app.name", environment.getProperty("cozy.app.id", "Cozy"));
		// 브라우저 열기와 트레이는 AWT를 쓴다. 디스플레이 서버에 연결하는 동안 멈출 수 있으므로(연결할 수 없는 DISPLAY 등)
		// 기동 스레드가 아닌 데몬 스레드에서 한다. 멈춰도 앱과 종료 경로는 영향을 받지 않는다
		Thread.ofPlatform().daemon().name("cozy-desktop").start(() -> {
			if (browser) {
				BrowserOpener.open(uri);
			}
			if (tray) {
				this.tray.install(name, uri, shutdown);
			}
		});
	}

	private void release() {
		if (this.dataDir != null) {
			PortFile.delete(this.dataDir.portFile());
		}
		if (this.lock != null) {
			this.lock.close();
			this.lock = null;
		}
	}

	private static boolean openBrowser(Environment environment) {
		return environment.getProperty("cozy.runtime.open-browser", Boolean.class, true);
	}

	static URI localUri(int port) {
		return URI.create("http://" + CozyRuntimeEnvironmentPostProcessor.LOOPBACK + ":" + port + "/");
	}

	/** 먼저 뜬 인스턴스가 아직 기동 중이면 포트 파일이 생길 때까지 잠시 기다린다. */
	static Optional<Integer> waitForPort(Path portFile, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (true) {
			Optional<Integer> port = PortFile.read(portFile);
			if (port.isPresent() || System.nanoTime() > deadline) {
				return port;
			}
			try {
				Thread.sleep(200);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
		}
	}

	/** 다른 인스턴스에 화면을 넘기고 이 실행을 조용히 끝낸다. Boot는 이 예외를 실패로 보고하지 않는다. */
	public static final class AlreadyRunningException extends SpringApplication.AbandonedRunException {

		private static final long serialVersionUID = 1L;

	}
}
