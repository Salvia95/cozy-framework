package io.salvia95.cozy.ui.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 매니페스트 파라미터의 {@code choices}: 값의 목록이나 값 → 이름의 맵. 형식이 틀리면 고치는 법을 담아 실패한다 */
class FragmentManifestChoicesTest {

	static List<FragmentManifest.Entry> parse(String param) {
		String yaml = """
				fragments:
				  - id: memo/card
				    template: memo/spi/card
				    signature: memoCard(size)
				    purpose: 메모 카드
				    params:
				      - %s
				    samples:
				      default: { size: compact }
				""".formatted(param);
		return FragmentManifest.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "fragments.yml");
	}

	@Test
	void choicesAreAListOrAMapOfLabels() {
		FragmentManifest.Param list = parse("{ name: size, type: String, required: true, choices: [compact, wide] }").getFirst().params().getFirst();
		assertThat(list.choices()).containsExactly(Map.entry("compact", ""), Map.entry("wide", ""));
		assertThat(list.choiceText("wide")).isEqualTo("wide");
		FragmentManifest.Param map = parse("{ name: size, type: String, required: true, choices: { compact: 좁게, wide: 넓게 } }").getFirst()
			.params()
			.getFirst();
		assertThat(map.choices()).containsExactly(Map.entry("compact", "좁게"), Map.entry("wide", "넓게"));
		assertThat(map.choiceText("wide")).isEqualTo("넓게 (wide)");
		assertThat(parse("{ name: size, type: String, required: true }").getFirst().params().getFirst().choices()).isEmpty();
	}

	@Test
	void wrongChoicesFailWithHowToFixThem() {
		assertThatThrownBy(() -> parse("{ name: size, type: int, required: true, choices: [1, 2] }"))
			.hasMessageContaining("memo/card의 size: choices는 String 파라미터에만 씁니다");
		assertThatThrownBy(() -> parse("{ name: size, type: String, required: true, choices: [] }")).hasMessageContaining("choices가 비어 있습니다");
		assertThatThrownBy(() -> parse("{ name: size, type: String, required: true, choices: wide }"))
			.hasMessageContaining("choices는 값의 목록이나 값 → 이름의 맵입니다");
		assertThatThrownBy(() -> parse("{ name: size, type: String, required: true, choices: [\"it's\"] }"))
			.hasMessageContaining("따옴표와 역슬래시가 없는 글자");
	}

	/** 프레임워크 매니페스트: 값을 정한 파라미터의 샘플 값도 그 가운데 하나다 */
	@Test
	void frameworkSamplesUseTheirOwnChoices() {
		FragmentManifest manifest = FragmentManifest.load(getClass().getClassLoader());
		List<String> withChoices = new java.util.ArrayList<>();
		for (FragmentManifest.Entry entry : manifest.fragments()) {
			for (FragmentManifest.Param param : entry.params()) {
				if (param.choices().isEmpty()) {
					continue;
				}
				withChoices.add(entry.id() + " " + param.name());
				assertThat(param.choices().values()).as(entry.id() + " " + param.name() + "의 이름").allMatch((label) -> !label.isBlank());
				entry.samples().forEach((state, values) -> {
					if (values.containsKey(param.name())) {
						assertThat(param.choices()).as(entry.id() + " " + state).containsKey(String.valueOf(values.get(param.name())));
					}
				});
			}
		}
		assertThat(withChoices).containsExactly("component/field type", "component/badge tone", "component/alert kind", "component/toast kind");
	}
}
