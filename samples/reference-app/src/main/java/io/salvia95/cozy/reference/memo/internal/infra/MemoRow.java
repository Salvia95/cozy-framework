package io.salvia95.cozy.reference.memo.internal.infra;

import java.time.Instant;

/** INSERT용 행. 생성된 id를 MyBatis가 채운다. */
public class MemoRow {

	private Long id;

	private String title;

	private String body;

	private boolean pinned;

	private Instant createdAt;

	private Instant updatedAt;

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

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

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
