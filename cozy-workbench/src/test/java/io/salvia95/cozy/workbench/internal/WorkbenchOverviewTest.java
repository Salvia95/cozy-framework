package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.ui.catalog.ScreenSpec;
import io.salvia95.cozy.workbench.internal.WorkbenchController.ScreenGroup;
import io.salvia95.cozy.workbench.internal.WorkbenchController.ScreenRow;

/** 개요의 화면 묶음과 상태 비교가 처음 고르는 상태 */
class WorkbenchOverviewTest {

	static ScreenRow row(String module, String screen, long violations) {
		ScreenSpec spec = new ScreenSpec(screen, module + "/api/" + screen, "", List.of(), Map.of(), Map.of("default", Map.of()), "test");
		return new ScreenRow(spec, Map.of("default", "기본"), violations);
	}

	static Map<String, List<ScreenRow>> modules(int perModule, String... names) {
		Map<String, List<ScreenRow>> byModule = new LinkedHashMap<>();
		for (String name : names) {
			List<ScreenRow> rows = new ArrayList<>();
			for (int i = 0; i < perModule; i++) {
				rows.add(row(name, name + "-" + i, name.equals("memo") && i == 0 ? 2 : 0));
			}
			byModule.put(name, rows);
		}
		return byModule;
	}

	@Test
	void fewScreensAreAllOpen() {
		List<ScreenGroup> groups = WorkbenchController.screenGroups(modules(4, "memo", "task", "report"));
		assertThat(groups).extracting(ScreenGroup::module, ScreenGroup::open, ScreenGroup::violations)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("memo", true, 2L), org.assertj.core.groups.Tuple.tuple("task", true, 0L),
					org.assertj.core.groups.Tuple.tuple("report", true, 0L));
	}

	@Test
	void manyScreensOpenOnlyModulesWithViolations() {
		List<ScreenGroup> groups = WorkbenchController.screenGroups(modules(5, "memo", "task", "report"));
		assertThat(groups).extracting(ScreenGroup::module, ScreenGroup::open)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("memo", true), org.assertj.core.groups.Tuple.tuple("task", false),
					org.assertj.core.groups.Tuple.tuple("report", false));
		assertThat(groups.getFirst().rows()).hasSize(5);
	}

	@Test
	void comparisonStartsWithTheCurrentStateAndUpToTheLimit() {
		Set<String> states = new java.util.LinkedHashSet<>(List.of("default", "empty", "noResults", "paged", "invalid", "many"));
		assertThat(WorkbenchController.compared(states, "paged")).containsExactly("paged", "default", "empty", "noResults");
		assertThat(WorkbenchController.compared(new java.util.LinkedHashSet<>(List.of("default", "empty")), "empty"))
			.containsExactly("empty", "default");
	}
}
