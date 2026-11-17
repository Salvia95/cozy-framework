package io.salvia95.cozy.rules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alpine 컴포넌트가 템플릿에 내놓는 이름. JS 파일의 한 줄 주석으로 적는다. 동작 명세에서 만든 JS는 cozy가 쓰고, cozy-ui의
 * {@code cozy.js}는 프레임워크 명세({@code cozy/behaviors.yml})와 같게 손으로 쓴다(테스트가 맞춘다).
 *
 * <pre>
 * // cozy:component memoBodyCount members=length,limit,over,text,label,count data=length,limit
 * </pre>
 *
 * <p>R16은 이 줄이 있는 컴포넌트에 대해, 감싸는 {@code x-data}가 그 컴포넌트인 요소의 식이 부르는 이름이 {@code members}에 있는지 본다.
 *
 * @param name 등록 이름
 * @param members 템플릿이 부를 수 있는 이름(상태, 계산, 동작, 메서드)
 * @param data {@code x-data} 요소에서 읽는 {@code data-*}의 이름({@code data-} 뒤)
 */
public record ComponentContract(String name, Set<String> members, Set<String> data) {

	public static final String MARKER = "cozy:component";

	private static final Pattern LINE = Pattern
		.compile("//\\s*" + Pattern.quote(MARKER) + " ([A-Za-z_$][A-Za-z0-9_$]*) members=([A-Za-z0-9_$,]*) data=([a-z0-9,-]*)");

	public ComponentContract {
		members = Set.copyOf(new LinkedHashSet<>(members));
		data = Set.copyOf(new LinkedHashSet<>(data));
	}

	/** 주석 한 줄 ({@code //}부터). 이름은 주어진 순서대로 */
	public static String line(String name, List<String> members, List<String> data) {
		return "// " + MARKER + " " + name + " members=" + String.join(",", members) + " data=" + String.join(",", data);
	}

	/** JS 파일의 모든 줄. 이름 → 계약 */
	public static Map<String, ComponentContract> parse(String script) {
		Map<String, ComponentContract> contracts = new LinkedHashMap<>();
		Matcher m = LINE.matcher(script);
		while (m.find()) {
			contracts.put(m.group(1), new ComponentContract(m.group(1), split(m.group(2)), split(m.group(3))));
		}
		return contracts;
	}

	private static Set<String> split(String list) {
		Set<String> names = new LinkedHashSet<>();
		for (String item : list.split(",")) {
			if (!item.isEmpty()) {
				names.add(item);
			}
		}
		return names;
	}
}
