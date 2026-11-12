package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CozyOptionsTest {

	enum Status {
		TODO, DONE
	}

	@Test
	void mapKeepsTheGivenOrder() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("b", "나");
		labels.put("a", "가");
		assertThat(CozyOptions.of(labels).items()).containsExactly(new CozyOptions.Option("b", "나"),
				new CozyOptions.Option("a", "가"));
	}

	@Test
	void enumConstantsBecomeValuesInDeclarationOrder() {
		CozyOptions options = CozyOptions.of(Status.class, (status) -> status == Status.TODO ? "할 일" : "완료");
		assertThat(options.items()).containsExactly(new CozyOptions.Option("TODO", "할 일"),
				new CozyOptions.Option("DONE", "완료"));
	}

	@Test
	void itemsAreACopy() {
		List<CozyOptions.Option> items = new java.util.ArrayList<>(List.of(new CozyOptions.Option("a", "가")));
		CozyOptions options = new CozyOptions(items);
		items.clear();
		assertThat(options.items()).hasSize(1);
	}

	@Test
	void valuesAreUniqueAndNotBlank() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new CozyOptions(List.of(new CozyOptions.Option("a", "가"), new CozyOptions.Option("a", "나"))))
			.withMessageContaining("'a'");
		assertThatIllegalArgumentException().isThrownBy(() -> new CozyOptions.Option(" ", "가"))
			.withMessageContaining("값");
		assertThatIllegalArgumentException().isThrownBy(() -> new CozyOptions.Option("a", ""))
			.withMessageContaining("이름");
	}
}
