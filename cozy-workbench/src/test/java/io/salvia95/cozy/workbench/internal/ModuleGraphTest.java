package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.workbench.internal.ModuleGraph.Edge;
import io.salvia95.cozy.workbench.internal.ModuleGraph.Node;

/** 모듈 의존성 다이어그램의 배치: 참조하는 모듈이 왼쪽, 참조되는 모듈이 오른쪽. 이벤트는 발행하는 모듈에서 수신하는 모듈로. */
class ModuleGraphTest {

	static Map<String, Object> module(String name, List<String> dependsOn, List<String> listensTo, int screens) {
		return Map.of("name", name, "dependsOn", dependsOn, "listeners",
				listensTo.stream().map((on) -> Map.of("type", "Listener", "on", on)).toList(), "screens",
				java.util.Collections.nCopies(screens, Map.of()), "facades", List.of(), "finders", List.of(), "events", List.of());
	}

	static final List<Map<String, Object>> REFERENCE = List.of(module("activity", List.of("memo :: spi"), List.of("memo.MemoCreated"), 0),
			module("dashboard", List.of("memo :: spi", "activity :: spi"), List.of(), 1), module("memo", List.of(), List.of(), 4));

	static Node node(ModuleGraph graph, String name) {
		return graph.nodes().stream().filter((n) -> n.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void modulesThatUseOthersSitToTheLeftOfTheModulesTheyUse() {
		ModuleGraph graph = ModuleGraph.of(REFERENCE, null);
		assertThat(graph.cyclic()).isFalse();
		assertThat(node(graph, "dashboard").x()).isLessThan(node(graph, "activity").x());
		assertThat(node(graph, "activity").x()).isLessThan(node(graph, "memo").x());
		assertThat(graph.width()).isGreaterThan(node(graph, "memo").x() + ModuleGraph.NODE_WIDTH);
		assertThat(graph.nodes()).extracting(Node::name).as("놓인 순서대로").containsExactly("dashboard", "activity", "memo");
		for (Node node : graph.nodes()) {
			assertThat(node.y()).as(node.name()).isPositive();
			assertThat(node.y() + ModuleGraph.NODE_HEIGHT).as(node.name()).isLessThan(graph.height());
		}
	}

	@Test
	void everyModuleKnowsWhatItUsesAndWhatUsesIt() {
		ModuleGraph graph = ModuleGraph.of(REFERENCE, null);
		assertThat(node(graph, "memo").uses()).isEmpty();
		assertThat(node(graph, "memo").usedBy()).containsExactly("activity", "dashboard");
		assertThat(node(graph, "dashboard").uses()).containsExactly("memo", "activity");
		assertThat(node(graph, "dashboard").usedBy()).isEmpty();
		assertThat(node(graph, "memo").summary()).isEqualTo("화면 4");
		assertThat(node(graph, "activity").summary()).isEqualTo("리스너 1");
	}

	@Test
	void dependenciesAreSolidEdgesAndEventsRunFromPublisherToListener() {
		ModuleGraph graph = ModuleGraph.of(REFERENCE, null);
		assertThat(graph.edges()).filteredOn((e) -> !e.hasEvent())
			.extracting(Edge::from, Edge::to)
			.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("activity", "memo"),
					org.assertj.core.groups.Tuple.tuple("dashboard", "memo"), org.assertj.core.groups.Tuple.tuple("dashboard", "activity"));
		assertThat(graph.edges()).filteredOn(Edge::hasEvent)
			.extracting(Edge::from, Edge::to, Edge::event)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("memo", "activity", "MemoCreated"));
		Edge skipping = graph.edges().stream().filter((e) -> e.from().equals("dashboard") && e.to().equals("memo")).findFirst().orElseThrow();
		assertThat(skipping.path()).as("열을 건너뛰는 선은 휜다").contains("C");
		Edge adjacent = graph.edges().stream().filter((e) -> e.from().equals("activity") && e.to().equals("memo")).findFirst().orElseThrow();
		assertThat(adjacent.path()).as("이웃한 열은 곧게 잇는다").contains("L").doesNotContain("C");
		assertThat(skipping.path().substring(skipping.path().lastIndexOf(' ', skipping.path().lastIndexOf(' ') - 1)))
			.as("한 마디에 들어오는 선은 끝점이 서로 다르다")
			.isNotEqualTo(adjacent.path().substring(adjacent.path().lastIndexOf(' ', adjacent.path().lastIndexOf(' ') - 1)));
		Edge event = graph.edges().stream().filter(Edge::hasEvent).findFirst().orElseThrow();
		assertThat(event.labelY()).as("이벤트 이름은 그림 안, 마디 위에 있다").isPositive().isLessThan(node(graph, "memo").y());
	}

	@Test
	void selectingAModuleMarksItsEdgesAndDimsTheOthers() {
		ModuleGraph graph = ModuleGraph.of(REFERENCE, "activity");
		assertThat(graph.selectedNode().name()).isEqualTo("activity");
		assertThat(graph.edges()).filteredOn((e) -> e.from().equals("dashboard") && e.to().equals("memo"))
			.extracting(Edge::state)
			.containsExactly("dim");
		assertThat(graph.edges()).filteredOn((e) -> e.to().equals("activity") || e.from().equals("activity"))
			.extracting(Edge::state)
			.containsOnly("on");
		assertThat(ModuleGraph.of(REFERENCE, null).edges()).extracting(Edge::state).containsOnly("");
		assertThat(ModuleGraph.of(REFERENCE, "nothing").selectedNode()).isNull();
	}

	@Test
	void aCycleIsReportedAndStillDrawn() {
		ModuleGraph graph = ModuleGraph.of(
				List.of(module("a", List.of("b :: spi"), List.of(), 0), module("b", List.of("a :: spi"), List.of(), 0)), null);
		assertThat(graph.cyclic()).isTrue();
		assertThat(graph.nodes()).hasSize(2);
		assertThat(graph.edges()).hasSize(2);
	}

	@Test
	void aDependencyOnAnUnknownModuleIsLeftOut() {
		ModuleGraph graph = ModuleGraph.of(List.of(module("a", List.of("gone :: spi"), List.of("gone.Happened"), 0)), null);
		assertThat(graph.edges()).isEmpty();
		assertThat(node(graph, "a").uses()).isEmpty();
	}
}
