package io.salvia95.cozy.rules.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.salvia95.cozy.rules.FragmentDeclaration;
import io.salvia95.cozy.rules.ModuleModel;
import io.salvia95.cozy.rules.Origin;
import io.salvia95.cozy.rules.RulesInput;
import io.salvia95.cozy.rules.Severity;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.Violation;
import io.salvia95.cozy.rules.internal.FragmentExpressions.Ref;
import io.salvia95.cozy.rules.internal.ParsedTemplate.FragmentDef;
import io.salvia95.cozy.rules.internal.ParsedTemplate.Problem;
import io.salvia95.cozy.rules.internal.ParsedTemplate.Reference;

/**
 * R1~R15 검사. 참조 하나에는 처음 걸린 규칙 하나만 보고한다.
 *
 * <p>모듈 템플릿은 {@code {모듈}/api/}(컨트롤러가 렌더링하는 화면), {@code {모듈}/spi/}(다른 모듈이 부르는 fragment),
 * {@code {모듈}/internal/}(모듈 안의 재료 조각)로 나눈다. api는 같은 모듈의 spi·internal과 허용된 모듈의 spi를, spi는
 * 같은 모듈의 internal과 허용된 모듈의 spi를, internal은 같은 모듈의 internal만 조합한다.
 */
public final class RuleChecker {

	private static final Set<String> RESERVED = Set.of("cozy", "fragments", "error");

	private static final List<String> LAYERS = List.of("layout", "pattern", "component", "icon");

	/** 아이콘 fragment가 있는 템플릿 (R13) */
	private static final String ICON_TEMPLATE = "cozy/icon/tabler";

	private static final java.util.regex.Pattern STRING_LITERAL = java.util.regex.Pattern.compile("'([a-z0-9-]+)'");

	/** R14: 색의 의미 다섯 가지. badge는 neutral도, alert·toast는 알림이라 neutral이 없다 */
	private static final List<String> TONES = List.of("neutral", "info", "success", "warning", "critical");

	/** tone을 받는 컴포넌트와 그 인자 위치 */
	private static final Map<String, Integer> TONE_ARGUMENTS = Map.of("cozy/component/badge :: badge", 1,
			"cozy/component/alert :: alert", 0, "cozy/component/toast :: toast", 0);

	/** 모듈 템플릿 폴더: 화면, 다른 모듈용, 모듈 전용 */
	private static final List<String> MODULE_LAYERS = List.of("api", "spi", "internal");

	private static final Pattern SIGNATURE = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_\\-]*)\\s*\\(([^)]*)\\)\\s*");

	private final RulesInput input;

	/** 템플릿 이름 → 해석 대상 (앱 파일이 프레임워크 파일을 가린다) */
	private final Map<String, ParsedTemplate> byName = new HashMap<>();

	private final List<ParsedTemplate> all = new ArrayList<>();

	private final List<Violation> violations = new ArrayList<>();

	private RuleChecker(RulesInput input) {
		this.input = input;
	}

	public static List<Violation> check(RulesInput input) {
		RuleChecker checker = new RuleChecker(input);
		checker.run();
		return checker.violations.stream()
			.sorted(Comparator.comparing(Violation::location).thenComparingInt(Violation::line)
				.thenComparing(Violation::ruleId))
			.toList();
	}

	public static List<String> declaredFragments(TemplateFile file) {
		return List.copyOf(TemplateScanner.scan(file).fragments.keySet());
	}

	private void run() {
		for (TemplateFile file : this.input.templates()) {
			ParsedTemplate parsed = TemplateScanner.scan(file);
			this.all.add(parsed);
			ParsedTemplate existing = this.byName.get(file.templateName());
			if (existing == null || file.origin() == Origin.APP) {
				this.byName.put(file.templateName(), parsed);
			}
		}
		for (ParsedTemplate template : this.all) {
			checkPlacement(template);
			checkSyntax(template);
			for (Reference reference : template.references) {
				checkReference(template, reference);
			}
			checkModelReads(template);
			checkDesign(template);
		}
		checkManifest();
	}

	// ── R1, R2: 위치 ───────────────────────────────────────────

	private void checkPlacement(ParsedTemplate template) {
		TemplateFile file = template.file;
		Place place = Place.of(file.path());
		if (place.top() == null) {
			error(file, 1, "R2", file.path(), "templates/ 바로 아래에는 템플릿을 두지 않습니다. 화면은 templates/{모듈}/에, "
					+ "Boot 오류 화면은 templates/error/에 두세요.");
			return;
		}
		if (file.origin() == Origin.APP) {
			if (place.top().equals("cozy")) {
				error(file, 1, "R1", "cozy/", "cozy/는 프레임워크(cozy-ui) 전용입니다. 앱 공용 fragment는 "
						+ "templates/fragments/{layout,pattern,component}/에 두세요.");
				return;
			}
			if (!RESERVED.contains(place.top()) && !this.input.modules().contains(place.top())) {
				error(file, 1, "R1", place.top() + "/", "'" + place.top() + "'는 Java 모듈이 아닙니다. 모듈 이름("
						+ moduleNames() + ") 또는 예약어(fragments, error) 폴더로 옮기세요.");
				return;
			}
			if (!RESERVED.contains(place.top()) && (place.second() == null || !MODULE_LAYERS.contains(place.second()))) {
				String m = place.top();
				error(file, 1, "R1", file.path(), "모듈 템플릿은 " + m + "/api/(컨트롤러가 렌더링하는 화면), " + m
						+ "/spi/(다른 모듈이 부르는 fragment), " + m + "/internal/(" + m + " 안에서 조합하는 조각) 아래에 둡니다.");
				return;
			}
		}
		else if (!place.top().equals("cozy") && !place.top().equals("error")) {
			error(file, 1, "R1", place.top() + "/", "프레임워크 템플릿은 cozy/ 또는 error/ 아래에만 둡니다.");
			return;
		}
		if (place.top().equals("cozy") || place.top().equals("fragments")) {
			boolean devAllowed = place.top().equals("cozy") && file.origin() == Origin.FRAMEWORK;
			String second = place.second();
			if (second == null || !(LAYERS.contains(second) || (devAllowed && second.equals("dev")))) {
				error(file, 1, "R1", file.path(), place.top() + "/ 아래에는 layout, pattern, component 폴더만 둡니다. "
						+ "fragment의 계층에 맞는 폴더로 옮기세요.");
			}
		}
	}

	// ── R6(중복 선언), R7: 구문 ─────────────────────────────────

	private void checkSyntax(ParsedTemplate template) {
		for (Problem problem : template.problems) {
			error(template.file, problem.line(), "R7", problem.text(), problem.why()
					+ ". 템플릿 경로와 fragment 이름을 글자 그대로 쓰세요 (예: ~{memo/spi/card :: card(${memo})}).");
		}
		for (Problem include : template.includes) {
			add(template.file, include.line(), "R7", Severity.WARNING, include.text(), include.why() + ".");
		}
		for (Problem each : template.eachWithInsertion) {
			error(template.file, each.line(), "R11", each.text(), "같은 요소에 쓰면 th:replace(th:insert)가 th:each보다 먼저 "
					+ "실행되어 반복 변수가 아직 없습니다. <th:block th:each=\"...\"> 안쪽 요소에 th:replace를 두세요.");
		}
		for (FragmentDef def : template.fragments.values()) {
			if (template.elements.contains(def.name().toLowerCase())) {
				error(template.file, def.line(), "R12", "th:fragment=\"" + def.name() + "\"", "같은 파일에 <" + def.name().toLowerCase()
						+ "> 요소가 있습니다. Thymeleaf는 '" + def.name() + "'를 고를 때 그 이름의 fragment와 요소를 모두 가져옵니다. "
						+ "HTML 요소 이름과 다른 이름으로 바꾸세요 (예: form → memoForm, table → memoTable).");
			}
		}
		for (FragmentDef duplicate : template.duplicateFragments) {
			error(template.file, duplicate.line(), "R6", duplicate.signature(), "같은 파일에 fragment '" + duplicate.name()
					+ "'가 두 번 선언되어 있습니다. 이름을 바꾸세요.");
		}
	}

	// ── R3, R4, R5, R6: 참조 ───────────────────────────────────

	private void checkReference(ParsedTemplate source, Reference reference) {
		Ref ref = reference.ref();
		TemplateFile file = source.file;
		String targetName = ref.sameFile() ? file.templateName() : ref.template();
		String display = ref.display(file.templateName());
		int line = reference.line();
		Place from = Place.of(file.path());
		Place to = Place.of(targetName + ".html");
		boolean sameFile = targetName.equals(file.templateName());

		if (!sameFile && boundary(file, from, to, targetName, line, display)) {
			return;
		}

		ParsedTemplate target = this.byName.get(targetName);
		if (target == null) {
			error(file, line, "R6", display, "템플릿 " + targetName + ".html이 없습니다. 경로를 확인하세요"
					+ suggestion(targetName) + ".");
			return;
		}
		checkSelector(file, line, display, ref, target);
	}

	/** @return 경계 위반을 보고했으면 true */
	private boolean boundary(TemplateFile file, Place from, Place to, String targetName, int line, String display) {
		if (from.top() == null || to.top() == null) {
			return false;
		}
		// R5
		if (from.top().equals("cozy") && !to.top().equals("cozy")) {
			error(file, line, "R5", display, "cozy/ 템플릿은 cozy/ 밖을 참조하지 않습니다.");
			return true;
		}
		if (from.top().equals("fragments") && !to.top().equals("cozy") && !to.top().equals("fragments")) {
			error(file, line, "R5", display, "fragments/는 모듈 템플릿을 참조하지 않습니다. 모듈 템플릿이 fragments/의 "
					+ "fragment에 값을 넘기도록 바꾸세요.");
			return true;
		}
		// R3, R9: 다른 모듈
		if (!RESERVED.contains(to.top()) && !to.top().equals(from.top())) {
			String a = from.top();
			String b = to.top();
			if (!this.input.modules().contains(b) && !this.byName.containsKey(targetName)) {
				return false;
			}
			if (!RESERVED.contains(a) && "internal".equals(from.second())) {
				error(file, line, "R9", display, "internal은 다른 모듈의 템플릿을 쓰지 않습니다. 다른 모듈의 spi fragment는 " + a
						+ "/api/나 " + a + "/spi/에서 조합하세요.");
				return true;
			}
			if (!"spi".equals(to.second())) {
				String folder = (to.second() != null) ? b + "/" + to.second() + "/" : b + "/";
				error(file, line, "R3", display, folder + "는 " + b + " 안에서만 씁니다. 다른 모듈에는 " + b
						+ "/spi/ 아래 fragment만 공개됩니다. " + b + "/spi/에 공개 fragment를 추가하세요.");
				return true;
			}
			ModuleModel.Module module = this.input.modules().modules().get(a);
			if (module == null || !module.mayUseTemplatesOf(b)) {
				String how = (module != null && !module.declaresDependencies())
						? a + "는 allowedDependencies를 선언하지 않았습니다. "
						: a + "의 allowedDependencies에 " + b + "가 없습니다. ";
				String message = how + a + "/package-info.java의 @ApplicationModule(allowedDependencies = " + "\"" + b
						+ " :: spi\")에 추가하세요.";
				// 모듈이 있을 때만 cozy link가 고친다 (없으면 R1이 먼저 알린다)
				String command = (module != null) ? "cozy link " + a + " --uses " + b : null;
				this.violations.add(new Violation(file.location(), line, "R3", Severity.ERROR, display, message, command));
				return true;
			}
			return false;
		}
		// R9: 같은 모듈 안의 방향 (internal → spi → api 쪽으로만 조합한다)
		if (!RESERVED.contains(from.top()) && from.top().equals(to.top())) {
			String a = from.top();
			if ("spi".equals(from.second()) && "api".equals(to.second())) {
				error(file, line, "R9", display, "spi는 같은 모듈의 api를 참조하지 않습니다. 화면과 같이 쓰는 조각은 " + a
						+ "/internal/로 옮기고 api와 spi가 둘 다 그것을 조합하게 하세요.");
				return true;
			}
			if ("internal".equals(from.second()) && ("api".equals(to.second()) || "spi".equals(to.second()))) {
				error(file, line, "R9", display, "internal은 같은 모듈의 " + to.second() + "를 참조하지 않습니다. internal은 "
						+ "internal 조각만 조합하고, api와 spi가 internal을 조합합니다.");
				return true;
			}
		}
		// R4
		int fromRank = from.layerRank();
		int toRank = to.layerRank();
		if (fromRank >= 0 && toRank >= 0) {
			if (toRank < fromRank) {
				error(file, line, "R4", display, LAYERS.get(fromRank) + "는 " + LAYERS.get(toRank)
						+ "를 참조할 수 없습니다. layout → pattern → component → icon 방향으로만 참조합니다.");
				return true;
			}
			if (toRank == fromRank && !this.input.allowSameLayer()) {
				error(file, line, "R4", display, "같은 계층(" + LAYERS.get(fromRank) + ")끼리는 참조하지 않습니다. 조합은 위 계층에서 "
						+ "하거나, 꼭 필요하면 cozy.rules.allow-same-layer=true로 허용하세요.");
				return true;
			}
		}
		return false;
	}

	private void checkSelector(TemplateFile file, int line, String display, Ref ref, ParsedTemplate target) {
		String targetName = target.file.templateName();
		switch (ref.kind()) {
			case WHOLE -> {
			}
			case ID -> {
				if (!target.ids.contains(ref.selector())) {
					error(file, line, "R6", display, targetName + "에 id=\"" + ref.selector() + "\"인 요소가 없습니다.");
				}
			}
			case CLASS -> {
				if (!target.classes.contains(ref.selector())) {
					error(file, line, "R6", display, targetName + "에 class \"" + ref.selector() + "\"인 요소가 없습니다.");
				}
			}
			case NAME -> {
				FragmentDef def = target.fragments.get(ref.selector());
				if (def == null) {
					if (ref.argCount() == null && target.elements.contains(ref.selector().toLowerCase())) {
						return;
					}
					error(file, line, "R6", display, targetName + "에 fragment '" + ref.selector() + "'가 없습니다. 있는 fragment: "
							+ available(target) + ".");
					return;
				}
				int given = (ref.argCount() != null) ? ref.argCount() : 0;
				if (given != def.params().size()) {
					error(file, line, "R6", display, def.signature() + "는 파라미터가 " + def.params().size() + "개인데 "
							+ given + "개를 넘겼습니다. 선언과 같은 개수로 넘기세요.");
					return;
				}
				if (targetName.equals(ICON_TEMPLATE) && given == 1) {
					checkIcon(file, line, display, ref);
				}
				checkTone(file, line, display, targetName, ref);
			}
		}
	}

	/** R13: 글자로 쓴 아이콘 이름이 스프라이트에 있어야 한다. 변수로 넘긴 이름은 Java 쪽(CozyNavItem 등)이 검사한다 */
	/** R14: 글자로 쓴 tone이 정해진 다섯 가지인가 (badge 둘째, alert·toast 첫째 인자). 변수로 넘긴 값은 확인하지 않는다 */
	private void checkTone(TemplateFile file, int line, String display, String targetName, Ref ref) {
		Integer index = TONE_ARGUMENTS.get(targetName + " :: " + ref.selector());
		if (index == null || ref.args().size() <= index) {
			return;
		}
		Matcher literal = STRING_LITERAL.matcher(ref.args().get(index).strip());
		if (literal.matches() && !TONES.contains(literal.group(1))) {
			error(file, line, "R14", display, "tone '" + literal.group(1) + "'는 없습니다. " + String.join(", ", TONES)
					+ " 중에서 의미에 맞는 것을 고르세요 (docs/conventions/design.md).");
		}
	}

	private void checkIcon(TemplateFile file, int line, String display, Ref ref) {
		Matcher literal = STRING_LITERAL.matcher(ref.args().get(0));
		if (!literal.matches()) {
			return;
		}
		String name = literal.group(1);
		boolean filled = ref.selector().equals("iconFilled");
		java.util.Set<String> names = filled ? this.input.filledIcons() : this.input.icons();
		if (names.isEmpty()) {
			error(file, line, "R13", display, "아이콘 목록이 없어 '" + name + "'를 확인할 수 없습니다. cozy-ui가 클래스패스에 있어야 합니다.");
			return;
		}
		if (!names.contains(name)) {
			String similar = names.stream()
				.filter((n) -> distance(n, name) <= 2)
				.sorted(java.util.Comparator.comparingInt((String n) -> distance(n, name)).thenComparing(n -> n))
				.limit(5)
				.reduce((a, b) -> a + ", " + b)
				.map((s) -> " 비슷한 이름: " + s + ".")
				.orElse("");
			error(file, line, "R13", display, "아이콘 '" + name + "'가 Tabler Icons" + (filled ? " filled" : "")
					+ "에 없습니다." + similar + " 이름은 tabler.io/icons에서 찾습니다(오프라인이면 /cozy/dev/catalog의 icon).");
		}
	}

	/** 편집 거리 (오타 제안용) */
	private static int distance(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}

	// ── R8: 매니페스트 ──────────────────────────────────────────

	private void checkManifest() {
		Map<String, FragmentDeclaration> declared = new LinkedHashMap<>();
		for (FragmentDeclaration declaration : this.input.manifest()) {
			Matcher m = SIGNATURE.matcher(declaration.signature());
			String name = m.matches() ? m.group(1) : declaration.signature().strip();
			String key = declaration.template() + " :: " + name;
			String reference = key;
			if (declared.putIfAbsent(key, declaration) != null) {
				manifestError(declaration, reference, "같은 fragment가 매니페스트에 두 번 있습니다. 하나를 지우세요.");
				continue;
			}
			Place place = Place.of(declaration.template() + ".html");
			boolean inScope = place.layerRank() >= 0 && (place.top().equals("cozy") || place.top().equals("fragments"));
			boolean moduleSpi = place.top() != null && !RESERVED.contains(place.top()) && "spi".equals(place.second());
			if (!inScope && !moduleSpi) {
				manifestError(declaration, reference, "매니페스트에는 cozy/·fragments/의 layout, pattern, component와 "
						+ "모듈 spi/의 fragment만 적습니다.");
				continue;
			}
			ParsedTemplate template = this.byName.get(declaration.template());
			if (template == null) {
				manifestError(declaration, reference, "템플릿 " + declaration.template() + ".html이 없습니다. 템플릿 경로를 "
						+ "고치거나 항목을 지우세요.");
				continue;
			}
			FragmentDef def = template.fragments.get(name);
			if (def == null) {
				manifestError(declaration, reference, declaration.template() + "에 fragment '" + name + "'가 없습니다. 있는 "
						+ "fragment: " + available(template) + ".");
				continue;
			}
			List<String> params = (m.matches() && !m.group(2).isBlank())
					? List.of(m.group(2).split(",")).stream().map(String::strip).toList() : List.of();
			if (!params.equals(def.params())) {
				manifestError(declaration, reference, "매니페스트의 signature " + declaration.signature() + "가 템플릿의 "
						+ def.signature() + "와 다릅니다. 같게 맞추세요.");
			}
		}
		for (ParsedTemplate template : this.all) {
			Place place = Place.of(template.file.path());
			if (place.top() == null || place.layerRank() < 0
					|| !(place.top().equals("cozy") || place.top().equals("fragments"))) {
				continue;
			}
			if (this.byName.get(template.file.templateName()) != template) {
				continue;
			}
			for (FragmentDef def : template.fragments.values()) {
				String key = template.file.templateName() + " :: " + def.name();
				if (!declared.containsKey(key)) {
					error(template.file, def.line(), "R8", key, "fragment가 매니페스트(cozy/fragments.yml)에 없습니다. id, "
							+ "template, signature, purpose, params, samples를 적어 추가하세요 (docs/conventions/ui.md).");
				}
			}
		}
	}

	// ── R14, R15: 디자인 원칙 (docs/conventions/design.md) ─────────

	private void checkDesign(ParsedTemplate template) {
		if (template.file.origin() == Origin.APP) {
			for (Problem color : template.colorClasses) {
				error(template.file, color.line(), "R14", color.why() + "=\"" + color.text() + "\"", "앱 화면은 색을 직접 고르지 않습니다. "
						+ "색은 의미(tone: neutral, info, success, warning, critical)를 받는 cozy 컴포넌트가 고릅니다 "
						+ "(예: badge('고정', 'neutral'), alert('critical', …), deleteConfirm(…)). 허용: text-secondary, btn-primary.");
			}
		}
		for (Problem order : template.actionOrder) {
			String message = order.why().equals("many")
					? "버튼 묶음 하나에 주 버튼(btn-primary, btn-danger)은 하나만 둡니다. 나머지는 btn으로 낮추세요."
					: "주 버튼(btn-primary, btn-danger)은 버튼 묶음의 맨 오른쪽(마지막)에 둡니다. 예: [취소] [저장], [삭제] [수정].";
			error(template.file, order.line(), "R15", order.text(), message);
		}
	}

	// ── R10: spi·internal은 파라미터만 ─────────────────────────────

	private void checkModelReads(ParsedTemplate template) {
		Place place = Place.of(template.file.path());
		if (template.file.origin() != Origin.APP || place.top() == null || RESERVED.contains(place.top())
				|| !("spi".equals(place.second()) || "internal".equals(place.second()))) {
			return;
		}
		for (ParsedTemplate.ModelRead read : template.modelReads) {
			String reference = (read.name() != null) ? read.expression() + " → " + read.name() : read.expression();
			String message;
			if (read.fragment() == null) {
				message = "th:fragment 밖의 표현식입니다. spi·internal 템플릿의 내용은 th:fragment 안에 두고 값은 파라미터로 "
						+ "받으세요.";
			}
			else if (read.name() == null) {
				message = "*{...}가 부르는 쪽의 th:object에 기댑니다. 폼 객체를 " + read.fragment() + "의 파라미터로 받아 fragment "
						+ "안에서 th:object로 지정하세요(파라미터 이름은 컨트롤러의 모델 이름과 같게).";
			}
			else if (read.name().startsWith("@")) {
				message = "'" + read.name() + "'는 템플릿에서 스프링 빈을 부릅니다. 값은 컨트롤러가 spi로 구해 " + read.fragment()
						+ "의 파라미터로 넘기세요.";
			}
			else if (read.name().startsWith("#")) {
				message = "'" + read.name() + "'는 모델이나 요청 전체를 읽습니다. 필요한 값을 " + read.fragment()
						+ "의 파라미터로 받으세요.";
			}
			else {
				message = "'" + read.name() + "'는 " + read.fragment() + "의 파라미터나 지역 변수가 아닙니다. spi·internal "
						+ "fragment는 부르는 화면의 모델을 읽지 않습니다. " + read.fragment() + "에 파라미터를 더하고 부르는 쪽이 "
						+ "값을 넘기게 하세요.";
			}
			error(template.file, read.line(), "R10", reference, message);
		}
	}

	private void manifestError(FragmentDeclaration declaration, String reference, String message) {
		this.violations.add(new Violation(declaration.source(), declaration.line(), "R8", Severity.ERROR,
				reference + " (id: " + declaration.id() + ")", message));
	}

	// ── 도움 ──────────────────────────────────────────────────

	private String moduleNames() {
		return this.input.modules().modules().keySet().stream().sorted().collect(Collectors.joining(", "));
	}

	private static String available(ParsedTemplate template) {
		if (template.fragments.isEmpty()) {
			return "없음";
		}
		return template.fragments.values().stream().map(FragmentDef::signature).collect(Collectors.joining(", "));
	}

	private String suggestion(String missing) {
		String leaf = missing.substring(missing.lastIndexOf('/') + 1);
		Set<String> similar = new HashSet<>();
		for (String name : this.byName.keySet()) {
			if (name.endsWith("/" + leaf)) {
				similar.add(name);
			}
		}
		return similar.isEmpty() ? "" : " (비슷한 템플릿: " + String.join(", ", similar.stream().sorted().toList()) + ")";
	}

	private void error(TemplateFile file, int line, String rule, String reference, String message) {
		add(file, line, rule, Severity.ERROR, reference, message);
	}

	private void add(TemplateFile file, int line, String rule, Severity severity, String reference, String message) {
		this.violations.add(new Violation(file.location(), line, rule, severity, reference, message));
	}

	/** 템플릿 경로의 첫 폴더와 둘째 폴더 */
	record Place(String top, String second) {

		static Place of(String path) {
			String[] parts = path.split("/");
			if (parts.length < 2) {
				return new Place(null, null);
			}
			return new Place(parts[0], (parts.length >= 3) ? parts[1] : null);
		}

		/** cozy/·fragments/ 계층 순위: layout 0, pattern 1, component 2. 계층이 아니면 -1 */
		int layerRank() {
			if (this.top == null || !(this.top.equals("cozy") || this.top.equals("fragments"))) {
				return -1;
			}
			return (this.second != null) ? LAYERS.indexOf(this.second) : -1;
		}
	}
}
