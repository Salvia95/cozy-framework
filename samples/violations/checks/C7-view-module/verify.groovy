// C7: memo 모듈의 컨트롤러가 wiki/ 뷰를 반환한다 → 앱의 MockMvc 테스트가 실패
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C7] it.cozy.app.memo.api.MemoController#list") : "build.log에 [C7] it.cozy.app.memo.api.MemoController#list 가 없습니다"
assert log.contains("wiki/api/list") : "build.log에 wiki/api/list 가 없습니다"
assert log.contains("MemoControllerTest") : "build.log에 MemoControllerTest 가 없습니다"
assert log.contains("Tests run: 1, Failures: 0, Errors: 1, Skipped: 0") : "MemoControllerTest가 C7로 실패하지 않았습니다"
return true
