package io.salvia95.cozy.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import org.springframework.modulith.core.ApplicationModules;

import io.salvia95.cozy.rules.RulesInput;
import io.salvia95.cozy.rules.SlotRules;
import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.TemplateRules;
import io.salvia95.cozy.rules.Violation;

/**
 * 아키텍처 검사 (PRD §6.5 검사 1, 2). 앱은 한 줄만 쓴다.
 *
 * <pre>
 * class ArchitectureTest extends CozyArchitectureTest {
 * }
 * </pre>
 *
 * <ul>
 * <li>C1: {@code ApplicationModules.of(앱 클래스).verify()}. 다른 모듈의 spi를 쓰는데 의존 선언이 빠졌으면 고치는 명령
 * ({@code cozy link})을 함께 낸다</li>
 * <li>C2: 템플릿 규칙 R1~R15. 위반이 있으면 전체 목록을 실패 메시지로 낸다. 경고는 표준 출력에 보고한다.</li>
 * <li>C6: 이벤트 리스너 파라미터는 CozyEvent, 이벤트마다 버전별 직렬화 샘플과 호환 (cozy-starter-events를 쓰는 앱)</li>
 * <li>C8: 모듈 안의 패키지(api, spi, internal/app·domain·infra·config)와 레이어 방향, 인터페이스 + Impl</li>
 * <li>C9: {@code src/main} 아래에 채우지 않은 빈칸({@code cozy:slot} 표시)이 없다</li>
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
	public final void slotsAreFilled() {
		List<Violation> slots = SlotRules.check(sourceRoot(), "src/main/");
		if (!slots.isEmpty()) {
			throw new AssertionFailedError("[C9] 채우지 않은 빈칸 " + slots.size() + "곳 (docs/conventions/testing.md)\n"
					+ slots.stream().map(Violation::format).collect(Collectors.joining("\n")));
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
		RulesInput input = new RulesInput(templates, CozyModules.model(CozyModules.of(applicationClass())),
				CozyProject.manifest(manifestFile()), allowSameLayer(), CozyProject.icons(), CozyProject.filledIcons());
		List<Violation> violations = TemplateRules.check(input);
		violations.stream().filter((v) -> !v.isError()).forEach((v) -> System.out.println(v.format()));
		List<Violation> errors = violations.stream().filter(Violation::isError).toList();
		if (!errors.isEmpty()) {
			throw new AssertionFailedError("[C2] 템플릿 규칙 위반 " + errors.size() + "건 (docs/conventions/rules.md)\n"
					+ errors.stream().map(Violation::format).collect(Collectors.joining("\n")));
		}
	}
}
