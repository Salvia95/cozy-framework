package io.salvia95.cozy.ui;

import java.util.Set;

/**
 * 리다이렉트 뒤 한 번 보여 줄 알림. 패턴이 page-body 맨 위에 모델의 {@code cozyFlash}를 그린다. 만들기·수정·삭제 결과는 이것으로
 * 알린다 (docs/conventions/design.md).
 *
 * <pre>{@code
 * redirectAttributes.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 만들었습니다"));
 * }</pre>
 *
 * @param kind 알림 tone: info, success, warning, critical
 * @param message 표시할 문장
 */
public record CozyFlash(String kind, String message) {

	public static final String ATTRIBUTE = "cozyFlash";

	private static final Set<String> KINDS = Set.of("info", "success", "warning", "critical");

	public CozyFlash {
		if (!KINDS.contains(kind)) {
			throw new IllegalArgumentException("알림 종류 '" + kind + "'는 없습니다. info, success, warning, critical 중 하나입니다.");
		}
	}

	public static CozyFlash success(String message) {
		return new CozyFlash("success", message);
	}

	public static CozyFlash info(String message) {
		return new CozyFlash("info", message);
	}

	public static CozyFlash warning(String message) {
		return new CozyFlash("warning", message);
	}

	/** 오류, 할 수 없음, 되돌릴 수 없는 결과 */
	public static CozyFlash critical(String message) {
		return new CozyFlash("critical", message);
	}
}
