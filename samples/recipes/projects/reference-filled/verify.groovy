// 빈칸을 채운 앱: 남은 빈칸이 없고, 생성된 테스트와 손으로 쓴 흐름 테스트가 모두 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
["Tests run: 15, Failures: 0, Errors: 0, Skipped: 0", "BUILD SUCCESS"].each {
  assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it
}
assert !log.contains("[C9]") : "빈칸이 남았습니다"
return true
