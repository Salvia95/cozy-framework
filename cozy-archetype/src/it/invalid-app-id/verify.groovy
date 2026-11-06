def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("Bad_Id") : "appId 검사 메시지가 없습니다"
assert !new File(basedir, "bad-id").exists() : "실패한 생성 결과가 남아 있습니다"
return true
