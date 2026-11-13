package io.salvia95.cozy.ui.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * 앱 모듈의 Alpine 컴포넌트 파일. 모듈마다 {@code static/{모듈}/{모듈}.js} 하나이고, 레이아웃이 Alpine보다 먼저 싣는다.
 *
 * <p>Spring Boot는 {@code static/}을 앱의 맨 위 경로에서 주지만, 그 자리({@code /memo/memo.js})는 모듈의 화면 경로({@code /memo/{id}})와
 * 겹친다. 그래서 {@value #PATH} 아래에서 준다. 폴더 이름과 파일 이름이 다른 파일은 모듈 파일이 아니므로 싣지 않는다.
 */
public final class CozyModuleScripts {

	/** 모듈 파일을 주는 경로 */
	public static final String PATH = "/cozy/modules/";

	private static final Pattern MODULE_FILE = Pattern.compile("/static/([a-z][a-z0-9]*)/\\1\\.js$");

	private final List<String> paths;

	public CozyModuleScripts(ResourcePatternResolver resources) {
		TreeSet<String> found = new TreeSet<>();
		try {
			for (Resource resource : resources.getResources("classpath*:static/*/*.js")) {
				Matcher m = MODULE_FILE.matcher(resource.getURL().toString());
				if (m.find()) {
					found.add(PATH + m.group(1) + "/" + m.group(1) + ".js");
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 모듈의 static/{모듈}/{모듈}.js를 찾지 못했습니다", ex);
		}
		this.paths = List.copyOf(found);
	}

	/** 실을 경로 (모듈 이름 순서) */
	public List<String> paths() {
		return this.paths;
	}
}
