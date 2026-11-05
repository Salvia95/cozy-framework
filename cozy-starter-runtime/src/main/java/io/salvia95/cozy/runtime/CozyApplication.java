package io.salvia95.cozy.runtime;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import io.salvia95.cozy.runtime.internal.DesktopLauncher;

/**
 * Cozy 앱의 진입점 (PRD §9). 앱의 main은 한 줄이다.
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     CozyApplication.run(App.class, args);
 * }
 * }</pre>
 *
 * <p>단일 인스턴스 잠금, 빈 포트 기동, 포트 파일, 브라우저 열기를 맡는다. 나머지(데이터 경로, 외부 설정,
 * 127.0.0.1 고정, 로그, Host 필터, CSRF)는 자동 설정이 맡으므로 테스트 컨텍스트에도 똑같이 적용된다.
 */
public final class CozyApplication {

	private CozyApplication() {
	}

	/**
	 * 앱을 실행한다.
	 * @return 실행한 컨텍스트. 이미 다른 인스턴스가 실행 중이어서 그 화면을 열고 끝냈으면 {@code null}
	 */
	public static @Nullable ConfigurableApplicationContext run(Class<?> appClass, String... args) {
		SpringApplication application = new SpringApplication(appClass);
		// 브라우저 열기(Desktop)와 트레이(M6)에 AWT가 필요하다
		application.setHeadless(false);
		application.addListeners(new DesktopLauncher());
		try {
			return application.run(args);
		}
		catch (DesktopLauncher.AlreadyRunningException ex) {
			return null;
		}
	}
}
