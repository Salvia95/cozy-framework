package io.salvia95.cozy.ui.internal;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 앱 모듈의 Alpine 컴포넌트 묶음({@link CozyModuleScripts}). 주소의 {@code v}가 지금 내용의 해시와 같으면 1년 동안 바뀌지 않는 것으로
 * 캐시하게 하고, 다르거나 없으면 캐시하지 않게 지금 내용을 준다.
 */
@org.springframework.stereotype.Controller
public class CozyModuleScriptsController {

	private static final MediaType JAVASCRIPT = new MediaType("text", "javascript", java.nio.charset.StandardCharsets.UTF_8);

	private final CozyModuleScripts scripts;

	public CozyModuleScriptsController(CozyModuleScripts scripts) {
		this.scripts = scripts;
	}

	@GetMapping(CozyModuleScripts.PATH)
	@ResponseBody
	ResponseEntity<String> bundle(@RequestParam(name = "v", required = false) @Nullable String version) {
		CozyModuleScripts.Bundle bundle = this.scripts.bundle();
		CacheControl cache = (!bundle.version().isEmpty() && bundle.version().equals(version))
				? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable() : CacheControl.noCache();
		return ResponseEntity.ok().contentType(JAVASCRIPT).cacheControl(cache).body(bundle.content());
	}
}
