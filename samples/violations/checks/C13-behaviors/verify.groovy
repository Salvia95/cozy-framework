// C13: 붙이지 않은 컴포넌트, 부르지 않는 동작, 받는 곳이 없는 화면 이벤트, 내는 곳이 없는 화면 이벤트 → 명세의 줄과 고치는 명령을 알리고,
// 다른 검사(C1, C2, C8, C9, C12)는 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
def expected = [
  "[C13] 이어지지 않은 화면 안의 동작 4곳",
  "[C13] src/main/resources/behaviors/memo.yml:3 → memoPanel — 어느 템플릿에도 붙이지 않은 컴포넌트입니다: memoPanel",
  "[C13] src/main/resources/behaviors/memo.yml:9 → memoSelection.drop — 부르는 요소가 없는 동작입니다: drop",
  "[C13] src/main/resources/behaviors/memo.yml:9 → memoSelection → memo:picked — 받는 곳이 없는 화면 이벤트입니다: memo:picked",
  "[C13] src/main/resources/behaviors/memo.yml:9 → memoSelection ← memo:archived — 내는 곳이 없는 화면 이벤트입니다: memo:archived",
  "behaviorsAreWired",
  "Tests run: 9, Failures: 1, Errors: 0, Skipped: 0",
]
expected.each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
