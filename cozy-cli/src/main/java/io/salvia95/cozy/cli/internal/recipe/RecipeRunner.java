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
 * {@code cozy add <레시피>}, {@code cozy link}, {@code cozy enable events}, {@code cozy sync agents}를 실행한다. 계획을 보여 주고, {@code --dry-run}이 아니면 적용한 뒤 남은 빈칸과 다음 할 일을
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
		report(recipe, ctx.plan(), next.replaceFirst("^다음: ", ""), true, dryRun, json, out);
	}

	/** {@code cozy sync agents [--force]}: 앱의 에이전트 문서를 이 cozy 명령에 든 것과 같게 맞춘다 */
	public static void sync(List<String> rest, AppProject project, boolean force, boolean dryRun, boolean json,
			PrintStream out) {
		if (rest.size() != 1 || !rest.getFirst().equals("agents")) {
			throw new CliException("cozy sync 뒤에는 agents만 적습니다 (cozy sync agents [--force]).");
		}
		Plan plan = new Plan(project.dir());
		String next = AgentDocs.sync(plan, force);
		report("sync agents", plan, next, false, dryRun, json, out);
	}

	/**
	 * 계획을 보여 주고, {@code --dry-run}이 아니면 적용한 뒤 남은 빈칸과 다음 할 일을 알린다.
	 *
	 * @param withSlots 바뀐 파일의 빈칸을 알리는가 (에이전트 문서는 빈칸 표시를 설명할 뿐이라 알리지 않는다)
	 */
	private static void report(String recipe, Plan plan, String next, boolean withSlots, boolean dryRun, boolean json,
			PrintStream out) {
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
			plan.changes().forEach((c) -> changes.add(Describe.ordered("action", c.action().name().toLowerCase(), "file", c.file(),
					"detail", c.detail())));
			List<Object> slotList = new ArrayList<>();
			slots.forEach((s) -> slotList.add(Describe.ordered("slot", s.reference().replace(SlotRules.MARKER, "").strip(), "file",
					s.location(), "line", s.line(), "what", s.message())));
			Map<String, Object> model = Describe.ordered("recipe", recipe, "applied", !dryRun && !plan.changesNothing(),
					"plan", changes, "slots", slotList, "next", nextLine);
			out.print(Json.write(model));
			return;
		}
		out.println("[cozy] 계획");
		for (Plan.Change change : plan.changes()) {
			out.println("  " + change.action().label() + "  " + change.file() + ((change.detail() != null) ? "    " + change.detail() : ""));
		}
		out.println("[cozy] " + status + (slots.isEmpty() ? "" : ". 빈칸 " + slots.size() + "곳"));
		for (Violation slot : slots) {
			out.println("  " + slot.location() + ":" + slot.line());
			out.println("    " + slot.reference().replace(SlotRules.MARKER, "").strip() + " — " + slot.message());
		}
		out.println("[cozy] 다음: " + nextLine);
	}
}
