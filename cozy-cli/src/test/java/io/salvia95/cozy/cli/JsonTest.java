package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.cli.internal.Describe;
import io.salvia95.cozy.cli.internal.Json;

class JsonTest {

	@Test
	void escapesAndKeepsOrder() {
		String json = Json.write(Describe.ordered("b", "따옴표 \" 역슬래시 \\ 줄\n탭\t", "a", 1, "c", Arrays.asList(true, null),
				"d", List.of(), "e", Describe.ordered()));
		assertThat(json).isEqualTo("""
				{
				  "b": "따옴표 \\" 역슬래시 \\\\ 줄\\n탭\\t",
				  "a": 1,
				  "c": [
				    true,
				    null
				  ],
				  "d": [],
				  "e": {}
				}
				""");
	}

	@Test
	void controlCharactersAreEscaped() {
		assertThat(Json.write("\u0001")).isEqualTo("\"\\u0001\"\n");
	}
}
