// C9: Java·HTML·SQL의 빈칸이 각각 한 번씩 보고되고(Java는 주석과 throw가 같은 빈칸), 다른 검사(C1, C2, C8)는 통과한다
def log = new File(basedir, "build.log").getText("UTF-8")
def expected = [
  "[C9] 채우지 않은 빈칸 3곳",
  "[C9] src/main/java/it/cozy/app/memo/internal/app/command/MemoFacadeImpl.java:12 → cozy:slot memo-create — 메모를 저장하고 id를 돌려주세요. 다 채우면 이 cozy:slot 표시를 지웁니다.",
  "[C9] src/main/resources/db/migration/V202611080900__memo_create_memo.sql:1 → cozy:slot migration-sql — memo 테이블을 만드는 DDL을 쓰세요.",
  "[C9] src/main/resources/templates/memo/internal/note.html:2 → cozy:slot note-body — 파라미터 text만으로 메모 한 줄을 그리세요.",
  "slotsAreFilled",
  "Tests run: 6, Failures: 1, Errors: 0, Skipped: 0",
]
expected.each { assert log.contains(it) : "build.log에 기대한 메시지가 없습니다: " + it }
return true
