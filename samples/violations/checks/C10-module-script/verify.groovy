// C10: 모듈의 Alpine 컴포넌트 파일에 문법 오류가 있다 → 파일과 줄을 알리고, 다른 렌더링 검사는 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["[C10] src/main/resources/static/memo/memo.js:7 — ",
 "모든 모듈의 컴포넌트를 멈춥니다",
 "everyModuleScriptParses",
 "Tests run: 4, Failures: 1, Errors: 0, Skipped: 0"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
return true
