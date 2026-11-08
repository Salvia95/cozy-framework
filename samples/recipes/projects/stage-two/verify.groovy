// enable events·dto·listener의 결과: 빈칸을 채운 뒤 mvn verify가 통과한다 (생성된 CRUD·화면 테스트, 모듈 검사, 이벤트 샘플 포함)
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.memo.MemoCrudTest", "in it.cozy.app.board.BoardCrudTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
assert !log.contains("[C9]") : "빈칸이 남았습니다"
def read = { new File(basedir, it).getText("UTF-8") }
def pom = read("pom.xml")
assert pom.contains("<!-- 모듈 사이 이벤트 (cozy enable events) -->\n    <dependency>\n      <groupId>io.salvia95.cozy</groupId>\n      <artifactId>cozy-starter-events</artifactId>")
assert pom.count("cozy-starter-events") == 1
def board = "src/main/java/it/cozy/app/board/"
assert read(board + "spi/BoardFacade.java").contains("long create(String title);")
assert read(board + "internal/app/command/MemoCreatedListener.java").contains("MemoCreatedListener(BoardFacade facade)")
assert read(board + "spi/dto/BoardSummary.java").contains("public record BoardSummary(long total, BoardLine latest, List<BoardLine> lines, LocalDate since)")
def event = read("src/main/java/it/cozy/app/memo/spi/event/MemoCreated.java")
assert event.contains("String title, Boolean pinned, LocalDate dueOn, String note) implements CozyEvent")
assert event.contains("return new MemoCreated(UUID.randomUUID(), 3, memoId, title, pinned, dueOn, note);")
assert read("src/main/java/it/cozy/app/memo/internal/app/command/MemoFacadeImpl.java").contains("MemoCreated.of(") : "crud가 옛 of()를 부른다"
["v1", "v2", "v3"].each { assert new File(basedir, "src/test/resources/events/MemoCreated/" + it + ".json").isFile() : it }
assert log =~ /Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: [^\n]* in it\.cozy\.app\.ArchitectureTest/ : "C6를 포함한 검사가 통과해야 합니다"
def recipes = read("recipes.log")
[
  "--publish(이벤트)에는 cozy-starter-events가 필요합니다",
  "만듦  src/main/java/it/cozy/app/board/spi/BoardFacade.java",
  "바꿈  src/main/java/it/cozy/app/board/spi/BoardFacade.java    listener가 만든 빈 Facade를 crud로 채움",
  "record에는 필수 여부가 없어 !를 쓰지 않습니다",
  "it.cozy.app.board.spi.dto.BoardRow가 없습니다",
  "BoardLine.java가 이미 있고 내용이 다릅니다",
  "+= Boolean pinned, LocalDate dueOn, schemaVersion 1 → 2",
  "MemoCreated에 pinned가 이미 Boolean로 있습니다(원하는 것: Integer)",
].each { assert recipes.contains(it) : "recipes.log에 기대한 메시지가 없습니다: " + it }
return true
