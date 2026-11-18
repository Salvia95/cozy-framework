// C8: events 모듈에 이벤트가 아닌 클래스와 주인 폴더가 없는 이벤트가 있다
def log = new File(basedir, "build.log").getText("UTF-8")
[
  "[C8] it.cozy.app.events.spi.Orphan — 이벤트는 주인 모듈 이름의 폴더에 둡니다(it.cozy.app.events.spi.{주인 모듈})",
  "[C8] it.cozy.app.events.spi.book.BookFormat — events 모듈에는 이벤트 계약만 둡니다",
  "modulesFollowLayerRules",
  "Tests run: 9, Failures: 1, Errors: 0, Skipped: 0",
].each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
