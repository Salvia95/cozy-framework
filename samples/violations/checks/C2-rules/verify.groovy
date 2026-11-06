// C2: voc 화면이 wiki의 내부 템플릿을 참조한다 → [R3]
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C2]") : "build.log에 [C2] 가 없습니다"
assert log.contains("[R3] templates/voc/api/list.html:2") : "build.log에 [R3] templates/voc/api/list.html:2 가 없습니다"
assert log.contains("templatesFollowBoundaryRules") : "build.log에 templatesFollowBoundaryRules 가 없습니다"
assert log.contains("Tests run: 5, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
