// 옛 동작 속성을 쓰던 앱: cozy check가 R16으로 실패하고, cozy migrate alpine 뒤에 mvn verify가 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.task.TaskCrudTest", "in it.cozy.app.task.TaskScreenTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert recipes.contains("[R16]") && recipes.contains("data-cozy-confirm") && recipes.contains("cozy migrate alpine이 바꿉니다") :
    "옮기기 전의 cozy check가 없앤 속성과 고치는 명령을 보여야 합니다"
assert recipes.contains("data-cozy-confirm → cozyConfirm 1곳, data-cozy-copy → cozyCopy 1곳") || recipes.contains("data-cozy-copy → cozyCopy 1곳, data-cozy-confirm → cozyConfirm 1곳")
def detail = new File(basedir, "src/main/resources/templates/task/api/detail.html").getText("UTF-8")
// 주석은 옮기지 않는다(옛 속성 이름이 설명에 남아 있다). 속성만 본다
assert !(detail =~ /\s(th:)?data-cozy-(confirm|copy)\s*=/) : "옛 속성이 남았습니다"
assert detail.contains('x-data="cozyConfirm" x-on:submit="ask" data-confirm-message="기록을 지울까요?"')
assert detail.contains('x-data="cozyCopy" x-on:click="copy" th:data-copy-text="${item.title}"')
return true
