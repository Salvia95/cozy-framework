package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * 화면 안의 동작 명령 여럿을 한 계획으로 실행한다({@link CozyCli#runTogether}). 워크벤치의 고치기 폼이 "바꾼 항목 → add, 지운 항목 → remove"를
 * 이것으로 부른다. 결과는 같은 명령을 터미널에서 차례로 실행한 것과 글자까지 같고, 하나라도 거절하면 아무 파일도 쓰지 않는다.
 */
class BehaviorTogetherTest {

	static final String SPEC = "src/main/resources/behaviors/memo.yml";

	static final String SCRIPT = "src/main/resources/static/memo/memo.js";

	static final String TEMPLATE = "src/main/resources/templates/memo/api/edit.html";

	/** 계산 label이 limit을 읽지 않게 고친 뒤 limit을 지운다. 지우기를 먼저 하면 label이 limit을 읽어 거절된다 */
	static final List<List<String>> EDIT_THEN_REMOVE = List.of(
			List.of("add", "behavior", "memo", "body-count", "--computed", "label:format('{length}자', 'ko-KR')"),
			List.of("remove", "behavior", "memo", "body-count", "--state", "limit"));

	@TempDir
	Path dir;

	@TempDir
	Path terminal;

	void app(Path at) throws IOException {
		App.of(at).module("memo", "{}");
		Path template = at.resolve(TEMPLATE);
		Files.createDirectories(template.getParent());
		Files.writeString(template, AttachCommandTest.EDIT, StandardCharsets.UTF_8);
		Result added = CozyCliTest.run("add", "behavior", "memo", "body-count", "--purpose", "본문을 쓰는 동안 글자 수를 보인다", "--state",
				"length:number@data-length", "--state", "limit:number=5000@data-limit", "--computed",
				"label:format('{length} / {limit}자', 'ko-KR')", "--action", "count:set(length, length(event.value))", "--project",
				at.toString());
		assertThat(added.code()).as(added.err()).isZero();
		Result attached = CozyCliTest.run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--on", "input:count", "--project",
				at.toString());
		assertThat(attached.code()).as(attached.err()).isZero();
	}

	Result together(List<List<String>> commands, String... options) {
		List<String> all = new ArrayList<>(List.of(options));
		all.add("--project");
		all.add(this.dir.toString());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.runTogether(commands, all, Path.of(""), CozyCliTest.REPOSITORY, new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	String read(Path at, String file) throws IOException {
		return Files.readString(at.resolve(file), StandardCharsets.UTF_8);
	}

	@Test
	void oneSharedPlanWritesWhatTheTerminalWritesInTheSameOrder() throws IOException {
		app(this.dir);
		app(this.terminal);

		Result result = together(EDIT_THEN_REMOVE);
		for (List<String> command : EDIT_THEN_REMOVE) {
			List<String> args = new ArrayList<>(command);
			args.addAll(List.of("--project", this.terminal.toString()));
			Result step = CozyCliTest.run(args.toArray(String[]::new));
			assertThat(step.code()).as(step.err()).isZero();
		}

		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("[cozy] 적용했습니다");
		for (String file : List.of(SPEC, SCRIPT, TEMPLATE)) {
			assertThat(read(this.dir, file)).as(file).isEqualTo(read(this.terminal, file));
		}
		assertThat(read(this.dir, SPEC)).doesNotContain("limit");
	}

	@Test
	void thePlanListsEachFileOnceAndTheDryRunWritesNothing() throws IOException {
		app(this.dir);
		String before = read(this.dir, SPEC);

		Result plan = together(EDIT_THEN_REMOVE, "--dry-run", "--json");

		assertThat(plan.code()).as(plan.err()).isZero();
		assertThat(plan.out()).contains("\"recipe\": \"add behavior, remove behavior\"", "\"applied\": false",
				"계산 바꿈 label; 컴포넌트 memoBodyCount: 상태 limit 지움");
		assertThat(plan.out().split("\"file\": \"" + SPEC + "\"", -1)).hasSize(2);
		assertThat(plan.out()).as("같은 내용은 한 번만").contains("\"detail\": \"동작 명세에서 만듦\"");
		assertThat(read(this.dir, SPEC)).isEqualTo(before);
	}

	@Test
	void whenALaterCommandIsRefusedNothingIsWritten() throws IOException {
		app(this.dir);
		String spec = read(this.dir, SPEC);
		String script = read(this.dir, SCRIPT);

		Result refused = together(List.of(EDIT_THEN_REMOVE.get(0), List.of("remove", "behavior", "memo", "body-count", "--action", "count")));

		assertThat(refused.code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(refused.err()).contains("템플릿이 부르는 이름입니다: count");
		assertThat(read(this.dir, SPEC)).isEqualTo(spec);
		assertThat(read(this.dir, SCRIPT)).isEqualTo(script);
	}

	@Test
	void onlyBehaviorCommandsAndCommonOptionsGoTogether() throws IOException {
		app(this.dir);

		assertThat(together(List.of(List.of("add", "module", "voc"))).err()).contains("한 계획으로 묶는 명령은 add behavior, remove behavior");
		assertThat(together(EDIT_THEN_REMOVE, "--force").err()).contains("함께 쓰는 옵션은");
		assertThat(together(List.of()).err()).contains("한 계획으로 묶을 명령이 없습니다");
	}
}
