package io.salvia95.cozy.runtime.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.HtmlUtils;

import io.salvia95.cozy.runtime.internal.diagnostics.DiagnosticsExporter;
import io.salvia95.cozy.runtime.internal.update.UpdateService;
import io.salvia95.cozy.runtime.internal.update.UpdateService.UpdateException;

/**
 * 화면 메뉴의 앱 제어 (PRD §9.2, §9.4, §9.5). 모두 POST이고 CSRF 토큰이 필요하다.
 *
 * <ul>
 * <li>{@code POST /cozy/shutdown}: 앱을 종료한다.</li>
 * <li>{@code POST /cozy/diagnostics}: 진단 zip을 만들고 폴더를 연 뒤, 보던 화면으로 돌아가 알림을 보인다.</li>
 * <li>{@code POST /cozy/update?version}: 업데이트를 설치하고 앱을 종료한다. 교체 스크립트가 새 버전을 실행한다.</li>
 * </ul>
 */
@Controller
@RequestMapping("/cozy")
public class CozyControlController {

	/** cozy-ui의 app-shell이 그리는 플래시 알림 이름 ({@code CozyFlash.ATTRIBUTE}와 같다) */
	public static final String FLASH_ATTRIBUTE = "cozyFlash";

	private static final Log logger = LogFactory.getLog(CozyControlController.class);

	private final CozyShutdown shutdown;

	private final DiagnosticsExporter diagnostics;

	private final UpdateService updates;

	private final boolean desktop;

	public CozyControlController(CozyShutdown shutdown, DiagnosticsExporter diagnostics, UpdateService updates,
			boolean desktop) {
		this.shutdown = shutdown;
		this.diagnostics = diagnostics;
		this.updates = updates;
		this.desktop = desktop;
	}

	@PostMapping("/shutdown")
	ResponseEntity<String> shutdown() {
		this.shutdown.request("화면 메뉴", 0);
		return page("앱을 종료했습니다", "이 탭을 닫아도 됩니다. 다시 쓰려면 앱을 실행하세요.");
	}

	@PostMapping("/diagnostics")
	String diagnostics(HttpServletRequest request, RedirectAttributes redirect) {
		try {
			Path zip = this.diagnostics.export();
			if (this.desktop) {
				BrowserOpener.openFolder(zip.getParent());
			}
			redirect.addFlashAttribute(FLASH_ATTRIBUTE,
					new Notice("success", "진단 파일을 만들었습니다: " + zip + " . 이 파일을 담당자에게 보내 주세요."));
		}
		catch (IOException | RuntimeException ex) {
			logger.error("[cozy] 진단 파일을 만들 수 없습니다", ex);
			redirect.addFlashAttribute(FLASH_ATTRIBUTE, new Notice("danger", "진단 파일을 만들 수 없습니다: " + ex.getMessage()));
		}
		return "redirect:" + backTo(request);
	}

	@PostMapping("/update")
	Object update(@RequestParam String version, HttpServletRequest request, RedirectAttributes redirect) {
		try {
			this.updates.install(version);
		}
		catch (UpdateException ex) {
			logger.warn("[cozy] 업데이트를 설치하지 않았습니다: " + ex.getMessage(), ex.getCause());
			redirect.addFlashAttribute(FLASH_ATTRIBUTE, new Notice("danger", ex.getMessage()));
			return "redirect:" + backTo(request);
		}
		this.shutdown.request("업데이트 " + version + " 설치", 0);
		return page("업데이트를 설치합니다",
				"앱을 종료하고 v" + version + "으로 바꿉니다. 잠시 뒤 새 버전이 열립니다. 이 탭은 닫아도 됩니다.");
	}

	/** 보던 화면의 경로. 다른 출처로 가지 않도록 Referer의 경로와 질의만 쓴다. */
	static String backTo(HttpServletRequest request) {
		String referer = request.getHeader("Referer");
		if (referer != null) {
			try {
				URI uri = URI.create(referer);
				String path = uri.getRawPath();
				if (path != null && path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/cozy/")) {
					return (uri.getRawQuery() != null) ? path + "?" + uri.getRawQuery() : path;
				}
			}
			catch (IllegalArgumentException ex) {
				// 형식이 틀린 Referer는 무시한다
			}
		}
		return "/";
	}

	private static ResponseEntity<String> page(String title, String message) {
		String html = """
				<!DOCTYPE html>
				<html lang="ko">
				<head><meta charset="utf-8"><title>%s</title></head>
				<body>
				<main>
				<h1>%s</h1>
				<p>%s</p>
				</main>
				</body>
				</html>
				""".formatted(HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(message));
		return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)).body(html);
	}

	/**
	 * 플래시 알림. cozy-ui의 {@code CozyFlash}와 같은 모양이라 app-shell이 그대로 그린다.
	 * @param kind success, info, warning, danger
	 * @param message 문장
	 */
	public record Notice(String kind, @Nullable String message) {
	}
}
