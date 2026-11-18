package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.App;
import io.salvia95.cozy.cli.CozyCliTest.Result;
import io.salvia95.cozy.cli.internal.behavior.LegacyScript;
import io.salvia95.cozy.rules.BehaviorRules;
import io.salvia95.cozy.rules.ComponentContract;

/**
 * {@code cozy migrate behaviors}: 손으로 쓴 모듈 JS의 컴포넌트를 낱말로 읽어 동작 명세로 옮기고 그 명세에서 JS를 다시 만든다. 낱말 밖의 JS가 하나라도
 * 있으면 그 모듈은 그대로 두고 어디인지 알린다. 옮기기 전에는 {@code cozy check}가 C12 경고로 이 명령을 알린다.
 */
class MigrateBehaviorsTest {

	static final String SPEC = "src/main/resources/behaviors/memo.yml";

	static final String FILE = "src/main/resources/static/memo/memo.js";

	static final String WIKI = "src/main/resources/static/wiki/wiki.js";

	/** 옛 cozy add behavior의 모양에 에이전트가 채운 컴포넌트 */
	static final String PANEL = """
			/* memo 모듈의 Alpine 컴포넌트: 서버를 거치지 않는 화면 안의 상태
			   - 이름의 앞은 모듈 이름(memo)이다. */
			document.addEventListener('alpine:init', function () {
			  // 고정 패널을 펼치고 접는다
			  window.Alpine.data('memoPanel', function () {
			    var root = null;
			    return {
			      open: false,
			      limit: 0,
			      title: '',
			      init: function () {
			        root = this.$root;
			        this.limit = parseInt(root.dataset.limit, 10) || 5000;
			        this.title = root.dataset.memoTitle;
			        var self = this;
			        window.addEventListener('memo:archived', function () {
			          self.close();
			        });
			      },
			      /** 버튼 글자 */
			      get label() {
			        return this.open ? '접기' : '펼치기';
			      },
			      get summary() {
			        var size = this.limit.toLocaleString('ko-KR');
			        return this.title + ' · ' + size + '자';
			      },
			      get tooLong() {
			        return this.title.length > this.limit && !this.open;
			      },
			      toggle: function () {
			        this.open = !this.open;
			      },
			      close: function () {
			        if (this.open) {
			          this.open = false;
			          this.$dispatch('memo:closed', { open: this.open });
			        }
			      },
			      rename(event) {
			        this.title = event.target.value;
			        this.limit -= 1;
			      }
			    };
			  });
			});
			""";

	/** 2.1.0까지 reference-app의 글자 수: 시작할 때 화면의 textarea를 읽는다(낱말 밖) */
	static final String BODY_COUNT = """
			document.addEventListener('alpine:init', function () {
			  // 본문 글자 수
			  window.Alpine.data('wikiBodyCount', function () {
			    var root = null;
			    return {
			      length: 0,
			      limit: 0,
			      init: function () {
			        root = this.$root;
			        this.limit = parseInt(root.dataset.limit, 10) || 0;
			        var area = root.querySelector('textarea');
			        this.length = area ? area.value.length : 0;
			      },
			      count: function (event) {
			        this.length = event.target.value.length;
			      }
			    };
			  });
			});
			""";

	@TempDir
	Path dir;

	@BeforeEach
	void app() throws IOException {
		App.of(this.dir).module("memo", "{}").module("wiki", "{}");
	}

	@Test
	void aHandWrittenComponentInTheOldShapeMovesToTheSpecAndItsScriptIsMadeAgain() throws IOException {
		write(FILE, PANEL);
		assertThat(run("check").out()).contains(
				"[C12 경고] src/main/resources/static/memo/memo.js → memo — 손으로 쓴 모듈 JS입니다(직접 쓴 코드)", "고치는 명령: cozy migrate behaviors");

		Result result = run("migrate", "behaviors");

		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("만듦  " + SPEC, "바꿈  " + FILE + "    손으로 쓴 JS를 동작 명세에서 만든 JS로 바꿈");
		assertThat(read(SPEC)).isEqualTo("""
				# memo 모듈의 화면 안 동작(동작 명세). cozy add behavior가 고치고, 이 명세에서 static/memo/memo.js를 만든다
				components:
				  panel:
				    purpose: "고정 패널을 펼치고 접는다"
				    state:
				      open: "bool"
				      limit: "number=5000@data-limit"
				      title: "text@data-memo-title"
				    computed:
				      label: "if(open, '접기', '펼치기')"
				      summary: "format('{title} · {limit}자', 'ko-KR')"
				      tooLong: "and(gt(length(title), limit), not(open))"
				    actions:
				      toggle: "toggle(open)"
				      close: "when(open, set(open, false), emit('memo:closed', open))"
				      rename: "set(title, event.value); decrement(limit)"
				    listens:
				      "memo:archived": "close"
				""");
		String script = read(FILE);
		assertThat(BehaviorRules.isGenerated(script)).isTrue();
		assertThat(ComponentContract.parse(script).get("memoPanel").members()).containsExactlyInAnyOrder("open", "limit", "title", "label",
				"summary", "tooLong", "toggle", "close", "rename");
		assertThat(BehaviorRules.check(this.dir.resolve("src/main/resources"), "")).isEmpty();
		assertThat(run("migrate", "behaviors").out()).contains("바뀐 것 없음");
	}

	@Test
	void aComponentOutsideTheVocabularyLeavesItsModuleAsItIsAndSaysWhere() throws IOException {
		write(FILE, PANEL);
		write(WIKI, BODY_COUNT);

		Result result = run("migrate", "behaviors", "--dry-run");

		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("만듦  " + SPEC)
			.contains(WIKI + ":11")
			.contains("wikiBodyCount: init에서는")
			.contains("var area = root.querySelector('textarea')")
			.contains("이 모듈은 옮기지 않았습니다")
			.doesNotContain("behaviors/wiki.yml");
		assertThat(this.dir.resolve(SPEC)).doesNotExist();
		assertThat(read(WIKI)).isEqualTo(BODY_COUNT);
	}

	@Test
	void eachShapeOutsideTheVocabularyIsReportedWithItsCode() {
		Map<String, String> cases = Map.ofEntries(Map.entry("save: function () { this.persist(); }", "this.persist()"),
				Map.entry("pick: function () { if (this.open) { this.open = false; } else { this.open = true; } }", "else"),
				Map.entry("get total() { return this.limit + this.limit; }", "숫자 더하기"),
				Map.entry("get first() { return this.items.filter((i) => i !== '').length; }", "this.items.filter"),
				Map.entry("items: null", "null"),
				Map.entry("destroy: function () {}", "destroy는 옮기지 않습니다"),
				Map.entry("grow: function () { this.limit = this.limit * 2; }", "낱말로 적을 수 없는 식"),
				Map.entry("name: function () { this.open = event.target.checked; }", "낱말로 적을 수 없는 식"),
				Map.entry("get same() { return this.limit == 1; }", "느슨한 비교(==)"));
		cases.forEach((member, expected) -> {
			String source = "document.addEventListener('alpine:init', function () {\n  // 목적\n  window.Alpine.data('memoX', function () {\n"
					+ "    return {\n      open: false,\n      limit: 0,\n      items: [],\n      " + member + "\n    };\n  });\n});\n";
			LegacyScript.Result result = LegacyScript.read("memo", source, Map.of());
			assertThat(result.components()).as(member).isEmpty();
			assertThat(result.problems()).as(member).singleElement().satisfies((p) -> {
				assertThat(p.component()).isEqualTo("memoX");
				assertThat(p.what() + " " + p.code()).as(member).contains(expected);
			});
		});
	}

	@Test
	void theValueAfterOrIsTheStartValueWhenTheAttributeIsMissing() {
		String source = "document.addEventListener('alpine:init', function () {\n  // 목적\n  window.Alpine.data('memoX', function () {\n"
				+ "    var root = null;\n    return {\n      limit: 10,\n      size: 3,\n      init: function () {\n        root = this.$root;\n"
				+ "        this.limit = parseInt(root.dataset.limit, 10) || 0;\n        this.size = Number(this.$el.dataset.size);\n      },\n"
				+ "      grow: function () { this.limit++; this.size += 2; }\n    };\n  });\n});\n";

		LegacyScript.Result result = LegacyScript.read("memo", source, Map.of());

		assertThat(result.problems()).isEmpty();
		assertThat(result.components()).singleElement().satisfies((c) -> {
			assertThat(c.state().get("limit").print()).isEqualTo("number@data-limit");
			assertThat(c.state().get("size").print()).isEqualTo("number=3@data-size");
			assertThat(io.salvia95.cozy.cli.internal.behavior.Expr.print(c.actions().get("grow"))).isEqualTo("increment(limit); increment(size, 2)");
		});
	}

	@Test
	void theFileMustBeTheOldShapeAndEachComponentNeedsAPurpose() throws IOException {
		assertThat(LegacyScript.read("memo", "window.foo = 1;", Map.of()).problems()).singleElement()
			.satisfies((p) -> assertThat(p.what()).startsWith("파일의 모양"));
		// 낱말로 나눌 수 없는 파일은 컴포넌트를 가리지 않고 통째로 옮기지 않는다
		assertThat(LegacyScript.read("memo", PANEL.replace("'접기'", "`접기`"), Map.of()).problems()).singleElement().satisfies((p) -> {
			assertThat(p.component()).isNull();
			assertThat(p.what()).contains("템플릿 문자열");
			assertThat(p.line()).isEqualTo(22);
		});
		String noPurpose = PANEL.replace("  // 고정 패널을 펼치고 접는다\n", "");
		assertThat(LegacyScript.read("memo", noPurpose, Map.of()).problems()).singleElement()
			.satisfies((p) -> assertThat(p.what()).contains("목적 한 문장이 없습니다", "--purpose memoPanel=문장"));

		write(FILE, noPurpose);
		Result unknown = run("migrate", "behaviors", "--purpose", "memoNope=x");
		assertThat(unknown.code()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(unknown.err()).contains("'memoNope'가 손으로 쓴 모듈 JS에 없습니다", "memoPanel");

		Result given = run("migrate", "behaviors", "--purpose", "memoPanel=패널을 바꾼다");
		assertThat(given.code()).as(given.err()).isZero();
		assertThat(read(SPEC)).contains("    purpose: \"패널을 바꾼다\"");
	}

	@Test
	void aComponentWithTheSameNameAsOneInTheSpecIsNotMoved() throws IOException {
		Result added = run("add", "behavior", "memo", "panel", "--purpose", "다른 패널", "--state", "open:bool");
		assertThat(added.code()).as(added.err()).isZero();
		String spec = read(SPEC);
		write(FILE, PANEL);

		Result result = run("migrate", "behaviors");

		assertThat(result.out()).contains("memoPanel: 동작 명세에 같은 이름의 컴포넌트가 이미 있습니다");
		assertThat(read(SPEC)).isEqualTo(spec);
		assertThat(read(FILE)).isEqualTo(PANEL);
	}

	@Test
	void withoutHandWrittenScriptsThereIsNothingToDo() throws IOException {
		Result result = run("migrate", "behaviors");

		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.out()).contains("손으로 쓴 모듈 JS가 없습니다");
		assertThat(run("migrate", "nope").err()).contains("alpine, events, behaviors");
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
}
