// 모듈 컴포넌트: 동작 명세에서 만든 JS는 빈칸이 없고, 손으로 고친 명세는 C12, 다른 모듈 컴포넌트는 R16으로 실패하고, 고친 앱은 mvn verify를 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.task.TaskCrudTest", "in it.cozy.app.task.TaskScreenTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
assert !log.contains("[C9]") && !log.contains("[R16]") && !log.contains("[C10]") && !log.contains("[C12]")
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert !recipes.contains("[C9]") : "add behavior는 빈칸을 남기지 않습니다"
assert recipes.contains("[C12] src/main/resources/behaviors/task.yml → task — 동작 명세를 고친 뒤") : "손으로 고친 명세는 C12여야 합니다"
assert recipes.contains("[R16] templates/task/api/detail.html") && recipes.contains("note 모듈의 컴포넌트입니다") : "다른 모듈의 컴포넌트는 R16이어야 합니다"
def task = new File(basedir, "src/main/resources/static/task/task.js").getText("UTF-8")
assert task.contains("window.Alpine.data('taskTitleCount'") && task.contains("cozy:behaviors spec=") && !task.contains("cozy:slot")
def spec = new File(basedir, "src/main/resources/behaviors/task.yml").getText("UTF-8")
assert spec.contains('      length: "length(title)"') : "cozy sync behaviors가 명세를 정리한 모양으로 다시 씁니다"
return true
