package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;
import io.salvia95.cozy.rules.BehaviorRules;

/**
 * {@code cozy add behavior}: 모듈의 동작 명세({@code behaviors/{모듈}.yml})에 컴포넌트를 선언하거나 고치고, 그 명세에서
 * {@code static/{모듈}/{모듈}.js}를 만든다. 빈칸을 남기지 않는다. {@code cozy sync behaviors}: 명세에서 JS를 다시 만든다. 명세와 JS가 맞지
 * 않으면 {@code cozy check}가 C12로 실패한다.
 */
class BehaviorRecipeTest {

	static final String SPEC = "src/main/resources/behaviors/memo.yml";

	static final String FILE = "src/main/resources/static/memo/memo.js";

	@TempDir
	Path dir;

	@BeforeEach
	void app() throws IOException {
		App.of(this.dir).module("memo", "{}").module("wiki", "{}");
	}

	Result run(String... args) {
		String[] all = new String[args.length + 2];
		System.arraycopy(args, 0, all, 0, args.length);
		all[args.length] = "--project";
		all[args.length + 1] = this.dir.toString();
		return CozyCliTest.run(all);
	}

	String read(String file) throws IOException {
		return Files.readString(this.dir.resolve(file), StandardCharsets.UTF_8);
	}

	void write(String file, String content) throws IOException {
		Path path = this.dir.resolve(file);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	Result bodyCount() {
		return run("add", "behavior", "memo", "body-count", "--purpose", "본문을 쓰는 동안 글자 수를 보인다", "--state",
				"length:number@data-length", "--computed", "label:format('{length}자', 'ko-KR')", "--action",
				"count:within('textarea', set(length, length(event.value)))");
	}

	@Test
	void aNewBehaviorWritesTheSpecAndTheScriptWithoutSlots() throws IOException {
		Result result = bodyCount();

		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("만듦  " + SPEC, "만듦  " + FILE, "다음: cozy attach memoBodyCount --at <템플릿>:<줄>", "C13")
			.doesNotContain("빈칸");
		assertThat(read(SPEC)).isEqualTo("""
				# memo 모듈의 화면 안 동작(동작 명세). cozy add behavior가 고치고, 이 명세에서 static/memo/memo.js를 만든다
				components:
				  bodyCount:
				    purpose: "본문을 쓰는 동안 글자 수를 보인다"
				    state:
				      length: "number@data-length"
				    computed:
				      label: "format('{length}자', 'ko-KR')"
				    actions:
				      count: "within('textarea', set(length, length(event.value)))"
				""");
		assertThat(read(FILE)).contains("window.Alpine.data('memoBodyCount', function () {", "this.length = readNumber(root, 'length', 0);",
				"get label() {", "count: function (event) {").doesNotContain("cozy:slot");
		// 선언만 하고 붙이지 않았으면 C13이다. 붙이면 통과한다
		Result check = run("check");
		assertThat(check.code()).isEqualTo(CozyCli.VIOLATIONS);
		assertThat(check.out()).contains("[C13] src/main/resources/behaviors/memo.yml:3 → memoBodyCount — 컴포넌트 memoBodyCount을 어느 템플릿에도 붙이지 않았습니다");
		write(AttachCommandTest.TEMPLATE, AttachCommandTest.EDIT);
		assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:5", "--on", "input:count").code()).isZero();
		assertThat(run("attach", "memoBodyCount", "--at", "memo/api/edit:7", "--text", "label").code()).isZero();
		Result attached = run("check");
		assertThat(attached.code()).as(attached.out()).isZero();
	}

	@Test
	void runningTheSameCommandAgainChangesNothing() {
		bodyCount();

		Result again = bodyCount();

		assertThat(again.code()).as(again.err()).isZero();
		assertThat(again.out()).contains("바뀐 것 없음");
	}

	@Test
	void anExistingBehaviorTakesNewEntriesAndChangedOnes() throws IOException {
		bodyCount();

		Result result = run("add", "behavior", "memo", "body-count", "--state", "limit:number=5000@data-limit", "--computed",
				"label:format('{length} / {limit}자', 'ko-KR')", "--computed", "over:gt(length, limit)");

		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("상태 limit", "계산 바꿈 label", "계산 over");
		assertThat(read(SPEC)).contains("purpose: \"본문을 쓰는 동안 글자 수를 보인다\"", "limit: \"number=5000@data-limit\"",
				"label: \"format('{length} / {limit}자', 'ko-KR')\"", "over: \"gt(length, limit)\"");
		assertThat(read(FILE)).contains("get over() {");
	}

	@Test
	void twoBehaviorsShareOneSpecAndOneScript() throws IOException {
		bodyCount();

		Result result = run("add", "behavior", "memo", "pin-toggle", "--purpose", "고정을 펼친다", "--state", "open:bool", "--action",
				"flip:toggle(open)");

		assertThat(result.code()).as(result.err()).isZero();
		assertThat(read(SPEC)).contains("  bodyCount:", "  pinToggle:");
		assertThat(read(FILE)).contains("'memoBodyCount'", "'memoPinToggle'");
	}

	@Test
	void aHandEditedScriptFailsTheCheckAndSyncMakesItAgain() throws IOException {
		bodyCount();
		String made = read(FILE);
		write(FILE, made.replace("get label() {", "get label() {\n        console.log('x');"));

		Result check = run("check");
		assertThat(check.code()).isEqualTo(CozyCli.VIOLATIONS);
		assertThat(check.out()).contains("[C12] src/main/resources/static/memo/memo.js → memo — 동작 명세에서 만든 파일을 손으로 고쳤습니다",
				"고치는 명령: cozy sync behaviors");

		Result sync = run("sync", "behaviors");
		assertThat(sync.code()).as(sync.err()).isZero();
		assertThat(sync.out()).contains("바꿈  " + FILE);
		assertThat(read(FILE)).isEqualTo(made);
		assertThat(run("check").out()).doesNotContain("[C12]").contains("[C13]", "을 어느 템플릿에도 붙이지 않았습니다");
	}

	@Test
	void aHandEditedSpecIsCheckedTidiedAndMadeAgainBySync() throws IOException {
		bodyCount();
		write(SPEC, read(SPEC).replace("    actions:\n", "    computed2: {}\n    actions:\n"));
		assertThat(run("sync", "behaviors").err()).contains("모르는 키 computed2");

		write(SPEC, "components:\n  bodyCount:\n    purpose: 본문을 쓰는 동안 글자 수를 보인다\n    state: { length: number@data-length }\n"
				+ "    computed: { label: \"format('{length}개')\" }\n");
		assertThat(run("check").out()).contains("[C12] src/main/resources/behaviors/memo.yml → memo — 동작 명세를 고친 뒤");

		Result sync = run("sync", "behaviors");
		assertThat(sync.code()).as(sync.err()).isZero();
		assertThat(read(SPEC)).contains("      label: \"format('{length}개')\"").doesNotContain("actions:");
		assertThat(read(FILE)).doesNotContain("count: function").contains("'개'");
		assertThat(run("check").out()).doesNotContain("[C12]");
		assertThat(run("sync", "behaviors").out()).contains("바뀐 것 없음");
	}

	@Test
	void syncRemovesAScriptWhoseSpecWasDeleted() throws IOException {
		bodyCount();
		Files.delete(this.dir.resolve(SPEC));
		assertThat(run("check").out()).contains("[C12] src/main/resources/static/memo/memo.js", "명세가 없습니다");

		Result sync = run("sync", "behaviors");

		assertThat(sync.out()).contains("지움  " + FILE);
		assertThat(this.dir.resolve(FILE)).doesNotExist();
	}

	@Test
	void aHandWrittenModuleScriptIsNeverOverwritten() throws IOException {
		String hand = "document.addEventListener('alpine:init', function () {\n  window.Alpine.data('memoOld', function () {\n"
				+ "    return {};\n  });\n});\n";
		write(FILE, hand);

		Result result = bodyCount();

		assertThat(result.code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains("손으로 쓴 컴포넌트 파일입니다");
		assertThat(read(FILE)).isEqualTo(hand);
		assertThat(this.dir.resolve(SPEC)).doesNotExist();
	}

	@Test
	void aRefusedCommandChangesNoFile() throws IOException {
		bodyCount();
		String spec = read(SPEC);
		String js = read(FILE);

		for (String[] args : new String[][] { { "--computed", "length:not(true)" }, { "--action", "count:toggle(length)" },
				{ "--computed", "x:sum(length)" }, { "--state", "x:date" }, { "--listen", "nope:memo:saved" } }) {
			Result result = run("add", "behavior", "memo", "body-count", args[0], args[1]);
			assertThat(result.code()).as(String.join(" ", args)).isEqualTo(CozyCli.UNUSABLE);
		}

		assertThat(read(SPEC)).isEqualTo(spec);
		assertThat(read(FILE)).isEqualTo(js);
		assertThat(BehaviorRules.check(this.dir.resolve("src/main/resources"), "")).isEmpty();
	}

	@Test
	void argumentsAreChecked() {
		assertThat(run("add", "behavior", "memo", "count").err()).contains("--purpose로 목적 한 문장");
		assertThat(run("add", "behavior", "memo", "CharCount", "--purpose", "x").err()).contains("소문자와 하이픈");
		assertThat(run("add", "behavior", "nope", "count", "--purpose", "x").err()).contains("모듈 'nope'가 없습니다");
		assertThat(run("add", "behavior", "memo", "count", "--purpose", "x", "--state", "length").err())
			.contains("--state는 이름:종류");
		assertThat(run("add", "behavior", "memo").code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(run("sync", "behaviors", "--force").err()).contains("--force는 cozy sync agents에만");
		assertThat(this.dir.resolve(FILE)).doesNotExist();
		assertThat(this.dir.resolve(SPEC)).doesNotExist();
	}

	@Test
	void syncWithoutSpecsSaysHowToDeclareOne() {
		Result sync = run("sync", "behaviors");

		assertThat(sync.code()).as(sync.err()).isZero();
		assertThat(sync.out()).contains("cozy add behavior로 선언합니다");
	}
}
