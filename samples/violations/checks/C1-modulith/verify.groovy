// C1: a 모듈이 b 모듈의 internal 클래스와, 선언하지 않은 b의 spi를 쓴다 → ApplicationModules.verify() 실패
// spi 쪽은 고치는 명령(cozy link)이 함께 나오고, internal 쪽은 명령이 없다 (ADR-0032 §6)
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C1]") : "build.log에 [C1] 가 없습니다"
assert log.contains("modulesAreVerified") : "build.log에 modulesAreVerified 가 없습니다"
assert log.contains("-> it.cozy.app.b.internal.infra.BHelper") : "internal 위반이 없습니다"
assert log.contains("고치는 명령: cozy link a --uses b") : "spi 위반에 고치는 명령이 없습니다"
assert log.count("고치는 명령:") == 2 : "고치는 명령은 한 번만(보고 두 곳: 오류 본문과 요약) 나와야 합니다"
assert log.contains("Tests run: 6, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
