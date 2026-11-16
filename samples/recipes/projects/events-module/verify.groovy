// 이벤트 계약 모듈을 거친 두 방향의 이벤트: 순환(C1) 없이 mvn verify를 통과하고, 리스너가 실제로 돈다
def log = new File(basedir, "build.log").getText("UTF-8")
["in it.cozy.app.EventsFlowTest", "in it.cozy.app.ArchitectureTest", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
["[C1]", "[C8]", "[C11]", "Cycle"].each {
  assert !log.contains(it) : "build.log에 있으면 안 되는 메시지가 있습니다: " + it
}
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert recipes.contains("[cozy check] 이상 없음") : "cozy check가 events 모듈을 통과시켜야 합니다"
return true
