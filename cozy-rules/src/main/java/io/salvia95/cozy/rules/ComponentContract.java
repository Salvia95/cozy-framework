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
 * // cozy:component memoSelection members=ids,count,pick,clear data= actions=pick,clear emits=memo:selection-changed listens=memo:archived:clear
 * </pre>
 *
 * <p>R16은 이 줄이 있는 컴포넌트에 대해, 감싸는 {@code x-data}가 그 컴포넌트인 요소의 식이 부르는 이름이 {@code members}에 있는지 본다. 동작 명세에서
 * 만든 JS는 뒤에 {@code actions}, {@code emits}, {@code listens}({@code 이벤트:동작})를 더 적는다. C13이 이 이름으로 붙인 곳과 화면 이벤트를 잇는다.
 *
 * @param name 등록 이름
 * @param members 템플릿이 부를 수 있는 이름(상태, 계산, 동작, 메서드)
 * @param data {@code x-data} 요소에서 읽는 {@code data-*}의 이름({@code data-} 뒤)
 * @param wiring 동작과 화면 이벤트. 동작 명세에서 만든 JS만 적는다. 없으면 {@code null}
 */
public record ComponentContract(String name, Set<String> members, Set<String> data, Wiring wiring) {

	public static final String MARKER = "cozy:component";

	private static final Pattern LINE = Pattern.compile("//\\s*" + Pattern.quote(MARKER)
			+ " ([A-Za-z_$][A-Za-z0-9_$]*) members=([A-Za-z0-9_$,]*) data=([a-z0-9,-]*)"
			+ "(?: actions=([A-Za-z0-9_$,]*) emits=([a-z0-9:,-]*) listens=([A-Za-z0-9:,-]*))?");

	/**
	 * 동작 명세에서 만든 컴포넌트의 동작과 화면 이벤트.
	 *
	 * @param actions 동작의 이름
	 * @param emits 동작이 내는 화면 이벤트 ({@code memo:archived})
	 * @param listens 받는 화면 이벤트 → 그때 부르는 동작
	 */
	public record Wiring(Set<String> actions, Set<String> emits, Map<String, String> listens) {

		public Wiring {
			actions = Set.copyOf(new LinkedHashSet<>(actions));
			emits = Set.copyOf(new LinkedHashSet<>(emits));
			listens = Map.copyOf(listens);
		}
	}

	public ComponentContract {
		members = Set.copyOf(new LinkedHashSet<>(members));
		data = Set.copyOf(new LinkedHashSet<>(data));
	}

	/** 동작과 화면 이벤트 없이 (프레임워크 컴포넌트) */
	public ComponentContract(String name, Set<String> members, Set<String> data) {
		this(name, members, data, null);
	}

	/** 주석 한 줄 ({@code //}부터). 이름은 주어진 순서대로 */
	public static String line(String name, List<String> members, List<String> data) {
		return "// " + MARKER + " " + name + " members=" + String.join(",", members) + " data=" + String.join(",", data);
	}

	/**
	 * 동작 명세에서 만든 컴포넌트의 주석 한 줄. 이름은 주어진 순서대로.
	 *
	 * @param listens 받는 화면 이벤트 → 동작
	 */
	public static String line(String name, List<String> members, List<String> data, List<String> actions, List<String> emits,
			Map<String, String> listens) {
		List<String> pairs = listens.entrySet().stream().map((e) -> e.getKey() + ":" + e.getValue()).toList();
		return line(name, members, data) + " actions=" + String.join(",", actions) + " emits=" + String.join(",", emits)
				+ " listens=" + String.join(",", pairs);
	}

	/** JS 파일의 모든 줄. 이름 → 계약 */
	public static Map<String, ComponentContract> parse(String script) {
		Map<String, ComponentContract> contracts = new LinkedHashMap<>();
		Matcher m = LINE.matcher(script);
		while (m.find()) {
			Wiring wiring = null;
			if (m.group(4) != null) {
				Map<String, String> listens = new LinkedHashMap<>();
				for (String pair : split(m.group(6))) {
					int at = pair.lastIndexOf(':');
					if (at > 0) {
						listens.put(pair.substring(0, at), pair.substring(at + 1));
					}
				}
				wiring = new Wiring(split(m.group(4)), split(m.group(5)), listens);
			}
			contracts.put(m.group(1), new ComponentContract(m.group(1), split(m.group(2)), split(m.group(3)), wiring));
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
