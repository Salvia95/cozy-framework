package io.salvia95.cozy.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tabler Icons (MIT) 이름 목록. 화면에서는 {@code ~{cozy/icon/tabler :: icon('plus')}}로 쓴다.
 *
 * <p>cozy-ui jar에 든 스프라이트에서 이름을 읽는다. 템플릿에 글자로 쓴 이름은 규칙 R13이, Java에서 넘기는 이름은
 * 이 클래스를 쓰는 쪽(예: {@link CozyNavItem})이 확인한다.
 */
public final class CozyIcons {

	/** Tabler Icons 버전 (cozy-ui 벤더 자산) */
	public static final String VERSION = "3.50.0";

	/** 외곽선 아이콘 스프라이트의 정적 경로 */
	public static final String OUTLINE_SPRITE = "/cozy/vendor/tabler-icons/" + VERSION + "/tabler-sprite.svg";

	/** 채운 아이콘 스프라이트의 정적 경로 */
	public static final String FILLED_SPRITE = "/cozy/vendor/tabler-icons/" + VERSION + "/tabler-sprite-filled.svg";

	private static final Pattern OUTLINE = Pattern.compile("<symbol id=\"tabler-(?!filled-)([a-z0-9-]+)\"");

	private static final Pattern FILLED = Pattern.compile("<symbol id=\"tabler-filled-([a-z0-9-]+)\"");

	private static volatile Set<String> outline;

	private static volatile Set<String> filled;

	private CozyIcons() {
	}

	/** {@code icon('이름')}에 쓸 수 있는 이름 */
	public static Set<String> outline() {
		Set<String> names = outline;
		if (names == null) {
			Set<String> read = new TreeSet<>(read(OUTLINE_SPRITE, OUTLINE));
			// 외곽선 'icons'의 심볼 id(tabler-icons)는 스프라이트 루트 <svg id="tabler-icons">와 겹쳐 그려지지 않는다
			read.remove("icons");
			names = Set.copyOf(read);
			outline = names;
		}
		return names;
	}

	/** {@code iconFilled('이름')}에 쓸 수 있는 이름 */
	public static Set<String> filled() {
		Set<String> names = filled;
		if (names == null) {
			names = read(FILLED_SPRITE, FILLED);
			filled = names;
		}
		return names;
	}

	/**
	 * @return 외곽선 아이콘에 그 이름이 있으면 그대로
	 * @throws IllegalArgumentException 없는 이름
	 */
	public static String requireOutline(String name) {
		if (name == null || !outline().contains(name)) {
			throw new IllegalArgumentException("[cozy] Tabler Icons에 없는 아이콘입니다: " + name
					+ ". 이름은 tabler.io/icons(오프라인이면 패턴 카탈로그의 icon)에서 찾습니다.");
		}
		return name;
	}

	private static Set<String> read(String sprite, Pattern symbol) {
		String resource = "META-INF/resources" + sprite;
		try (InputStream in = CozyIcons.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("[cozy] 아이콘 스프라이트가 클래스패스에 없습니다: " + resource);
			}
			Matcher m = symbol.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			Set<String> names = new TreeSet<>();
			while (m.find()) {
				names.add(m.group(1));
			}
			return Set.copyOf(names);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
