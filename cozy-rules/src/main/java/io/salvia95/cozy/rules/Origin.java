package io.salvia95.cozy.rules;

/** 템플릿을 어디서 읽었는가. */
public enum Origin {

	/** 앱의 {@code src/main/resources/templates} */
	APP,

	/** 프레임워크 jar(cozy-ui)의 {@code templates} */
	FRAMEWORK

}
