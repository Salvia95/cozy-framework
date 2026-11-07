// C1: a 모듈이 b 모듈의 internal 클래스를 쓴다 → ApplicationModules.verify() 실패
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C1]") : "build.log에 [C1] 가 없습니다"
assert log.contains("modulesAreVerified") : "build.log에 modulesAreVerified 가 없습니다"
assert log.contains("Tests run: 6, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
