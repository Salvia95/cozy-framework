package io.salvia95.cozy.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.salvia95.cozy.cli.CozyCliTest.Result;

/**
 * {@code cozy add field}: crud 한 벌에 필드 하나. 필드 N개로 만든 crud와, N-1개로 만든 뒤 필드를 더한 결과가 같아야 한다. 자리를 찾지 못하면
 * 아무 파일도 바꾸지 않고 실패한다. 더한 앱이 {@code mvn verify}를 통과하는지는 samples/recipes가 확인한다.
 */
class FieldRecipeTest {

	static final String JAVA = "src/main/java/it/cozy/app/memo/";

	static final String RESOURCES = "src/main/resources/";

	static final String TESTS = "src/test/java/it/cozy/app/memo/";

	static final String[] CRUD = { "add", "crud", "memo", "--entity", "Memo", "--label", "메모", "--icon", "notes", "--field",
			"title:text!:제목", "--field", "pinned:bool:고정" };

	@TempDir
	Path root;

	Path dir;

	@BeforeAll
	static void fixTheClock() {
		System.setProperty("cozy.cli.now", "202611081000");
	}

	/** 아키타입(persistence=true)과 같은 의존의 빈 앱 */
	void app(String name) throws IOException {
		this.dir = this.root.resolve(name);
		write("pom.xml", """
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <parent>
				    <groupId>io.salvia95.cozy</groupId>
				    <artifactId>cozy-parent</artifactId>
				    <version>%s</version>
				  </parent>
				  <artifactId>test-app</artifactId>
				  <dependencies>
				    <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-ui</artifactId></dependency>
				    <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-starter-sqlite</artifactId></dependency>
				  </dependencies>
				</project>
				""".formatted(CozyCliTest.VERSION));
		write("src/main/java/it/cozy/app/App.java", "package it.cozy.app;\n\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
				+ "@SpringBootApplication\npublic class App {\n}\n");
		write("src/main/resources/cozy/fragments.yml", "# 매니페스트\nfragments: []\n");
	}

	/** title, pinned 두 필드의 crud */
	void memo() throws IOException {
		app("app");
		ok(CRUD);
	}

	Result cozy(String... args) {
		List<String> all = new ArrayList<>(List.of(args));
		all.addAll(List.of("--project", this.dir.toString()));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = CozyCli.run(all.toArray(String[]::new), Path.of(""), CozyCliTest.REPOSITORY,
				new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	Result ok(String... args) {
		Result result = cozy(args);
		assertThat(result.code()).as(result.err()).isZero();
		return result;
	}

	Result field(String field, String... more) {
		return ok(concat(new String[] { "add", "field", "memo", "--entity", "Memo", "--field", field }, more));
	}

	Result fails(String message, String... args) {
		Map<String, String> before = files();
		Result result = cozy(args);
		assertThat(result.code()).as(result.out()).isEqualTo(CozyCli.UNUSABLE);
		assertThat(result.err()).contains(message);
		assertThat(files()).as("실패한 명령은 아무 파일도 바꾸지 않는다").isEqualTo(before);
		return result;
	}

	Result fieldFails(String message, String field, String... more) {
		return fails(message, concat(new String[] { "add", "field", "memo", "--entity", "Memo", "--field", field }, more));
	}

	String read(String path) {
		try {
			return Files.readString(this.dir.resolve(path), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	void write(String path, String content) throws IOException {
		Path file = this.dir.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	/** 있는 글자를 바꾼다. 없으면 테스트가 잘못된 것이다 */
	void change(String path, String from, String to) throws IOException {
		String text = read(path);
		assertThat(text).contains(from);
		write(path, text.replace(from, to));
	}

	Map<String, String> files() {
		try (Stream<Path> paths = Files.walk(this.dir)) {
			Map<String, String> result = new TreeMap<>();
			paths.filter(Files::isRegularFile)
				.forEach((p) -> result.put(this.dir.relativize(p).toString().replace('\\', '/'),
						read(this.dir.relativize(p).toString())));
			return result;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static String[] concat(String[] first, String... more) {
		String[] all = Arrays.copyOf(first, first.length + more.length);
		System.arraycopy(more, 0, all, first.length, more.length);
		return all;
	}

	/**
	 * 비교에서 빼는 것: 마이그레이션(더한 쪽은 ALTER TABLE 파일이 따로 있다), 목록과 상세 템플릿(배치는 따로 확인한다), import의 순서, 그리고 글
	 * 필드의 검색 조건과 검색 문구(더한 필드는 검색에 넣지 않는다).
	 */
	Map<String, String> comparable(boolean searchable, boolean detail) {
		Map<String, String> result = new TreeMap<>();
		files().forEach((path, text) -> {
			if (path.contains("db/migration/") || (!detail && path.endsWith("api/detail.html"))) {
				return;
			}
			if (path.endsWith(".java")) {
				List<String> lines = text.lines().toList();
				String imports = lines.stream().filter((l) -> l.startsWith("import ")).sorted().collect(Collectors.joining("\n"));
				String rest = lines.stream().filter((l) -> !l.startsWith("import ") && !l.isBlank()).collect(Collectors.joining("\n"));
				text = imports + "\n" + rest;
			}
			if (searchable) {
				text = text.lines()
					.filter((l) -> !l.contains(" LIKE ") && !l.contains("q는 ") && !l.contains("{@code query}가 들어간")
							&& !l.contains("placeholder="))
					.collect(Collectors.joining("\n"));
			}
			result.put(path, text);
		});
		return result;
	}

	void sameAsCrud(String field, String... more) throws IOException {
		sameAsCrud(CRUD, !field.contains(":longtext"), field, more);
	}

	void sameAsCrud(String[] crud, boolean detail, String field, String... more) throws IOException {
		boolean searchable = field.contains(":text") || field.contains(":longtext");
		app("whole-" + crud.length + field.hashCode() + more.length);
		ok(concat(crud, "--field", field));
		Map<String, String> whole = comparable(searchable, detail);
		app("added-" + crud.length + field.hashCode() + more.length);
		ok(crud);
		Result added = field(field, more);
		assertThat(added.out()).contains("[cozy] 적용했습니다\n");
		Map<String, String> result = comparable(searchable, detail);
		assertThat(result.keySet()).as(field).isEqualTo(whole.keySet());
		whole.forEach((path, text) -> assertThat(result.get(path)).as(field + " " + path).isEqualTo(text));
		assertThat(field(field, more).out()).as("다시 실행").contains("[cozy] 바뀐 것 없음");
	}

	@Test
	void addingAFieldGivesTheSameFilesAsCreatingTheCrudWithIt() throws IOException {
		sameAsCrud("note:text:비고");
		sameAsCrud("body:longtext:본문");
		sameAsCrud("amount:int:수량");
		sameAsCrud("ownerId:long:담당자");
		sameAsCrud("archived:bool:보관");
		sameAsCrud("dueOn:date:마감일");
		sameAsCrud("code:text!:코드", "--default", "없음");
		sameAsCrud("amount:int!:수량", "--default", "0");
		sameAsCrud("dueOn:date!:마감일", "--default", "2026-01-01");
		// 긴 글이 이미 있는 crud는 상세 배치도 같다. 첫 긴 글은 넓은 칸의 끝에 카드로 더한다
		String[] withBody = concat(CRUD, "--field", "body:longtext:본문");
		sameAsCrud(withBody, true, "remark:longtext:비고");
		sameAsCrud(withBody, true, "dueOn:date:마감일");
	}

	@Test
	void theFirstLongTextBecomesACardAtTheEndOfTheWideColumn() throws IOException {
		memo();
		field("body:longtext:본문");
		assertThat(read(RESOURCES + "templates/memo/api/detail.html")).contains("""
				          </div>
				        </div>
				        <div class="card">
				          <div class="card-header"><h3 class="card-title">본문</h3></div>
				          <div class="card-body">
				            <th:block th:if="${item.bodyLines.isEmpty()}"><p class="text-secondary mb-0">본문이 없습니다.</p></th:block>
				            <p class="mb-2" th:each="line : ${item.bodyLines}" th:text="${line}">본문</p>
				          </div>
				        </div>
				      </div>
				      <div class="col-lg-4">
				""");
		assertThat(read(RESOURCES + "templates/memo/api/list.html")).doesNotContain("row.body").doesNotContain("본문");
	}

	@Test
	void aListOrDetailTheCommandCannotReadIsLeftAsWorkToDoByHand() throws IOException {
		memo();
		change(RESOURCES + "templates/memo/api/list.html", "th:text=\"${row.updatedAt}\"", "th:text=\"${#strings.substring(row.updatedAt, 0, 10)}\"");
		change(RESOURCES + "templates/memo/api/detail.html", "<div class=\"col-lg-8\">", "<div class=\"col-12\">");
		String list = read(RESOURCES + "templates/memo/api/list.html");
		Result dueOn = field("dueOn:date:마감일");
		assertThat(dueOn.out()).contains("[cozy] 적용했습니다. 남은 일 1곳\n").contains("""
				  src/main/resources/templates/memo/api/list.html
				    목록에 마감일 열을 더하세요. 머리 줄에 th, 행에 td입니다
				      <th class="w-1">마감일</th>
				      <td class="text-secondary text-nowrap" th:text="${row.dueOn} ?: '—'">—</td>
				""");
		assertThat(read(RESOURCES + "templates/memo/api/list.html")).isEqualTo(list);
		assertThat(read(RESOURCES + "templates/memo/api/detail.html")).contains("<div class=\"datagrid-title\">마감일</div>");
		Result body = field("body:longtext:본문", "--json");
		assertThat(body.out()).contains("""
				  "manual": [
				    {
				      "file": "src/main/resources/templates/memo/api/detail.html",
				      "what": "상세에 본문 카드를 더하세요",
				""").contains("\"snippet\": \"<div class=\\\"card\\\">\\n");
		assertThat(field("dueOn:date:마감일").out()).contains("[cozy] 바뀐 것 없음");
		assertThat(ok("add", "module", "voc", "--json").out()).contains("\"manual\": [],\n");
	}


	@Test
	void theMigrationAddsOneColumnAndTheListSearchIsLeftAlone() throws IOException {
		memo();
		Result result = field("dueOn:date:마감일");
		assertThat(result.out()).contains("만듦  src/main/resources/db/migration/V202611081001__memo_add_memo_due_on.sql")
			.contains("바꿈  " + JAVA + "spi/MemoFacade.java")
			.contains("[cozy] 다음: mvn verify");
		assertThat(read(RESOURCES + "db/migration/V202611081001__memo_add_memo_due_on.sql"))
			.endsWith("ALTER TABLE memo ADD COLUMN due_on TEXT;\n");
		field("note:text:비고");
		assertThat(read(RESOURCES + "mapper/memo/MemoMapper.xml")).contains("<sql id=\"columns\">id, title, pinned, due_on, note, created_at, updated_at</sql>")
			.contains("(title LIKE #{like} ESCAPE '\\')")
			.contains("INSERT INTO memo (title, pinned, due_on, note, created_at, updated_at)")
			.contains("VALUES (#{title}, #{pinned}, #{dueOn}, #{note}, #{createdAt}, #{updatedAt})")
			.contains("SET title = #{title}, pinned = #{pinned}, due_on = #{dueOn}, note = #{note}, updated_at = #{updatedAt}");
		assertThat(read(JAVA + "spi/MemoFacade.java")).contains("long create(String title, boolean pinned, LocalDate dueOn, String note);")
			.contains("boolean update(long id, String title, boolean pinned, LocalDate dueOn, String note);")
			.contains("import java.time.LocalDate;");
		assertThat(read(JAVA + "api/MemoController.java"))
			.contains("this.facade.create(form.getTitle(), form.isPinned(), form.getDueOn(), form.getNote())")
			.contains("this.facade.update(id, form.getTitle(), form.isPinned(), form.getDueOn(), form.getNote())");
		assertThat(read(TESTS + "MemoCrudTest.java"))
			.contains("this.facade.create(\"예시 제목\", true, LocalDate.of(2026, 11, 6), \"예시 비고\")");
	}

	@Test
	void requiredFieldsNeedADefaultForTheRowsThatAlreadyExist() throws IOException {
		memo();
		fieldFails("필수 필드(!)를 더하려면 --default", "code:text!:코드");
		fieldFails("--default는 필수 필드(!)에만 씁니다", "code:text:코드", "--default", "x");
		fieldFails("--default는 필수 필드(!)에만 씁니다", "archived:bool:보관", "--default", "1");
		fieldFails("숫자여야 합니다", "amount:int!:수량", "--default", "많이");
		fieldFails("yyyy-MM-dd", "dueOn:date!:마감일", "--default", "내일");
		fieldFails("빈 글자일 수 없습니다", "code:text!:코드", "--default", "");
		field("code:text!:코드", "--default", "it's");
		assertThat(read(RESOURCES + "db/migration/V202611081001__memo_add_memo_code.sql"))
			.endsWith("ALTER TABLE memo ADD COLUMN code TEXT NOT NULL DEFAULT 'it''s' CHECK (length(code) > 0);\n");
		field("amount:int!:수량", "--default", "-3");
		assertThat(read(RESOURCES + "db/migration/V202611081002__memo_add_memo_amount.sql"))
			.endsWith("ALTER TABLE memo ADD COLUMN amount INTEGER NOT NULL DEFAULT -3;\n");
		field("archived:bool:보관");
		assertThat(read(RESOURCES + "db/migration/V202611081003__memo_add_memo_archived.sql"))
			.endsWith("ALTER TABLE memo ADD COLUMN archived INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1));\n");
	}

	@Test
	void argumentsAreCheckedBeforeAnythingIsRead() throws IOException {
		memo();
		fails("--entity가 필요합니다", "add", "field", "memo", "--field", "note:text");
		fails("--field가 필요합니다", "add", "field", "memo", "--entity", "Memo");
		fails("--field는 한 번만 씁니다", "add", "field", "memo", "--entity", "Memo", "--field", "a:text", "--field", "b:text");
		fieldFails("instant를 쓰지 않습니다", "at:instant");
		fieldFails("명령이 직접 넣는 이름", "createdAt:date");
		fails("모듈 'voc'가 없습니다", "add", "field", "voc", "--entity", "Memo", "--field", "note:text");
		fails("crud 한 벌이 아닙니다", "add", "field", "memo", "--entity", "Note", "--field", "note:text");
		Files.delete(this.dir.resolve(JAVA + "api/dto/MemoForm.java"));
		fieldFails("crud 한 벌이 아닙니다", "note:text");
		fieldFails(JAVA + "api/dto/MemoForm.java", "note:text");
	}

	@Test
	void aFieldThatIsAlreadyThereChangesNothingAndAHalfAddedOneFails() throws IOException {
		memo();
		Map<String, String> before = files();
		assertThat(field("pinned:bool:고정").out()).contains("[cozy] 바뀐 것 없음");
		assertThat(files()).isEqualTo(before);
		fieldFails("title가 이미 String로 있습니다", "title:int");
		change(JAVA + "internal/domain/Memo.java", "boolean pinned, ", "boolean pinned, String note, ");
		fieldFails("일부에만 있습니다", "note:text:비고");
		fieldFails(JAVA + "internal/domain/Memo.java", "note:text:비고");
	}

	@Test
	void codeTheAgentHasFilledInIsKept() throws IOException {
		memo();
		change(JAVA + "internal/app/command/MemoFacadeImpl.java", "\t\tInstant now = Instant.now(this.clock);\n",
				"\t\tInstant now = Instant.now(this.clock);\n\t\tif (title.startsWith(\"!\")) {\n\t\t\tpinned = true;\n\t\t}\n");
		change(JAVA + "api/dto/MemoForm.java", "\t/** 고정 */\n\tprivate boolean pinned;\n", "\t/** 목록 위에 고정 */\n\tprivate boolean pinned;\n");
		change(JAVA + "api/dto/MemoViews.java", "\tpublic static MemoForm formOf(MemoItem item) {\n",
				"\tpublic static String shortTitle(MemoItem item) {\n\t\treturn item.title().strip();\n\t}\n\n"
						+ "\tpublic static MemoForm formOf(MemoItem item) {\n");
		field("dueOn:date:마감일");
		assertThat(read(JAVA + "internal/app/command/MemoFacadeImpl.java")).contains("\t\t\tpinned = true;\n\t\t}\n")
			.contains("\t\trow.setPinned(pinned);\n\t\trow.setDueOn(dueOn);\n\t\trow.setCreatedAt(now);\n")
			.contains("this.mapper.update(id, title, pinned, dueOn, Instant.now(this.clock))");
		assertThat(read(JAVA + "api/dto/MemoForm.java"))
			.contains("\t/** 목록 위에 고정 */\n\tprivate boolean pinned;\n\n\t/** 마감일 */\n\t@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)\n\tprivate LocalDate dueOn;\n");
		assertThat(read(JAVA + "api/dto/MemoViews.java")).contains("return item.title().strip();")
			.as("화면 DTO의 설명: 워크벤치가 속성 이름으로 쓴다")
			.contains("\t * @param dueOn 마감일\n\t * @param updatedAt 수정한 시각\n\t */\n\tpublic record Row(")
			.contains("\t * @param dueOn 마감일\n\t * @param createdAt 만든 시각\n")
			.contains("\t\tform.setDueOn(item.dueOn());\n\t\treturn form;\n")
			.contains("\n\t\t\t\t&& Objects.equals(item.dueOn(), form.getDueOn());\n");
	}

	@Test
	void callersInMainCodeGetTheValueFromTheSameObjectOrTheCommandFails() throws IOException {
		memo();
		write(JAVA + "api/MemoQuickController.java", """
				package it.cozy.app.memo.api;

				import it.cozy.app.memo.api.dto.MemoForm;
				import it.cozy.app.memo.spi.MemoFacade;

				class MemoQuickController {

					private final MemoFacade memos;

					MemoQuickController(MemoFacade memos) {
						this.memos = memos;
					}

					long quick(MemoForm input) {
						return memos.create(input.getTitle(), input.isPinned());
					}
				}
				""");
		field("note:text:비고");
		assertThat(read(JAVA + "api/MemoQuickController.java")).contains("memos.create(input.getTitle(), input.isPinned(), input.getNote())");
		change(JAVA + "api/MemoQuickController.java", "input.getTitle(), input.isPinned(), input.getNote()", "\"빠른 메모\", false, null");
		fieldFails("넘길 값을 정할 수 없습니다", "amount:int:수량");
		fieldFails(JAVA + "api/MemoQuickController.java:15", "amount:int:수량");
		change(JAVA + "api/MemoQuickController.java", "\"빠른 메모\", false, null", "\"빠른 메모\"");
		fieldFails("인자가 1개입니다(기대: 3개)", "amount:int:수량");
	}

	@Test
	void shapesTheCommandCannotReadFailWithoutChangingAnything() throws IOException {
		memo();
		String impl = JAVA + "internal/app/command/MemoFacadeImpl.java";
		String original = read(impl);
		write(impl, original.replace("\t\trow.setCreatedAt(now);\n", "\t\tstamp(row, now);\n"));
		fieldFails("row.setCreatedAt(…)", "note:text");
		write(impl, original);

		String xml = RESOURCES + "mapper/memo/MemoMapper.xml";
		String mapper = read(xml);
		write(xml, mapper.replace("id, title, pinned, created_at, updated_at", "id, pinned, title, created_at, updated_at"));
		fieldFails("열 순서", "note:text");
		write(xml, mapper.replace("<update id=\"update\">", "<update id=\"modify\">"));
		fieldFails("<update id=\"update\">", "note:text");
		write(xml, mapper);

		String views = JAVA + "api/dto/MemoViews.java";
		String viewsSource = read(views);
		write(views, viewsSource.replace("\tpublic static boolean unchanged(", "\tpublic static boolean same("));
		fieldFails("unchanged", "note:text");
		write(views, viewsSource);

		String fields = RESOURCES + "templates/memo/internal/fields.html";
		String fragment = read(fields);
		write(fields, fragment.replace("th:fragment=\"memoFields()\"", "th:fragment=\"inputs()\""));
		fieldFails("memoFields", "note:text");
		write(fields, fragment);

		String spec = RESOURCES + "specs/memo-detail.yml";
		String detail = read(spec);
		write(spec, detail.replace("    item: { id: 2,", "    item: [ 2,").replaceFirst("(?m)(item: \\[ 2,.*) }$", "$1 ]"));
		fieldFails(spec, "note:text");
		write(spec, detail);

		String screenTest = TESTS + "MemoScreenTest.java";
		write(screenTest, read(screenTest).replace(".param(\"pinned\", \"true\")", ""));
		fieldFails(".param(…)", "note:text");
	}

	@Test
	void specValuesAreAddedInFlowAndBlockMappingsOfEveryScreenThatUsesTheDto() throws IOException {
		memo();
		write(RESOURCES + "specs/memo-detail.yml", """
				screen: memo-detail
				template: memo/api/detail
				fragments: []
				model:
				  item: it.cozy.app.memo.api.dto.MemoViews.Detail
				states:
				  default:
				    # 블록 표기
				    item:
				      id: 1
				      title: 예시 제목
				      pinned: true
				      createdAt: "2026-11-06 10:00"
				      updatedAt: "2026-11-06 10:30"
				  empty:
				    item: { id: 2, title: "제목", pinned: false, createdAt: "2026-11-06 10:00", updatedAt: "2026-11-06 10:00" }
				""");
		field("dueOn:date:마감일");
		assertThat(read(RESOURCES + "specs/memo-detail.yml")).contains("""
				      pinned: true
				      dueOn: "2026-11-06"
				      createdAt: "2026-11-06 10:00"
				""").contains("pinned: false, dueOn: null, createdAt:");
		assertThat(read(RESOURCES + "specs/memo-list.yml"))
			.contains("- { id: 1, title: \"예시 제목\", pinned: true, dueOn: \"2026-11-06\", updatedAt: \"2026-11-06 10:00\" }");
		assertThat(read(RESOURCES + "specs/memo-form.yml")).contains("form: { title: \"예시 제목\", pinned: true, dueOn: \"2026-11-06\" }")
			.contains("  empty:\n    form: {}\n");
		field("body:longtext:본문");
		assertThat(read(RESOURCES + "specs/memo-detail.yml")).contains("""
				      dueOn: "2026-11-06"
				      bodyLines: [첫 줄, 둘째 줄]
				      createdAt: "2026-11-06 10:00"
				""").contains("dueOn: null, bodyLines: [], createdAt:");
		assertThat(read(RESOURCES + "specs/memo-list.yml")).doesNotContain("body");
	}

	@Test
	void theRecipeIsListedAndDryRunWritesNothing() throws IOException {
		memo();
		assertThat(ok("recipes").out())
			.contains("cozy add field <모듈> --entity <E> --field 이름:종류[!][:라벨] [--default 값]");
		Map<String, String> before = files();
		Result dry = field("note:text:비고", "--dry-run", "--json");
		assertThat(dry.out()).contains("\"recipe\": \"field\"").contains("\"applied\": false").contains("\"slots\": []");
		assertThat(files()).isEqualTo(before);
	}
}
