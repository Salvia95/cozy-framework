// 빈칸 없는 레시피만 실행한 앱: 만든 직후 mvn verify가 통과한다 (생성된 CRUD·화면 테스트 포함)
def log = new File(basedir, "build.log").getText("UTF-8")
["Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed", "in it.cozy.app.task.TaskCrudTest",
 "in it.cozy.app.task.TaskScreenTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
assert !log.contains("[C9]") : "빈칸이 남았습니다"
def events = ["TaskCreated", "TaskUpdated", "TaskDeleted", "ReportSent"]
events.each { assert new File(basedir, "src/test/resources/events/" + it + "/v1.json").isFile() : it + " 샘플이 없습니다" }
def report = new File(basedir, "src/main/java/it/cozy/app/report/package-info.java").getText("UTF-8")
assert report.contains('@ApplicationModule(allowedDependencies = { "task :: spi" })')
return true
