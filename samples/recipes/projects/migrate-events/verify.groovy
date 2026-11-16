// 옛 이벤트 자리의 앱을 cozy migrate events로 옮기면 순환 없이 mvn verify를 통과하고, 두 방향의 리스너가 돈다
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.EventsFlowTest", "in it.cozy.app.rental.BookAddedSampleTest", "in it.cozy.app.ArchitectureTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
["[C1]", "[C8]", "[C11]", "Cycle"].each {
  assert !log.contains(it) : "build.log에 있으면 안 되는 메시지가 있습니다: " + it
}
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert recipes.contains("[C8] it.cozy.app.book.spi.event.BookAdded — it.cozy.app.book.spi.event는 옛 이벤트 자리입니다") &&
    recipes.contains("고치는 명령: cozy migrate events") : "옮기기 전의 cozy check가 옛 자리와 고치는 명령을 보여야 합니다"
assert recipes.contains("[cozy check] 이상 없음") : "옮긴 뒤의 cozy check가 통과해야 합니다"

def main = "src/main/java/it/cozy/app/"
def read = { String file -> new File(basedir, file).getText("UTF-8") }
assert !new File(basedir, main + "book/spi/event").exists() && !new File(basedir, main + "rental/spi/event").exists() : "옛 자리가 남았습니다"
assert read(main + "events/spi/book/BookAdded.java").startsWith("package it.cozy.app.events.spi.book;")
assert read(main + "events/spi/rental/RentalCreated.java").startsWith("package it.cozy.app.events.spi.rental;")
// book은 rental의 이벤트만 받았으므로 rental 의존이 없어진다. rental은 book을 읽으므로 남는다
assert read(main + "book/package-info.java").contains('allowedDependencies = { "events :: spi" }')
assert read(main + "rental/package-info.java").contains('allowedDependencies = { "book :: spi", "events :: spi" }')
assert read("src/test/java/it/cozy/app/rental/BookAddedSampleTest.java").contains("import it.cozy.app.events.spi.book.BookAdded;")
def sql = read("src/main/resources/db/migration/V202611081000__cozy_move_event_contracts.sql")
assert sql.contains("UPDATE cozy_event_publication SET event_type = 'it.cozy.app.events.spi.book.BookAdded' WHERE event_type = 'it.cozy.app.book.spi.event.BookAdded';")
assert sql.contains("UPDATE cozy_inbox SET listener_id = replace(listener_id, '(it.cozy.app.rental.spi.event.RentalCreated)', '(it.cozy.app.events.spi.rental.RentalCreated)')")
return true
