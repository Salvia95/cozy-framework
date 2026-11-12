package io.salvia95.cozy.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 고를 수 있는 값의 목록 (select, checkboxGroup, radioGroup 컴포넌트 입력). 보이는 순서는 목록의 순서다.
 *
 * @param items 선택지
 */
public record CozyOptions(List<Option> items) {

	public CozyOptions {
		Objects.requireNonNull(items, "items");
		items = List.copyOf(items);
		Set<String> values = new HashSet<>();
		for (Option item : items) {
			if (!values.add(item.value())) {
				throw new IllegalArgumentException("[cozy] CozyOptions에 같은 값 '" + item.value() + "'이 두 번 있습니다");
			}
		}
	}

	/** 값 → 보일 이름. 맵의 순서대로 보인다(순서가 있는 맵을 넘긴다). */
	public static CozyOptions of(Map<String, String> labels) {
		List<Option> items = new ArrayList<>();
		labels.forEach((value, label) -> items.add(new Option(value, label)));
		return new CozyOptions(items);
	}

	/** enum 상수마다 하나. 값은 상수 이름이고 선언 순서대로 보인다. */
	public static <E extends Enum<E>> CozyOptions of(Class<E> type, Function<E, String> label) {
		List<Option> items = new ArrayList<>();
		for (E constant : type.getEnumConstants()) {
			items.add(new Option(constant.name(), label.apply(constant)));
		}
		return new CozyOptions(items);
	}

	/**
	 * 선택지 하나.
	 *
	 * @param value 값 (폼 속성에 들어가는 값)
	 * @param label 이름 (화면에 보이는 글자)
	 */
	public record Option(String value, String label) {

		public Option {
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("[cozy] 선택지의 값이 비어 있습니다");
			}
			if (label == null || label.isBlank()) {
				throw new IllegalArgumentException("[cozy] 선택지 '" + value + "'의 이름이 비어 있습니다");
			}
		}
	}
}
