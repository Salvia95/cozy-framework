package io.salvia95.cozy.ui.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * 앱 모듈의 Alpine 컴포넌트. 모듈마다 {@code static/{모듈}/{모듈}.js} 하나이고, 레이아웃은 그것들을 모듈 이름 순서로 이은 묶음 하나를
 * Alpine보다 먼저 싣는다({@value #PATH}). 화면마다 요청이 한 번이고, 주소에 내용의 해시를 붙여 오래 캐시한다.
 *
 * <p>폴더 이름과 파일 이름이 다른 파일은 모듈 파일이 아니므로 넣지 않는다. 각 부분 앞에 원래 파일 이름을 주석으로 둔다(브라우저 오류의 줄
 * 번호는 묶음 기준이다). 파일이 빌드 결과 폴더에 있으면(개발 중) 부를 때마다 다시 읽고, jar 안이면 처음 한 번만 읽는다.
 */
public final class CozyModuleScripts {

	/** 묶음을 주는 경로 */
	public static final String PATH = "/cozy/modules.js";

	private static final Pattern MODULE_FILE = Pattern.compile("/static/([a-z][a-z0-9]*)/\\1\\.js$");

	/**
	 * 이은 내용.
	 *
	 * @param content 이은 JavaScript
	 * @param version 내용의 SHA-256 앞 12자 (파일이 없으면 빈 문자열)
	 * @param files 넣은 파일 ({@code static/memo/memo.js}, 모듈 이름 순서)
	 */
	public record Bundle(String content, String version, List<String> files, String parts) {
	}

	/**
	 * 레이아웃이 싣는 스크립트.
	 *
	 * @param src 주소 ({@code /cozy/modules.js?v=…})
	 * @param parts 부분마다 원래 파일과 묶음에서의 첫 줄({@code static/memo/memo.js:2;static/wiki/wiki.js:40}). 브라우저 오류의 줄을 원래
	 * 파일의 줄로 바꾸는 데 쓴다
	 */
	public record Script(String src, String parts) {
	}

	private final ResourcePatternResolver resources;

	private final boolean reload;

	private volatile Bundle bundle;

	public CozyModuleScripts(ResourcePatternResolver resources) {
		this.resources = resources;
		this.reload = inBuildFolder(resources);
		this.bundle = read();
	}

	/** 레이아웃이 싣는 스크립트. 모듈 파일이 없으면 {@code null} */
	public @Nullable Script script() {
		Bundle current = bundle();
		return current.files().isEmpty() ? null : new Script(PATH + "?v=" + current.version(), current.parts());
	}

	public Bundle bundle() {
		if (this.reload) {
			this.bundle = read();
		}
		return this.bundle;
	}

	private static boolean inBuildFolder(ResourcePatternResolver resources) {
		Resource root = resources.getResource("classpath:static/");
		try {
			return root.exists() && root.getURL().getProtocol().equals("file");
		}
		catch (IOException ex) {
			return false;
		}
	}

	private Bundle read() {
		Map<String, Resource> found = new TreeMap<>();
		try {
			for (Resource resource : this.resources.getResources("classpath*:static/*/*.js")) {
				Matcher m = MODULE_FILE.matcher(resource.getURL().toString());
				if (m.find()) {
					found.putIfAbsent(m.group(1), resource);
				}
			}
			StringBuilder content = new StringBuilder();
			List<String> parts = new java.util.ArrayList<>();
			int line = 1;
			for (Map.Entry<String, Resource> entry : found.entrySet()) {
				String file = "static/" + entry.getKey() + "/" + entry.getKey() + ".js";
				String part;
				try (InputStream in = entry.getValue().getInputStream()) {
					part = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
				if (!part.endsWith("\n")) {
					part = part + "\n";
				}
				parts.add(file + ":" + (line + 1));
				// 끝의 ;는 다음 파일의 첫 줄이 앞 파일의 끝과 이어 읽히지 않게 한다
				content.append("/* ").append(file).append(" */\n").append(part).append(";\n");
				line += 2 + (int) part.chars().filter((c) -> c == '\n').count();
			}
			List<String> files = found.keySet().stream().map((m) -> "static/" + m + "/" + m + ".js").toList();
			String text = content.toString();
			return new Bundle(text, files.isEmpty() ? "" : hash(text), files, String.join(";", parts));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[cozy] 모듈의 static/{모듈}/{모듈}.js를 읽지 못했습니다", ex);
		}
	}

	private static String hash(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).substring(0, 12);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
