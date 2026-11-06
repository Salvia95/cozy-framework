package io.salvia95.cozy.reference.memo.view;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 메모 등록·수정 입력. 화면 입력은 getter/setter가 있는 폼 클래스로 받는다 (PRD §10). */
public class MemoForm {

	@NotBlank(message = "제목을 입력하세요")
	@Size(max = 100, message = "제목은 100자까지 쓸 수 있습니다")
	private String title;

	@Size(max = 5000, message = "본문은 5000자까지 쓸 수 있습니다")
	private String body;

	private boolean pinned;

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return this.body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public boolean isPinned() {
		return this.pinned;
	}

	public void setPinned(boolean pinned) {
		this.pinned = pinned;
	}
}
