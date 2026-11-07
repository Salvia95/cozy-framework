package io.salvia95.cozy.cli.internal.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.salvia95.cozy.cli.internal.CliException;

/** 레시피 인자: 위치 인자와 {@code --이름 값}(여러 번 쓸 수 있다) */
public final class Args {

	/** 값을 받는 옵션 */
	static final Set<String> VALUED = Set.of("label", "entity", "icon", "field", "publish", "pattern", "title", "nav", "param",
			"purpose", "on", "name", "uses");

	private final List<String> positional = new ArrayList<>();

	private final Map<String, List<String>> options = new LinkedHashMap<>();

	public static Args parse(List<String> args) {
		Args result = new Args();
		for (int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			if (arg.startsWith("--")) {
				String name = arg.substring(2);
				if (!VALUED.contains(name)) {
					throw new CliException("모르는 옵션입니다: " + arg + ". cozy recipes로 레시피의 인자를 보세요.");
				}
				if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
					throw new CliException(arg + " 뒤에 값을 적습니다.");
				}
				result.options.computeIfAbsent(name, (k) -> new ArrayList<>()).add(args.get(++i));
			}
			else {
				result.positional.add(arg);
			}
		}
		return result;
	}

	/** n번째 위치 인자 (0부터) */
	public String positional(int index, String what) {
		if (index >= this.positional.size()) {
			throw new CliException(what + "를 적습니다.");
		}
		return this.positional.get(index);
	}

	public int positionalCount() {
		return this.positional.size();
	}

	public String option(String name) {
		List<String> values = this.options.get(name);
		if (values == null) {
			return null;
		}
		if (values.size() > 1) {
			throw new CliException("--" + name + "는 한 번만 씁니다.");
		}
		return values.getFirst();
	}

	public String required(String name) {
		String value = option(name);
		if (value == null || value.isBlank()) {
			throw new CliException("--" + name + "가 필요합니다.");
		}
		return value;
	}

	public List<String> all(String name) {
		return this.options.getOrDefault(name, List.of());
	}

	/** 이 레시피가 받지 않는 옵션이 있으면 실패한다 */
	public Args only(String recipe, int positionals, String... allowed) {
		if (this.positional.size() > positionals) {
			throw new CliException(recipe + "는 위치 인자를 " + positionals + "개 받습니다: " + this.positional);
		}
		Set<String> ok = Set.of(allowed);
		for (String name : this.options.keySet()) {
			if (!ok.contains(name)) {
				throw new CliException(recipe + "는 --" + name + "를 받지 않습니다. cozy recipes로 인자를 보세요.");
			}
		}
		return this;
	}
}
