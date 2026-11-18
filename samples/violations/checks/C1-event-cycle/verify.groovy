// C1: 이벤트를 각 모듈의 spi.event(옛 자리)에 두면 서로의 이벤트를 받는 두 모듈이 순환한다. 옛 자리는 C8도 실패하고 고치는 명령을 알린다
def log = new File(basedir, "build.log").getText("UTF-8")
[
  "[C1]",
  "Cycle detected",
  "modulesAreVerified",
  "[C8] it.cozy.app.book.spi.event.BookAdded — it.cozy.app.book.spi.event는 옛 이벤트 자리입니다",
  "고치는 명령: cozy migrate events",
  "Tests run: 9, Failures: 2, Errors: 0, Skipped: 0",
].each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
