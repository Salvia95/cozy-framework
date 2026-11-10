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
def list = new File(basedir, "src/main/resources/templates/task/api/list.html").getText("UTF-8")
["메모", "우선순위", "예상 시간", "마감일", "긴급", "코드"].each { assert list.contains('<th class="w-1">' + it + "</th>") : "목록에 열이 없습니다: " + it }
assert !list.contains("설명") : "긴 글은 목록에 넣지 않습니다"
def detail = new File(basedir, "src/main/resources/templates/task/api/detail.html").getText("UTF-8")
assert detail.contains('<h3 class="card-title">설명</h3>') && detail.contains('<div class="datagrid-title">코드</div>')
assert !new File(basedir, "recipes.log").getText("UTF-8").contains("남은 일") : "목록과 상세에 손으로 할 일이 남지 않아야 합니다"
return true
