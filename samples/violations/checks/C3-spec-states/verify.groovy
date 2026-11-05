// C3: 스펙에 empty 상태가 없다 → 스펙을 읽을 때 실패
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("화면 스펙 오류") : "build.log에 화면 스펙 오류가 없습니다"
assert log.contains("'empty'가 없습니다") : "build.log에 empty 상태 누락 메시지가 없습니다"
return true
