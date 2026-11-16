package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** describe의 앱 이벤트를 주인 모듈마다 보인다 */
class ModuleEventsTest {

	@Test
	void eachModuleGetsTheEventsItOwns() {
		Map<String, Object> memo = new LinkedHashMap<>(Map.of("name", "memo"));
		Map<String, Object> events = new LinkedHashMap<>(Map.of("name", "events"));
		Map<String, Object> created = Map.of("type", "MemoCreated", "owner", "memo");
		Map<String, Object> described = new LinkedHashMap<>();
		described.put("modules", new ArrayList<>(List.of(memo, events)));
		described.put("events", List.of(created, Map.of("type", "Orphan")));

		WorkbenchEngine.withModuleEvents(described);

		assertThat(memo.get("events")).isEqualTo(List.of(created));
		assertThat(events.get("events")).as("events 모듈 자신과 주인이 없는 이벤트").isEqualTo(List.of());
	}
}
