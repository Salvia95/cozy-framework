// 빈칸을 채운 앱: 남은 빈칸이 없고, 생성된 테스트와 손으로 쓴 흐름 테스트가 모두 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["Tests run: 15, Failures: 0, Errors: 0, Skipped: 0", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
assert !log.contains("[C9]") : "빈칸이 남았습니다"
// 손 작업(hand/reference-filled)에는 빈칸을 채운 결과만 있다. 구조(Finder, Mapper, 행, Service, record)는 명령이 만든다
def hand = new File(recipesHome, "hand/reference-filled/src/main/java")
def structure = []
hand.eachFileRecurse { f -> if (f.name ==~ /.*(Finder|FinderImpl|Mapper|Row|Service|ServiceImpl|Item)\.java/) { structure << f.name } }
assert structure.isEmpty() : "손으로 쓴 구조가 남았습니다: " + structure
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
assert recipes.contains("\$ cozy add query activity recent") && recipes.contains("\$ cozy add command activity record")
return true
