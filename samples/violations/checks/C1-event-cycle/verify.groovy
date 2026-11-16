// C1: 이벤트를 각 모듈의 spi.event에 두면 서로의 이벤트를 받는 두 모듈이 순환한다
def log = new File(basedir, "build.log").getText("UTF-8")
[
  "[C1]",
  "Cycle detected",
  "modulesAreVerified",
  "Tests run: 7, Failures: 1, Errors: 0, Skipped: 0",
].each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
