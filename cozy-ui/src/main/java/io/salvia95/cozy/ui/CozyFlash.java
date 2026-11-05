package io.salvia95.cozy.ui;

/**
 * 리다이렉트 뒤 한 번 보여 줄 알림 (PRD §6.4 alert). app-shell이 모델의 {@code cozyFlash}를 그린다.
 *
 * <pre>{@code
 * redirectAttributes.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("저장했습니다"));
 * }</pre>
 *
 * @param kind Tabler 알림 종류: success, info, warning, danger
 * @param message 표시할 문장
 */
public record CozyFlash(String kind, String message) {

	public static final String ATTRIBUTE = "cozyFlash";

	public static CozyFlash success(String message) {
		return new CozyFlash("success", message);
	}

	public static CozyFlash info(String message) {
		return new CozyFlash("info", message);
	}

	public static CozyFlash warning(String message) {
		return new CozyFlash("warning", message);
	}

	public static CozyFlash danger(String message) {
		return new CozyFlash("danger", message);
	}
}
