package io.salvia95.cozy.reference.memo.api.dto;

/** 목록 필터 (GET 쿼리 파라미터). */
public class MemoFilter {

	private String q;

	private boolean pinned;

	private Integer page;

	public String getQ() {
		return this.q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public boolean isPinned() {
		return this.pinned;
	}

	public void setPinned(boolean pinned) {
		this.pinned = pinned;
	}

	public Integer getPage() {
		return this.page;
	}

	public void setPage(Integer page) {
		this.page = page;
	}
}
