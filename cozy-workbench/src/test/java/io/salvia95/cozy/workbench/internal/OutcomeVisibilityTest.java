package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 적용한 파일이 언제 보이는가: 새로고침(바로 보임)인가, 다시 빌드하고 실행(빌드 필요)인가 */
class OutcomeVisibilityTest {

	static WorkbenchEngine.Change edit(String file) {
		return new WorkbenchEngine.Change("edit", file, null);
	}

	static WorkbenchEngine.Outcome outcome(WorkbenchEngine.Change... changes) {
		return new WorkbenchEngine.Outcome(true, List.of(changes), List.of(), List.of(), "", null);
	}

	@Test
	void templatesAndStaticShowOnRefreshOnlyWhenReadFromTheSource() {
		WorkbenchEngine.Change template = edit("src/main/resources/templates/memo/internal/fields.html");
		WorkbenchEngine.Change script = edit("src/main/resources/static/memo/memo.js");
		WorkbenchEngine.Change spec = edit("src/main/resources/behaviors/memo.yml");
		WorkbenchEngine.Change java = edit("src/main/java/it/cozy/app/memo/api/MemoController.java");

		assertThat(template.immediate(true)).isTrue();
		assertThat(script.immediate(true)).isTrue();
		assertThat(template.immediate(false)).isFalse();
		assertThat(spec.immediate(false)).as("동작 명세와 화면 명세는 워크벤치가 소스에서 읽는다").isTrue();
		assertThat(java.immediate(true)).isFalse();
		assertThat(new WorkbenchEngine.Change("delete", "src/main/resources/static/memo/memo.js", null).immediate(true))
			.as("지운 파일은 빌드 결과에 남아 계속 보인다")
			.isFalse();
	}

	@Test
	void theLineSaysWhichFilesNeedABuild() {
		WorkbenchEngine.Change template = edit("src/main/resources/templates/memo/internal/fields.html");
		WorkbenchEngine.Change java = edit("src/main/java/it/cozy/app/memo/api/MemoController.java");
		WorkbenchEngine.Change same = new WorkbenchEngine.Change("unchanged", "src/main/resources/static/memo/memo.js", null);

		assertThat(outcome(template, same).visibility(true)).isEqualTo("바뀐 파일은 모두 새로고침하면 미리보기와 화면에 보입니다.");
		assertThat(outcome(template).visibility(false)).isEqualTo("바뀐 파일은 앱을 다시 빌드하고 실행한 뒤 보입니다.");
		assertThat(outcome(template, java).visibility(true)).isEqualTo("바뀐 파일 1개는 새로고침하면 보이고, 1개는 앱을 다시 빌드하고 실행한 뒤 보입니다.");
		assertThat(outcome(same).visibility(true)).as("바뀐 것이 없으면 알리지 않는다").isNull();
	}
}
