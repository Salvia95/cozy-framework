package io.salvia95.cozy.cli.internal.recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.SourceVersion;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Role;

/** 2단계 레시피: dto, enable events. 이미 있는 파일은 파서가 찾은 위치에 덧붙인다 */
final class StageTwoRecipes {

	private StageTwoRecipes() {
	}

	// ── dto ───────────────────────────────────────────────────

	/** {@code 이름:종류[:설명]}, {@code 이름:Record[:설명]}, {@code 이름:list:Record[:설명]} */
	private static final Pattern DTO_FIELD = Pattern.compile("([a-z][A-Za-z0-9]*):(list:)?([A-Za-z][A-Za-z0-9]*)(!?)(?::(.+))?");

	/** record 구성 요소 하나 */
	private record Component(String name, String type, String importName, String description) {
	}

	static String dto(Context ctx, Args args) {
		args.only("dto", 2, "field");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String record = Names.pascal(args.positional(1, "record 이름"), "record");
		ctx.module(module);
		if (args.all("field").isEmpty()) {
			throw new CliException("--field가 하나 이상 필요합니다 (예: --field message:text:무엇이 있었는지).");
		}
		String pkg = ctx.rolePackage(module, Layer.SPI_DTO);
		List<Component> components = new ArrayList<>();
		Set<String> names = new LinkedHashSet<>();
		for (String text : args.all("field")) {
			Component component = component(ctx, module, pkg, record, text);
			if (!names.add(component.name())) {
				throw new CliException("같은 이름의 필드가 둘 이상입니다: " + component.name());
			}
			components.add(component);
		}
		Set<String> imports = new TreeSet<>();
		components.stream().map(Component::importName).filter((i) -> i != null).forEach(imports::add);
		StringBuilder params = new StringBuilder();
		components.stream()
			.filter((c) -> c.description() != null)
			.forEach((c) -> params.append(" * @param ").append(c.name()).append(' ').append(c.description()).append('\n'));
		ctx.ensureNamedInterface(module, Layer.SPI_DTO);
		ctx.plan()
			.create(ctx.roleFile(module, Role.SPI_DTO, record), new Template().put("pkg", pkg)
				.put("imports", imports.isEmpty() ? "" : String.join("", imports.stream().map((i) -> "import " + i + ";\n").toList()) + "\n")
				.put("module", module)
				.put("Record", record)
				.put("params", params.isEmpty() ? "" : " *\n" + params)
				.put("components", String.join(", ", components.stream().map((c) -> c.type() + " " + c.name()).toList()))
				.render("dto.java"));
		return "다음: " + Names.topicParticle(record) + " " + module + "의 Finder가 돌려주는 값이나 공개 fragment의 파라미터(cozy add fragment " + module
				+ " <이름> --param 이름:" + record + ")로 씁니다";
	}

	private static Component component(Context ctx, String module, String pkg, String record, String text) {
		Matcher m = DTO_FIELD.matcher(text);
		if (!m.matches()) {
			throw new CliException("필드 '" + text + "'는 이름:종류[:설명] 형식입니다. 종류는 text·longtext·int·long·bool·date·instant, "
					+ module + ".spi.dto의 record 이름, 또는 list:record 이름입니다 (예: message:text:무엇이 있었는지).");
		}
		String name = m.group(1);
		boolean list = m.group(2) != null;
		String kind = m.group(3);
		if (!m.group(4).isEmpty()) {
			throw new CliException("필드 '" + text + "': record에는 필수 여부가 없어 !를 쓰지 않습니다. !를 빼세요.");
		}
		if (SourceVersion.isKeyword(name)) {
			throw new CliException("필드 이름 '" + name + "'는 Java 예약어라 쓸 수 없습니다.");
		}
		String description = (m.group(5) != null) ? Names.label(m.group(5), "필드 설명") : null;
		if (!list && Character.isLowerCase(kind.charAt(0))) {
			Field field = new Field(name, Field.Kind.of(kind), false, name);
			return new Component(name, field.eventType(), field.importName(), description);
		}
		if (Character.isLowerCase(kind.charAt(0))) {
			throw new CliException("필드 '" + text + "': list: 뒤에는 " + module + ".spi.dto의 record 이름을 씁니다 (예: items:list:"
					+ record + "Line).");
		}
		if (kind.equals(record)) {
			throw new CliException("필드 '" + text + "': record가 자기 자신을 담을 수는 없습니다.");
		}
		String file = ctx.roleFile(module, Role.SPI_DTO, kind);
		if (!ctx.project().sources().types().containsKey(pkg + "." + kind) && !ctx.plan().exists(file)) {
			throw new CliException(pkg + "." + kind + "가 없습니다. spi DTO에는 원시 값과 같은 모듈의 spi DTO만 담습니다. 먼저 cozy add dto "
					+ module + " " + kind + " --field …로 만드세요.");
		}
		return list ? new Component(name, "List<" + kind + ">", "java.util.List", description)
				: new Component(name, kind, null, description);
	}

	// ── enable events ─────────────────────────────────────────

	static final String ENABLE_USAGE = "cozy enable 뒤에는 events나 workbench를 적습니다 (cozy enable events, cozy enable workbench).";

	/** {@code cozy enable events}, {@code cozy enable workbench} */
	static String enable(Context ctx, Args args) {
		String feature = (args.positionalCount() == 0) ? "" : args.positional(0, "켤 기능");
		return switch (feature) {
			case "events" -> enableEvents(ctx, args);
			case "workbench" -> enableWorkbench(ctx, args);
			default -> throw new CliException(ENABLE_USAGE);
		};
	}

	/** 워크벤치를 관리하는 첫 cozy-parent 버전 */
	private static final int[] WORKBENCH_SINCE = { 1, 2 };

	private static String enableWorkbench(Context ctx, Args args) {
		args.only("enable workbench", 1);
		String version = ctx.project().cozyVersion();
		java.util.regex.Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\..*").matcher((version != null) ? version : "");
		if (!m.matches()) {
			throw new CliException("앱이 cozy-parent를 상속하지 않아 cozy 버전을 알 수 없습니다. pom.xml의 parent를 "
					+ XmlEdits.COZY_GROUP + ":cozy-parent로 두세요.");
		}
		int major = Integer.parseInt(m.group(1));
		int minor = Integer.parseInt(m.group(2));
		if (major < WORKBENCH_SINCE[0] || (major == WORKBENCH_SINCE[0] && minor < WORKBENCH_SINCE[1])) {
			throw new CliException("워크벤치는 cozy " + WORKBENCH_SINCE[0] + "." + WORKBENCH_SINCE[1] + ".0부터 있습니다. 이 앱은 cozy "
					+ version + "입니다. pom.xml의 cozy-parent 버전을 먼저 올리세요.");
		}
		String file = "pom.xml";
		String current = ctx.plan().current(file);
		if (current == null) {
			throw new CliException("pom.xml이 없습니다.");
		}
		ctx.plan().edit(file, XmlEdits.addWorkbench(file, current), "의존 += " + XmlEdits.COZY_GROUP + ":cozy-workbench");
		return "다음: dev 프로필로 앱을 실행하고 /cozy/dev/workbench를 엽니다. 새 의존은 mvn verify가 받습니다";
	}

	private static String enableEvents(Context ctx, Args args) {
		args.only("enable events", 1);
		String file = "pom.xml";
		String current = ctx.plan().current(file);
		if (current == null) {
			throw new CliException("pom.xml이 없습니다.");
		}
		ctx.plan().edit(file, XmlEdits.addEventsStarter(file, current), "의존 += " + XmlEdits.COZY_GROUP + ":cozy-starter-events");
		return "다음: 이벤트는 cozy add event <모듈> <이벤트> --field …로 만들고, 받는 쪽은 cozy add listener <모듈> --on <모듈>.<이벤트>. "
				+ "새 의존은 mvn verify가 받습니다";
	}
}
