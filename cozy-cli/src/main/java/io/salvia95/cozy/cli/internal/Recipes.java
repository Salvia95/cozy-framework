package io.salvia95.cozy.cli.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code cozy recipes}: 구조를 만드는 명령 목록 (ADR-0032 부록). 에이전트는 파일을 만들거나 옮기지 않고 이 명령을 쓴다. 아직 만들지 않은
 * 레시피는 {@code available: false}이고, 실행하면 종료 코드 2로 알린다.
 */
public final class Recipes {

	private Recipes() {
	}

	/**
	 * 레시피 하나.
	 *
	 * @param name {@code cozy add} 뒤의 이름, 또는 {@code link}
	 * @param stage 1단계(새 파일과 의존 선언) 또는 2단계(명령이 만든 파일의 본문에 덧붙임)
	 * @param usage 명령 형식
	 * @param does 무엇을 만드는지
	 * @param slots 에이전트가 채울 빈칸. 없으면 만든 직후 {@code mvn verify}가 통과한다
	 * @param available 지금 쓸 수 있는가
	 */
	public record Recipe(String name, int stage, String usage, String does, String slots, boolean available) {
	}

	public static final List<Recipe> ALL = List.of(
			new Recipe("module", 1, "cozy add module <모듈> [--label 라벨]", "빈 모듈(package-info.java, allowedDependencies = {})",
					"없음", false),
			new Recipe("link", 1, "cozy link <모듈> --uses <다른 모듈>[,…]", "package-info.java의 allowedDependencies에 \"모듈 :: spi\" 추가",
					"없음", false),
			new Recipe("crud", 1,
					"cozy add crud <모듈> --entity <E> --label 라벨 --icon 아이콘 --field 이름:종류[!][:라벨] … [--publish created,updated,deleted]",
					"목록·만들기·상세·수정·삭제 한 벌(spi, internal, api, SQL, 템플릿, 스펙, 테스트)", "없음", false),
			new Recipe("screen", 1,
					"cozy add screen <모듈> <화면> --pattern overview|details|list|master-detail --title 제목 [--field …] [--nav 라벨:아이콘]",
					"읽기 화면 하나(컨트롤러, 화면 DTO, 템플릿, 스펙)", "컨트롤러 view-model", false),
			new Recipe("fragment", 1, "cozy add fragment <모듈> <이름> --param 이름:타입 … --purpose \"용도\"",
					"다른 모듈에 공개하는 fragment와 매니페스트 항목", "템플릿 fragment-body", false),
			new Recipe("event", 1, "cozy add event <모듈> <이벤트> --field 이름:종류 …", "CozyEvent record와 직렬화 샘플 v1",
					"없음(발행할 한 줄은 에이전트가 넣는다)", false),
			new Recipe("listener", 1, "cozy add listener <모듈> --on <보내는 모듈>.<이벤트> [--name 이름]",
					"이벤트 리스너(필요하면 link 포함)", "listener-body", false),
			new Recipe("migration", 1, "cozy add migration <모듈> <설명>", "V{시각}__{모듈}_{설명}.sql", "migration-sql", false),
			new Recipe("query", 2, "cozy add query <모듈> <이름> --param … --returns …", "Finder 메서드, 구현, Mapper, SELECT",
					"SQL, 변환", false),
			new Recipe("command", 2, "cozy add command <모듈> <이름> --param … --returns …", "Facade 메서드와 구현", "메서드 본문",
					false),
			new Recipe("field", 2, "cozy add field <모듈> <필드>", "crud 한 벌에 필드 하나(마이그레이션부터 스펙까지)", "없음", false),
			new Recipe("event-version", 2, "cozy add event-version <모듈> <이벤트> --field …", "이벤트 필드 추가, schemaVersion 올림, 샘플 추가",
					"없음", false),
			new Recipe("enable events", 2, "cozy enable events", "pom에 cozy-starter-events 추가", "없음", false));

	public static Map<String, Object> model() {
		List<Object> recipes = new ArrayList<>();
		for (Recipe r : ALL) {
			recipes.add(Describe.ordered("name", r.name(), "stage", r.stage(), "usage", r.usage(), "does", r.does(), "slots",
					r.slots(), "available", r.available()));
		}
		return Describe.ordered("recipes", recipes);
	}

	public static String text() {
		StringBuilder out = new StringBuilder("구조는 아래 명령으로 바꾸고, 명령이 남긴 빈칸(cozy:slot)만 채운다 (ADR-0032).\n");
		for (int stage = 1; stage <= 2; stage++) {
			out.append('\n').append(stage).append("단계\n");
			for (Recipe r : ALL) {
				if (r.stage() == stage) {
					out.append("  ").append(r.usage()).append(r.available() ? "" : "  (준비 중)").append('\n');
					out.append("      ").append(r.does()).append(". 빈칸: ").append(r.slots()).append('\n');
				}
			}
		}
		return out.toString();
	}
}
