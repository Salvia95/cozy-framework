package ${package}.home.internal;

import java.time.Instant;

/** INSERT용 행. MyBatis가 생성된 id를 채운다. */
public class NoteRow {

	private Long id;

	private String title;

	private Instant createdAt;

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

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
