// 빈칸 4곳(C9)만 실패하고, 생성된 화면 스펙의 렌더링(C3·C4)과 이벤트 샘플(C6) 검사는 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
[
  "[C9] 채우지 않은 빈칸 4곳",
  "[C9] src/main/java/it/cozy/app/report/api/ReportSummaryController.java",
  "[C9] src/main/java/it/cozy/app/report/api/ReportController.java",
  "[C9] src/main/java/it/cozy/app/report/internal/app/command/ReportAuditListener.java",
  "[C9] src/main/resources/db/migration/V202611081000__report_create_report.sql",
  "ArchitectureTest.slotsAreFilled",
  "Tests run: 9, Failures: 1, Errors: 0, Skipped: 0",
].each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
