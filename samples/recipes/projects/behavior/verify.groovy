// 모듈 컴포넌트: 빈칸이 남으면 C9, 다른 모듈 컴포넌트는 R16으로 실패하고, 고친 앱은 mvn verify를 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.task.TaskCrudTest", "in it.cozy.app.task.TaskScreenTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
assert !log.contains("[C9]") && !log.contains("[R16]")
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert recipes.contains("[C9] src/main/resources/static/task/task.js") && recipes.contains("behavior-notePicker") : "채우기 전에는 빈칸 둘이 보여야 합니다"
assert recipes.contains("[R16] templates/task/api/detail.html") && recipes.contains("note 모듈의 컴포넌트입니다") : "다른 모듈의 컴포넌트는 R16이어야 합니다"
def task = new File(basedir, "src/main/resources/static/task/task.js").getText("UTF-8")
assert task.contains("window.Alpine.data('taskTitleCount'") && !task.contains("cozy:slot")
return true
