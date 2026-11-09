package io.salvia95.cozy.ui;

/**
 * 사이드 내비 항목. 모듈이 이 타입의 빈을 등록하면 app-shell이 {@code order}, {@code label} 순으로 그린다.
 *
 * <pre>{@code
 * @Bean
 * CozyNavItem memoNav() {
 *     return new CozyNavItem("메모", "/memo", 10, "notes");
 * }
 * }</pre>
 *
 * @param label 표시 이름
 * @param path 이동할 경로. 요청 경로가 이 경로로 시작하면 활성으로 표시한다.
 * @param order 작을수록 위
 * @param icon Tabler Icons 외곽선 아이콘 이름 ({@link CozyIcons}). 없는 이름이면 기동이 실패한다.
 */
public record CozyNavItem(String label, String path, int order, String icon) {

	public CozyNavItem {
		if (label == null || label.isBlank() || path == null || !path.startsWith("/")) {
			throw new IllegalArgumentException("[cozy] CozyNavItem은 이름과 '/'로 시작하는 경로가 필요합니다: " + label + ", " + path);
		}
		CozyIcons.requireOutline(icon);
	}
}
