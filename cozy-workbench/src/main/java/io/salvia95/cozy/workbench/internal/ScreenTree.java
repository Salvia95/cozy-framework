package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Entry;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine.OutlineNode;

/**
 * 화면 구조를 보이는 트리로 바꾼다. 프레임워크 fragment의 삽입은 매니페스트의 이름과 묶음(패턴, 컴포넌트, 레이아웃)으로, 앱 fragment의 삽입은
 * fragment 이름과 템플릿으로, 자리는 감싸는 삽입의 파라미터 이름으로 부른다. 아이콘은
 * 구조가 아니므로 뺀다.
 */
final class ScreenTree {

	private ScreenTree() {
	}

	/** 깊이는 여기까지만 들여 쓴다 */
	static final int MAX_DEPTH = 8;

	private static final String ICONS = "cozy/icon/tabler";

	private static final Pattern SLOT_ARGUMENT = Pattern.compile("~\\{\\s*(?:this)?\\s*::\\s*#([A-Za-z_][A-Za-z0-9_\\-]*)\\s*}");

	private static final Map<String, String> GROUP_LABELS = Map.of("pattern", "패턴", "component", "컴포넌트", "layout", "레이아웃");

	/**
	 * 트리의 줄 하나.
	 *
	 * @param depth 들여쓰기 (0부터 {@link #MAX_DEPTH}까지)
	 * @param kind {@code fragment}, {@code replace}, {@code insert}, {@code slot}
	 * @param label 이름
	 * @param detail 종류나 요소 id
	 * @param location {@code 템플릿 파일:줄}
	 * @param line 요소가 시작하는 줄
	 * @param endLine 요소가 끝나는 줄
	 * @param target 삽입하는 템플릿 이름. 자리와 선언이면 빈 문자열
	 */
	record Item(int depth, String kind, String label, String detail, String location, int line, int endLine, String target) {
	}

	/**
	 * @param file 위치에 적을 템플릿 파일 ({@code templates/memo/api/list.html})
	 */
	static List<Item> of(List<OutlineNode> outline, FragmentManifest manifest, String file) {
		List<Item> items = new ArrayList<>();
		// 감싸는 삽입: 깊이 → 마디
		List<OutlineNode> enclosing = new ArrayList<>();
		for (OutlineNode node : outline) {
			while (enclosing.size() > node.depth()) {
				enclosing.removeLast();
			}
			String location = file + ":" + node.line();
			int depth = Math.min(node.depth(), MAX_DEPTH);
			switch (node.kind()) {
				case "slot" -> {
					String parameter = parameter(enclosing, node.id(), manifest);
					items.add((parameter != null) ? item(depth, node, parameter, "#" + node.id(), location, "")
							: item(depth, node, "#" + node.id(), "자리", location, ""));
				}
				case "fragment" -> items.add(item(depth, node, node.fragment(), "fragment 선언", location, ""));
				default -> {
					if (!ICONS.equals(node.target())) {
						Entry entry = entry(manifest, node);
						items.add((entry != null && entry.isFramework())
								? item(depth, node, entry.id().substring(entry.id().indexOf('/') + 1),
										GROUP_LABELS.getOrDefault(entry.group(), entry.group()), location, node.target())
								: item(depth, node, (node.fragment() != null) ? node.fragment() : target(node),
										(node.fragment() != null) ? target(node) : "템플릿", location, node.target()));
					}
				}
			}
			enclosing.add(node);
		}
		return items;
	}

	private static Item item(int depth, OutlineNode node, String label, String detail, String location, String target) {
		return new Item(depth, node.kind(), label, detail, location, node.line(), node.endLine(), target);
	}

	private static String target(OutlineNode node) {
		return node.target().isEmpty() ? "이 템플릿" : node.target();
	}

	private static Entry entry(FragmentManifest manifest, OutlineNode node) {
		if (node.fragment() == null) {
			return null;
		}
		return manifest.fragments()
			.stream()
			.filter((e) -> e.template().equals(node.target()) && e.fragmentName().equals(node.fragment()))
			.findFirst()
			.orElse(null);
	}

	/** 가장 가까운 감싸는 삽입이 {@code ~{::#id}}를 넘기는 자리의 파라미터 이름. 모르면 {@code null} */
	private static String parameter(List<OutlineNode> enclosing, String id, FragmentManifest manifest) {
		for (int i = enclosing.size() - 1; i >= 0; i--) {
			OutlineNode node = enclosing.get(i);
			for (int index = 0; index < node.args().size(); index++) {
				Matcher m = SLOT_ARGUMENT.matcher(node.args().get(index));
				if (m.matches() && m.group(1).equals(id)) {
					Entry entry = entry(manifest, node);
					return (entry != null && index < entry.params().size()) ? entry.params().get(index).name() : null;
				}
			}
		}
		return null;
	}
}
