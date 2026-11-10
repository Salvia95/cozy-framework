package io.salvia95.cozy.ui.catalog;

import java.util.List;

/**
 * 앱의 화면 스펙을 주는 곳. 패턴 카탈로그가 화면 미리보기를 그릴 때마다 묻는다.
 *
 * <p>
 * 기본 구현은 클래스패스의 {@code specs/*.yml}을 기동할 때 한 번 읽는다. 다른 빈을 등록하면 그것을 쓴다(워크벤치는 소스 폴더의 스펙을 읽어, 고친
 * 값이 다시 빌드하지 않아도 보이게 한다).
 */
@FunctionalInterface
public interface ScreenSpecs {

	/** 화면 스펙 (이름 순서) */
	List<ScreenSpec> all();
}
