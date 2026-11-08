package io.salvia95.cozy.cli.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code cozy recipes}: 구조를 만드는 명령 목록 (ADR-0032 부록, 2단계는 ADR-0033). 에이전트는 파일을 만들거나 옮기지 않고 이 명령을 쓴다.
 * 아직 만들지 않은 레시피는 {@code available: false}이고, 실행하면 종료 코드 2로 알린다. 보류한 {@code field}는 목록에 없다.
 */
public final class Recipes {

	private Recipes() {
	}

	/**
	 * 레시피 하나.
	 *
	 * @param name {@code cozy add} 뒤의 이름, 또는 {@code link}
	 * @param stage 1단계(새 파일과 의존 선언) 또는 2단계(이미 있는 파일에 덧붙임, ADR-0033)
	 * @param usage 명령 형식
	 * @param does 무엇을 만드는지
	 * @param slots 에이전트가 채울 빈칸. 없으면 만든 직후 {@code mvn verify}가 통과한다
	 * @param available 지금 쓸 수 있는가
	 */
	public record Recipe(String name, int stage, String usage, String does, String slots, boolean available) {
	}

	public static final List<Recipe> ALL = List.of(
			new Recipe("module", 1, "cozy add module <모듈> [--label 라벨]", "빈 모듈(package-info.java, allowedDependencies = {})",
					"없음", true),
			new Recipe("link", 1, "cozy link <모듈> --uses <다른 모듈>[,…]", "package-info.java의 allowedDependencies에 \"모듈 :: spi\" 추가",
					"없음", true),
			new Recipe("crud", 1,
					"cozy add crud <모듈> --entity <E> --label 라벨 --icon 아이콘 --field 이름:종류[!][:라벨] … [--publish created,updated,deleted]",
					"목록·만들기·상세·수정·삭제 한 벌(spi, internal, api, SQL, 템플릿, 스펙, 테스트)", "없음", true),
			new Recipe("screen", 1,
					"cozy add screen <모듈> <화면> --pattern overview|details --title 제목 [--field …] [--nav 라벨:아이콘]",
					"읽기 화면 하나(컨트롤러, 화면 DTO, 템플릿, 스펙, 내비게이션). 목록·폼은 crud가 만든다", "컨트롤러 view-model", true),
			new Recipe("fragment", 1, "cozy add fragment <모듈> <이름> --param 이름:타입 … --purpose \"용도\"",
					"다른 모듈에 공개하는 fragment와 매니페스트 항목", "템플릿 fragment-body", true),
			new Recipe("event", 1, "cozy add event <모듈> <이벤트> --field 이름:종류 …", "CozyEvent record와 직렬화 샘플 v1",
					"없음(발행할 한 줄은 에이전트가 넣는다)", true),
			new Recipe("listener", 1, "cozy add listener <모듈> --on <보내는 모듈>.<이벤트> [--name 이름]",
					"이벤트 리스너. 자기 모듈의 {모듈}Facade를 받는다(없으면 빈 Facade와 구현을 함께 만든다). 필요하면 link 포함", "listener-body", true),
			new Recipe("migration", 1, "cozy add migration <모듈> <설명>", "V{시각}__{모듈}_{설명}.sql", "migration-sql", true),
			new Recipe("dto", 2, "cozy add dto <모듈> <Record> --field 이름:종류[:설명] …",
					"spi/dto의 record 하나(모듈 사이 값). 종류는 crud와 같고 같은 모듈의 record·list:record도 쓴다", "없음", true),
			new Recipe("enable events", 2, "cozy enable events", "pom에 cozy-starter-events 추가(cozy-starter-sqlite가 있어야 한다)", "없음",
					true),
			new Recipe("query", 2,
					"cozy add query <모듈> <이름> [--param 이름:종류 …] --returns long|int|bool|text|<Record>|list:<Record> [--in <Finder>]",
					"Finder 메서드와 구현, Mapper 메서드, 행 record, <select>. Finder가 없으면 만든다", "query-sql", true),
			new Recipe("command", 2,
					"cozy add command <모듈> <이름> [--param 이름:종류 …] [--returns long|int|bool|text] [--sql insert|update|delete] [--in <Facade>]",
					"Facade 메서드와 구현. --sql이면 Mapper 메서드와 쓰기 SQL 문. Facade가 없으면 만든다", "command-body, (--sql이면) command-sql", true),
			new Recipe("event-version", 2, "cozy add event-version <모듈> <이벤트> --field 이름:종류 …",
					"이벤트 필드 추가(박싱 타입), schemaVersion 올림, 옛 of()는 새 필드를 null로 넘김, 샘플 v{n+1} 추가", "없음", true));

	public static Map<String, Object> model() {
		List<Object> recipes = new ArrayList<>();
		for (Recipe r : ALL) {
			recipes.add(Describe.ordered("name", r.name(), "stage", r.stage(), "usage", r.usage(), "does", r.does(), "slots",
					r.slots(), "available", r.available()));
		}
		return Describe.ordered("recipes", recipes);
	}

	/** 쓸 수 있는 것을 먼저, 준비 중인 것을 뒤에 둔다. 쓸 수 있는 부분은 {@link #availableText()}와 같다 */
	public static String text() {
		StringBuilder out = new StringBuilder("구조는 아래 명령으로 바꾸고, 명령이 남긴 빈칸(cozy:slot)만 채운다 (ADR-0032, ADR-0033).\n\n");
		out.append(availableText());
		if (ALL.stream().anyMatch((r) -> !r.available())) {
			out.append("\n준비 중 (실행하면 종료 코드 2)\n");
			ALL.stream().filter((r) -> !r.available()).forEach((r) -> append(out, r));
		}
		return out.toString();
	}

	/** 지금 쓸 수 있는 레시피만. 앱의 AGENTS.md에 이 글이 그대로 들어간다(docs/AGENTS.md, 테스트가 같은지 확인한다) */
	public static String availableText() {
		StringBuilder out = new StringBuilder();
		ALL.stream().filter(Recipe::available).forEach((r) -> append(out, r));
		return out.toString();
	}

	private static void append(StringBuilder out, Recipe r) {
		out.append("  ").append(r.usage()).append(r.available() ? "" : "  (준비 중)").append('\n');
		out.append("      ").append(r.does()).append(". 빈칸: ").append(r.slots()).append('\n');
	}
}
