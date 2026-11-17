package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.BehaviorScript;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Component;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.State;
import io.salvia95.cozy.cli.internal.behavior.Expr;
import io.salvia95.cozy.rules.BehaviorRules;

/**
 * {@code cozy add behavior}: 모듈의 동작 명세({@code behaviors/{모듈}.yml})에 컴포넌트를 선언하거나 고치고, 그 명세에서
 * {@code static/{모듈}/{모듈}.js}를 만든다. {@code cozy sync behaviors}: 모든 명세에서 JS를 다시 만든다. 형식: docs/conventions/cli.md
 */
final class BehaviorRecipe {

	private static final String RESOURCES = "src/main/resources/";

	private BehaviorRecipe() {
	}

	static String specFile(String module) {
		return RESOURCES + BehaviorRules.specPath(module);
	}

	static String scriptFile(String module) {
		return RESOURCES + BehaviorRules.scriptPath(module);
	}

	static String run(Context ctx, Args args) {
		args.only("behavior", 2, "purpose", "state", "computed", "action", "listen");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String key = Names.camelOfKebab(Names.kebab(args.positional(1, "동작"), "동작"));
		ctx.module(module);
		Plan plan = ctx.plan();
		BehaviorSpec spec = read(plan, module);
		Component existing = spec.components().get(key);
		String purpose = args.option("purpose");
		if (existing == null && (purpose == null || purpose.isBlank())) {
			throw new CliException("새 동작에는 --purpose로 목적 한 문장을 적습니다 (예: --purpose \"본문을 쓰는 동안 글자 수를 보인다\").");
		}
		if (purpose != null && (purpose.isBlank() || purpose.contains("\n"))) {
			throw new CliException("--purpose는 한 줄 문장입니다.");
		}
		Component component = (existing != null) ? copy(existing, purpose) : Component.empty(key, purpose.strip());
		String name = component.name(module);
		List<String> changes = new ArrayList<>();
		if (existing == null) {
			changes.add("컴포넌트 " + name);
		}
		else if (purpose != null && !purpose.strip().equals(existing.purpose())) {
			changes.add("목적");
		}
		String where = "컴포넌트 " + key;
		for (String entry : args.all("state")) {
			List<String> parts = BehaviorSpec.split(entry, ':', "--state", "이름:종류[=처음 값][@data-이름] (예: length:number@data-length)");
			put(component, component.state(), "상태", parts.get(0), State.parse(parts.get(1), where + " 상태 " + parts.get(0)),
					changes);
		}
		for (String entry : args.all("computed")) {
			List<String> parts = BehaviorSpec.split(entry, ':', "--computed", "이름:식 (예: over:gt(length, limit))");
			put(component, component.computed(), "계산", parts.get(0), Expr.parse(parts.get(1), where + " 계산 " + parts.get(0)),
					changes);
		}
		for (String entry : args.all("action")) {
			List<String> parts = BehaviorSpec.split(entry, ':', "--action", "이름:단계[; 단계] (예: count:set(length, length(event.value)))");
			put(component, component.actions(), "동작", parts.get(0), Expr.parseSteps(parts.get(1), where + " 동작 " + parts.get(0)),
					changes);
		}
		for (String entry : args.all("listen")) {
			List<String> parts = BehaviorSpec.split(entry, ':', "--listen", "동작:모듈:이벤트 (예: clear:memo:archived)");
			String before = component.listens().put(parts.get(1), parts.get(0));
			if (!parts.get(0).equals(before)) {
				changes.add(((before == null) ? "받는 이벤트 " : "받는 이벤트 바꿈 ") + parts.get(1));
			}
		}
		Map<String, Component> components = new LinkedHashMap<>(spec.components());
		components.put(key, component);
		BehaviorSpec next = new BehaviorSpec(module, components);
		write(plan, next, String.join(", ", changes));
		return "다음: " + module + " 화면 템플릿에 x-data=\"" + name + "\"를 붙이고, 이벤트와 값은 이름만 씁니다(x-on:input=\"동작\", "
				+ "x-text=\"계산\"). " + scriptFile(module) + "는 고치지 않습니다. 동작을 바꿀 때는 이 명령을 다시 씁니다";
	}

	/** {@code cozy sync behaviors}: 모든 명세를 정리해 다시 쓰고 JS를 다시 만든다. 명세가 없는 만든 JS는 지운다 */
	static String sync(Plan plan, AppProject project) {
		Path resources = project.dir().resolve(RESOURCES);
		List<String> modules = new ArrayList<>();
		Path specs = resources.resolve(BehaviorRules.SPEC_DIR);
		try {
			if (Files.isDirectory(specs)) {
				try (Stream<Path> files = Files.list(specs)) {
					for (Path file : files.sorted().toList()) {
						String fileName = file.getFileName().toString();
						if (!fileName.endsWith(".yml") || !fileName.substring(0, fileName.length() - 4).matches("[a-z][a-z0-9]*")) {
							throw new CliException(RESOURCES + BehaviorRules.SPEC_DIR + "/" + fileName
									+ "는 동작 명세가 아닙니다. 동작 명세 폴더에는 모듈 이름의 .yml만 둡니다.");
						}
						modules.add(fileName.substring(0, fileName.length() - 4));
					}
				}
			}
			for (String module : modules) {
				if (!project.modules().containsKey(module)) {
					throw new CliException(specFile(module) + "의 모듈 '" + module + "'가 없습니다. 있는 모듈: "
							+ String.join(", ", project.modules().keySet()));
				}
				write(plan, read(plan, module), "명세에서 다시 만듦");
			}
			Path statics = resources.resolve("static");
			if (Files.isDirectory(statics)) {
				try (Stream<Path> dirs = Files.list(statics)) {
					for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
						String module = dir.getFileName().toString();
						String script = plan.current(scriptFile(module));
						if (script != null && !modules.contains(module) && BehaviorRules.isGenerated(script)) {
							plan.delete(scriptFile(module), "동작 명세(" + BehaviorRules.specPath(module) + ")가 없음");
						}
					}
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return modules.isEmpty() ? "동작 명세가 없습니다. 동작은 cozy add behavior로 선언합니다"
				: "cozy check, 그다음 mvn verify로 확인합니다";
	}

	private static BehaviorSpec read(Plan plan, String module) {
		String text = plan.current(specFile(module));
		return (text == null) ? BehaviorSpec.empty(module) : BehaviorSpec.read(module, text, specFile(module));
	}

	/** 명세와 JS를 함께 쓴다. 손으로 쓴 JS가 있으면 덮어쓰지 않는다 */
	static void write(Plan plan, BehaviorSpec spec, String detail) {
		String module = spec.module();
		String script = plan.current(scriptFile(module));
		if (script != null && !BehaviorRules.isGenerated(script)) {
			throw new CliException(scriptFile(module) + "는 손으로 쓴 컴포넌트 파일입니다. 동작 명세에서 만드는 파일과 함께 둘 수 없어 덮어쓰지 않습니다. "
					+ "그 파일의 컴포넌트를 cozy add behavior로 명세에 옮긴 뒤 손으로 쓴 파일을 지웁니다.");
		}
		String specText = spec.write();
		String js = BehaviorScript.generate(spec, specText);
		plan.replace(specFile(module), specText, detail.isEmpty() ? "동작 명세" : detail);
		plan.replace(scriptFile(module), js, "동작 명세에서 만듦");
	}

	/** 같은 칸의 같은 이름은 바꾸고, 다른 칸에 있는 이름은 거부한다 */
	private static <T> void put(Component component, Map<String, T> section, String label, String name, T value,
			List<String> changes) {
		String other = component.sectionOf(name);
		if (other != null && !other.equals(label)) {
			throw new CliException("컴포넌트 " + component.key() + "에 '" + name + "'이 이미 " + other + "으로 있습니다. 템플릿이 이름 하나로 부르므로 "
					+ "다른 이름을 씁니다.");
		}
		T before = section.put(name, value);
		if (before == null) {
			changes.add(label + " " + name);
		}
		else if (!before.equals(value)) {
			changes.add(label + " 바꿈 " + name);
		}
	}

	private static Component copy(Component c, String purpose) {
		return new Component(c.key(), (purpose != null) ? purpose.strip() : c.purpose(), c.state(), c.computed(), c.actions(),
				c.listens());
	}
}
