package io.salvia95.cozy.rules;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	/**
	 * 동작을 붙이던 프레임워크 속성 → 고치는 법. 동작은 cozy 컴포넌트로 옮겼다. {@code cozy migrate alpine}이 바꾸는 것은
	 * {@link #MIGRATED}에 있다. 값을 담기만 하는 {@code data-cozy-*}(워크벤치의 {@code data-cozy-line} 등)는 여기 없다.
	 */
	private static final Map<String, String> REMOVED = Map.of(
			"data-cozy-confirm", "폼 제출 전 확인은 cozyConfirm 컴포넌트로 옮겼습니다: "
					+ "x-data=\"cozyConfirm\" x-on:submit=\"ask\" data-confirm-message=\"…\"",
			"data-cozy-copy", "복사는 cozyCopy 컴포넌트로 옮겼습니다: x-data=\"cozyCopy\" x-on:click=\"copy\" data-copy-text=\"…\"",
			"data-cozy-autosize", "미리보기 높이는 cozyAutoHeight 컴포넌트로 옮겼습니다. iframe에는 Alpine 식을 달 수 없으므로 감싸는 요소에 "
					+ "x-data=\"cozyAutoHeight\" x-on:load.capture=\"fit\"을 다세요",
			"data-cozy-icon-filter", "아이콘 거르기는 cozyIconList 컴포넌트로 옮겼습니다. 입력과 목록을 감싸는 요소에 x-data=\"cozyIconList\", "
					+ "입력에 x-on:input=\"filter\", 목록에 x-on:click=\"copy\"를 다세요");

	/** {@code cozy migrate alpine}이 같은 뜻의 cozy 컴포넌트로 바꾸는 속성 */
	public static final Set<String> MIGRATED = Set.of("data-cozy-confirm", "data-cozy-copy");

	private AlpineAttributes() {
	}

	/**
	 * 없앤 동작 속성인가.
	 *
	 * @param name 속성 이름 (소문자, {@code th:}나 {@code data-th-} 없이)
	 * @return 고치는 법. 없앤 속성이 아니면 비어 있다
	 */
	public static Optional<String> removed(String name) {
		String guide = REMOVED.get(name);
		if (guide == null) {
			return Optional.empty();
		}
		return Optional.of(guide + (MIGRATED.contains(name) ? ". cozy migrate alpine이 바꿉니다." : ". 명령이 바꾸지 않으니 직접 고치세요."));
	}

	/** @param name 속성 이름 (소문자) */
	public static boolean allowed(String name) {
		return DIRECTIVE.matcher(name).matches();
	}
}
