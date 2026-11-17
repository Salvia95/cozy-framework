package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.Attach;
import io.salvia95.cozy.cli.internal.behavior.Attach.Target;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Component;
import io.salvia95.cozy.cli.internal.behavior.FrameworkBehaviors;

/**
 * {@code cozy attach}: 템플릿의 요소 하나에 컴포넌트를 붙이고 뗀다. {@code cozy remove behavior}, {@code cozy rename behavior}: 동작
 * 명세의 컴포넌트(또는 그 항목)를 지우고 이름을 바꾸며, 붙인 곳도 함께 고친다. 형식: docs/conventions/cli.md
 */
public final class BehaviorCommands {

	public static final String ATTACH_USAGE = "cozy attach <컴포넌트> --at <템플릿>:<줄> [--data 이름=값 …] [--on 이벤트:동작 …] [--text 이름] "
			+ "[--show 이름] [--bind 속성:이름 …] [--model 상태] [--cloak] [--detach]";

	public static final String REMOVE_USAGE = "cozy remove behavior <모듈> <동작> [--state 이름 …] [--computed 이름 …] [--action 이름 …] "
			+ "[--listen 모듈:이벤트 …]";

	public static final String RENAME_USAGE = "cozy rename behavior <모듈> <동작> <새 동작>";

	private static final String TEMPLATES = "src/main/resources/templates/";

	private static final Pattern AT = Pattern.compile("(?:templates/)?([a-z0-9_/-]+?)(?:\\.html)?:([1-9][0-9]*)");

	private BehaviorCommands() {
	}

	static String attach(Context ctx, List<String> rest) {
		List<String> args = new ArrayList<>(rest);
		boolean cloak = args.remove("--cloak");
		boolean detach = args.remove("--detach");
		Args parsed = Args.parse(args);
		parsed.only("attach", 1, "at", "data", "on", "text", "show", "bind", "model");
		String name = parsed.positional(0, "컴포넌트 이름");
		Matcher at = AT.matcher(parsed.required("at"));
		if (!at.matches()) {
			throw new CliException("--at은 템플릿:줄 모양입니다 (예: --at memo/internal/fields:7, cozy describe가 보이는 위치): "
					+ parsed.option("at"));
		}
		String template = at.group(1);
		int line = Integer.parseInt(at.group(2));
		String file = TEMPLATES + template + ".html";
		String text = ctx.plan().current(file);
		if (text == null) {
			throw new CliException("템플릿 " + file + "가 없습니다.");
		}
		Target target = target(ctx, name, template);
		List<String[]> data = new ArrayList<>();
		for (String entry : parsed.all("data")) {
			int eq = entry.indexOf('=');
			if (eq <= 0 || !entry.substring(0, eq).matches("[a-z][a-z0-9-]*")) {
				throw new CliException("--data는 이름=값 모양입니다 (예: --data limit=5000, --data \"length=${bodyLength}\"): " + entry);
			}
			data.add(new String[] { entry.substring(0, eq), entry.substring(eq + 1) });
		}
		List<String[]> bindings = new ArrayList<>();
		parsed.all("on").forEach((v) -> bindings.add(Attach.on(v)));
		parsed.all("bind").forEach((v) -> bindings.add(Attach.bind(v)));
		single(parsed, "show").forEach((v) -> bindings.add(new String[] { "x-show", v }));
		single(parsed, "text").forEach((v) -> bindings.add(new String[] { "x-text", v }));
		single(parsed, "model").forEach((v) -> bindings.add(new String[] { "x-model", v }));
		Attach.Result result = Attach.apply(text, "templates/" + template, line, target,
				new Attach.Request(data, bindings, cloak, detach));
		if (result.detail() == null) {
			ctx.plan().edit(file, text, "같은 것을 붙였음");
		}
		else {
			ctx.plan().edit(file, result.text(), result.detail());
		}
		return "cozy check, 그다음 mvn verify로 확인합니다";
	}

	static String remove(Context ctx, List<String> rest) {
		if (rest.isEmpty() || !rest.getFirst().equals("behavior")) {
			throw new CliException("cozy remove 뒤에는 behavior를 적습니다 (" + REMOVE_USAGE + ").");
		}
		Args args = Args.parse(rest.subList(1, rest.size()));
		args.only("remove behavior", 2, "state", "computed", "action", "listen");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String key = Names.camelOfKebab(Names.kebab(args.positional(1, "동작"), "동작"));
		ctx.module(module);
		BehaviorSpec spec = spec(ctx, module);
		Component component = spec.components().get(key);
		if (component == null) {
			throw new CliException(module + " 모듈의 동작 명세에 " + key + "가 없습니다. 있는 것: " + spec.components().keySet());
		}
		String name = component.name(module);
		Target target = Target.of(module, component);
		Map<String, Component> components = new LinkedHashMap<>(spec.components());
		Set<String> names = new LinkedHashSet<>();
		names.addAll(args.all("state"));
		names.addAll(args.all("computed"));
		names.addAll(args.all("action"));
		if (names.isEmpty() && args.all("listen").isEmpty()) {
			components.remove(key);
			for (String file : templates(ctx, module)) {
				String text = ctx.plan().current(file);
				String detached = Attach.detachEverywhere(text, target);
				if (!detached.equals(text)) {
					ctx.plan().edit(file, detached, "뗌 " + name);
				}
			}
			BehaviorSpec next = new BehaviorSpec(module, components);
			if (components.isEmpty()) {
				ctx.plan().delete(BehaviorRecipe.specFile(module), "마지막 컴포넌트 " + name + "를 지움");
				if (ctx.plan().current(BehaviorRecipe.scriptFile(module)) != null) {
					ctx.plan().delete(BehaviorRecipe.scriptFile(module), "동작 명세가 없음");
				}
			}
			else {
				BehaviorRecipe.write(ctx.plan(), next, "컴포넌트 " + name + "를 지움");
			}
			return "cozy check, 그다음 mvn verify로 확인합니다";
		}
		Component edited = new Component(component.key(), component.purpose(), component.state(), component.computed(),
				component.actions(), component.listens());
		List<String> changes = new ArrayList<>();
		drop(edited.state(), args.all("state"), "상태", changes);
		drop(edited.computed(), args.all("computed"), "계산", changes);
		drop(edited.actions(), args.all("action"), "동작", changes);
		drop(edited.listens(), args.all("listen"), "받는 이벤트", changes);
		List<String> used = new ArrayList<>();
		for (String file : templates(ctx, module)) {
			for (int line : Attach.uses(ctx.plan().current(file), name, names)) {
				used.add(file.substring(TEMPLATES.length()) + ":" + line);
			}
		}
		if (!used.isEmpty()) {
			throw new CliException(String.join(", ", names) + "를 템플릿이 부릅니다(" + String.join(", ", used) + "). 그 요소에서 먼저 뗍니다 (cozy attach "
					+ name + " --at <템플릿>:<줄> --detach).");
		}
		components.put(key, edited);
		BehaviorRecipe.write(ctx.plan(), new BehaviorSpec(module, components), "컴포넌트 " + name + ": " + String.join(", ", changes));
		return "cozy check, 그다음 mvn verify로 확인합니다";
	}

	static String rename(Context ctx, List<String> rest) {
		if (rest.isEmpty() || !rest.getFirst().equals("behavior")) {
			throw new CliException("cozy rename 뒤에는 behavior를 적습니다 (" + RENAME_USAGE + ").");
		}
		Args args = Args.parse(rest.subList(1, rest.size()));
		args.only("rename behavior", 3);
		String module = Names.module(args.positional(0, "모듈 이름"));
		String from = Names.camelOfKebab(Names.kebab(args.positional(1, "동작"), "동작"));
		String to = Names.camelOfKebab(Names.kebab(args.positional(2, "새 동작"), "새 동작"));
		ctx.module(module);
		BehaviorSpec spec = spec(ctx, module);
		Component component = spec.components().get(from);
		if (component == null) {
			throw new CliException(module + " 모듈의 동작 명세에 " + from + "가 없습니다. 있는 것: " + spec.components().keySet());
		}
		if (spec.components().containsKey(to)) {
			throw new CliException(module + " 모듈에 " + to + "가 이미 있습니다.");
		}
		Map<String, Component> components = new LinkedHashMap<>();
		spec.components().forEach((k, c) -> components.put(k.equals(from) ? to
				: k, k.equals(from) ? new Component(to, c.purpose(), c.state(), c.computed(), c.actions(), c.listens()) : c));
		String oldName = component.name(module);
		String newName = components.get(to).name(module);
		BehaviorRecipe.write(ctx.plan(), new BehaviorSpec(module, components), "이름 " + oldName + " → " + newName);
		for (String file : templates(ctx, module)) {
			String text = ctx.plan().current(file);
			String renamed = Attach.rename(text, oldName, newName);
			if (!renamed.equals(text)) {
				ctx.plan().edit(file, renamed, "x-data " + oldName + " → " + newName);
			}
		}
		return "cozy check, 그다음 mvn verify로 확인합니다";
	}

	/** 붙일 컴포넌트: cozy로 시작하면 프레임워크 명세, 아니면 모듈의 동작 명세 */
	private static Target target(Context ctx, String name, String template) {
		if (name.startsWith("cozy")) {
			String text = ctx.framework().behaviors();
			if (text == null) {
				throw new CliException("cozy-ui에 프레임워크 컴포넌트의 명세(cozy/behaviors.yml)가 없습니다. 이 판의 cozy-ui로는 붙일 이름을 확인할 수 없습니다.");
			}
			FrameworkBehaviors.Component component = FrameworkBehaviors.read(text, "cozy/behaviors.yml").components().get(name);
			if (component == null) {
				throw new CliException("프레임워크 컴포넌트 " + name + "가 없습니다. 있는 것: "
						+ FrameworkBehaviors.read(text, "cozy/behaviors.yml").components().keySet());
			}
			return Target.of(component);
		}
		for (String module : ctx.project().modules().keySet()) {
			if (name.length() > module.length() && name.startsWith(module) && Character.isUpperCase(name.charAt(module.length()))) {
				String key = Character.toLowerCase(name.charAt(module.length())) + name.substring(module.length() + 1);
				Component component = spec(ctx, module).components().get(key);
				if (component == null) {
					continue;
				}
				if (!template.startsWith(module + "/")) {
					throw new CliException(name + "는 " + module + " 모듈의 컴포넌트라 " + module + " 템플릿(templates/" + module + "/…)에만 붙입니다.");
				}
				return Target.of(module, component);
			}
		}
		throw new CliException("컴포넌트 " + name + "가 없습니다. 앱 컴포넌트는 cozy add behavior <모듈> <동작>으로 먼저 선언하고, 프레임워크 컴포넌트는 "
				+ "이름이 cozy로 시작합니다.");
	}

	private static BehaviorSpec spec(Context ctx, String module) {
		String text = ctx.plan().current(BehaviorRecipe.specFile(module));
		return (text == null) ? BehaviorSpec.empty(module) : BehaviorSpec.read(module, text, BehaviorRecipe.specFile(module));
	}

	/** 모듈의 템플릿 ({@code templates/{모듈}/} 아래) */
	private static List<String> templates(Context ctx, String module) {
		Path dir = ctx.project().dir().resolve(TEMPLATES + module);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(dir)) {
			return files.filter((f) -> f.toString().endsWith(".html"))
				.sorted()
				.map((f) -> ctx.project().dir().relativize(f).toString().replace('\\', '/'))
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static <T> void drop(Map<String, T> section, List<String> names, String label, List<String> changes) {
		for (String name : names) {
			if (section.remove(name) == null) {
				throw new CliException(label + " '" + name + "'이 없습니다. 있는 것: " + section.keySet());
			}
			changes.add(label + " " + name + " 지움");
		}
	}

	private static List<String> single(Args args, String option) {
		List<String> values = args.all(option);
		if (values.size() > 1) {
			throw new CliException("--" + option + "는 한 번만 씁니다.");
		}
		return values;
	}
}
