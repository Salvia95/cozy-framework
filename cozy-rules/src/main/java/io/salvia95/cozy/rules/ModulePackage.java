package io.salvia95.cozy.rules;

/**
 * 앱 모듈 하나의 이름과 기준 패키지 (Spring Modulith의 모듈).
 *
 * @param name 모듈 이름 ({@code memo})
 * @param basePackage 기준 패키지 ({@code it.cozy.app.memo})
 */
public record ModulePackage(String name, String basePackage) {

	/** 이 모듈 아래의 자리 ({@code it.cozy.app.memo.internal.app.command}) */
	public String of(Layer layer) {
		return layer.packageIn(this.basePackage);
	}

	/** 이 모듈 아래의 패키지. 자리가 아닌 중간 패키지({@code internal}, {@code internal.app})에 쓴다 */
	public String sub(String path) {
		return this.basePackage + "." + path;
	}
}
