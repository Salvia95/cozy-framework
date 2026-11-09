package io.salvia95.cozy.test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C1(Spring Modulith) 위반에 고치는 cozy 명령을 붙인다. 다른 모듈의 spi를 쓰는데 의존 선언이 빠졌으면
 * {@code cozy link}가 고친다. 다른 모듈의 internal을 쓰거나({@code depends on module 'b' via …}) 알아볼 수 없는 메시지는 명령을
 * 붙이지 않는다(추측하지 않는다).
 */
final class ModuleHints {

	/**
	 * Modulith 2.x가 선언하지 않은 named interface 의존을 알리는 형식:
	 * {@code Module 'a' depends on named interface(s) 'b :: spi' via x.A -> y.B. Allowed targets: none.}
	 */
	private static final Pattern UNDECLARED_SPI = Pattern
		.compile("Module '([a-z][a-z0-9]*)' depends on named interface\\(s\\) '([a-z][a-z0-9]*) :: spi' via ");

	private ModuleHints() {
	}

	/**
	 * @param message {@code ApplicationModules.verify()}가 던진 메시지
	 * @return 고치는 명령들 (중복 없이, 나온 순서대로)
	 */
	static List<String> commands(String message) {
		Set<String> commands = new LinkedHashSet<>();
		Matcher m = UNDECLARED_SPI.matcher((message != null) ? message : "");
		while (m.find()) {
			commands.add("cozy link " + m.group(1) + " --uses " + m.group(2));
		}
		return List.copyOf(commands);
	}
}
