package io.salvia95.cozy.rules.internal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.rules.AlpineAttributes;
import io.salvia95.cozy.rules.ModuleModel;
import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.ScriptFile;
import io.salvia95.cozy.rules.Severity;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.Violation;
import io.salvia95.cozy.rules.internal.ParsedTemplate.AlpineAttribute;
import io.salvia95.cozy.rules.internal.RuleChecker.Place;

/**
 * R16: Alpine.js 속성. 화면은 Alpine의 CSP 빌드를 쓴다(CSP가 eval을 막는다). 그래서 템플릿은 그 빌드가 읽을 수 있는 모양으로만 쓴다.
 *
 * <ul>
 * <li>속성은 긴 이름만 쓴다({@code x-on:click}, {@code x-bind:class}). 줄임 표기({@code @click}, {@code :class})는 쓰지 않는다.</li>
 * <li>{@code x-data}에는 JS 파일이 {@code Alpine.data('이름', …)}로 등록한 컴포넌트 이름만 쓴다. 앱 템플릿은 프레임워크
 * 컴포넌트와 자기 모듈의 컴포넌트만 쓴다.</li>
 * <li>쓰는 디렉티브는 정해진 목록이다. {@code x-html}과 {@code x-teleport}는 쓰지 않는다.</li>
 * <li>Thymeleaf로 Alpine 속성을 만들지 않는다({@code th:attr}, {@code th:x-*}). 서버 값은 {@code data-*}로 넘긴다.</li>
 * <li>CSP 빌드가 읽지 못하는 식(화살표 함수, 템플릿 문자열, 전개, 속성 대입, 전역 이름)을 쓰지 않는다.</li>
 * <li>JS 파일의 등록: 이름은 글자로 쓴다. 앱 컴포넌트는 {@code static/{모듈}/}에 두고 이름은 모듈 이름으로 시작한다.</li>
 * </ul>
 */
final class AlpineChecker {

	private static final String RULE = "R16";

	/** 식이 아닌 값을 받는 디렉티브: 이름, 클래스 목록, 값 없음 */
	private static final Pattern NOT_EXPRESSION = Pattern.compile("x-(?:data|ref|cloak|ignore.*|transition.*)");

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

	/** CSP 빌드가 읽지 못하는 전역 이름 */
	private static final Set<String> GLOBALS = Set.of("window", "document", "globalThis", "self", "console", "Math",
			"JSON", "Object", "Array", "Number", "String", "Date", "parseInt", "parseFloat", "fetch", "XMLHttpRequest",
			"eval", "Function", "setTimeout", "setInterval", "localStorage", "sessionStorage", "location", "navigator",
			"history", "alert", "confirm", "prompt", "htmx", "Alpine");

	private static final Pattern STRING = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"");

	private static final Pattern WORD = Pattern.compile("(?<![.$\\w])[A-Za-z_$][A-Za-z0-9_$]*");

	/** 속성 대입: {@code a.b = 1}, {@code a[0] += 1}, {@code a.b++} */
	private static final Pattern PROPERTY_ASSIGNMENT = Pattern
		.compile("[A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*|\\[[^\\]]*\\])+\\s*(?:=(?![=>])|[-+*/%]=|\\+\\+|--)");

	/** {@code th:attr}의 대상 중 Alpine 속성 */
	private static final Pattern TH_ATTR_TARGET = Pattern.compile("(?:^|,)\\s*(x-[a-z0-9:.-]*|@[a-z0-9:.-]*|:[a-z0-9:.-]*)\\s*=");

	private static final Pattern REGISTRATION = Pattern.compile("Alpine\\s*\\.\\s*data\\s*\\(\\s*");

	private static final Pattern LITERAL_NAME = Pattern.compile("(['\"])([A-Za-z_$][A-Za-z0-9_$]*)\\1");

	private static final Set<String> RESERVED = Set.of("cozy", "fragments", "error");

	/** 등록된 컴포넌트 하나 */
	private record Component(String name, ScriptFile script) {
	}

	private final ModuleModel modules;

	private final List<Violation> violations;

	/** 이름 → 등록 (프레임워크와 앱) */
	private final Map<String, Component> components = new LinkedHashMap<>();

	private boolean frameworkScripts;

	private AlpineChecker(ModuleModel modules, List<Violation> violations) {
		this.modules = modules;
		this.violations = violations;
	}

	/** 스캐너가 모을 속성인가 */
	static boolean relevant(String name, String thName) {
		if (name.startsWith("x-") || name.startsWith("@") || name.startsWith(":")) {
			return true;
		}
		return thName != null && (thName.startsWith("x-") || thName.equals("attr") || thName.equals("attrappend")
				|| thName.equals("attrprepend"));
	}

	static void check(List<ScriptFile> scripts, List<ParsedTemplate> templates, ModuleModel modules,
			List<Violation> violations) {
		AlpineChecker checker = new AlpineChecker(modules, violations);
		scripts.stream()
			.sorted(Comparator.comparing((ScriptFile s) -> s.origin() == Origin.APP).thenComparing(ScriptFile::path))
			.forEach(checker::register);
		for (ParsedTemplate template : templates) {
			for (AlpineAttribute attribute : template.alpine) {
				checker.attribute(template.file, attribute);
			}
		}
	}

	// ── JS 파일의 등록 ────────────────────────────────────────────

	private void register(ScriptFile script) {
		this.frameworkScripts |= script.origin() == Origin.FRAMEWORK;
		Matcher call = REGISTRATION.matcher(script.content());
		while (call.find()) {
			int line = lineOf(script.content(), call.start());
			Matcher literal = LITERAL_NAME.matcher(script.content()).region(call.end(), script.content().length());
			if (!literal.lookingAt()) {
				scriptError(script, line, "Alpine.data(…)", "컴포넌트 이름을 변수로 등록하면 템플릿의 x-data를 검사할 수 없습니다. "
						+ "이름을 글자로 쓰세요 (예: Alpine.data('memoEditor', …)).");
				continue;
			}
			String name = literal.group(2);
			if (script.origin() == Origin.APP && !appRegistration(script, line, name)) {
				continue;
			}
			Component existing = this.components.get(name);
			if (existing != null) {
				scriptError(script, line, name, "'" + name + "'는 이미 " + existing.script().location()
						+ "에서 등록했습니다. 다른 이름을 쓰세요.");
				continue;
			}
			this.components.put(name, new Component(name, script));
		}
	}

	/** @return 앱 컴포넌트로 받아들이면 true */
	private boolean appRegistration(ScriptFile script, int line, String name) {
		String module = moduleOf(script);
		if (module == null) {
			scriptError(script, line, name, "앱 컴포넌트는 모듈 폴더 static/{모듈}/{모듈}.js에 둡니다. 모듈 이름("
					+ String.join(", ", this.modules.modules().keySet().stream().sorted().toList()) + ")의 폴더로 옮기세요.");
			return false;
		}
		if (name.startsWith("cozy")) {
			scriptError(script, line, name, "cozy로 시작하는 이름은 프레임워크 컴포넌트입니다. 이름을 모듈 이름으로 시작하세요 (예: "
					+ module + "Editor).");
			return false;
		}
		if (!ownedBy(name, module)) {
			scriptError(script, line, name, module + " 모듈의 컴포넌트 이름은 '" + module + "'로 시작합니다 (예: " + module
					+ capitalize(name) + ").");
			return false;
		}
		return true;
	}

	// ── 템플릿의 속성 ─────────────────────────────────────────────

	private void attribute(TemplateFile file, AlpineAttribute attribute) {
		String name = attribute.name();
		String display = name + (attribute.value().isEmpty() ? "" : "=\"" + attribute.value() + "\"");
		if (name.startsWith("@") || name.startsWith(":")) {
			String longName = (name.startsWith("@") ? "x-on:" : "x-bind:") + name.substring(1);
			error(file, attribute.line(), display, "줄임 표기는 쓰지 않습니다. " + longName + "처럼 긴 이름으로 쓰세요.");
			return;
		}
		if (!name.startsWith("x-")) {
			thymeleaf(file, attribute, display);
			return;
		}
		if (name.equals("x-html")) {
			error(file, attribute.line(), display, "x-html은 서버를 거치지 않은 HTML을 넣습니다(CSP 빌드도 지원하지 않습니다). 글자는 "
					+ "x-text로, 서버가 그린 HTML은 htmx로 넣으세요.");
			return;
		}
		if (name.startsWith("x-teleport")) {
			error(file, attribute.line(), display, "x-teleport는 요소를 템플릿 위치 밖으로 옮겨 htmx 대상과 어긋납니다. 모달은 "
					+ "cozy 컴포넌트(Tabler 모달)를 쓰세요.");
			return;
		}
		if (!AlpineAttributes.allowed(name)) {
			error(file, attribute.line(), display, "Cozy가 쓰지 않는 Alpine 속성입니다. 쓸 수 있는 것: " + AlpineAttributes.LIST
					+ " (docs/conventions/ui.md).");
			return;
		}
		if (name.equals("x-data")) {
			data(file, attribute, display);
			return;
		}
		if (!NOT_EXPRESSION.matcher(name).matches()) {
			if ("iframe".equals(attribute.element()) || "script".equals(attribute.element())) {
				error(file, attribute.line(), display, "Alpine CSP 빌드는 " + attribute.element() + " 요소에서 식을 실행하지 않고, 그 컴포넌트의 나머지 초기화도 멈춥니다. "
						+ "load는 감싸는 요소에서 x-on:load.capture로 받고, src 같은 값은 컴포넌트의 메서드가 정하세요.");
				return;
			}
			expression(file, attribute, display);
		}
	}

	private void thymeleaf(TemplateFile file, AlpineAttribute attribute, String display) {
		String thName = attribute.name().startsWith("th:") ? attribute.name().substring(3) : attribute.name().substring(8);
		boolean builds = thName.startsWith("x-") || TH_ATTR_TARGET.matcher(attribute.value()).find();
		if (builds) {
			error(file, attribute.line(), display, "Thymeleaf로 Alpine 속성을 만들지 않습니다. 사용자 입력이 식으로 실행될 수 있습니다. "
					+ "서버 값은 data-* 속성으로 넘기고(th:data-title=\"${…}\") 컴포넌트가 this.$el.dataset에서 읽게 하세요.");
		}
	}

	private void data(TemplateFile file, AlpineAttribute attribute, String display) {
		String value = attribute.value().strip();
		if (!IDENTIFIER.matcher(value).matches()) {
			error(file, attribute.line(), display, "x-data에는 등록된 컴포넌트 이름만 씁니다 (예: x-data=\"cozyDisclosure\"). 상태와 "
					+ "메서드는 JS 파일의 Alpine.data('이름', …)에 두고, 서버 값은 data-* 속성으로 넘기세요.");
			return;
		}
		Component component = this.components.get(value);
		String module = moduleOf(file);
		boolean usable = component != null && ((component.script().origin() == Origin.FRAMEWORK
				&& (value.startsWith("cozy") || file.origin() == Origin.FRAMEWORK))
				|| (file.origin() == Origin.APP && module != null && module.equals(moduleOf(component.script()))));
		if (usable) {
			return;
		}
		if (component != null && component.script().origin() == Origin.FRAMEWORK) {
			// 프레임워크 화면(워크벤치 등)만 쓰는 컴포넌트: 앱이 실행할 때는 없을 수 있다
			error(file, attribute.line(), display, "'" + value + "'는 프레임워크 화면 전용 컴포넌트입니다. 앱 템플릿은 cozy 컴포넌트만 씁니다"
					+ ((module != null) ? "(그리고 " + module + " 모듈의 컴포넌트)" : "") + ". 같이 쓸 동작이면 cozy 컴포넌트로 요청하세요.");
			return;
		}
		if (component != null) {
			String owner = moduleOf(component.script());
			error(file, attribute.line(), display, "'" + value + "'는 " + owner + " 모듈의 컴포넌트입니다. "
					+ ((module != null) ? module + " 템플릿은 cozy 컴포넌트와 " + module + " 모듈의 컴포넌트만 씁니다. "
							: "이 템플릿은 cozy 컴포넌트만 씁니다. ")
					+ "같이 쓸 동작이면 cozy 컴포넌트로 요청하세요.");
			return;
		}
		if (!this.frameworkScripts) {
			error(file, attribute.line(), display, "컴포넌트 목록이 없어 '" + value + "'를 확인할 수 없습니다. cozy-ui가 클래스패스에 "
					+ "있어야 합니다.");
			return;
		}
		String similar = this.components.keySet()
			.stream()
			.filter((n) -> RuleChecker.distance(n, value) <= 3)
			.sorted(Comparator.comparingInt((String n) -> RuleChecker.distance(n, value)).thenComparing((n) -> n))
			.limit(3)
			.reduce((a, b) -> a + ", " + b)
			.map((s) -> " 비슷한 이름: " + s + ".")
			.orElse("");
		String where = (module != null) ? "static/" + module + "/" + module + ".js" : "JS 파일";
		error(file, attribute.line(), display, "등록된 컴포넌트 '" + value + "'가 없습니다." + similar + " 새 컴포넌트는 " + where
				+ "에 Alpine.data('" + value + "', …)로 등록합니다.");
	}

	private void expression(TemplateFile file, AlpineAttribute attribute, String display) {
		String value = attribute.value();
		if (attribute.name().equals("x-for")) {
			int in = value.indexOf(" in ");
			value = (in >= 0) ? value.substring(in + 4) : value;
		}
		if (value.indexOf('`') >= 0) {
			unsupported(file, attribute, display, "템플릿 문자열(`…`)", "글자는 +로 잇거나 컴포넌트의 getter로 만드세요");
			return;
		}
		String code = STRING.matcher(value).replaceAll("''");
		if (code.contains("=>")) {
			unsupported(file, attribute, display, "화살표 함수", "로직은 컴포넌트의 메서드로 옮기고 x-on:click=\"save\"처럼 이름만 쓰세요");
			return;
		}
		if (code.contains("...")) {
			unsupported(file, attribute, display, "전개(...)", "값을 합치는 일은 컴포넌트의 getter로 옮기세요");
			return;
		}
		if (PROPERTY_ASSIGNMENT.matcher(code).find()) {
			unsupported(file, attribute, display, "속성 대입(a.b = …)", "값을 바꾸는 일은 컴포넌트의 메서드로 옮기세요");
			return;
		}
		Matcher word = WORD.matcher(code);
		while (word.find()) {
			if (GLOBALS.contains(word.group())) {
				unsupported(file, attribute, display, "전역 이름 " + word.group(), "필요한 값은 컴포넌트의 메서드나 getter에서 구하세요");
				return;
			}
		}
	}

	private void unsupported(TemplateFile file, AlpineAttribute attribute, String display, String what, String how) {
		error(file, attribute.line(), display, "Alpine CSP 빌드가 읽지 못하는 식입니다(" + what + "). " + how + ".");
	}

	// ── 도움 ──────────────────────────────────────────────────

	/** 앱 템플릿의 모듈. 모듈 템플릿이 아니거나(fragments/, error/) 프레임워크 템플릿이면 null */
	private String moduleOf(TemplateFile file) {
		if (file.origin() != Origin.APP) {
			return null;
		}
		Place place = Place.of(file.path());
		return (place.top() != null && !RESERVED.contains(place.top())) ? place.top() : null;
	}

	/** 앱 JS 파일의 모듈 ({@code static/{모듈}/…}). 아니면 null */
	private String moduleOf(ScriptFile script) {
		if (script.origin() != Origin.APP) {
			return null;
		}
		int slash = script.path().indexOf('/');
		String top = (slash > 0) ? script.path().substring(0, slash) : null;
		return (top != null && this.modules.contains(top)) ? top : null;
	}

	private static boolean ownedBy(String name, String module) {
		return name.startsWith(module) && name.length() > module.length()
				&& Character.isUpperCase(name.charAt(module.length()));
	}

	private static String capitalize(String name) {
		return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static int lineOf(String content, int index) {
		int line = 1;
		for (int i = 0; i < index; i++) {
			if (content.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	private void error(TemplateFile file, int line, String reference, String message) {
		this.violations.add(new Violation(file.location(), line, RULE, Severity.ERROR, reference, message));
	}

	private void scriptError(ScriptFile script, int line, String reference, String message) {
		this.violations.add(new Violation(script.location(), line, RULE, Severity.ERROR, reference, message));
	}
}
