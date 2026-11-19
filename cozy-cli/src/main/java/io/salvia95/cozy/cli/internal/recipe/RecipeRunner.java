package io.salvia95.cozy.cli.internal.recipe;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Describe;
import io.salvia95.cozy.cli.internal.FrameworkJar;
import io.salvia95.cozy.cli.internal.Json;
import io.salvia95.cozy.rules.SlotRules;
import io.salvia95.cozy.rules.Violation;

/**
 * {@code cozy add <레시피>}, {@code cozy link}, {@code cozy enable events}, {@code cozy sync agents}를 실행한다. 계획을 보여 주고, {@code --dry-run}이 아니면 적용한 뒤 남은 빈칸, 손으로 할 일, 다음 할 일을
 * 알린다. 출력 형식: docs/conventions/cli.md
 */
public final class RecipeRunner {

	private RecipeRunner() {
	}

	/**
	 * @param action {@code add}, {@code link}, {@code enable}
	 * @param rest 동작 뒤의 인자 (add면 첫째가 레시피 이름)
	 */
	public static void run(String action, List<String> rest, AppProject project, Supplier<FrameworkJar> framework,
			LocalDateTime now, boolean dryRun, boolean json, PrintStream out) {
		String recipe;
		List<String> args;
		if (action.equals("link")) {
			recipe = "link";
			args = rest;
		}
		else if (action.equals("enable")) {
			recipe = "enable " + (rest.isEmpty() ? "" : rest.getFirst());
			args = rest;
		}
		else {
			if (rest.isEmpty()) {
				throw new CliException("cozy add 뒤에 레시피 이름을 적습니다. 목록은 cozy recipes로 봅니다.");
			}
			recipe = rest.getFirst();
			args = rest.subList(1, rest.size());
		}
		Context ctx = new Context(project, framework, now);
		Args parsed = Args.parse(args);
		String next = switch (recipe) {
			case "module" -> BasicRecipes.module(ctx, parsed);
			case "link" -> BasicRecipes.link(ctx, parsed);
			case "event" -> BasicRecipes.event(ctx, parsed);
			case "listener" -> BasicRecipes.listener(ctx, parsed);
			case "migration" -> BasicRecipes.migration(ctx, parsed);
			case "fragment" -> BasicRecipes.fragment(ctx, parsed);
			case "behavior" -> BehaviorRecipe.run(ctx, parsed);
			case "screen" -> ScreenRecipe.run(ctx, parsed);
			case "crud" -> CrudRecipe.run(ctx, parsed);
			case "dto" -> StageTwoRecipes.dto(ctx, parsed);
			case "enable events", "enable workbench", "enable " -> StageTwoRecipes.enable(ctx, parsed);
			case "query" -> MethodRecipes.query(ctx, parsed);
			case "command" -> MethodRecipes.command(ctx, parsed);
			case "event-version" -> EventVersionRecipe.run(ctx, parsed);
			case "field" -> FieldRecipe.run(ctx, parsed);
			default -> throw new CliException(recipe.startsWith("enable ") ? StageTwoRecipes.ENABLE_USAGE
					: "모르는 레시피입니다: " + recipe + ". cozy recipes로 목록을 봅니다.");
		};
		report(recipe, ctx.plan(), ctx.manual(), next.replaceFirst("^다음: ", ""), true, dryRun, json, out);
	}

	/**
	 * {@code cozy sync agents [--force]}: 앱의 에이전트 문서를 이 cozy 명령에 든 것과 같게 맞춘다. {@code cozy sync behaviors}: 동작 명세에서
	 * 모듈의 JS를 다시 만든다
	 */
	public static void sync(List<String> rest, AppProject project, boolean force, boolean dryRun, boolean json,
			PrintStream out) {
		if (rest.size() == 1 && rest.getFirst().equals("behaviors")) {
			if (force) {
				throw new CliException("--force는 cozy sync agents에만 씁니다.");
			}
			Plan plan = new Plan(project.dir());
			String next = BehaviorRecipe.sync(plan, project);
			report("sync behaviors", plan, List.of(), next, false, dryRun, json, out);
			return;
		}
		if (rest.size() != 1 || !rest.getFirst().equals("agents")) {
			throw new CliException("cozy sync 뒤에는 agents나 behaviors를 적습니다 (cozy sync agents [--force], cozy sync behaviors).");
		}
		Plan plan = new Plan(project.dir());
		String next = AgentDocs.sync(plan, force);
		report("sync agents", plan, List.of(), next, false, dryRun, json, out);
	}

	/** {@code cozy attach}, {@code cozy remove behavior}, {@code cozy rename behavior}: 화면 안의 동작을 템플릿에 붙이고, 지우고, 이름을 바꾼다 */
	public static void behaviors(String action, List<String> rest, AppProject project, Supplier<FrameworkJar> framework,
			LocalDateTime now, boolean dryRun, boolean json, PrintStream out) {
		Context ctx = new Context(project, framework, now);
		String next = switch (action) {
			case "attach" -> BehaviorCommands.attach(ctx, rest);
			case "remove" -> BehaviorCommands.remove(ctx, rest);
			default -> BehaviorCommands.rename(ctx, rest);
		};
		String name = action.equals("attach") ? "attach" : action + " behavior";
		report(name, ctx.plan(), List.of(), next, false, dryRun, json, out);
	}

	/** 한 계획으로 묶을 수 있는 명령 */
	public static final List<String> TOGETHER = List.of("add behavior", "remove behavior", "rename behavior", "attach");

	/**
	 * 화면 안의 동작 명령 여럿({@link #TOGETHER})을 한 계획으로 실행한다. 뒤의 명령은 앞의 명령이 계획한 내용을 읽고, 하나라도 거절하면 아무 파일도
	 * 쓰지 않는다. 같은 명령을 터미널에서 차례로 실행한 것과 같은 파일이 된다. 다음 할 일은 마지막 명령의 것이다.
	 *
	 * @param commands 명령마다 {@code cozy} 뒤의 인자 ({@code [remove, behavior, memo, body-count, --state, limit]})
	 */
	public static void together(List<List<String>> commands, AppProject project, Supplier<FrameworkJar> framework,
			LocalDateTime now, boolean dryRun, boolean json, PrintStream out) {
		if (commands.isEmpty()) {
			throw new CliException("한 계획으로 묶을 명령이 없습니다.");
		}
		Context ctx = new Context(project, framework, now);
		List<String> names = new ArrayList<>();
		String next = "";
		for (List<String> command : commands) {
			String action = command.isEmpty() ? "" : command.getFirst();
			List<String> rest = command.subList(Math.min(1, command.size()), command.size());
			String what = (action.equals("attach") || rest.isEmpty()) ? action : action + " " + rest.getFirst();
			next = switch (what) {
				case "add behavior" -> BehaviorRecipe.run(ctx, Args.parse(rest.subList(1, rest.size()))).replaceFirst("^다음: ", "");
				case "remove behavior" -> BehaviorCommands.remove(ctx, rest);
				case "rename behavior" -> BehaviorCommands.rename(ctx, rest);
				case "attach" -> BehaviorCommands.attach(ctx, rest);
				default -> throw new CliException("한 계획으로 묶는 명령은 " + String.join(", ", TOGETHER) + "입니다: " + String.join(" ", command));
			};
			names.add(what);
		}
		report(String.join(", ", names), ctx.plan(), List.of(), next, false, dryRun, json, out);
	}

	/** {@code cozy set arg …}: 템플릿의 fragment 호출 인자 가운데 문자열 하나를 바꾼다 */
	public static void set(List<String> rest, AppProject project, Supplier<FrameworkJar> framework, boolean dryRun, boolean json,
			PrintStream out) {
		if (rest.isEmpty() || !rest.getFirst().equals("arg")) {
			throw new CliException("cozy set 뒤에는 arg만 적습니다 (" + SetArg.USAGE + ").");
		}
		Plan plan = new Plan(project.dir());
		String next = SetArg.run(plan, project, framework, Args.parse(rest.subList(1, rest.size())));
		report("set arg", plan, List.of(), next, false, dryRun, json, out);
	}

	/**
	 * {@code cozy migrate alpine}: 앱 템플릿의 없앤 동작 속성을 cozy 컴포넌트로 바꾼다. {@code cozy migrate events}: 옛 이벤트 자리의
	 * 이벤트를 이벤트 계약 모듈로 옮긴다. {@code cozy migrate behaviors}: 손으로 쓴 모듈 JS를 동작 명세로 옮긴다.
	 *
	 * @param now 마이그레이션 이름의 시각 (events)
	 */
	public static void migrate(List<String> rest, AppProject project, LocalDateTime now, boolean dryRun, boolean json,
			PrintStream out) {
		String what = rest.isEmpty() ? "" : rest.getFirst();
		switch (what) {
			case "alpine" -> {
				Plan plan = new Plan(project.dir());
				List<Context.Manual> manual = new ArrayList<>();
				String next = MigrateAlpine.run(plan, manual, project, rest);
				report("migrate alpine", plan, manual, next, false, dryRun, json, out);
			}
			case "events" -> {
				Context ctx = new Context(project, () -> {
					throw new IllegalStateException("cozy migrate events는 cozy-ui를 읽지 않습니다");
				}, now);
				String next = MigrateEvents.run(ctx, rest);
				report("migrate events", ctx.plan(), ctx.manual(), next, false, dryRun, json, out);
			}
			case "behaviors" -> {
				Context ctx = new Context(project, () -> {
					throw new IllegalStateException("cozy migrate behaviors는 cozy-ui를 읽지 않습니다");
				}, now);
				String next = MigrateBehaviors.run(ctx, rest);
				report("migrate behaviors", ctx.plan(), ctx.manual(), next, false, dryRun, json, out);
			}
			default -> throw new CliException("cozy migrate 뒤에는 alpine, events, behaviors를 적습니다 (" + MigrateAlpine.USAGE + ", "
					+ MigrateEvents.USAGE + ", " + MigrateBehaviors.USAGE + ").");
		}
	}

	/**
	 * 계획을 보여 주고, {@code --dry-run}이 아니면 적용한 뒤 남은 빈칸과 다음 할 일을 알린다.
	 *
	 * @param manual 명령이 고치지 못해 손으로 할 일
	 * @param withSlots 바뀐 파일의 빈칸을 알리는가 (에이전트 문서는 빈칸 표시를 설명할 뿐이라 알리지 않는다)
	 */
	/** 계획에 싣는 바뀐 뒤의 글의 최대 크기 (UTF-8 바이트) */
	public static final int AFTER_LIMIT = 256 * 1024;

	private static void report(String recipe, Plan plan, List<Context.Manual> manual, String next, boolean withSlots,
			boolean dryRun, boolean json, PrintStream out) {
		if (!dryRun) {
			plan.apply();
		}
		List<Violation> slots = withSlots ? plan.slots() : List.of();
		String status = plan.changesNothing()
				? (plan.keptDifferent() ? "바뀐 것 없음" : "바뀐 것 없음(이미 같은 내용이 있습니다)")
				: dryRun ? "계획만 보였습니다(--dry-run)" : "적용했습니다";
		String nextLine = slots.isEmpty() ? next : "빈칸을 채우고 cozy check, 그다음 mvn verify. " + next;
		if (json) {
			List<Object> changes = new ArrayList<>();
			for (Plan.Change c : plan.changes()) {
				Map<String, Object> change = Describe.ordered("action", c.action().name().toLowerCase(), "file", c.file(), "detail",
						c.detail());
				// 계획이면 바뀐 뒤의 글을 싣는다 (화면이 지금 파일과 견준다). 너무 크면 싣지 않는다
				boolean written = c.action() == Plan.Action.CREATE || c.action() == Plan.Action.EDIT;
				if (dryRun && written && c.content() != null) {
					if (c.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= AFTER_LIMIT) {
						change.put("after", c.content());
					}
					else {
						change.put("afterOmitted", true);
					}
				}
				changes.add(change);
			}
			List<Object> slotList = new ArrayList<>();
			slots.forEach((s) -> slotList.add(Describe.ordered("slot", s.reference().replace(SlotRules.MARKER, "").strip(), "file",
					s.location(), "line", s.line(), "what", s.message())));
			List<Object> manualList = new ArrayList<>();
			manual.forEach((m) -> manualList.add(Describe.ordered("file", m.file(), "what", m.what(), "snippet", m.snippet())));
			Map<String, Object> model = Describe.ordered("recipe", recipe, "applied", !dryRun && !plan.changesNothing(),
					"plan", changes, "slots", slotList, "manual", manualList, "next", nextLine);
			out.print(Json.write(model));
			return;
		}
		out.println("[cozy] 계획");
		for (Plan.Change change : plan.changes()) {
			out.println("  " + change.action().label() + "  " + change.file() + ((change.detail() != null) ? "    " + change.detail() : ""));
		}
		out.println("[cozy] " + status + (slots.isEmpty() ? "" : ". 빈칸 " + slots.size() + "곳")
				+ (manual.isEmpty() ? "" : ". 남은 일 " + manual.size() + "곳"));
		for (Violation slot : slots) {
			out.println("  " + slot.location() + ":" + slot.line());
			out.println("    " + slot.reference().replace(SlotRules.MARKER, "").strip() + " — " + slot.message());
		}
		for (Context.Manual m : manual) {
			out.println("  " + m.file());
			out.println("    " + m.what());
			m.snippet().lines().forEach((line) -> out.println("      " + line));
		}
		out.println("[cozy] 다음: " + nextLine);
	}
}
