package {{infraPkg}};

{{instantImports}}/** INSERT용 행 (cozy add crud). 생성된 id를 MyBatis가 채운다. */
public class {{E}}Row {

	private Long id;

{{rowFields}}	private Instant createdAt;

	private Instant updatedAt;

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

{{rowAccessors}}	public Instant getCreatedAt() {
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
