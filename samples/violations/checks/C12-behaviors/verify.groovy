// C12: 동작 명세에서 만든 JS를 손으로 고쳤다 → 그 파일과 고치는 명령을 알리고, 다른 검사(C1, C2, C8, C9)는 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
def expected = [
  "[C12] 동작 명세와 만든 JS가 맞지 않음 1곳",
  "[C12] src/main/resources/static/memo/memo.js → memo — 동작 명세에서 만든 파일을 손으로 고쳤습니다",
  "고치는 명령: cozy sync behaviors",
  "behaviorScriptsMatchTheirSpecs",
  "Tests run: 8, Failures: 1, Errors: 0, Skipped: 0",
]
expected.each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
