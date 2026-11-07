package io.salvia95.cozy.cli.internal.recipe;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.rules.Layer;

/**
 * 내비게이션 항목: 모듈의 {@code internal/config/{M}Configuration.java}에 {@code CozyNavItem} 빈을 덧붙인다. 덧붙일 자리는
 * {@code // cozy:append nav} 표시다. 파일이 없으면 만들고, 표시가 없으면 실패한다(ADR-0032 §2).
 */
final class Navigation {

	static final String ANCHOR = "\t// cozy:append nav";

	private Navigation() {
	}

	static void add(Context ctx, String module, String bean, String label, String path, String icon) {
		ctx.requireIcon(icon);
		String M = Names.capitalize(module);
		String pkg = ctx.rolePackage(module, Layer.CONFIG);
		String file = ctx.javaFile(pkg, M + "Configuration");
		String current = ctx.plan().current(file);
		boolean created = current == null;
		if (created) {
			current = new Template().put("pkg", pkg).put("M", M).put("module", module).render("configuration.java");
		}
		if (current.contains("\"" + path + "\"") && current.contains("CozyNavItem")) {
			return;
		}
		int at = current.indexOf(ANCHOR);
		if (at < 0) {
			throw new CliException(file + "에 덧붙일 자리 표시(// cozy:append nav)가 없습니다. 내비게이션 항목을 손으로 더하세요: "
					+ "@Bean CozyNavItem " + bean + "NavItem() { return new CozyNavItem(\"" + label + "\", \"" + path + "\", 순서, \"" + icon
					+ "\"); }");
		}
		int order = (ctx.navItemCount() + 1) * 10;
		String method = "\t@Bean\n\tCozyNavItem " + bean + "NavItem() {\n\t\treturn new CozyNavItem(\"" + label + "\", \"" + path + "\", "
				+ order + ", \"" + icon + "\");\n\t}\n\n";
		String next = current.substring(0, at) + method + current.substring(at);
		if (created) {
			ctx.plan().create(file, next);
		}
		else {
			ctx.plan().edit(file, next, "내비게이션 " + label);
		}
	}
}
