package io.salvia95.cozy.cli.internal.behavior;

import java.util.List;

/**
 * 동작 명세의 낱말: 상태의 종류, 이벤트 값, 계산 함수, 동작 단계. 명세를 확인하고 JS를 만드는 코드가 이 목록을 쓰고, {@code cozy recipes --json}의
 * {@code vocabulary}와 워크벤치의 낱말 표가 같은 목록을 보인다. 규약 문서(docs/conventions/ui.md의 "동작 명세")와 같은지는 테스트가 본다.
 */
public final class BehaviorVocabulary {

	/** 상태의 종류 */
	public static final String TYPE = "type";

	/** 이벤트 값 (동작에서만) */
	public static final String EVENT = "event";

	/** 계산과 동작의 값에 쓰는 함수 */
	public static final String FUNCTION = "function";

	/** 동작의 단계 */
	public static final String STEP = "step";

	/**
	 * 낱말 하나.
	 *
	 * @param kind {@link #TYPE}, {@link #EVENT}, {@link #FUNCTION}, {@link #STEP}
	 * @param name 이름 ({@code gt}, {@code event.data})
	 * @param shape 적는 모양 ({@code gt(a, b)}, {@code event.data.이름})
	 * @param text 한 줄 설명
	 * @param arity 함수의 인자 수. -1은 둘 이상. 함수가 아니면 0
	 */
	public record Word(String kind, String name, String shape, String text, int arity) {
	}

	public static final List<Word> WORDS = List.of(
			type("bool", "참거짓. 처음 값은 false"),
			type("number", "숫자. 처음 값은 0"),
			type("text", "글자. 처음 값은 ''"),
			type("list", "글자의 목록. 처음 값은 []이고, 정하려면 쉼표로 잇는다(=a,b). data-*에서 읽으면 공백으로 나눈다"),
			event("event.value", "event.value", "이벤트가 일어난 요소의 값(글자)"),
			event("event.checked", "event.checked", "이벤트가 일어난 요소의 체크 여부"),
			event("event.data", "event.data.이름", "이벤트가 일어난 요소부터 x-data 요소까지 올라가며 처음 찾은 data-이름(글자)"),
			event("event.detail", "event.detail.이름", "받은 화면 이벤트의 detail 값. 없으면 빈 글자"),
			function("not", "not(a)", 1, "참거짓을 뒤집는다"),
			function("and", "and(a, b, …)", -1, "모두 참인가"),
			function("or", "or(a, b, …)", -1, "하나라도 참인가"),
			function("eq", "eq(a, b)", 2, "같은가(종류까지 비교)"),
			function("ne", "ne(a, b)", 2, "다른가(종류까지 비교)"),
			function("gt", "gt(a, b)", 2, "a가 b보다 큰가 (>)"),
			function("lt", "lt(a, b)", 2, "a가 b보다 작은가 (<)"),
			function("ge", "ge(a, b)", 2, "a가 b보다 크거나 같은가 (>=)"),
			function("le", "le(a, b)", 2, "a가 b보다 작거나 같은가 (<=)"),
			function("empty", "empty(a)", 1, "글자나 목록이 비었는가"),
			function("count", "count(list)", 1, "목록의 수"),
			function("length", "length(text)", 1, "글자 수"),
			function("includes", "includes(list, a)", 2, "목록에 그 값이 있는가"),
			function("number", "number(a)", 1, "글자를 숫자로. 읽을 수 없으면 0"),
			function("format", "format('글자 {이름}', 'ko-KR')", -1, "{이름} 자리에 상태나 계산의 값. 둘째 인자가 있으면 숫자를 그 형식으로"),
			function("if", "if(조건, 참일 때, 거짓일 때)", 3, "조건에 따라 두 값 가운데 하나. 두 값은 같은 종류"),
			step("set", "set(상태, 값)", "값을 넣는다. 종류가 같아야 한다"),
			step("toggle", "toggle(상태)", "bool 상태의 참거짓을 뒤집는다"),
			step("increment", "increment(상태[, 숫자])", "number 상태에 더한다(기본 1)"),
			step("decrement", "decrement(상태[, 숫자])", "number 상태에서 뺀다(기본 1)"),
			step("addItem", "addItem(list, 값)", "목록에 넣는다. 이미 있으면 그대로"),
			step("removeItem", "removeItem(list, 값)", "목록에서 뺀다"),
			step("toggleItem", "toggleItem(list, 값)", "있으면 빼고 없으면 넣는다"),
			step("reset", "reset(상태)", "처음 값(data-*로 읽었으면 그 값)으로 되돌린다"),
			step("emit", "emit('모듈:이름', 이름 …)", "화면 이벤트를 낸다. 적은 상태나 계산이 detail에 들어간다"),
			step("when", "when(조건, 단계 …)", "조건이 참일 때만"),
			step("within", "within('선택자', 단계 …)", "이벤트가 x-data 요소 안의 그 선택자 요소에서 일어났을 때만"));

	private BehaviorVocabulary() {
	}

	/** 그 종류의 낱말 */
	public static List<Word> of(String kind) {
		return WORDS.stream().filter((w) -> w.kind().equals(kind)).toList();
	}

	/** 그 종류의 이름 */
	public static List<String> names(String kind) {
		return of(kind).stream().map(Word::name).toList();
	}

	private static Word type(String name, String text) {
		return new Word(TYPE, name, name, text, 0);
	}

	private static Word event(String name, String shape, String text) {
		return new Word(EVENT, name, shape, text + ". 동작에서만 쓴다", 0);
	}

	private static Word function(String name, String shape, int arity, String text) {
		return new Word(FUNCTION, name, shape, text, arity);
	}

	private static Word step(String name, String shape, String text) {
		return new Word(STEP, name, shape, text, 0);
	}
}
