// C8: 모듈 안의 배치와 레이어 방향 위반이 각각 보고되고, 다른 검사(C1 등)는 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
def expected = [
  "[C8] it.cozy.app.memo.MemoService — 모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다",
  "[C8] it.cozy.app.memo.spi.MemoFinder → it.cozy.app.memo.internal.domain.Memo — spi는 internal·api 타입을 쓰지 않습니다",
  "[C8] it.cozy.app.memo.spi.dto — spi 패키지마다 package-info.java에 @NamedInterface(\"spi\")를 둡니다",
  "[C8] it.cozy.app.memo.api.MemoController → it.cozy.app.memo.internal.infra.MemoStore — api는 internal을 쓰지 않습니다",
  "[C8] it.cozy.app.memo.internal.app.command.MemoWriter — app.command의 클래스는 인터페이스 + Impl 쌍으로 둡니다",
  "[C8] it.cozy.app.memo.internal.app.query.MemoFinderImpl → it.cozy.app.memo.internal.app.command.MemoWriter — app.query는 app.command에 의존하지 않습니다",
  "[C8] it.cozy.app.memo.internal.domain.MemoRepository — domain에는 엔티티·VO·도메인 규칙만 둡니다",
  "modulesFollowLayerRules",
  "Tests run: 5, Failures: 1, Errors: 0, Skipped: 0",
]
expected.each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
assert log.contains("[C8] 모듈 구조 규칙 위반 7건") : "C8 위반이 7건이 아닙니다"
return true
