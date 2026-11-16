// 명령으로 만든 두 방향의 이벤트: 이벤트 계약 모듈에 이벤트가 있고, 순환(C1) 없이 mvn verify를 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.ArchitectureTest", "in it.cozy.app.book.BookCrudTest", "in it.cozy.app.rental.RentalCrudTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
["[C1]", "[C8]", "[C11]", "Cycle"].each {
  assert !log.contains(it) : "build.log에 있으면 안 되는 메시지가 있습니다: " + it
}
def java = "src/main/java/it/cozy/app/"
["events/package-info.java", "events/spi/book/BookCreated.java", "events/spi/rental/RentalCreated.java"].each {
  assert new File(basedir, java + it).isFile() : it + "가 없습니다"
}
["book/spi/event", "rental/spi/event"].each {
  assert !new File(basedir, java + it).exists() : it + "에 이벤트를 만들면 안 됩니다"
}
def book = new File(basedir, java + "book/package-info.java").getText("UTF-8")
assert book.contains('allowedDependencies = { "events :: spi" }') : book
def rental = new File(basedir, java + "rental/package-info.java").getText("UTF-8")
assert rental.contains('allowedDependencies = { "events :: spi", "book :: spi" }') : rental
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert recipes.contains("[cozy check] 이상 없음") : "cozy check가 통과해야 합니다"
assert recipes.contains("의존이 순환합니다: book → rental → book") : "읽기 순환은 거절해야 합니다"
return true
