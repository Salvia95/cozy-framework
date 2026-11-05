package io.salvia95.cozy.runtime.internal;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 기본 브라우저로 주소를 연다 (PRD §9.2).
 *
 * <p>{@link Desktop#browse}를 먼저 쓰고, 안 되면 OS의 기본 URL 처리기로 대체한다.
 * Windows는 {@code rundll32 url.dll,FileProtocolHandler}다. 모두 실패하면 주소를 로그로 남긴다.
 */
public final class BrowserOpener {

	private static final Log logger = LogFactory.getLog(BrowserOpener.class);

	private BrowserOpener() {
	}

	/** @return 열기를 요청했으면 true */
	public static boolean open(URI uri) {
		if (browseWithDesktop(uri)) {
			return true;
		}
		List<String> command = fallbackCommand(System.getProperty("os.name", ""), uri);
		if (command != null) {
			try {
				new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
				return true;
			}
			catch (IOException ex) {
				logger.debug("기본 URL 처리기를 실행할 수 없습니다: " + command, ex);
			}
		}
		logger.warn("[cozy] 브라우저를 열 수 없습니다. 직접 여세요: " + uri);
		return false;
	}

	private static boolean browseWithDesktop(URI uri) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(uri);
				return true;
			}
		}
		catch (Exception | LinkageError ex) {
			logger.debug("Desktop.browse를 쓸 수 없습니다", ex);
		}
		return false;
	}

	static List<String> fallbackCommand(String osName, URI uri) {
		String os = osName.toLowerCase(Locale.ROOT);
		if (os.startsWith("windows")) {
			return List.of("rundll32", "url.dll,FileProtocolHandler", uri.toString());
		}
		if (os.startsWith("mac")) {
			return List.of("open", uri.toString());
		}
		if (os.contains("linux")) {
			return List.of("xdg-open", uri.toString());
		}
		return null;
	}
}
