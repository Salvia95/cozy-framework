package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.web.server.ResponseStatusException;

/** 붙이기 폼 → cozy attach의 인자, 그리고 터미널에 붙여 넣을 명령 */
class AttachArgsTest {

	@Test
	void dataComesFirstAndOnlyWhatChanged() {
		Map<String, String[]> form = new LinkedHashMap<>();
		form.put("data.length", new String[] { "${bodyLength}" });
		form.put("data.limit", new String[] { "5000" });
		form.put("was.limit", new String[] { "5000" });
		form.put("data.unused", new String[] { " " });
		form.put("option", new String[] { "on" });

		Map<String, String> data = WorkbenchController.attachData(form);

		assertThat(data).containsExactly(Map.entry("length", "${bodyLength}"));
		assertThat(WorkbenchController.attachArgs("memoBodyCount", "memo/internal/fields", 7, data, "on", "input", "count", false))
			.containsExactly("attach", "memoBodyCount", "--at", "memo/internal/fields:7", "--data", "length=${bodyLength}", "--on", "input:count");
		assertThat(WorkbenchController.attachArgs("memoBodyCount", "memo/internal/fields", 7, Map.of(), "", "", "", false))
			.as("붙일 것 없이 보내면 컴포넌트의 자리만 (x-data)")
			.containsExactly("attach", "memoBodyCount", "--at", "memo/internal/fields:7");
		assertThatThrownBy(() -> WorkbenchController.attachData(Map.of("data.Limit", new String[] { "1" })))
			.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void theCommandQuotesWhatTheShellWouldExpand() {
		assertThat(WorkbenchController.commandLine(List.of("attach", "memoBodyCount", "--data", "length=${bodyLength}", "--data", "limit=5000")))
			.isEqualTo("cozy attach memoBodyCount --data 'length=${bodyLength}' --data limit=5000");
		assertThat(WorkbenchController.commandLine(List.of("--data", "label=${'a'}", "--purpose", "글자 수")))
			.isEqualTo("cozy --data \"label=\\${'a'}\" --purpose \"글자 수\"");
	}
}
