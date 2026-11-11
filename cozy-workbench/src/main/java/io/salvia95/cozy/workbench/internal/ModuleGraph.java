package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 모듈 의존성 다이어그램의 배치. {@code cozy describe}의 모듈 목록으로 마디와 선의 좌표를 계산한다. 그리는 일은 템플릿이 한다.
 *
 * <ul>
 * <li>열: 참조하는 모듈이 왼쪽, 참조되는 모듈이 오른쪽이다. 의존하는 모듈이 없는 모듈이 맨 오른쪽 열이다.</li>
 * <li>의존 선: 왼쪽 마디의 오른쪽 가운데에서 오른쪽 마디의 왼쪽 가운데로 간다. 열을 건너뛰는 선은 사이의 마디를 피해 아래로 휜다.</li>
 * <li>이벤트 선: 발행하는 모듈의 위에서 수신하는 모듈의 위로, 마디들 위쪽으로 휜다.</li>
 * </ul>
 *
 * @param nodes 마디 (왼쪽 열부터, 위에서 아래로)
 * @param edges 선 (의존을 먼저, 이벤트를 뒤에)
 * @param width 그림의 폭
 * @param height 그림의 높이
 * @param cyclic 의존이 순환해서 열을 정하지 못한 모듈이 있는가
 */
record ModuleGraph(List<Node> nodes, List<Edge> edges, int width, int height, boolean cyclic) {

	static final int NODE_WIDTH = 184;

	static final int NODE_HEIGHT = 64;

	private static final int COLUMN_GAP = 104;

	private static final int ROW_GAP = 28;

	private static final int PADDING = 24;

	/** 이벤트 선 하나가 마디 위로 올라가는 높이 */
	private static final int ARC = 44;

	/**
	 * 마디 하나.
	 *
	 * @param name 모듈 이름
	 * @param summary 이름 아래 한 줄 ({@code 화면 4 · 인터페이스 2 · 이벤트 1})
	 * @param x 왼쪽
	 * @param y 위
	 * @param uses 의존하는 모듈
	 * @param usedBy 이 모듈에 의존하는 모듈
	 * @param selected 선택한 모듈인가
	 */
	record Node(String name, String summary, int x, int y, List<String> uses, List<String> usedBy, boolean selected) {

		public int textX() {
			return this.x + 14;
		}

		public int nameY() {
			return this.y + 27;
		}

		public int summaryY() {
			return this.y + 47;
		}
	}

	/**
	 * 선 하나.
	 *
	 * @param from 의존이면 참조하는 모듈, 이벤트면 발행하는 모듈
	 * @param to 의존이면 참조되는 모듈, 이벤트면 수신하는 모듈
	 * @param event 이벤트 선이면 이벤트 이름, 의존 선이면 빈 문자열
	 * @param path SVG 경로
	 * @param labelX 이벤트 이름을 적을 가운데 x
	 * @param labelY 이벤트 이름을 적을 y
	 * @param state 선택한 모듈이 없으면 빈 문자열, 선택한 모듈에 닿으면 {@code on}, 닿지 않으면 {@code dim}
	 */
	record Edge(String from, String to, String event, String path, int labelX, int labelY, String state) {

		public boolean hasEvent() {
			return !this.event.isEmpty();
		}
	}

	/**
	 * @param modules {@code cozy describe --json}의 {@code modules}
	 * @param selected 선택한 모듈 이름. 없으면 {@code null}
	 */
	static ModuleGraph of(List<Map<String, Object>> modules, String selected) {
		Map<String, List<String>> uses = new TreeMap<>();
		for (Map<String, Object> module : modules) {
			List<String> targets = new ArrayList<>();
			for (Object dependency : list(module.get("dependsOn"))) {
				String target = moduleOf(String.valueOf(dependency));
				if (!targets.contains(target)) {
					targets.add(target);
				}
			}
			uses.put(String.valueOf(module.get("name")), targets);
		}
		uses.values().forEach((targets) -> targets.removeIf((target) -> !uses.containsKey(target)));

		// 열: 의존하는 모듈까지의 가장 긴 길이. 순환하면 정하지 못한다
		Map<String, Integer> depth = new LinkedHashMap<>();
		boolean cyclic = false;
		for (String name : uses.keySet()) {
			cyclic |= depth(name, uses, depth, new ArrayList<>()) < 0;
		}
		int deepest = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		Map<Integer, List<String>> columns = new TreeMap<>();
		depth.forEach((name, d) -> columns.computeIfAbsent(deepest - Math.max(d, 0), (c) -> new ArrayList<>()).add(name));
		int tallest = columns.values().stream().mapToInt(List::size).max().orElse(1);

		// 이벤트: 발행하는 모듈 → 수신하는 모듈
		List<String[]> events = new ArrayList<>();
		for (Map<String, Object> module : modules) {
			for (Object listener : list(module.get("listeners"))) {
				String on = String.valueOf(((Map<?, ?>) listener).get("on"));
				int dot = on.indexOf('.');
				if (dot > 0 && uses.containsKey(on.substring(0, dot))) {
					events.add(new String[] { on.substring(0, dot), String.valueOf(module.get("name")), on.substring(dot + 1) });
				}
			}
		}
		events.sort(Comparator.comparing((String[] e) -> e[0]).thenComparing((e) -> e[1]).thenComparing((e) -> e[2]));

		int top = PADDING + (events.isEmpty() ? 0 : ARC * events.size() + 12);
		int bodyHeight = tallest * NODE_HEIGHT + (tallest - 1) * ROW_GAP;
		Map<String, int[]> position = new LinkedHashMap<>();
		columns.forEach((column, names) -> {
			int columnHeight = names.size() * NODE_HEIGHT + (names.size() - 1) * ROW_GAP;
			for (int row = 0; row < names.size(); row++) {
				position.put(names.get(row), new int[] { PADDING + column * (NODE_WIDTH + COLUMN_GAP),
						top + (bodyHeight - columnHeight) / 2 + row * (NODE_HEIGHT + ROW_GAP), column });
			}
		});

		// 의존 선의 끝점: 한 마디에 선이 여럿이면 변을 따라 나눠 놓아 화살표가 겹치지 않게 한다. 휘는 선이 아래쪽이다
		record Link(String from, String to, int span) {
		}
		List<Link> links = new ArrayList<>();
		uses.forEach((from, targets) -> targets
			.forEach((to) -> links.add(new Link(from, to, position.get(to)[2] - position.get(from)[2]))));
		Comparator<Link> lowerLast = Comparator.comparing((Link l) -> l.span() != 1);
		List<Edge> edges = new ArrayList<>();
		int lowest = 0;
		for (Link link : links) {
			int[] from = position.get(link.from());
			int[] to = position.get(link.to());
			List<Link> leaving = links.stream()
				.filter((l) -> l.from().equals(link.from()))
				.sorted(lowerLast.thenComparing((l) -> position.get(l.to())[1]))
				.toList();
			List<Link> arriving = links.stream()
				.filter((l) -> l.to().equals(link.to()))
				.sorted(lowerLast.thenComparing((l) -> position.get(l.from())[1]))
				.toList();
			int span = link.span();
			int x1 = from[0] + NODE_WIDTH;
			int y1 = from[1] + NODE_HEIGHT / 2 + port(leaving.indexOf(link), leaving.size());
			int x2 = to[0];
			int y2 = to[1] + NODE_HEIGHT / 2 + port(arriving.indexOf(link), arriving.size());
			String path;
			if (span == 1) {
				path = String.format(Locale.ROOT, "M%d %d L%d %d", x1, y1, x2, y2);
			}
			else {
				// 사이의 열을 건너뛴다(순환이면 거꾸로 간다): 마디들 아래로 휜다
				int sag = bodyHeight / 2 + NODE_HEIGHT / 2 + 24 * Math.max(Math.abs(span) - 1, 1);
				int bottom = top + bodyHeight / 2 + sag;
				path = String.format(Locale.ROOT, "M%d %d C%d %d %d %d %d %d", x1, y1, x1 + COLUMN_GAP, bottom, x2 - COLUMN_GAP, bottom,
						x2, y2);
				lowest = Math.max(lowest, bottom);
			}
			edges.add(new Edge(link.from(), link.to(), "", path, 0, 0, state(selected, link.from(), link.to())));
		}
		for (int i = 0; i < events.size(); i++) {
			String[] event = events.get(i);
			int[] from = position.get(event[0]);
			int[] to = position.get(event[1]);
			// 선마다 올라가는 높이를 달리해 이름이 겹치지 않게 한다
			int peak = top - 12 - ARC * (events.size() - i) + 8;
			int x1 = from[0] + NODE_WIDTH / 2 + 24;
			int x2 = to[0] + NODE_WIDTH / 2 - 24;
			if (event[0].equals(event[1])) {
				x2 = x1 - 48;
			}
			String path = String.format(Locale.ROOT, "M%d %d C%d %d %d %d %d %d", x1, from[1], x1, peak, x2, peak, x2, to[1]);
			int curveTop = (from[1] + to[1]) / 8 + peak * 3 / 4;
			edges.add(new Edge(event[0], event[1], event[2], path, (x1 + x2) / 2, curveTop - 6, state(selected, event[0], event[1])));
		}

		Map<String, List<String>> usedBy = new TreeMap<>();
		uses.forEach((name, targets) -> targets.forEach((target) -> usedBy.computeIfAbsent(target, (t) -> new ArrayList<>()).add(name)));
		List<Node> nodes = new ArrayList<>();
		for (Map<String, Object> module : modules) {
			String name = String.valueOf(module.get("name"));
			int[] at = position.get(name);
			nodes.add(new Node(name, summary(module), at[0], at[1], List.copyOf(uses.get(name)),
					List.copyOf(usedBy.getOrDefault(name, List.of())), name.equals(selected)));
		}
		// 화면에 놓인 순서(왼쪽 열부터, 위에서 아래로)가 키보드로 옮겨 가는 순서다
		nodes.sort(Comparator.comparingInt(Node::x).thenComparingInt(Node::y));
		int width = PADDING * 2 + columns.size() * NODE_WIDTH + Math.max(columns.size() - 1, 0) * COLUMN_GAP;
		int height = Math.max(top + bodyHeight, lowest) + PADDING;
		return new ModuleGraph(List.copyOf(nodes), List.copyOf(edges), Math.max(width, PADDING * 2 + NODE_WIDTH), height, cyclic);
	}

	/** 선택한 모듈의 마디. 선택하지 않았거나 없는 이름이면 {@code null} */
	Node selectedNode() {
		return this.nodes.stream().filter(Node::selected).findFirst().orElse(null);
	}

	/** 의존하는 모듈까지의 가장 긴 길이. 순환하는 길 위에 있으면 -1 */
	private static int depth(String name, Map<String, List<String>> uses, Map<String, Integer> known, List<String> walking) {
		if (known.containsKey(name)) {
			return known.get(name);
		}
		if (walking.contains(name)) {
			return -1;
		}
		walking.add(name);
		int deepest = 0;
		boolean cyclic = false;
		for (String target : uses.get(name)) {
			int below = depth(target, uses, known, walking);
			cyclic |= below < 0;
			deepest = Math.max(deepest, below + 1);
		}
		walking.remove(name);
		known.put(name, cyclic ? -1 : deepest);
		return known.get(name);
	}

	/** 변의 가운데에서 떨어진 거리: 선 {@code count}개 가운데 {@code index}번째 */
	private static int port(int index, int count) {
		return (int) Math.round((index - (count - 1) / 2.0) * 16);
	}

	private static String state(String selected, String from, String to) {
		if (selected == null) {
			return "";
		}
		return (selected.equals(from) || selected.equals(to)) ? "on" : "dim";
	}

	/** {@code memo :: spi} → {@code memo} */
	static String moduleOf(String dependency) {
		int separator = dependency.indexOf("::");
		return ((separator < 0) ? dependency : dependency.substring(0, separator)).strip();
	}

	private static String summary(Map<String, Object> module) {
		List<String> parts = new ArrayList<>();
		count(parts, "화면", list(module.get("screens")).size());
		count(parts, "인터페이스", list(module.get("facades")).size() + list(module.get("finders")).size());
		count(parts, "이벤트", list(module.get("events")).size());
		count(parts, "리스너", list(module.get("listeners")).size());
		return parts.isEmpty() ? "공개 API 없음" : String.join(" · ", parts);
	}

	private static void count(List<String> parts, String label, int count) {
		if (count > 0) {
			parts.add(label + " " + count);
		}
	}

	private static List<?> list(Object value) {
		return (value instanceof List<?> list) ? list : List.of();
	}
}
