// 레시피만 실행한 앱: 남은 빈칸 4곳(C9)만 실패하고 나머지 검사·테스트(생성된 memo 테스트 포함)는 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
def expected = [
  "[C9] 채우지 않은 빈칸 4곳",
  "[C9] src/main/java/it/cozy/app/activity/internal/app/command/MemoCreatedListener.java:17 → cozy:slot listener-body",
  "[C9] src/main/java/it/cozy/app/dashboard/api/DashboardController.java:20 → cozy:slot view-model",
  "[C9] src/main/resources/db/migration/V202611081001__activity_create_activity.sql:3 → cozy:slot migration-sql",
  "[C9] src/main/resources/templates/activity/spi/recent-activity.html:5 → cozy:slot fragment-body",
  "ArchitectureTest.slotsAreFilled",
  "Tests run: 14, Failures: 1, Errors: 0, Skipped: 0",
]
expected.each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
def recipes = new File(basedir, "recipes.log").getText("UTF-8")
[
  "이미 있고 내용이 다릅니다. 덮어쓰지 않습니다",
  "의존이 순환합니다: memo → activity → memo",
  "memo에 이벤트 MemoDeleted가 없습니다",
  "아이콘 'no-such-icon'가 Tabler Icons에 없습니다",
  "cozy add query는 2단계 레시피라 아직 만들지 않았습니다",
].each { assert recipes.contains(it) : "recipes.log에 기대한 메시지가 없습니다: " + it }
return true
