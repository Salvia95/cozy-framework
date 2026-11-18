package io.salvia95.cozy.cli.internal.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec;
import io.salvia95.cozy.cli.internal.behavior.BehaviorSpec.Component;
import io.salvia95.cozy.cli.internal.behavior.LegacyScript;
import io.salvia95.cozy.rules.BehaviorRules;

/**
 * {@code cozy migrate behaviors}: 손으로 쓴 모듈 JS({@code static/{모듈}/{모듈}.js})의 컴포넌트를 동작 명세로 옮기고, 그 명세에서 JS를 다시
 * 만든다. 한 모듈의 컴포넌트를 모두 낱말로 적을 수 있을 때만 그 모듈을 바꾼다(한 모듈에 손으로 쓴 JS와 만든 JS를 함께 둘 수 없다). 옮길 수 없는 곳은
 * 손으로 할 일로 알리고 그 모듈은 그대로 둔다. 형식: docs/conventions/cli.md
 */
final class MigrateBehaviors {

	static final String USAGE = "cozy migrate behaviors [--purpose 컴포넌트=목적 …] [--dry-run] [--json]";

	private MigrateBehaviors() {
	}

	/** @return 다음 할 일 */
	static String run(Context ctx, List<String> rest) {
		Args args = Args.parse(rest.subList(1, rest.size())).only("cozy migrate behaviors", 0, "purpose");
		Map<String, String> purposes = new LinkedHashMap<>();
		for (String entry : args.all("purpose")) {
			List<String> parts = BehaviorSpec.split(entry, '=', "--purpose", "컴포넌트=목적 (예: memoPanel=펼친 패널을 바꾼다)");
			if (parts.get(1).contains("\n")) {
				throw new CliException("--purpose는 한 줄 문장입니다.");
			}
			purposes.put(parts.get(0), parts.get(1));
		}
		Plan plan = ctx.plan();
		List<String> moved = new ArrayList<>();
		List<String> named = new ArrayList<>();
		for (String module : ctx.project().modules().keySet().stream().sorted().toList()) {
			String file = BehaviorRecipe.scriptFile(module);
			String script = plan.current(file);
			if (script == null || BehaviorRules.isGenerated(script)) {
				continue;
			}
			LegacyScript.Result result = LegacyScript.read(module, script, purposes);
			result.components().forEach((c) -> named.add(c.name(module)));
			result.problems().forEach((p) -> named.add(p.component()));
			if (!result.problems().isEmpty()) {
				for (LegacyScript.Problem p : result.problems()) {
					String who = (p.component() != null) ? p.component() : "파일";
					ctx.manual(file + ":" + p.line(), who + ": " + p.what() + ". 낱말로 적을 수 없어 이 모듈은 옮기지 않았습니다",
							p.code());
				}
				continue;
			}
			String specText = plan.current(BehaviorRecipe.specFile(module));
			BehaviorSpec spec = (specText == null) ? BehaviorSpec.empty(module)
					: BehaviorSpec.read(module, specText, BehaviorRecipe.specFile(module));
			Map<String, Component> components = new LinkedHashMap<>(spec.components());
			List<String> names = new ArrayList<>();
			boolean clash = false;
			for (Component c : result.components()) {
				if (components.containsKey(c.key())) {
					ctx.manual(file, c.name(module) + ": 동작 명세에 같은 이름의 컴포넌트가 이미 있습니다. 한쪽 이름을 cozy rename behavior로 바꾼 뒤 다시 실행합니다",
							"");
					clash = true;
				}
				components.put(c.key(), c);
				names.add(c.name(module));
			}
			if (clash) {
				continue;
			}
			if (names.isEmpty()) {
				ctx.manual(file, "옮길 컴포넌트(Alpine.data 등록)가 없습니다. 이 파일이 하는 일을 cozy 컴포넌트나 동작 명세로 옮긴 뒤 파일을 지웁니다", "");
				continue;
			}
			BehaviorRecipe.write(plan, new BehaviorSpec(module, components), "손으로 쓴 JS에서 옮김: " + String.join(", ", names), true);
			moved.add(module);
		}
		for (String component : purposes.keySet()) {
			if (!named.contains(component)) {
				throw new CliException("--purpose의 컴포넌트 '" + component + "'가 손으로 쓴 모듈 JS에 없습니다. 있는 컴포넌트: "
						+ (named.isEmpty() ? "없음" : String.join(", ", named.stream().filter((n) -> n != null).toList())));
			}
		}
		if (!ctx.manual().isEmpty()) {
			return "남은 일의 컴포넌트는 손으로 쓴 JS 그대로입니다(cozy check의 C12 경고). 낱말 밖의 동작은 프레임워크 컴포넌트(cozy…)나 새 낱말이 필요합니다. "
					+ "옮긴 모듈은 cozy check로 붙인 곳과 화면 이벤트(C13)를 확인하고, 그다음 mvn verify.";
		}
		return moved.isEmpty() ? "손으로 쓴 모듈 JS가 없습니다." : "cozy check로 붙인 곳과 화면 이벤트(C13)를 확인하고, 그다음 mvn verify.";
	}
}
