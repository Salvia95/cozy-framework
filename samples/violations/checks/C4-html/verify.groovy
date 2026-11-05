// C4: 렌더링 결과에 같은 id가 두 번 있다 → vnu 오류
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C4] specs/memo-list.yml 상태 default") : "build.log에 [C4] specs/memo-list.yml 상태 default 가 없습니다"
assert log.contains("Duplicate ID") : "build.log에 Duplicate ID 가 없습니다"
assert log.contains("everyScreenStateRendersAsValidHtml") : "build.log에 everyScreenStateRendersAsValidHtml 가 없습니다"
assert log.contains("Tests run: 3, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
