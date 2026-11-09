package io.salvia95.cozy.runtime.internal;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 앱 종료. 화면 메뉴의 POST /cozy/shutdown, 트레이의 종료, 업데이트 설치, 훈련 실행이 쓴다.
 *
 * <p>응답이 브라우저에 도착하도록 잠시 기다린 뒤 별도 스레드에서 컨텍스트를 닫는다.
 * {@code CozyApplication.run}으로 띄운 앱({@code cozy.runtime.exit-jvm=true})은 JVM도 끝낸다.
 * 트레이(AWT) 스레드가 남아 있어도 프로세스가 끝나야 업데이트 스크립트가 앱 폴더를 바꿀 수 있기 때문이다.
 */
public class CozyShutdown {

	/** {@code CozyApplication.run}이 기본값으로 켠다. 테스트 컨텍스트에서는 꺼져 있다. */
	public static final String EXIT_JVM_PROPERTY = "cozy.runtime.exit-jvm";

	private static final Log logger = LogFactory.getLog(CozyShutdown.class);

	private static final long RESPONSE_GRACE_MILLIS = 300;

	private final ConfigurableApplicationContext context;

	private final boolean exitJvm;

	private final AtomicBoolean requested = new AtomicBoolean();

	public CozyShutdown(ConfigurableApplicationContext context, boolean exitJvm) {
		this.context = context;
		this.exitJvm = exitJvm;
	}

	/** 종료를 요청한다. 두 번째 요청부터는 무시한다. */
	public void request(String reason, int exitCode) {
		if (!this.requested.compareAndSet(false, true)) {
			return;
		}
		logger.info("[cozy] 종료합니다: " + reason);
		Thread thread = new Thread(() -> {
			try {
				Thread.sleep(RESPONSE_GRACE_MILLIS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			int code = SpringApplication.exit(this.context, () -> exitCode);
			if (this.exitJvm) {
				System.exit(code);
			}
		}, "cozy-shutdown");
		thread.setDaemon(false);
		thread.start();
	}

	public boolean isRequested() {
		return this.requested.get();
	}
}
