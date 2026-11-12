package ${package}.home.api.dto;

/** 목록 필터 (GET 쿼리 파라미터). 입력은 getter/setter가 있는 폼 클래스로 받는다. */
public class NoteFilter {

	/** 검색어 */
	private String q;

	public String getQ() {
		return this.q;
	}

	public void setQ(String q) {
		this.q = q;
	}
}
