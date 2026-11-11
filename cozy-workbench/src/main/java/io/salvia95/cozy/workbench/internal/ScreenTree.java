package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Entry;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Param;
import io.salvia95.cozy.workbench.internal.WorkbenchEngine.OutlineNode;

/**
 * 화면 구조를 보이는 트리로 바꾼다. fragment의 삽입은 매니페스트의 표시 이름으로, 영역은 감싸는 삽입의 파라미터 표시 이름으로 부른다.
 * 표시 이름이 없으면 종류(컴포넌트, 프래그먼트, 영역)로 부른다. 식별자는 이름 뒤에 따로 둔다. 아이콘은 구조가 아니므로 뺀다.
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
	 * @param label 사람에게 보일 이름 ({@code 목록 탐색}, {@code 필터 영역})
	 * @param detail 소스에서 찾을 식별자 ({@code list-with-filter}, {@code cozyFilter})
	 * @param kindLabel 종류 ({@code 패턴}, {@code 컴포넌트}, {@code 영역})
	 * @param code 에이전트에게 넘길 이름. 식별자로만 쓴다 ({@code list-with-filter (패턴)})
	 * @param location {@code 템플릿 파일:줄}
	 * @param line 요소가 시작하는 줄
	 * @param endLine 요소가 끝나는 줄
	 * @param target 삽입하는 템플릿 이름. 영역과 선언이면 빈 문자열
	 * @param id 매니페스트의 id ({@code component/badge}). 매니페스트에 없으면 빈 문자열
	 * @param purpose 매니페스트의 용도 한 줄. 없으면 빈 문자열
	 * @param args 삽입에 넘긴 인자 (선언한 파라미터 순서)
	 */
	record Item(int depth, String kind, String label, String detail, String kindLabel, String code, String location, int line,
			int endLine, String target, String id, String purpose, List<Arg> args) {

		/** 소스의 줄 범위 ({@code 40–54}, 한 줄이면 {@code 47}) */
		public String lines() {
			return (this.endLine > this.line) ? this.line + "–" + this.endLine : String.valueOf(this.line);
		}
	}

	/**
	 * 삽입에 넘긴 인자 하나.
	 *
	 * @param label 사람에게 보일 이름. 파라미터의 표시 이름이나 이름, 모르면 순번
	 * @param name 파라미터 이름. 모르면 빈 문자열
	 * @param value 템플릿에 적힌 값 ({@code '고정'}, {@code ~{::#filter}}, {@code ${result.page}})
	 * @param description 파라미터의 설명. 없으면 빈 문자열
	 */
	record Arg(String label, String name, String value, String description) {
	}

	/** 트리의 마디: 줄 하나와 그 안의 줄들 */
	record Node(Item item, List<Node> children) {
	}

	/** 깊이로 적힌 줄들을 마디로 묶는다 */
	static List<Node> nest(List<Item> items) {
		List<Node> roots = new ArrayList<>();
		List<Node> open = new ArrayList<>();
		for (Item item : items) {
			while (open.size() > item.depth()) {
				open.removeLast();
			}
			Node node = new Node(item, new ArrayList<>());
			(open.isEmpty() ? roots : open.getLast().children()).add(node);
			open.add(node);
		}
		return roots;
	}

	private static List<Arg> args(OutlineNode node, Entry entry) {
		List<Arg> args = new ArrayList<>();
		for (int i = 0; i < node.args().size(); i++) {
			Param param = (entry != null && i < entry.params().size()) ? entry.params().get(i) : null;
			args.add(new Arg((param != null) ? param.displayName() : "인자 " + (i + 1), (param != null) ? param.name() : "",
					node.args().get(i), (param != null) ? param.description() : ""));
		}
		return args;
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
					Param parameter = parameter(enclosing, node.id(), manifest);
					if (parameter == null) {
						items.add(item(depth, node, "영역", "#" + node.id(), "영역", "#" + node.id() + " (자리)", location, ""));
					}
					else {
						items.add(item(depth, node, parameter.label().isBlank() ? "영역" : parameter.label() + " 영역", parameter.name(), "영역",
								parameter.name() + " (#" + node.id() + ")", location, ""));
					}
				}
				case "fragment" -> items.add(item(depth, node, "프래그먼트", node.fragment(), "프래그먼트",
						node.fragment() + " (fragment 선언)", location, ""));
				default -> {
					if (!ICONS.equals(node.target())) {
						Entry entry = entry(manifest, node);
						if (entry != null && entry.isFramework()) {
							String name = entry.id().substring(entry.id().indexOf('/') + 1);
							String group = GROUP_LABELS.getOrDefault(entry.group(), entry.group());
							items.add(item(depth, node, entry.label().isBlank() ? group : entry.label(), name, group,
									name + " (" + group + ")", location, node.target(), entry));
						}
						else if (node.fragment() != null) {
							items.add(item(depth, node, (entry != null && !entry.label().isBlank()) ? entry.label() : "프래그먼트",
									node.fragment(), "프래그먼트",
									node.fragment() + " (" + target(node) + ")", location, node.target(), entry));
						}
						else {
							items.add(item(depth, node, "템플릿", target(node), "템플릿", target(node) + " (템플릿)", location, node.target()));
						}
					}
				}
			}
			enclosing.add(node);
		}
		return items;
	}

	private static Item item(int depth, OutlineNode node, String label, String detail, String kindLabel, String code,
			String location, String target) {
		return item(depth, node, label, detail, kindLabel, code, location, target, null);
	}

	private static Item item(int depth, OutlineNode node, String label, String detail, String kindLabel, String code,
			String location, String target, Entry entry) {
		boolean insertion = !node.kind().equals("slot") && !node.kind().equals("fragment");
		return new Item(depth, node.kind(), label, detail, kindLabel, code, location, node.line(), node.endLine(), target,
				(entry != null) ? entry.id() : "", (entry != null) ? entry.purpose() : "", insertion ? args(node, entry) : List.of());
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

	/** 가장 가까운 감싸는 삽입이 {@code ~{::#id}}를 넘기는 자리의 파라미터. 모르면 {@code null} */
	private static Param parameter(List<OutlineNode> enclosing, String id, FragmentManifest manifest) {
		for (int i = enclosing.size() - 1; i >= 0; i--) {
			OutlineNode node = enclosing.get(i);
			for (int index = 0; index < node.args().size(); index++) {
				Matcher m = SLOT_ARGUMENT.matcher(node.args().get(index));
				if (m.matches() && m.group(1).equals(id)) {
					Entry entry = entry(manifest, node);
					return (entry != null && index < entry.params().size()) ? entry.params().get(index) : null;
				}
			}
		}
		return null;
	}
}
