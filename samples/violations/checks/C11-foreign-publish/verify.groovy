// C11: rental이 book의 이벤트를 만든다
def log = new File(basedir, "build.log").getText("UTF-8")
[
  "[C11] it.cozy.app.rental.internal.app.command.RentalFacadeImpl → it.cozy.app.events.spi.book.BookAdded — 이벤트는 주인 모듈(book)의 internal.app.command에서만 만듭니다",
  "eventsArePublishedByTheirOwner",
  "Tests run: 8, Failures: 1, Errors: 0, Skipped: 0",
].each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
