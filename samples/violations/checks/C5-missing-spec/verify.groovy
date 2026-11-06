// C5: 화면 템플릿에 스펙이 없다
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C5] templates/memo/api/list.html") : "build.log에 [C5] templates/memo/api/list.html 가 없습니다"
assert log.contains("specs/memo-list.yml") : "build.log에 specs/memo-list.yml 가 없습니다"
assert log.contains("everyScreenTemplateHasSpec") : "build.log에 everyScreenTemplateHasSpec 가 없습니다"
assert log.contains("Tests run: 3, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
