package io.salvia95.cozy.rules;

import java.util.regex.Pattern;

/**
 * Cozy가 쓰는 Alpine.js 속성 이름. 템플릿 규칙(R16)과 렌더링 결과의 HTML 검사(C4)가 같은 목록을 쓴다.
 *
 * <p>긴 이름만 받는다({@code x-on:click}, {@code x-bind:class}). 줄임 표기({@code @click}, {@code :class}), {@code x-html},
 * {@code x-teleport}와 목록에 없는 {@code x-*}는 받지 않는다.
 */
public final class AlpineAttributes {

	/** 쓰는 디렉티브. 수식어({@code .number}, {@code .window})와 인자({@code :click})를 포함한다 */
	private static final Pattern DIRECTIVE = Pattern.compile("x-(?:data|init|show|text|ref|cloak|if|for|id|effect"
			+ "|ignore(?:\\.self)?|model(?:\\.[a-z0-9.]+)?|transition(?:[:.][a-z0-9.:-]+)?|on:[a-z0-9.:-]+|bind:[a-z0-9.:-]+)");

	/** 사람에게 보일 목록 */
	public static final String LIST = "x-data, x-init, x-show, x-bind:*, x-on:*, x-model, x-text, x-ref, x-cloak, "
			+ "x-transition, x-if, x-for, x-id, x-effect, x-ignore";

	private AlpineAttributes() {
	}

	/** @param name 속성 이름 (소문자) */
	public static boolean allowed(String name) {
		return DIRECTIVE.matcher(name).matches();
	}
}
