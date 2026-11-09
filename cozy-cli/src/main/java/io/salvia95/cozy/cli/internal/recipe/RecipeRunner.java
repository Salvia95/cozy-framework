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
 * {@code cozy add <레시피>}와 {@code cozy link}를 실행한다. 계획을 보여 주고, {@code --dry-run}이 아니면 적용한 뒤 남은 빈칸과 다음 할 일을
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
			case "enable events", "enable " -> StageTwoRecipes.enableEvents(ctx, parsed);
			case "query" -> MethodRecipes.query(ctx, parsed);
			case "command" -> MethodRecipes.command(ctx, parsed);
			case "event-version" -> EventVersionRecipe.run(ctx, parsed);
			case "field" -> throw new CliException("cozy add field는 보류한 레시피입니다. 필드는 손으로 더하고 cozy check와 "
					+ "mvn verify로 확인합니다.");
			default -> throw new CliException(recipe.startsWith("enable ") ? "cozy enable 뒤에는 events를 적습니다 (cozy enable events)."
					: "모르는 레시피입니다: " + recipe + ". cozy recipes로 목록을 봅니다.");
		};
		next = next.replaceFirst("^다음: ", "");
		Plan plan = ctx.plan();
		if (!dryRun) {
			plan.apply();
		}
		List<Violation> slots = plan.slots();
		String status = plan.changesNothing() ? "바뀐 것 없음(이미 같은 내용이 있습니다)" : dryRun ? "계획만 보였습니다(--dry-run)" : "적용했습니다";
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
