package io.salvia95.cozy.rules;

import java.nio.file.Path;
import java.util.List;

import io.salvia95.cozy.rules.internal.WiringChecker;

/**
 * 검사 C13: 동작 명세의 컴포넌트가 화면에 이어졌는가. 동작 명세에서 만든 JS({@link BehaviorRules})의 {@code cozy:component} 줄을 앱 템플릿과
 * 서버의 {@code HX-Trigger}({@link ServerEvents})에 맞춘다. 오류인 경우:
 *
 * <ul>
 * <li>컴포넌트를 어느 앱 템플릿에도 붙이지 않았다({@code x-data})</li>
 * <li>동작을 부르는 요소가 없고, 받는 화면 이벤트에도 잇지 않았다</li>
 * <li>내는 화면 이벤트({@code emit})를 받는 곳이 없다: 컴포넌트의 {@code listens}, 템플릿의 {@code hx-trigger}와 {@code x-on:모듈:이름}</li>
 * <li>받는 화면 이벤트({@code listens})를 내는 곳이 없다: 컴포넌트의 {@code emit}, 서버의 {@code HX-Trigger}</li>
 * </ul>
 *
 * <p>위치는 동작 명세({@code src/main/resources/behaviors/{모듈}.yml})의 컴포넌트 줄이다. 손으로 쓴 JS의 컴포넌트는 보지 않는다(C12 경고).
 */
public final class BehaviorWiring {

	public static final String RULE = "C13";

	private BehaviorWiring() {
	}

	/**
	 * @param templates 앱 템플릿 (프레임워크 템플릿은 건너뛴다)
	 * @param scripts 앱 JS (프레임워크 JS는 건너뛴다)
	 * @param main 앱의 {@code src/main}. 명세의 줄과 서버의 {@code HX-Trigger}를 읽는다
	 * @param displayPrefix 위치 앞에 붙일 경로 ({@code src/main/})
	 * @return 명세 순서
	 */
	public static List<Violation> check(List<TemplateFile> templates, List<ScriptFile> scripts, Path main, String displayPrefix) {
		return WiringChecker.check(templates, scripts, main, displayPrefix);
	}
}
