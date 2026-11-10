// crud에 필드를 일곱 번 더한 앱: 더한 직후 mvn verify가 통과한다 (생성된 CRUD·화면 테스트, 스펙 렌더링 포함)
def log = new File(basedir, "build.log").getText("UTF-8")
["Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed", "in it.cozy.app.task.TaskCrudTest",
 "in it.cozy.app.task.TaskScreenTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
assert !log.contains("[C9]") : "빈칸이 남았습니다"
def migrations = new File(basedir, "src/main/resources/db/migration").list().findAll { it.contains("_add_task_") }
assert migrations.size() == 7 : "필드마다 마이그레이션이 하나씩 있어야 합니다: " + migrations
def item = new File(basedir, "src/main/java/it/cozy/app/task/spi/dto/TaskItem.java").getText("UTF-8")
assert item.contains("public record TaskItem(long id, String title, boolean done, String note, String detail, Integer priority, Long estimate, "
    + "LocalDate due, boolean urgent, String code, Instant createdAt, Instant updatedAt)")
def impl = new File(basedir, "src/main/java/it/cozy/app/task/internal/app/command/TaskFacadeImpl.java").getText("UTF-8")
assert impl.contains("title.strip()") : "손으로 고친 본문이 남아 있어야 합니다"
return true
