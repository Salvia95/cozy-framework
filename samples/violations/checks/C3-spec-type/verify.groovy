// C3: 스펙 fixture의 필드 이름이 화면 DTO와 다르다 → 렌더링 실패
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C3] specs/memo-list.yml 상태 default") : "build.log에 [C3] specs/memo-list.yml 상태 default 가 없습니다"
assert log.contains("ListView") : "build.log에 ListView 가 없습니다"
assert log.contains("everyScreenStateRendersAsValidHtml") : "build.log에 everyScreenStateRendersAsValidHtml 가 없습니다"
assert log.contains("Tests run: 3, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
