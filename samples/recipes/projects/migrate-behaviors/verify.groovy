// 손으로 쓴 모듈 JS를 cozy migrate behaviors로 옮기면 동작 명세와 그 명세에서 만든 JS가 되고, 손으로 붙인 템플릿 그대로 mvn verify를 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.task.TaskScreenTest", "in it.cozy.app.ArchitectureTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
["[C10]", "[C12", "[C13]", "[R16]"].each {
  assert !log.contains(it) : "build.log에 있으면 안 되는 메시지가 있습니다: " + it
}
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert recipes.contains("[C12 경고] src/main/resources/static/task/task.js → task — 손으로 쓴 모듈 JS입니다(직접 쓴 코드)") &&
    recipes.contains("고치는 명령: cozy migrate behaviors") : "옮기기 전의 cozy check가 경고와 고치는 명령을 보여야 합니다"
assert recipes.contains("[cozy check] 이상 없음 (R1") : "옮긴 뒤의 cozy check가 경고 없이 통과해야 합니다"

def read = { String file -> new File(basedir, file).getText("UTF-8") }
assert read("src/main/resources/behaviors/task.yml") == '''# task 모듈의 화면 안 동작(동작 명세). cozy add behavior가 고치고, 이 명세에서 static/task/task.js를 만든다
components:
  panel:
    purpose: "상세의 안내를 펼치고 접으며 제목의 글자 수를 보인다"
    state:
      open: "bool"
      title: "text@data-title"
    computed:
      label: "if(open, '안내 접기', '안내 펼치기')"
      size: "length(title)"
      summary: "format('제목 {size}자')"
    actions:
      toggle: "toggle(open)"
'''
def script = read("src/main/resources/static/task/task.js")
assert script.contains("cozy:behaviors spec=") && script.contains("// cozy:component taskPanel members=open,title,label,size,summary,toggle data=title actions=toggle emits= listens=")
return true
