package {{dtoPkg}};

/** {{L}} 목록 필터 (GET 쿼리 파라미터, cozy add crud). q는 {{searchLabels}}에서 찾는다. */
public class {{E}}Filter {

	private String q;

	private Integer page;

	public String getQ() {
		return this.q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public Integer getPage() {
		return this.page;
	}

	public void setPage(Integer page) {
		this.page = page;
	}
}
