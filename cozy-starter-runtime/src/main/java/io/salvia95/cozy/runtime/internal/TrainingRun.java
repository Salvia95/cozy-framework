package io.salvia95.cozy.runtime.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * AOT 캐시 훈련 실행 (PRD §9.6, {@code cozy.runtime.training=true}).
 *
 * <p>기동이 끝나면 정해진 경로를 한 번씩 요청해 화면 렌더링에 쓰는 클래스까지 읽게 한 뒤 앱을 종료한다.
 * 요청이 하나라도 2xx가 아니면 종료 코드 1로 끝나 {@code -Pdist}가 실패한다.
 */
final class TrainingRun {

	private static final Log logger = LogFactory.getLog(TrainingRun.class);

	private TrainingRun() {
	}

	static void start(URI base, List<String> paths, CozyShutdown shutdown) {
		Thread thread = new Thread(() -> {
			int code = run(base, paths) ? 0 : 1;
			shutdown.request("훈련 실행 완료", code);
		}, "cozy-training");
		thread.start();
	}

	static boolean run(URI base, List<String> paths) {
		HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		boolean ok = true;
		for (String path : paths) {
			URI uri = base.resolve(path.startsWith("/") ? path.substring(1) : path);
			try {
				HttpResponse<String> response = client.send(
						HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
						HttpResponse.BodyHandlers.ofString());
				boolean success = response.statusCode() / 100 == 2;
				logger.info("[cozy] 훈련 요청 " + uri + " → " + response.statusCode());
				ok &= success;
			}
			catch (Exception ex) {
				if (ex instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				logger.error("[cozy] 훈련 요청 실패 " + uri, ex);
				ok = false;
			}
		}
		return ok;
	}
}
