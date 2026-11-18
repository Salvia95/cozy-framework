package io.salvia95.cozy.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import org.springframework.modulith.core.ApplicationModules;

import io.salvia95.cozy.rules.RulesInput;
import io.salvia95.cozy.rules.ScriptFile;
import io.salvia95.cozy.rules.BehaviorRules;
import io.salvia95.cozy.rules.BehaviorWiring;
import io.salvia95.cozy.rules.SlotRules;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.TemplateRules;
import io.salvia95.cozy.rules.Violation;

/**
 * 아키텍처 검사 (C1, C2, C6, C8, C9, C11, C12, C13). 앱은 한 줄만 쓴다.
 *
 * <pre>
 * class ArchitectureTest extends CozyArchitectureTest {
 * }
 * </pre>
 *
 * <ul>
 * <li>C1: {@code ApplicationModules.of(앱 클래스).verify()}. 다른 모듈의 spi를 쓰는데 의존 선언이 빠졌으면 고치는 명령
 * ({@code cozy link})을 함께 낸다</li>
 * <li>C2: 템플릿 규칙 R1~R16. 위반이 있으면 전체 목록을 실패 메시지로 낸다. 경고는 표준 출력에 보고한다.</li>
 * <li>C6: 이벤트 리스너 파라미터는 CozyEvent, 이벤트마다 버전별 직렬화 샘플과 호환 (cozy-starter-events를 쓰는 앱)</li>
 * <li>C8: 모듈 안의 패키지(api, spi, internal/app·domain·infra·config)와 레이어 방향, 인터페이스 + Impl</li>
 * <li>C9: {@code src/main} 아래에 채우지 않은 빈칸({@code cozy:slot} 표시)이 없다</li>
 * <li>C11: 이벤트는 주인 모듈의 internal.app.command에서만 만든다(이벤트 계약 모듈 {@code events.spi.{주인 모듈}}이면 폴더 이름이
 * 주인이다)</li>
 * <li>C12: 동작 명세({@code behaviors/{모듈}.yml})에서 만든 JS({@code static/{모듈}/{모듈}.js})가 지금 명세와 같고 손으로 고치지 않았다.
 * 명세가 없는 손으로 쓴 모듈 JS는 경고로 표준 출력에 보고한다({@code cozy migrate behaviors})</li>
 * <li>C13: 동작 명세의 컴포넌트를 앱 템플릿에 붙였고, 동작마다 부르는 요소나 받는 화면 이벤트가 있고, 내는 화면 이벤트를 받는 곳과 받는 화면 이벤트를
 * 내는 곳(다른 컴포넌트, 서버의 {@code HX-Trigger})이 있다</li>
 * </ul>
 * 문서: docs/conventions/structure.md, docs/conventions/testing.md, docs/conventions/rules.md
 */
public abstract class CozyArchitectureTest {

	/** 같은 계층 fragment 참조 허용 (R4). 앱 설정 {@code cozy.rules.allow-same-layer} */
	public static final String ALLOW_SAME_LAYER = "cozy.rules.allow-same-layer";

	/** 검사할 앱. 기본값은 테스트 패키지에서 위로 찾은 {@code @SpringBootApplication} 클래스 */
	protected Class<?> applicationClass() {
		return CozyProject.applicationClass(getClass());
	}

	/** 앱 템플릿 루트 */
	protected Path templateRoot() {
		return CozyProject.TEMPLATES;
	}

	/** 빈칸(C9)을 찾을 앱 소스 루트 */
	protected Path sourceRoot() {
		return CozyProject.SOURCES;
	}

	/** 앱 매니페스트 */
	protected Path manifestFile() {
		return CozyProject.MANIFEST;
	}

	/** 앱의 Alpine 컴포넌트를 찾을 정적 자원 루트 (R16) */
	protected Path staticRoot() {
		return CozyProject.STATIC;
	}

	protected boolean allowSameLayer() {
		return Boolean.parseBoolean(CozyProject.appProperty(ALLOW_SAME_LAYER));
	}

	@Test
	public final void modulesAreVerified() {
		ApplicationModules modules = CozyModules.of(applicationClass());
		try {
			modules.verify();
		}
		catch (RuntimeException ex) {
			List<String> commands = ModuleHints.commands(ex.getMessage());
			String fix = commands.stream().map((c) -> "\n고치는 명령: " + c).collect(Collectors.joining());
			throw new AssertionFailedError("[C1] 모듈 의존 규칙 위반 (Spring Modulith). 다른 모듈의 internal 패키지를 "
					+ "쓰지 말고, 필요한 의존은 package-info.java의 allowedDependencies에 선언하세요.\n" + ex.getMessage() + fix, ex);
		}
	}

	@Test
	public final void modulesFollowLayerRules() {
		Class<?> app = applicationClass();
		List<String> problems = LayerChecks.problems(CozyModules.of(app), app.getPackageName());
		if (!problems.isEmpty()) {
			throw new AssertionFailedError("[C8] 모듈 구조 규칙 위반 " + problems.size() + "건 (docs/conventions/structure.md)\n"
					+ String.join("\n", problems));
		}
	}

	@Test
	public final void eventsArePublishedByTheirOwner() {
		Class<?> app = applicationClass();
		List<String> problems = EventOwnerChecks.problems(CozyModules.of(app), app.getPackageName());
		if (!problems.isEmpty()) {
			throw new AssertionFailedError("[C11] 주인이 아닌 곳에서 만든 이벤트 " + problems.size() + "건 (docs/conventions/events.md)\n"
					+ String.join("\n", problems));
		}
	}

	@Test
	public final void slotsAreFilled() {
		List<Violation> slots = SlotRules.check(sourceRoot(), "src/main/");
		if (!slots.isEmpty()) {
			throw new AssertionFailedError("[C9] 채우지 않은 빈칸 " + slots.size() + "곳 (docs/conventions/testing.md)\n"
					+ slots.stream().map(Violation::format).collect(Collectors.joining("\n")));
		}
	}

	@Test
	public final void behaviorScriptsMatchTheirSpecs() {
		List<Violation> all = BehaviorRules.check(sourceRoot().resolve("resources"), "src/main/resources/");
		all.stream().filter((v) -> !v.isError()).forEach((v) -> System.out.println(v.format()));
		List<Violation> problems = all.stream().filter(Violation::isError).toList();
		if (!problems.isEmpty()) {
			throw new AssertionFailedError("[C12] 동작 명세와 만든 JS가 맞지 않음 " + problems.size() + "곳 (docs/conventions/ui.md)\n"
					+ problems.stream().map(Violation::format).collect(Collectors.joining("\n")));
		}
	}

	@Test
	public final void behaviorsAreWired() {
		List<Violation> problems = BehaviorWiring.check(CozyProject.appTemplates(templateRoot()), CozyProject.appScripts(staticRoot()),
				sourceRoot(), "src/main/");
		if (!problems.isEmpty()) {
			throw new AssertionFailedError("[C13] 이어지지 않은 화면 안의 동작 " + problems.size() + "곳 (docs/conventions/ui.md)\n"
					+ problems.stream().map(Violation::format).collect(Collectors.joining("\n")));
		}
	}

	@Test
	public final void eventListenersReceiveCozyEvents() {
		if (EventChecks.available()) {
			fail(EventChecks.listenerProblems(applicationClass().getPackageName()));
		}
	}

	@Test
	public final void eventSamplesAreCompatible() {
		if (EventChecks.available()) {
			fail(EventChecks.sampleProblems(applicationClass().getPackageName(), eventSamples()));
		}
	}

	/** 이벤트 직렬화 샘플 루트 */
	protected Path eventSamples() {
		return EventChecks.SAMPLES;
	}

	private static void fail(List<String> problems) {
		if (!problems.isEmpty()) {
			throw new AssertionFailedError(problems.size() + "건 실패 (docs/conventions/events.md)\n"
					+ String.join("\n", problems));
		}
	}

	@Test
	public final void templatesFollowBoundaryRules() {
		List<TemplateFile> templates = new ArrayList<>(CozyProject.appTemplates(templateRoot()));
		templates.addAll(CozyProject.frameworkTemplates());
		List<ScriptFile> scripts = new ArrayList<>(CozyProject.appScripts(staticRoot()));
		scripts.addAll(CozyProject.frameworkScripts());
		RulesInput input = new RulesInput(templates, CozyModules.model(CozyModules.of(applicationClass())),
				CozyProject.manifest(manifestFile()), allowSameLayer(), CozyProject.icons(), CozyProject.filledIcons(),
				scripts);
		List<Violation> violations = TemplateRules.check(input);
		violations.stream().filter((v) -> !v.isError()).forEach((v) -> System.out.println(v.format()));
		List<Violation> errors = violations.stream().filter(Violation::isError).toList();
		if (!errors.isEmpty()) {
			throw new AssertionFailedError("[C2] 템플릿 규칙 위반 " + errors.size() + "건 (docs/conventions/rules.md)\n"
					+ errors.stream().map(Violation::format).collect(Collectors.joining("\n")));
		}
	}
}
