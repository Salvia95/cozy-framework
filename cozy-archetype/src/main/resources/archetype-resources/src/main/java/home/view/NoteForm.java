package ${package}.home.view;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 메모 등록 입력. 검증 메시지는 화면에 그대로 보인다. */
public class NoteForm {

	@NotBlank(message = "제목을 입력하세요")
	@Size(max = 100, message = "제목은 100자까지 쓸 수 있습니다")
	private String title;

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
}
